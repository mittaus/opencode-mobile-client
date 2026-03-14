package handler

import (
	"bytes"
	"io"
	"log"

	"github.com/gin-gonic/gin"
	"github.com/igniteLabs/opencode-gateway/internal/usecase"
)

// SessionHandler handles all session-related routes.
type SessionHandler struct {
	uc *usecase.SessionUseCase
}

func NewSessionHandler(uc *usecase.SessionUseCase) *SessionHandler {
	return &SessionHandler{uc: uc}
}

// GetSessions proxies GET /session[?project=<path>]
func (h *SessionHandler) GetSessions(c *gin.Context) {
	body, status, err := h.uc.GetSessions(c.Request.Context(), c.Query("project"))
	proxy(c, body, status, err)
}

// CreateSession proxies POST /session[?directory=<path>]
func (h *SessionHandler) CreateSession(c *gin.Context) {
	dir := c.Query("directory")
	reqBody, _ := io.ReadAll(c.Request.Body)
	log.Printf("[handler] CreateSession directory=%q req=%s", dir, string(reqBody))
	body, status, err := h.uc.CreateSession(c.Request.Context(), bytes.NewReader(reqBody), dir)
	if err == nil {
		preview := string(body)
		if len(preview) > 200 { preview = preview[:200] }
		log.Printf("[handler] CreateSession ✓ status=%d body=%s", status, preview)
	} else {
		log.Printf("[handler] CreateSession ✗ status=%d err=%v", status, err)
	}
	proxy(c, body, status, err)
}

// DeleteSession proxies DELETE /session/:id
func (h *SessionHandler) DeleteSession(c *gin.Context) {
	body, status, err := h.uc.DeleteSession(c.Request.Context(), c.Param("id"))
	proxy(c, body, status, err)
}

// AbortSession proxies POST /session/:id/abort
func (h *SessionHandler) AbortSession(c *gin.Context) {
	body, status, err := h.uc.AbortSession(c.Request.Context(), c.Param("id"))
	proxy(c, body, status, err)
}

// GetTodo proxies GET /session/:id/todo
func (h *SessionHandler) GetTodo(c *gin.Context) {
	body, status, err := h.uc.GetTodo(c.Request.Context(), c.Param("id"))
	proxy(c, body, status, err)
}

// GetDiff proxies GET /session/:id/diff[?messageID=...]
func (h *SessionHandler) GetDiff(c *gin.Context) {
	body, status, err := h.uc.GetDiff(c.Request.Context(), c.Param("id"), c.Query("messageID"))
	proxy(c, body, status, err)
}

// RevertMessage proxies POST /session/:id/revert
func (h *SessionHandler) RevertMessage(c *gin.Context) {
	body, status, err := h.uc.RevertMessage(c.Request.Context(), c.Param("id"), c.Request.Body)
	proxy(c, body, status, err)
}

// RespondToPermission proxies POST /session/:id/permissions/:permId
func (h *SessionHandler) RespondToPermission(c *gin.Context) {
	body, status, err := h.uc.RespondToPermission(
		c.Request.Context(),
		c.Param("id"),
		c.Param("permId"),
		c.Request.Body,
	)
	proxy(c, body, status, err)
}
