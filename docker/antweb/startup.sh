#!/bin/sh
# Container entrypoint. Runs as root only long enough to start cron (whose jobs
# stay root: admin.sh, the monthly dump, the weekly taxonSetBackup) and to give
# tomcat the paths it writes; then replaces itself with Tomcat running as the
# unprivileged user tomcat. The switch needs CAP_SETUID and CAP_SETGID, which
# docker-compose.yml keeps; no-new-privileges does not block it (setpriv is not
# a setuid program, it is root dropping its own privileges).

service cron start

/usr/local/bin/antweb-fix-perms || echo "startup.sh: antweb-fix-perms reported problems (see above)" >&2

# Report, without stopping, anything tomcat cannot read or write. Messages go
# to `docker logs antweb_antweb_1`.
as_tomcat() {
  setpriv --reuid=tomcat --regid=antweb --init-groups "$@"
}
for f in db mail editor turnstile taxonworks scheduler name-update; do
  p=/antweb/deploy/etc/$f.properties
  # shellcheck disable=SC2016 # $1 expands in the inner sh
  if [ -e "$p" ] && ! as_tomcat sh -c 'test -r "$1"' _ "$p"; then
    echo "startup.sh: WARNING tomcat cannot read $p (host: chgrp 2000 + chmod 640, see doc/tomcat-nonroot-runbook.md)" >&2
  fi
done
for d in /mnt/antweb/images /mnt/antweb/web/upload /mnt/antweb/web/workingdir \
         /mnt/antweb/web/curator /mnt/antweb/web/speciesList /mnt/antweb/web/log/upload; do
  # shellcheck disable=SC2016 # $1 expands in the inner sh
  if [ -d "$d" ] && ! as_tomcat sh -c 'test -w "$1"' _ "$d"; then
    echo "startup.sh: WARNING tomcat cannot write $d (host rclone mount options, see doc/tomcat-nonroot-runbook.md)" >&2
  fi
done

cd /usr/local/tomcat || exit 1

export HOME=/usr/local/tomcat USER=tomcat LOGNAME=tomcat
exec setpriv --reuid=tomcat --regid=antweb --init-groups /usr/local/tomcat/bin/catalina.sh run
