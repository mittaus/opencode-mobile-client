package usecase

import (
	"context"
	"encoding/json"
	"io"
	"net/http"

	"github.com/igniteLabs/opencode-gateway/config"
	"github.com/igniteLabs/opencode-gateway/internal/domain"
	opencodeRepo "github.com/igniteLabs/opencode-gateway/internal/repository/opencode"
)

// ─── Health ────────────────────────────────────────────────────────────────────

type HealthUseCase struct{ repo domain.OpenCodeRepository }

func NewHealthUseCase(r domain.OpenCodeRepository) *HealthUseCase { return &HealthUseCase{r} }

func (uc *HealthUseCase) GetHealth(ctx context.Context) ([]byte, int, error) {
	return uc.repo.GetHealth(ctx)
}

// ─── Project ───────────────────────────────────────────────────────────────────

type ProjectUseCase struct {
	repo                domain.OpenCodeRepository
	cfg                 *config.Config
	onProjectRegistered func(ctx context.Context) // called after a successful registration
}

func NewProjectUseCase(r domain.OpenCodeRepository, cfg *config.Config) *ProjectUseCase {
	return &ProjectUseCase{repo: r, cfg: cfg}
}

// SetOnProjectRegistered wires in a callback invoked after RegisterProject succeeds.
// Use this to trigger pool re-discovery so new projects are routed correctly.
func (uc *ProjectUseCase) SetOnProjectRegistered(fn func(ctx context.Context)) {
	uc.onProjectRegistered = fn
}

func (uc *ProjectUseCase) GetProjects(ctx context.Context) ([]byte, int, error) {
	return uc.repo.GetProjects(ctx)
}

func (uc *ProjectUseCase) GetCurrentProject(ctx context.Context) ([]byte, int, error) {
	return uc.repo.GetCurrentProject(ctx)
}

func (uc *ProjectUseCase) GetVcs(ctx context.Context) ([]byte, int, error) {
	return uc.repo.GetVcs(ctx)
}

// projectDir resolves the active project path from context (if set by the middleware)
// or falls back to the static config path for single-project mode.
// If both are empty, it asks the OpenCode instance for its current project.
func (uc *ProjectUseCase) projectDir(ctx context.Context) string {
	if dir, ok := ctx.Value(opencodeRepo.ProjectPathKey{}).(string); ok && dir != "" {
		return dir
	}
	if uc.cfg.OpenCodeProject != "" {
		return uc.cfg.OpenCodeProject
	}
	b, status, err := uc.GetCurrentProject(ctx)
	if err == nil && status == http.StatusOK {
		var p struct {
			Worktree string `json:"worktree"`
		}
		if json.Unmarshal(b, &p) == nil && p.Worktree != "" {
			return p.Worktree
		}
	}
	return ""
}

// GetVcsDiff runs "git diff HEAD" in the configured project directory and
// returns the file diffs as JSON. Each diff includes a `staged` field.
func (uc *ProjectUseCase) GetVcsDiff(ctx context.Context) ([]byte, int, error) {
	dir := uc.projectDir(ctx)
	if dir == "" {
		b, _ := json.Marshal(map[string]string{"error": "project directory not configured"})
		return b, http.StatusBadRequest, nil
	}
	diffs, err := gitDiff(dir)
	if err != nil {
		b, _ := json.Marshal(map[string]string{"error": err.Error()})
		return b, http.StatusInternalServerError, nil
	}
	if diffs == nil {
		diffs = []domain.FileDiff{}
	}
	// Mark already-staged files
	staged, _ := gitGetStagedPaths(dir)
	stagedSet := make(map[string]bool, len(staged))
	for _, p := range staged {
		stagedSet[p] = true
	}
	for i := range diffs {
		diffs[i].Staged = stagedSet[diffs[i].Path]
	}
	b, _ := json.Marshal(diffs)
	return b, http.StatusOK, nil
}

// StageFiles stages the given paths with "git add".
func (uc *ProjectUseCase) StageFiles(_ context.Context, paths []string) ([]byte, int, error) {
	dir := uc.cfg.OpenCodeProject
	if dir == "" {
		b, _ := json.Marshal(map[string]string{"error": "project directory not configured"})
		return b, http.StatusBadRequest, nil
	}
	if err := gitStage(dir, paths); err != nil {
		b, _ := json.Marshal(map[string]string{"error": err.Error()})
		return b, http.StatusInternalServerError, nil
	}
	b, _ := json.Marshal(map[string]string{"status": "ok"})
	return b, http.StatusOK, nil
}

// DiscardChanges discards working-tree changes for a single file.
func (uc *ProjectUseCase) DiscardChanges(_ context.Context, path, status string) ([]byte, int, error) {
	dir := uc.cfg.OpenCodeProject
	if dir == "" {
		b, _ := json.Marshal(map[string]string{"error": "project directory not configured"})
		return b, http.StatusBadRequest, nil
	}
	if err := gitDiscard(dir, path, status); err != nil {
		b, _ := json.Marshal(map[string]string{"error": err.Error()})
		return b, http.StatusInternalServerError, nil
	}
	b, _ := json.Marshal(map[string]string{"status": "ok"})
	return b, http.StatusOK, nil
}

// UnstageFiles unstages the given paths with "git restore --staged".
func (uc *ProjectUseCase) UnstageFiles(_ context.Context, paths []string) ([]byte, int, error) {
	dir := uc.cfg.OpenCodeProject
	if dir == "" {
		b, _ := json.Marshal(map[string]string{"error": "project directory not configured"})
		return b, http.StatusBadRequest, nil
	}
	if err := gitUnstage(dir, paths); err != nil {
		b, _ := json.Marshal(map[string]string{"error": err.Error()})
		return b, http.StatusInternalServerError, nil
	}
	b, _ := json.Marshal(map[string]string{"status": "ok"})
	return b, http.StatusOK, nil
}

// Commit creates a commit with the given message.
func (uc *ProjectUseCase) Commit(_ context.Context, message string) ([]byte, int, error) {
	dir := uc.cfg.OpenCodeProject
	if dir == "" {
		b, _ := json.Marshal(map[string]string{"error": "project directory not configured"})
		return b, http.StatusBadRequest, nil
	}
	if err := gitCommit(dir, message); err != nil {
		b, _ := json.Marshal(map[string]string{"error": err.Error()})
		return b, http.StatusInternalServerError, nil
	}
	b, _ := json.Marshal(map[string]string{"status": "ok"})
	return b, http.StatusOK, nil
}

// RegisterProject ensures the directory is a git repo and creates an OpenCode session for it.
// After success it calls onProjectRegistered (if set) so the pool can rediscover projects.
func (uc *ProjectUseCase) RegisterProject(ctx context.Context, directory string) ([]byte, int, error) {
	body, status, err := uc.repo.RegisterProject(ctx, directory)
	if err == nil && status >= 200 && status < 300 && uc.onProjectRegistered != nil {
		go uc.onProjectRegistered(context.Background())
	}
	return body, status, err
}

// ─── Provider ──────────────────────────────────────────────────────────────────

type ProviderUseCase struct{ repo domain.OpenCodeRepository }

func NewProviderUseCase(r domain.OpenCodeRepository) *ProviderUseCase { return &ProviderUseCase{r} }

func (uc *ProviderUseCase) GetProviders(ctx context.Context) ([]byte, int, error) {
	return uc.repo.GetProviders(ctx)
}

// ─── Session ───────────────────────────────────────────────────────────────────

type SessionUseCase struct{ repo domain.OpenCodeRepository }

func NewSessionUseCase(r domain.OpenCodeRepository) *SessionUseCase { return &SessionUseCase{r} }

func (uc *SessionUseCase) GetSessions(ctx context.Context, project string) ([]byte, int, error) {
	return uc.repo.GetSessions(ctx, project)
}

func (uc *SessionUseCase) CreateSession(ctx context.Context, body io.Reader, directory string) ([]byte, int, error) {
	return uc.repo.CreateSession(ctx, body, directory)
}

func (uc *SessionUseCase) DeleteSession(ctx context.Context, id string) ([]byte, int, error) {
	return uc.repo.DeleteSession(ctx, id)
}

func (uc *SessionUseCase) AbortSession(ctx context.Context, id string) ([]byte, int, error) {
	return uc.repo.AbortSession(ctx, id)
}

func (uc *SessionUseCase) GetTodo(ctx context.Context, sessionID string) ([]byte, int, error) {
	return uc.repo.GetTodo(ctx, sessionID)
}

func (uc *SessionUseCase) GetDiff(ctx context.Context, sessionID, messageID string) ([]byte, int, error) {
	return uc.repo.GetDiff(ctx, sessionID, messageID)
}

func (uc *SessionUseCase) RevertMessage(ctx context.Context, sessionID string, body io.Reader) ([]byte, int, error) {
	return uc.repo.RevertMessage(ctx, sessionID, body)
}

func (uc *SessionUseCase) RespondToPermission(ctx context.Context, sessionID, permID string, body io.Reader) ([]byte, int, error) {
	return uc.repo.RespondToPermission(ctx, sessionID, permID, body)
}

// ─── Message ───────────────────────────────────────────────────────────────────

type MessageUseCase struct{ repo domain.OpenCodeRepository }

func NewMessageUseCase(r domain.OpenCodeRepository) *MessageUseCase { return &MessageUseCase{r} }

func (uc *MessageUseCase) GetMessages(ctx context.Context, sessionID string) ([]byte, int, error) {
	return uc.repo.GetMessages(ctx, sessionID)
}

func (uc *MessageUseCase) SendMessage(ctx context.Context, sessionID string, body io.Reader) ([]byte, int, error) {
	return uc.repo.SendMessage(ctx, sessionID, body)
}

func (uc *MessageUseCase) SendMessageAsync(ctx context.Context, sessionID string, body io.Reader) ([]byte, int, error) {
	return uc.repo.SendMessageAsync(ctx, sessionID, body)
}

func (uc *MessageUseCase) ExecuteCommand(ctx context.Context, sessionID string, body io.Reader) ([]byte, int, error) {
	return uc.repo.ExecuteCommand(ctx, sessionID, body)
}

// ─── Process ───────────────────────────────────────────────────────────────────

type ProcessUseCase struct{ repo domain.ProcessRepository }

func NewProcessUseCase(r domain.ProcessRepository) *ProcessUseCase { return &ProcessUseCase{r} }

func (uc *ProcessUseCase) Start(ctx context.Context) error                    { return uc.repo.Start(ctx) }
func (uc *ProcessUseCase) Stop(ctx context.Context) error                     { return uc.repo.Stop(ctx) }
func (uc *ProcessUseCase) Restart(ctx context.Context) error                  { return uc.repo.Restart(ctx) }
func (uc *ProcessUseCase) Switch(ctx context.Context, directory string) error { return uc.repo.Switch(ctx, directory) }
func (uc *ProcessUseCase) Status() domain.ProcessStatus                       { return uc.repo.Status() }
func (uc *ProcessUseCase) IsRunning() bool                                    { return uc.repo.IsRunning() }
func (uc *ProcessUseCase) Logs(n int) []string                                { return uc.repo.Logs(n) }
