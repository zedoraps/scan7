#!/usr/bin/env bash
# Control the headless Android emulators on mini and tunnel them to this Mac.
#   scripts/mini-emulators.sh start     # boot emulators on mini, open tunnels, adb connect
#   scripts/mini-emulators.sh stop      # disconnect, close tunnels, stop emulators on mini
#   scripts/mini-emulators.sh status    # remote status + tunnel + adb state
#   scripts/mini-emulators.sh tunnel    # (re)open tunnels and adb connect only
#   scripts/mini-emulators.sh untunnel  # adb disconnect and close tunnels only
# Serials on the Mac: localhost:5561 / localhost:5563 / localhost:5565 = scan7-emu-1..3.
# All commands are idempotent.
set -u

HOST="${MINI_HOST:-pmo@mini}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
CTL="$HOME/.ssh/scan7-mini-emu.ctl"
SSH_OPTS=(-o BatchMode=yes -o ConnectTimeout=10)
LOCAL_PORTS=(5561 5563 5565)
REMOTE_PORTS=(5555 5557 5559)

# No forwards from ~/.ssh/config (e.g. RemoteForward) on our connections.
remote() { ssh "${SSH_OPTS[@]}" -o ClearAllForwardings=yes "$HOST" "~/android/emulators.sh $1"; }

# The tunnel must carry only our -L forwards, but ClearAllForwardings would drop
# those too, so resolve the host via `ssh -G` and connect without ~/.ssh/config.
resolved_opts() {
  ssh -G "$HOST" 2>/dev/null | awk '
    $1=="hostname"{print "-o"; print "HostName="$2}
    $1=="user"{print "-o"; print "User="$2}
    $1=="port"{print "-o"; print "Port="$2}
    $1=="identityfile"{print "-i"; print $2}'
}

tunnel_up() { ssh -F /dev/null -S "$CTL" -O check "$HOST" >/dev/null 2>&1; }

tunnel() {
  if tunnel_up; then
    echo "tunnel already open ($CTL)"
  else
    rm -f "$CTL"
    local fwd=() i
    for i in 0 1 2; do fwd+=(-L "127.0.0.1:${LOCAL_PORTS[$i]}:127.0.0.1:${REMOTE_PORTS[$i]}"); done
    local opts=() o
    while IFS= read -r o; do opts+=("${o/#\~/$HOME}"); done < <(resolved_opts)
    ssh -F /dev/null "${opts[@]}" "${SSH_OPTS[@]}" -f -N -M -S "$CTL" -o ControlPersist=no \
      -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=3 \
      "${fwd[@]}" "$HOST" || { echo "failed to open tunnel" >&2; return 1; }
    echo "tunnel open: Mac ${LOCAL_PORTS[*]} -> mini ${REMOTE_PORTS[*]}"
  fi
  local p
  for p in "${LOCAL_PORTS[@]}"; do
    if "$ADB" devices | grep -q "^localhost:$p[[:space:]]*device"; then
      echo "localhost:$p already connected"
    else
      "$ADB" disconnect "localhost:$p" >/dev/null 2>&1   # drop stale offline entry
      "$ADB" connect "localhost:$p"
    fi
  done
}

untunnel() {
  local p
  for p in "${LOCAL_PORTS[@]}"; do "$ADB" disconnect "localhost:$p" >/dev/null 2>&1 || true; done
  if tunnel_up; then ssh -F /dev/null -S "$CTL" -O exit "$HOST" 2>/dev/null; echo "tunnel closed"; else echo "tunnel not open"; fi
  rm -f "$CTL"
}

status() {
  echo "== mini"; remote status
  echo "== tunnel"; if tunnel_up; then echo "open ($CTL)"; else echo "closed"; fi
  echo "== adb (Mac)"; "$ADB" devices | grep -E "^localhost:556[135]" || echo "no mini emulators connected"
}

case "${1:-status}" in
  start)    remote start && tunnel ;;
  stop)     untunnel; remote stop ;;
  status)   status ;;
  tunnel)   tunnel ;;
  untunnel) untunnel ;;
  *) echo "usage: $0 start|stop|status|tunnel|untunnel" >&2; exit 2 ;;
esac
