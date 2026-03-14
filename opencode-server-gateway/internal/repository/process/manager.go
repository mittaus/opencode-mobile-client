package process

import (
	"context"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"strconv"
	"sync"
	"time"

	"github.com/igniteLabs/opencode-gateway/config"
	"github.com/igniteLabs/opencode-gateway/internal/domain"
)

const maxLogLines = 500

// Manager manages the opencode CLI subprocess lifecycle.
type Manager struct {
	cfg       *config.Config
	// port and directory override cfg values when set (used by NewManagerForProject).
	// A zero port means "use cfg.OpenCodePort"; an empty directory means "use cfg.OpenCodeProject".
	port      int
	directory string

	mu        sync.Mutex
	cmd       *exec.Cmd
	state     domain.ProcessState
	pid       int
	startedAt *time.Time
	logLines  []string
	logMu     sync.RWMutex
}

// NewManager creates a process manager whose port and working directory come from cfg.
// This is the bootstrap manager (port cfg.OpenCodePort, dir cfg.OpenCodeProject).
func NewManager(cfg *config.Config) *Manager {
	return &Manager{
		cfg:      cfg,
		state:    domain.ProcessStateStopped,
		logLines: make([]string, 0, maxLogLines),
	}
}

// NewManagerForProject creates a process manager for a specific project on a specific port.
// The port and directory are stored as instance fields and take precedence over cfg values,
// allowing each project to have its own process without mutating the shared config.
func NewManagerForProject(cfg *config.Config, port int, directory string) *Manager {
	return &Manager{
		cfg:       cfg,
		port:      port,
		directory: directory,
		state:     domain.ProcessStateStopped,
		logLines:  make([]string, 0, maxLogLines),
	}
}

// effectivePort returns the port this manager binds to.
func (m *Manager) effectivePort() int {
	if m.port != 0 {
		return m.port
	}
	return m.cfg.OpenCodePort
}

// effectiveDirectory returns the working directory for the subprocess.
func (m *Manager) effectiveDirectory() string {
	if m.directory != "" {
		return m.directory
	}
	return m.cfg.OpenCodeProject
}

// effectiveBaseURL returns the health-check base URL for this manager's port.
func (m *Manager) effectiveBaseURL() string {
	if m.port != 0 {
		return fmt.Sprintf("http://127.0.0.1:%d", m.port)
	}
	return m.cfg.OpenCodeBaseURL
}

// Start launches the opencode CLI subprocess.
func (m *Manager) Start(ctx context.Context) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if m.state == domain.ProcessStateRunning || m.state == domain.ProcessStateStarting {
		return fmt.Errorf("opencode is already %s", m.state)
	}

	m.state = domain.ProcessStateStarting

	args := []string{
		"serve",
		"--hostname", "0.0.0.0",
		"--port", strconv.Itoa(m.effectivePort()),
	}

	cmd := exec.CommandContext(ctx, m.cfg.OpenCodeBin, args...)

	dir := m.effectiveDirectory()
	log.Printf("[process] effectiveDirectory=%q (m.directory=%q cfg.OpenCodeProject=%q)", dir, m.directory, m.cfg.OpenCodeProject)
	if dir != "" {
		cmd.Dir = dir
		log.Printf("[process] cmd.Dir set to %q", cmd.Dir)
	} else {
		log.Printf("[process] cmd.Dir not set (directory is empty) — opencode will inherit gateway cwd")
	}

	// Pass the server password via environment
	env := os.Environ()
	if m.cfg.OpenCodePassword != "" {
		env = append(env, "OPENCODE_SERVER_PASSWORD="+m.cfg.OpenCodePassword)
	}
	cmd.Env = env

	// Capture stdout + stderr into our ring buffer
	cmd.Stdout = &logWriter{m: m, prefix: "[opencode][stdout] "}
	cmd.Stderr = &logWriter{m: m, prefix: "[opencode][stderr] "}

	if err := cmd.Start(); err != nil {
		m.state = domain.ProcessStateStopped
		return fmt.Errorf("failed to start opencode: %w", err)
	}

	now := time.Now()
	m.cmd = cmd
	m.pid = cmd.Process.Pid
	m.startedAt = &now
	m.state = domain.ProcessStateRunning

	log.Printf("[process] opencode started — PID %d port %d dir=%q", m.pid, m.effectivePort(), m.effectiveDirectory())

	// Watch for unexpected exits and auto-restart
	go m.watch(ctx)

	// Wait until opencode's HTTP server is ready
	if err := m.waitReady(ctx, 20*time.Second); err != nil {
		log.Printf("[process] opencode started but not ready: %v", err)
		// Don't fail Start() — process is running, just not ready yet
	}

	return nil
}

// waitReady polls opencode's health endpoint until it responds (any HTTP status = up).
// 401 with auth means opencode is running with a password — counts as ready.
func (m *Manager) waitReady(ctx context.Context, timeout time.Duration) error {
	healthURL := m.effectiveBaseURL() + "/global/health"
	deadline := time.Now().Add(timeout)
	client := &http.Client{Timeout: 2 * time.Second}
	for time.Now().Before(deadline) {
		req, _ := http.NewRequest(http.MethodGet, healthURL, nil)
		if m.cfg.OpenCodePassword != "" {
			req.SetBasicAuth("opencode", m.cfg.OpenCodePassword)
		}
		resp, err := client.Do(req)
		if err == nil {
			resp.Body.Close()
			log.Printf("[process] waitReady port %d: health status=%d ✓", m.effectivePort(), resp.StatusCode)
			return nil
		}
		log.Printf("[process] waitReady port %d: %v", m.effectivePort(), err)
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(300 * time.Millisecond):
		}
	}
	return fmt.Errorf("opencode on port %d did not become ready within %s", m.effectivePort(), timeout)
}

// Stop terminates the opencode subprocess gracefully.
func (m *Manager) Stop(_ context.Context) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if m.state != domain.ProcessStateRunning {
		return fmt.Errorf("opencode is not running (current state: %s)", m.state)
	}

	m.state = domain.ProcessStateStopping
	log.Printf("[process] stopping opencode PID %d port %d", m.pid, m.effectivePort())

	if err := stopProcess(m.cmd.Process); err != nil {
		m.state = domain.ProcessStateRunning
		return fmt.Errorf("failed to stop opencode: %w", err)
	}

	m.state = domain.ProcessStateStopped
	m.pid = 0
	m.startedAt = nil
	return nil
}

// Restart stops then starts the process.
func (m *Manager) Restart(ctx context.Context) error {
	if err := m.Stop(ctx); err != nil {
		log.Printf("[process] restart: stop error (ignored): %v", err)
	}
	time.Sleep(500 * time.Millisecond)
	return m.Start(ctx)
}

// Switch saves the new working directory, stops opencode, and waits for
// the port to be free so the next Start() has a clean slate.
// For the bootstrap manager (no instance-level port/dir), this mutates cfg as before.
// For project-specific managers, it updates the instance-level directory field.
func (m *Manager) Switch(ctx context.Context, directory string) error {
	log.Printf("[process] switch — saving new dir=%q, stopping opencode", directory)
	if m.port != 0 {
		// Per-project manager: update instance field, not shared cfg.
		m.directory = directory
	} else {
		m.cfg.OpenCodeProject = directory
	}
	if err := m.Stop(ctx); err != nil {
		log.Printf("[process] switch: stop (ignored): %v", err)
	}
	// If the port is still in use (e.g. an orphaned process), force-kill whatever holds it.
	if m.isPortInUse() {
		port := m.effectivePort()
		log.Printf("[process] switch: port %d still in use after stop — killing by port", port)
		if err := killByPort(port); err != nil {
			log.Printf("[process] switch: killByPort: %v", err)
		}
	}
	// Wait for port to be freed before returning so next Start() doesn't race
	if err := m.waitPortFree(ctx, 10*time.Second); err != nil {
		log.Printf("[process] switch: port not freed in time (proceeding): %v", err)
	}
	log.Printf("[process] switch complete — next Start() will use dir=%q port=%d", directory, m.effectivePort())
	return nil
}

// isPortInUse does a quick check (no waiting) on whether the opencode port is occupied.
func (m *Manager) isPortInUse() bool {
	addr := fmt.Sprintf("127.0.0.1:%d", m.effectivePort())
	conn, err := net.DialTimeout("tcp", addr, 200*time.Millisecond)
	if err != nil {
		return false
	}
	conn.Close()
	return true
}

// waitPortFree polls until the manager's port is no longer accepting connections.
func (m *Manager) waitPortFree(ctx context.Context, timeout time.Duration) error {
	port := m.effectivePort()
	addr := fmt.Sprintf("127.0.0.1:%d", port)
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		conn, err := net.DialTimeout("tcp", addr, 200*time.Millisecond)
		if err != nil {
			log.Printf("[process] port %d is free", port)
			return nil
		}
		conn.Close()
		log.Printf("[process] port %d still in use, waiting…", port)
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(300 * time.Millisecond):
		}
	}
	return fmt.Errorf("port %d still in use after %s", port, timeout)
}

// Status returns the current process state.
func (m *Manager) Status() domain.ProcessStatus {
	m.mu.Lock()
	defer m.mu.Unlock()

	status := domain.ProcessStatus{State: m.state}
	if m.pid != 0 {
		status.PID = m.pid
	}
	if m.startedAt != nil {
		t := m.startedAt.UnixMilli()
		status.StartedAt = &t
		status.UptimeSec = int64(time.Since(*m.startedAt).Seconds())
	}
	return status
}

// IsRunning reports whether the opencode process is currently running.
func (m *Manager) IsRunning() bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.state == domain.ProcessStateRunning
}

// Logs returns the last n captured log lines from the opencode process.
func (m *Manager) Logs(n int) []string {
	m.logMu.RLock()
	defer m.logMu.RUnlock()

	if n <= 0 || n >= len(m.logLines) {
		result := make([]string, len(m.logLines))
		copy(result, m.logLines)
		return result
	}

	start := len(m.logLines) - n
	result := make([]string, n)
	copy(result, m.logLines[start:])
	return result
}

// watch waits for the process to exit and handles auto-restart logic.
func (m *Manager) watch(ctx context.Context) {
	cmd := m.cmd
	err := cmd.Wait()

	m.mu.Lock()
	// intentionallyStopped is true when Stop() already set state to Stopping or Stopped
	intentionallyStopped := m.state == domain.ProcessStateStopping || m.state == domain.ProcessStateStopped
	m.state = domain.ProcessStateStopped
	m.pid = 0
	m.startedAt = nil
	m.mu.Unlock()

	if intentionallyStopped || ctx.Err() != nil {
		log.Println("[process] opencode exited cleanly")
		return
	}

	log.Printf("[process] opencode exited unexpectedly (port %d): %v — restarting in 3s", m.effectivePort(), err)
	time.Sleep(3 * time.Second)

	if restartErr := m.Start(ctx); restartErr != nil {
		log.Printf("[process] auto-restart failed: %v", restartErr)
	}
}

// appendLog adds a line to the ring buffer.
func (m *Manager) appendLog(line string) {
	m.logMu.Lock()
	defer m.logMu.Unlock()

	m.logLines = append(m.logLines, line)
	if len(m.logLines) > maxLogLines {
		m.logLines = m.logLines[len(m.logLines)-maxLogLines:]
	}
}

// logWriter implements io.Writer for capturing subprocess output.
type logWriter struct {
	m      *Manager
	prefix string
	buf    []byte
}

func (w *logWriter) Write(p []byte) (int, error) {
	w.buf = append(w.buf, p...)
	for {
		nl := -1
		for i, b := range w.buf {
			if b == '\n' {
				nl = i
				break
			}
		}
		if nl < 0 {
			break
		}
		line := string(w.buf[:nl])
		w.buf = w.buf[nl+1:]
		full := w.prefix + line
		log.Println(full)
		w.m.appendLog(full)
	}
	return len(p), nil
}
