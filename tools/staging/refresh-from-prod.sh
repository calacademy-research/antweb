#!/usr/bin/env bash
# Weekly refresh of STAGING's database from production's newest nightly dump.
# It also proves every week that production's backups load.
#
# Runs ON THE STAGING DROPLET as root (ssh root@165.232.136.170), from the deployed copy:
#     /root/antweb-next/tools/staging/refresh-from-prod.sh --dry-run    # show what it would do
#     /root/antweb-next/tools/staging/refresh-from-prod.sh              # refresh now
#     /root/antweb-next/tools/staging/refresh-from-prod.sh --rollback   # load the safety dump again
# Procedure, cron line and rollback: docs/runbook.md, "Staging refresh".
#
# Steps (each logged to journald, `journalctl -t staging-refresh`, and to /root/staging-refresh/refresh.log):
#   1. Refuses unless this is staging: host name "staging", /etc/antweb-role says "staging",
#      the project is /root/antweb-next, AntWeb was started from there, and this is not
#      production's address. Refuses between 04:45 and 08:15 Pacific (nightly run) and while
#      another refresh runs.
#   2. Finds production's newest daily dump in Spaces (bucket antweb-dbarchive, db/daily/), read
#      only, with rclone (or uses --dump FILE). Refuses a dump older than --max-age-days.
#   3. Checks free disk (3 x the dump size + a margin) before downloading anything.
#   4. Downloads the dump and checks it is complete (gzip test, one database `ant`, "Dump completed").
#   5. Saves the staging test logins (password hashes) with create-test-accounts.py --save-to,
#      and the list of columns, to see afterwards what staging had that production has not.
#   6. Dumps staging's current database to one safety dump (the rollback).
#   7. Stops AntWeb, loads the production dump, checks it, scrubs personal data (scrub.sql),
#      puts the test logins back with their saved hashes, adds zztest_uploader to the "Upload as"
#      list of the administrator login(s) in /root/staging-refresh/upload-as.txt (lines
#      adminLogin=NAME; no file: the login antweb) if missing, starts AntWeb and waits for it.
#   8. On any failure in step 7 it loads the safety dump again and starts AntWeb.
#   9. On success it deletes the downloaded dump and the safety dump (--keep-safety keeps the
#      safety dump until the next refresh).
# Nothing is ever written to production or to the Spaces bucket.

set -Eeuo pipefail
umask 077
# cron and systemd-run may start this without HOME; rclone then would not find its settings.
export RCLONE_CONFIG="${RCLONE_CONFIG:-/root/.config/rclone/rclone.conf}"

PROJECT_DIR=/root/antweb-next
EXPECTED_HOST=staging
ROLE_FILE=/etc/antweb-role
PROD_ADDRESSES="165.232.137.86 64.225.91.165"
WORK_DIR=/root/staging-refresh
LOCK_FILE=/run/lock/staging-refresh.lock
DB_CONTAINER=antweb_mysql_1
APP_CONTAINER=antweb_antweb_1
APP_URL=http://127.0.0.1:8081
DATABASE=ant
TAG=staging-refresh

REMOTE="dbarchive-ro:antweb-dbarchive/db/daily"
DUMP_FILE=""
DRY_RUN=0
SCRUB=1
KEEP_SAFETY=0
ROLLBACK=0
NOT_AFTER=""
MAX_AGE_DAYS=3
MARGIN_MB=1024
KEEP_LOGINS_FILE="$WORK_DIR/keep-logins.txt"
UPLOAD_AS_FILE="$WORK_DIR/upload-as.txt"

LOG_FILE=""
SAFETY="$WORK_DIR/staging-before-refresh.sql.gz"
SAVED="$WORK_DIR/keep-accounts.json"
SCHEMA_BEFORE="$WORK_DIR/schema-before.tsv"
APP_STOPPED=0
IN_RESTORE=0

# ---------------------------------------------------------------- pure helpers (unit-tested)

# guard_problems HOST ROLE PROJECT_OK CONTAINER_DIR NOW_HHMM ADDRESSES
# Prints one line per reason not to run; prints nothing when it is safe.
guard_problems() {
  local host=$1 role=$2 project_ok=$3 container_dir=$4 now=$5 addresses=$6 a
  [[ "$host" == "$EXPECTED_HOST" ]] || echo "this host is \"$host\", not \"$EXPECTED_HOST\": staging only"
  [[ "$role" == "staging" ]] || echo "$ROLE_FILE does not say \"staging\" (it says \"$role\")"
  [[ "$project_ok" == 1 ]] || echo "$PROJECT_DIR/etc/db.properties not found"
  [[ "$container_dir" == "$PROJECT_DIR" ]] || echo "$APP_CONTAINER was started from \"$container_dir\", not $PROJECT_DIR"
  for a in $PROD_ADDRESSES; do
    if [[ " $addresses " == *" $a "* ]]; then echo "this server has production's address $a"; fi
  done
  if in_nightly_window "$now"; then echo "it is $now Pacific, inside the nightly run (04:45 to 08:15): try later"; fi
  return 0
}

# in_nightly_window HH:MM -> success between 04:45 and 08:15
in_nightly_window() {
  local m; m=$(hhmm_minutes "$1")
  (( m >= 4 * 60 + 45 && m < 8 * 60 + 15 ))
}

hhmm_minutes() {
  local h=${1%%:*} m=${1##*:}
  echo $(( 10#$h * 60 + 10#$m ))
}

# newest_dump: file names on stdin (YYYY-MM-DD.sql.gz only), prints the newest
newest_dump() {
  { grep -E '^[0-9]{4}-[0-9]{2}-[0-9]{2}\.sql\.gz$' || true; } | sort | tail -n 1
}

# strip_definers: mysqldump text on stdin, without DEFINER=`user`@`host` clauses (outside data
# rows), so routines, triggers and views load as staging's database user, whoever made them.
strip_definers() {
  sed -E '/^INSERT INTO /!s/DEFINER=`[^`]*`@`[^`]*`//g'
}

# dump_age_days NAME TODAY(YYYY-MM-DD)
dump_age_days() {
  local d=${1%.sql.gz}
  echo $(( ( $(date -u -d "$2" +%s) - $(date -u -d "$d" +%s) ) / 86400 ))
}

# needed_mb DUMP_BYTES MARGIN_MB: downloaded dump + safety dump (about the same size) + room
# for the load, i.e. 3 x the compressed dump, plus a fixed margin.
needed_mb() {
  echo $(( ( $1 * 3 + 1048575 ) / 1048576 + $2 ))
}

# ---------------------------------------------------------------- logging and database

pacific_now() { TZ=America/Los_Angeles date "+${1:-%H:%M}"; }

log() {
  local msg="$*"
  logger -t "$TAG" -- "$msg" 2>/dev/null || true
  printf '%s %s\n' "$(pacific_now '%F %T')" "$msg"
  if [[ -n "$LOG_FILE" ]]; then printf '%s %s\n' "$(pacific_now '%F %T')" "$msg" >> "$LOG_FILE"; fi
}

# die MESSAGE [EXIT]: once AntWeb has been stopped, first put staging back (recover).
die() {
  log "ERROR: $1"
  if (( IN_RESTORE || APP_STOPPED )); then trap - ERR; set +e; recover; log "Refresh FAILED"; exit 1; fi
  exit "${2:-1}"
}

load_db_login() {
  local f="$PROJECT_DIR/etc/db.properties"
  DB_USER=$(sed -n 's/^user=//p' "$f")
  MYSQL_PWD=$(sed -n 's/^password=//p' "$f")
  export MYSQL_PWD
  [[ -n "$DB_USER" && -n "$MYSQL_PWD" ]] || die "$f has no user= or password= line"
}

# mysql_ant [mysql options]: SQL from stdin, against `ant`
mysql_ant() {
  docker exec -i -e MYSQL_PWD "$DB_CONTAINER" mysql --default-character-set=utf8mb4 -u "$DB_USER" "$@" "$DATABASE"
}

sql_value() { mysql_ant -N -B <<< "$1"; }

free_mb() { df -Pm "$1" | awk 'NR == 2 { print $4 }'; }

app_answer() {
  curl -s --max-time 10 "$APP_URL/util.do?action=isRestart" 2>/dev/null || true
}

wait_for_app() {
  local i answer
  for i in $(seq 1 40); do   # up to 10 minutes
    answer=$(app_answer)
    if [[ "$answer" == *"<b>false</b>"* || "$answer" == *"<b>true</b>"* ]]; then
      log "AntWeb answers on $APP_URL after about $(( (i - 1) * 15 )) seconds"
      return 0
    fi
    sleep 15
  done
  return 1
}

dump_staging_to() {
  local out=$1
  docker exec -e MYSQL_PWD "$DB_CONTAINER" mysqldump -u "$DB_USER" --routines --single-transaction \
    --no-tablespaces --default-character-set=utf8mb4 --databases "$DATABASE" | gzip -c > "$out.tmp" || return 1
  check_dump "$out.tmp" || return 1
  mv -f "$out.tmp" "$out"
}

# check_dump FILE: complete gzip, exactly one database (`ant`), ends with "Dump completed"
check_dump() {
  local f=$1 summary
  [[ -s "$f" && $(stat -c %s "$f") -ge 1024 ]] || { log "$f is missing or smaller than 1 KB"; return 1; }
  gzip -t "$f" || { log "$f is not a complete gzip file"; return 1; }
  summary=$(gunzip -c "$f" | awk '/^USE `/ { uses = uses $0 " " } { last = $0 } END { print uses "|" last }')
  [[ "${summary%%|*}" == 'USE `ant`; ' ]] || { log "$f: expected only the database ant, found: ${summary%%|*}"; return 1; }
  [[ "${summary#*|}" == "-- Dump completed"* ]] || { log "$f does not end with \"-- Dump completed\""; return 1; }
}

load_dump() {
  gunzip -c "$1" | strip_definers | mysql_ant
}

# ---------------------------------------------------------------- failure: back to the safety dump

on_error() {
  local rc=$? line=${1:-?}
  # In a pipeline's subshell (e.g. mysql in load_dump) only report the failure upwards; the
  # main shell then sees the failed pipeline and recovers once.
  if [[ $BASHPID != "$$" ]]; then exit "$rc"; fi
  trap - ERR
  set +e
  log "ERROR: step failed (line $line, exit $rc)"
  recover
  log "Refresh FAILED"
  exit 1
}

recover() {
  if [[ $IN_RESTORE == 1 && -s "$SAFETY" ]]; then
    log "Loading the safety dump $SAFETY again (staging's database from before this run)"
    if load_dump "$SAFETY"; then
      log "Safety dump loaded: staging's database is as it was before this run"
      docker start "$APP_CONTAINER" >/dev/null && wait_for_app || log "WARNING: AntWeb did not answer within 10 minutes"
    else
      log "CRITICAL: the safety dump did not load. AntWeb is left stopped. See docs/runbook.md, Staging refresh, Rollback."
    fi
  elif [[ $APP_STOPPED == 1 ]]; then
    docker start "$APP_CONTAINER" >/dev/null && log "AntWeb started again (the database was not changed)"
  fi
  IN_RESTORE=0
  APP_STOPPED=0
}

# ---------------------------------------------------------------- steps

usage() {
  sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'
  cat <<EOF
Options:
  --dry-run           show what would happen; change nothing
  --remote R          rclone path of production's daily dumps (default $REMOTE)
  --dump FILE         use this dump file instead of downloading one
  --keep-safety       keep the safety dump after a successful refresh (until the next one)
  --rollback          load the safety dump again (staging back to before the last refresh)
  --no-scrub          keep production's emails and password hashes (refused if mail.properties exists)
  --not-after HH:MM   do not start stopping AntWeb at or after this Pacific time (cron: 03:15)
  --max-age-days N    refuse a production dump older than N days (default $MAX_AGE_DAYS)
  --margin-mb N       free disk needed on top of 3 x the dump size (default $MARGIN_MB)
EOF
}

parse_args() {
  while (( $# )); do
    case "$1" in
      --dry-run) DRY_RUN=1 ;;
      --remote) REMOTE=${2:?}; shift ;;
      --dump) DUMP_FILE=${2:?}; shift ;;
      --keep-safety) KEEP_SAFETY=1 ;;
      --rollback) ROLLBACK=1 ;;
      --no-scrub) SCRUB=0 ;;
      --not-after) NOT_AFTER=${2:?}; shift ;;
      --max-age-days) MAX_AGE_DAYS=${2:?}; shift ;;
      --margin-mb) MARGIN_MB=${2:?}; shift ;;
      -h|--help) usage; exit 0 ;;
      *) echo "unknown option: $1 (see --help)" >&2; exit 64 ;;
    esac
    shift
  done
  if [[ -n "$NOT_AFTER" && ! "$NOT_AFTER" =~ ^[0-2][0-9]:[0-5][0-9]$ ]]; then
    echo "--not-after needs HH:MM" >&2; exit 64
  fi
}

check_guard() {
  local role="" container_dir="" project_ok=0 problems
  [[ -r "$ROLE_FILE" ]] && role=$(tr -d '[:space:]' < "$ROLE_FILE")
  [[ -f "$PROJECT_DIR/etc/db.properties" ]] && project_ok=1
  container_dir=$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}' \
    "$APP_CONTAINER" 2>/dev/null || true)
  problems=$(guard_problems "$(hostname)" "$role" "$project_ok" "$container_dir" "$(pacific_now)" \
    "$(hostname -I 2>/dev/null || true)")
  if [[ -n "$problems" ]]; then
    log "Refused:"
    while IFS= read -r p; do log "  - $p"; done <<< "$problems"
    exit 2
  fi
}

check_mail() {
  local f="$PROJECT_DIR/etc/mail.properties"
  if [[ -e "$f" ]]; then
    if [[ $SCRUB == 0 ]]; then
      die "$f exists: staging could send mail to real people from production's data. Refused with --no-scrub." 2
    fi
    log "WARNING: $f exists on staging. Emails are scrubbed to @example.invalid, so no real person can be reached,"
    log "         but mail to those addresses would bounce at Amazon SES. Recommended: no mail.properties on staging."
  else
    log "Mail: no $f, staging sends no email (messages only go to logbak/emails.log)"
  fi
}

pick_dump() {
  local listing name size
  if [[ -n "$DUMP_FILE" ]]; then
    [[ -f "$DUMP_FILE" ]] || die "--dump $DUMP_FILE not found"
    DUMP_NAME=$(basename "$DUMP_FILE")
    DUMP_BYTES=$(stat -c %s "$DUMP_FILE")
    log "Dump: $DUMP_FILE ($(( DUMP_BYTES / 1048576 )) MB), given on the command line"
    return 0
  fi
  command -v rclone >/dev/null || die "rclone is not installed (see docs/runbook.md, Staging refresh, one-time setup)"
  listing=$(rclone lsf --files-only --format "sp" --separator ";" "$REMOTE" 2>&1) \
    || die "cannot list $REMOTE: ${listing:0:300}"
  name=$(cut -d ';' -f 2 <<< "$listing" | newest_dump)
  [[ -n "$name" ]] || die "no YYYY-MM-DD.sql.gz dump in $REMOTE"
  size=$(awk -F ';' -v n="$name" '$2 == n { print $1 }' <<< "$listing")
  DUMP_NAME=$name
  DUMP_BYTES=$size
  log "Dump: newest in $REMOTE is $name ($(( size / 1048576 )) MB)"
}

check_age() {
  local age
  [[ "$DUMP_NAME" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}\.sql\.gz$ ]] || { log "Dump name has no date: age not checked"; return 0; }
  age=$(dump_age_days "$DUMP_NAME" "$(pacific_now %F)")
  if (( age > MAX_AGE_DAYS )); then
    die "production's newest daily dump is $age days old (more than $MAX_AGE_DAYS): production's nightly backup may be failing. Check it (docs/runbook.md, Backups). Nothing was changed." 4
  fi
  log "Dump age: $age day(s)"
}

check_disk() {
  local need free_work free_docker docker_root
  need=$(needed_mb "$DUMP_BYTES" "$MARGIN_MB")
  free_work=$(free_mb "$( [[ -d $WORK_DIR ]] && echo "$WORK_DIR" || echo /root )")
  docker_root=$(docker info -f '{{.DockerRootDir}}' 2>/dev/null || echo /var/lib/docker)
  free_docker=$(free_mb "$docker_root")
  log "Disk: need about $need MB free; free: $free_work MB for $WORK_DIR, $free_docker MB for $docker_root (MySQL data)"
  if (( free_work < need || free_docker < need )); then
    die "not enough free disk (need about $need MB). Free space first: $PROJECT_DIR/tools/staging/cleanup-disk.sh (see docs/runbook.md). Nothing was changed." 5
  fi
}

dry_run_report() {
  local n_spec n_login recent keep
  n_spec=$(sql_value "select count(*) from specimen")
  n_login=$(sql_value "select count(*) from login")
  log "Staging now: $n_spec specimens, $n_login logins"
  log "Test logins on staging now: $(sql_value "select coalesce(group_concat(name order by name separator ', '), 'none') from login where name like 'zztest\\_%'")"
  keep="none"
  [[ -f "$KEEP_LOGINS_FILE" ]] && keep=$({ grep -v '^[[:space:]]*\(#\|$\)' "$KEEP_LOGINS_FILE" || true; } | tr '\n' ' ')
  log "More logins to keep ($KEEP_LOGINS_FILE): ${keep:-none}"
  keep="antweb (no $UPLOAD_AS_FILE)"
  [[ -f "$UPLOAD_AS_FILE" ]] && keep=$({ grep -v '^[[:space:]]*\(#\|$\)' "$UPLOAD_AS_FILE" || true; } | tr '\n' ' ')
  log "Admin login(s) that get zztest_uploader in \"Upload as\": ${keep:-none}"
  recent=$(sql_value "select coalesce(group_concat(name order by name separator ', '), 'none') from login where last_login > now() - interval 14 day and name not like 'zztest\\_%'")
  log "Logins used on staging in the last 14 days (not zztest): $recent"
  log "  (the ANTWEB_TEST_USER login of the end-to-end tests is probably among them; add its name to $KEEP_LOGINS_FILE)"
  if [[ -s "$SAFETY" ]]; then log "A safety dump from an earlier run exists: $SAFETY (it would be replaced)"; fi
  log "Would: download $DUMP_NAME to $WORK_DIR, save the test logins, dump staging to $SAFETY, stop AntWeb,"
  log "       load the dump, $( ((SCRUB)) && echo 'scrub emails and passwords (scrub.sql)' || echo 'NOT scrub'), put the test logins back,"
  log "       add zztest_uploader to the admin's \"Upload as\" list, start AntWeb,"
  log "       then delete the downloaded dump$( ((KEEP_SAFETY)) && echo '' || echo ' and the safety dump')."
  log "Dry run: nothing was changed."
}

do_rollback() {
  [[ -s "$SAFETY" ]] || die "no safety dump at $SAFETY: nothing to roll back to (DigitalOcean's weekly droplet backup is the fallback)"
  check_dump "$SAFETY" || die "the safety dump is damaged"
  if (( DRY_RUN )); then log "Dry run: would stop AntWeb, load $SAFETY, start AntWeb"; return 0; fi
  log "Rollback: stopping AntWeb"
  docker stop "$APP_CONTAINER" >/dev/null
  APP_STOPPED=1
  log "Rollback: loading $SAFETY"
  load_dump "$SAFETY"
  log "Rollback: starting AntWeb"
  docker start "$APP_CONTAINER" >/dev/null
  APP_STOPPED=0
  wait_for_app || log "WARNING: AntWeb did not answer within 10 minutes; check docker logs $APP_CONTAINER"
  log "Rollback done: staging's database is as it was before the last refresh. $SAFETY is kept."
}

refresh() {
  local local_dump n_spec n_login last_upload unscrubbed missing t0 accounts_py="$PROJECT_DIR/tools/staging/create-test-accounts.py"
  t0=$SECONDS

  # Download (read-only on production's bucket) and check.
  if [[ -n "$DUMP_FILE" ]]; then
    local_dump=$DUMP_FILE
  else
    local_dump="$WORK_DIR/prod-$DUMP_NAME"
    rm -f "$WORK_DIR"/prod-*.sql.gz   # left over from a failed run
    log "Downloading $REMOTE/$DUMP_NAME"
    rclone copyto --retries 3 --low-level-retries 10 "$REMOTE/$DUMP_NAME" "$local_dump"
  fi
  chmod 600 "$local_dump"
  check_dump "$local_dump" || die "the production dump $DUMP_NAME is damaged or incomplete: production's backup needs checking. Nothing was changed."
  log "Dump checked: complete, one database (ant), ends with \"Dump completed\""

  # Keep what is staging-only.
  log "Saving the test logins (password hashes) to $SAVED"
  python3 "$accounts_py" --save-to "$SAVED" --keep-logins "$KEEP_LOGINS_FILE" 2>&1 | while IFS= read -r l; do log "  $l"; done
  [[ -s "$SAVED" ]] || die "could not save the test logins"
  sql_value "select table_name, column_name from information_schema.columns where table_schema = 'ant'" | LC_ALL=C sort > "$SCHEMA_BEFORE"

  log "Safety dump of staging's current database to $SAFETY"
  dump_staging_to "$SAFETY" || die "the safety dump failed. Nothing was changed."
  log "Safety dump done ($(( $(stat -c %s "$SAFETY") / 1048576 )) MB)"

  if [[ -n "$NOT_AFTER" ]] && (( $(hhmm_minutes "$(pacific_now)") >= $(hhmm_minutes "$NOT_AFTER") )); then
    die "it is past $NOT_AFTER Pacific: stopping before AntWeb is touched (the weekly reboot is at 04:00). Nothing was changed." 6
  fi

  # From here on, a failure loads the safety dump again.
  IN_RESTORE=1
  log "Stopping AntWeb ($APP_CONTAINER)"
  docker stop "$APP_CONTAINER" >/dev/null
  APP_STOPPED=1

  log "Loading $DUMP_NAME into $DATABASE (this takes a while)"
  local t1=$SECONDS
  load_dump "$local_dump"
  log "Loaded in $(( (SECONDS - t1) / 60 )) min $(( (SECONDS - t1) % 60 )) s"

  n_spec=$(sql_value "select count(*) from specimen")
  n_login=$(sql_value "select count(*) from login")
  last_upload=$(sql_value "select max(created) from upload")
  log "Loaded data: $n_spec specimens, $n_login logins, last upload $last_upload"
  (( n_spec > 0 && n_login > 0 )) || die "the loaded database has no specimens or no logins"

  if (( SCRUB )); then
    log "Scrubbing emails, passwords, sessions and reset links (tools/staging/scrub.sql)"
    mysql_ant < "$PROJECT_DIR/tools/staging/scrub.sql"
    unscrubbed=$(sql_value "select count(*) from login where email <> '' and email not like '%@example.invalid'")
    [[ "$unscrubbed" == 0 ]] || die "$unscrubbed email addresses were not scrubbed"
    log "Scrubbed: every email is now login<id>@example.invalid; no production password works on staging"
  else
    log "Not scrubbed (--no-scrub): production's emails and password hashes are on staging"
  fi

  log "Putting the test logins back with their saved passwords"
  python3 "$accounts_py" --restore-from "$SAVED" 2>&1 | while IFS= read -r l; do log "  $l"; done

  # The test uploader has a new login number after each refresh: put it back in the admin's
  # "Upload as" menu. Not worth a rollback if it fails.
  log "Adding the test uploader to the \"Upload as\" list of the admin login(s) ($UPLOAD_AS_FILE; no file: antweb)"
  python3 "$accounts_py" --add-upload-as "$UPLOAD_AS_FILE" 2>&1 | while IFS= read -r l; do log "  $l"; done \
    || log "WARNING: the \"Upload as\" list was not updated; add zztest_uploader by hand on viewLogin.do"

  sql_value "select table_name, column_name from information_schema.columns where table_schema = 'ant'" \
    | LC_ALL=C sort > "$WORK_DIR/schema-after.tsv"
  missing=$(LC_ALL=C comm -23 "$SCHEMA_BEFORE" "$WORK_DIR/schema-after.tsv" | tr '\t' '.' | tr '\n' ' ')
  if [[ -n "$missing" ]]; then
    log "WARNING: columns staging had that production's database has not (re-apply staging's db/upgrade files if a branch on staging needs them): $missing"
  fi

  log "Starting AntWeb"
  docker start "$APP_CONTAINER" >/dev/null
  APP_STOPPED=0
  IN_RESTORE=0
  if ! wait_for_app; then
    log "WARNING: AntWeb did not answer within 10 minutes. The database is refreshed; check docker logs $APP_CONTAINER."
    log "         To go back: $0 --rollback (the safety dump is kept)"
    KEEP_SAFETY=1
  fi

  if [[ -z "$DUMP_FILE" ]]; then rm -f "$local_dump"; fi
  if (( KEEP_SAFETY )); then
    log "Safety dump kept: $SAFETY (rollback: $0 --rollback)"
  else
    rm -f "$SAFETY"
    log "Safety dump deleted"
  fi
  log "Refresh OK: staging has production's data of $DUMP_NAME ($(( (SECONDS - t0) / 60 )) min in all)"
}

main() {
  parse_args "$@"
  [[ $(id -u) == 0 ]] || { echo "run as root" >&2; exit 2; }
  check_guard
  load_db_login

  exec 9> "$LOCK_FILE"
  flock -n 9 || die "another refresh is running (lock $LOCK_FILE)" 3

  if (( ! DRY_RUN )); then
    install -d -m 700 "$WORK_DIR"
    LOG_FILE="$WORK_DIR/refresh.log"
    # The downloaded production dump never stays on staging (it can be fetched again from Spaces).
    trap 'rm -f "$WORK_DIR"/prod-*.sql.gz' EXIT
  fi
  log "Start$( ((DRY_RUN)) && echo ' (dry run)')$( ((ROLLBACK)) && echo ' (rollback)'): $*"
  trap 'on_error $LINENO' ERR

  if (( ROLLBACK )); then do_rollback; exit 0; fi

  check_mail
  pick_dump
  check_age
  check_disk
  if (( DRY_RUN )); then dry_run_report; exit 0; fi
  refresh
}

# Sourced by the unit test (functions only); run as a script it refreshes.
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
  exit $?
fi
