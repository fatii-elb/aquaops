#!/usr/bin/env bash
# =============================================================================
# Tests of deploy/vm/aquaops-k3s-deploy. Card SCRUM-56 / NFR-09 · E5
#
# No root, no Docker, no k3s, no network, no real secret: the wrapper runs as
# the current user against a local git "remote", with test doubles of docker,
# k3s, kubectl, terraform, git and stat (deploy/vm/tests/k3s-mocks). The REAL
# deploy/k3s/import-images.sh runs against the docker double; deploy-services.sh
# is replaced by a recording stub in the test commits. Only the FIXED
# CONFIGURATION block of the wrapper is replaced (sandbox paths, current user,
# no setpriv); the rest is the file under review.
#
#     bash deploy/vm/tests/test-aquaops-k3s-deploy.sh
#
# Needs Linux (or WSL) with bash, git, flock, awk, base64, tar and GNU stat,
# the sandbox on a Linux file system (AQUAOPS_TEST_TMP, default /tmp).
# Optional: visudo (package sudo) to validate the sudoers file.
# Exit code: 0 if every test passes.
# =============================================================================
# "check && ok || ko" is intended: ok and ko always succeed.
# shellcheck disable=SC2015
set -uo pipefail
umask 022   # modes are set explicitly; never depend on the caller's umask

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
WRAPPER_SRC="$REPO/deploy/vm/aquaops-k3s-deploy"
SUDOERS="$REPO/deploy/vm/aquaops-k3s-deploy.sudoers"
WORKFLOW="$REPO/.github/workflows/k3s-deploy.yml"
SB="$(mktemp -d "${AQUAOPS_TEST_TMP:-${TMPDIR:-/tmp}}/aquaops-k3s-test.XXXXXX")"
trap 'chmod -R u+rwx "$SB" 2> /dev/null; rm -rf "$SB"' EXIT
ME="$(id -un)"
# Fake values, low entropy on purpose (nothing for Gitleaks), built at run time.
rep() { local s=""; for ((i = 0; i < $2; i++)); do s+="$1"; done; printf '%s' "$s"; }
TOKEN="gh""s_123456_$(rep a 180).$(rep b 180).$(rep 'c-_' 55)"
TOKEN_B64="$(printf 'x-access-token:%s' "$TOKEN" | base64 -w 0)"
DB_SECRET="fake-db-$(rep q 12)"
KC_SECRET="fake-kc-$(rep r 12)"
ACTOR=octo-tester
GOOD_STDIN="$TOKEN\n$ACTOR\n"
W="$SB/sbin/aquaops-k3s-deploy"
CONF="$SB/etc"
RB="$SB/root-base"
SV="$SB/svc-base"
LOG="$SB/mock.log"
REC="$SV/home/stub-record"
ALL_OUT="$SB/all-output"
: > "$ALL_OUT"

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  PASS  $1"; }
ko() { FAIL=$((FAIL + 1)); echo "  FAIL  $1"; }
section() { echo; echo "== $1"; }

# --- Sandbox -------------------------------------------------------------------
mkdir -p "$SB/sbin" "$CONF" "$SB/mocks" "$SB/src" "$SV/home"
chmod 755 "$SB/sbin" "$CONF" "$SB/mocks"
mkdir -m 700 "$RB"; chmod 700 "$SV" "$SV/home"
for m in docker k3s kubectl terraform git stat; do tr -d '\r' < "$HERE/k3s-mocks/$m" > "$SB/mocks/$m"; chmod 755 "$SB/mocks/$m"; done

mock_conf() {   # mock_conf [VAR=value ...]
    {
        printf 'MOCK_LOG=%q\nMOCK_TOKEN=%q\nMOCK_TOKEN_B64=%q\nMOCK_SECRETS=%q\n' "$LOG" "$TOKEN" "$TOKEN_B64" "$TOKEN $DB_SECRET $KC_SECRET"
        for kv in "$@"; do printf '%s=%q\n' "${kv%%=*}" "${kv#*=}"; done
    } > "$SB/mocks/mock.conf"
}
mock_conf

write_env() {   # fake rc11.env (TF_VAR_* only), mode 600; extra lines as arguments
    {
        echo "# fake values for the tests"
        echo "TF_VAR_db_user=aquaops_rc11"
        echo "TF_VAR_db_password=$DB_SECRET"
        echo "TF_VAR_keycloak_admin_password='$KC_SECRET'"
        for l in "$@"; do echo "$l"; done
    } > "$CONF/rc11.env"
    chmod 600 "$CONF/rc11.env"
}
write_env
echo "apiVersion: v1" > "$CONF/rc11.kubeconfig"; chmod 600 "$CONF/rc11.kubeconfig"

awk -v cfg="readonly RUN_USER=$ME
readonly SVC_USER=$ME
readonly ROOT_OWNER=$ME
readonly SELF=$W
readonly CONF_DIR=$CONF
readonly ROOT_BASE=$RB
readonly SVC_BASE=$SV
readonly REPO_URL=file://$SB/remote.git
readonly SAFE_PATH=$SB/mocks:/usr/local/bin:/usr/bin:/bin
readonly -a AS_SVC=(env --)
readonly -a K3S_IMPORT=(k3s ctr -n k8s.io images import)" '
    /^# BEGIN FIXED CONFIGURATION/ { print; print cfg; skip = 1; next }
    /^# END FIXED CONFIGURATION/   { skip = 0 }
    !skip' "$WRAPPER_SRC" | tr -d '\r' > "$W"
chmod 755 "$W"

# Local "GitHub": the real deploy files, then:
#   main: C0 (no k3s-deploy.yml: predates k3s) -> CD (services disabled in
#         rc11.tfvars) -> C1 (ready) -> C2 (ready, marker changed)
#   branch "feature": C1 -> CX, never merged.
G() { git -C "$SB/src" -c user.name=test -c user.email=test@example.invalid "$@"; }
git init --quiet -b main "$SB/src"
for p in deploy/k3s deploy/terraform deploy/k8s deploy/integration/postgres-init docs/keycloak; do
    mkdir -p "$SB/src/$(dirname "$p")"; cp -r "$REPO/$p" "$SB/src/$p"
done
find "$SB/src" -name .terraform -prune -exec rm -rf {} +
find "$SB/src" -type f -exec sed -i 's/\r$//' {} +
TFV="$SB/src/deploy/terraform/environment/envs/rc11.tfvars"
grep -qE '^services_enabled[[:space:]]*=[[:space:]]*true' "$TFV" || { echo "SETUP ERROR: rc11.tfvars does not enable the services"; exit 1; }
# Stub deploy-services.sh: records how it was started; behaviour from
# $HOME/stub.ctl ($HOME = the service account's home of the wrapper).
cat > "$SB/src/deploy/k3s/deploy-services.sh" <<'STUB'
#!/usr/bin/env bash
rc=0; print_secrets=no; pause=0; hold=no
[ ! -f "$HOME/stub.ctl" ] || . "$HOME/stub.ctl"
{
    echo "script=$0"; echo "cwd=$PWD"; echo "args=$*"; echo "argc=$#"
    env | grep -vE '^(TF_VAR_[a-z_]+)=' | sort
    env | grep -oE '^TF_VAR_[a-z_]+' | sed 's/$/=<set>/' | sort
    echo "TF_VAR_db_password_length=${#TF_VAR_db_password}"
    echo "TF_VAR_keycloak_admin_password_length=${#TF_VAR_keycloak_admin_password}"
    echo "stdin_is_null=$( [ "$(readlink /proc/self/fd/0)" = /dev/null ] && echo yes || echo no )"
} > "$HOME/stub-record"
[ "$print_secrets" = no ] || echo "stub output: db=$TF_VAR_db_password kc=$TF_VAR_keycloak_admin_password"
sleep "$pause"
# hold=yes: wait until the test creates $HOME/stub.go (deterministic concurrency test)
if [ "$hold" = yes ]; then for _ in $(seq 600); do [ -f "$HOME/stub.go" ] && break; sleep 0.1; done; fi
exit "$rc"
STUB
echo zero > "$SB/src/docs/keycloak/marker"
G add -A && G commit --quiet -m C0
C0="$(G rev-parse HEAD)"
mkdir -p "$SB/src/.github/workflows"; tr -d '\r' < "$WORKFLOW" > "$SB/src/.github/workflows/k3s-deploy.yml"
sed -i -E 's/^(services_enabled[[:space:]]*=[[:space:]]*)true/\1false/' "$TFV"
G add -A && G commit --quiet -m CD
CD="$(G rev-parse HEAD)"
sed -i -E 's/^(services_enabled[[:space:]]*=[[:space:]]*)false/\1true/' "$TFV"
echo one > "$SB/src/docs/keycloak/marker"
G commit --quiet -am C1
C1="$(G rev-parse HEAD)"
G switch --quiet -c feature
echo unreviewed > "$SB/src/docs/keycloak/marker"
G commit --quiet -am CX
CX="$(G rev-parse HEAD)"
G switch --quiet main
echo two > "$SB/src/docs/keycloak/marker"
G commit --quiet -am C2
C2="$(G rev-parse HEAD)"
git clone --quiet --bare "$SB/src" "$SB/remote.git"
P=1700000000

# --- Helpers ------------------------------------------------------------------
run() {   # run <stdin> <args...>: sets RC and OUT
    rm -f "$REC"
    OUT="$(printf '%b' "$1" | "$W" "${@:2}" 2>&1)"; RC=$?
    printf '%s\n' "$OUT" >> "$ALL_OUT"
}
expect_rc()  { [ "$RC" = "$1" ] && ok "$2 (exit $RC)" || { ko "$2: exit $RC, expected $1"; printf '        %s\n' "$OUT" | tail -n 15; }; }
expect_out() { grep -qE -- "$1" <<< "$OUT" && ok "$2" || { ko "$2: output lacks /$1/"; printf '        %s\n' "$OUT" | tail -n 5; }; }
stub_ran()   { [ -f "$REC" ] && ok "$1" || ko "$1: deploy-services.sh did not run"; }
stub_idle()  { [ ! -f "$REC" ] && ok "$1" || ko "$1: deploy-services.sh ran"; }
rec()        { grep -qxF -- "$1" "$REC" 2> /dev/null && ok "$2" || ko "$2: record lacks [$1]"; }
norec()      { grep -qE -- "$1" "$REC" 2> /dev/null && ko "$2" || ok "$2"; }
ctl()        { printf '%s\n' "$@" > "$SV/home/stub.ctl"; }
imported()   { cat "$LOG" 2> /dev/null | grep -c '^IMPORTED'; }
ctl

# =============================================================================
section "1. Arguments: only <rc11> <sha> <epoch> (or 'check rc11 <sha>')"
run "$GOOD_STDIN";                                expect_rc 2 "no argument"
run "$GOOD_STDIN" rc11 "$C2";                     expect_rc 2 "push time missing"
run "$GOOD_STDIN" test "$C2" $P;                  expect_rc 2 "environment test (the VM's Compose environment) refused"
run "$GOOD_STDIN" prod "$C2" $P;                  expect_rc 2 "unknown environment"
run "$GOOD_STDIN" "rc11 " "$C2" $P;               expect_rc 2 "environment with a trailing space"
run "$GOOD_STDIN" rc11 "${C2:0:7}" $P;            expect_rc 2 "short SHA"
run "$GOOD_STDIN" rc11 "${C2^^}" $P;              expect_rc 2 "uppercase SHA"
run "$GOOD_STDIN" rc11 "$C2" $P extra;            expect_rc 2 "fourth argument"
run "$GOOD_STDIN" "rc11 $C2 $P";                  expect_rc 2 "all values in one argument"
run "$GOOD_STDIN" rc11 "$C2" 12345678;            expect_rc 2 "push time too short"
run "$GOOD_STDIN" rc11 "$C2" 123456789012;        expect_rc 2 "push time too long"
run "$GOOD_STDIN" rc11 "$C2" -1700000000;         expect_rc 2 "negative push time"
run "$GOOD_STDIN" check rc11 "${C2:0:12}";        expect_rc 2 "check with short SHA"
run "$GOOD_STDIN" check test "$C2";               expect_rc 2 "check of a non-deployable environment"
run "$GOOD_STDIN" rc11 "${C2:0:30};touch\ $SB/pwn" $P; expect_rc 2 "command injection ;"
run "$GOOD_STDIN" "\$(touch $SB/pwn)" "$C2" $P;   expect_rc 2 "command substitution in the environment"
run "$GOOD_STDIN" rc11 "$C2" "1\$(touch $SB/pwn)"; expect_rc 2 "injection in the push time"
run "$GOOD_STDIN" "../rc11" "$C2" $P;             expect_rc 2 "path traversal in the environment"
[ ! -e "$SB/pwn" ] && ok "no injected command ran" || ko "an injected command ran"
grep -qF -- "touch" "$ALL_OUT" && ko "rejected values are echoed back" || ok "rejected values are never echoed back"
stub_idle "deploy-services.sh never ran for rejected arguments"
[ "$(imported)" = 0 ] && ok "no image imported for rejected arguments" || ko "images imported"

# =============================================================================
section "2. Credentials: token and user name on stdin only, fail safe"
run "" rc11 "$C2" $P;                             expect_rc 2 "empty stdin"; expect_out "malformed token" "  says the token is missing"
run "short\n$ACTOR\n" rc11 "$C2" $P;              expect_rc 2 "token too short"
run "FAKE FAKE FAKE FAKE FAKE FAKE\n$ACTOR\n" rc11 "$C2" $P; expect_rc 2 "token with spaces"
run "FAKE\$(id)FAKEFAKEFAKEFAKEFAKE\n$ACTOR\n" rc11 "$C2" $P; expect_rc 2 "token with \$( )"
run "$(rep a 4097)\n$ACTOR\n" rc11 "$C2" $P;      expect_rc 2 "token longer than 4096 characters"
run "$TOKEN" rc11 "$C2" $P;                       expect_rc 2 "no user name line"
run "$TOKEN\nbad user;id\n" rc11 "$C2" $P;        expect_rc 2 "user name with shell characters"
mv "$SB/remote.git" "$SB/remote.bak"
run "$GOOD_STDIN" rc11 "$C2" $P;                  expect_rc 2 "repository unreachable (bad token or network)"; expect_out "cannot fetch main" "  clear message"
mv "$SB/remote.bak" "$SB/remote.git"
stub_idle "deploy-services.sh never ran without valid credentials"
run "$GOOD_STDIN" rc11 "$C2" $P;                  expect_rc 0 "valid token (stateless GITHUB_TOKEN format, ${#TOKEN} characters): deployed"

# =============================================================================
section "3. Only commits of main that prepare k3s deployments"
run "$GOOD_STDIN" rc11 "$CX" $P;                  expect_rc 2 "commit of an unmerged branch"; expect_out "does not exist" "  clear message"
run "$GOOD_STDIN" rc11 0123456789abcdef0123456789abcdef01234567 $P; expect_rc 2 "unknown commit"
run "$GOOD_STDIN" rc11 "$C0" $P;                  expect_rc 2 "commit of main without k3s-deploy.yml"; expect_out "predates k3s deployments" "  clear message"
run "$GOOD_STDIN" rc11 "$CD" $P;                  expect_rc 2 "commit whose rc11.tfvars disables the services"; expect_out "does not enable the services" "  clear message"
stub_idle "  nothing deployed"
[ ! -e "$RB/repo.git/hooks" ] && ok "private repository created without hook templates" || ko "hooks folder created"
mkdir -p "$RB/repo.git/hooks"
for h in reference-transaction post-checkout pre-auto-gc; do
    printf '#!/bin/sh\ntouch %s/pwn-hook-%s\n' "$SB" "$h" > "$RB/repo.git/hooks/$h"; chmod 755 "$RB/repo.git/hooks/$h"
done
git -C "$SB/remote.git" update-ref refs/heads/main "$C1"
run "$GOOD_STDIN" rc11 "$C2" $P;                  expect_rc 2 "commit no longer on main (main rewritten)"; expect_out "not on main" "  clear message"
git -C "$SB/remote.git" update-ref refs/heads/main "$C2"
run "$GOOD_STDIN" rc11 "$C1" $P;                  expect_rc 0 "older commit of main: deployed"
[ "$(cat "$SV/release/docs/keycloak/marker")" = one ] && ok "  release = files of that commit" || ko "  release content"
hooks_run="$(find "$SB" -maxdepth 1 -name 'pwn-hook-*' -printf '%f ')"
[ -z "$hooks_run" ] && ok "no git hook ran while main moved twice" || ko "git hook ran: $hooks_run"
rm -rf "$RB/repo.git/hooks"

# =============================================================================
section "4. Fixed settings: the caller cannot redirect anything"
: > "$LOG"
run "$GOOD_STDIN" rc11 "$C2" $P;                  expect_rc 0 "deployment of the tip of main"
rec "args=rc11 $C2 $P" "deploy-services.sh gets <env> <sha> <pushed_at>"
rec "argc=3" "  and nothing else"
rec "script=$SV/release/deploy/k3s/deploy-services.sh" "script from the trusted export, not a workspace"
rec "KUBE_CONFIG_PATH=$CONF/rc11.kubeconfig" "kubeconfig fixed (per environment)"
rec "AQUAOPS_TF_STATE_DIR=$SV/state/terraform" "Terraform state folder fixed"
rec "AQUAOPS_K3S_HISTORY=$SV/state/k3s-deployments.jsonl" "deployment history (NFR-09) fixed"
rec "AQUAOPS_K3S_DEPLOY_ENVS=rc11" "deployable environment = the requested one only"
rec "HOME=$SV/home" "home of the service account"
rec "TF_VAR_db_password=<set>" "secrets from the env file reach Terraform"
rec "TF_VAR_db_password_length=${#DB_SECRET}" "  whole"
rec "TF_VAR_keycloak_admin_password_length=${#KC_SECRET}" "  quotes around a value removed"
rec "stdin_is_null=yes" "deploy-services.sh gets no stdin (the token stays with import-images.sh)"
norec 'gh''s_|GHCR|TOKEN' "  no token variable in its environment"
[ "$(cat "$SV/release/docs/keycloak/marker")" = two ] && ok "export = files of the requested commit" || ko "export content"
for f in deploy/k3s/import-images.sh deploy/terraform/tf.sh deploy/terraform/environment/e2_services.tf deploy/k8s deploy/integration/postgres-init docs/keycloak/aquaops-realm.json; do
    [ -e "$SV/release/$f" ] || { ko "export lacks $f"; continue; }
done
ok "export holds what import-images.sh, tf.sh and Terraform read"
[ ! -e "$SV/release/.github" ] && [ ! -e "$SV/release/services" ] && ok "export holds nothing else" || ko "export has extra folders"

mkdir -p "$SB/evil" "$SB/evilbin"
for b in docker git bash awk k3s kubectl terraform tar; do printf '#!/bin/sh\ntouch %s/pwn-path-%s\nexit 1\n' "$SB" "$b" > "$SB/evilbin/$b"; chmod 755 "$SB/evilbin/$b"; done
printf 'touch %s/pwn-bashenv\n' "$SB" > "$SB/evil/bashenv"
OUT="$(cd "$SB/evil" && printf '%b' "$GOOD_STDIN" | env \
    PATH="$SB/evilbin:$PATH" BASH_ENV="$SB/evil/bashenv" ENV="$SB/evil/bashenv" \
    'BASH_FUNC_git%%=() { touch '"$SB"'/pwn-func; }' SHELLOPTS=xtrace PS4='+TRACE ' \
    KUBECONFIG=/etc/evil KUBE_CONFIG_PATH=/etc/evil AQUAOPS_TF_STATE_DIR="$SB/evil" \
    AQUAOPS_K3S_DEPLOY_ENVS="rc11 test" AQUAOPS_IMAGE_SOURCE=local K3S_IMPORT_CMD="touch $SB/pwn-import" \
    TF_VAR_db_password=from-the-caller TF_VAR_extra=from-the-caller DOCKER_HOST=tcp://192.0.2.1:2375 \
    GIT_DIR="$SB/evil" GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=core.fsmonitor GIT_CONFIG_VALUE_0="touch $SB/pwn-git" \
    "$W" rc11 "$C2" $P 2>&1)"; RC=$?
printf '%s\n' "$OUT" >> "$ALL_OUT"
expect_rc 0 "hostile caller environment and working directory: deployment still uses fixed values"
pwned="$(find "$SB" -maxdepth 1 -name 'pwn*' -printf '%f ')"
[ -z "$pwned" ] && ok "no caller file, PATH entry, BASH_ENV, function, git or import setting was used" || ko "caller influence: $pwned"
grep -q '^+TRACE' <<< "$OUT" && ko "SHELLOPTS=xtrace imported" || ok "SHELLOPTS/xtrace from the caller ignored"
rec "TF_VAR_db_password_length=${#DB_SECRET}" "  secret from the env file, not from the caller"
norec '^TF_VAR_extra=' "  caller TF_VAR_ variable dropped"
rec "KUBE_CONFIG_PATH=$CONF/rc11.kubeconfig" "  kubeconfig still fixed"
rec "AQUAOPS_K3S_DEPLOY_ENVS=rc11" "  deployable environments still fixed"
norec '^DOCKER_HOST=' "  DOCKER_HOST dropped"

# =============================================================================
section "5. Secrets file: TF_VAR_name=value only, parsed, never sourced"
write_env "TF_VAR_note=\$(touch $SB/pwn-env)"
run "$GOOD_STDIN" rc11 "$C2" $P;                  expect_rc 0 "value with \$( ) taken literally"
[ ! -e "$SB/pwn-env" ] && ok "  the value was not executed" || ko "  the env file was executed"
write_env "export TF_VAR_x=1";                    run "$GOOD_STDIN" rc11 "$C2" $P; expect_rc 2 "'export' line refused"
write_env "DB_PASSWORD=x";                        run "$GOOD_STDIN" rc11 "$C2" $P; expect_rc 2 "non TF_VAR_ variable refused"
write_env "TF_VAR_x";                             run "$GOOD_STDIN" rc11 "$C2" $P; expect_rc 2 "line without a value refused"
write_env "TF_VAR_x=";                            run "$GOOD_STDIN" rc11 "$C2" $P; expect_rc 2 "empty value refused"
write_env "TF_VAR_x=change-me";                   run "$GOOD_STDIN" rc11 "$C2" $P; expect_rc 2 "placeholder value refused"
write_env "TF_VAR_Bad=1";                         run "$GOOD_STDIN" rc11 "$C2" $P; expect_rc 2 "uppercase variable name refused"
grep -qF "$DB_SECRET" "$ALL_OUT" && ko "a secret appeared in the refusal messages" || ok "refusals never print a secret value"
printf '# only comments\n' > "$CONF/rc11.env"; chmod 600 "$CONF/rc11.env"
run "$GOOD_STDIN" rc11 "$C2" $P;                  expect_rc 2 "env file without any TF_VAR_ refused"
write_env
printf 'TF_VAR_db_user=crlf\r\nTF_VAR_db_password=%s\r\n' "$DB_SECRET" > "$CONF/rc11.env"; chmod 600 "$CONF/rc11.env"
run "$GOOD_STDIN" rc11 "$C2" $P;                  expect_rc 0 "Windows line endings tolerated"
rec "TF_VAR_db_password_length=${#DB_SECRET}" "  carriage return not part of the value"
write_env

# =============================================================================
section "6. Trusted paths: owner, mode, no symbolic links"
t() { run "$GOOD_STDIN" rc11 "$C2" $P; expect_rc 2 "$1"; }
chmod 640 "$CONF/rc11.env";        t "secrets file readable by group";       chmod 600 "$CONF/rc11.env"
chmod 604 "$CONF/rc11.env";        t "secrets file readable by others";      chmod 600 "$CONF/rc11.env"
chmod 644 "$CONF/rc11.kubeconfig"; t "kubeconfig readable by others";        chmod 600 "$CONF/rc11.kubeconfig"
rm "$CONF/rc11.kubeconfig";        t "kubeconfig missing"; echo "apiVersion: v1" > "$CONF/rc11.kubeconfig"; chmod 600 "$CONF/rc11.kubeconfig"
chmod 757 "$CONF";                 t "config folder writable by others";     chmod 755 "$CONF"
chmod 775 "$W";                    t "wrapper writable by group";            chmod 755 "$W"
chmod 777 "$SB/sbin";              t "wrapper folder writable by others";    chmod 755 "$SB/sbin"
chmod 750 "$RB";                   t "root folder (lock, repository) open to the group"; chmod 700 "$RB"
chmod 705 "$SV";                   t "service folder open to others";        chmod 700 "$SV"
chmod 777 "$SB/mocks";             t "PATH directory writable by others"; expect_out "PATH directory" "  clear message"; chmod 755 "$SB/mocks"
mv "$CONF/rc11.env" "$SB/real.env"; ln -s "$SB/real.env" "$CONF/rc11.env"
t "secrets file replaced by a symbolic link"; expect_out "rc11.env is a symbolic link" "  refused as a link"
rm "$CONF/rc11.env"; mv "$SB/real.env" "$CONF/rc11.env"
mv "$SV/state" "$SB/state.real"; ln -s "$SB/evil" "$SV/state"
t "state folder replaced by a symbolic link"; rm "$SV/state"; mv "$SB/state.real" "$SV/state"
mv "$RB/repo.git" "$SB/repo.real"; ln -s "$SB/evil" "$RB/repo.git"
t "private repository replaced by a symbolic link"; rm "$RB/repo.git"; mv "$SB/repo.real" "$RB/repo.git"
rm -f "$RB/lock"; ln -s "$SB/evil/lockfile" "$RB/lock"
t "lock file replaced by a symbolic link"; expect_out "lock is a symbolic link" "  refused as a link"; rm -f "$RB/lock"
for p in "$W:wrapper" "$SB/sbin:wrapper folder" "$CONF:config folder" "$CONF/rc11.env:secrets file" \
         "$CONF/rc11.kubeconfig:kubeconfig" "$RB:root folder" "$SV:service folder" "$SV/state:state folder" \
         "$RB/repo.git:private repository" "$SB/mocks:PATH directory"; do
    mock_conf "MOCK_STAT_FOREIGN=${p%%:*}"
    t "${p#*:} owned by another user"; expect_out "must be owned by" "  refused by the owner rule"
done
mock_conf
sed -i "s/^readonly RUN_USER=.*/readonly RUN_USER=someone-else/" "$W"
t "not running as the required user (root)"; expect_out "must run as someone-else" "  clear message"
sed -i "s/^readonly RUN_USER=.*/readonly RUN_USER=$ME/" "$W"
run "$GOOD_STDIN" rc11 "$C2" $P;                  expect_rc 0 "all paths restored: deployment allowed again"

# =============================================================================
section "7. One deployment at a time"
# Deterministic: the holder keeps the lock until the test releases it, and
# the first deployment waits in its stub until the second request is done
# (fixed sleeps raced on loaded hosts: 62-test investigation, Sprint 6).
wait_locked() { for _ in $(seq 600); do flock -n "$RB/lock" true || return 0; sleep 0.1; done; return 1; }
rm -f "$SB/unlock"
( exec 8> "$RB/lock"; flock 8; until [ -f "$SB/unlock" ]; do sleep 0.1; done ) &
holder=$!; wait_locked
run "$GOOD_STDIN" rc11 "$C2" $P; expect_rc 2 "lock already held"; expect_out "another deployment is running" "  clear message"
touch "$SB/unlock"; wait "$holder"; rm -f "$SB/unlock"
ctl "hold=yes"
rm -f "$REC" "$SV/home/stub.go"
( printf '%b' "$GOOD_STDIN" | "$W" rc11 "$C2" $P > "$SB/p1.out" 2>&1; echo $? > "$SB/p1.rc" ) &
first=$!
for _ in $(seq 600); do [ -f "$REC" ] && break; sleep 0.1; done
printf '%b' "$GOOD_STDIN" | "$W" rc11 "$C2" $P > "$SB/p2.out" 2>&1; echo $? > "$SB/p2.rc"
touch "$SV/home/stub.go"; wait "$first"; rm -f "$SV/home/stub.go"
ctl
r="$(cat "$SB/p1.rc") $(cat "$SB/p2.rc")"
[ "$r" = "0 2" ] && ok "two simultaneous requests: first deploys, second refused (exit $r)" || ko "simultaneous requests: exit $r"

# =============================================================================
section "8. Images: scanned GHCR images, checked, imported by root only"
: > "$LOG"
run "$GOOD_STDIN" rc11 "$C2" $P;                  expect_rc 0 "deployment"
grep -q 'LOGIN stdin=token-ok' "$LOG" && ok "GHCR login gets the token on stdin" || ko "GHCR login"
grep -q 'LOGOUT ghcr.io' "$LOG" && ok "logout after the pulls" || ko "logout"
[ "$(grep -c "^CALL docker pull --quiet ghcr.io/fatii-elb/aquaops/[a-z]*:$C2 " "$LOG")" = 7 ] && ok "7 images pulled by full SHA from ghcr.io/fatii-elb/aquaops" || ko "pulls: $(grep -c '^CALL docker pull' "$LOG")"
grep -qE "^CALL k3s ctr -n k8s.io images import $SV/tmp/images.tar " "$LOG" && ok "root imports the archive into k3s containerd" || ko "k3s import call"
grep -q "^IMPORTED MOCK-IMAGE-ARCHIVE localhost/aquaops/gateway:$C2 .*localhost/aquaops/simulator:$C2" "$LOG" && ok "  the archive holds the 7 localhost/aquaops/<service>:<sha> images" || ko "  archive content"
[ "$(grep -n 'images import' "$LOG" | cut -d: -f1)" -gt "$(grep -n 'LOGOUT' "$LOG" | cut -d: -f1)" ] && ok "  import after every pull and label check" || ko "  import order"
[ ! -e "$SV/tmp/images.tar" ] && ok "  archive deleted after the import" || ko "  archive left on disk"
[ -s "$SV/state/digests-$C2.txt" ] && [ "$(wc -l < "$SV/state/digests-$C2.txt")" = 7 ] && ok "  7 registry digests recorded" || ko "  digests"
mock_conf MOCK_REVISION=0000000000000000000000000000000000000000
run "$GOOD_STDIN" rc11 "$C2" $P;                  expect_rc 1 "image revision label differs from the SHA -> 1"
expect_out "cluster was not touched" "  says the cluster was not touched"
stub_idle "  deploy-services.sh not run"
mock_conf MOCK_PULL=fail
: > "$LOG"; run "$GOOD_STDIN" rc11 "$C2" $P;      expect_rc 1 "image missing in GHCR (Images run failed?) -> 1"
grep -q 'images import' "$LOG" && ko "  import attempted" || ok "  nothing imported"
stub_idle "  deploy-services.sh not run"
mock_conf MOCK_IMPORT=fail
run "$GOOD_STDIN" rc11 "$C2" $P;                  expect_rc 1 "containerd refuses the archive -> 1"
stub_idle "  deploy-services.sh not run"
mock_conf

# =============================================================================
section "9. Exit codes of deploy-services.sh"
ctl "rc=1"; run "$GOOD_STDIN" rc11 "$C2" $P;      expect_rc 1 "failed, previous version restored -> 1"
ctl "rc=2"; run "$GOOD_STDIN" rc11 "$C2" $P;      expect_rc 2 "refused -> 2"
ctl "rc=3"; run "$GOOD_STDIN" rc11 "$C2" $P;      expect_rc 3 "failed, NOT restored -> 3"
ctl "rc=7"; run "$GOOD_STDIN" rc11 "$C2" $P;      expect_rc 3 "unexpected code -> 3 (act now, never a silent success)"
ctl
run "$GOOD_STDIN" rc11 "$C2" $P;                  expect_out "finished with exit code 0 after [0-9]+ s" "duration printed (NFR-09 evidence)"

# =============================================================================
section "10. Token and secrets never shown, never on a command line, never on disk"
ctl "print_secrets=yes"; run "$GOOD_STDIN" rc11 "$C2" $P; expect_rc 0 "script printing the secrets"
expect_out 'db=\*\*\* kc=\*\*\*' "  secrets replaced by *** in the output"
ctl
grep -qF -- "$TOKEN" "$ALL_OUT" && ko "token visible in an output" || ok "token never in any output"
grep -qF -- "$TOKEN_B64" "$ALL_OUT" && ko "encoded token visible" || ok "encoded token never in any output"
grep -qF -- "$DB_SECRET" "$ALL_OUT" && ko "database secret visible" || ok "database secret never in any output"
grep -qF -- "$KC_SECRET" "$ALL_OUT" && ko "Keycloak secret visible" || ok "Keycloak secret never in any output"
grep -q 'SECRET-IN-ARGV' "$LOG" && ko "a secret on a git or docker command line" || ok "no secret on any git or docker command line"
grep -rqF -- "$TOKEN" "$RB" "$SV" 2> /dev/null && ko "token written to disk" || ok "token never written to disk"
grep -rqF -- "$DB_SECRET" "$RB" "$SV" 2> /dev/null && ko "secret written to disk" || ok "secrets never written to disk by the wrapper"
grep -qi extraheader "$RB/repo.git/config" && ko "auth header stored in git config" || ok "no credential in the git configuration"
find "$SV" -name config.json -path '*docker*' | grep -q . && ko "docker credentials left on disk" || ok "no docker credential left on disk"

# =============================================================================
section "11. 'check' mode changes nothing"
snap() { { find "$SV/release" "$SV/state" -printf '%p %s %T@\n'; find "$SV/tmp" -mindepth 1; } | sort | md5sum; }
before="$(snap)"
: > "$LOG"
run "$GOOD_STDIN" check rc11 "$C1";               expect_rc 0 "check of a commit on main"; expect_out "CHECK OK" "  reports OK"
stub_idle "  deploy-services.sh not run"
grep -qE 'docker (pull|login|save)|images import' "$LOG" && ko "  pulled or imported images" || ok "  no pull, login, save or import"
grep -q "^CALL kubectl --request-timeout=15s get namespace kube-system -o name | KUBECONFIG=$CONF/rc11.kubeconfig" "$LOG" && ok "  cluster reached with the fixed kubeconfig" || ko "  kubectl call"
[ "$before" = "$(snap)" ] && ok "  release, state and temporary folders unchanged" || ko "  something changed"
mock_conf MOCK_CLUSTER=down
run "$GOOD_STDIN" check rc11 "$C2";               expect_rc 2 "check: cluster unreachable -> 2"; expect_out "cluster not reachable" "  explained"
mock_conf MOCK_DOCKER=down
run "$GOOD_STDIN" check rc11 "$C2";               expect_rc 2 "check: Docker unreachable for the service account -> 2"
mock_conf
run "$GOOD_STDIN" check rc11 "$CX";               expect_rc 2 "check: unmerged commit refused"

# =============================================================================
section "12. Shipped files"
tr -d '\r' < "$WRAPPER_SRC" | sed -n '/^# BEGIN FIXED CONFIGURATION/,/^# END FIXED CONFIGURATION/p' > "$SB/cfg"
cat > "$SB/cfg.expected" <<'EOF'
# BEGIN FIXED CONFIGURATION (the tests replace this block, nothing else)
readonly RUN_USER=root
readonly SVC_USER=aquaops-k3s
readonly ROOT_OWNER=root
readonly SELF=/usr/local/sbin/aquaops-k3s-deploy
readonly CONF_DIR=/etc/aquaops-k3s
readonly ROOT_BASE=/var/lib/aquaops-k3s-deploy
readonly SVC_BASE=/var/lib/aquaops-k3s
readonly REPO_URL=https://github.com/fatii-elb/aquaops.git
readonly SAFE_PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
readonly -a AS_SVC=(setpriv --reuid=aquaops-k3s --regid=aquaops-k3s --init-groups --inh-caps=-all --)
readonly -a K3S_IMPORT=(k3s ctr -n k8s.io images import)
# END FIXED CONFIGURATION
EOF
diff -q "$SB/cfg" "$SB/cfg.expected" > /dev/null && ok "production paths, accounts, privilege drop and repository in the shipped wrapper" || ko "shipped configuration block differs"
grep -qx 'readonly ALLOWED_ENVS="rc11"           # disposable environment only, for now' <(tr -d '\r' < "$WRAPPER_SRC") \
    && ok "only rc11 is deployable" || ko "ALLOWED_ENVS"
[ "$(head -n 1 "$WRAPPER_SRC" | tr -d '\r')" = '#!/bin/bash -p' ] && ok "shebang bash -p" || ko "shebang"
diff <(tr -d '\r' < "$WRAPPER_SRC" | sed '/^# BEGIN FIXED/,/^# END FIXED/d') <(sed '/^# BEGIN FIXED/,/^# END FIXED/d' "$W") > /dev/null \
    && ok "tested copy identical to the shipped file outside the configuration block" || ko "tested copy differs"
S="$(tr -d '\r' < "$SUDOERS")"
grep -qxF 'aquaops-k3s-runner ALL=(root) NOPASSWD: /usr/local/sbin/aquaops-k3s-deploy ^rc11 [0-9a-f]{40} [0-9]{9,11}$' <<< "$S" \
    && ok "sudoers: one rule, k3s runner -> wrapper only, 'rc11 <sha> <digits>'" || ko "sudoers rule"
grep -qxF 'Defaults!/usr/local/sbin/aquaops-k3s-deploy !log_input, !log_output' <<< "$S" \
    && ok "sudoers: stdin (the token) never recorded" || ko "sudoers: log_input"
[ "$(grep -cvE '^[[:space:]]*(#|$)' <<< "$S")" = 2 ] && ok "sudoers: no other active line" || ko "sudoers: extra lines"
grep -vE '^[[:space:]]*(#|$)' <<< "$S" | grep -qE 'SETENV|NOPASSWD: *ALL|\(ALL\)|aquaops-runner ' \
    && ko "sudoers grants SETENV, ALL, or the VM runner" || ok "sudoers: no SETENV, no ALL, not the VM runner account"
if command -v visudo > /dev/null; then
    printf '%s\n' "$S" > "$SB/sudoers.check"
    visudo -cf "$SB/sudoers.check" > /dev/null 2>&1 && ok "visudo accepts the sudoers file ($(visudo -V | head -n 1))" || ko "visudo rejects the sudoers file"
else
    echo "  SKIP  visudo not installed (package sudo): syntax not checked"
fi
WF="$(tr -d '\r' < "$WORKFLOW")"
grep -qE '^  workflow_dispatch:' <<< "$WF" && ! grep -qE '^  (workflow_run|push|pull_request|pull_request_target|schedule):' <<< "$WF" \
    && ok "workflow: manual trigger only (automatic trigger still commented out)" || ko "workflow triggers"
grep -qx 'permissions: {}' <<< "$WF" && ok "workflow: no default token permission" || ko "workflow permissions"
sed -n '/^  deploy:/,$p' <<< "$WF" > "$SB/deploy-job"
grep -q 'actions/checkout' "$SB/deploy-job" && ko "deploy job checks out code on the privileged runner" || ok "deploy job: no checkout on the privileged runner"
grep -qE '^    runs-on: \[self-hosted, aquaops-k3s\]$' "$SB/deploy-job" && ok "deploy job: dedicated aquaops-k3s runner" || ko "deploy job runner"
grep -qE '^    environment: k3s-' "$SB/deploy-job" && ok "deploy job: protected environment k3s-<env>" || ko "deploy job environment"
grep -qE '^        shell: bash$' "$SB/deploy-job" && ok "deploy step: shell bash (pipefail: the wrapper's failure fails the step)" || ko "deploy step shell"
# shellcheck disable=SC2016  # the literal text of the workflow, not an expansion
grep -qF '| sudo -n /usr/local/sbin/aquaops-k3s-deploy "$ENV_NAME" "$SHA" "$PUSHED_AT"' "$SB/deploy-job" && ok "deploy step: the sudo call matches the sudoers rule" || ko "deploy step sudo call"

echo
echo "RESULT: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
