FROM golang:1.21-bookworm AS builder
WORKDIR /app

COPY go.* ./
RUN go mod download

COPY . ./

# Build the binary.
RUN go build -v -o server

RUN wget https://github.com/minio/certgen/releases/latest/download/certgen-linux-amd64 -O /tmp/certgen \
    && chmod +x /tmp/certgen

RUN /tmp/certgen -host "127.0.0.1,gateway-service,api.mypremiumdealership.com,mypremiumdealership.com"
RUN ls -la

FROM debian:bookworm-slim
SHELL ["/bin/bash", "-c"]
WORKDIR /app

RUN set -x && apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y \
    ca-certificates libcap2-bin && \
    rm -rf /var/lib/apt/lists/* && \
    useradd --system --no-create-home --uid 10001 app

# Copy the binary to the production image from the builder stage.
COPY --from=builder /app/server /app/server
COPY --from=builder --chown=app:app /app/public.crt /app/server.crt
COPY --from=builder --chown=app:app /app/private.key /app/server.key

# Allow the non-root user to bind the privileged port 443.
RUN setcap cap_net_bind_service=+ep /app/server

EXPOSE 443

USER app

CMD ["/app/server"]

