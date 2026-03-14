package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/igniteLabs/opencode-gateway/internal/usecase"
)

// ─── Health Handler ────────────────────────────────────────────────────────────

type HealthHandler struct {
	uc *usecase.HealthUseCase
}

func NewHealthHandler(uc *usecase.HealthUseCase) *HealthHandler {
	return &HealthHandler{uc: uc}
}

// GetHealth proxies GET /global/health
func (h *HealthHandler) GetHealth(c *gin.Context) {
	body, status, err := h.uc.GetHealth(c.Request.Context())
	if err != nil {
		c.JSON(http.StatusBadGateway, gin.H{"error": err.Error()})
		return
	}
	c.Data(status, "application/json", body)
}

// GatewayHealth returns the gateway's own health (not OpenCode's).
func (h *HealthHandler) GatewayHealth(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{
		"status":  "ok",
		"service": "opencode-gateway",
	})
}
