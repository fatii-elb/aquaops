#!/usr/bin/env bash
# =============================================================================
# Tests of deploy/vm/aquaops-deploy. Card SCRUM-55 / NFR-08 · E5
#
# No root, no Docker, no network, no real secret: the wrapper runs as the
# current user against a local git "remote", with test doubles of docker,
# curl, git and stat (deploy/vm/tests/mocks). Only the FIXED CONFIGURATION block of
# the wrapper is replaced (sandbox paths, current user); the rest is the file
# under review.
#
#     bash deploy/vm/tests/test-aquaops-deploy.sh
#
# Needs Linux (or WSL) with bash, git, flock, awk, base64, tar and GNU stat.
# The sandbox must be on a Linux file system (mode bits are tested), not on a
# Windows drive: AQUAOPS_TEST_TMP, default ${TMPDIR:-/tmp}. Deleted at the end.
# Exit code: 0 if every test passes.
# =============================================================================
# "check && ok || ko" is intended: ok and ko always succeed.
# shellcheck disable=SC2015
set -uo pipefail
# The sandbox must not depend on the caller's umask. Ubuntu gives ordinary
# users 002: the test-double folder (first in the wrapper's PATH) was then
# group-writable, the wrapper rightly refused it as a PATH directory, and 62
# tests failed although the wrapper was correct. Every mode the tests need
# is set explicitly below; 022 is the base they assume.
umask 022

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
WRAPPER_SRC="$REPO/deploy/vm/aquaops-deploy"
SB="$(mktemp -d "${AQUAOPS_TEST_TMP:-${TMPDIR:-/tmp}}/aquaops-deploy-test.XXXXXX")"
trap 'chmod -R u+rwx "$SB" 2> /dev/null; rm -rf "$SB"' EXIT
ME="$(id -un)"
# Fake values, low entropy on purpose (not secrets, nothing for Gitleaks).
# Main fake token: same shape as the Actions GITHUB_TOKEN since October 2026
# ("ghs_<app id>_<JWT>", ~540 characters with "." "-" "_"). Built at run time
# from repeated letters so that no secret scanner sees a real-looking token.
rep() { local s=""; for ((i = 0; i < $2; i++)); do s+="$1"; done; printf '%s' "$s"; }
TOKEN="gh""s_123456_$(rep a 180).$(rep b 180).$(rep 'c-_' 55)"
LEGACY_TOKEN=FAKEFAKEFAKEFAKEFAKEFAKEFAKE0000                  # old short style
PAT_TOKEN="github""_pat_$(rep D 22)_$(rep e 59)"                 # fine-grained PAT shape
TOKEN_B64="$(printf 'x-access-token:%s' "$TOKEN" | base64 -w 0)"
ACTOR=octo-tester
GOOD_STDIN="$TOKEN\n$ACTOR\n"
W="$SB/bin/aquaops-deploy"
BASE="$SB/base"
LOG="$SB/mock.log"
STUB_REC="$BASE/state/stub-record"
ALL_OUT="$SB/all-output"
: > "$ALL_OUT"

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  PASS  $1"; }
ko() { FAIL=$((FAIL + 1)); echo "  FAIL  $1"; }
section() { echo; echo "== $1"; }

# --- Sandbox -------------------------------------------------------------------
mkdir -p "$SB/bin" "$SB/etc" "$SB/mocks" "$SB/src"
chmod 755 "$SB/bin" "$SB/etc"
mkdir -m 700 "$BASE"
for m in docker curl git stat; do tr -d '\r' < "$HERE/mocks/$m" > "$SB/mocks/$m"; chmod 755 "$SB/mocks/$m"; done

mock_conf() {   # mock_conf [VAR=value ...]: behaviour of the test doubles
    {
        printf 'MOCK_LOG=%q\nMOCK_TOKEN=%q\nMOCK_TOKEN_B64=%q\n' "$LOG" "$TOKEN" "$TOKEN_B64"
        for kv in "$@"; do printf '%s=%q\n' "${kv%%=*}" "${kv#*=}"; done
    } > "$SB/mocks/mock.conf"
}
mock_conf

write_env() {   # fake test.env, mode 600; extra lines as arguments
    {
        echo "DB_HOST=postgres"
        echo "DB_USER=aquaops_test"
        echo "DB_PASSWORD=not-a-secret"
        echo "KAFKA_BOOTSTRAP_SERVERS=kafka:9092"
        echo "KEYCLOAK_ISSUER_URI=http://keycloak:8180/realms/aquaops"
        echo "KEYCLOAK_ADMIN=admin"
        echo "KEYCLOAK_ADMIN_PASSWORD=not-a-secret"
        echo "KEYCLOAK_AUDIT_CLIENT_SECRET=not-a-secret"
        for l in "$@"; do echo "$l"; done
    } > "$SB/etc/test.env"
    chmod 600 "$SB/etc/test.env"
}
write_env

# Wrapper under test: only the fixed configuration block is replaced.
awk -v cfg="readonly DEPLOY_USER=$ME
readonly BASE=$BASE
readonly SELF=$W
readonly ROOT_OWNER=$ME
readonly CONF_DIR=$SB/etc
readonly ENV_FILE=$SB/etc/test.env
readonly REPO_URL=file://$SB/remote.git
readonly SAFE_PATH=$SB/mocks:/usr/local/bin:/usr/bin:/bin" '
    /^# BEGIN FIXED CONFIGURATION/ { print; print cfg; skip = 1; next }
    /^# END FIXED CONFIGURATION/   { skip = 0 }
    !skip' "$WRAPPER_SRC" | tr -d '\r' > "$W"
chmod 755 "$W"

# Local "GitHub", real deploy files and the real publish.yml, whose three
# switches each test commit sets itself (whatever the repository has today):
#   main: C0 (policy off: off/off/'0') -> CP (partly on: on/on/'0') ->
#         C1 (on: on/on/'1') -> C2 (on, stub deploy-test.sh)
#   branch "feature": C1 -> CX, never merged.
G() { git -C "$SB/src" -c user.name=test -c user.email=test@example.invalid "$@"; }
git init --quiet -b main "$SB/src"
for p in deploy/scripts/deploy-test.sh deploy/scripts/wait-healthy.sh deploy/test/compose.yml \
         deploy/test/compose.infra.yml .github/workflows/publish.yml; do
    mkdir -p "$SB/src/$(dirname "$p")"; tr -d '\r' < "$REPO/$p" > "$SB/src/$p"
done
mkdir -p "$SB/src/deploy/integration" "$SB/src/docs"
cp -r "$REPO/deploy/integration/postgres-init" "$SB/src/deploy/integration/"
cp -r "$REPO/docs/keycloak" "$SB/src/docs/"
PUB="$SB/src/.github/workflows/publish.yml"
set_policy() {   # set_policy <DEPLOY_TEST> <GHCR_PUBLISH> <TRIVY_EXIT_CODE>
    sed -i -E "s/^  DEPLOY_TEST: '[a-z-]+'\$/  DEPLOY_TEST: '$1'/; s/^  GHCR_PUBLISH: '[a-z-]+'\$/  GHCR_PUBLISH: '$2'/; s/^  TRIVY_EXIT_CODE: '[01]'\$/  TRIVY_EXIT_CODE: '$3'/" "$PUB"
    [ "$(grep -cxE "  (DEPLOY_TEST: '$1'|GHCR_PUBLISH: '$2'|TRIVY_EXIT_CODE: '$3')" "$PUB")" = 3 ] \
        || { echo "SETUP ERROR: the three switches of publish.yml were not found"; exit 1; }
}
set_policy off off 0
echo zero > "$SB/src/docs/keycloak/marker"
G add -A && G commit --quiet -m C0
C0="$(G rev-parse HEAD)"
set_policy on on 0
echo partial > "$SB/src/docs/keycloak/marker"
G commit --quiet -am CP
CP="$(G rev-parse HEAD)"
set_policy on on 1
echo one > "$SB/src/docs/keycloak/marker"
G commit --quiet -am C1
C1="$(G rev-parse HEAD)"
G switch --quiet -c feature
echo unreviewed > "$SB/src/docs/keycloak/marker"
G commit --quiet -am CX
CX="$(G rev-parse HEAD)"
G switch --quiet main
# Stub deploy-test.sh: records how it was started; behaviour from
# $HOME/stub.ctl ($HOME is the wrapper's BASE).
cat > "$SB/src/deploy/scripts/deploy-test.sh" <<'STUB'
#!/usr/bin/env bash
rc=0; echo_token=no; pause=0; hold=no
[ ! -f "$HOME/stub.ctl" ] || . "$HOME/stub.ctl"
{
    echo "script=$0"
    echo "cwd=$PWD"
    echo "args=$#"
    env | grep -v '^GHCR_TOKEN=' | sort
    [ "${GHCR_TOKEN:-}" = "" ] && echo "GHCR_TOKEN=<empty>" || echo "GHCR_TOKEN=<set>"
    echo "GHCR_TOKEN_LENGTH=${#GHCR_TOKEN}"
} > "$AQUAOPS_STATE_DIR/stub-record"
[ "$echo_token" = no ] || echo "stub output: token is $GHCR_TOKEN"
sleep "$pause"
# hold=yes: wait until the test creates $HOME/stub.go (deterministic concurrency test)
if [ "$hold" = yes ]; then for _ in $(seq 600); do [ -f "$HOME/stub.go" ] && break; sleep 0.1; done; fi
exit "$rc"
STUB
echo two > "$SB/src/docs/keycloak/marker"
G commit --quiet -am C2
C2="$(G rev-parse HEAD)"
git clone --quiet --bare "$SB/src" "$SB/remote.git"

# --- Helpers ------------------------------------------------------------------
run() {   # run <stdin> <args...>: sets RC and OUT
    rm -f "$STUB_REC"
    OUT="$(printf '%b' "$1" | "$W" "${@:2}" 2>&1)"; RC=$?
    printf '%s\n' "$OUT" >> "$ALL_OUT"
}
expect_rc()  { [ "$RC" = "$1" ] && ok "$2 (exit $RC)" || { ko "$2: exit $RC, expected $1"; printf '        %s\n' "$OUT" | tail -n 25; }; }
expect_out() { grep -qE -- "$1" <<< "$OUT" && ok "$2" || { ko "$2: output lacks /$1/"; printf '        %s\n' "$OUT" | tail -n 5; }; }
stub_ran()   { [ -f "$STUB_REC" ] && ok "$1" || ko "$1: deploy-test.sh did not run"; }
stub_idle()  { [ ! -f "$STUB_REC" ] && ok "$1" || ko "$1: deploy-test.sh ran"; }
rec()        { grep -qxF -- "$1" "$STUB_REC" 2> /dev/null && ok "$2" || ko "$2: record lacks [$1]"; }
ctl()        { printf '%s\n' "$@" > "$BASE/stub.ctl"; }
ctl

# =============================================================================
section "1. Arguments: only <sha> <epoch> (or 'check <sha>') reach the deployment"
run "$GOOD_STDIN";                               expect_rc 2 "no argument"
run "$GOOD_STDIN" "${C2:0:7}" 1;                 expect_rc 2 "short SHA"
run "$GOOD_STDIN" "${C2^^}" 1;                   expect_rc 2 "uppercase SHA"
run "$GOOD_STDIN" "$C2" 1 extra;                 expect_rc 2 "third argument"
run "$GOOD_STDIN" "$C2 1";                       expect_rc 2 "both values in one argument"
run "$GOOD_STDIN" "$C2" abc;                     expect_rc 2 "push time not a number"
run "$GOOD_STDIN" "$C2" 1234567890123;           expect_rc 2 "push time too long"
run "$GOOD_STDIN" "$C2" -1;                      expect_rc 2 "negative push time"
run "$GOOD_STDIN" --help "$C2";                  expect_rc 2 "option-like argument"
run "$GOOD_STDIN" check "${C2:0:12}";            expect_rc 2 "check with short SHA"
run "$GOOD_STDIN" "${C2:0:39}g" 1;               expect_rc 2 "non-hex character"
run "$GOOD_STDIN" "$C2"$'\n' 1;                  expect_rc 2 "SHA followed by a newline"
run "$GOOD_STDIN" "${C2:0:30};touch\ $SB/pwn" 1; expect_rc 2 "command injection ;"
run "$GOOD_STDIN" "\$(touch $SB/pwn)" 1;         expect_rc 2 "command substitution"
run "$GOOD_STDIN" "$C2" "1\$(touch $SB/pwn)";    expect_rc 2 "injection in the push time"
[ ! -e "$SB/pwn" ] && ok "no injected command ran" || ko "an injected command ran"
grep -qF -- "touch" "$ALL_OUT" && ko "rejected values are echoed back" || ok "rejected values are never echoed back"
stub_idle "deploy-test.sh never ran for rejected arguments"

# =============================================================================
section "2. Credentials: token and user name on stdin only, fail safe"
run "" "$C2" 1;                                  expect_rc 2 "empty stdin";  expect_out "malformed token" "  says the token is missing"
run "short\n$ACTOR\n" "$C2" 1;                   expect_rc 2 "token too short"
run "FAKE FAKE FAKE FAKE FAKE FAKE\n$ACTOR\n" "$C2" 1; expect_rc 2 "token with spaces"
run "FAKE\"FAKEFAKEFAKEFAKEFAKE\n$ACTOR\n" "$C2" 1; expect_rc 2 "token with a quote"
run "FAKE\$(id)FAKEFAKEFAKEFAKEFAKE\n$ACTOR\n" "$C2" 1; expect_rc 2 "token with \$( )"
run "FAKEFAKE;FAKEFAKEFAKEFAKEFAKE\n$ACTOR\n" "$C2" 1; expect_rc 2 "token with ;"
run "FAKEFAKE/FAKEFAKEFAKE+FAKE=\n$ACTOR\n" "$C2" 1; expect_rc 2 "token with / + ="
run "$(rep a 4097)\n$ACTOR\n" "$C2" 1;           expect_rc 2 "token longer than 4096 characters"
run "$TOKEN" "$C2" 1;                            expect_rc 2 "no user name line"
run "$TOKEN\nbad user;id\n" "$C2" 1;             expect_rc 2 "user name with shell characters"
run "$TOKEN\n-leading-dash\n" "$C2" 1;           expect_rc 2 "user name starting with a dash"
cp -r "$SB/remote.git" "$SB/remote.bak"; rm -rf "$SB/remote.git"
run "$GOOD_STDIN" "$C2" 1;                       expect_rc 2 "repository unreachable (bad token or network)"
expect_out "cannot fetch main" "  clear message"
rm -rf "$SB/remote.git"; mv "$SB/remote.bak" "$SB/remote.git"
stub_idle "deploy-test.sh never ran without valid credentials"
# Every real token format is accepted and reaches deploy-test.sh whole.
for t in "$TOKEN" "$LEGACY_TOKEN" "$PAT_TOKEN"; do
    case "$t" in
        "$TOKEN") label="Actions GITHUB_TOKEN, stateless JWT format (. - _)" ;;
        "$LEGACY_TOKEN") label="legacy short format" ;;
        *) label="fine-grained PAT format" ;;
    esac
    run "$t\n$ACTOR\n" "$C2" 1; expect_rc 0 "token accepted: $label, ${#t} characters"
    rec "GHCR_TOKEN_LENGTH=${#t}" "  handed to deploy-test.sh whole"
done

# =============================================================================
section "3. Only commits on main"
run "$GOOD_STDIN" "$CX" 1;                       expect_rc 2 "commit of an unmerged branch (never fetched: only main is)"
expect_out "does not exist" "  clear message"
run "$GOOD_STDIN" "0123456789abcdef0123456789abcdef01234567" 1; expect_rc 2 "unknown commit"
run "$GOOD_STDIN" "$C2" 1700000000;              expect_rc 0 "tip of main"
stub_ran "deploy-test.sh ran for the tip of main"
[ ! -e "$BASE/repo.git/hooks" ] && ok "private repository created without hook templates" || ko "hooks folder created"
# A hook in the private repository is never run (core.hooksPath=/dev/null):
# reference-transaction would fire on every fetch that moves main.
mkdir -p "$BASE/repo.git/hooks"
for h in reference-transaction post-checkout pre-auto-gc; do
    printf '#!/bin/sh\ntouch %s/pwn-hook-%s\n' "$SB" "$h" > "$BASE/repo.git/hooks/$h"; chmod 755 "$BASE/repo.git/hooks/$h"
done
# Commit already fetched, then no longer on main (history rewritten): refused.
git -C "$SB/remote.git" update-ref refs/heads/main "$C1"
run "$GOOD_STDIN" "$C2" 1;                       expect_rc 2 "commit no longer on main (main rewritten)"
expect_out "not on main" "  clear message"
git -C "$SB/remote.git" update-ref refs/heads/main "$C2"
run "$GOOD_STDIN" "$C2" 1;                       expect_rc 0 "main restored: deployed again"
hooks_run="$(find "$SB" -maxdepth 1 -name 'pwn-hook-*' -printf '%f ')"
[ -z "$hooks_run" ] && ok "no git hook ran while main moved twice" || ko "git hook ran: $hooks_run"
rm -rf "$BASE/repo.git/hooks"
rm -f "$STUB_REC"
stub_idle "deploy-test.sh never ran for an unauthorised commit"

# =============================================================================
section "3b. The deployment policy committed in THAT commit must be on"
run "$GOOD_STDIN" "$C0" 1;   expect_rc 2 "commit of main with the policy off"
expect_out "policy of $C0 does not allow" "  clear message"
stub_idle "  nothing deployed"
run "$GOOD_STDIN" "$CP" 1;   expect_rc 2 "policy partly on (Trivy still audit-only)"
[ "$(cat "$BASE/release/docs/keycloak/marker")" = two ] && ok "  release folder untouched by refused requests" || ko "  release changed"
run "$GOOD_STDIN" "$C1" 1;   expect_rc 0 "policy fully on: deployed"
run "$GOOD_STDIN" check "$C0"; expect_rc 0 "check mode still runs with the policy off"
expect_out "NOTE: the policy of $C0" "  and says a deployment would be refused"

# =============================================================================
section "4. Fixed settings: the caller cannot redirect anything"
run "$GOOD_STDIN" "$C2" 1700000000;              expect_rc 0 "deployment of the tip of main"
rec "IMAGE_TAG=$C2" "IMAGE_TAG = requested SHA"
rec "PUSHED_AT=1700000000" "PUSHED_AT = requested push time"
rec "AQUAOPS_ENV_FILE=$SB/etc/test.env" "env file fixed"
rec "AQUAOPS_PROJECT=aquaops-test" "Compose project fixed"
rec "AQUAOPS_WITH_INFRA=true" "infrastructure layer fixed"
rec "AQUAOPS_STATE_DIR=$BASE/state" "state folder fixed"
rec "IMAGE_NAMESPACE=ghcr.io/fatii-elb/aquaops" "registry namespace fixed"
rec "GHCR_USERNAME=$ACTOR" "registry user from stdin"
rec "GHCR_TOKEN=<set>" "registry token handed over (environment only)"
rec "script=$BASE/release/deploy/scripts/deploy-test.sh" "script from the trusted export, not a workspace"
rec "cwd=/" "working directory /"
rec "args=0" "no argument passed to deploy-test.sh"
[ "$(cat "$BASE/release/docs/keycloak/marker")" = two ] && ok "export = files of the requested commit" || ko "export content"

mkdir -p "$SB/evil/deploy/scripts" "$SB/evilbin"
printf '#!/bin/sh\ntouch %s/pwn-workspace\n' "$SB" > "$SB/evil/deploy/scripts/deploy-test.sh"
for b in docker git bash awk; do printf '#!/bin/sh\ntouch %s/pwn-path-%s\nexit 1\n' "$SB" "$b" > "$SB/evilbin/$b"; chmod 755 "$SB/evilbin/$b"; done
printf 'touch %s/pwn-bashenv\n' "$SB" > "$SB/evil/bashenv"
rm -f "$STUB_REC"
OUT="$(cd "$SB/evil" && printf '%b' "$GOOD_STDIN" | env \
    PATH="$SB/evilbin:$PATH" BASH_ENV="$SB/evil/bashenv" ENV="$SB/evil/bashenv" \
    'BASH_FUNC_git%%=() { touch '"$SB"'/pwn-func; }' SHELLOPTS=xtrace PS4='+TRACE ' \
    AQUAOPS_ENV_FILE=/etc/passwd AQUAOPS_PROJECT=production AQUAOPS_STATE_DIR="$SB/evil" \
    IMAGE_NAMESPACE=evil.example/x IMAGE_TAG=latest DOCKER_HOST=tcp://192.0.2.1:2375 \
    DOCKER_CONFIG="$SB/evil" GIT_DIR="$SB/evil" GIT_CONFIG_PARAMETERS="'core.pager=touch $SB/pwn-git'" \
    GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=core.fsmonitor GIT_CONFIG_VALUE_0="touch $SB/pwn-git2" \
    COMPOSE_FILE="$SB/evil/x.yml" "$W" "$C2" 1 2>&1)"; RC=$?
printf '%s\n' "$OUT" >> "$ALL_OUT"
expect_rc 0 "hostile caller environment and working directory: deployment still uses fixed values"
pwned="$(find "$SB" -maxdepth 1 -name 'pwn*' -printf '%f ')"
[ -z "$pwned" ] && ok "no caller file, PATH entry, BASH_ENV, exported function or git setting was used" || ko "caller influence: $pwned"
grep -q '^+TRACE' <<< "$OUT" && ko "SHELLOPTS=xtrace imported (trace would print values)" || ok "SHELLOPTS/xtrace from the caller ignored"
rec "AQUAOPS_PROJECT=aquaops-test" "  project still fixed"
rec "IMAGE_TAG=$C2" "  IMAGE_TAG still the SHA"
grep -qE '^(DOCKER_HOST|DOCKER_CONFIG|GIT_DIR|COMPOSE_FILE|BASH_ENV)=' "$STUB_REC" \
    && ko "caller variable reached deploy-test.sh" || ok "  no caller variable reached deploy-test.sh"

# =============================================================================
section "5. Trusted paths: owner, mode, no symbolic links"
chmod 775 "$BASE"; run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "deploy folder writable by group"; chmod 700 "$BASE"
chmod 757 "$SB/etc"; run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "config folder writable by others"; chmod 755 "$SB/etc"
chmod 775 "$W"; run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "wrapper writable by group"; chmod 755 "$W"
chmod 777 "$SB/bin"; run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "wrapper folder writable by others"; chmod 755 "$SB/bin"
chmod 640 "$SB/etc/test.env"; run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "env file readable by group"; chmod 600 "$SB/etc/test.env"
chmod 701 "$BASE"; run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "deploy folder traversable by others"; expect_out "not be accessible to others" "  clear message"; chmod 700 "$BASE"
chmod 777 "$SB/mocks"; run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "PATH directory writable by others"; expect_out "PATH directory" "  clear message"; chmod 755 "$SB/mocks"
# Symbolic links: refused by the link check itself (the message says so).
mv "$SB/etc/test.env" "$SB/real.env"; ln -s "$SB/real.env" "$SB/etc/test.env"
run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "env file replaced by a symbolic link"; expect_out "test.env is a symbolic link" "  refused as a link"
rm "$SB/etc/test.env"; mv "$SB/real.env" "$SB/etc/test.env"
mv "$BASE/state" "$SB/state.real"; ln -s "$SB/evil" "$BASE/state"
run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "state folder replaced by a symbolic link"; expect_out "state is a symbolic link" "  refused as a link"
rm "$BASE/state"; mv "$SB/state.real" "$BASE/state"
mv "$BASE/repo.git" "$SB/repo.real"; ln -s "$SB/evil" "$BASE/repo.git"
run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "repository replaced by a symbolic link"; expect_out "repo.git is a symbolic link" "  refused as a link"
rm "$BASE/repo.git"; mv "$SB/repo.real" "$BASE/repo.git"
rm -f "$BASE/lock"; ln -s "$SB/evil/lockfile" "$BASE/lock"
run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "lock file replaced by a symbolic link"; expect_out "lock is a symbolic link" "  refused as a link"
rm -f "$BASE/lock"
# Owners (reported by the stat test double: no root needed). Mode and type are
# correct here, so only the owner rule can refuse.
for p in "$W:wrapper" "$SB/bin:wrapper folder" "$SB/etc:config folder" "$SB/etc/test.env:env file" \
         "$BASE:deploy folder" "$BASE/state:state folder" "$BASE/repo.git:private repository" "$SB/mocks:PATH directory"; do
    mock_conf "MOCK_STAT_FOREIGN=${p%%:*}"
    run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "${p#*:} owned by another user"; expect_out "must be owned by" "  refused by the owner rule"
done
mock_conf
sed -i "s/^readonly DEPLOY_USER=.*/readonly DEPLOY_USER=someone-else/" "$W"
run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "not running as the deployment account"; expect_out "must run as someone-else" "  clear message"
sed -i "s/^readonly DEPLOY_USER=.*/readonly DEPLOY_USER=$ME/" "$W"
stub_idle "deploy-test.sh never ran with an untrusted path"
run "$GOOD_STDIN" "$C2" 1; expect_rc 0 "all paths restored: deployment allowed again"

# =============================================================================
section "6. One deployment at a time"
# Deterministic: the holder keeps the lock until the test releases it, and
# the first deployment waits in its stub until the second request is done
# (fixed sleeps raced on loaded hosts: 62-test investigation, Sprint 6).
wait_locked() { for _ in $(seq 600); do flock -n "$BASE/lock" true || return 0; sleep 0.1; done; return 1; }
rm -f "$SB/unlock"
( exec 8> "$BASE/lock"; flock 8; until [ -f "$SB/unlock" ]; do sleep 0.1; done ) &
holder=$!; wait_locked
run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "lock already held"; expect_out "another deployment is running" "  clear message"
touch "$SB/unlock"; wait "$holder"; rm -f "$SB/unlock"
ctl "hold=yes"
rm -f "$STUB_REC" "$BASE/stub.go"
( printf '%b' "$GOOD_STDIN" | "$W" "$C2" 1 > "$SB/p1.out" 2>&1; echo $? > "$SB/p1.rc" ) &
first=$!
for _ in $(seq 600); do [ -f "$STUB_REC" ] && break; sleep 0.1; done
printf '%b' "$GOOD_STDIN" | "$W" "$C2" 1 > "$SB/p2.out" 2>&1; echo $? > "$SB/p2.rc"
touch "$BASE/stub.go"; wait "$first"; rm -f "$BASE/stub.go"
ctl
r="$(cat "$SB/p1.rc") $(cat "$SB/p2.rc")"
[ "$r" = "0 2" ] && ok "two simultaneous requests: first deploys, second refused (exit $r)" || ko "simultaneous requests: exit $r"
run "$GOOD_STDIN" "$C2" 1; expect_rc 0 "lock released after the run"

# =============================================================================
section "7. Host checks: no second simulator, no port conflict"
mock_conf "MOCK_HOST_PS=aquaops-simulator|aquaops/simulator:local||8086/tcp"
run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "standalone simulator running"; expect_out "two simulators" "  explained"
stub_idle "  nothing deployed"
mock_conf "MOCK_HOST_PS=aquaops-test-simulator-1|ghcr.io/fatii-elb/aquaops/simulator:$C1|aquaops-test|"
run "$GOOD_STDIN" "$C2" 1; expect_rc 0 "simulator of the project itself is fine"
mock_conf "MOCK_HOST_PS=other-gw|nginx|other|127.0.0.1:8080->80/tcp"
run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "port 8080 used by another project"; expect_out "port 8080" "  explained"
mock_conf "MOCK_HOST_PS=kc|keycloak|deploy|0.0.0.0:8180->8180/tcp, [::]:8180->8180/tcp"
run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "port 8180 used by another project"
write_env "GATEWAY_HOST_PORT=9090"
mock_conf "MOCK_HOST_PS=other-gw|nginx|other|127.0.0.1:8080->80/tcp"
run "$GOOD_STDIN" "$C2" 1; expect_rc 0 "port from the env file (9090): 8080 elsewhere is fine"
mock_conf "MOCK_HOST_PS=other-gw|nginx|other|127.0.0.1:9090->80/tcp"
run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "port from the env file (9090) already used"
write_env
mock_conf MOCK_DAEMON=down
run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "Docker unreachable"; expect_out "docker group" "  explained"
mock_conf

# =============================================================================
section "8. 'check' mode changes nothing"
snap() { { find "$BASE/release" "$BASE/state" -printf '%p %s %T@\n'; find "$BASE/tmp" -mindepth 1; } | sort | md5sum; }
before="$(snap)"
mock_conf "MOCK_PROJECT_PS=aquaops-test-gateway-1  ghcr.io/fatii-elb/aquaops/gateway:$C1  running"
: > "$LOG"
run "$GOOD_STDIN" check "$C2"; expect_rc 0 "check of a commit on main"; expect_out "CHECK OK" "  reports OK"
expect_out "aquaops-test-gateway-1" "  lists the containers of project aquaops-test"
stub_idle "  deploy-test.sh not run"
grep -qE "compose -p aquaops-test -f $BASE/tmp/check/deploy/test/compose.yml -f $BASE/tmp/check/deploy/test/compose.infra.yml --env-file $SB/etc/test.env config --quiet \| IMAGE_TAG=$C2" "$LOG" \
    && ok "  validates the Compose configuration (quiet, fixed files)" || ko "  compose config call"
grep -qE 'compose .* (pull|up)|docker (login|rm|stop|start)' "$LOG" && ko "  touched images or containers" || ok "  no pull, up, login, stop or rm"
# Allow-list: the only Docker commands of check mode are read-only ones.
other="$(grep '^CALL docker ' "$LOG" | sed 's/ | .*//' \
    | grep -vE '^CALL docker (version --format|ps |compose -p aquaops-test -f [^ ]+ -f [^ ]+ --env-file [^ ]+ config --quiet$)' || true)"
[ -z "$other" ] && ok "  only docker version, docker ps and compose config --quiet" || ko "  other docker call: $other"
after="$(snap)"
[ "$before" = "$after" ] && ok "  release, state and temporary folders unchanged" || ko "  something changed"
mock_conf MOCK_CONFIG=fail
run "$GOOD_STDIN" check "$C2"; expect_rc 2 "check: Compose rejects the env file"
mock_conf
run "$GOOD_STDIN" check "$CX"; expect_rc 2 "check: unmerged commit refused"
mock_conf "MOCK_PROJECT_PS="
run "$GOOD_STDIN" check "$C2"; expect_out "no container yet" "check warns when project aquaops-test does not exist"
mock_conf

# =============================================================================
section "9. Exit codes, token never shown or stored"
ctl "rc=1"; run "$GOOD_STDIN" "$C2" 1; expect_rc 1 "deploy-test.sh failure -> 1"
ctl "rc=2"; run "$GOOD_STDIN" "$C2" 1; expect_rc 2 "deploy-test.sh refusal -> 2"
ctl "rc=7"; run "$GOOD_STDIN" "$C2" 1; expect_rc 1 "unexpected code -> 1"
ctl "echo_token=yes"; run "$GOOD_STDIN" "$C2" 1; expect_rc 0 "script printing the token"
expect_out 'token is \*\*\*' "  token replaced by *** in the output"
ctl
grep -qF -- "$TOKEN" "$ALL_OUT" && ko "token visible in an output" || ok "token never in any output of this run"
grep -qF -- "$TOKEN_B64" "$ALL_OUT" && ko "encoded token visible" || ok "encoded token never in any output"
grep -q 'TOKEN-IN-ARGV' "$LOG" && ko "token on a command line (git or docker)" || ok "token never on a git or docker command line"
grep -rqF -- "$TOKEN" "$BASE" 2> /dev/null && ko "token written under $BASE" || ok "token never written to disk (repository, state, release)"
grep -rqF -- "$TOKEN_B64" "$BASE" 2> /dev/null && ko "encoded token written to disk" || ok "encoded token never written to disk"
grep -qi extraheader "$BASE/repo.git/config" && ko "auth header stored in git config" || ok "no credential in the git configuration"

# =============================================================================
section "10. Compatibility with the real deploy-test.sh and wait-healthy.sh"
: > "$LOG"
run "$GOOD_STDIN" "$C1" 1700000000
expect_rc 0 "real deploy-test.sh of an older commit of main: deployed and healthy"
[ "$(cat "$BASE/release/docs/keycloak/marker")" = one ] && ok "  release folder switched to that commit" || ko "  release content"
[ "$(cat "$BASE/state/aquaops-test/deployed-sha" 2> /dev/null || cat "$BASE/state/deployed-sha" 2> /dev/null)" = "$C1" ] \
    && ok "  deployed SHA recorded in the fixed state folder" || ko "  deployed-sha"
grep -qF "compose -p aquaops-test -f $BASE/release/deploy/test/compose.yml -f $BASE/release/deploy/test/compose.infra.yml --env-file $SB/etc/test.env up -d --no-build --pull never" "$LOG" \
    && ok "  up -d on project aquaops-test with the trusted files and env file" || ko "  compose up call"
grep -q 'LOGIN stdin=token-ok' "$LOG" && ok "  registry login receives the token on stdin" || ko "  login"
grep -q 'LOGOUT ghcr.io' "$LOG" && ok "  logout after the pull" || ko "  logout"
grep -qE 'compose .*(down|rm|-v)( |$)|docker (rm|volume|system|prune)' "$LOG" && ko "  destructive command" || ok "  no down, rm, prune or -v: volumes kept"
grep -q 'CALL curl .*actuator/health' "$LOG" && ok "  gateway smoke test run" || ko "  smoke test"
grep -qF -- "$TOKEN" "$ALL_OUT" && ko "  token in output" || ok "  token not in output"
grep -q 'mock-resolved-password' "$ALL_OUT" && ko "  resolved Compose config printed" || ok "  resolved Compose config (passwords) never printed"
grep -q 'token_in_env=yes' "$LOG" && ko "  a docker call still had the token in its environment" || ok "  no docker call has the token in its environment"
write_env "KEYCLOAK_AUDIT_CLIENT_SECRET=change-me"
run "$GOOD_STDIN" "$C1" 1; expect_rc 2 "real deploy-test.sh refuses a placeholder secret -> 2"
write_env

# =============================================================================
section "11. Shipped file"
tr -d '\r' < "$WRAPPER_SRC" | sed -n '/^# BEGIN FIXED CONFIGURATION/,/^# END FIXED CONFIGURATION/p' > "$SB/cfg"
cat > "$SB/cfg.expected" <<'EOF'
# BEGIN FIXED CONFIGURATION (the tests replace this block, nothing else)
readonly DEPLOY_USER=aquaops-deploy
readonly BASE=/var/lib/aquaops-deploy
readonly SELF=/usr/local/sbin/aquaops-deploy
readonly ROOT_OWNER=root
readonly CONF_DIR=/etc/aquaops
readonly ENV_FILE=/etc/aquaops/test.env
readonly REPO_URL=https://github.com/fatii-elb/aquaops.git
readonly SAFE_PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
# END FIXED CONFIGURATION
EOF
diff -q "$SB/cfg" "$SB/cfg.expected" > /dev/null && ok "production paths, user and repository in the shipped wrapper" || ko "shipped configuration block differs"
[ "$(head -n 1 "$WRAPPER_SRC" | tr -d '\r')" = '#!/bin/bash -p' ] && ok "shebang bash -p (no BASH_ENV, no imported functions)" || ko "shebang"
diff <(tr -d '\r' < "$WRAPPER_SRC" | sed '/^# BEGIN FIXED/,/^# END FIXED/d') <(sed '/^# BEGIN FIXED/,/^# END FIXED/d' "$W") > /dev/null \
    && ok "tested copy identical to the shipped file outside the configuration block" || ko "tested copy differs"
grep -qE '^aquaops-runner ALL=\(aquaops-deploy\) NOPASSWD: /usr/local/sbin/aquaops-deploy \^\[0-9a-f\]\{40\} \[0-9\]\+\$$' "$REPO/deploy/vm/aquaops-deploy.sudoers" \
    && ok "sudoers: one rule, runner -> aquaops-deploy, wrapper only, <sha> <digits>" || ko "sudoers rule"
grep -qxF 'Defaults!/usr/local/sbin/aquaops-deploy !log_input, !log_output' "$REPO/deploy/vm/aquaops-deploy.sudoers" \
    && ok "sudoers: stdin (the token) and output of the wrapper never recorded by sudo I/O logging" || ko "sudoers: no !log_input"
[ "$(grep -cvE '^[[:space:]]*(#|$)' "$REPO/deploy/vm/aquaops-deploy.sudoers")" = 2 ] && ok "sudoers: no other active line" || ko "sudoers: extra lines"
grep -vE '^[[:space:]]*(#|$)' "$REPO/deploy/vm/aquaops-deploy.sudoers" | grep -qE 'SETENV|ALL[[:space:]]*$|\(root\)|\(ALL\)' \
    && ko "sudoers grants SETENV, root or ALL commands" || ok "sudoers: no SETENV, no root, no ALL command"

echo
echo "RESULT: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
