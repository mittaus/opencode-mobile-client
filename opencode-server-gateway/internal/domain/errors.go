package domain

import "errors"

var (
	ErrNotFound     = errors.New("not found")
	ErrUnauthorized = errors.New("unauthorized")
	ErrBadRequest   = errors.New("bad request")
	ErrInternal     = errors.New("internal error")
	ErrConflict     = errors.New("conflict")
	ErrUnavailable  = errors.New("opencode service unavailable")
)
