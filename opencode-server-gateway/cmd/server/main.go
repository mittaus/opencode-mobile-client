package main

import (
	"context"
	"log"
	"net/http"
	"os/signal"
	"time"

	"github.com/igniteLabs/opencode-gateway/config"
	delivery "github.com/igniteLabs/opencode-gateway/internal/delivery/http"
	"github.com/igniteLabs/opencode-gateway/internal/delivery/http/handler"
	"github.com/igniteLabs/opencode-gateway/internal/platform"
	poolPkg "github.com/igniteLabs/opencode-gateway/internal/pool"
	opencodeRepo "github.com/igniteLabs/opencode-gateway/internal/repository/opencode"
	processRepo "github.com/igniteLabs/opencode-gateway/internal/repository/process"
	"github.com/igniteLabs/opencode-gateway/internal/usecase"
)

func main() {
	// ── Config ────────────────────────────────────────────────────────────────
	cfg := config.Load()
	log.Printf("[main] OpenCode gateway starting on :%s", cfg.GatewayPort)
	log.Printf("[main] OpenCode CLI: %s → %s", cfg.OpenCodeBin, cfg.OpenCodeBaseURL)

	// ── Context with graceful shutdown ────────────────────────────────────────
	// platform.ShutdownSignals() returns the correct signals per OS:
	//   Linux/Mac → os.Interrupt + syscall.SIGTERM (for systemd, Docker, kill)
	//   Windows   → os.Interrupt only (Ctrl+C)
	ctx, stop := signal.NotifyContext(context.Background(), platform.ShutdownSignals()...)
	defer stop()

	// ── Infrastructure ────────────────────────────────────────────────────────
	openCodeClient := opencodeRepo.NewClient(cfg) // bootstrap: port 4096
	procManager := processRepo.NewManager(cfg)

	// ── Use Cases (use RoutingClient so all requests route to the correct project) ──
	// routingClient implements OpenCodeRepository and delegates to the right per-project
	// *Client based on the X-Project-Path header injected by the project middleware.
	// Until Discover() runs, pool is empty and all calls fall back to openCodeClient.
	routingClient := opencodeRepo.NewRoutingClient(nil, openCodeClient) // pool wired after Discover
	healthUC := usecase.NewHealthUseCase(routingClient)
	projectUC := usecase.NewProjectUseCase(routingClient, cfg)
	providerUC := usecase.NewProviderUseCase(routingClient)
	sessionUC := usecase.NewSessionUseCase(routingClient)
	messageUC := usecase.NewMessageUseCase(routingClient)
	processUC := usecase.NewProcessUseCase(procManager)

	// ── SSE Event Broker (bootstrap — used when no X-Project-Path is set) ──────
	broker := usecase.NewEventBroker(openCodeClient)
	go broker.Run(ctx)
	log.Println("[main] SSE event broker started")

	// ── Auto-start OpenCode CLI ───────────────────────────────────────────────
	if cfg.AutoStart {
		log.Println("[main] auto-starting opencode CLI…")
		if err := procManager.Start(ctx); err != nil {
			log.Printf("[main] ⚠ auto-start failed: %v", err)
		}
	}

	// ── Project Pool: discover all projects from the running OpenCode ────────
	projectPool := poolPkg.New(cfg, procManager, openCodeClient)
	discoverCtx, discoverCancel := context.WithTimeout(ctx, 10*time.Second)
	defer discoverCancel()
	if err := projectPool.Discover(discoverCtx, openCodeClient); err != nil {
		log.Printf("[main] ⚠ project discovery failed (pool will be empty): %v", err)
	} else {
		log.Printf("[main] ✓ project pool ready — %d project(s)", projectPool.Count())
		// Wire the pool into the routing client so per-project routing is active
		routingClient.SetPool(projectPool)
		// Start all per-project SSE brokers (project[0] broker already ran above)
		go projectPool.RunBrokers(ctx)
	}
	// Always register the setter so a late re-discovery (when OpenCode starts after the gateway)
	// can re-wire the routing client automatically via RediscoverIfEmpty.
	projectPool.SetRoutingClientSetter(func(p opencodeRepo.ClientPool) {
		routingClient.SetPool(p)
	})

	// After a project is registered (git init + session creation), re-discover so the pool
	// includes the new project and the routing client can reach it.
	projectUC.SetOnProjectRegistered(func(ctx context.Context) {
		log.Println("[main] project registered — re-discovering pool")
		if err := projectPool.Rediscover(ctx); err != nil {
			log.Printf("[main] ⚠ post-register re-discovery failed: %v", err)
		} else {
			log.Printf("[main] ✓ pool re-discovered — %d project(s)", projectPool.Count())
		}
	})

	// ── Handlers ───────────────────────────────────────────────────────
	healthH := handler.NewHealthHandler(healthUC)
	projectH := handler.NewProjectHandler(projectUC)
	registerProjectH := handler.NewRegisterProjectHandler(projectUC)
	providerH := handler.NewProviderHandler(providerUC)
	sessionH := handler.NewSessionHandler(sessionUC)
	messageH := handler.NewMessageHandler(messageUC)
	eventH := handler.NewEventHandler(broker)
	eventH.SetPool(projectPool) // enables per-project SSE routing
	processH := handler.NewProcessHandler(processUC)
	poolH := handler.NewPoolHandler(projectPool)

	// ── Router ────────────────────────────────────────────────────────────────
	router := delivery.NewRouter(cfg, healthH, projectH, registerProjectH, providerH, sessionH, messageH, eventH, processH, poolH)

	// ── HTTP Server ───────────────────────────────────────────────────────────
	srv := &http.Server{
		Addr:         ":" + cfg.GatewayPort,
		Handler:      router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 0, // 0 = no timeout (needed for SSE streams)
		IdleTimeout:  120 * time.Second,
	}

	// Start server in background goroutine
	go func() {
		log.Printf("[main] ✓ gateway listening on :%s", cfg.GatewayPort)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("[main] server error: %v", err)
		}
	}()

	// ── Wait for shutdown signal ──────────────────────────────────────────────
	<-ctx.Done()
	log.Println("[main] shutdown signal received — draining connections…")

	// Stop the OpenCode process if we started it
	if cfg.AutoStart && procManager.IsRunning() {
		shutCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := procManager.Stop(shutCtx); err != nil {
			log.Printf("[main] ⚠ stopping opencode: %v", err)
		}
	}

	// Graceful HTTP shutdown
	shutCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutCtx); err != nil {
		log.Printf("[main] ⚠ server shutdown: %v", err)
	}

	log.Println("[main] ✓ gateway stopped")
}
