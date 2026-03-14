package handler

import (
	"log"
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/igniteLabs/opencode-gateway/internal/usecase"
)

type stageRequest struct {
	Paths []string `json:"paths"`
}

type commitRequest struct {
	Message string `json:"message"`
}

type discardRequest struct {
	Path   string `json:"path"`
	Status string `json:"status"` // added | modified | deleted
}

// ─── Project Handler ───────────────────────────────────────────────────────────

type ProjectHandler struct {
	uc *usecase.ProjectUseCase
}

func NewProjectHandler(uc *usecase.ProjectUseCase) *ProjectHandler {
	return &ProjectHandler{uc: uc}
}

// GetProjects proxies GET /project
func (h *ProjectHandler) GetProjects(c *gin.Context) {
	body, status, err := h.uc.GetProjects(c.Request.Context())
	proxy(c, body, status, err)
}

// GetCurrentProject proxies GET /project/current
func (h *ProjectHandler) GetCurrentProject(c *gin.Context) {
	body, status, err := h.uc.GetCurrentProject(c.Request.Context())
	if err == nil {
		preview := string(body)
		if len(preview) > 300 {
			preview = preview[:300]
		}
		log.Printf("[handler] GetCurrentProject status=%d body=%s", status, preview)
	}
	proxy(c, body, status, err)
}

// GetVcs proxies GET /vcs
func (h *ProjectHandler) GetVcs(c *gin.Context) {
	body, status, err := h.uc.GetVcs(c.Request.Context())
	proxy(c, body, status, err)
}

// GetVcsDiff runs git diff HEAD in the project directory and returns file diffs.
func (h *ProjectHandler) GetVcsDiff(c *gin.Context) {
	body, status, err := h.uc.GetVcsDiff(c.Request.Context())
	proxy(c, body, status, err)
}

// StageFiles stages the requested paths via git add.
func (h *ProjectHandler) StageFiles(c *gin.Context) {
	var req stageRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	body, status, err := h.uc.StageFiles(c.Request.Context(), req.Paths)
	proxy(c, body, status, err)
}

// DiscardChanges discards working-tree changes for a file.
func (h *ProjectHandler) DiscardChanges(c *gin.Context) {
	var req discardRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	body, status, err := h.uc.DiscardChanges(c.Request.Context(), req.Path, req.Status)
	proxy(c, body, status, err)
}

// UnstageFiles unstages the requested paths.
func (h *ProjectHandler) UnstageFiles(c *gin.Context) {
	var req stageRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	body, status, err := h.uc.UnstageFiles(c.Request.Context(), req.Paths)
	proxy(c, body, status, err)
}

// Commit creates a git commit with the given message.
func (h *ProjectHandler) Commit(c *gin.Context) {
	var req commitRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	if req.Message == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "message is required"})
		return
	}
	body, status, err := h.uc.Commit(c.Request.Context(), req.Message)
	proxy(c, body, status, err)
}

// ─── Provider Handler ──────────────────────────────────────────────────────────

type ProviderHandler struct {
	uc *usecase.ProviderUseCase
}

func NewProviderHandler(uc *usecase.ProviderUseCase) *ProviderHandler {
	return &ProviderHandler{uc: uc}
}

// GetProviders proxies GET /provider
func (h *ProviderHandler) GetProviders(c *gin.Context) {
	body, status, err := h.uc.GetProviders(c.Request.Context())
	proxy(c, body, status, err)
}

// ─── Shared proxy helper ───────────────────────────────────────────────────────

// proxy writes the (body, statusCode, error) from a use case directly to the response.
// No re-serialization needed — raw JSON bytes from OpenCode pass through as-is.
func proxy(c *gin.Context, body []byte, statusCode int, err error) {
	if err != nil {
		c.JSON(http.StatusBadGateway, gin.H{"error": err.Error()})
		return
	}
	c.Data(statusCode, "application/json", body)
}
