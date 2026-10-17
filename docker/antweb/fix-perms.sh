#!/bin/sh
# Installed in the image as /usr/local/bin/antweb-fix-perms (root-owned).
# Gives the user tomcat ownership of every local path Tomcat writes, so Tomcat
# can run without root. Runs as root: from startup.sh at every container start
# and from .github/scripts/deploy.sh before `ant deploy`.
#
# Only local disks are touched (the image, the antweb_genInc volume, and the
# host's ./logs and ./logbak). The DigitalOcean Spaces mount /mnt/antweb is never
# chowned here (that would rewrite millions of objects); its ownership comes from
# the host's rclone mount options (doc/tomcat-nonroot-runbook.md). find -xdev keeps
# each walk on one filesystem, so the symlinks into /mnt and the read-only code
# mounts are not followed.

U=tomcat
G=antweb
status=0

for d in \
    /usr/local/antweb \
    /usr/local/antweb/web/genInc \
    /usr/local/antweb/log \
    /usr/local/tomcat \
    /usr/local/tomcat/logs \
    /antweb/deploy/build \
    /antweb/deploy/build-test \
    /antweb/deploy/dist
do
  [ -d "$d" ] || continue
  if ! find "$d" -xdev ! -user "$U" -exec chown -h "$U:$G" {} +; then
    echo "antweb-fix-perms: could not chown everything under $d" >&2
    status=1
  fi
done

# ant deploy (run as tomcat) creates build/ and build-test/ here; the code
# folders inside are read-only mounts and stay as they are.
chown "$U:$G" /antweb/deploy || status=1

exit $status
