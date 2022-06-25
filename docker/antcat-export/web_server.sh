#!/usr/bin/env bash

# hosts the antcat.antweb.txt file in the docker network for development.
# accessible as http://antcat-export:9090/antcat.antweb.txt from inside antweb's docker containers and from the host machine

# place the antcat.antweb.txt file inside the data directory adjacent to this script (docker/antcat-export/data/)

# gets the directory the script is in regardless of where the script is called from -- `./docker/antcat-export/web_server.sh` works
SCRIPT_PATH=$(dirname "$(realpath -s "$0")")

docker run -it --rm --name web --hostname antcat-export \
  --network antweb_default \
  -p 9090:9090 \
  -v "$SCRIPT_PATH"/data:/srv \
  caddy caddy file-server --browse --listen :9090
