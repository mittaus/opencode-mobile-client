package domain

import "encoding/json"

// ─── Health ────────────────────────────────────────────────────────────────────

type Health struct {
	Healthy bool   `json:"healthy"`
	Version string `json:"version"`
}

// ─── Project ───────────────────────────────────────────────────────────────────

type Project struct {
	ID   string   `json:"id"`
	Path string   `json:"path"`
	Git  *GitInfo `json:"git,omitempty"`
}

type GitInfo struct {
	Branch *string `json:"branch,omitempty"`
	Remote *string `json:"remote,omitempty"`
}

type VcsInfo struct {
	Branch *string `json:"branch,omitempty"`
	Remote *string `json:"remote,omitempty"`
}

// ─── Provider ──────────────────────────────────────────────────────────────────

type ProviderList struct {
	All       []Provider `json:"all"`
	Connected []string   `json:"connected"`
}

type Provider struct {
	ID     string  `json:"id"`
	Name   string  `json:"name"`
	Models []Model `json:"models"`
}

type Model struct {
	ID            string     `json:"id"`
	Name          string     `json:"name"`
	Cost          *ModelCost `json:"cost,omitempty"`
	ContextWindow int        `json:"context"`
}

type ModelCost struct {
	Input  float64 `json:"input"`
	Output float64 `json:"output"`
}

// ─── Session ───────────────────────────────────────────────────────────────────

type Session struct {
	ID        string   `json:"id"`
	Title     string   `json:"title"`
	Directory string   `json:"directory"`
	Path      string   `json:"path"`
	Model     string   `json:"model"`
	Provider  string   `json:"provider"`
	Time      TimePair `json:"time"`
	Share     *string  `json:"share,omitempty"`
}

type TimePair struct {
	Created int64 `json:"created"`
	Updated int64 `json:"updated"`
}

// ─── Message ───────────────────────────────────────────────────────────────────

type MessageListItem struct {
	Info  MessageInfo `json:"info"`
	Parts []Part      `json:"parts"`
}

type MessageInfo struct {
	ID        string      `json:"id"`
	SessionID string      `json:"sessionID"`
	Role      string      `json:"role"`
	Time      TimePair    `json:"time"`
	Tokens    *TokenUsage `json:"tokens,omitempty"`
}

type TokenUsage struct {
	Input         int `json:"input"`
	Output        int `json:"output"`
	CacheRead     int `json:"cache_read"`
	CacheCreation int `json:"cache_creation"`
}

type Part struct {
	Type           string          `json:"type"`
	Text           *string         `json:"text,omitempty"`
	ToolInvocation *ToolInvocation `json:"toolInvocation,omitempty"`
	Title          *string         `json:"title,omitempty"`
}

type ToolInvocation struct {
	ToolCallID string          `json:"toolCallId"`
	ToolName   string          `json:"toolName"`
	Args       json.RawMessage `json:"args,omitempty"`
	State      string          `json:"state"` // call | partial-call | result
	Result     json.RawMessage `json:"result,omitempty"`
	IsError    bool            `json:"isError"`
}

// ─── FileDiff ──────────────────────────────────────────────────────────────────

type FileDiff struct {
	Path      string `json:"path"`
	Status    string `json:"status"` // modified | added | deleted | renamed
	Additions int    `json:"additions"`
	Deletions int    `json:"deletions"`
	Patch     string `json:"patch"`
	Staged    bool   `json:"staged"`
}

// ─── Todo ──────────────────────────────────────────────────────────────────────

type TodoItem struct {
	ID       string `json:"id"`
	Content  string `json:"content"`
	Status   string `json:"status"`   // pending | in_progress | completed
	Priority string `json:"priority"` // low | medium | high
}

// ─── Permission ────────────────────────────────────────────────────────────────

type PermissionRequest struct {
	ID          string  `json:"id"`
	SessionID   string  `json:"sessionID"`
	ToolName    string  `json:"toolName"`
	Description string  `json:"description"`
	Command     *string `json:"command,omitempty"`
	FilePath    *string `json:"filePath,omitempty"`
}

type PermissionResponse struct {
	Response string `json:"response"` // "allow" | "deny"
	Remember bool   `json:"remember"`
}

// ─── Process ───────────────────────────────────────────────────────────────────

type ProcessState string

const (
	ProcessStateStopped  ProcessState = "stopped"
	ProcessStateStarting ProcessState = "starting"
	ProcessStateRunning  ProcessState = "running"
	ProcessStateStopping ProcessState = "stopping"
)

type ProcessStatus struct {
	State     ProcessState `json:"state"`
	PID       int          `json:"pid,omitempty"`
	StartedAt *int64       `json:"startedAt,omitempty"`
	UptimeSec int64        `json:"uptimeSec,omitempty"`
}

// ─── Project Pool ──────────────────────────────────────────────────────────────

// ProjectEntry represents a single discovered OpenCode project in the pool.
type ProjectEntry struct {
	Worktree string       `json:"worktree"`
	Port     int          `json:"port"`
	Status   ProcessState `json:"status"`
}
