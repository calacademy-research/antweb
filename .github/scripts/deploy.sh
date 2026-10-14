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

# Get the latest source code
git fetch origin "$DEPLOY_BRANCH"
git checkout "$DEPLOY_BRANCH"
git pull --rebase origin "$DEPLOY_BRANCH"

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

echo "Running ant deploy..."
compose exec -T antweb ant deploy < /dev/null

echo "Restarting antweb container..."
compose restart antweb

echo "Container status after restart:"
compose ps antweb

echo "Antweb container started at:"
docker inspect -f 'StartedAt={{.State.StartedAt}} RestartCount={{.RestartCount}}' antweb_antweb_1
