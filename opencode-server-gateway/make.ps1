# make.ps1 — Reemplaza el Makefile para entornos Windows sin 'make' instalado.
# Uso: .\make.ps1 <comando>
# Comandos disponibles: run, build, tidy, clean

param([string]$Command = "help")

$BINARY   = "opencode-gateway.exe"
$CMD_PATH = ".\cmd\server\main.go"

# Activar Go si se usa gvm (ajusta la versión si es necesario)
function Activate-Go {
    try {
        $null = Get-Command go -ErrorAction Stop
    } catch {
        Write-Host "Activando Go via gvm..." -ForegroundColor Yellow
        gvm --format=powershell 1.24.2 | Invoke-Expression
    }
}

switch ($Command) {

    "run" {
        Activate-Go
        Write-Host "[run] Iniciando gateway..." -ForegroundColor Cyan
        go run $CMD_PATH
    }

    "build" {
        Activate-Go
        Write-Host "[build] Compilando $BINARY..." -ForegroundColor Cyan
        go build -o $BINARY $CMD_PATH
        if ($LASTEXITCODE -eq 0) {
            $size = [math]::Round((Get-Item $BINARY).Length / 1MB, 1)
            Write-Host "[build] OK → $BINARY ($size MB)" -ForegroundColor Green
        }
    }

    "tidy" {
        Activate-Go
        Write-Host "[tidy] Descargando dependencias..." -ForegroundColor Cyan
        go mod tidy
    }

    "clean" {
        Write-Host "[clean] Eliminando binario..." -ForegroundColor Cyan
        Remove-Item -Force -ErrorAction SilentlyContinue $BINARY
        Write-Host "[clean] OK" -ForegroundColor Green
    }

    "vet" {
        Activate-Go
        Write-Host "[vet] Analizando código..." -ForegroundColor Cyan
        go vet ./...
    }

    "env" {
        Activate-Go
        Write-Host "[env] Variables de entorno Go:" -ForegroundColor Cyan
        go env
    }

    default {
        Write-Host ""
        Write-Host "Uso: .\make.ps1 <comando>" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "Comandos disponibles:" -ForegroundColor White
        Write-Host "  run     Inicia el gateway (requiere .env)"
        Write-Host "  build   Compila el binario opencode-gateway.exe"
        Write-Host "  tidy    Descarga y actualiza dependencias"
        Write-Host "  clean   Elimina el binario compilado"
        Write-Host "  vet     Analiza el código con go vet"
        Write-Host "  env     Muestra variables de entorno Go"
        Write-Host ""
    }
}
