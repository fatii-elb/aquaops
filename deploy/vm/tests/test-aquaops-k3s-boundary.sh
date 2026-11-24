#!/usr/bin/env bash
# =============================================================================
# Privilege boundary of deploy/vm/aquaops-k3s-deploy, with the REAL sudo, the
# REAL sudoers rule, real accounts and the real setpriv drop. Card SCRUM-56 · E5
#
# DESTRUCTIVE FOR THE HOST IT RUNS ON (creates accounts, /etc/sudoers.d,
# /usr/local/sbin, /etc/aquaops-k3s): run it ONLY as root in a throwaway
# container, never on a real machine:
#
#   docker run --rm -v "$PWD:/src:ro" ubuntu:24.04 bash -c \
#     'apt-get update -qq && apt-get install -y -qq git sudo ca-certificates > /dev/null \
#      && bash /src/deploy/vm/tests/test-aquaops-k3s-boundary.sh'
#
# The wrapper is installed as shipped, except REPO_URL (a local repository)
# and the test doubles of docker, k3s, kubectl and terraform in /usr/local/bin.
# =============================================================================
# shellcheck disable=SC2015
set -uo pipefail
[ "$(id -u)" = 0 ] || { echo "run as root in a throwaway container"; exit 2; }
[ -f /.dockerenv ] || [ "${AQUAOPS_I_AM_A_THROWAWAY_HOST:-}" = yes ] \
    || { echo "refusing: not a container (set AQUAOPS_I_AM_A_THROWAWAY_HOST=yes if it is disposable)"; exit 2; }

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  PASS  $1"; }
ko() { FAIL=$((FAIL + 1)); echo "  FAIL  $1"; }
section() { echo; echo "== $1"; }
rep() { local s=""; for ((i = 0; i < $2; i++)); do s+="$1"; done; printf '%s' "$s"; }
TOKEN="gh""s_123456_$(rep a 120).$(rep b 120)"
DB_SECRET="fake-db-$(rep q 12)"
LOG=/tmp/mock.log; : > "$LOG"; chmod 666 "$LOG"

# --- Host as the runner guide installs it --------------------------------------
groupadd -f docker
useradd -m -s /bin/bash aquaops-k3s-runner
useradd -r -m -d /var/lib/aquaops-k3s -s /usr/sbin/nologin -G docker aquaops-k3s
chmod 700 /var/lib/aquaops-k3s
install -d -m 700 -o root -g root /var/lib/aquaops-k3s-deploy
install -d -m 755 -o root -g root /etc/aquaops-k3s
printf 'TF_VAR_db_user=aquaops_rc11\nTF_VAR_db_password=%s\n' "$DB_SECRET" > /etc/aquaops-k3s/rc11.env
chown root:root /etc/aquaops-k3s/rc11.env; chmod 600 /etc/aquaops-k3s/rc11.env
echo "apiVersion: v1" > /etc/aquaops-k3s/rc11.kubeconfig
chown aquaops-k3s:aquaops-k3s /etc/aquaops-k3s/rc11.kubeconfig; chmod 600 /etc/aquaops-k3s/rc11.kubeconfig
for m in docker k3s kubectl terraform; do tr -d '\r' < "$SRC/deploy/vm/tests/k3s-mocks/$m" > "/usr/local/bin/$m"; chmod 755 "/usr/local/bin/$m"; done
printf 'MOCK_LOG=%q\nMOCK_TOKEN=%q\nMOCK_TOKEN_B64=x\nMOCK_SECRETS=%q\n' "$LOG" "$TOKEN" "$TOKEN $DB_SECRET" > /usr/local/bin/mock.conf
chmod 644 /usr/local/bin/mock.conf

# Local "GitHub": the deploy files of this checkout, deploy-services.sh replaced
# by a stub that records who runs it.
R=/tmp/src; mkdir -p "$R"
for p in deploy/k3s deploy/terraform deploy/k8s deploy/integration/postgres-init docs/keycloak .github/workflows/k3s-deploy.yml; do
    mkdir -p "$R/$(dirname "$p")"; cp -r "$SRC/$p" "$R/$p"
done
find "$R" -name .terraform -prune -exec rm -rf {} +
cat > "$R/deploy/k3s/deploy-services.sh" <<'STUB'
#!/usr/bin/env bash
{ echo "user=$(id -un)"; echo "groups=$(id -Gn)"; echo "capeff=$(grep CapEff /proc/self/status | awk '{print $2}')"
  echo "secret_set=$([ -n "${TF_VAR_db_password:-}" ] && echo yes || echo no)"; } > "$HOME/stub-record"
exit 0
STUB
git -C "$R" init -q -b main && git -C "$R" add -A && git -C "$R" -c user.name=t -c user.email=t@example.invalid commit -qm C1
SHA="$(git -C "$R" rev-parse HEAD)"
git clone -q --bare "$R" /srv/remote.git

sed -e 's#^readonly REPO_URL=.*#readonly REPO_URL=file:///srv/remote.git#' "$SRC/deploy/vm/aquaops-k3s-deploy" | tr -d '\r' > /usr/local/sbin/aquaops-k3s-deploy
chown root:root /usr/local/sbin/aquaops-k3s-deploy; chmod 755 /usr/local/sbin/aquaops-k3s-deploy
tr -d '\r' < "$SRC/deploy/vm/aquaops-k3s-deploy.sudoers" > /tmp/sudoers
visudo -cf /tmp/sudoers > /dev/null && install -m 440 -o root -g root /tmp/sudoers /etc/sudoers.d/aquaops-k3s-deploy \
    && ok "sudoers installed after visudo -cf ($(sudo -V | head -n 1))" || { ko "visudo rejects the sudoers file"; exit 1; }

as_runner() { su -s /bin/bash aquaops-k3s-runner -c "$1" > /tmp/out 2>&1; RC=$?; }

# =============================================================================
section "1. What the runner account may run through sudo"
as_runner "printf '%s\n%s\n' '$TOKEN' octo | sudo -n /usr/local/sbin/aquaops-k3s-deploy rc11 $SHA 1700000000"
[ "$RC" = 0 ] && ok "rc11 <sha> <epoch>: allowed, deployed (exit 0)" || { ko "allowed form: exit $RC"; tail -n 15 /tmp/out; }
deny() {   # deny <label> <command>
    as_runner "echo | $2"
    if [ "$RC" != 0 ] && grep -qiE 'a password is required|not allowed|may not run|sorry' /tmp/out; then ok "$1: refused by sudo"
    else ko "$1: exit $RC, $(tail -n 1 /tmp/out)"; fi
}
deny "check mode (administrator only)"     "sudo -n /usr/local/sbin/aquaops-k3s-deploy check rc11 $SHA"
deny "another environment"                  "sudo -n /usr/local/sbin/aquaops-k3s-deploy test $SHA 1700000000"
deny "short SHA"                            "sudo -n /usr/local/sbin/aquaops-k3s-deploy rc11 ${SHA:0:12} 1700000000"
deny "extra argument"                       "sudo -n /usr/local/sbin/aquaops-k3s-deploy rc11 $SHA 1700000000 x"
deny "no argument"                          "sudo -n /usr/local/sbin/aquaops-k3s-deploy"
deny "a shell"                              "sudo -n /bin/bash -c id"
deny "another command"                      "sudo -n /usr/bin/id"
deny "as the service account"               "sudo -n -u aquaops-k3s /usr/local/sbin/aquaops-k3s-deploy rc11 $SHA 1700000000"
deny "setting a variable (no SETENV)"       "sudo -n PATH=/tmp /usr/local/sbin/aquaops-k3s-deploy rc11 $SHA 1700000000"
deny "preserving the environment"           "sudo -n -E /usr/local/sbin/aquaops-k3s-deploy rc11 $SHA 1700000000"
deny "editing a file (sudoedit)"            "sudo -n -e /etc/aquaops-k3s/rc11.env"
as_runner "sudo -n -l"
[ "$(grep -c 'NOPASSWD:' /tmp/out)" = 1 ] && grep -qF '(root) NOPASSWD: /usr/local/sbin/aquaops-k3s-deploy ^rc11' /tmp/out     && ! grep -qE '\(ALL|ALL\) ALL' /tmp/out && ok "sudo -l: exactly one rule, the wrapper as root" || { ko "sudo -l"; cat /tmp/out; }

# =============================================================================
section "2. Privilege drop: only the containerd import runs as root"
grep -q '^user=aquaops-k3s$' /var/lib/aquaops-k3s/home/stub-record && ok "deploy-services.sh ran as aquaops-k3s" || ko "deploy-services.sh user: $(cat /var/lib/aquaops-k3s/home/stub-record)"
grep -q '^groups=.*docker' /var/lib/aquaops-k3s/home/stub-record && ok "  with its groups (docker)" || ko "  groups"
grep -q '^capeff=0000000000000000$' /var/lib/aquaops-k3s/home/stub-record && ok "  with no capability" || ko "  capabilities"
grep -q '^secret_set=yes$' /var/lib/aquaops-k3s/home/stub-record && ok "  with the secrets of rc11.env" || ko "  secrets"
users="$(grep '^CALL docker' "$LOG" | sed 's/.*| user=//' | sort -u | tr '\n' ' ')"
[ "$users" = "aquaops-k3s " ] && ok "every docker call ran as aquaops-k3s" || ko "docker users: $users"
grep -q '^CALL k3s ctr -n k8s.io images import .* | user=root$' "$LOG" && ok "containerd import ran as root" || ko "import user"
[ "$(stat -c '%U' /var/lib/aquaops-k3s/release/deploy/k3s/deploy-services.sh)" = aquaops-k3s ] && ok "release files owned by aquaops-k3s (Terraform can write .terraform)" || ko "release owner"
[ "$(stat -c '%U %a' /var/lib/aquaops-k3s-deploy/repo.git)" = "root 700" ] && ok "private repository: root, 700" || ko "repo owner"
grep -q 'SECRET-IN-ARGV' "$LOG" && ko "secret on a command line" || ok "no token or secret on a docker command line"

# =============================================================================
section "3. What the runner account can NOT reach directly"
for p in /etc/aquaops-k3s/rc11.env /etc/aquaops-k3s/rc11.kubeconfig /var/lib/aquaops-k3s/state /var/lib/aquaops-k3s-deploy/repo.git; do
    if [ -d "$p" ]; then as_runner "ls $p"; else as_runner "head -c 1 $p"; fi
    [ "$RC" != 0 ] && ok "runner cannot read $p" || ko "runner reads $p"
done
as_runner "touch /usr/local/sbin/aquaops-k3s-deploy"
[ "$RC" != 0 ] && ok "runner cannot modify the wrapper" || ko "runner modifies the wrapper"
as_runner "id -Gn"
grep -qwE 'docker|sudo' /tmp/out && ko "runner in docker or sudo group: $(cat /tmp/out)" || ok "runner is in neither the docker nor the sudo group"

echo
echo "RESULT: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
