#!/usr/bin/env bash
set -euo pipefail

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  else
    docker-compose "$@"
  fi
}

# Let's assume antweb is in home directory of the ssh user
cd "$HOME/antweb"

# Get the latest source code
git checkout master
git pull --rebase origin master

# Restart docker compose services
# -T disables pseudo-TTY allocation (required when running non-interactively via SSH/CI)
compose exec -T antweb ant deploy
compose restart antweb
