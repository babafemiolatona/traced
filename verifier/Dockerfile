FROM golang:1.21-alpine AS builder

WORKDIR /app
COPY go.mod .
COPY main.go .

RUN go build -o emitter main.go

FROM alpine:latest

WORKDIR /app
COPY --from=builder /app/emitter .
COPY emit.sh .

RUN chmod +x emit.sh

ENTRYPOINT ["./emit.sh"]