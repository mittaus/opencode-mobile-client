package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/igniteLabs/opencode-gateway/internal/usecase"
)

type registerProjectRequest struct {
	Directory string `json:"directory" binding:"required"`
}

// RegisterProjectHandler handles POST /project/register.
type RegisterProjectHandler struct {
	uc *usecase.ProjectUseCase
}

func NewRegisterProjectHandler(uc *usecase.ProjectUseCase) *RegisterProjectHandler {
	return &RegisterProjectHandler{uc: uc}
}

// RegisterProject ensures the directory is a git repo, creates an OpenCode session,
// and returns {"project": <session object from OpenCode>}.
func (h *RegisterProjectHandler) RegisterProject(c *gin.Context) {
	var req registerProjectRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	body, status, err := h.uc.RegisterProject(c.Request.Context(), req.Directory)
	if err != nil {
		c.JSON(http.StatusBadGateway, gin.H{"error": err.Error()})
		return
	}

	if status < 200 || status >= 300 {
		c.JSON(status, gin.H{"error": string(body)})
		return
	}

	c.Data(http.StatusOK, "application/json", body)
}
