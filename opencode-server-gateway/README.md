# OpenCode Server Gateway

Backend en **Go** con **Clean Architecture** que actúa como BFF (Backend for Frontend) entre la aplicación móvil [opencode-kmp](../opencode-kmp) y el CLI de **OpenCode AI**.

## Arquitectura

```
             Mobile App (Android / iOS)
                     │   X-API-Key + X-Project-Path headers
                     ▼
┌─────────────────────────────────────────────────────────────┐
│           opencode-server-gateway (Go / Gin)                │
│                                                             │
│  delivery/http  → Gin handlers + router                     │
│  usecase        → EventBroker (SSE fanout + backoff)        │
│  pool           → Project pool (filesystem discovery)       │
│  domain         → Entidades + interfaces                    │
│  repository/    → RoutingClient + Process manager           │
└───────┬─────────────────┬─────────────────┬────────────────┘
        │ :4096 bootstrap │ :4097 project-A │ :4098 project-B
        ▼                 ▼                 ▼
   opencode serve    opencode serve    opencode serve
   (discovery only)  /projects/foo     /projects/bar
```

### Capas (Clean Architecture)

| Capa | Paquete | Responsabilidad |
|------|---------|----------------|
| **Domain** | `internal/domain` | Entidades, interfaces de repositorio, errores |
| **Use Case** | `internal/usecase` | Lógica de negocio, SSE Broker con auto-reconnect |
| **Pool** | `internal/pool` | Descubrimiento de proyectos, un proceso opencode por proyecto |
| **Repository** | `internal/repository` | RoutingClient (selección por X-Project-Path), cliente HTTP, gestión del subprocess |
| **Platform** | `internal/platform` | Abstracciones por OS (señales, stop de proceso) |
| **Delivery** | `internal/delivery/http` | Handlers Gin, middleware API Key, router |

### Multi-project pool

Al iniciar, el gateway escanea los **subdirectorios** de `OPENCODE_PROJECT` y levanta un proceso `opencode serve` independiente por cada uno, en puertos consecutivos a partir de `OPENCODE_PORT + 1`:

```
OPENCODE_PROJECT=/home/user/projects
  ├── cashflow360  →  opencode serve --port 4097  (workdir: /home/user/projects/cashflow360)
  ├── todo         →  opencode serve --port 4098  (workdir: /home/user/projects/todo)
  └── webilabs     →  opencode serve --port 4099  (workdir: /home/user/projects/webilabs)
```

El proceso bootstrap en `OPENCODE_PORT` (4096) solo se usa para el health check inicial. Cada request del móvil lleva el header `X-Project-Path: /ruta/al/proyecto`; el `RoutingClient` selecciona el cliente HTTP correcto. Si el header está ausente, se usa el bootstrap como fallback (compatible con versiones anteriores).

Re-discovery ocurre automáticamente cuando se registra un nuevo proyecto via `POST /project/register`.

---

## Requisitos

| Herramienta | Versión mínima | Notas |
|---|---|---|
| **Go** | 1.26.0 | Ver instalación abajo |
| **opencode CLI** | última | `npm i -g opencode-ai` |

---

## Instalación de Go

### Windows (con gvm)

> **Importante:** Los comandos de `gvm` **solo funcionan en PowerShell**. No uses CMD ni Git Bash para estos pasos — `gvm --format=powershell` genera código que solo PowerShell puede interpretar con `Invoke-Expression`.

```powershell
# En PowerShell como Administrador:
[Net.ServicePointManager]::SecurityProtocol = "tls12"
Invoke-WebRequest -URI https://github.com/andrewkroh/gvm/releases/download/v0.6.0/gvm-windows-amd64.exe `
  -Outfile C:\Windows\System32\gvm.exe

# Instalar y activar Go (requerido en cada sesión de PowerShell nueva)
gvm --format=powershell 1.26.0 | Invoke-Expression

go version  # go version go1.26.0 windows/amd64
```

> **Tip:** Para no repetir la activación en cada sesión, agrégala a tu perfil de PowerShell:
> ```powershell
> Add-Content $PROFILE "`ngvm --format=powershell 1.26.0 | Invoke-Expression"
> ```
> Esto aplica solo a **PowerShell** — si usas Windows Terminal con CMD u otras shells, deberás activar Go manualmente o usar el instalador oficial de Go.

### Linux / Mac

```bash
# Instalar go via gvm o el instalador oficial:
curl -L https://go.dev/dl/go1.26.0.linux-amd64.tar.gz | sudo tar -C /usr/local -xz
echo 'export PATH=$PATH:/usr/local/go/bin' >> ~/.bashrc && source ~/.bashrc

go version  # go version go1.26.0 linux/amd64
```

---

## Configuración

```bash
# Linux / Mac
cp .env.example .env && nano .env
```

```powershell
# Windows (PowerShell)
Copy-Item .env.example .env; notepad .env
```

| Variable | Descripción | Default |
|---|---|---|
| `GATEWAY_PORT` | Puerto del gateway | `8080` |
| `GATEWAY_API_KEY` | API Key para auth del móvil (**requerida**) | — |
| `OPENCODE_BIN` | Ruta al binario opencode | `opencode` |
| `OPENCODE_PORT` | Puerto interno de OpenCode | `4096` |
| `OPENCODE_HOSTNAME` | Hostname del proceso opencode | `127.0.0.1` |
| `OPENCODE_SERVER_PASSWORD` | Password del servidor OpenCode | — (requerida si OpenCode tiene auth) |
| `OPENCODE_PROJECT` | Directorio de trabajo de `opencode serve` | — |
| `OPENCODE_AUTO_START` | Auto-iniciar opencode al arrancar el gateway | `false` |

**Generar un API Key seguro:**

```bash
# Linux / Mac
openssl rand -hex 32

# Windows (PowerShell)
-join ((48..57 + 65..90 + 97..122) | Get-Random -Count 48 | ForEach-Object {[char]$_})
```

---

## Build y ejecución

### Windows

```powershell
gvm --format=powershell 1.26.0 | Invoke-Expression

# Descargar dependencias
go mod tidy

# Compilar
go build -o opencode-gateway.exe .\cmd\server\main.go

# Correr
.\opencode-gateway.exe

# O usar el script PowerShell incluido (equivale a Makefile):
.\make.ps1 tidy
.\make.ps1 build
.\make.ps1 run
```

**Detener:** `Ctrl+C` → graceful shutdown (drena conexiones activas)

### Linux / Mac (servidor de producción)

```bash
go mod tidy
go build -o opencode-gateway ./cmd/server/main.go
./opencode-gateway
```

**Detener:**
```bash
Ctrl+C          # SIGINT — graceful shutdown
kill <PID>      # SIGTERM — también graceful (soporte nativo en Linux)
```

### Cross-compilar para Linux desde Windows

```powershell
gvm --format=powershell 1.26.0 | Invoke-Expression
$env:GOOS="linux"; $env:GOARCH="amd64"
go build -o opencode-gateway .\cmd\server\main.go
# → binario listo para subir al servidor Linux via SCP/SFTP
```

---

## Diferencias por sistema operativo

El proyecto usa **build tags de Go** para adaptar comportamientos automáticamente según el OS de compilación — sin `#ifdef` ni código condicional en runtime:

| Comportamiento | Windows | Linux / Mac |
|---|---|---|
| **Señal de shutdown** | `Ctrl+C` (os.Interrupt) | `Ctrl+C` + `kill <PID>` (SIGTERM) |
| **Stop de opencode** | `Kill()` inmediato | `SIGTERM` → espera 8s → `SIGKILL` |

> El SIGTERM en Linux es esencial para que **systemd**, **Docker** y **Kubernetes** puedan detener el servicio limpiamente.

Archivos por plataforma (compilados automáticamente según el OS):

```
internal/platform/
├── signals_unix.go        # Linux/Mac: os.Interrupt + syscall.SIGTERM
└── signals_windows.go     # Windows:   os.Interrupt (Ctrl+C únicamente)

internal/repository/process/
├── stop_unix.go           # Linux/Mac: SIGTERM → wait 8s → SIGKILL
└── stop_windows.go        # Windows:   Kill() directo
```

---

## Despliegue en producción (Linux + Caddy)

Esta sección cubre cómo exponer el gateway a internet con HTTPS automático usando [Caddy](https://caddyserver.com) como reverse proxy.

```
Internet
   │  HTTPS :443
   ▼
Caddy  (TLS automático vía Let's Encrypt)
   │  HTTP interno
   ▼
opencode-gateway :8080
   │
   ▼
opencode serve :4096
```

### Prerequisitos

| Requisito | Detalle |
|-----------|---------|
| Servidor Linux | Ubuntu 22.04+ / Debian 12+ recomendado |
| IP pública | Con puertos **80** y **443** abiertos en el firewall/router |
| Dominio o subdominio | Registro `A` apuntando a tu IP pública (ej. `opencode.tu-dominio.com`) |
| OpenCode CLI instalado | `npm i -g opencode-ai` en el servidor |
| Go 1.24+ | Para compilar el gateway (ver sección Build) |

> Let's Encrypt no emite certificados para IPs directas — el dominio es obligatorio.

---

### 1. Compilar el gateway para Linux

Desde tu máquina de desarrollo (Windows o Linux):

```bash
# Linux / macOS
go build -o opencode-gateway ./cmd/server/main.go

# Cross-compilar desde Windows (PowerShell)
$env:GOOS="linux"; $env:GOARCH="amd64"
go build -o opencode-gateway .\cmd\server\main.go
```

Copiar al servidor:
```bash
scp opencode-gateway .env.example usuario@tu-servidor:/opt/opencode-gateway/
```

---

### 2. Configurar el gateway

Asegurarse que se tiene el servidor de opencode ejecutandose

```sh
OPENCODE_SERVER_PASSWORD=<password de opencode serve> opencode serve
```

```bash
ssh usuario@tu-servidor
cd /opt/opencode-gateway

cp .env.example .env
nano .env
```

Valores recomendados para producción:

```env
GATEWAY_PORT=8080
GATEWAY_API_KEY=<genera con: openssl rand -hex 32>
OPENCODE_BIN=opencode
OPENCODE_PORT=4096
OPENCODE_HOSTNAME=0.0.0.0
OPENCODE_SERVER_PASSWORD=<password de opencode serve>
# Directorio PADRE que contiene los subdirectorios de proyectos.
# El gateway descubre automáticamente cada subdirectorio y le asigna un puerto.
# Ejemplo: si tienes /projects/foo y /projects/bar, configura OPENCODE_PROJECT=/projects
OPENCODE_PROJECT=/ruta/al/directorio/padre
OPENCODE_AUTO_START=true
```

> **Estructura esperada de `OPENCODE_PROJECT`:**
> ```
> /ruta/al/directorio/padre/
>   ├── proyecto-a/    ←  opencode :4097
>   ├── proyecto-b/    ←  opencode :4098
>   └── proyecto-c/    ←  opencode :4099
> ```

---

### 3. Instalar Caddy

```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update && sudo apt install caddy
```

---

### 4. Configurar Caddy

```bash
sudo nano /etc/caddy/Caddyfile
```

```
opencode.tu-dominio.com {
    reverse_proxy localhost:8080

    # Opcional: Logs para ver si hay errores de conexión
    log {
        output file /var/log/caddy/opencode.log
    }
}
```

Caddy obtiene y renueva el certificado TLS automáticamente la primera vez que recibe una request.

Aplicar la configuración:
```bash
sudo systemctl reload caddy
```

---

### 5. Ejecutar el gateway con systemd

```bash
sudo nano /etc/systemd/system/opencode-gateway.service
```

```ini
[Unit]
Description=OpenCode Server Gateway
After=network.target

[Service]
Type=simple
User=deploy
WorkingDirectory=/opt/opencode-gateway
EnvironmentFile=/opt/opencode-gateway/.env
ExecStart=/opt/opencode-gateway/opencode-gateway
Restart=always
RestartSec=5
KillMode=mixed
KillSignal=SIGTERM
TimeoutStopSec=15

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable opencode-gateway
sudo systemctl start  opencode-gateway
sudo systemctl status opencode-gateway
sudo journalctl -u opencode-gateway -f    # ver logs en tiempo real
```

---

### 6. Verificar

```bash
# Health del gateway (sin auth)
curl https://opencode.tu-dominio.com/health

# Debería responder:
# {"status":"ok"}
```

En la app Android, conectar a:
```
URL:     https://opencode.tu-dominio.com
API Key: <valor de GATEWAY_API_KEY>
```

---

### Checklist de producción

```
[ ] Registro DNS tipo A apuntando a la IP del servidor
[ ] Puertos 80 y 443 abiertos en el firewall
[ ] GATEWAY_API_KEY generado con openssl rand -hex 32
[ ] .env con permisos restrictivos: chmod 600 .env
[ ] opencode-gateway corriendo como servicio systemd
[ ] Caddy corriendo como servicio systemd (se instala automáticamente)
[ ] curl https://tu-dominio/health responde {"status":"ok"}
```

---

## API Endpoints

Todos los endpoints (excepto `GET /health`) requieren el header:

```
X-API-Key: <GATEWAY_API_KEY>
```

También se acepta: `Authorization: Bearer <GATEWAY_API_KEY>`

Para rutas que operan sobre un proyecto específico, incluir también:

```
X-Project-Path: /ruta/absoluta/al/proyecto
```

Si se omite, la request se enruta al proceso bootstrap (puerto base).

### Proxy transparente a OpenCode

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/global/health` | Health del servidor OpenCode |
| `GET` | `/project` | Lista de proyectos |
| `GET` | `/project/current` | Proyecto actual |
| `GET` | `/vcs` | Info de VCS (branch, remote) |
| `GET` | `/provider` | Providers y modelos de IA disponibles |
| `GET` | `/session` | Lista de sesiones |
| `POST` | `/session` | Crear sesión |
| `DELETE` | `/session/:id` | Eliminar sesión |
| `POST` | `/session/:id/abort` | Abortar sesión activa |
| `GET` | `/session/:id/message` | Mensajes de una sesión |
| `POST` | `/session/:id/message` | Enviar mensaje (síncrono) |
| `POST` | `/session/:id/prompt_async` | Enviar mensaje asíncrono |
| `POST` | `/session/:id/command` | Ejecutar comando slash |
| `GET` | `/session/:id/todo` | Lista de TODOs |
| `GET` | `/session/:id/diff` | Git diff de la sesión |
| `POST` | `/session/:id/revert` | Revertir a un mensaje anterior |
| `POST` | `/session/:id/permissions/:permId` | Responder solicitud de permiso |
| `GET` | `/event` | **Stream SSE de eventos** |

### Endpoints propios del gateway

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| `GET` | `/health` | No | Health del gateway |
| `GET` | `/process/status` | Sí | Estado del proceso opencode (usa `X-Project-Path` para proyecto específico) |
| `GET` | `/process/logs?n=100` | Sí | Últimas N líneas de stdout/stderr |
| `POST` | `/process/start` | Sí | Iniciar `opencode serve` |
| `POST` | `/process/stop` | Sí | Detener `opencode serve` |
| `POST` | `/process/restart` | Sí | Reiniciar `opencode serve` |
| `GET` | `/pool` | Sí | Lista todos los proyectos del pool con puerto y estado |
| `POST` | `/project/register` | Sí | Registrar un nuevo directorio como proyecto (lanza proceso + re-discovery) |

---

## Conectar la app móvil

En la pantalla **Connect** de `opencode-kmp`, configura:

```
URL:      http://<IP-del-servidor>:8080
Username: gateway          ← cualquier valor (no se verifica)
Password: <GATEWAY_API_KEY>
```

La app envía `Authorization: Bearer <password>` que el middleware acepta automáticamente.

---

## Funcionalidades vs conectarse directo a OpenCode

| Feature | OpenCode directo | Este Gateway |
|---------|-----------------|--------------|
| **Autenticación** | Basic auth (password fijo) | ✅ API Key por header |
| **SSE reconexión** | El cliente móvil lo maneja | ✅ Automática en el servidor (backoff 1s→60s) |
| **SSE multi-cliente** | 1 cliente simultáneo | ✅ N clientes (fan-out en el broker) |
| **Keep-alive SSE** | No | ✅ Ping cada 30s (evita timeout de proxies) |
| **Gestión del proceso** | Manual en el servidor | ✅ `/process/start\|stop\|restart\|status` |
| **Auto-restart** | No | ✅ Reinicia opencode en 3s si muere inesperadamente |
| **Logs del proceso** | stdout/stderr directo | ✅ Ring buffer vía `/process/logs` |
| **Shutdown graceful** | No aplica | ✅ SIGTERM en Linux, Ctrl+C en Windows |
| **Multi-proyecto** | Un proceso, un directorio | ✅ Un proceso opencode por subdirectorio, routing automático por `X-Project-Path` |

---

## OpenCode CLI — API consumida internamente

El gateway se comunica con el proceso `opencode serve` corriendo en `localhost:OPENCODE_PORT`. Estas son las llamadas que hace el código Go en `internal/repository/opencode/client.go`:

Todas las requests agregan `Authorization: Basic` cuando `OPENCODE_SERVER_PASSWORD` está configurado.

| Método | Ruta | Función Go | Descripción |
|--------|------|------------|-------------|
| `GET` | `/global/health` | `GetHealth` | Verifica que OpenCode CLI está activo |
| `GET` | `/project` | `GetProjects` | Lista todos los proyectos registrados |
| `GET` | `/project/current` | `GetCurrentProject` | Proyecto activo actualmente |
| `PATCH` | `/project/:projectID` | — | Actualiza nombre, ícono o comandos del proyecto |
| `POST` | `/project/git/init` | — | Inicializa git en el proyecto actual |
| `GET` | `/vcs` | `GetVcs` | Branch, remote y estado del repositorio |
| `GET` | `/provider` | `GetProviders` | Providers de IA y modelos disponibles |
| `GET` | `/session` | `GetSessions` | Lista sesiones; acepta `?directory=` |
| `POST` | `/session` | `CreateSession` | Crea sesión; acepta `?directory=` — también registra el proyecto si no existe |
| `DELETE` | `/session/:id` | `DeleteSession` | Elimina una sesión |
| `POST` | `/session/:id/abort` | `AbortSession` | Aborta la sesión en curso |
| `POST` | `/session/:id/init` | — | Analiza el proyecto y genera `AGENTS.md` |
| `GET` | `/session/:id/message` | `GetMessages` | Mensajes de una sesión |
| `POST` | `/session/:id/message` | `SendMessage` | Envía mensaje (síncrono) |
| `POST` | `/session/:id/prompt_async` | `SendMessageAsync` | Envía mensaje asíncrono |
| `POST` | `/session/:id/command` | `ExecuteCommand` | Ejecuta comando slash |
| `GET` | `/session/:id/todo` | `GetTodo` | Lista de TODOs de la sesión |
| `GET` | `/session/:id/diff` | `GetDiff` | Git diff; acepta `?messageID=` |
| `POST` | `/session/:id/revert` | `RevertMessage` | Revierte al mensaje anterior |
| `POST` | `/session/:id/permissions/:permId` | `RespondToPermission` | Responde solicitud de permiso |
| `GET` | `/event` | `StreamEvents` | SSE stream perpetuo (sin timeout) |

> **Cómo se registra un proyecto en OpenCode:**
>
> OpenCode no expone un endpoint `POST /project`. El registro ocurre internamente a través de `Project.fromDirectory(directory)`, que se invoca al crear una sesión (`POST /session?directory=<path>`) o al iniciar `opencode serve` en un directorio.
>
> La lógica es:
> - Si el directorio (o algún padre) contiene `.git` → se crea un proyecto independiente con ID basado en el worktree del repositorio git.
> - Si **no** hay `.git` → el directorio se agrupa bajo el proyecto `global` y no aparece como proyecto propio en `GET /project`.
>
> **Para que un directorio aparezca como proyecto independiente:**
> 1. Inicializar git: `git init` dentro del directorio
> 2. Crear una sesión en ese directorio: `POST /session?directory=<path>`
>
> Después de esto, `GET /project` devolverá el nuevo proyecto y el gateway lo incluirá en el pool tras un restart.

> **Fuente:** Rutas definidas en [`packages/opencode/src/server/routes/`](https://github.com/sst/opencode/tree/dev/packages/opencode/src/server/routes) — archivos `project.ts` y `session.ts`. Lógica de registro en [`packages/opencode/src/project/project.ts`](https://github.com/sst/opencode/blob/dev/packages/opencode/src/project/project.ts).

> El proceso OpenCode se inicia con `opencode serve --port OPENCODE_PORT --hostname OPENCODE_HOSTNAME` y es gestionado por `internal/repository/process/manager.go`.

---

## Estructura de archivos

```
opencode-server-gateway/
├── cmd/server/main.go                    ← Entry point, DI, graceful shutdown
├── config/config.go                      ← Carga .env / env vars
├── internal/
│   ├── domain/
│   │   ├── entity.go                     ← Todas las entidades del dominio
│   │   ├── errors.go                     ← Sentinel errors
│   │   └── repository.go                 ← Interfaces de repositorios
│   ├── platform/
│   │   ├── signals_unix.go               ← Linux/Mac: SIGINT + SIGTERM
│   │   └── signals_windows.go            ← Windows: solo SIGINT (Ctrl+C)
│   ├── usecase/
│   │   ├── event_broker.go               ← SSE Broker: reconnect + fan-out
│   │   └── usecases.go                   ← Todos los use cases
│   ├── pool/
│   │   └── pool.go                       ← Project pool: descubrimiento + un proceso por proyecto
│   ├── repository/
│   │   ├── opencode/
│   │   │   ├── client.go                 ← Cliente HTTP a OpenCode
│   │   │   └── routing_client.go         ← RoutingClient: selección por X-Project-Path
│   │   └── process/
│   │       ├── manager.go                ← Gestión del subprocess opencode
│   │       ├── stop_unix.go              ← SIGTERM → wait → SIGKILL (Linux/Mac)
│   │       └── stop_windows.go           ← Kill() directo (Windows)
│   └── delivery/http/
│       ├── router.go                     ← Configuración de rutas Gin
│       ├── middleware/
│       │   ├── apikey.go                 ← Middleware de autenticación
│       │   └── project.go                ← Extrae X-Project-Path al contexto
│       └── handler/                      ← health, session, message, project,
│                                            provider, event, process, register_project
├── make.ps1                              ← Script PowerShell (Windows sin make)
├── Makefile                              ← Linux/Mac o Windows con make instalado
├── .env.example                          ← Plantilla de configuración
└── go.mod
```
