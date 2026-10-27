#!/usr/bin/env bash
set -euo pipefail

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  else
    docker-compose "$@"
  fi
}

DEPLOY_BRANCH="${DEPLOY_BRANCH:-master}"

# Let's assume antweb is in home directory of the ssh user
cd "$HOME/antweb"

restart_caddy_if_changed() {
  # The Caddyfiles are single-file bind mounts too: restart Caddy (a few seconds)
  # when the running copy differs from the repo's, so config changes take effect.
  live=$(mktemp)
  caddy_changed=0
  compose exec -T caddy cat /etc/caddy/Caddyfile.common > "$live" < /dev/null 2>/dev/null || true
  cmp -s "$live" docker/caddy/Caddyfile.common || caddy_changed=1
  compose exec -T caddy cat /etc/caddy/Caddyfile > "$live" < /dev/null 2>/dev/null || true
  cmp -s "$live" docker/caddy/Caddyfile || cmp -s "$live" docker/caddy/Caddyfile.staging || caddy_changed=1
  rm -f "$live"
  if [ "$caddy_changed" = 1 ]; then
    echo "Caddyfile changed; restarting caddy..."
    compose restart caddy
  fi
}

wait_until_idle() {
  # Never restart AntWeb in the middle of an upload: on 2026-10-26 a deploy restarted it while a
  # curator was uploading images, and her upload stopped half way. Ask the running AntWeb
  # (util.do?action=isBusy, on the local port: the public address goes through Cloudflare) and wait
  # while it answers "busy: ...", asking every 30 seconds for up to 30 minutes. After that, go ahead
  # and say so loudly. No answer, or not "idle"/"busy:" (AntWeb down, still starting, or older code
  # without isBusy): go ahead at once. Call it only right before a step that restarts AntWeb.
  # The 30 minutes are for the whole deploy (busy_waited), even when it asks twice.
  # tests/unit/test_deploy_paths.py checks this and runs it with a stand-in curl.
  local url="${ANTWEB_URL:-http://127.0.0.1:8081}"
  local poll="${BUSY_POLL_SECONDS:-30}" max="${BUSY_MAX_SECONDS:-1800}" waited=0 answer
  url="${url%/}/util.do?action=isBusy"
  while true; do
    answer=$(curl -s --max-time 10 "$url" < /dev/null 2>/dev/null | head -c 200 | tr -d '\r\n' || true)
    case "$answer" in
      idle)
        [ "$waited" -gt 0 ] && echo "AntWeb is idle now (waited ${waited} seconds); going ahead."
        return 0 ;;
      busy:*) ;;
      *)
        echo "AntWeb did not say whether it is busy (down, starting, or older code); not waiting."
        return 0 ;;
    esac
    if [ "$busy_waited" -ge "$max" ]; then
      echo "::warning::AntWeb still $answer after waiting ${busy_waited} seconds; restarting anyway."
      echo "WARNING: AntWeb still $answer after waiting ${busy_waited} seconds; restarting anyway. Check the upload afterwards." >&2
      return 0
    fi
    echo "AntWeb is $answer; waiting before the restart (${busy_waited} of ${max} seconds so far)..."
    sleep "$poll"
    waited=$((waited + poll))
    busy_waited=$((busy_waited + poll))
  done
}
busy_waited=0

migrate_web_log() {
  # web/log (compute.log, admin.log, taxonworksNightly.log, ...) used to live in the
  # container and was lost when the container was replaced. It is now the host folder
  # <project>/logs/web (docker-compose.yml). The first time a container without that
  # mount is replaced by one with it, stop the old container and copy its web/log to
  # the host folder, so nothing is lost. Afterwards the old container already has the
  # mount and this does nothing. If anything fails, the old container is started again
  # and the deploy stops (the site keeps running the old code).
  local dc="$1" wd dest need avail
  if docker inspect antweb_antweb_1 --format '{{range .Mounts}}{{println .Destination}}{{end}}' 2>/dev/null \
       | grep -qx /usr/local/antweb/web/log; then
    return 0
  fi
  wd=$(docker inspect antweb_antweb_1 --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}')
  if ! grep -qE ':/usr/local/antweb/web/log/?$' "$wd/docker-compose.yml"; then
    return 0   # the new code has no web/log mount either
  fi
  dest="$wd/logs/web"
  # Host folder already in use (e.g. staging ran an older branch for a while): keep
  # what is there and put the container's copy in a dated subfolder next to it.
  if [ -n "$(ls -A "$dest" 2>/dev/null)" ]; then
    dest="$dest/from-container-$(date +%Y%m%d-%H%M%S)"
  fi
  # Size of web/log (du does not follow the links to the bucket); 0 if the container is not running.
  need=$(docker exec antweb_antweb_1 du -sk /usr/local/antweb/web/log < /dev/null 2>/dev/null | cut -f1 || true)
  need=${need:-0}
  mkdir -p "$wd/logs"
  avail=$(df -Pk "$wd/logs" | awk 'NR==2 {print $4}')
  echo "Moving web/log out of the container: ${need} KB to copy, ${avail} KB free on the host."
  if [ "$avail" -lt $((need + 1048576)) ]; then
    echo "ERROR: not enough disk space to copy web/log (need ${need} KB + 1 GB spare); deploy stopped, nothing changed." >&2
    exit 1
  fi
  $dc stop antweb < /dev/null
  mkdir -p "$dest"
  if ! docker cp antweb_antweb_1:/usr/local/antweb/web/log/. "$dest/" < /dev/null; then
    echo "ERROR: copying web/log out of the container failed; starting the old container again. Deploy stopped." >&2
    docker start antweb_antweb_1 < /dev/null
    exit 1
  fi
  chown -Rh 2001:2000 "$dest"
  echo "web/log copied to $dest ($(du -sk "$dest" | cut -f1) KB, $(find "$dest" -type f | wc -l) files)."
}

# Get the latest source code
PREV=$(git rev-parse HEAD)
git fetch origin "$DEPLOY_BRANCH"
git checkout "$DEPLOY_BRANCH"
git pull --rebase origin "$DEPLOY_BRANCH"

# Record the deployed commit for the page header (version · short hash, linked to
# GitHub). etc/ is mounted read-only in the container at /antweb/deploy/etc/ and the
# file is gitignored; GitCommit.java re-reads it when it changes, so no restart is
# needed. A failure here never stops the deploy.
git rev-parse HEAD > etc/git-commit.tmp && mv -f etc/git-commit.tmp etc/git-commit || echo "warning: could not write etc/git-commit"

# Nothing the site runs changed (only CI, tests, docs, release notes or tools/, the host
# scripts the site never runs): no restart. tests/unit/test_deploy_paths.py checks this list.
changed=$(git diff --name-only "$PREV" HEAD)
if [ -n "$changed" ] && ! printf '%s\n' "$changed" | grep -qvE '^(\.github/|tests/|tools/|doc/|docs/|release-notes/|[^/]*\.md$)'; then
  echo "Only CI, tests, docs or tools changed since $PREV; antweb not restarted."
  exit 0
fi

# If the antweb image itself changed (Dockerfile, Tomcat config, base image), ant
# deploy into the running container is not enough: rebuild the image (which runs
# ant deploy) and replace the container. The old container keeps serving during
# the build. Uses the same compose project and files the running container was
# started with, so prod and staging each keep their own settings.
if ! git diff --quiet "$PREV" HEAD -- docker/antweb docker-compose.yml docker-compose.stage.yml; then
  echo "docker/antweb or a compose file changed since $PREV; rebuilding and recreating the antweb container..."
  files=$(docker inspect antweb_antweb_1 --format '{{index .Config.Labels "com.docker.compose.project.config_files"}}')
  project=$(docker inspect antweb_antweb_1 --format '{{index .Config.Labels "com.docker.compose.project"}}')
  dc="docker compose -p $project $(echo "$files" | tr ',' '\n' | sed 's/^/-f /')"
  $dc build antweb < /dev/null
  wait_until_idle   # the old container keeps serving until here
  migrate_web_log "$dc"
  $dc up -d --build api antweb < /dev/null
  restart_caddy_if_changed
  echo "Rebuilt; antweb container started at:"
  docker inspect -f 'StartedAt={{.State.StartedAt}}' antweb_antweb_1
  exit 0
fi

# Restart docker compose services
# -T disables pseudo-TTY allocation (required when running non-interactively via SSH/CI)
# Remove deployed jars that are no longer in the repo's WEB-INF/lib (e.g. after a
# library upgrade). ant deploy only adds files, and build.xml is a single-file bind
# mount the running container may not see updated, so do it here. Two copies of a
# library (e.g. log4j-web) stop Tomcat from starting.
# From here on the running AntWeb is changed (jars removed, ant deploy reloads the web app, then a
# restart): wait until no upload is running.
wait_until_idle
echo "Removing stale jars..."
compose exec -T antweb sh -c 'for d in /usr/local/antweb/WEB-INF/lib /antweb/deploy/build/WEB-INF/lib; do [ -d "$d" ] || continue; for j in "$d"/*.jar; do [ -e "$j" ] || continue; [ -e "/antweb/deploy/WEB-INF/lib/$(basename "$j")" ] || { echo "removing $j"; rm -f "$j"; }; done; done' < /dev/null

# build.xml is a single-file bind mount: after git changes it, the running container
# still sees the old copy until it restarts. Restart first in that case so ant uses
# the new build file.
if ! compose exec -T antweb cat /antweb/deploy/build.xml < /dev/null | cmp -s - build.xml; then
  echo "build.xml changed; restarting antweb so the container sees it..."
  compose restart antweb
  sleep 5
fi

# The API's code is copied into its image, so rebuild it. Docker's build cache makes
# this quick when api/ has not changed, and compose only recreates the container
# when the image changed.
echo "Rebuilding the API image if api/ changed..."
compose up -d --build api < /dev/null

# Tomcat runs as the user tomcat (docker/antweb/startup.sh), so build and deploy
# as tomcat too: first give tomcat anything a root run left behind, then run ant
# as tomcat. An older image without antweb-fix-perms still deploys as root.
echo "Running ant deploy..."
if compose exec -T antweb test -x /usr/local/bin/antweb-fix-perms < /dev/null; then
  compose exec -T antweb /usr/local/bin/antweb-fix-perms < /dev/null
  compose exec -T -u tomcat antweb ant deploy < /dev/null
else
  compose exec -T antweb ant deploy < /dev/null
fi

# ant deploy takes a minute or two: ask again in case an upload started meanwhile.
wait_until_idle
echo "Restarting antweb container..."
compose restart antweb

restart_caddy_if_changed

echo "Container status after restart:"
compose ps antweb

echo "Antweb container started at:"
docker inspect -f 'StartedAt={{.State.StartedAt}} RestartCount={{.RestartCount}}' antweb_antweb_1
