package handler

import (
	"context"
	"fmt"
	"net/http"
	"sync/atomic"
	"time"

	"github.com/gin-gonic/gin"
	opencodeRepo "github.com/igniteLabs/opencode-gateway/internal/repository/opencode"
	"github.com/igniteLabs/opencode-gateway/internal/usecase"
)

// BrokerPool is the minimal interface EventHandler needs from the pool to look up brokers.
type BrokerPool interface {
	BrokerFor(worktree string) *usecase.EventBroker
}

// EventHandler handles the SSE streaming endpoint.
// It uses the pool to route to the correct EventBroker based on X-Project-Path.
type EventHandler struct {
	defaultBroker *usecase.EventBroker
	pool          BrokerPool // may be nil if pool not yet initialized
	counter       uint64     // atomic counter for unique client IDs
}

// NewEventHandler creates an EventHandler with a default broker (bootstrap project).
func NewEventHandler(broker *usecase.EventBroker) *EventHandler {
	return &EventHandler{defaultBroker: broker}
}

// SetPool wires up the pool so the handler can route by worktree.
// Called from main.go after pool.Discover() succeeds.
func (h *EventHandler) SetPool(p BrokerPool) {
	h.pool = p
}

// resolveBroker returns the correct EventBroker based on the X-Project-Path context value.
func (h *EventHandler) resolveBroker(ctx context.Context) *usecase.EventBroker {
	if h.pool != nil {
		if worktree, ok := ctx.Value(opencodeRepo.ProjectPathKey{}).(string); ok && worktree != "" {
			if b := h.pool.BrokerFor(worktree); b != nil {
				return b
			}
		}
	}
	return h.defaultBroker
}

// StreamEvents handles GET /event
// It subscribes the caller to the correct EventBroker and streams events as SSE.
func (h *EventHandler) StreamEvents(c *gin.Context) {
	broker := h.resolveBroker(c.Request.Context())

	id := atomic.AddUint64(&h.counter, 1)
	clientID := fmt.Sprintf("client-%d-%d", id, time.Now().UnixNano())

	ch := broker.Subscribe(clientID)
	defer broker.Unsubscribe(clientID)

	w := c.Writer
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("X-Accel-Buffering", "no")
	w.WriteHeader(http.StatusOK)
	w.Flush()

	if broker.IsConnected() {
		fmt.Fprintf(w, "data: {\"type\":\"gateway.connected\",\"properties\":{\"broker\":\"connected\"}}\n\n")
	} else {
		fmt.Fprintf(w, "data: {\"type\":\"gateway.connected\",\"properties\":{\"broker\":\"reconnecting\"}}\n\n")
	}
	w.Flush()

	ticker := time.NewTicker(15 * time.Second)
	defer ticker.Stop()

	clientGone := c.Request.Context().Done()

	for {
		select {
		case <-clientGone:
			return
		case <-ticker.C:
			fmt.Fprintf(w, ": ping\n\n")
			w.Flush()
		case event, ok := <-ch:
			if !ok {
				return
			}
			fmt.Fprintf(w, "data: %s\n\n", event)
			w.Flush()
		}
	}
}
