package handler

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/igniteLabs/opencode-gateway/internal/pool"
)

// PoolHandler exposes the project pool state and per-project process management over HTTP.
type PoolHandler struct {
	pool *pool.Pool
}

// NewPoolHandler creates a new PoolHandler.
func NewPoolHandler(p *pool.Pool) *PoolHandler {
	return &PoolHandler{pool: p}
}

// ListProjects handles GET /projects — returns all discovered projects with live process status.
// If the pool is empty (e.g., OpenCode wasn't running when the gateway started), it attempts
// a lazy re-discovery before responding.
func (h *PoolHandler) ListProjects(c *gin.Context) {
	h.pool.RediscoverIfEmpty(c.Request.Context())
	c.JSON(http.StatusOK, h.pool.List())
}

// GetProcessStatus handles GET /projects/:idx/process/status
func (h *PoolHandler) GetProcessStatus(c *gin.Context) {
	idx, err := strconv.Atoi(c.Param("idx"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid project index"})
		return
	}
	status, ok := h.pool.StatusAt(idx)
	if !ok {
		c.JSON(http.StatusNotFound, gin.H{"error": "project not found"})
		return
	}
	c.JSON(http.StatusOK, status)
}

// StartProcess handles POST /projects/:idx/process/start
func (h *PoolHandler) StartProcess(c *gin.Context) {
	idx, err := strconv.Atoi(c.Param("idx"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid project index"})
		return
	}
	if err := h.pool.StartAt(c.Request.Context(), idx); err != nil {
		c.JSON(http.StatusConflict, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"status": "started"})
}

// StopProcess handles POST /projects/:idx/process/stop
func (h *PoolHandler) StopProcess(c *gin.Context) {
	idx, err := strconv.Atoi(c.Param("idx"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid project index"})
		return
	}
	if err := h.pool.StopAt(c.Request.Context(), idx); err != nil {
		c.JSON(http.StatusConflict, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"status": "stopped"})
}
