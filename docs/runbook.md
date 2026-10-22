# AntWeb operations runbook

Step-by-step procedures for running www.antweb.org and staging.antweb.org. How the pieces fit together: [architecture-overview.md](architecture-overview.md). Written 2026-10-18; the backup restore has not yet been tested end to end (planned in the documentation phase).

## Rules that apply to every procedure

- **Staging first, then production.** Write down the rollback before a change goes live.
- **Every command block starts with the ssh line** of the server it runs on. Run one block at a time and compare the output with what the text says to expect.
  - antweb-prod: `ssh root@165.232.137.86` (project folder `/root/antweb`)
  - staging: `ssh root@165.232.136.170` (project folder `/root/antweb-next`; `/root/antweb` is a link to it, so `/root/antweb/...` paths also work there)
- **Never edit, chmod or otherwise change a tracked file on a server.** git sees a local change and the next deploy fails. Code and file modes change only through GitHub. The settings files in `etc/` are not tracked and may be changed on the server.
- **No secrets in commands, chat, docs or the repo.** Database commands read the login from `etc/db.properties` (pattern below); never put a password on a command line. Never `cat` a file in `etc/` that holds a password, key or token (`db`, `api-db`, `mail`, `turnstile`, `taxonworks`).
- **Avoid 05:00 to 08:00 Pacific on production** for restarts and deploys that restart AntWeb: the nightly sets are running, and a restart stops them.
- **Write tests never run against production.**
- Deleting or power-cycling a droplet is done only by Brian in the DigitalOcean console. Changing DigitalOcean, Cloudflare, AWS or GitHub settings needs Brian's yes.

Database command pattern (production; on staging use `F=/root/antweb-next/etc/db.properties`):

```
# ssh root@165.232.137.86   (antweb-prod)
F=/root/antweb/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "select count(*) from specimen;"
```

Before deleting or changing rows, copy them to a dated backup table (for example `create table login_backup_20261018 as select * from login where ...;`).

## Deploy

1. Make the change on a branch and open a pull request into `master` (REST API: `gh api repos/calacademy-research/antweb/pulls -f title=... -f head=<branch> -f base=master -f body=...`).
2. Push the same branch to the `staging` branch. The "Deploy Staging" workflow runs `.github/scripts/deploy.sh` on staging. The end-to-end tests run only for pull requests, so the PR must exist.
3. Wait for both checks on the PR: "Tests" (about 3 minutes) and "End-to-end tests" (about 10 minutes, against staging). Failures: open the Actions run in a browser; the job summary lists the failing test and its first error line.
4. If the change is visible (layout, wording, new pages), Brian looks at the listed staging URLs.
5. Merge: `gh api -X PUT repos/calacademy-research/antweb/pulls/<N>/merge`. The "Deploy" workflow runs `deploy.sh` on antweb-prod.
6. Check production:

```
# ssh root@165.232.137.86   (antweb-prod)
cd /root/antweb && git log -1 --oneline && cat etc/git-commit && docker inspect -f 'StartedAt={{.State.StartedAt}}' antweb_antweb_1
```

Expect the merge commit in both lines. `StartedAt` is the deploy time when AntWeb was restarted, or older when only docs, tests or tools changed (no restart). After a restart the site answers 502 or "initializing" for 2 to 4 minutes; then the header shows the version and the short commit.

What `deploy.sh` decides (see the architecture overview for detail): no restart when only `.github/`, `tests/`, `tools/`, `doc/`, `docs/`, `release-notes/` or top-level `*.md` files changed; image rebuild and new containers when `docker/antweb/`, `docker-compose.yml` or `docker-compose.stage.yml` changed (the old container serves during the build; if the build fails the script stops and the old container keeps running); otherwise `ant deploy` and a restart. Caddy restarts when a Caddyfile changed.

## Rollback

**Code (the normal rollback).** Revert the merge on a branch and send it through the same flow:

```
git checkout -b revert/pr-<N> origin/master
git revert -m 1 <merge commit>
git push origin revert/pr-<N>
```

Open a PR, push it to `staging`, merge. In an emergency (production broken, staging tests cannot help) Brian may decide to merge the revert without waiting for the end-to-end tests. Never roll back by checking out an older commit on the server: the next deploy expects the checkout to follow `master`.

**Settings switch.** Put the file in `etc/` back as it was (see "Switches"); the application picks it up within a minute (scheduler: at the end of the next nightly run).

**Database change.** Restore from the dated backup table made before the change (each change's PR or to-do note gives the exact statement). For a whole-database problem see "Restore".

## Restart

A restart stops any running upload (it is rolled back and has to be repeated) and any running nightly set. First check that no upload is running: open `https://www.antweb.org/serverStatus.do` as an admin.

```
# ssh root@165.232.137.86   (antweb-prod)
docker restart antweb_antweb_1 && sleep 180 && /root/antweb/bin/reboot_checker.sh --dry-run
```

Expect `Answer: false (no restart)` once the application is up (if it says `empty`, wait another minute and run only the last command again). Caches load for 2 to 4 minutes after the start; pages answer 502 or "initializing" meanwhile.

Other containers: `docker restart antweb_caddy_1` (a few seconds, no cache load), `docker restart antweb_api_1`. Restart MySQL (`antweb_mysql_1`) only when needed and only with AntWeb stopped or idle; AntWeb then needs its own restart.

An admin can also ask for a restart from the Curate page ("Restart server now", `utilData.do?action=setIsRestart`): `reboot_checker.sh` restarts the container within 5 minutes, but not while an upload is running.

## Logs

Production paths; on staging replace `/root/antweb` with `/root/antweb-next` (or use the `/root/antweb` link).

| Log | Where on the host | Also readable |
|---|---|---|
| Nightly run (`compute.log`), admin actions (`admin.log`), TaxonWorks nightly (`taxonworksNightly.log`), 21:00 job (`adminTask.log`), `taxonSetBackup.log` and other application logs | `/root/antweb/logs/web/` (kept across rebuilds; rotated as below) | `https://www.antweb.org/logMgr.do` (admin), e.g. `logMgr.do?action=get&fileName=compute&ext=log` |
| Tomcat console (`catalina.out`: start-up, errors, `startup.sh` warnings) | `docker logs antweb_antweb_1` (last 50 MB) | `logMgr.do?action=tomcatLog` |
| Tomcat files: `catalina.*.log`, `localhost_access_log.*.txt` (180 days), `antweb.log`, `antwebInfo.log`, `detail/` (search, upload, event logs) | `/root/antweb/logs/` | `logMgr.do?action=antwebLog`, `antwebInfoLog` |
| Data logs: `emails.log` (every mail sent or not sent), `messages.txt` (one short line per message page), `longRequest.log` (requests over 10 seconds), `serverBusy.html`, `cCheck.log` (CPU over 90%), `logrotate.log` | `/root/antweb/logbak/` (container `/usr/local/antweb/log`; rotated as below) | `messages.txt`, `longRequest.log`, `cCheck.log` and `serverBusy.html` through logMgr.do; `emails.log` only on the host |
| Upload reports (public, linked from group and upload pages) | `/mnt/antweb/web/log/{upload,specimen,worldants,detail}/` | `https://www.antweb.org/web/log/upload/...` |
| Caddy access log (JSON) | `/root/antweb/docker/caddy/logs/access.log` | |
| Restart checker | `/root/antweb/logs/detail/rebootCheck.log` (only restarts and warnings) | |
| Watchdog | `/var/log/antweb-watchdog.log`; thread dumps in `/root/antweb-watchdog-dumps/` | |
| Daily database dump | `/root/antweb/db-daily-dump.log`; failures also in `/mnt/backup/db/daily/error.log` | |
| Weekly reboot | `journalctl -t weekly-reboot` | |
| API, MySQL, Caddy console | `docker logs antweb_api_1` (likewise `antweb_mysql_1`, `antweb_caddy_1`) | |

```
# ssh root@165.232.137.86   (antweb-prod)
tail -30 /root/antweb/logs/web/compute.log; tail -10 /root/antweb/logs/web/taxonworksNightly.log
```

### Log sizes and rotation

- **Rotation.** `docker/antweb/logrotate.conf` (in the image as `/etc/antweb-logrotate.conf`) runs every day at 03:40 as the user tomcat (`docker/antweb/crontab.txt`). It covers the top-level `*.log` and `*.txt` of `logs/web/` and `logbak/`: a file over 10 MB is rotated once a week, a file over 100 MB at the next daily run. Old copies are compressed at once (`compute.log-20261025.gz`); 26 are kept per log, older ones are deleted. The report folders (`upload`, `specimen`, `worldants`, `detail`) are never touched. Each run is logged in `logbak/logrotate.log`; its state (when each file was last rotated) is `logbak/.logrotate.state`.
- **A new file waits a week.** logrotate records a file it has not seen before with the date of that run and rotates it by the weekly rule only a week later ("log does not need rotating (log has already been rotated)" in `logrotate.log`). That is why `messages.txt` was not rotated when rotation started on 2026-10-19: 11 GB on staging, 1.8 GB on production by 10-22. Since 2026-10-22 the 100 MB `maxsize` rotates a big file the next day regardless.
- **Size limits in the code.** No log entry is longer than 20,000 characters (`LogMgr.MAX_ENTRY`, cut with "... [cut, N characters]"); upload reports in the report folders are written in full. A message page writes one short line to `messages.txt`: the date, the first 300 characters of the message as plain text, its full length if cut, and the page address without the form token or password values (`MessagePageLog`). The same message is written at most 3 times a day; the next day one "summary: shown N times" line gives the count (the "You must log in" page shown to bots was millions of lines on production). `longRequest.log` lines hold the time, the address (at most 500 characters) and the start of the user agent. The log viewer (logMgr.do) shows at most 2,000 characters of a line and 5 MB in all.
- **Warning.** After each nightly run (`compute.log`: "Log size check: ok") AntWeb checks for a log file over 1 GB in `logbak/`, `logs/web/` or `logs/`, and the disk over 85%. If so, it adds an admin alert (Admin Alert Manager) and emails antweb@antweb.org (forwarded to Brian), every day until fixed.

### A log grows too big

```
# ssh root@165.232.137.86   (antweb-prod)
ls -laS /root/antweb/logbak | head -15; ls -laS /root/antweb/logs/web | head -15; df -h /
tail -20 /root/antweb/logbak/logrotate.log
```

Expect files well under 100 MB except on the day before a rotation. To rotate now instead of waiting for 03:40 (the same command the crontab runs; it rotates only files due under the rules above and compresses them, which can take a few minutes for a multi-GB file):

```
# ssh root@165.232.137.86   (antweb-prod)
docker exec antweb_antweb_1 setpriv --reuid=tomcat --regid=antweb --init-groups /usr/sbin/logrotate --state /usr/local/antweb/log/.logrotate.state --log /usr/local/antweb/log/logrotate.log /etc/antweb-logrotate.conf; ls -la /root/antweb/logbak
```

Then find what writes so much: `tail -c 2000 <file>` shows the last lines. A single writer filling a log is a bug to fix in the code (as for `messages.txt` on 2026-10-22), not only a file to delete.

**Old leftovers, safe to delete (Brian).** These are not rotated (their names do not end in `.log` or `.txt`) or are no longer written: `longRequest.log.0` (730 MB, last written 2023-12-03), `cCheck.log.0` (219 MB, 2023-12-03) and `cCheck.log` (211 MB, last written 2024-06-21) in `logbak/` on production. Check the dates first, then delete:

```
# ssh root@165.232.137.86   (antweb-prod)
ls -la /root/antweb/logbak/*.log.0 /root/antweb/logbak/cCheck.log
rm /root/antweb/logbak/longRequest.log.0 /root/antweb/logbak/cCheck.log.0 /root/antweb/logbak/cCheck.log
```

Expect the three files listed with those sizes and 2023 or 2024 dates; `rm` prints nothing. (If `cCheck.log` shows a 2026 date, it is being written again: leave it, rotation handles it.) On staging, the same `ls -la /root/antweb-next/logbak/*.log.0` shows whether it has such files.

## Common checks

```
# ssh root@165.232.137.86   (antweb-prod)
docker ps
docker logs antweb_antweb_1 --since 1h 2>&1 | tail -50
df -h / ; free -h ; docker stats --no-stream antweb_antweb_1
/root/antweb/bin/reboot_checker.sh --dry-run
```

Expect four containers `Up` (the API also `(healthy)`), no repeated exceptions in the log, the root disk under 85%, and `Answer: false`.

## Backups

| What | When | Where | Kept |
|---|---|---|---|
| Database dump (`ant`, with routines), `bin/db-daily-dump.sh` from host cron | 01:00 daily | `/mnt/backup/db/daily/YYYY-MM-DD.sql.gz` (about 120 MB); the newest also as `/mnt/backup/db/ant-currentDump.sql.gz` | files older than 7 days are deleted (in practice 8 to 9 daily files are present; 9 on 2026-10-18) |
| Monthly dump, `bin/db-monthly-dump.sh` from container cron | 23:00 on the 1st | `/mnt/backup/db/monthly/YYYY-MM-DD.sql.gz` | 365 days |
| Taxon sets (geolocale, project, museum, bioregion) | Thursdays 03:50 | `/mnt/antweb/web/bak/taxonSets/` | not pruned |
| DigitalOcean droplet backups | daily (antweb-prod, antcat-prod); weekly (staging, antcat-export) | DigitalOcean | 7 days (daily); 28 days (weekly) |

Images, uploaded files and curator files are in DigitalOcean Spaces (`/mnt/antweb`), not on the droplet.

Check that last night's dump exists:

```
# ssh root@165.232.137.86   (antweb-prod)
ls -lh /mnt/backup/db/daily/ | tail -3; tail -3 /root/antweb/db-daily-dump.log
```

Expect today's file, about 120 MB. If `/mnt/backup` is not mounted, the script finds no folder and silently does nothing: check `findmnt /mnt/backup`.

### Restore a dump to staging

This replaces staging's database with a production dump, for a restore test or to refresh staging. It never touches production. The weekly automatic refresh ("Staging refresh", below) does the same with the newest dump; use these steps for one particular date. A restore to production is Brian's decision and is not covered here.

Notes before starting:
- The dump holds personal data (logins, email addresses, password hashes). Keep copies in root-only folders and delete them at the end.
- Pick a time away from 05:00 to 08:00 (nightly), 13:17 (daily end-to-end run) and any staging deploy. Tell anyone using staging.
- The dump was made with `--databases ant`, so it loads into the database `ant` and replaces every table it contains. Tables that exist only on staging stay.
- Accounts that exist only on staging (for example the end-to-end test login) are replaced by production's `login` table. Check step 7.

**Step 1. Make a folder for the dump on staging.**

```
# ssh root@165.232.136.170   (staging)
install -d -m 700 /root/restore && df -h / && ls -la /root/restore
```

Expect an empty folder and at least 10 GB free on `/` (an estimate, not measured).

**Step 2. Copy the chosen dump from production to staging, through your Mac.** Run these two lines on the Mac (not on a server); replace the date.

```
# On your Mac (Terminal), not on a server
scp root@165.232.137.86:/mnt/backup/db/daily/2026-10-18.sql.gz ~/Downloads/ && scp ~/Downloads/2026-10-18.sql.gz root@165.232.136.170:/root/restore/ && rm ~/Downloads/2026-10-18.sql.gz
```

Expect two progress lines of about 120 MB each.

**Step 3. Check the file is complete.**

```
# ssh root@165.232.136.170   (staging)
chmod 600 /root/restore/*.sql.gz && gzip -t /root/restore/2026-10-18.sql.gz && echo OK && gunzip -c /root/restore/2026-10-18.sql.gz | tail -1
```

Expect `OK` and a last line `-- Dump completed on ...`. If either is missing, the file is damaged: pick another day.

**Step 4. Save staging's current database (this is the rollback).**

```
# ssh root@165.232.136.170   (staging)
F=/root/antweb-next/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysqldump -u "$(sed -n 's/^user=//p' "$F")" --routines --single-transaction --no-tablespaces --default-character-set=utf8mb4 --databases ant | gzip -c > /root/restore/staging-before-restore-$(date +%Y%m%d-%H%M).sql.gz && ls -lh /root/restore
```

Expect a new `staging-before-restore-*.sql.gz` of roughly the same size as the production dump. Do not go on if it is tiny or missing.

**Step 5. Stop AntWeb on staging** (the database stays up), so nothing writes during the load.

```
# ssh root@165.232.136.170   (staging)
docker stop antweb_antweb_1 && docker ps --format '{{.Names}} {{.Status}}'
```

Expect `antweb_antweb_1` missing from the list and the other three `Up`.

**Step 6. Load the dump.** This can take a long time (time not yet measured); leave the window open.

```
# ssh root@165.232.136.170   (staging)
F=/root/antweb-next/etc/db.properties; time (gunzip -c /root/restore/2026-10-18.sql.gz | docker exec -i -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql --default-character-set=utf8mb4 -u "$(sed -n 's/^user=//p' "$F")" ant) && echo LOADED
```

Expect no error lines, then the time taken and `LOADED`. On an error, note the line and go to "Undo" below.

**Step 7. Check the data, then start AntWeb.**

```
# ssh root@165.232.136.170   (staging)
F=/root/antweb-next/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "select count(*) as specimens from specimen; select max(created) as last_upload from upload; select count(*) as logins from login;"
```

Expect production's numbers and a last upload on or just before the dump date. Then:

```
# ssh root@165.232.136.170   (staging)
docker start antweb_antweb_1 && sleep 240 && /root/antweb-next/bin/reboot_checker.sh --dry-run
```

Expect `Answer: false`. Open https://staging.antweb.org/ and a species page; log in. Run the end-to-end tests on staging (Actions, "End-to-end tests", Run workflow); if the test login no longer exists, Claude recreates it before the run.

**Step 8. Clean up** (after the checks pass):

```
# ssh root@165.232.136.170   (staging)
rm /root/restore/2026-10-18.sql.gz && ls -lh /root/restore
```

Keep `staging-before-restore-*` for a week, then delete it the same way.

**Undo** (staging back to how it was before step 6): stop AntWeb as in step 5, load `staging-before-restore-*.sql.gz` exactly as in step 6, start AntWeb as in step 7.

### Staging refresh

Once a week staging's database is replaced by production's newest nightly dump. This keeps staging's data current and proves every week that production's backups load. The manual procedure above stays for loading one particular date.

How it works (`tools/staging/refresh-from-prod.sh`, runs on staging only):
- It refuses to run unless the host is `staging`, `/etc/antweb-role` says `staging`, the project is `/root/antweb-next`, AntWeb was started from there and the server does not have production's address. It also refuses between 04:45 and 08:15 Pacific (nightly run) and while another refresh runs.
- It reads production's newest daily dump from the Spaces bucket `antweb-dbarchive` (`db/daily/`) with rclone, read only. Nothing is ever written to production or to the bucket. A dump older than 3 days stops the refresh with an error: that means production's nightly backup is failing.
- It checks free disk first (3 times the dump size plus 1 GB) and the dump itself (complete gzip, only the database `ant`, ends with "Dump completed").
- Before loading it saves the test logins (`zztest_uploader`, `zztest_noupload`, `zztest_admin`, the test group, and any login named in `/root/staging-refresh/keep-logins.txt`) with their password hashes, and makes one safety dump of staging's database.
- It stops AntWeb, loads the dump, scrubs personal data (`tools/staging/scrub.sql`: every email becomes `login<id>@example.invalid`, every password a random hash nobody knows, sessions and reset links ended), puts the test logins back with the same password hashes (so the GitHub secrets keep working), adds `zztest_uploader` (which has a new login number after each refresh) to the "Upload as" list of the administrator login(s) named in `/root/staging-refresh/upload-as.txt` (lines `adminLogin=NAME`; without the file: the login `antweb`; a file with only comments or blank lines switches it off; any other line is an error) if it is not there yet, starts AntWeb and waits until it answers on 127.0.0.1:8081. A failure of the "Upload as" step is only a WARNING (add it by hand on the login's page).
- If any step after stopping AntWeb fails, it loads the safety dump again and starts AntWeb: staging is as before.
- On success it deletes the downloaded dump and the safety dump (`--keep-safety` keeps the safety dump until the next refresh).
- Columns staging had that production has not (a migration tried on staging only) are listed as a WARNING; re-apply that `db/upgrade` file if the branch on staging still needs it.
- Log: `journalctl -t staging-refresh` and `/root/staging-refresh/refresh.log`. Exit codes: 0 done, 1 failed (staging put back), 2 refused (not staging, nightly window, or `--no-scrub` with `mail.properties`), 3 another refresh running, 4 production's dump too old, 5 not enough disk, 6 too late (`--not-after`).
- Mail: staging should have no `etc/mail.properties` (then nothing is sent). With scrubbing no real address is left in any case. `--no-scrub` is refused while `mail.properties` exists.

**Decisions (Brian):** scrubbing is on by default (recommended: staging needs no real emails or password hashes; the end-to-end tests use only the test logins). Schedule: Sundays 02:30 Pacific, after production's 01:00 dump and before the 04:00 weekly reboot; the cron line passes `--not-after 03:15`, so a late run stops before AntWeb is touched. How long the load takes is not yet measured: the first run's log shows it ("Loaded in ...").

#### One-time setup and first refresh

Each step on staging:

```
ssh root@165.232.136.170
```

**Step 1. Mark the server as staging and check the clock.**

```
echo staging > /etc/antweb-role && cat /etc/antweb-role && hostname && timedatectl show -p Timezone --value && ls -l /root/antweb-next/tools/staging/
```

Expect `staging`, `staging`, `America/Los_Angeles`, and `refresh-from-prod.sh` and `cleanup-disk.sh` with `x` in their modes (`-rwxr-xr-x`). If the time zone is different, stop and tell Claude (the cron time assumes Pacific).

**Step 2. Check rclone.**

```
rclone version | head -1; rclone listremotes
```

Expect a version line (if "command not found": `apt-get install -y rclone`) and a list of remotes, possibly empty. If `dbarchive-ro:` is already listed, skip step 3.

**Step 3. Give staging read-only access to production's dumps.** In the DigitalOcean console: Spaces Object Storage, Access Keys, Create Access Key, "Limited access", bucket `antweb-dbarchive`, permission Read; name it `staging-refresh-readonly`. The secret is shown once. Then on staging type the key id and secret at the prompts (the secret is not shown):

```
read -rp 'Spaces access key id: ' K; read -rsp 'Spaces secret key: ' S; echo; rclone config create dbarchive-ro s3 provider=DigitalOcean endpoint=sfo3.digitaloceanspaces.com access_key_id="$K" secret_access_key="$S" acl=private --non-interactive >/dev/null; unset K S; chmod 600 /root/.config/rclone/rclone.conf; rclone lsf dbarchive-ro:antweb-dbarchive/db/daily | tail -3
```

Expect the three newest dump names, for example `2026-10-25.sql.gz`. "AccessDenied": the key is not for that bucket.

**Step 4. Disk report (deletes nothing).**

```
/root/antweb-next/tools/staging/cleanup-disk.sh
```

Expect the disk use, the largest folders, Docker's use, and the safe list with sizes; at the end "Report only: nothing was deleted". Read what it would delete (build cache, dangling images, old dump copies in `/root`, old restore files, `/root/antweb-old-2026-10-13` if no container uses it, rotated logs over 30 days, old watchdog dumps, journal above 100 MB, apt cache). The section "Not on the safe list" is for Brian to decide, by hand.

**Step 5. Free the safe list.**

```
/root/antweb-next/tools/staging/cleanup-disk.sh --apply
```

Expect "deleted ..." lines, then the report again with a lower `Use%`. Docker volumes (the database), `/mnt` and `etc/` are never touched.

**Step 6. Dry run of the refresh (changes nothing).**

```
/root/antweb-next/tools/staging/refresh-from-prod.sh --dry-run
```

Expect: "Mail: no ... mail.properties", "Dump: newest in ... is YYYY-MM-DD.sql.gz (about 120 MB)", "Dump age: 0 day(s)", "Disk: need about ... MB free; free: ...", the test logins on staging, "Logins used on staging in the last 14 days (not zztest): ...", and "Dry run: nothing was changed." A "Refused" or "ERROR" line says what to fix.

**Step 7. Keep the end-to-end curator login.** The tests also log in with `ANTWEB_TEST_USER` (a curator login, not one of the zztest logins). Its name is among "Logins used on staging in the last 14 days" in step 6. Put it in the keep list (replace `NAME`):

```
install -d -m 700 /root/staging-refresh && echo 'NAME' > /root/staging-refresh/keep-logins.txt && cat /root/staging-refresh/keep-logins.txt
```

Expect the name. That login keeps its staging password (and email, rights, projects) after each refresh; every other login is scrubbed.

**Step 8. First refresh, by hand.** Not between 04:45 and 08:15, not near 13:17 (daily end-to-end run). It runs as a background service, so a dropped ssh connection does not stop it; `--keep-safety` keeps the safety dump for a rollback after checking.

```
systemd-run --unit=staging-refresh-manual --collect /root/antweb-next/tools/staging/refresh-from-prod.sh --keep-safety && journalctl -t staging-refresh -f
```

Expect the steps one by one: download, "Dump checked", "Saved 4 login(s)", "Safety dump done", "Stopping AntWeb", "Loaded in ...", "Scrubbed", four "restored" lines, "Starting AntWeb", "AntWeb answers", "Refresh OK". Then press Ctrl-C (this only stops watching). "Refresh FAILED" means staging was put back; send Claude the lines above it.

**Step 9. Check.** Open https://staging.antweb.org/ and a species page. Then Claude starts the end-to-end tests on staging (they log in with the test logins). If they pass, the safety dump can stay; the next refresh replaces it.

**Step 10. Install the weekly run.**

```
install -m 644 /root/antweb-next/tools/staging/staging-refresh.cron /etc/cron.d/staging-refresh && cat /etc/cron.d/staging-refresh | grep -v '^#'
```

Expect the `SHELL`, `PATH`, `MAILTO` lines and `30 2 * * 0 root /root/antweb-next/tools/staging/refresh-from-prod.sh --not-after 03:15 ...`.

#### Weekly check (Sunday, after 03:30)

```
journalctl -t staging-refresh --since "6 hours ago" --no-pager | tail -5
```

Expect "Refresh OK: staging has production's data of <today>.sql.gz". "production's newest daily dump is N days old" means production's backup is failing: check it (Backups, above).

#### Rollback

Back to staging's database from before the last refresh (only while the safety dump exists: after a failed refresh, after a `--keep-safety` run, or when AntWeb did not answer after the refresh):

```
/root/antweb-next/tools/staging/refresh-from-prod.sh --rollback
```

Expect "Rollback done". Without a safety dump, the fallbacks are the next weekly refresh (staging's data is a copy of production anyway) or DigitalOcean's weekly droplet backup (Brian, in the console). If the test logins ever stop working, `python3 /root/antweb-next/tools/staging/create-test-accounts.py --reset-passwords` makes new passwords for the GitHub secrets.

#### Stop the weekly refresh

```
rm /etc/cron.d/staging-refresh && ls /etc/cron.d/
```

Expect `staging-refresh` no longer listed. To start it again, step 10.

## Alerts

All alerts email bfisher@calacademy.org.

| Alert | What it usually means | What to do |
|---|---|---|
| CPU above 90% for 1 hour (antweb-prod) | Between 05:00 and 08:00: the nightly sets (expected heavy load). Otherwise a crawler burst or a stuck Java thread. | Common checks. Outside the nightly window: `docker stats --no-stream`; `tail /var/log/antweb-watchdog.log` (the watchdog restarts a stuck thread within the hour). If it persists, restart (above). |
| Memory above 90% for 30 minutes | Java heap growth (Java may use up to 40% of RAM), MySQL, or many API bulk requests (API is capped at 1 GB). | `free -h; docker stats --no-stream`; `grep "heap used" /root/antweb/logs/web/compute.log \| tail -5` shows the heap at the end of each nightly run. Restart AntWeb at a quiet time. If it recurs, `nightlyRestart=true` (Switches) brings back the daily restart. |
| Disk above 85% for 30 minutes | Logs, Docker images or build cache, journal. | "Disk full" below. |
| "Log size check" (email from antweb@antweb.org and an admin alert, after the nightly run) | A log file over 1 GB, or the disk over 85%. | "A log grows too big" (Logs, above). |
| Disk read above 90 MB/s for 1 hour (antweb-prod) | The nightly sets, or a long database scan. | If outside 05:00 to 08:00: running queries with the DB pattern and `show full processlist;`. |
| Uptime: www.antweb.org/v3.1/ down 10 minutes from all regions | Cloudflare cannot reach Caddy or the API (droplet down, Caddy or API stopped, firewall, certificate). This check does not cover Tomcat: AntWeb pages can be down while it passes. | "Site down" below. |
| Uptime: www.antcat.org/v1/taxa/429011 | antcat.org is down. | AntCat runbook (AntCat repository and project instructions). |

## Weekly security reboot

Every Sunday `/usr/local/sbin/weekly-reboot` (from `/etc/cron.d/weekly-security-reboot`) reboots the droplet if Ubuntu flags a reboot or a newer kernel is installed than the one running. Times (Pacific): staging 04:00, antweb-prod 04:15. The containers come back by themselves (`restart: unless-stopped`); AntWeb then loads its caches for 2 to 4 minutes. Check on Sunday morning:

```
# ssh root@165.232.137.86   (antweb-prod)
journalctl -t weekly-reboot --no-pager | tail -3; uptime; findmnt /mnt/antweb /mnt/backup; docker ps --format '{{.Names}} {{.Status}}'
```

Expect a log line for today (rebooted or not needed), both Spaces mounts listed, four containers `Up`.

## Restart checker and watchdog

- `bin/reboot_checker.sh` (host cron, every 5 minutes) asks the application on `http://127.0.0.1:8081/util.do?action=isRestart`. It restarts the container when the answer is `true` or the application's error page (`case#`) comes back. The application answers `true` when set 1 of the nightly run has been running for 210 minutes, when an admin asked for a restart, or (only with `nightlyRestart=true`) after the nightly run; never while an upload is running. An empty answer (application down) is logged and nothing is done. Check without acting: `/root/antweb/bin/reboot_checker.sh --dry-run`.
- `/root/antweb-watchdog.sh` (root crontab, hourly at :15; not in git) takes two thread CPU samples 30 seconds apart; if one thread is above 95% in both, the container has been up more than 12 hours and there was no watchdog restart in the last 6 hours, it saves a thread dump and restarts the container. Log `/var/log/antweb-watchdog.log`.

Why did the container restart? `grep -h "restart" /root/antweb/logs/detail/rebootCheck.log | tail -3; tail -5 /var/log/antweb-watchdog.log; journalctl -t weekly-reboot --no-pager | tail -2`, and the Deploy runs in GitHub Actions.

## Switches

The files are in `etc/` of the project folder, not tracked by git, read-only for the application. Each file must be readable by group 2000: create it with the commands below. The application re-reads `editor`, `upload-email`, `taxonworks`, `mail` and `turnstile` within a minute; `scheduler.properties` at the end of the next nightly run.

**Upload-complete emails off** (on again: remove the file):

```
# ssh root@165.232.137.86   (antweb-prod)
cd /root/antweb/etc && printf 'enabled=off\n' > upload-email.properties && chgrp 2000 upload-email.properties && chmod 640 upload-email.properties && cat upload-email.properties
```

Expect `enabled=off`. Undo: `rm /root/antweb/etc/upload-email.properties` (production default: on, except group 1, CAS).

**Old page editor for all curators** (CKEditor 3 instead of TinyMCE):

```
# ssh root@165.232.137.86   (antweb-prod)
cd /root/antweb/etc && printf 'pageEditor=ckeditor\n' > editor.properties && chgrp 2000 editor.properties && chmod 640 editor.properties && cat editor.properties
```

Undo: `rm /root/antweb/etc/editor.properties`. One page only: add `pageEditor=ckeditor` to that edit page's address.

**Restart after the nightly run** (off since 2026-10-18):

```
# ssh root@165.232.137.86   (antweb-prod)
cd /root/antweb/etc && printf 'nightlyRestart=true\n' > scheduler.properties && chgrp 2000 scheduler.properties && chmod 640 scheduler.properties && cat scheduler.properties
```

Undo: `rm /root/antweb/etc/scheduler.properties`. compute.log says at the end of each run whether the restart was requested or skipped.

**Stop the nightly TaxonWorks upload.** The file holds a private download link: never print it. Rename it rather than deleting it, so it can be put back:

```
# ssh root@165.232.137.86   (antweb-prod)
cd /root/antweb/etc && mv taxonworks.properties taxonworks.properties.off-$(date +%Y%m%d) && ls -l taxonworks.properties*
```

Undo: `mv taxonworks.properties.off-<date> taxonworks.properties`. Without the file, both the nightly run and the admin's manual run (`/utilData.do?action=taxonWorksNightly`) return "skipped: no /antweb/deploy/etc/taxonworks.properties".

**Stop all outgoing mail** (for example a mail loop): rename `mail.properties` the same way (`mv mail.properties mail.properties.off-$(date +%Y%m%d)`). Messages are then only written to `logbak/emails.log`.

## Emergencies

### Site down

1. Is it everyone? Check https://www.antweb.org/ and https://www.antweb.org/v3.1/ in a browser. A Cloudflare error page (520 to 526) means Cloudflare cannot get a good answer from the droplet; Caddy's "Not found" or a 502 from Caddy means Caddy is up but the application is not.
2. On the droplet:

```
# ssh root@165.232.137.86   (antweb-prod)
docker ps -a --format '{{.Names}} {{.Status}}'; docker logs antweb_antweb_1 --since 15m 2>&1 | tail -40; /root/antweb/bin/reboot_checker.sh --dry-run; df -h /; free -h
```

3. Decide from the output:
   - `antweb_antweb_1` up less than 4 minutes: it is loading caches; wait.
   - A container `Exited` or `Restarting`: `docker start <name>` and read its `docker logs` for the cause (a missing or unreadable `etc/` file shows as a `startup.sh: WARNING` line).
   - All up but `Answer: empty`: restart AntWeb (above).
   - Disk at 100%: "Disk full".
   - A deploy just ran and failed: see the Deploy run in GitHub Actions; roll back the code.
4. If ssh does not answer, the droplet itself is down: Brian checks it in the DigitalOcean console (power cycle only there).

### Disk full

```
# ssh root@165.232.137.86   (antweb-prod)
df -h /; du -sh /root/antweb/logs /root/antweb/logs/web /root/antweb/logbak /root/antweb/docker/caddy/logs /root/antweb-watchdog-dumps 2>/dev/null; docker system df; journalctl --disk-usage
```

Safe to free: unused Docker build cache and dangling images (`docker builder prune -f; docker image prune -f`); old thread dumps in `/root/antweb-watchdog-dumps/`; rotated logs (`*.gz`) under `/root/antweb/logs/web` and `/root/antweb/logbak` after copying them to `/mnt/backup/logs/` if they may be needed. The journal is capped at 300 MB. Never delete under `/mnt/antweb` or `/mnt/backup` to free local disk (they are Spaces, not the droplet's disk), and never delete Docker volumes without Brian (they hold the database).

### Nightly run stuck

```
# ssh root@165.232.137.86   (antweb-prod)
tail -20 /root/antweb/logs/web/compute.log; tail -10 /root/antweb/logs/web/taxonworksNightly.log; /root/antweb/bin/reboot_checker.sh --dry-run
```

- `Begin set1` with no end: set 1 may wait up to 90 minutes for TaxonWorks and then upload. After 210 minutes (8:30 AM) the application asks for a restart and `reboot_checker.sh` restarts it within 5 minutes, unless an upload is running.
- A later set running far beyond its usual time (2: about 45 min, 3 and 4: about 15, 5: about 50): no automatic restart, except the watchdog for a thread spinning at full CPU. Check that no upload is running (`serverStatus.do`), then restart.
- `Scheduler FAILED (remaining sets skipped)`: the sets after the failing one did not run. An admin can run one set by hand from the Curate page (`schedule.do?action=run&num=<N>`, confirm page). Avoid busy hours; set 5 is heavy and is skipped on staging.

### Upload stuck

While an upload is running, `serverStatus.do` names it, and the application refuses restart requests (so `reboot_checker.sh` waits). The watchdog and a manual `docker restart` still restart. Since 2026-10-20 uploads and admin data jobs started from a page run in the background, one at a time: `jobStatus.do` (Upload status, linked from the Curate page) lists them with their status and current step, and `serverStatus.do` shows "Background job running". A restart interrupts the running and waiting jobs; their status pages then say "Interrupted" and the curator uploads again (the upload's transaction is rolled back). Database view: `select id, kind, action, status, login_name, created, step from background_job order by id desc limit 20;`

```
# ssh root@165.232.137.86   (antweb-prod)
tail -30 /root/antweb/logs/detail/upload.log; docker logs antweb_antweb_1 --since 30m 2>&1 | grep -i upload | tail -20
```

If the log shows recent progress, wait: large uploads take a long time. If nothing has moved for a long time (no rule yet; judge from the upload's size), restart AntWeb. The upload is rolled back and the curator repeats it. If it was the nightly TaxonWorks upload, it runs again the next night, or an admin starts it by hand at `/utilData.do?action=taxonWorksNightly`.

## Backup facts checked on 2026-10-18

- Production: `/mnt/backup` is the rclone mount of Spaces bucket `antweb-dbarchive` (service `rclone-dbarchive`), `/mnt/antweb` of bucket `antweb` (service `rclone`); both enabled at boot. Check with `findmnt /mnt/backup` (one folder per call).
- Staging: `/mnt/backup` is a plain local folder on staging's own disk (last written October 2025); staging runs no daily dump, so it never touches production's backups.
- `/mnt/backup/db/daily/error.log` holds failures from up to December 2023 only. A failed or skipped dump is written only there and is not reported anywhere yet (an alert is on the to-do list).
