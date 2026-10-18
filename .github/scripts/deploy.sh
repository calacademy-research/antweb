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

# Nothing the site runs changed (only CI, tests, docs or release notes): no restart.
changed=$(git diff --name-only "$PREV" HEAD)
if [ -n "$changed" ] && ! printf '%s\n' "$changed" | grep -qvE '^(\.github/|tests/|doc/|docs/|release-notes/|[^/]*\.md$)'; then
  echo "Only CI, tests or docs changed since $PREV; antweb not restarted."
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

echo "Restarting antweb container..."
compose restart antweb

restart_caddy_if_changed

echo "Container status after restart:"
compose ps antweb

echo "Antweb container started at:"
docker inspect -f 'StartedAt={{.State.StartedAt}} RestartCount={{.RestartCount}}' antweb_antweb_1
