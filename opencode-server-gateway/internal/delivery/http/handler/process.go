package handler

import (
	"context"
	"log"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/igniteLabs/opencode-gateway/internal/usecase"
)

// ProcessHandler exposes process management endpoints (gateway-specific, not in OpenCode).
type ProcessHandler struct {
	uc *usecase.ProcessUseCase
}

func NewProcessHandler(uc *usecase.ProcessUseCase) *ProcessHandler {
	return &ProcessHandler{uc: uc}
}

// StartProcess handles POST /process/start
func (h *ProcessHandler) StartProcess(c *gin.Context) {
	// Use context.Background() so the opencode subprocess outlives this HTTP request.
	if err := h.uc.Start(context.Background()); err != nil {
		c.JSON(http.StatusConflict, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"message": "opencode started"})
}

// StopProcess handles POST /process/stop
func (h *ProcessHandler) StopProcess(c *gin.Context) {
	if err := h.uc.Stop(c.Request.Context()); err != nil {
		c.JSON(http.StatusConflict, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"message": "opencode stopped"})
}

// RestartProcess handles POST /process/restart
func (h *ProcessHandler) RestartProcess(c *gin.Context) {
	if err := h.uc.Restart(c.Request.Context()); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"message": "opencode restarted"})
}

// GetProcessStatus handles GET /process/status
func (h *ProcessHandler) GetProcessStatus(c *gin.Context) {
	c.JSON(http.StatusOK, h.uc.Status())
}

// SwitchProcess handles POST /process/switch?directory=<path>
func (h *ProcessHandler) SwitchProcess(c *gin.Context) {
	dir := c.Query("directory")
	log.Printf("[handler] SwitchProcess dir=%q", dir)
	if dir == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "directory query param is required"})
		return
	}
	if err := h.uc.Switch(c.Request.Context(), dir); err != nil {
		log.Printf("[handler] SwitchProcess ✗ %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	log.Printf("[handler] SwitchProcess ✓ dir=%q", dir)
	c.JSON(http.StatusOK, gin.H{"message": "opencode switched", "directory": dir})
}

// GetProcessLogs handles GET /process/logs[?n=100]
func (h *ProcessHandler) GetProcessLogs(c *gin.Context) {
	n := 100
	if q := c.Query("n"); q != "" {
		if v, err := strconv.Atoi(q); err == nil && v > 0 {
			n = v
		}
	}
	lines := h.uc.Logs(n)
	c.JSON(http.StatusOK, gin.H{
		"lines": lines,
		"count": len(lines),
	})
}
