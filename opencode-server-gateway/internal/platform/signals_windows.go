//go:build windows

package platform

import "os"

// ShutdownSignals returns the OS signals that trigger a graceful gateway shutdown.
// On Windows: only Ctrl+C (os.Interrupt) — SIGTERM is not supported by Windows signal handling.
func ShutdownSignals() []os.Signal {
	return []os.Signal{os.Interrupt}
}
