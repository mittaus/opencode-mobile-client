package usecase

import (
	"bufio"
	"fmt"
	"os/exec"
	"strings"

	"github.com/igniteLabs/opencode-gateway/internal/domain"
)

// gitDiff runs "git diff HEAD" in dir and returns parsed file diffs.
// Falls back to "git diff --cached" when HEAD doesn't exist yet (empty repo).
func gitDiff(dir string) ([]domain.FileDiff, error) {
	out, err := runGit(dir, "diff", "HEAD")
	if err != nil {
		// HEAD may not exist on a brand-new repo — try staged changes
		out, err = runGit(dir, "diff", "--cached")
		if err != nil {
			return nil, fmt.Errorf("git diff: %w", err)
		}
	}
	diffs := parseUnifiedDiff(out)

	// Also surface untracked (new) files that git diff HEAD won't include.
	// For each one, run "git diff --no-index -- /dev/null <file>" to get its
	// full content rendered as additions.
	untracked, _ := runGit(dir, "ls-files", "--others", "--exclude-standard")
	for _, path := range strings.Split(strings.TrimSpace(untracked), "\n") {
		path = strings.TrimSpace(path)
		if path == "" {
			continue
		}
		fileDiffs := untrackedDiff(dir, path)
		diffs = append(diffs, fileDiffs...)
	}

	return diffs, nil
}

// untrackedDiff returns a FileDiff for a file that is not yet tracked by git.
// It uses "git diff --no-index -- /dev/null <file>" which works cross-platform
// with Git for Windows (Git translates /dev/null internally).
func untrackedDiff(dir, path string) []domain.FileDiff {
	out, _ := runGit(dir, "diff", "--no-index", "--", "/dev/null", path)
	if out == "" {
		// Empty file — return stub with zero counts and no patch
		return []domain.FileDiff{{Path: path, Status: "added"}}
	}
	parsed := parseUnifiedDiff(out)
	// Force status to "added" regardless of what the parser inferred
	for i := range parsed {
		parsed[i].Status = "added"
		if parsed[i].Path == "" {
			parsed[i].Path = path
		}
	}
	return parsed
}

// gitStage runs "git add -- <paths...>" in dir.
func gitStage(dir string, paths []string) error {
	args := append([]string{"add", "--"}, paths...)
	cmd := exec.Command("git", args...)
	cmd.Dir = dir
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("git add: %w\n%s", err, string(out))
	}
	return nil
}

// gitDiscard discards working-tree changes for a file.
// For untracked (added) files it removes them with "git clean -fd".
// For tracked files it restores the working copy with "git restore".
func gitDiscard(dir, path, status string) error {
	var cmd *exec.Cmd
	if status == "added" {
		cmd = exec.Command("git", "clean", "-fd", "--", path)
	} else {
		cmd = exec.Command("git", "restore", "--", path)
	}
	cmd.Dir = dir
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("git discard: %w\n%s", err, string(out))
	}
	return nil
}

// gitUnstage runs "git restore --staged -- <paths...>" in dir.
func gitUnstage(dir string, paths []string) error {
	args := append([]string{"restore", "--staged", "--"}, paths...)
	cmd := exec.Command("git", args...)
	cmd.Dir = dir
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("git restore --staged: %w\n%s", err, string(out))
	}
	return nil
}

// gitCommit runs "git commit -m <message>" in dir.
func gitCommit(dir string, message string) error {
	cmd := exec.Command("git", "commit", "-m", message)
	cmd.Dir = dir
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("git commit: %w\n%s", err, string(out))
	}
	return nil
}

// gitGetStagedPaths returns the list of paths staged for commit.
func gitGetStagedPaths(dir string) ([]string, error) {
	out, err := runGit(dir, "diff", "--cached", "--name-only")
	if err != nil {
		return nil, err
	}
	var paths []string
	for _, p := range strings.Split(strings.TrimSpace(out), "\n") {
		p = strings.TrimSpace(p)
		if p != "" {
			paths = append(paths, p)
		}
	}
	return paths, nil
}

func runGit(dir string, args ...string) (string, error) {
	cmd := exec.Command("git", args...)
	cmd.Dir = dir
	out, err := cmd.Output()
	if err != nil {
		// git diff returns exit code 1 when there are differences — that's normal
		if len(out) > 0 {
			return string(out), nil
		}
		return "", err
	}
	return string(out), nil
}

// parseUnifiedDiff parses the output of "git diff" into a slice of FileDiff.
func parseUnifiedDiff(patch string) []domain.FileDiff {
	var diffs []domain.FileDiff
	var cur *domain.FileDiff
	var patchBuf []string

	commit := func() {
		if cur == nil {
			return
		}
		cur.Patch = strings.Join(patchBuf, "\n")
		diffs = append(diffs, *cur)
		cur = nil
		patchBuf = nil
	}

	scanner := bufio.NewScanner(strings.NewReader(patch))
	for scanner.Scan() {
		line := scanner.Text()

		if strings.HasPrefix(line, "diff --git ") {
			commit()
			cur = &domain.FileDiff{Status: "modified"}
			patchBuf = []string{line}
			continue
		}

		if cur == nil {
			continue
		}
		patchBuf = append(patchBuf, line)

		switch {
		case strings.HasPrefix(line, "new file mode"):
			cur.Status = "added"
		case strings.HasPrefix(line, "deleted file mode"):
			cur.Status = "deleted"
		case strings.HasPrefix(line, "similarity index"):
			cur.Status = "renamed"
		case strings.HasPrefix(line, "+++ b/"):
			cur.Path = strings.TrimPrefix(line, "+++ b/")
		case strings.HasPrefix(line, "+++ /dev/null"):
			cur.Status = "deleted"
		case strings.HasPrefix(line, "--- a/") && cur.Path == "":
			// For deleted files +++ is /dev/null, grab path from ---
			cur.Path = strings.TrimPrefix(line, "--- a/")
		default:
			if len(line) > 0 && !strings.HasPrefix(line, "@@") &&
				!strings.HasPrefix(line, "---") && !strings.HasPrefix(line, "+++") {
				switch line[0] {
				case '+':
					cur.Additions++
				case '-':
					cur.Deletions++
				}
			}
		}
	}
	commit()
	return diffs
}
