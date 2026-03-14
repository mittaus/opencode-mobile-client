//go:build !windows

package process

import (
	"fmt"
	"log"
	"os"
	"os/exec"
	"strings"
	"syscall"
	"time"
)

// stopProcess sends SIGTERM to request a graceful exit, then waits up to 8 seconds.
// If the process hasn't exited by then, it sends SIGKILL (force kill).
// This allows opencode to flush state and clean up before dying.
func stopProcess(p *os.Process) error {
	log.Printf("[process] sending SIGTERM to PID %d (graceful shutdown)…", p.Pid)

	if err := p.Signal(syscall.SIGTERM); err != nil {
		// Process may have already died — try force kill anyway
		log.Printf("[process] SIGTERM failed (%v), forcing Kill", err)
		return p.Kill()
	}

	// Wait for graceful exit in a goroutine
	exited := make(chan error, 1)
	go func() {
		_, err := p.Wait()
		exited <- err
	}()

	select {
	case err := <-exited:
		log.Printf("[process] opencode exited gracefully after SIGTERM")
		return err
	case <-time.After(8 * time.Second):
		log.Printf("[process] opencode did not exit after 8s — sending SIGKILL")
		return p.Kill()
	}
}

// killByPort finds and terminates the process listening on the given TCP port using lsof/fuser.
func killByPort(port int) error {
	// lsof -t -i :<port> returns just the PID(s)
	out, err := exec.Command("lsof", "-t", fmt.Sprintf("-i:%d", port)).CombinedOutput()
	if err != nil || strings.TrimSpace(string(out)) == "" {
		return fmt.Errorf("no process found on port %d", port)
	}
	for _, pidStr := range strings.Fields(string(out)) {
		log.Printf("[process] killByPort: kill -9 %s (port %d)", pidStr, port)
		ko, ke := exec.Command("kill", "-9", pidStr).CombinedOutput()
		if ke != nil {
			log.Printf("[process] killByPort kill output: %s", string(ko))
		} else {
			log.Printf("[process] killByPort ✓ killed PID %s", pidStr)
		}
	}
	return nil
}
