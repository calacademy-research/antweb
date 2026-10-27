#!/usr/bin/env bash
# What uses disk space on STAGING, and (with --apply) free the items on a fixed safe list.
#
# Runs ON THE STAGING DROPLET as root (ssh root@165.232.136.170):
#     /root/antweb-next/tools/staging/cleanup-disk.sh            # report only (the default)
#     /root/antweb-next/tools/staging/cleanup-disk.sh --apply    # delete the safe list, then report again
#
# The safe list (nothing else is ever deleted):
#   1. Docker build cache                         docker builder prune -f
#   2. Dangling Docker images (untagged, unused)  docker image prune -f
#   3. Old database dump copies in /root          /root/ant-currentDump.sql.gz, /root/*.sql.gz older than 7 days
#   4. Old manual restore files                   /root/restore/*.sql.gz older than 7 days
#   5. The pre-migration project copy             /root/antweb-old-2026-10-13 (only if no container uses it)
#   6. Rotated logs older than 30 days            *.gz under /root/antweb-next/logs and /root/antweb-next/logbak
#   7. Watchdog thread dumps older than 14 days   /root/antweb-watchdog-dumps/
#   8. The systemd journal above 100 MB           journalctl --vacuum-size=100M
#   9. Downloaded package files                   apt-get clean
#  10. Backups of replaced images, older than 14 days   /mnt/antweb/images/backup/ (the image-upload tests
#      replace the test specimen's images on every run; each replaced file is kept there: 1 GB on 2026-10-24)
#  11. Upload backups older than 30 days        /mnt/antweb/web/bak/ (3.7 GB on 2026-10-24)
#      10 and 11 only while /mnt/antweb is a plain folder on staging's disk, never a mounted bucket.
# Never touched: Docker volumes (the database), running containers' images, the rest of /mnt/antweb,
# /mnt/backup, etc/ settings, the refresh's own folder /root/staging-refresh.
# Refuses to run anywhere but staging (same checks as refresh-from-prod.sh).
# Procedure: docs/runbook.md, "Staging refresh".

set -Euo pipefail

# Same staging checks as the refresh (host name, /etc/antweb-role, project folder, container).
# shellcheck source=refresh-from-prod.sh
source "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/refresh-from-prod.sh"
set +e   # the sourced file sets -e; here a failed du or find must not stop the report

OLD_PROJECT=/root/antweb-old-2026-10-13
APPLY=0

# on_safe_list PATH: the only file paths --apply may delete (unit-tested).
on_safe_list() {
  [[ "$1" != *..* ]] || return 1
  [[ "$1" =~ ^/root/[^/]+\.sql\.gz$ || "$1" =~ ^/root/restore/[^/]+\.sql\.gz$ \
     || "$1" =~ ^/root/antweb-next/(logs|logbak)/.+\.gz$ || "$1" =~ ^/root/antweb-watchdog-dumps/[^/]+$ \
     || "$1" =~ ^/mnt/antweb/images/backup/[^/]+$ || "$1" =~ ^/mnt/antweb/web/bak/.+$ ]]
}

size_of() { (( $# )) || { echo "    (none)"; return 0; }; du -sh "$@" 2>/dev/null | sort -h | sed 's/^/    /'; }

old_dumps() { find /root -maxdepth 1 -type f \( -name 'ant-currentDump.sql.gz' -o \( -name '*.sql.gz' -mtime +7 \) \) 2>/dev/null; }
old_restores() { [[ -d /root/restore ]] && find /root/restore -maxdepth 1 -type f -name '*.sql.gz' -mtime +7 2>/dev/null; true; }
old_logs() { find "$PROJECT_DIR/logs" "$PROJECT_DIR/logbak" -type f -name '*.gz' -mtime +30 2>/dev/null; true; }
old_thread_dumps() { [[ -d /root/antweb-watchdog-dumps ]] && find /root/antweb-watchdog-dumps -type f -mtime +14 2>/dev/null; true; }

# /mnt/antweb is a plain folder on staging's own disk (production mounts a Spaces bucket there): only then.
antweb_is_local() { [[ -d /mnt/antweb ]] && ! findmnt -n /mnt/antweb >/dev/null 2>&1; }
old_image_backups() { antweb_is_local && [[ -d /mnt/antweb/images/backup ]] && find /mnt/antweb/images/backup -xdev -maxdepth 1 -type f -mtime +14 2>/dev/null; true; }
old_upload_backups() { antweb_is_local && [[ -d /mnt/antweb/web/bak ]] && find /mnt/antweb/web/bak -xdev -type f -mtime +30 2>/dev/null; true; }

old_project_in_use() {
  # A container mount, a running process or the /root/antweb link that points into it.
  docker ps -aq | xargs -r docker inspect -f '{{range .Mounts}}{{.Source}} {{end}}' 2>/dev/null | grep -q "$OLD_PROJECT" && return 0
  [[ "$(readlink -f /root/antweb)" == "$OLD_PROJECT"* ]] && return 0
  local p
  for p in /proc/[0-9]*/cwd; do [[ "$(readlink "$p" 2>/dev/null)" == "$OLD_PROJECT"* ]] && return 0; done
  return 1
}

report() {
  echo "== Disk"
  df -h / | sed 's/^/    /'
  echo "== Largest folders on / (top 15)"
  du -xsh /root/* /root/.[!.]* /var/* /home/* /opt/* /usr/* /tmp 2>/dev/null | sort -h | tail -15 | sed 's/^/    /'
  echo "== Docker (images, containers, volumes, build cache)"
  docker system df 2>/dev/null | sed 's/^/    /'
  echo "== Journal"
  echo "    $(journalctl --disk-usage 2>/dev/null)"
  echo "== On the safe list"
  local -a dumps restores logs
  mapfile -t dumps < <(old_dumps); mapfile -t restores < <(old_restores); mapfile -t logs < <(old_logs)
  echo "  3. old dump copies in /root:";            size_of "${dumps[@]}"
  echo "  4. old files in /root/restore:";          size_of "${restores[@]}"
  if [[ -d "$OLD_PROJECT" ]]; then
    if old_project_in_use; then echo "  5. $OLD_PROJECT: IN USE, will not be deleted"; else echo "  5. $OLD_PROJECT (not in use):"; fi
    size_of "$OLD_PROJECT"
  else
    echo "  5. $OLD_PROJECT: not present"
  fi
  echo "  6. rotated logs older than 30 days: ${#logs[@]} files"
  if (( ${#logs[@]} )); then du -ch "${logs[@]}" 2>/dev/null | tail -1 | sed 's/^/    /'; fi
  echo "  7. watchdog thread dumps older than 14 days: $(old_thread_dumps | wc -l) files"
  echo "  9. apt package cache:"; size_of /var/cache/apt/archives
  echo " 10. image backups older than 14 days: $(old_image_backups | wc -l) files"
  echo " 11. upload backups older than 30 days: $(old_upload_backups | wc -l) files"
  echo "== Not on the safe list (for Brian to decide)"
  if [[ -d /mnt/backup ]] && ! findmnt -n /mnt/backup >/dev/null 2>&1; then
    echo "  /mnt/backup is a plain folder on this disk (not Spaces); old copies from 2025:"; size_of /mnt/backup
  fi
  if [[ -d /root/staging-refresh ]]; then echo "  /root/staging-refresh (the weekly refresh's folder):"; size_of /root/staging-refresh; fi
  echo "  Docker images not used by any container (removing them slows the next rebuild):"
  docker images --filter dangling=false --format '{{.Repository}}:{{.Tag}} {{.Size}}' 2>/dev/null | while read -r img size; do
    if [[ -z "$(docker ps -aq --filter "ancestor=$img")" ]]; then echo "    $img $size"; fi
  done
}

apply() {
  local f
  local -a files
  logger -t "$TAG" "cleanup --apply started" 2>/dev/null || true
  echo "== Deleting the safe list"
  echo "  1. build cache:";  docker builder prune -f 2>&1 | tail -1 | sed 's/^/    /'
  echo "  2. dangling images:"; docker image prune -f 2>&1 | tail -1 | sed 's/^/    /'
  mapfile -t files < <(old_dumps; old_restores; old_logs; old_thread_dumps; old_image_backups; old_upload_backups)
  for f in "${files[@]}"; do
    [[ -n "$f" ]] || continue
    if on_safe_list "$f"; then
      rm -f -- "$f" && echo "    deleted $f"
    else
      echo "    skipped $f (not on the safe list)"
    fi
  done
  if [[ -d "$OLD_PROJECT" && ! -L "$OLD_PROJECT" ]]; then
    if old_project_in_use; then
      echo "  5. $OLD_PROJECT is in use: kept"
    else
      rm -rf --one-file-system -- "$OLD_PROJECT" && echo "  5. deleted $OLD_PROJECT"
    fi
  fi
  echo "  8. journal:"; journalctl --vacuum-size=100M 2>&1 | tail -1 | sed 's/^/    /'
  echo "  9. apt cache:"; apt-get clean && echo "    cleaned"
  logger -t "$TAG" "cleanup --apply done: $(df -h / | awk 'NR == 2 { print $5 " used, " $4 " free" }')" 2>/dev/null || true
}

cleanup_main() {
  case "${1:-}" in
    --apply) APPLY=1 ;;
    --dry-run|"") APPLY=0 ;;
    -h|--help) sed -n '2,27p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $1 (use --dry-run or --apply)" >&2; exit 64 ;;
  esac
  [[ $(id -u) == 0 ]] || { echo "run as root" >&2; exit 2; }
  TAG=staging-cleanup
  local role="" project_ok=0 container_dir problems
  [[ -r "$ROLE_FILE" ]] && role=$(tr -d '[:space:]' < "$ROLE_FILE")
  [[ -f "$PROJECT_DIR/etc/db.properties" ]] && project_ok=1
  container_dir=$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}' "$APP_CONTAINER" 2>/dev/null || true)
  # The nightly window does not matter for cleaning disk: pass a time outside it.
  problems=$(guard_problems "$(hostname)" "$role" "$project_ok" "$container_dir" "12:00" "$(hostname -I 2>/dev/null || true)")
  if [[ -n "$problems" ]]; then
    echo "Refused:"; sed 's/^/  - /' <<< "$problems"; exit 2
  fi

  if (( APPLY )); then
    apply
    echo
  fi
  report
  if (( ! APPLY )); then
    echo
    echo "Report only: nothing was deleted. To delete the safe list: $0 --apply"
  fi
}

# Sourced by the unit test (functions only); run as a script it reports or cleans.
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  cleanup_main "$@"
fi
