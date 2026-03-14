package handler

import (
	"bytes"
	"io"
	"log"

	"github.com/gin-gonic/gin"
	"github.com/igniteLabs/opencode-gateway/internal/usecase"
)

// MessageHandler handles message-related routes.
type MessageHandler struct {
	uc *usecase.MessageUseCase
}

func NewMessageHandler(uc *usecase.MessageUseCase) *MessageHandler {
	return &MessageHandler{uc: uc}
}

// GetMessages proxies GET /session/:id/message
func (h *MessageHandler) GetMessages(c *gin.Context) {
	body, status, err := h.uc.GetMessages(c.Request.Context(), c.Param("id"))
	proxy(c, body, status, err)
}

// SendMessage proxies POST /session/:id/message
func (h *MessageHandler) SendMessage(c *gin.Context) {
	sessionID := c.Param("id")
	reqBody, _ := io.ReadAll(c.Request.Body)
	preview := string(reqBody)
	if len(preview) > 200 {
		preview = preview[:200] + "…"
	}
	log.Printf("[handler] SendMessage session=%s body=%s", sessionID, preview)

	body, status, err := h.uc.SendMessage(c.Request.Context(), sessionID, bytes.NewReader(reqBody))
	if err != nil {
		log.Printf("[handler] SendMessage ✗ session=%s status=%d err=%v", sessionID, status, err)
	} else {
		respPreview := string(body)
		if len(respPreview) > 200 {
			respPreview = respPreview[:200] + "…"
		}
		log.Printf("[handler] SendMessage ✓ session=%s status=%d resp=%s", sessionID, status, respPreview)
	}
	proxy(c, body, status, err)
}

// SendMessageAsync proxies POST /session/:id/prompt_async
func (h *MessageHandler) SendMessageAsync(c *gin.Context) {
	sessionID := c.Param("id")
	reqBody, _ := io.ReadAll(c.Request.Body)
	preview := string(reqBody)
	if len(preview) > 300 {
		preview = preview[:300] + "…"
	}
	log.Printf("[handler] SendMessageAsync session=%s body=%s", sessionID, preview)
	body, status, err := h.uc.SendMessageAsync(c.Request.Context(), sessionID, bytes.NewReader(reqBody))
	if err != nil {
		log.Printf("[handler] SendMessageAsync ✗ status=%d err=%v", status, err)
	}
	proxy(c, body, status, err)
}

// ExecuteCommand proxies POST /session/:id/command
func (h *MessageHandler) ExecuteCommand(c *gin.Context) {
	body, status, err := h.uc.ExecuteCommand(c.Request.Context(), c.Param("id"), c.Request.Body)
	proxy(c, body, status, err)
}
