package middleware

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

const apiKeyHeader = "X-API-Key"

// APIKeyAuth returns a Gin middleware that validates the X-API-Key header.
func APIKeyAuth(apiKey string) gin.HandlerFunc {
	return func(c *gin.Context) {
		key := c.GetHeader(apiKeyHeader)
		if key == "" {
			// Also support Authorization: Bearer <key> as a fallback
			bearer := c.GetHeader("Authorization")
			if len(bearer) > 7 && bearer[:7] == "Bearer " {
				key = bearer[7:]
			}
		}

		if key != apiKey {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{
				"error": "invalid or missing API key — provide X-API-Key header",
			})
			return
		}

		c.Next()
	}
}
