package domain

import (
	"context"
	"io"
)

// OpenCodeRepository defines the contract for communicating with the OpenCode CLI server.
// Methods return raw response bytes and the HTTP status code from OpenCode so handlers
// can proxy them transparently to the mobile client.
type OpenCodeRepository interface {
	// Health
	GetHealth(ctx context.Context) ([]byte, int, error)

	// Projects
	GetProjects(ctx context.Context) ([]byte, int, error)
	GetCurrentProject(ctx context.Context) ([]byte, int, error)
	GetVcs(ctx context.Context) ([]byte, int, error)
	RegisterProject(ctx context.Context, directory string) ([]byte, int, error)

	// Providers
	GetProviders(ctx context.Context) ([]byte, int, error)

	// Sessions
	GetSessions(ctx context.Context, directory string) ([]byte, int, error)
	CreateSession(ctx context.Context, body io.Reader, directory string) ([]byte, int, error)
	DeleteSession(ctx context.Context, id string) ([]byte, int, error)
	AbortSession(ctx context.Context, id string) ([]byte, int, error)

	// Messages
	GetMessages(ctx context.Context, sessionID string) ([]byte, int, error)
	SendMessage(ctx context.Context, sessionID string, body io.Reader) ([]byte, int, error)
	SendMessageAsync(ctx context.Context, sessionID string, body io.Reader) ([]byte, int, error)
	ExecuteCommand(ctx context.Context, sessionID string, body io.Reader) ([]byte, int, error)

	// Session extras
	GetTodo(ctx context.Context, sessionID string) ([]byte, int, error)
	GetDiff(ctx context.Context, sessionID string, messageID string) ([]byte, int, error)
	RevertMessage(ctx context.Context, sessionID string, body io.Reader) ([]byte, int, error)
	RespondToPermission(ctx context.Context, sessionID, permissionID string, body io.Reader) ([]byte, int, error)

	// SSE — returns a channel that emits raw JSON event strings.
	// The caller is responsible for closing ctx to stop streaming.
	StreamEvents(ctx context.Context) (<-chan string, error)
}

// ProcessRepository manages the lifecycle of the opencode CLI subprocess.
type ProcessRepository interface {
	Start(ctx context.Context) error
	Stop(ctx context.Context) error
	Restart(ctx context.Context) error
	// Switch stops opencode, changes the working directory, and restarts it.
	Switch(ctx context.Context, directory string) error
	Status() ProcessStatus
	IsRunning() bool
	// Logs returns the last n lines from the opencode process output.
	Logs(n int) []string
}
