package config

import (
	"fmt"
	"log"
	"os"
	"strconv"

	"github.com/joho/godotenv"
)

// Config holds all gateway and OpenCode CLI settings.
type Config struct {
	// Gateway
	GatewayPort string
	APIKey      string

	// OpenCode CLI process management
	OpenCodeBin      string // path to opencode binary (e.g., "opencode" or "/usr/local/bin/opencode")
	OpenCodePort     int
	OpenCodeHostname string // hostname opencode binds to (passed to --hostname flag)
	OpenCodePassword string
	OpenCodeProject  string // working directory for opencode serve
	AutoStart        bool   // auto-start opencode on gateway boot

	// Derived
	OpenCodeBaseURL string // always uses 127.0.0.1 for client connections
}

// Load reads config from .env file (if present) and environment variables.
func Load() *Config {
	if err := godotenv.Load(); err != nil {
		log.Println("[config] no .env file found, reading from environment")
	}

	opencodePort, _ := strconv.Atoi(getEnv("OPENCODE_PORT", "4096"))

	rawProject := os.Getenv("OPENCODE_PROJECT")
	log.Printf("[config] raw OPENCODE_PROJECT from os.Getenv = %q (len=%d)", rawProject, len(rawProject))

	cfg := &Config{
		GatewayPort:      getEnv("GATEWAY_PORT", "8080"),
		APIKey:           getEnv("GATEWAY_API_KEY", ""),
		OpenCodeBin:      getEnv("OPENCODE_BIN", "opencode"),
		OpenCodePort:     opencodePort,
		OpenCodeHostname: getEnv("OPENCODE_HOSTNAME", "127.0.0.1"),
		OpenCodePassword: getEnv("OPENCODE_SERVER_PASSWORD", ""),
		OpenCodeProject:  getEnv("OPENCODE_PROJECT", ""),
		AutoStart:        getEnv("OPENCODE_AUTO_START", "false") == "true",
	}

	log.Printf("[config] OpenCodeProject=%q AutoStart=%v OpenCodePort=%d", cfg.OpenCodeProject, cfg.AutoStart, cfg.OpenCodePort)

	// Always connect via 127.0.0.1 regardless of the bind hostname.
	// OPENCODE_HOSTNAME controls what opencode binds to (e.g. 0.0.0.0),
	// but the gateway client always talks to localhost.
	cfg.OpenCodeBaseURL = fmt.Sprintf("http://127.0.0.1:%d", cfg.OpenCodePort)

	if cfg.APIKey == "" {
		log.Fatal("[config] GATEWAY_API_KEY is required — set it in .env or as environment variable")
	}

	return cfg
}

func getEnv(key, defaultVal string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return defaultVal
}
