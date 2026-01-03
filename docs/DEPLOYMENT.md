# Deployment Guide

This guide covers common deployment scenarios for Signet.

## Tailscale Setup

Tailscale provides secure access to Signet without exposing it to the public internet. All devices on your tailnet can reach Signet via its Tailscale hostname.

### Architecture Note

The UI proxies all API requests to the daemon internally:

```
Browser → UI (:4174) → [proxy] → Daemon (:3000)
```

You only expose the UI. The daemon doesn't need direct external access - it communicates with NIP-46 clients via Nostr relays, not HTTP.

### Configuration

Set `EXTERNAL_URL` to your Tailscale hostname so that `auth_url` responses are reachable from other devices on your tailnet:

```bash
EXTERNAL_URL=http://signet.tailnet-name.ts.net:4174 docker compose up --build
```

Or in `signet.json`:

```json
{
  "baseUrl": "http://signet.tailnet-name.ts.net:4174",
  "allowedOrigins": [
    "http://signet.tailnet-name.ts.net:4174"
  ]
}
```

Replace `signet.tailnet-name.ts.net` with your actual Tailscale hostname (find it with `tailscale status`).

### HTTPS with Tailscale Serve

Some browser features (like clipboard copy) require HTTPS. Tailscale Serve provides automatic TLS certificates for `*.ts.net` domains:

```bash
# Serve the UI over HTTPS
tailscale serve https / http://localhost:4174
```

Then update your config to use HTTPS:

```json
{
  "baseUrl": "https://signet.tailnet-name.ts.net",
  "allowedOrigins": [
    "https://signet.tailnet-name.ts.net"
  ]
}
```

Note: Tailscale Serve on port 443 means you drop the port from URLs.

### When is EXTERNAL_URL needed?

| Setup | EXTERNAL_URL |
|-------|--------------|
| Single machine (Signet + apps on same device) | Not needed (localhost works) |
| Multi-device (Signet on server, apps on phone/laptop) | Required - use Tailscale hostname |

The `auth_url` sent to NIP-46 clients must be reachable from whatever device needs to approve requests. The default `localhost` only works for single-machine setups.

## Wireguard Setup

Wireguard provides secure access to Signet without exposing it to the public internet. This guide assumes you already have a Wireguard VPN configured.

### Architecture Note

The UI proxies all API requests to the daemon internally:

```
Browser → UI (:4174) → [proxy] → Daemon (:3000)
```

You only expose the UI. The daemon doesn't need direct external access - it communicates with NIP-46 clients via Nostr relays, not HTTP.

### Find Your Wireguard IP

Check your Wireguard server's IP address:

```bash
# From your server's Wireguard config
grep Address /etc/wireguard/wg0.conf
# Example output: Address = 10.0.0.1/24

# Or check the active interface
ip addr show wg0
```

Use the server's Wireguard IP (e.g., `10.0.0.1`) - this is reachable from all peers on your VPN.

### Configuration

Set `EXTERNAL_URL` to your Wireguard IP so that `auth_url` responses are reachable from other devices on your VPN:

```bash
EXTERNAL_URL=http://10.0.0.1:4174 docker compose up --build
```

Or in `signet.json`:

```json
{
  "baseUrl": "http://10.0.0.1:4174",
  "allowedOrigins": [
    "http://10.0.0.1:4174"
  ]
}
```

Replace `10.0.0.1` with your actual Wireguard server IP.

### HTTPS Note

Some browser features (like clipboard copy) require HTTPS. Unlike Tailscale, Wireguard doesn't provide automatic TLS certificates. Options:

- **Accept the limitation** - Manual copy/paste still works
- **Add a reverse proxy** - Use Caddy or nginx with Let's Encrypt (requires domain + port forwarding, beyond this guide's scope)
- **Self-signed certificate** - Works but triggers browser warnings

For most private network setups, HTTP is fine.

### When is EXTERNAL_URL needed?

| Setup | EXTERNAL_URL |
|-------|--------------|
| Single machine (Signet + apps on same device) | Not needed (localhost works) |
| Multi-device (Signet on server, apps on phone/laptop) | Required - use Wireguard IP |

The `auth_url` sent to NIP-46 clients must be reachable from whatever device needs to approve requests. The default `localhost` only works for single-machine setups.

## Systemd Services

Run Signet as systemd services for automatic startup and restart on failure.

### Prerequisites

1. Install Signet to `/opt/signet` (or adjust paths in the service files):

```bash
sudo mkdir -p /opt/signet
sudo chown $USER:$USER /opt/signet
git clone https://github.com/Letdown2491/signet /opt/signet
cd /opt/signet
pnpm install
pnpm run build:daemon
pnpm run build:ui
cd apps/signet && pnpm run prisma:migrate
```

2. Create a dedicated user (optional but recommended):

```bash
sudo useradd -r -s /bin/false signet
sudo chown -R signet:signet /opt/signet
sudo chown -R signet:signet ~/.signet-config  # if config already exists
```

### Service Files

Create `/etc/systemd/system/signet-daemon.service`:

```ini
[Unit]
Description=Signet NIP-46 Daemon
After=network.target

[Service]
Type=simple
User=signet
Group=signet
WorkingDirectory=/opt/signet
ExecStart=/usr/bin/pnpm run start:daemon
Restart=always
RestartSec=5
StartLimitBurst=5
StartLimitIntervalSec=60
Environment=NODE_ENV=production

[Install]
WantedBy=multi-user.target
```

Create `/etc/systemd/system/signet-ui.service`:

```ini
[Unit]
Description=Signet Web UI
After=network.target signet-daemon.service

[Service]
Type=simple
User=signet
Group=signet
WorkingDirectory=/opt/signet
ExecStart=/usr/bin/pnpm run start:ui
Restart=always
RestartSec=5
StartLimitBurst=5
StartLimitIntervalSec=60
Environment=NODE_ENV=production

[Install]
WantedBy=multi-user.target
```

### Installation

```bash
# Reload systemd to pick up new service files
sudo systemctl daemon-reload

# Enable services to start on boot
sudo systemctl enable signet-daemon
sudo systemctl enable signet-ui

# Start services
sudo systemctl start signet-daemon
sudo systemctl start signet-ui
```

### Usage

```bash
# Check status
sudo systemctl status signet-daemon
sudo systemctl status signet-ui

# View logs
sudo journalctl -u signet-daemon -f
sudo journalctl -u signet-ui -f

# Restart after updates
sudo systemctl restart signet-daemon
sudo systemctl restart signet-ui

# Stop services
sudo systemctl stop signet-ui
sudo systemctl stop signet-daemon
```

### Notes

- The UI service starts after the daemon (`After=signet-daemon.service`) but doesn't hard-depend on it. If the daemon crashes, the UI stays running and recovers when the daemon restarts.
- Both services use `Restart=always` with a 5-second delay. If a service fails 5 times within 60 seconds, systemd stops trying (prevents runaway restart loops).
- Logs go to journald. Use `journalctl` to view them.
- Adjust `/usr/bin/pnpm` if pnpm is installed elsewhere (check with `which pnpm`).

## Runit Services (Void Linux)

Run Signet as runit services for automatic startup and supervision.

### Prerequisites

Same as systemd setup above - install Signet to `/opt/signet` and optionally create a dedicated user.

### Service Directories

Create `/etc/sv/signet-daemon/run`:

```bash
#!/bin/sh
cd /opt/signet
exec chpst -u signet:signet /usr/bin/pnpm run start:daemon 2>&1
```

Create `/etc/sv/signet-daemon/log/run`:

```bash
#!/bin/sh
exec svlogd -tt /var/log/signet-daemon
```

Create `/etc/sv/signet-ui/run`:

```bash
#!/bin/sh
cd /opt/signet
sv check signet-daemon > /dev/null || exit 1
exec chpst -u signet:signet /usr/bin/pnpm run start:ui 2>&1
```

Create `/etc/sv/signet-ui/log/run`:

```bash
#!/bin/sh
exec svlogd -tt /var/log/signet-ui
```

### Installation

```bash
# Create log directories
sudo mkdir -p /var/log/signet-daemon /var/log/signet-ui

# Make run scripts executable
sudo chmod +x /etc/sv/signet-daemon/run /etc/sv/signet-daemon/log/run
sudo chmod +x /etc/sv/signet-ui/run /etc/sv/signet-ui/log/run

# Enable services (symlink to /var/service)
sudo ln -s /etc/sv/signet-daemon /var/service/
sudo ln -s /etc/sv/signet-ui /var/service/
```

### Usage

```bash
# Check status
sudo sv status signet-daemon
sudo sv status signet-ui

# View logs
sudo tail -f /var/log/signet-daemon/current
sudo tail -f /var/log/signet-ui/current

# Restart services
sudo sv restart signet-daemon
sudo sv restart signet-ui

# Stop services
sudo sv stop signet-ui
sudo sv stop signet-daemon

# Disable services (remove symlink)
sudo rm /var/service/signet-daemon
sudo rm /var/service/signet-ui
```

### Notes

- The UI service checks if the daemon is running before starting (`sv check signet-daemon`). If the daemon isn't up, runit will keep retrying.
- Runit automatically restarts services that exit. No additional configuration needed.
- Logs are managed by `svlogd` with automatic rotation. The `-tt` flag adds timestamps.
- `chpst -u signet:signet` runs the process as the signet user.
