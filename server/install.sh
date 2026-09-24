#!/usr/bin/env bash
# Pocket Relay ntfy server installer
# https://github.com/asadullahbro/pocket-relay
#
# Usage:
#   curl -sSL https://get.asdl.website | DOMAIN=ntfy.example.com bash
#   curl -sSL https://get.asdl.website | bash -s -- ntfy.example.com
#
# This version string is stamped at release time by .github/workflows/release.yml
# (replaces @@VERSION@@ with the git tag). "dev" means an unreleased checkout.
INSTALLER_VERSION="@@VERSION@@"

set -euo pipefail

if [[ "${1:-}" == "--version" || "${1:-}" == "-v" ]]; then
  echo "pocket-relay installer $INSTALLER_VERSION"
  exit 0
fi

DOMAIN="${DOMAIN:-${1:-}}"
TOPIC="${TOPIC:-sms-forward}"
NTFY_USER="${NTFY_USER:-forwarder}"
LISTEN_PORT="${LISTEN_PORT:-2586}"
NTFY_MIN_VERSION="${NTFY_MIN_VERSION:-}" # optional pin, e.g. v2.28.0

log()  { echo "==> $*"; }
warn() { echo "!! $*" >&2; }
die()  { echo "Error: $*" >&2; exit 1; }

echo "pocket-relay installer $INSTALLER_VERSION"

if [[ -z "$DOMAIN" ]]; then
  if [[ -e /dev/tty ]]; then
    read -rp "Domain for this ntfy server (must already point at this host's IP): " DOMAIN </dev/tty || true
  fi
fi
[[ -n "$DOMAIN" ]] || die "no domain given. Usage: DOMAIN=ntfy.example.com bash -s, or pass as first arg."

if [[ "$(id -u)" -eq 0 ]]; then
  SUDO=""
else
  command -v sudo >/dev/null 2>&1 || die "this installer needs root or sudo."
  SUDO="sudo"
fi

# ---- platform detection ------------------------------------------------

OS_KERNEL="$(uname -s)"
[[ "$OS_KERNEL" == "Linux" ]] || die "this installer only supports Linux servers (found: $OS_KERNEL). For local/desktop use, see https://ntfy.sh/docs/install/"

ARCH_RAW="$(uname -m)"
case "$ARCH_RAW" in
  x86_64|amd64)   NTFY_ARCH=amd64 ;;
  aarch64|arm64)  NTFY_ARCH=arm64 ;;
  armv7l|armhf)   NTFY_ARCH=armv7 ;;
  *) die "unsupported CPU architecture: $ARCH_RAW" ;;
esac

PKG_FAMILY="unknown"
if [[ -r /etc/os-release ]]; then
  # shellcheck disable=SC1091
  . /etc/os-release
  ID_ALL="${ID:-} ${ID_LIKE:-}"
  if [[ "$ID_ALL" == *debian* || "$ID_ALL" == *ubuntu* ]]; then
    PKG_FAMILY="deb"
  elif [[ "$ID_ALL" == *rhel* || "$ID_ALL" == *fedora* || "$ID_ALL" == *centos* ]]; then
    PKG_FAMILY="rpm"
  elif [[ "$ID_ALL" == *alpine* ]]; then
    PKG_FAMILY="tarball" # ntfy publishes no apk package
  fi
fi
[[ "$PKG_FAMILY" != "unknown" ]] && log "Detected platform: ${PRETTY_NAME:-$ID} ($PKG_FAMILY, $NTFY_ARCH)" \
  || { warn "Unrecognized distro, falling back to a generic binary install."; PKG_FAMILY="tarball"; }

command -v systemctl >/dev/null 2>&1 || die "this installer requires systemd."

# ---- fetch release info -------------------------------------------------

if [[ -n "$NTFY_MIN_VERSION" ]]; then
  NTFY_VERSION="$NTFY_MIN_VERSION"
else
  NTFY_VERSION="$(curl -fsSL https://api.github.com/repos/binwiederhier/ntfy/releases/latest \
    | grep -oE '"tag_name":[[:space:]]*"v[^"]+"' | head -1 | cut -d'"' -f4)"
fi
[[ -n "$NTFY_VERSION" ]] || die "couldn't determine latest ntfy version."
NTFY_VERSION_NUM="${NTFY_VERSION#v}"

# ---- install ntfy --------------------------------------------------------

if command -v ntfy >/dev/null 2>&1; then
  log "ntfy already installed ($(ntfy --version | head -1))"
else
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TMP_DIR"' EXIT

  case "$PKG_FAMILY" in
    deb)
      log "Installing ntfy $NTFY_VERSION (.deb)"
      URL="https://github.com/binwiederhier/ntfy/releases/download/${NTFY_VERSION}/ntfy_${NTFY_VERSION_NUM}_linux_${NTFY_ARCH}.deb"
      curl -fsSL "$URL" -o "$TMP_DIR/ntfy.deb"
      $SUDO dpkg -i "$TMP_DIR/ntfy.deb"
      ;;
    rpm)
      log "Installing ntfy $NTFY_VERSION (.rpm)"
      RPM_ARCH="$NTFY_ARCH"; [[ "$RPM_ARCH" == "amd64" ]] && RPM_ARCH="x86_64"; [[ "$RPM_ARCH" == "arm64" ]] && RPM_ARCH="aarch64"
      URL="https://github.com/binwiederhier/ntfy/releases/download/${NTFY_VERSION}/ntfy_${NTFY_VERSION_NUM}_linux_${NTFY_ARCH}.rpm"
      curl -fsSL "$URL" -o "$TMP_DIR/ntfy.rpm"
      $SUDO rpm -i "$TMP_DIR/ntfy.rpm" 2>/dev/null || $SUDO dnf install -y "$TMP_DIR/ntfy.rpm" 2>/dev/null || $SUDO yum install -y "$TMP_DIR/ntfy.rpm"
      ;;
    tarball)
      log "Installing ntfy $NTFY_VERSION (generic Linux binary)"
      URL="https://github.com/binwiederhier/ntfy/releases/download/${NTFY_VERSION}/ntfy_${NTFY_VERSION_NUM}_linux_${NTFY_ARCH}.tar.gz"
      curl -fsSL "$URL" -o "$TMP_DIR/ntfy.tar.gz"
      tar -xzf "$TMP_DIR/ntfy.tar.gz" -C "$TMP_DIR"
      $SUDO install -m 755 "$TMP_DIR"/ntfy_*/ntfy /usr/local/bin/ntfy
      $SUDO id ntfy >/dev/null 2>&1 || $SUDO useradd --system --no-create-home --shell /usr/sbin/nologin ntfy
      $SUDO mkdir -p /etc/ntfy /var/cache/ntfy /var/lib/ntfy
      $SUDO chown ntfy:ntfy /var/cache/ntfy /var/lib/ntfy
      $SUDO tee /etc/systemd/system/ntfy.service > /dev/null <<'UNIT'
[Unit]
Description=ntfy server
After=network.target

[Service]
ExecStart=/usr/local/bin/ntfy serve --config /etc/ntfy/server.yml
User=ntfy
Group=ntfy
Restart=on-failure
RestartSec=5
ProtectSystem=strict
ReadWritePaths=/var/cache/ntfy /var/lib/ntfy

[Install]
WantedBy=multi-user.target
UNIT
      $SUDO systemctl daemon-reload
      ;;
  esac
fi

# ---- configure ntfy -------------------------------------------------------

log "Writing /etc/ntfy/server.yml"
$SUDO mkdir -p /etc/ntfy
$SUDO tee /etc/ntfy/server.yml > /dev/null <<EOF
base-url: "https://${DOMAIN}"
listen-http: "127.0.0.1:${LISTEN_PORT}"
behind-proxy: true

cache-file: "/var/cache/ntfy/cache.db"
cache-duration: "12h"

auth-file: "/var/lib/ntfy/auth.db"
auth-default-access: "deny-all"
enable-signup: false
enable-login: false

# Lets the official iOS/Android apps receive push through ntfy.sh's
# Firebase/APNs relay. Only the topic name + message ID are sent there
# to trigger a "wake up and fetch" poke -- message content stays here.
upstream-base-url: "https://ntfy.sh"
EOF

log "Starting ntfy"
$SUDO systemctl enable --now ntfy
$SUDO systemctl restart ntfy

# ---- reverse proxy + TLS ---------------------------------------------------

if command -v nginx >/dev/null 2>&1; then
  log "Configuring nginx for ${DOMAIN}"
  VHOST_BODY="server {
    listen 80;
    server_name ${DOMAIN};

    location / {
        proxy_pass http://127.0.0.1:${LISTEN_PORT};
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection \"upgrade\";
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_read_timeout 3600;
        client_max_body_size 20M;
    }
}"

  if [[ -d /etc/nginx/sites-available ]]; then
    # Debian/Ubuntu convention
    echo "$VHOST_BODY" | $SUDO tee /etc/nginx/sites-available/"$DOMAIN" > /dev/null
    $SUDO ln -sf /etc/nginx/sites-available/"$DOMAIN" /etc/nginx/sites-enabled/"$DOMAIN"
  else
    # RHEL/Fedora convention: no sites-available, just conf.d
    echo "$VHOST_BODY" | $SUDO tee /etc/nginx/conf.d/"$DOMAIN".conf > /dev/null
  fi

  $SUDO nginx -t && $SUDO systemctl reload nginx

  if command -v certbot >/dev/null 2>&1; then
    log "Requesting TLS certificate via certbot"
    $SUDO certbot --nginx -d "$DOMAIN" --non-interactive --agree-tos -m "admin@${DOMAIN#*.}" --redirect \
      || warn "certbot failed -- if DNS for ${DOMAIN} doesn't point here yet, fix that and re-run: sudo certbot --nginx -d ${DOMAIN}"
  else
    warn "certbot not found. Install it, then run: sudo certbot --nginx -d ${DOMAIN}"
    [[ "$PKG_FAMILY" == "deb" ]] && warn "  e.g. sudo apt install certbot python3-certbot-nginx"
    [[ "$PKG_FAMILY" == "rpm" ]] && warn "  e.g. sudo dnf install certbot python3-certbot-nginx"
  fi
else
  warn "nginx not found -- point your reverse proxy at 127.0.0.1:${LISTEN_PORT} for ${DOMAIN} manually (any proxy works, nginx isn't required)."
fi

# ---- ntfy user + token -----------------------------------------------------

log "Creating scoped user '${NTFY_USER}' for topic '${TOPIC}'"
if $SUDO ntfy user list 2>/dev/null | grep -q "^user ${NTFY_USER}"; then
  log "User '${NTFY_USER}' already exists, leaving password as-is"
  PASSWORD="(unchanged -- run: sudo ntfy user change-pass ${NTFY_USER})"
else
  PASSWORD="$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-20)"
  printf '%s\n%s\n' "$PASSWORD" "$PASSWORD" | $SUDO ntfy user add --role=user "$NTFY_USER"
fi
$SUDO ntfy access "$NTFY_USER" "$TOPIC" rw
TOKEN="$($SUDO ntfy token add "$NTFY_USER" | grep -oE 'tk_[A-Za-z0-9]+' | head -1)"

cat <<SUMMARY

============================================================
 ntfy relay is live: https://${DOMAIN}
 Topic:    ${TOPIC}
 User:     ${NTFY_USER}
 Password: ${PASSWORD}
 Token:    ${TOKEN}

 Android app: use the token as a Bearer token.
 iOS/Android ntfy app: log in with the username/password above.
============================================================
SUMMARY
