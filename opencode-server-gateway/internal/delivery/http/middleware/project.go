package middleware

import (
	"context"

	"github.com/gin-gonic/gin"
	opencodeRepo "github.com/igniteLabs/opencode-gateway/internal/repository/opencode"
)

// ProjectPath extracts the X-Project-Path header and stores the worktree value
// in the request context so that RoutingClient can select the right OpenCode instance.
// If the header is absent, nothing is set and the RoutingClient falls back to the
// bootstrap client (i.e., the existing single-project behavior is preserved).
func ProjectPath() gin.HandlerFunc {
	return func(c *gin.Context) {
		worktree := c.GetHeader("X-Project-Path")
		if worktree != "" {
			ctx := context.WithValue(c.Request.Context(), opencodeRepo.ProjectPathKey{}, worktree)
			c.Request = c.Request.WithContext(ctx)
		}
		c.Next()
	}
}
