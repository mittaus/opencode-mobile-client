package http

import (
	"github.com/gin-gonic/gin"
	"github.com/igniteLabs/opencode-gateway/config"
	"github.com/igniteLabs/opencode-gateway/internal/delivery/http/handler"
	"github.com/igniteLabs/opencode-gateway/internal/delivery/http/middleware"
)

// NewRouter builds and returns the Gin engine with all routes configured.
func NewRouter(
	cfg *config.Config,
	healthH *handler.HealthHandler,
	projectH *handler.ProjectHandler,
	registerProjectH *handler.RegisterProjectHandler,
	providerH *handler.ProviderHandler,
	sessionH *handler.SessionHandler,
	messageH *handler.MessageHandler,
	eventH *handler.EventHandler,
	processH *handler.ProcessHandler,
	poolH *handler.PoolHandler,
) *gin.Engine {
	r := gin.New()
	r.Use(gin.Logger())
	r.Use(gin.Recovery())

	// ── Public routes (no auth) ───────────────────────────────────────────────
	r.GET("/health", healthH.GatewayHealth)

	// ── Authenticated routes ──────────────────────────────────────────────────
	auth := r.Group("/", middleware.APIKeyAuth(cfg.APIKey), middleware.ProjectPath())
	{
		// ── OpenCode: Health ─────────────────────────────────────────────────
		auth.GET("/global/health", healthH.GetHealth)

		// ── Project Pool ─────────────────────────────────────────────────────
		projects := auth.Group("/projects")
		{
			projects.GET("", poolH.ListProjects)
			projects.GET("/:idx/process/status", poolH.GetProcessStatus)
			projects.POST("/:idx/process/start", poolH.StartProcess)
			projects.POST("/:idx/process/stop", poolH.StopProcess)
		}

		// ── OpenCode: Projects ───────────────────────────────────────────────
		auth.GET("/project", projectH.GetProjects)
		auth.POST("/project/register", registerProjectH.RegisterProject)
		auth.GET("/project/current", projectH.GetCurrentProject)
		auth.GET("/vcs", projectH.GetVcs)
		auth.GET("/vcs/diff", projectH.GetVcsDiff)
		auth.POST("/vcs/stage",   projectH.StageFiles)
		auth.POST("/vcs/unstage", projectH.UnstageFiles)
		auth.POST("/vcs/discard", projectH.DiscardChanges)
		auth.POST("/vcs/commit",  projectH.Commit)

		// ── OpenCode: Providers ──────────────────────────────────────────────
		auth.GET("/provider", providerH.GetProviders)

		// ── OpenCode: Sessions ───────────────────────────────────────────────
		auth.GET("/session", sessionH.GetSessions)
		auth.POST("/session", sessionH.CreateSession)
		auth.DELETE("/session/:id", sessionH.DeleteSession)
		auth.POST("/session/:id/abort", sessionH.AbortSession)
		auth.GET("/session/:id/todo", sessionH.GetTodo)
		auth.GET("/session/:id/diff", sessionH.GetDiff)
		auth.POST("/session/:id/revert", sessionH.RevertMessage)
		auth.POST("/session/:id/permissions/:permId", sessionH.RespondToPermission)

		// ── OpenCode: Messages ───────────────────────────────────────────────
		auth.GET("/session/:id/message", messageH.GetMessages)
		auth.POST("/session/:id/message", messageH.SendMessage)
		auth.POST("/session/:id/prompt_async", messageH.SendMessageAsync)
		auth.POST("/session/:id/command", messageH.ExecuteCommand)

		// ── OpenCode: SSE Event Stream ───────────────────────────────────────
		auth.GET("/event", eventH.StreamEvents)

		// ── Gateway: Process Management (extra feature) ──────────────────────
		process := auth.Group("/process")
		{
			process.GET("/status", processH.GetProcessStatus)
			process.GET("/logs", processH.GetProcessLogs)
			process.POST("/start", processH.StartProcess)
			process.POST("/stop", processH.StopProcess)
			process.POST("/restart", processH.RestartProcess)
			process.POST("/switch", processH.SwitchProcess)
		}
	}

	return r
}
