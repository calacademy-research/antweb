# Runbook: Tomcat as the user tomcat (phase 2)

Branch `sec/tomcat-nonroot`. Tomcat runs as `tomcat` (uid 2001, primary group
`antweb` gid 2000) inside `antweb_antweb_1`; cron and its jobs stay root.
`docker/antweb/startup.sh` starts cron, runs `antweb-fix-perms` (gives tomcat the
local paths it writes), then `exec setpriv --reuid=tomcat --regid=antweb
--init-groups catalina.sh run`.

Two things cannot be done from inside the container and must be done on the
host first, on staging, then prod:

1. `/mnt/antweb` (DigitalOcean Spaces via rclone) must let uid 2001 write.
   rclone ignores chown/chmod; ownership comes only from its mount options.
2. The server-only secrets in `<project>/etc/*.properties` (root 600) must be
   readable by gid 2000.

Both changes also work with today's root Tomcat, so they go in before the code.
`./logs`, `./logbak` and the `antweb_genInc` volume need nothing on the host: the
container chowns them at start.

Order: staging (A to E), run e2e and the checks, then prod (A to E).
Staging: `ssh root@165.232.136.170`. Prod: `ssh root@165.232.137.86`.
Every block is run as root on the host; nothing prints a secret.

## A. Read-only checks (paste the output back before going on)

Find the compose project folder of the running container (prod `~/antweb`, staging `/root/antweb-next`):
```
docker inspect antweb_antweb_1 --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}'
```

How the buckets are mounted (look for `allow_other`; FUSE's `user_id=0` is the mounting user, not the file owner):
```
mount | grep -E 's3fs|rclone|antweb|backup'; findmnt -T /mnt/antweb -o TARGET,SOURCE,FSTYPE,OPTIONS
```

Which service mounts /mnt/antweb and with which options:
```
systemctl list-units --type=service --all | grep -iE 'rclone|s3fs'; ps -o args= -C rclone -C s3fs
```

Ownership and modes (numeric) of the folders Tomcat writes:
```
ls -ldn /mnt/antweb /mnt/antweb/images /mnt/antweb/web /mnt/antweb/web/upload /mnt/antweb/web/workingdir /mnt/antweb/web/curator /mnt/antweb/web/speciesList /mnt/antweb/web/log /mnt/antweb/web/log/upload /mnt/antweb/web/bak /mnt/backup
```

Secrets: owner and mode only, never contents:
```
D=$(docker inspect antweb_antweb_1 --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}'); ls -ldn "$D/etc"; ls -ln "$D"/etc/*.properties
```

Make sure uid 2001 and gid 2000 are not real accounts on the host (expect no output):
```
getent passwd 2001; getent group 2000
```

Which other containers use /mnt/antweb (expect only antweb_antweb_1):
```
docker ps -q | xargs docker inspect -f '{{.Name}} {{range .Mounts}}{{.Source}} {{end}}' | grep /mnt/antweb
```

The host watchdog and reboot checker: confirm they only `docker restart` (a `jstack`, `jcmd` or `kill -3` inside the container would now need `docker exec -u tomcat`):
```
grep -rhE 'watchdog|reboot_checker' /etc/cron.d /var/spool/cron/crontabs 2>/dev/null; for f in $(grep -rhoE '/[^ ]*(watchdog|reboot_checker)[^ ]*\.sh' /etc/cron.d /var/spool/cron/crontabs 2>/dev/null | sort -u); do echo "== $f"; grep -nE 'docker|jstack|jcmd|kill|ps ' "$f"; done
```

Only after the new image is deployed (step D), the user ids inside the container:
```
docker exec antweb_antweb_1 id tomcat
```
Expected: `uid=2001(tomcat) gid=2000(antweb) groups=2000(antweb),2001(tomcat)`.

## B. Bucket mount: let uid 2001 write /mnt/antweb

Skip B if step A already shows `--allow-other --uid 2001 --gid 2000` in the rclone
arguments and `2001 2000` in `ls -ldn /mnt/antweb/web/upload`.
The commands assume the rclone systemd unit described in `doc/deployment.md`
(service `rclone`, `rclone mount digitalocean:/antweb /mnt/antweb ...`). If step A
shows s3fs or a different unit, stop and send the output; the s3fs equivalent is
`-o allow_other,uid=2001,gid=2000,umask=0002,mp_umask=0002`.
`/mnt/backup` is left alone: Tomcat never uses it, and keeping it root-only means
Tomcat cannot touch the database dumps.

Back up the unit file and add the options to the `rclone mount` line (expect `1` from grep):
```
F=$(systemctl show -p FragmentPath --value rclone); echo "$F"; grep -c 'rclone mount [^ ]* /mnt/antweb' "$F"; cp -a "$F" "$F.bak-nonroot"; sed -i 's|\(rclone mount [^ ]* /mnt/antweb\)|\1 --allow-other --uid 2001 --gid 2000 --umask 0002|' "$F"; systemctl cat rclone | grep -A3 'ExecStart='
```

Remount. This stops the site for a few minutes (plus 2 to 4 minutes of cache loading), so do it when no upload is running; rclone's full VFS cache resumes any unfinished uploads after the restart:
```
docker stop antweb_antweb_1 && systemctl daemon-reload && systemctl restart rclone && sleep 5 && findmnt -T /mnt/antweb -o TARGET,OPTIONS && ls -ldn /mnt/antweb/web/upload /mnt/antweb/images && docker start antweb_antweb_1
```
Expected: options include `allow_other`, folders show `2001 2000` and `drwxrwxr-x`.
The container must be stopped and started (not just left running): it keeps
the old mount until it is restarted.

## C. Secrets: group read for gid 2000

Give the files Tomcat reads group 2000 and mode 640 (api-db.properties stays root 600; only the API container reads it):
```
D=$(docker inspect antweb_antweb_1 --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}'); for f in db mail editor turnstile taxonworks flickr; do p="$D/etc/$f.properties"; [ -f "$p" ] && chgrp 2000 "$p" && chmod 640 "$p"; done; ls -ln "$D"/etc/*.properties
```

Only if step A showed `$D/etc` as `drwx------` (Tomcat could not open the folder):
```
D=$(docker inspect antweb_antweb_1 --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}'); chgrp 2000 "$D/etc" && chmod 750 "$D/etc" && ls -ldn "$D/etc"
```
No restart is needed; the root Tomcat still reads them.

## D. Deploy the code

Staging: push `sec/tomcat-nonroot` to `staging` (CI deploys; `deploy.sh` sees
`docker/antweb` changed, rebuilds the image and recreates the container). Prod:
merge the PR to master after staging passes. Wait 2 to 4 minutes for the caches.

## E. Verification

Tomcat runs as 2001, cron as root:
```
docker top antweb_antweb_1 -eo user,pid,args | grep -E 'UID|USER|java|cron'
```

Start-up warnings from startup.sh and antweb-fix-perms (expect no output):
```
docker logs antweb_antweb_1 --since 15m 2>&1 | grep -E 'startup.sh|antweb-fix-perms'
```

Tomcat could read the database login (expect `read from`, not `could not read` or `not found`):
```
D=$(docker inspect antweb_antweb_1 --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}'); grep -hE 'props\(\) ' "$D"/logs/antweb*.log | tail -3
```

As tomcat, create and delete an empty test file in each folder Tomcat writes:
```
docker exec -u tomcat antweb_antweb_1 sh -c 'for d in /usr/local/antweb/web/log /usr/local/antweb/web/genInc /usr/local/antweb/web/data /usr/local/antweb/log /usr/local/antweb/workingDir /usr/local/antweb/temp /usr/local/tomcat/logs /usr/local/tomcat/work /usr/local/tomcat/temp /mnt/antweb/images /mnt/antweb/web/upload /mnt/antweb/web/workingdir /mnt/antweb/web/curator /mnt/antweb/web/speciesList /mnt/antweb/web/log/upload /mnt/antweb/web/log/specimen /mnt/antweb/web/log/detail /mnt/antweb/web/log/worldants /mnt/antweb/web/documentation; do mkdir -p "$d" 2>/dev/null; if touch "$d/.nonroot-test" && rm "$d/.nonroot-test"; then echo "ok   $d"; else echo "FAIL $d"; fi; done'
```

Container cron jobs by hand, as cron runs them (staging; on prod only admin.sh):
```
docker exec antweb_antweb_1 sh -c '/antweb/deploy/bin/admin.sh | setpriv --reuid=tomcat --regid=antweb --init-groups tee /data/antweb/web/log/adminTask.log >/dev/null' && docker exec antweb_antweb_1 ls -ln /usr/local/antweb/web/log/adminTask.log
```
```
docker exec antweb_antweb_1 sh -c '/antweb/deploy/bin/taxonSetBackup.sh | setpriv --reuid=tomcat --regid=antweb --init-groups tee -a /data/antweb/web/log/taxonSetBackup.log >/dev/null' && docker exec antweb_antweb_1 sh -c 'ls -lt /mnt/antweb/web/bak/taxonSets/project | head -3'
```

In the browser (staging first; log in as admin):
- e2e suite on GitHub Actions (runs daily against staging, or re-run it).
- A specimen upload (curate page), then a species list upload.
- An image upload (image uploader) for a test specimen; the image appears.
- A description edit that adds an image, saved; then the image picker.
- Admin log manager and https://staging.antweb.org/web/log/adminTask.log.
- Password reset email (mail.properties is read).
- Nightly set 1 by hand: `https://staging.antweb.org/schedule.do?action=run&num=1`
  (WorldAnts import and TaxonWorks upload), then `compute.log` and
  `taxonworksNightly.log` under /web/log/.
- Restart path: `docker restart antweb_antweb_1`, then the `docker top` check again
  (this is what reboot_checker.sh and the watchdog do).

## Rollback

Code: revert the PR on master (staging: push master to `staging`). `deploy.sh`
sees `docker/antweb` change, rebuilds and Tomcat runs as root again. The host
changes in B and C also work with root Tomcat and can stay.

Undo the mount options, if wanted (same short outage as B):
```
F=$(systemctl show -p FragmentPath --value rclone); cp -a "$F.bak-nonroot" "$F" && docker stop antweb_antweb_1 && systemctl daemon-reload && systemctl restart rclone && sleep 5 && findmnt -T /mnt/antweb -o TARGET,OPTIONS && docker start antweb_antweb_1
```

Undo the secrets permissions, if wanted:
```
D=$(docker inspect antweb_antweb_1 --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}'); for f in db mail editor turnstile taxonworks flickr; do p="$D/etc/$f.properties"; [ -f "$p" ] && chgrp 0 "$p" && chmod 600 "$p"; done; ls -ln "$D"/etc/*.properties
```

Host log folders back to root (not needed; root reads them either way):
```
D=$(docker inspect antweb_antweb_1 --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}'); chown -R 0:0 "$D/logs" "$D/logbak"
```
