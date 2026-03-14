package opencode

import (
	"context"
	"io"
	"log"
	"sync"
)

// ProjectPathKey is the context key used by the project middleware to store
// the active project worktree path for request-scoped routing.
type ProjectPathKey struct{}

// RoutingClient implements domain.OpenCodeRepository by dynamically selecting
// the correct per-project *Client based on the ProjectPathKey stored in the
// request context (set by the project middleware from X-Project-Path header).
//
// If no project path is in context, or the worktree is not found in the pool,
// the fallback client (the bootstrap instance at port 4096) is used — maintaining
// full backward compatibility with clients that don't send X-Project-Path.
type RoutingClient struct {
	mu       sync.RWMutex
	pool     ClientPool
	fallback *Client
}

// ClientPool is a minimal interface the RoutingClient needs from the project pool.
// Using an interface avoids circular imports between opencode and pool packages.
type ClientPool interface {
	ClientFor(worktree string) *Client
}

// NewRoutingClient creates a RoutingClient backed by an optional pool with a fallback client.
// pool may be nil initially; use SetPool() after Discover() to wire it in.
func NewRoutingClient(pool ClientPool, fallback *Client) *RoutingClient {
	return &RoutingClient{pool: pool, fallback: fallback}
}

// SetPool wires in the project pool after discovery. Thread-safe.
func (r *RoutingClient) SetPool(pool ClientPool) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.pool = pool
}

// resolve returns the right *Client for this request context.
func (r *RoutingClient) resolve(ctx context.Context) *Client {
	r.mu.RLock()
	p := r.pool
	r.mu.RUnlock()

	if p != nil {
		if worktree, ok := ctx.Value(ProjectPathKey{}).(string); ok && worktree != "" {
			if c := p.ClientFor(worktree); c != nil {
				log.Printf("[routing] X-Project-Path=%q → matched pool client", worktree)
				return c
			}
			log.Printf("[routing] X-Project-Path=%q → NOT found in pool, falling back to bootstrap", worktree)
		}
	}
	return r.fallback
}

// ─── OpenCodeRepository implementation — delegate to resolved client ──────────

func (r *RoutingClient) GetHealth(ctx context.Context) ([]byte, int, error) {
	return r.resolve(ctx).GetHealth(ctx)
}
func (r *RoutingClient) GetProjects(ctx context.Context) ([]byte, int, error) {
	return r.resolve(ctx).GetProjects(ctx)
}
func (r *RoutingClient) GetCurrentProject(ctx context.Context) ([]byte, int, error) {
	return r.resolve(ctx).GetCurrentProject(ctx)
}
func (r *RoutingClient) GetVcs(ctx context.Context) ([]byte, int, error) {
	return r.resolve(ctx).GetVcs(ctx)
}
func (r *RoutingClient) GetProviders(ctx context.Context) ([]byte, int, error) {
	return r.resolve(ctx).GetProviders(ctx)
}
func (r *RoutingClient) GetSessions(ctx context.Context, directory string) ([]byte, int, error) {
	return r.resolve(ctx).GetSessions(ctx, directory)
}
func (r *RoutingClient) CreateSession(ctx context.Context, body io.Reader, directory string) ([]byte, int, error) {
	return r.resolve(ctx).CreateSession(ctx, body, directory)
}
func (r *RoutingClient) DeleteSession(ctx context.Context, id string) ([]byte, int, error) {
	return r.resolve(ctx).DeleteSession(ctx, id)
}
func (r *RoutingClient) AbortSession(ctx context.Context, id string) ([]byte, int, error) {
	return r.resolve(ctx).AbortSession(ctx, id)
}
func (r *RoutingClient) GetMessages(ctx context.Context, sessionID string) ([]byte, int, error) {
	return r.resolve(ctx).GetMessages(ctx, sessionID)
}
func (r *RoutingClient) SendMessage(ctx context.Context, sessionID string, body io.Reader) ([]byte, int, error) {
	return r.resolve(ctx).SendMessage(ctx, sessionID, body)
}
func (r *RoutingClient) SendMessageAsync(ctx context.Context, sessionID string, body io.Reader) ([]byte, int, error) {
	return r.resolve(ctx).SendMessageAsync(ctx, sessionID, body)
}
func (r *RoutingClient) ExecuteCommand(ctx context.Context, sessionID string, body io.Reader) ([]byte, int, error) {
	return r.resolve(ctx).ExecuteCommand(ctx, sessionID, body)
}
func (r *RoutingClient) GetTodo(ctx context.Context, sessionID string) ([]byte, int, error) {
	return r.resolve(ctx).GetTodo(ctx, sessionID)
}
func (r *RoutingClient) GetDiff(ctx context.Context, sessionID, messageID string) ([]byte, int, error) {
	return r.resolve(ctx).GetDiff(ctx, sessionID, messageID)
}
func (r *RoutingClient) RevertMessage(ctx context.Context, sessionID string, body io.Reader) ([]byte, int, error) {
	return r.resolve(ctx).RevertMessage(ctx, sessionID, body)
}
func (r *RoutingClient) RespondToPermission(ctx context.Context, sessionID, permissionID string, body io.Reader) ([]byte, int, error) {
	return r.resolve(ctx).RespondToPermission(ctx, sessionID, permissionID, body)
}
func (r *RoutingClient) StreamEvents(ctx context.Context) (<-chan string, error) {
	return r.resolve(ctx).StreamEvents(ctx)
}

// RegisterProject always uses the fallback (bootstrap) client because the project
// being registered does not yet have its own per-project instance in the pool.
func (r *RoutingClient) RegisterProject(ctx context.Context, directory string) ([]byte, int, error) {
	return r.fallback.RegisterProject(ctx, directory)
}
