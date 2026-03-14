package usecase

import (
	"context"
	"log"
	"sync"
	"time"

	"github.com/igniteLabs/opencode-gateway/internal/domain"
)

const (
	brokerInitialBackoff = 1 * time.Second
	brokerMaxBackoff     = 60 * time.Second
	brokerHealthyConnMin = 30 * time.Second // connection duration considered "healthy"
	clientChannelBuffer  = 128
)

// EventBroker maintains a single SSE connection to the OpenCode server and fans out
// events to all subscribed mobile clients.
//
// Auto-reconnect with exponential backoff:
//   - starts at 1 s, doubles each failure up to 60 s
//   - resets to 1 s when a connection lived longer than 30 s (healthy)
type EventBroker struct {
	repo domain.OpenCodeRepository

	mu      sync.RWMutex
	clients map[string]chan string

	// connStatus is broadcast to clients when the broker connects/disconnects
	connected bool
}

// NewEventBroker creates a new EventBroker.
func NewEventBroker(repo domain.OpenCodeRepository) *EventBroker {
	return &EventBroker{
		repo:    repo,
		clients: make(map[string]chan string),
	}
}

// Subscribe adds a new client and returns a channel that receives raw JSON event strings.
// clientID must be unique; use a UUID or similar.
func (b *EventBroker) Subscribe(clientID string) <-chan string {
	ch := make(chan string, clientChannelBuffer)
	b.mu.Lock()
	b.clients[clientID] = ch
	b.mu.Unlock()
	log.Printf("[broker] client %s subscribed (total: %d)", clientID, b.clientCount())
	return ch
}

// Unsubscribe removes a client and closes its channel.
func (b *EventBroker) Unsubscribe(clientID string) {
	b.mu.Lock()
	if ch, ok := b.clients[clientID]; ok {
		close(ch)
		delete(b.clients, clientID)
	}
	b.mu.Unlock()
	log.Printf("[broker] client %s unsubscribed (total: %d)", clientID, b.clientCount())
}

// IsConnected reports whether the broker currently has an active connection to OpenCode.
func (b *EventBroker) IsConnected() bool {
	b.mu.RLock()
	defer b.mu.RUnlock()
	return b.connected
}

// Run starts the reconnect loop. It blocks until ctx is cancelled.
// Call this in a goroutine: go broker.Run(ctx)
func (b *EventBroker) Run(ctx context.Context) {
	backoff := brokerInitialBackoff

	for {
		if ctx.Err() != nil {
			log.Println("[broker] context cancelled — shutting down")
			return
		}

		start := time.Now()
		log.Println("[broker] connecting to OpenCode SSE stream…")

		err := b.stream(ctx)
		elapsed := time.Since(start)

		b.setConnected(false)

		if ctx.Err() != nil {
			return
		}

		// Reset backoff if the connection was healthy long enough
		if elapsed >= brokerHealthyConnMin {
			backoff = brokerInitialBackoff
		}

		log.Printf("[broker] SSE connection lost after %s: %v — reconnecting in %s",
			elapsed.Round(time.Second), err, backoff)

		select {
		case <-ctx.Done():
			return
		case <-time.After(backoff):
			backoff = min(backoff*2, brokerMaxBackoff)
		}
	}
}

// stream opens ONE SSE connection to OpenCode and fans events out until it drops.
func (b *EventBroker) stream(ctx context.Context) error {
	ch, err := b.repo.StreamEvents(ctx)
	if err != nil {
		return err
	}

	b.setConnected(true)
	log.Println("[broker] ✓ connected to OpenCode SSE stream")

	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case event, ok := <-ch:
			if !ok {
				return nil // channel closed by client.go when conn drops
			}
			b.broadcast(event)
		}
	}
}

// broadcast sends an event to all connected mobile clients.
// Slow clients are skipped (non-blocking send) to avoid head-of-line blocking.
func (b *EventBroker) broadcast(data string) {
	b.mu.RLock()
	defer b.mu.RUnlock()

	preview := data
	if len(preview) > 120 {
		preview = preview[:120] + "…"
	}
	log.Printf("[broker] broadcast to %d client(s): %s", len(b.clients), preview)

	for id, ch := range b.clients {
		select {
		case ch <- data:
		default:
			log.Printf("[broker] client %s is too slow — dropping event", id)
		}
	}
}

func (b *EventBroker) setConnected(v bool) {
	b.mu.Lock()
	b.connected = v
	b.mu.Unlock()
}

func (b *EventBroker) clientCount() int {
	b.mu.RLock()
	defer b.mu.RUnlock()
	return len(b.clients)
}

func min(a, b time.Duration) time.Duration {
	if a < b {
		return a
	}
	return b
}
