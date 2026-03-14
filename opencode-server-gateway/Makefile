BINARY=opencode-gateway
CMD=./cmd/server

.PHONY: run build tidy clean lint

## run: start the gateway (requires .env)
run:
	go run $(CMD)/main.go

## build: compile the binary
build:
	go build -o $(BINARY) $(CMD)/main.go

## tidy: download and tidy dependencies
tidy:
	go mod tidy

## clean: remove compiled binary
clean:
	rm -f $(BINARY) $(BINARY).exe

## lint: run golangci-lint
lint:
	golangci-lint run ./...
