package pool

import (
	"context"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sync"

	"github.com/igniteLabs/opencode-gateway/config"
	"github.com/igniteLabs/opencode-gateway/internal/domain"
	opencodeRepo "github.com/igniteLabs/opencode-gateway/internal/repository/opencode"
	processrepo "github.com/igniteLabs/opencode-gateway/internal/repository/process"
	"github.com/igniteLabs/opencode-gateway/internal/usecase"
)

// projectItem matches the JSON shape of GET /project from the OpenCode CLI.
type projectItem struct {
	ID       string `json:"id"`
	Worktree string `json:"worktree"`
}

// ProjectContext holds everything needed to interact with one OpenCode project instance:
// its process Manager, HTTP Client, and SSE EventBroker.
type ProjectContext struct {
	Worktree string
	Port     int
	Manager  *processrepo.Manager
	Client   *opencodeRepo.Client
	Broker   *usecase.EventBroker
}

// Pool manages one ProjectContext per discovered OpenCode project.
// It implements opencodeRepo.ClientPool so the RoutingClient can look up clients.
type Pool struct {
	mu               sync.RWMutex
	contexts         []*ProjectContext
	cfg              *config.Config
	bootstrapManager *processrepo.Manager
	bootstrapClient  *opencodeRepo.Client

	// portMap persists assigned ports across Rediscover calls so that a project
	// always gets the same port (and thus the same running process) on re-discovery.
	portMap  map[string]int // worktree → assigned port
	nextPort int            // next port to assign to a new project (starts at base+1)

	// routingClientSetter is wired after Discover so we can re-wire after RediscoverIfEmpty.
	routingClientSetter func(opencodeRepo.ClientPool)
}

// New creates an empty Pool.
// bootstrapManager and bootstrapClient are the already-created instances for
// the first OpenCode process (port cfg.OpenCodePort, dir cfg.OpenCodeProject).
func New(cfg *config.Config, bootstrapManager *processrepo.Manager, bootstrapClient *opencodeRepo.Client) *Pool {
	return &Pool{
		cfg:              cfg,
		bootstrapManager: bootstrapManager,
		bootstrapClient:  bootstrapClient,
		portMap:          make(map[string]int),
		nextPort:         cfg.OpenCodePort + 1, // base port (4096) is bootstrap-only; projects start at 4097
	}
}

// SetRoutingClientSetter stores a callback that re-wires the RoutingClient after a late discovery.
func (p *Pool) SetRoutingClientSetter(fn func(opencodeRepo.ClientPool)) {
	p.mu.Lock()
	p.routingClientSetter = fn
	p.mu.Unlock()
}

// Rediscover re-runs project discovery unconditionally (e.g. after registering a new project).
// Existing running processes are NOT restarted; only new projects get fresh Manager+Client+Start.
func (p *Pool) Rediscover(ctx context.Context) error {
	if err := p.Discover(ctx, p.bootstrapClient); err != nil {
		return err
	}
	p.mu.RLock()
	fn := p.routingClientSetter
	p.mu.RUnlock()
	if fn != nil {
		fn(p)
	}
	go p.RunBrokers(context.Background())
	return nil
}

// RediscoverIfEmpty checks if the pool has no projects and, if so, runs Discover again.
// This handles the case where OpenCode was not running when the gateway started.
// Returns true if discovery was attempted.
func (p *Pool) RediscoverIfEmpty(ctx context.Context) bool {
	if p.Count() > 0 {
		return false
	}
	log.Println("[pool] pool is empty — attempting re-discovery")
	if err := p.Discover(ctx, p.bootstrapClient); err != nil {
		log.Printf("[pool] re-discovery failed: %v", err)
		return true
	}
	log.Printf("[pool] re-discovery succeeded — %d project(s)", p.Count())
	// Re-wire the routing client
	p.mu.RLock()
	fn := p.routingClientSetter
	p.mu.RUnlock()
	if fn != nil {
		fn(p)
	}
	// Brokers must use a long-lived context, NOT the request context.
	go p.RunBrokers(context.Background())
	return true
}

// Discover scans cfg.OpenCodeProject for subdirectories and builds one ProjectContext
// per subdirectory. Each project gets its own dedicated opencode process on a unique port.
// On re-discovery, already-running projects are identified by worktree and reused as-is.
// The repo parameter is kept for API compatibility but is not used.
func (p *Pool) Discover(ctx context.Context, repo domain.OpenCodeRepository) error {
	baseDir := p.cfg.OpenCodeProject
	if baseDir == "" {
		log.Printf("[pool] OPENCODE_PROJECT is empty — skipping discovery")
		return nil
	}

	entries, err := os.ReadDir(baseDir)
	if err != nil {
		return fmt.Errorf("reading project dir %q: %w", baseDir, err)
	}

	// Collect subdirectories as project worktrees.
	var worktrees []string
	for _, entry := range entries {
		if entry.IsDir() {
			worktrees = append(worktrees, filepath.Join(baseDir, entry.Name()))
		}
	}
	log.Printf("[pool] discovery: scanned %q — found %d subdirectorie(s): %v", baseDir, len(worktrees), worktrees)

	p.mu.Lock()
	defer p.mu.Unlock()

	// Build a map of existing contexts by worktree so we can reuse running ones.
	existing := make(map[string]*ProjectContext, len(p.contexts))
	for _, pctx := range p.contexts {
		existing[pctx.Worktree] = pctx
	}

	newContexts := make([]*ProjectContext, 0, len(worktrees))

	for i, worktree := range worktrees {
		// ── Reuse an existing context if one is already running ──────────────
		if prev, ok := existing[worktree]; ok {
			log.Printf("[pool] project[%d]: worktree=%q — reusing existing port %d", i, worktree, prev.Port)
			newContexts = append(newContexts, prev)
			continue
		}

		// ── Assign a port (stable across re-discoveries) ─────────────────────
		var port int
		if assigned, alreadyHasPort := p.portMap[worktree]; alreadyHasPort {
			port = assigned
		} else {
			port = p.nextPort
			p.portMap[worktree] = port
			p.nextPort++
		}

		// ── New project: dedicated process in its own directory ───────────────
		baseURL := fmt.Sprintf("http://127.0.0.1:%d", port)
		mgr := processrepo.NewManagerForProject(p.cfg, port, worktree)
		client := opencodeRepo.NewClientWithBaseURL(baseURL, p.cfg.OpenCodePassword)
		log.Printf("[pool] project[%d]: worktree=%q — new process port %d", i, worktree, port)

		go func(m *processrepo.Manager, wt string, pt int) {
			if err := m.Start(context.Background()); err != nil {
				log.Printf("[pool] failed to start process for worktree=%q port=%d: %v", wt, pt, err)
			}
		}(mgr, worktree, port)

		broker := usecase.NewEventBroker(client)

		newContexts = append(newContexts, &ProjectContext{
			Worktree: worktree,
			Port:     port,
			Manager:  mgr,
			Client:   client,
			Broker:   broker,
		})
	}

	p.contexts = newContexts
	log.Printf("[pool] discovery complete — %d project(s)", len(p.contexts))
	return nil
}

// RunBrokers starts all EventBrokers in background goroutines.
// Call this after Discover() and pass the app context.
func (p *Pool) RunBrokers(ctx context.Context) {
	p.mu.RLock()
	defer p.mu.RUnlock()

	for i, pctx := range p.contexts {
		log.Printf("[pool] starting broker for project[%d] worktree=%q port=%d", i, pctx.Worktree, pctx.Port)
		go pctx.Broker.Run(ctx)
	}
}

// ─── opencodeRepo.ClientPool implementation ───────────────────────────────────

// ClientFor returns the *opencode.Client for the given worktree, or nil if not found.
// This satisfies the opencodeRepo.ClientPool interface used by RoutingClient.
func (p *Pool) ClientFor(worktree string) *opencodeRepo.Client {
	p.mu.RLock()
	defer p.mu.RUnlock()
	for _, pctx := range p.contexts {
		if pctx.Worktree == worktree {
			return pctx.Client
		}
	}
	return nil
}

// BrokerFor returns the *usecase.EventBroker for the given worktree, or nil if not found.
func (p *Pool) BrokerFor(worktree string) *usecase.EventBroker {
	p.mu.RLock()
	defer p.mu.RUnlock()
	for _, pctx := range p.contexts {
		if pctx.Worktree == worktree {
			return pctx.Broker
		}
	}
	return nil
}

// ─── Read helpers ─────────────────────────────────────────────────────────────

// List returns a snapshot of all projects with live process status.
func (p *Pool) List() []domain.ProjectEntry {
	p.mu.RLock()
	defer p.mu.RUnlock()

	result := make([]domain.ProjectEntry, len(p.contexts))
	for i, pctx := range p.contexts {
		result[i] = domain.ProjectEntry{
			Worktree: pctx.Worktree,
			Port:     pctx.Port,
			Status:   pctx.Manager.Status().State,
		}
	}
	return result
}

func (p *Pool) GetAt(idx int) (*ProjectContext, bool) {
	p.mu.RLock()
	defer p.mu.RUnlock()
	if idx < 0 || idx >= len(p.contexts) {
		return nil, false
	}
	return p.contexts[idx], true
}

func (p *Pool) GetByWorktree(worktree string) (*ProjectContext, bool) {
	p.mu.RLock()
	defer p.mu.RUnlock()
	for _, pctx := range p.contexts {
		if pctx.Worktree == worktree {
			return pctx, true
		}
	}
	return nil, false
}

func (p *Pool) Count() int {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return len(p.contexts)
}

// ─── Process management ───────────────────────────────────────────────────────

func (p *Pool) StartAt(ctx context.Context, idx int) error {
	pctx, ok := p.GetAt(idx)
	if !ok {
		return fmt.Errorf("project index %d out of range (pool has %d projects)", idx, p.Count())
	}
	return pctx.Manager.Start(ctx)
}

func (p *Pool) StopAt(ctx context.Context, idx int) error {
	pctx, ok := p.GetAt(idx)
	if !ok {
		return fmt.Errorf("project index %d out of range (pool has %d projects)", idx, p.Count())
	}
	return pctx.Manager.Stop(ctx)
}

func (p *Pool) StatusAt(idx int) (domain.ProcessStatus, bool) {
	pctx, ok := p.GetAt(idx)
	if !ok {
		return domain.ProcessStatus{}, false
	}
	return pctx.Manager.Status(), true
}
