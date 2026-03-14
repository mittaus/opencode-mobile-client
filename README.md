# OpenCode Mobile Client

A mobile remote control system for [OpenCode](https://opencode.ai) — the AI coding CLI. Control OpenCode sessions from your phone in real time, from anywhere on your local network.

This monorepo contains two components:

| Project | Description | Stack |
|---------|-------------|-------|
| [`opencode-kmp`](./opencode-kmp) | Android mobile app | Kotlin Multiplatform + Jetpack Compose |
| [`opencode-server-gateway`](./opencode-server-gateway) | Backend gateway | Go + Gin |

---

## How it works

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Your Machine (PC / Server)                   │
│                                                                     │
│   ┌──────────────────────────────────────────────────────────────┐  │
│   │              opencode-server-gateway  (:8080)                │  │
│   │                                                              │  │
│   │  ┌─────────────┐   ┌──────────────┐   ┌──────────────────┐   │  │
│   │  │  API Key    │   │ SSE Broker   │   │ Process Manager  │   │  │
│   │  │  Auth       │   │ (fan-out +   │   │ start/stop/      │   │  │
│   │  │ Middleware  │   │  reconnect)  │   │ restart opencode │   │  │
│   │  └──────┬──────┘   └──────┬───────┘   └────────┬─────────┘   │  │
│   │         │                 │                    │             │  │
│   └─────────┼─────────────────┼────────────────────┼─────────────┘  │
│             │                 │  HTTP proxy        │                │
│             ▼                 ▼                    ▼                │
│   ┌──────────────────────────────────────────────────────────────┐  │
│   │  opencode :4096 (bootstrap)  opencode :4097  opencode :4098  │  │
│   │  (one process per project subdirectory — auto-discovered)    │  │
│   └──────────────────────────────────────────────────────────────┘  │
└──────────────────────────┬──────────────────────────────────────────┘
                           │  HTTP + SSE  (local network / VPN)
                           │  X-API-Key header
                           │
              ┌────────────▼────────────┐
              │   opencode-kmp          │
              │   Android App           │
              │   (Jetpack Compose)     │
              └─────────────────────────┘
```

### Request flow

1. The **mobile app** connects to the gateway over your local network (WiFi/LAN).
2. Every request carries an `X-API-Key` header — the gateway rejects anything without it.
3. For most routes, the gateway **transparently proxies** requests to the OpenCode CLI running locally on the same machine.
4. The **SSE event stream** (`GET /event`) is special: the gateway maintains a single upstream connection to OpenCode and **fans out** events to all connected mobile clients simultaneously, with automatic reconnection and exponential backoff.
5. The gateway also exposes **process management** endpoints so you can start, stop, and restart OpenCode directly from the app — without touching your terminal.

### Multi-project support

The gateway supports multiple simultaneous OpenCode projects. On startup it scans `OPENCODE_PROJECT` for subdirectories and launches a dedicated OpenCode process for each one on its own port. The mobile app selects the active project via the `X-Project-Path` header; omitting it falls back to the bootstrap process (backward compatible).

```
OPENCODE_PROJECT=/home/user/projects
  ├── /home/user/projects/foo  →  opencode :4097  (bootstrap on :4096 only for discovery)
  ├── /home/user/projects/bar  →  opencode :4098
  └── /home/user/projects/baz  →  opencode :4099

Gateway :8080
  ├── X-Project-Path: /home/user/projects/foo  →  routed to :4097
  ├── X-Project-Path: /home/user/projects/bar  →  routed to :4098
  └── (no header)                              →  bootstrap :4096
```

---

## Quick start

### Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| [OpenCode CLI](https://opencode.ai) | latest | `npm i -g opencode-ai` |
| Go | 1.24+ | For the gateway |
| Android Studio | Hedgehog+ | For the mobile app |
| JDK | 17 or 21 | Android Studio includes one |

### 1 — Run the gateway

```bash
cd opencode-server-gateway
cp .env.example .env
# Edit .env: set GATEWAY_API_KEY and OPENCODE_PROJECT
```

Generate a secure API key:
```bash
# Linux / macOS
openssl rand -hex 32

# Windows (PowerShell)
-join ((48..57 + 65..90 + 97..122) | Get-Random -Count 48 | ForEach-Object {[char]$_})
```

Start the gateway:
```bash
make run          # Linux / macOS
./make.ps1 run    # Windows (PowerShell)
```

### 2 — Install the mobile app

Open `opencode-kmp` in Android Studio and run it on your device or emulator.

On the **Connect** screen, enter:
- **Server URL**: `http://<your-machine-ip>:8080`
- **API Key**: the value of `GATEWAY_API_KEY` from your `.env`

> Tip: find your machine's local IP with `ip a` (Linux/macOS) or `ipconfig` (Windows).

---

## Projects

### `opencode-kmp` — Android App

A Kotlin Multiplatform app with Jetpack Compose UI, targeting Android (iOS-ready via shared KMP code).

**Architecture: Clean Architecture + MVVM**

```
androidApp  (Jetpack Compose + ViewModel + Koin DI)
     │
shared/commonMain  (Domain: models, repository interfaces, use cases)
     │
shared/commonMain  (Data: Ktor HTTP client, SSE parser, DTOs, mappers)
     │
androidMain / iosMain  (platform-specific: DataStore / NSUserDefaults)
```

**Screens**

| Screen | Description |
|--------|-------------|
| Connect | Enter server URL and API key; verifies gateway health |
| Projects | Browse all projects registered in OpenCode |
| Models | Select AI provider and model |
| Chat | Real-time chat; supports Build/Plan mode, Stop, Undo |
| Chat › Todos | View and track tasks managed by OpenCode |
| Files | Git diffs produced by the current session |
| Sessions | Create, list, resume, and delete coding sessions |
| Stats | Cumulative token usage and cost per model/tool |

Full documentation: [`opencode-kmp/README.md`](./opencode-kmp/README.md)

---

### `opencode-server-gateway` — Backend Gateway

A lightweight Go service acting as a secure proxy and process manager between the mobile app and the OpenCode CLI.

**Architecture: Clean Architecture**

```
delivery/http  (Gin handlers, API Key middleware, router)
     │
usecase        (EventBroker: SSE fan-out + exponential backoff reconnect)
     │
repository     (HTTP client to OpenCode CLI, subprocess lifecycle manager)
     │
domain         (entities, repository interfaces, error types)
```

**Key features vs connecting directly to OpenCode**

| Feature | OpenCode direct | Gateway |
|---------|----------------|---------|
| Authentication | Basic auth | ✅ API Key header |
| SSE multi-client | 1 client | ✅ N clients (fan-out) |
| SSE auto-reconnect | Client-side | ✅ Server-side (backoff 1s → 60s) |
| SSE keep-alive | No | ✅ Ping every 30s |
| Process management | Manual | ✅ start / stop / restart via API |
| Auto-restart on crash | No | ✅ Restarts opencode in 3s |
| Process logs | stdout only | ✅ Ring buffer via `/process/logs` |
| Multi-project | No | ✅ One process per project |

Full documentation: [`opencode-server-gateway/README.md`](./opencode-server-gateway/README.md)

---

## Repository structure

```
OpenCodeMobileClient/
├── opencode-kmp/                  # Android / KMP mobile app
│   ├── androidApp/                # Jetpack Compose UI, ViewModels, DI
│   └── shared/                    # KMP shared module (domain + data)
│       ├── commonMain/            # Pure Kotlin: models, use cases, Ktor API
│       ├── androidMain/           # DataStore, currentTimeMs
│       └── iosMain/               # NSUserDefaults, currentTimeMs
│
└── opencode-server-gateway/       # Go backend gateway
    ├── cmd/server/main.go         # Entry point, DI, graceful shutdown
    ├── config/config.go           # .env loader
    └── internal/
        ├── domain/                # Entities, interfaces, errors
        ├── usecase/               # EventBroker, business logic
        ├── repository/            # OpenCode HTTP client, process manager
        ├── platform/              # OS-specific signal handling
        └── delivery/http/         # Gin router, handlers, middleware
```

---

## Domain model

Both projects share the same conceptual entities:

| Entity | Description |
|--------|-------------|
| `Session` | An AI coding session (title, project, model, timestamps) |
| `Message` | Chat message with role (USER / ASSISTANT) and structured parts |
| `MessagePart` | Text, ToolCall, ToolResult, or StepStart |
| `Project` | Project path with git branch and remote |
| `Provider` / `Model` | AI provider and available models with cost info |
| `FileDiff` | Git file changes with patch content |
| `TodoItem` | Task with status (pending / in_progress / completed) and priority |
| `PermissionRequest` | Tool call permission prompt that requires user approval |

---

## Roadmap

- [ ] iOS app (KMP shared module is ready — needs SwiftUI or Compose Multiplatform UI)
- [ ] SSE auto-reconnect on the mobile client side (with backoff)
- [ ] Unit tests for use cases and mappers (both projects)
- [ ] ProGuard rules for Android release build
- [ ] Deep links for shared sessions (`opencode share`)

---

## License

MIT
