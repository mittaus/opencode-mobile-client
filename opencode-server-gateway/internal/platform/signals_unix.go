//go:build !windows

package platform

import (
	"os"
	"syscall"
)

// ShutdownSignals returns the OS signals that trigger a graceful gateway shutdown.
// On Linux/Mac: Ctrl+C (SIGINT) and kill (SIGTERM) — essential for systemd, Docker, etc.
func ShutdownSignals() []os.Signal {
	return []os.Signal{os.Interrupt, syscall.SIGTERM}
}
