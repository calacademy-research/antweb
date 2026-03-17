#!/usr/bin/env bash
# Having sensitive error checking making the script fail.
#set -euxo pipefail

# Let's assume antweb is in home directory of the ssh user
cd $HOME/antweb

# Get the latest source code
git checkout master
git pull --rebase origin master

# Restart docker compose services
# -T disables pseudo-TTY allocation (required when running non-interactively via SSH/CI)
docker compose exec -T antweb ant deploy || docker-compose exec -T antweb ant deploy
docker compose restart antweb || docker-compose restart antweb
