FROM golang:1.27.1-alpine AS builder
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -o /out/coffee-api ./cmd/api && CGO_ENABLED=0 go build -trimpath -o /out/coffee-migrate ./cmd/migrate

FROM alpine:3.22
RUN apk add --no-cache ca-certificates && addgroup -S coffee && adduser -S coffee -G coffee && mkdir -p /data/uploads && chown -R coffee:coffee /data
WORKDIR /app
COPY --from=builder /out/coffee-api /out/coffee-migrate /app/
USER coffee
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s CMD wget -q -O /dev/null http://127.0.0.1:8080/readyz || exit 1
CMD ["/app/coffee-api"]
