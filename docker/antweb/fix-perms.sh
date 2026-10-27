#!/bin/sh
# Installed in the image as /usr/local/bin/antweb-fix-perms (root-owned).
# Gives the user tomcat ownership of every local path Tomcat writes, so Tomcat
# can run without root. Runs as root: from startup.sh at every container start
# and from .github/scripts/deploy.sh before `ant deploy`.
#
# Only local disks are touched (the image, the antweb_genInc volume, and the
# host's ./logs, ./logs/web and ./logbak). The DigitalOcean Spaces mount /mnt/antweb is never
# chowned here (that would rewrite millions of objects); its ownership comes from
# the host's rclone mount options (doc/tomcat-nonroot-runbook.md). find -xdev keeps
# each walk on one filesystem, so the symlinks into /mnt and the read-only code
# mounts are not followed.

U=tomcat
G=antweb
status=0

# web/log is a host folder (docker-compose.yml: ./logs/web), so the links the image
# made in it are hidden. Make the upload report links here: reports must go to the
# bucket (/mnt/antweb/web/log/...), where the public report URLs find them. A real
# folder in their place is reported, never moved or deleted.
L=/usr/local/antweb/web/log
if [ -d "$L" ]; then
  for r in specimen worldants detail upload; do
    want=/mnt/antweb/web/log/$r
    if [ -L "$L/$r" ]; then
      [ "$(readlink "$L/$r")" = "$want" ] || { echo "antweb-fix-perms: WARNING $L/$r links to $(readlink "$L/$r"), not $want" >&2; status=1; }
    elif [ -e "$L/$r" ]; then
      echo "antweb-fix-perms: WARNING $L/$r is a real folder, not a link to $want: reports written there are not public" >&2
      status=1
    else
      ln -s "$want" "$L/$r" || status=1
    fi
  done
  chmod 0775 "$L" || status=1
fi

for d in \
    /usr/local/antweb \
    /usr/local/antweb/web/log \
    /usr/local/antweb/web/genInc \
    /usr/local/antweb/log \
    /usr/local/tomcat \
    /usr/local/tomcat/logs \
    /antweb/deploy/build \
    /antweb/deploy/build-test \
    /antweb/deploy/dist
do
  [ -d "$d" ] || continue
  # A regular file with more than one hard link is skipped: tomcat could link a root file (outside these trees) here.
  if ! find "$d" -xdev ! -user "$U" \( ! -type f -o -links 1 \) -exec chown -h "$U:$G" {} +; then
    echo "antweb-fix-perms: could not chown everything under $d" >&2
    status=1
  fi
done

# ant deploy (run as tomcat) creates build/ and build-test/ here; the code
# folders inside are read-only mounts and stay as they are.
chown "$U:$G" /antweb/deploy || status=1

exit $status
