#!/usr/bin/env bash
# =============================================================================
# AquaOps: READ-ONLY checks before installing k3s on the test VM
# (SCRUM-56 / NFR-09, E5). Changes nothing, starts or stops nothing.
#
#   bash deploy/k3s/preflight.sh            # as the VM administrator
#
# Exit code: 0 = no blocker (warnings may remain), 1 = at least one blocker.
# Docker checks need read access to Docker (sudo or the docker group): without
# it they are reported as warnings, not run.
# =============================================================================
set -uo pipefail

BLOCKERS=0
WARNINGS=0
ok()      { printf '  OK    %s\n' "$*"; }
warn()    { printf '  WARN  %s\n' "$*"; WARNINGS=$((WARNINGS + 1)); }
blocker() { printf '  STOP  %s\n' "$*"; BLOCKERS=$((BLOCKERS + 1)); }

# The two ranges given to k3s in deploy/k3s/config.yaml.
K3S_RANGES="10.42. 10.43."

echo "== System"
if [ -r /etc/os-release ]; then
  # shellcheck disable=SC1091
  . /etc/os-release
  case "${ID:-}-${VERSION_ID:-}" in
    ubuntu-24.04) ok "Ubuntu 24.04" ;;
    *) warn "OS is ${PRETTY_NAME:-unknown}: procedure written for Ubuntu 24.04" ;;
  esac
fi
if [ -f /sys/fs/cgroup/cgroup.controllers ]; then ok "cgroup v2"; else warn "cgroup v2 not detected"; fi

cpus="$(nproc)"
if [ "$cpus" -ge 2 ]; then ok "$cpus CPU"; else blocker "$cpus CPU: k3s needs at least 2"; fi

mem_total_mb=$(( $(awk '/^MemTotal:/ {print $2}' /proc/meminfo) / 1024 ))
mem_avail_mb=$(( $(awk '/^MemAvailable:/ {print $2}' /proc/meminfo) / 1024 ))
swap_mb=$(( $(awk '/^SwapTotal:/ {print $2}' /proc/meminfo) / 1024 ))
if [ "$mem_total_mb" -ge 7500 ]; then ok "RAM ${mem_total_mb} MiB"; else warn "RAM ${mem_total_mb} MiB: config.yaml assumes 8 GiB"; fi
if   [ "$mem_avail_mb" -ge 1500 ]; then ok "available now: ${mem_avail_mb} MiB"
elif [ "$mem_avail_mb" -ge 900 ];  then warn "available now: ${mem_avail_mb} MiB (k3s alone needs about 600 MiB)"
else blocker "available now: ${mem_avail_mb} MiB: not enough for k3s next to Docker Compose"
fi
if [ "$swap_mb" -eq 0 ]; then ok "no swap"; else warn "swap ${swap_mb} MiB: accepted by k3s, but memory limits become less strict"; fi

disk_free_gb=$(df -BG --output=avail /var/lib | tail -1 | tr -dc '0-9')
if [ "$disk_free_gb" -ge 15 ]; then ok "/var/lib: ${disk_free_gb} GB free"; else blocker "/var/lib: ${disk_free_gb} GB free, need 15 GB (k3s images + volumes)"; fi

echo "== k3s"
if command -v k3s > /dev/null 2>&1; then
  warn "k3s already installed: $(k3s --version 2>/dev/null | head -1)"
else
  ok "k3s not installed yet"
fi
if [ -e /etc/rancher/k3s/config.yaml ]; then
  mode="$(stat -c '%a %U' /etc/rancher/k3s/config.yaml)"
  if [ "$mode" = "600 root" ]; then ok "config.yaml in place (600 root)"; else warn "config.yaml is $mode, expected 600 root"; fi
else
  warn "/etc/rancher/k3s/config.yaml not installed yet (step 2 of the README)"
fi

echo "== Ports k3s will use (TCP 6443 API, 10250 kubelet, 80/443 Traefik; UDP 8472 flannel)"
for p in 6443 10250 80 443; do
  if ss -H -ltn "sport = :$p" 2>/dev/null | grep -q .; then
    blocker "TCP $p already in use: $(ss -H -ltnp "sport = :$p" 2>/dev/null | awk '{print $4}' | head -1)"
  else
    ok "TCP $p free"
  fi
done
if ss -H -lun "sport = :8472" 2>/dev/null | grep -q .; then blocker "UDP 8472 already in use"; else ok "UDP 8472 free"; fi

echo "== Networks (k3s uses 10.42.0.0/16 and 10.43.0.0/16)"
for r in $K3S_RANGES; do
  if ip -4 route 2>/dev/null | grep -q "^${r}"; then blocker "a host route already uses ${r}x"; else ok "no host route in ${r}0.0/16"; fi
done

echo "== Docker Compose stack (must keep running)"
if docker info > /dev/null 2>&1; then
  running="$(docker ps -q | wc -l)"
  unhealthy="$(docker ps --filter health=unhealthy -q | wc -l)"
  ok "Docker reachable: $running running container(s), $unhealthy unhealthy"
  [ "$unhealthy" -eq 0 ] || warn "fix the unhealthy containers before installing k3s"
  subnets="$(docker network ls -q | xargs -r docker network inspect --format '{{range .IPAM.Config}}{{.Subnet}} {{end}}' | tr ' ' '\n' | sed '/^$/d')"
  clash=0
  for s in $subnets; do
    for r in $K3S_RANGES; do
      case "$s" in "$r"*) blocker "Docker network $s overlaps k3s range ${r}0.0/16"; clash=1 ;; esac
    done
  done
  [ "$clash" -eq 0 ] && ok "no Docker network in 10.42/16 or 10.43/16 ($(echo "$subnets" | wc -l) subnet(s) checked)"
  docker_mem_mb="$(docker stats --no-stream --format '{{.MemUsage}}' 2>/dev/null \
    | awk '{v=$1; u=v; gsub(/[0-9.]/,"",u); gsub(/[A-Za-z]/,"",v);
            if (u=="GiB") v*=1024; else if (u=="KiB") v/=1024; else if (u=="B") v/=1048576;
            s+=v} END {printf "%d", s}')"
  ok "containers use about ${docker_mem_mb:-?} MiB now (config.yaml reserves 5 GiB for them and the OS)"
else
  warn "Docker not readable as $(id -un): run with sudo to check the Compose stack and its networks"
fi

echo
echo "Result: $BLOCKERS blocker(s), $WARNINGS warning(s)."
[ "$BLOCKERS" -eq 0 ]
