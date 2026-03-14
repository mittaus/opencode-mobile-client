//go:build windows

package process

import (
	"fmt"
	"log"
	"os"
	"os/exec"
	"strings"
)

// stopProcess on Windows uses taskkill /F /T to kill the process tree,
// ensuring child processes (like opencode's HTTP server) are also terminated.
func stopProcess(p *os.Process) error {
	pid := p.Pid
	log.Printf("[process] taskkill /F /T /PID %d", pid)
	out, err := exec.Command("taskkill", "/F", "/T", "/PID", fmt.Sprintf("%d", pid)).CombinedOutput()
	if err != nil {
		log.Printf("[process] taskkill output: %s", string(out))
		// "not found" means the process is already dead — treat as success.
		if strings.Contains(string(out), "not found") || strings.Contains(string(out), "not exist") {
			_ = p.Release()
			return nil
		}
		// Fall back to p.Kill()
		if killErr := p.Kill(); killErr != nil {
			// "invalid argument" means the OS handle is already invalid (process gone).
			if strings.Contains(killErr.Error(), "invalid argument") {
				return nil
			}
			return killErr
		}
		return nil
	}
	log.Printf("[process] taskkill ✓: %s", string(out))
	_ = p.Release()
	return nil
}

// killByPort finds and terminates the process listening on the given TCP port.
func killByPort(port int) error {
	// netstat -ano lists PID for each listening port
	out, err := exec.Command("netstat", "-ano").CombinedOutput()
	if err != nil {
		return fmt.Errorf("netstat: %w", err)
	}
	target := fmt.Sprintf(":%d", port)
	for _, line := range strings.Split(string(out), "\n") {
		if !strings.Contains(line, target) {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) < 5 {
			continue
		}
		pid := fields[len(fields)-1]
		if pid == "0" {
			continue
		}
		log.Printf("[process] killByPort: taskkill /F /T /PID %s (port %d)", pid, port)
		ko, ke := exec.Command("taskkill", "/F", "/T", "/PID", pid).CombinedOutput()
		if ke != nil {
			log.Printf("[process] killByPort taskkill output: %s", string(ko))
		} else {
			log.Printf("[process] killByPort ✓: %s", strings.TrimSpace(string(ko)))
		}
		return nil
	}
	return fmt.Errorf("no process found on port %d", port)
}
