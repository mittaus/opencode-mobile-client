package opencode

import (
	"context"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"strings"
	"time"

	"github.com/igniteLabs/opencode-gateway/config"
)

// Client is a thin HTTP client that proxies requests to the OpenCode CLI server.
// It adds basic auth credentials on every request when a password is configured.
type Client struct {
	baseURL  string
	password string
	http     *http.Client
	sseHTTP  *http.Client // separate client with no timeout for SSE
}

// NewClientWithBaseURL creates a new OpenCode HTTP client pointing at an arbitrary base URL.
// This is used by the pool to create per-project clients on different ports.
func NewClientWithBaseURL(baseURL, password string) *Client {
	transport := &http.Transport{
		MaxIdleConns:       20,
		IdleConnTimeout:    90 * time.Second,
		DisableCompression: true,
	}
	return &Client{
		baseURL:  baseURL,
		password: password,
		http: &http.Client{
			Transport: transport,
			Timeout:   30 * time.Second,
		},
		sseHTTP: &http.Client{
			Transport: transport,
			Timeout:   0,
		},
	}
}

// NewClient creates a new OpenCode HTTP client.
func NewClient(cfg *config.Config) *Client {
	transport := &http.Transport{
		MaxIdleConns:        20,
		IdleConnTimeout:     90 * time.Second,
		DisableCompression:  true,
	}

	return &Client{
		baseURL:  cfg.OpenCodeBaseURL,
		password: cfg.OpenCodePassword,
		http: &http.Client{
			Transport: transport,
			Timeout:   30 * time.Second,
		},
		sseHTTP: &http.Client{
			Transport: transport,
			Timeout:   0, // no timeout — SSE is a perpetual stream
		},
	}
}

// do executes an HTTP request against the OpenCode server and returns (body, statusCode, error).
func (c *Client) do(ctx context.Context, method, path string, body io.Reader) ([]byte, int, error) {
	url := c.baseURL + path

	req, err := http.NewRequestWithContext(ctx, method, url, body)
	if err != nil {
		return nil, 0, fmt.Errorf("building request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	if c.password != "" {
		req.SetBasicAuth("opencode", c.password)
	}

	resp, err := c.http.Do(req)
	if err != nil {
		return nil, 0, fmt.Errorf("executing request %s %s: %w", method, path, err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, resp.StatusCode, fmt.Errorf("reading response body: %w", err)
	}

	return respBody, resp.StatusCode, nil
}

// StreamEvents connects to OpenCode's SSE endpoint and returns a channel of raw JSON strings.
// The caller controls the lifetime via ctx.
func (c *Client) StreamEvents(ctx context.Context) (<-chan string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+"/event", nil)
	if err != nil {
		return nil, err
	}
	if c.password != "" {
		req.SetBasicAuth("opencode", c.password)
	}
	req.Header.Set("Accept", "text/event-stream")
	req.Header.Set("Cache-Control", "no-cache")

	resp, err := c.sseHTTP.Do(req)
	if err != nil {
		return nil, fmt.Errorf("connecting to /event: %w", err)
	}

	ch := make(chan string, 64)

	go func() {
		defer close(ch)
		defer resp.Body.Close()

		buf := make([]byte, 0, 4096)
		tmp := make([]byte, 512)
		eventCount := 0

		for {
			n, err := resp.Body.Read(tmp)
			if n > 0 {
				buf = append(buf, tmp[:n]...)
				before := eventCount
				buf, eventCount = processSSEBuffer(buf, ch, eventCount)
				if eventCount > before {
					// logged inside processSSEBuffer
				}
			}
			if err != nil {
				log.Printf("[opencode-sse] stream ended after %d events: %v", eventCount, err)
				return
			}
		}
	}()

	return ch, nil
}

// processSSEBuffer extracts complete SSE lines from buf, sends "data:" lines to ch,
// and returns the remaining unprocessed bytes and updated event count.
func processSSEBuffer(buf []byte, ch chan<- string, eventCount int) ([]byte, int) {
	for {
		// find next newline
		nl := -1
		for i, b := range buf {
			if b == '\n' {
				nl = i
				break
			}
		}
		if nl < 0 {
			break
		}

		line := string(buf[:nl])
		buf = buf[nl+1:]

		// strip trailing \r
		if len(line) > 0 && line[len(line)-1] == '\r' {
			line = line[:len(line)-1]
		}

		if len(line) > 5 && line[:5] == "data:" {
			data := line[5:]
			if len(data) > 0 && data[0] == ' ' {
				data = data[1:]
			}
			eventCount++
			preview := data
			if len(preview) > 120 {
				preview = preview[:120] + "…"
			}
			log.Printf("[opencode-sse] event #%d: %s", eventCount, preview)
			select {
			case ch <- data:
			default:
				log.Printf("[opencode-sse] channel full — dropping event #%d", eventCount)
			}
		}
	}
	return buf, eventCount
}

// ─── OpenCodeRepository implementation (proxy methods) ────────────────────────

func (c *Client) GetHealth(ctx context.Context) ([]byte, int, error) {
	return c.do(ctx, http.MethodGet, "/global/health", nil)
}

func (c *Client) GetProjects(ctx context.Context) ([]byte, int, error) {
	return c.do(ctx, http.MethodGet, "/project", nil)
}

func (c *Client) GetCurrentProject(ctx context.Context) ([]byte, int, error) {
	return c.do(ctx, http.MethodGet, "/project/current", nil)
}

func (c *Client) GetVcs(ctx context.Context) ([]byte, int, error) {
	return c.do(ctx, http.MethodGet, "/vcs", nil)
}

func (c *Client) GetProviders(ctx context.Context) ([]byte, int, error) {
	return c.do(ctx, http.MethodGet, "/provider", nil)
}

func (c *Client) GetSessions(ctx context.Context, directory string) ([]byte, int, error) {
	path := "/session"
	if directory != "" {
		path += "?directory=" + url.QueryEscape(directory)
	}
	return c.do(ctx, http.MethodGet, path, nil)
}

func (c *Client) CreateSession(ctx context.Context, body io.Reader, directory string) ([]byte, int, error) {
	path := "/session"
	if directory != "" {
		path += "?directory=" + url.QueryEscape(directory)
	}
	return c.do(ctx, http.MethodPost, path, body)
}

func (c *Client) DeleteSession(ctx context.Context, id string) ([]byte, int, error) {
	return c.do(ctx, http.MethodDelete, "/session/"+id, nil)
}

func (c *Client) AbortSession(ctx context.Context, id string) ([]byte, int, error) {
	return c.do(ctx, http.MethodPost, "/session/"+id+"/abort", nil)
}

func (c *Client) GetMessages(ctx context.Context, sessionID string) ([]byte, int, error) {
	return c.do(ctx, http.MethodGet, "/session/"+sessionID+"/message", nil)
}

func (c *Client) SendMessage(ctx context.Context, sessionID string, body io.Reader) ([]byte, int, error) {
	return c.do(ctx, http.MethodPost, "/session/"+sessionID+"/message", body)
}

func (c *Client) SendMessageAsync(ctx context.Context, sessionID string, body io.Reader) ([]byte, int, error) {
	return c.do(ctx, http.MethodPost, "/session/"+sessionID+"/prompt_async", body)
}

func (c *Client) ExecuteCommand(ctx context.Context, sessionID string, body io.Reader) ([]byte, int, error) {
	return c.do(ctx, http.MethodPost, "/session/"+sessionID+"/command", body)
}

func (c *Client) GetTodo(ctx context.Context, sessionID string) ([]byte, int, error) {
	return c.do(ctx, http.MethodGet, "/session/"+sessionID+"/todo", nil)
}

func (c *Client) GetDiff(ctx context.Context, sessionID, messageID string) ([]byte, int, error) {
	path := "/session/" + sessionID + "/diff"
	if messageID != "" {
		path += "?messageID=" + messageID
	}
	return c.do(ctx, http.MethodGet, path, nil)
}

func (c *Client) RevertMessage(ctx context.Context, sessionID string, body io.Reader) ([]byte, int, error) {
	return c.do(ctx, http.MethodPost, "/session/"+sessionID+"/revert", body)
}

func (c *Client) RespondToPermission(ctx context.Context, sessionID, permissionID string, body io.Reader) ([]byte, int, error) {
	return c.do(ctx, http.MethodPost, "/session/"+sessionID+"/permissions/"+permissionID, body)
}

func (c *Client) RegisterProject(ctx context.Context, directory string) ([]byte, int, error) {
	// Check if .git already exists; if not, initialise the repo.
	gitDir := directory + "/.git"
	if _, err := os.Stat(gitDir); os.IsNotExist(err) {
		log.Printf("[opencode] RegisterProject: .git not found in %q — running git init", directory)
		cmd := exec.CommandContext(ctx, "git", "init", directory)
		if out, err := cmd.CombinedOutput(); err != nil {
			return nil, http.StatusInternalServerError, fmt.Errorf("git init %q: %w\n%s", directory, err, out)
		}
	}

	// Create a session for the directory — this is how OpenCode registers a project.
	path := "/session?directory=" + url.QueryEscape(directory)
	return c.do(ctx, http.MethodPost, path, strings.NewReader("{}"))
}
