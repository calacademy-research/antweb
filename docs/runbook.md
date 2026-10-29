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

A deploy waits for running uploads, up to 30 minutes. Before any step that restarts AntWeb, `deploy.sh` asks the running application on `http://127.0.0.1:8081/util.do?action=isBusy` (anonymous, read-only, plain text). The answer is `idle` or `busy:` with the kinds of work running: a specimen upload, an image upload (also between two files of an unfinished upload, until 10 minutes without a new file), the TaxonWorks nightly upload, the nightly name update, a background job, a data job or the nightly run. While it is busy the deploy asks again every 30 seconds ("AntWeb is busy: ...; waiting before the restart" in the Deploy log). After 30 minutes in all it restarts anyway, with a warning in the GitHub run ("restarting anyway"): then check the upload. No answer (AntWeb down or starting) means no wait. A deploy that does not restart AntWeb never waits. Check by hand: `curl -s http://127.0.0.1:8081/util.do?action=isBusy` on the server. Added 2026-10-27, after a deploy on 10-26 cut a curator image upload short.

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

- **Rotation.** `docker/antweb/logrotate.conf` (in the image as `/etc/antweb-logrotate.conf`) runs every day at 03:40 as the user tomcat (`docker/antweb/crontab.txt`). It covers the top-level `*.log` and `*.txt` of `logs/web/` and `logbak/`: a file over 10 MB is rotated once a week, a file over 100 MB at the next daily run. Old copies are compressed at once (`compute.log-20261025-03.gz`, date and hour); 26 are kept per log, older ones are deleted. The report folders (`upload`, `specimen`, `worldants`, `detail`) are never touched. Each run is logged in `logbak/logrotate.log`; its state (when each file was last rotated) is `logbak/.logrotate.state`.
- **A new file waits a week.** logrotate records a file it has not seen before with the date of that run and rotates it by the weekly rule only a week later ("log does not need rotating (log has already been rotated)" in `logrotate.log`). That is why `messages.txt` was not rotated when rotation started on 2026-10-19: 11 GB on staging, 1.8 GB on production by 10-22. Since 2026-10-22 the 100 MB `maxsize` rotates a big file the next day regardless.
- **Size limits in the code.** No log entry is longer than 20,000 characters (`LogMgr.MAX_ENTRY`, cut with "... [cut, N characters]"); upload reports in the report folders are written in full. A message page writes one short line to `messages.txt`: the date, the first 300 characters of the message as plain text, its full length if cut, and the page address without the form token or password values (`MessagePageLog`). The same message is written at most 3 times a day, then "count so far" lines at 100, 1,000, 10,000 ... showings (kept even if AntWeb restarts), and the next day one "summary: shown N times" line gives the day's count (the "You must log in" page shown to bots was millions of lines on production). `longRequest.log` lines hold the time, the address (at most 500 characters) and the start of the user agent. The log viewer (logMgr.do) shows at most 2,000 characters of a line and 5 MB in all.
- **Warning.** After each nightly run (`compute.log`: "Log size check: ok") AntWeb checks for a log file over 1 GB in `logbak/`, `logs/web/` or `logs/`, and the disk over 85%. If so, it emails antweb@antweb.org (forwarded to Brian) every day until fixed, and adds an admin alert (Admin Alert Manager) at most once a week.

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

Expect "deleted ..." lines, then the report again with a lower `Use%`. Docker volumes (the database) and `etc/` are never touched; in `/mnt/antweb` (a plain folder on staging) only image backups older than 14 days (`images/backup`) and upload backups older than 30 days (`web/bak`), and only while `/mnt/antweb` is not a mounted bucket.

**Weekly cleanup (since 2026-10-24).** Staging's disk filled twice in a week (an 11 GB log on 10-22; on 10-24 image and upload backups, build cache and the count check's temporary tables). Install once, on staging only:

```
# ssh root@165.232.136.170   (staging)
install -m 644 /root/antweb-next/tools/staging/staging-cleanup.cron /etc/cron.d/staging-cleanup && cat /etc/cron.d/staging-cleanup | tail -1
```

Expect the cron line (Sundays 02:00). Log: `journalctl -t staging-cleanup`. Remove: `rm /etc/cron.d/staging-cleanup`.

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

## Drop the old unused tables (2026-10-23)

Brian's yes of 2026-10-22: drop the 14 old tables and 1 view that nothing reads (`docs/data-model.md` section 13, "Dropped already"), each backed up first, and widen `upload.group_name` from varchar(64) to varchar(100) (the width of `ant_group.name`). The SQL is `db/upgrade/8.115/2026-10-23-drop-old-tables-widen-group-name.sql` (pull request #249). The objects: tables `afro_types_tmp`, `artist_copy`, `description`, `favorite_images`, `groups_project`, `homepage`, `image_backup`, `image_count`, `image_count_materialized`, `junkspec`, `proj_taxonT` (capital T), `similar_species`, `synonymy`, `temp_image_artist`; view `darwin_core_2_test`.

**Order.** Staging, then production, on the same day (the weekly staging refresh copies production's database to staging, so a staging-only drop would come back). On production: archive (a), run the file (b) and check (c) first, then Claude merges the pull request (it deploys by itself), then the structure check (c2). Uploads are safe in either order: the new code cuts a group name to the column's real width (read on every upload: 64 before (b), 100 after), and the old code works with the wider column. Running (b) first keeps the time in which the repository and production differ short: between (b) and the deploy, and in the other order until (b) runs, the count check page (`countCheck.do`, "Database structure") lists the 15 objects as removed and `upload.group_name` as retyped; nothing else shows it. Do it after 08:15 Pacific (not during the nightly run) and not while an upload is running: the ALTER holds the `upload` table for a moment.

Commands (b) and staging's (c2) fetch the pull request's files from GitHub into git's FETCH_HEAD (`git fetch ... pull/249/head`). This changes no file in the checkout, and it works before and after the merge. (b) copies the SQL file to `/root/drop-old-tables-20261023.sql` and runs it only if its SHA-256 is the one reviewed (pinned in the command).

### Old tables: staging

**a1. Look before changing anything** (read-only).

```
# ssh root@165.232.136.170   (staging)
F=/root/antweb-next/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "select table_name, table_type, table_rows, round((data_length + index_length) / 1048576, 1) as mb from information_schema.tables where table_schema = database() and table_name in ('afro_types_tmp','artist_copy','description','favorite_images','groups_project','homepage','image_backup','image_count','image_count_materialized','junkspec','proj_taxonT','similar_species','synonymy','temp_image_artist','darwin_core_2_test') order by table_name; select table_type, count(*) as n from information_schema.tables where table_schema = database() group by table_type; select column_type, is_nullable, character_set_name, collation_name, column_default, column_comment from information_schema.columns where table_schema = database() and table_name = 'upload' and column_name = 'group_name'; select max(char_length(group_name)) as longest_group_name from upload; select table_name as other_view_using_them from information_schema.views where table_schema = database() and table_name <> 'darwin_core_2_test' and view_definition regexp concat(char(96 using utf8mb4), 'ant', char(96 using utf8mb4), '[.]', char(96 using utf8mb4), '(afro_types_tmp|artist_copy|description|favorite_images|groups_project|homepage|image_backup|image_count|image_count_materialized|junkspec|proj_taxonT|similar_species|synonymy|temp_image_artist)', char(96 using utf8mb4)); select table_name, referenced_table_name from information_schema.key_column_usage where table_schema = database() and referenced_table_name in ('afro_types_tmp','artist_copy','description','favorite_images','groups_project','homepage','image_backup','image_count','image_count_materialized','junkspec','proj_taxonT','similar_species','synonymy','temp_image_artist'); select trigger_name, event_object_table from information_schema.triggers where trigger_schema = database() and event_object_table in ('afro_types_tmp','artist_copy','description','favorite_images','groups_project','homepage','image_backup','image_count','image_count_materialized','junkspec','proj_taxonT','similar_species','synonymy','temp_image_artist'); select definer as view_definer from information_schema.views where table_schema = database() and table_name = 'darwin_core_2_test'; show grants;"; df -h /mnt/backup
```

Expect: 15 rows (14 `BASE TABLE`, 1 `VIEW`) with their sizes in MB; the number of tables and views (note it: after (b) there are 14 tables and 1 view fewer); `varchar(64) NO utf8mb3 utf8mb3_general_ci NULL` and an empty comment (the ALTER in (b) writes the whole column definition, so a default or comment would be lost); a longest group name of 64 or less; nothing under `other_view_using_them` (a view that reads one of the tables, written `ant`.`name` in its definition; until 2026-10-23 the check also matched columns of the same name, such as `s`.`image_count` in the API views), no foreign key and no trigger lines; the view's definer; the login's grants; free space on `/mnt/backup` well above the total MB. Stop and paste the output to Claude if the column is not `utf8mb3` / `utf8mb3_general_ci`, if it has a default or a comment, if a view, key or trigger is listed, or if the grants are not `ALL PRIVILEGES ON ant.*` (or more).

**a2. Archive the 15 objects.** On staging `/mnt/backup` is a plain folder on staging's own disk (not production's backup bucket); the archive goes to `/mnt/backup/archive` there too. The command refuses to overwrite an archive that exists.

```
# ssh root@165.232.136.170   (staging)
F=/root/antweb-next/etc/db.properties; A=/mnt/backup/archive/old_tables_20261023.sql.gz; install -d -m 700 /mnt/backup/archive && test ! -e "$A" && (set -o pipefail; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysqldump -u "$(sed -n 's/^user=//p' "$F")" --single-transaction --no-tablespaces --skip-triggers --default-character-set=utf8mb4 ant afro_types_tmp artist_copy description favorite_images groups_project homepage image_backup image_count image_count_materialized junkspec proj_taxonT similar_species synonymy temp_image_artist darwin_core_2_test | gzip -c > "$A") && gzip -t "$A" && ls -l "$A" && zgrep -c '^CREATE TABLE' "$A" && zgrep -c 'VIEW `darwin_core_2_test` AS' "$A" && gunzip -c "$A" | tail -1
```

Expect: the file with its size, then `14` (one `CREATE TABLE` per table), `2` (mysqldump writes a stand-in for the view, then its definition) and `-- Dump completed on ...`. These numbers were checked with mysqldump 8.0; staging runs 8.4 like production, so staging's output is the check for production: if it differs, paste it to Claude before going on. Anything else (an error, a number other than 14 and 2, no "Dump completed"): stop, do not run (b), and paste the output to Claude. Nothing in the database has changed. A failed archive stays on disk and the command refuses to overwrite it: before running a2 again (and only if (b) has not run), remove it with `rm /mnt/backup/archive/old_tables_20261023.sql.gz`.

**b. Run the upgrade file.**

```
# ssh root@165.232.136.170   (staging)
F=/root/antweb-next/etc/db.properties; U=/root/drop-old-tables-20261023.sql; test -s /mnt/backup/archive/old_tables_20261023.sql.gz && git -C /root/antweb-next fetch -q origin pull/249/head && git -C /root/antweb-next show FETCH_HEAD:db/upgrade/8.115/2026-10-23-drop-old-tables-widen-group-name.sql > "$U" && echo "2b4a6334c0f5ddbfb2371728fb4bb707bcdefaddc5af33b670e8584045ed01ca  $U" | sha256sum -c && docker exec -i -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant < "$U" && echo DONE
```

Expect `/root/drop-old-tables-20261023.sql: OK` (the file is the reviewed one: its SHA-256 is pinned here), then `DONE`. `FAILED` there means the file on GitHub changed after review: nothing was run; stop and tell Claude. If an error mentions `SYSTEM_USER` or `DEFINER`, the view could not be dropped (it is the last statement: the column and the 14 tables are done); paste it to Claude. Any other error: paste it to Claude; the file can be run again.

**c. Check.**

```
# ssh root@165.232.136.170   (staging)
F=/root/antweb-next/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "select count(*) as old_objects_left from information_schema.tables where table_schema = database() and table_name in ('afro_types_tmp','artist_copy','description','favorite_images','groups_project','homepage','image_backup','image_count','image_count_materialized','junkspec','proj_taxonT','similar_species','synonymy','temp_image_artist','darwin_core_2_test'); select table_type, count(*) as n from information_schema.tables where table_schema = database() group by table_type; show columns from upload like 'group_name'; select character_set_name, collation_name from information_schema.columns where table_schema = database() and table_name = 'upload' and column_name = 'group_name';"
```

Expect `old_objects_left` 0; 14 tables and 1 view fewer than in a1; `group_name varchar(100) NO`; `utf8mb3 utf8mb3_general_ci`.

**c2. Compare the whole structure with the repository's new snapshot** (read-only; the export command of `docs/data-model.md`).

```
# ssh root@165.232.136.170   (staging)
F=/root/antweb-next/etc/db.properties
sed -n 's/^password=//p' "$F" | docker exec -i antweb_mysql_1 sh -c 'IFS= read -r p; MYSQL_PWD="$p" exec mysql -B -N -u "$1" ant -e "SELECT c.table_name, c.column_name, c.column_type, t.table_type FROM information_schema.columns c JOIN information_schema.tables t USING (table_schema, table_name) WHERE c.table_schema = DATABASE() ORDER BY c.table_name, c.ordinal_position"' sh "$(sed -n 's/^user=//p' "$F")" > /root/live-schema.tsv
git -C /root/antweb-next fetch -q origin pull/249/head && git -C /root/antweb-next show FETCH_HEAD:doc/schema/live-schema-2026-10-23.tsv | diff - /root/live-schema.tsv; echo "diff exit $?"
```

Staging's database is a copy of production from its last weekly refresh, so a few lines may differ (dated backup tables, names with `_bak_`, `_backup_`, `_deleted_` or a date at the end, and anything changed on production since that copy). None of the 15 objects and no `upload group_name` line should appear. Paste the output to Claude.

### Old tables: production

**a1. Look before changing anything** (read-only).

```
# ssh root@165.232.137.86   (antweb-prod)
F=/root/antweb/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "select table_name, table_type, table_rows, round((data_length + index_length) / 1048576, 1) as mb from information_schema.tables where table_schema = database() and table_name in ('afro_types_tmp','artist_copy','description','favorite_images','groups_project','homepage','image_backup','image_count','image_count_materialized','junkspec','proj_taxonT','similar_species','synonymy','temp_image_artist','darwin_core_2_test') order by table_name; select table_type, count(*) as n from information_schema.tables where table_schema = database() group by table_type; select column_type, is_nullable, character_set_name, collation_name, column_default, column_comment from information_schema.columns where table_schema = database() and table_name = 'upload' and column_name = 'group_name'; select max(char_length(group_name)) as longest_group_name from upload; select table_name as other_view_using_them from information_schema.views where table_schema = database() and table_name <> 'darwin_core_2_test' and view_definition regexp concat(char(96 using utf8mb4), 'ant', char(96 using utf8mb4), '[.]', char(96 using utf8mb4), '(afro_types_tmp|artist_copy|description|favorite_images|groups_project|homepage|image_backup|image_count|image_count_materialized|junkspec|proj_taxonT|similar_species|synonymy|temp_image_artist)', char(96 using utf8mb4)); select table_name, referenced_table_name from information_schema.key_column_usage where table_schema = database() and referenced_table_name in ('afro_types_tmp','artist_copy','description','favorite_images','groups_project','homepage','image_backup','image_count','image_count_materialized','junkspec','proj_taxonT','similar_species','synonymy','temp_image_artist'); select trigger_name, event_object_table from information_schema.triggers where trigger_schema = database() and event_object_table in ('afro_types_tmp','artist_copy','description','favorite_images','groups_project','homepage','image_backup','image_count','image_count_materialized','junkspec','proj_taxonT','similar_species','synonymy','temp_image_artist'); select definer as view_definer from information_schema.views where table_schema = database() and table_name = 'darwin_core_2_test'; show grants;"; findmnt /mnt/backup
```

Expect as on staging; tables and views: 97 and 8 on 2026-10-22 (more tables if dated backups were made since). `findmnt` must show `/mnt/backup` as the rclone mount (`fuse.rclone`), so the archive goes to the Spaces bucket `antweb-dbarchive`. Stop on the same conditions as on staging.

**a2. Archive the 15 objects** to `/mnt/backup/archive/old_tables_20261023.sql.gz` (Spaces bucket `antweb-dbarchive`).

```
# ssh root@165.232.137.86   (antweb-prod)
F=/root/antweb/etc/db.properties; A=/mnt/backup/archive/old_tables_20261023.sql.gz; install -d -m 700 /mnt/backup/archive && test ! -e "$A" && (set -o pipefail; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysqldump -u "$(sed -n 's/^user=//p' "$F")" --single-transaction --no-tablespaces --skip-triggers --default-character-set=utf8mb4 ant afro_types_tmp artist_copy description favorite_images groups_project homepage image_backup image_count image_count_materialized junkspec proj_taxonT similar_species synonymy temp_image_artist darwin_core_2_test | gzip -c > "$A") && gzip -t "$A" && ls -l "$A" && zgrep -c '^CREATE TABLE' "$A" && zgrep -c 'VIEW `darwin_core_2_test` AS' "$A" && gunzip -c "$A" | tail -1
```

Expect `14`, `2` and `-- Dump completed on ...`, as on staging. Otherwise stop; nothing has changed (a failed archive: remove it as on staging before trying again, only if (b) has not run).

**b. Run the upgrade file** (before the merge; see "Order").

```
# ssh root@165.232.137.86   (antweb-prod)
F=/root/antweb/etc/db.properties; U=/root/drop-old-tables-20261023.sql; test -s /mnt/backup/archive/old_tables_20261023.sql.gz && git -C /root/antweb fetch -q origin pull/249/head && git -C /root/antweb show FETCH_HEAD:db/upgrade/8.115/2026-10-23-drop-old-tables-widen-group-name.sql > "$U" && echo "2b4a6334c0f5ddbfb2371728fb4bb707bcdefaddc5af33b670e8584045ed01ca  $U" | sha256sum -c && docker exec -i -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant < "$U" && echo DONE
```

Expect `/root/drop-old-tables-20261023.sql: OK`, then `DONE` (`FAILED` or errors: as on staging).

**c. Check.**

```
# ssh root@165.232.137.86   (antweb-prod)
F=/root/antweb/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "select count(*) as old_objects_left from information_schema.tables where table_schema = database() and table_name in ('afro_types_tmp','artist_copy','description','favorite_images','groups_project','homepage','image_backup','image_count','image_count_materialized','junkspec','proj_taxonT','similar_species','synonymy','temp_image_artist','darwin_core_2_test'); select table_type, count(*) as n from information_schema.tables where table_schema = database() group by table_type; show columns from upload like 'group_name'; select character_set_name, collation_name from information_schema.columns where table_schema = database() and table_name = 'upload' and column_name = 'group_name';"
```

Expect `old_objects_left` 0; 14 tables and 1 view fewer than in a1 (83 and 7 if no dated backup was made since 2026-10-22); `group_name varchar(100) NO`; `utf8mb3 utf8mb3_general_ci`. Then tell Claude: Claude merges #249, and the deploy runs by itself (a few minutes; AntWeb restarts and needs 2 to 4 minutes to load).

**c2. After the deploy: compare the whole structure with the new snapshot** (read-only).

```
# ssh root@165.232.137.86   (antweb-prod)
F=/root/antweb/etc/db.properties
sed -n 's/^password=//p' "$F" | docker exec -i antweb_mysql_1 sh -c 'IFS= read -r p; MYSQL_PWD="$p" exec mysql -B -N -u "$1" ant -e "SELECT c.table_name, c.column_name, c.column_type, t.table_type FROM information_schema.columns c JOIN information_schema.tables t USING (table_schema, table_name) WHERE c.table_schema = DATABASE() ORDER BY c.table_name, c.ordinal_position"' sh "$(sed -n 's/^user=//p' "$F")" > /root/live-schema.tsv
cat /root/antweb/etc/git-commit; diff /root/antweb/doc/schema/live-schema-2026-10-23.tsv /root/live-schema.tsv; echo "diff exit $?"
```

Expect the merged commit, then no diff lines and `diff exit 0` (lines for dated backup tables made since 2026-10-22 may appear; any other line: paste it to Claude). Then open https://www.antweb.org/countCheck.do (admin login): "Database structure" names the snapshot `live-schema-2026-10-23.tsv` and says "No change" (again apart from dated backups, which it leaves out).

### Old tables: rollback

Only if something turns out to need the old tables. Production shown; on staging use `/root/antweb-next/etc/db.properties` and the ssh line `ssh root@165.232.136.170`.

**d1. Restore the 14 tables (with their rows) and the view from the archive.**

```
# ssh root@165.232.137.86   (antweb-prod)
F=/root/antweb/etc/db.properties; A=/mnt/backup/archive/old_tables_20261023.sql.gz; gzip -t "$A" && gunzip -c "$A" | docker exec -i -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql --default-character-set=utf8mb4 -u "$(sed -n 's/^user=//p' "$F")" ant && echo RESTORED
```

Expect `RESTORED`. An error about `DEFINER`, `SET_USER_ID` or `SYSTEM_USER` concerns only the view (it comes last in the archive; the tables are back): the view is not needed by anything. The count check then lists the restored objects as added, until the snapshot is set back (a pull request: Claude).

**d2. Before narrowing `upload.group_name` back to 64:** check that no longer name was stored since (read-only).

```
# ssh root@165.232.137.86   (antweb-prod)
F=/root/antweb/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "select count(*) as longer_than_64 from upload where char_length(group_name) > 64;"
```

**d3. Narrow the column** only if d2 gave 0 (the code reads the column's width on every upload and cuts to it, so it needs no change). With rows longer than 64 MySQL refuses the ALTER ("Data too long") and changes nothing.

```
# ssh root@165.232.137.86   (antweb-prod)
F=/root/antweb/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "ALTER TABLE upload MODIFY group_name varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL; show columns from upload like 'group_name';"
```

Expect `group_name varchar(64) NO`.

Tested 2026-10-22 on a local MySQL 8.0.46 with the 14 tables (production's columns), the view and the `upload` table, as a login with `ALL PRIVILEGES ON ant.*`: a1's queries, the archive (14 and 2, "Dump completed"), the file (twice), c, the restore d1 and the ALTER back d3 (refused while a 81-character name was stored). Dropping the view failed with `SYSTEM_USER` when its definer was root, hence the definer check in a1 and the view last in the file.

## Read-only data checks (decisions of 2026-10-27)

These commands only read: they change nothing and can be run at any time (not during the nightly run, 05:00 to 08:00, to keep the load down). Staging has a copy of production's data from the weekly refresh, so run them there first; the same command on production uses `F=/root/antweb/etc/db.properties` (the database command pattern of the project instructions).

### Upload dates that look wrong

Until 2026-10-27 upload dates were read leniently: "19/10/2013" became 2014-07-10, a day "00" the last day of the month before ("2011-05-00" became 2011-04-30), "2011-02-30" became 2011-03-02. Since then they are read strictly (`docs/flows/specimen-upload.md`, step "Save"). A specimen's stored dates are corrected when its group uploads again (CAS: at the next nightly TaxonWorks upload). This check compares each stored date (`datecollectedstart`, `datecollectedend`, `datedetermined`) with the text typed in the file (`...str`) and counts the ones that do not match it.

```
# ssh root@165.232.136.170   (staging)
F=/root/antweb-next/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "with d as (select code, access_group, 'collected start' as field, trim(datecollectedstartstr) as typed, datecollectedstart as stored_date from specimen where datecollectedstart is not null and datecollectedstartstr is not null union all select code, access_group, 'collected end', trim(datecollectedendstr), datecollectedend from specimen where datecollectedend is not null and datecollectedendstr is not null union all select code, access_group, 'determined', trim(datedeterminedstr), datedetermined from specimen where datedetermined is not null and datedeterminedstr is not null), r as (select d.*, case when cast(stored_date as char) not regexp '^[0-9]{4}-[0-9]{2}-[0-9]{2}' then 'stored value is not a date' when stored_date > curdate() then 'in the future' when typed regexp '^[0-9]{4}[-/]0{1,2}([-/]0{0,2})?\$' then if(month(stored_date) = 1 and day(stored_date) = 1 and year(stored_date) = left(typed, 4), null, 'month 00 not stored as January 1') when typed regexp '^[0-9]{4}[-/][0-9]{1,2}[-/]0{1,2}\$' then if(day(stored_date) = 1 and year(stored_date) = left(typed, 4), null, 'day 00 not stored as the 1st') when typed regexp '^[0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2}\$' then if(year(stored_date) = left(typed, 4) and month(stored_date) = substring_index(substring_index(replace(typed, '/', '-'), '-', 2), '-', -1) and day(stored_date) = substring_index(replace(typed, '/', '-'), '-', -1), null, 'year-month-day not as typed') when typed regexp '^[0-9]{1,2}/[0-9]{1,2}/[0-9]{4}\$' then if(year(stored_date) = right(typed, 4) and ((month(stored_date) = substring_index(typed, '/', 1) and day(stored_date) = substring_index(substring_index(typed, '/', 2), '/', -1)) or (day(stored_date) = substring_index(typed, '/', 1) and month(stored_date) = substring_index(substring_index(typed, '/', 2), '/', -1))), null, 'day/month/year not as typed') when typed regexp '[0-9]{4}' and locate(year(stored_date), typed) = 0 then 'year not in the typed value' else null end as reason from d) select field, reason, count(*) as specimens, count(distinct access_group) as groups_, min(concat(code, ': ', typed, ' -> ', stored_date)) as example from r where reason is not null group by field, reason order by field, reason;"
```

Expect: one line per field and reason with the number of specimens, the number of groups and one example ("code: typed -> stored"), or nothing at all when every stored date matches its text. To see the specimens of one reason, run the same command with the last `select` changed to `select field, reason, access_group, code, typed, stored_date from r where reason is not null order by field, reason, access_group, code limit 500;`. Paste the output to Claude: the fix is a new upload by the group (or the nightly for CAS), not an edit of the rows.

### Specimens with an empty name and the set 2 clean-up

Set 2 starts with a clean-up (`UtilDataAction` action `dataCleanup`: `TaxonDb.deleteSpeciesWithoutSpecimenOrAntcatSource` and `deleteGeneraWithoutSpecimenOrAntcatSource`) that deletes species, subspecies and genus rows of the `taxon` table that are not in the AntCat list (worldants) and have no specimen. It used `taxon_name not in (select taxon_name from specimen)` (genera: `(subfamily, genus) not in (select subfamily, genus from specimen)`), which deletes nothing at all as soon as one specimen has a NULL name (or a NULL subfamily or genus); so did four other cleanups (morphospecies list rows, and after each upload the group's orphan names and indets without specimens). Brian Fisher, 2026-10-27: count first, then decide. 2026-10-29: make them safe with blank names and turn them back on (`home/BlankNameCleanup`, [flows/nightly.md](flows/nightly.md) set 2): the first nightly run after the deploy only previews (compute.log, lines starting "blank-name-safe cleanup preview (2026-10-29)": per cleanup the rows the old and the blank-safe rule delete now, with examples); the run after it deletes. To keep previewing, see "Blank-name clean-up kept in preview" under Switches. This check counts the specimens with a NULL or empty name, subfamily or genus, the rows the clean-up deletes tonight (NOT IN) and the rows the corrected rule (NOT EXISTS, NULL-safe for genera) would delete.

```
# ssh root@165.232.136.170   (staging)
F=/root/antweb-next/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "select (select count(*) from specimen where taxon_name is null) as specimens_null_name, (select count(*) from specimen where taxon_name = '') as specimens_empty_name, (select count(*) from specimen where subfamily is null or genus is null) as specimens_null_subfamily_or_genus, (select count(*) from taxon t where t.taxarank in ('species', 'subspecies') and t.taxon_name not in (select taxon_name from proj_taxon where project_name = 'worldants') and t.taxon_name not in (select taxon_name from specimen)) as species_deleted_now, (select count(*) from taxon t where t.taxarank in ('species', 'subspecies') and not exists (select 1 from proj_taxon p where p.project_name = 'worldants' and p.taxon_name = t.taxon_name) and not exists (select 1 from specimen s where s.taxon_name = t.taxon_name)) as species_deleted_corrected, (select count(*) from taxon t where t.taxarank = 'genus' and t.taxon_name not in (select taxon_name from proj_taxon where project_name = 'worldants') and (t.subfamily, t.genus) not in (select subfamily, genus from specimen)) as genera_deleted_now, (select count(*) from taxon t left join (select distinct subfamily, genus, 1 as hit from specimen) sg on sg.subfamily <=> t.subfamily and sg.genus <=> t.genus where t.taxarank = 'genus' and sg.hit is null and not exists (select 1 from proj_taxon p where p.project_name = 'worldants' and p.taxon_name = t.taxon_name)) as genera_deleted_corrected\G select taxarank, status, src, count(*) as names, min(taxon_name) as example from (select t.taxarank, t.status, left(t.source, 30) as src, t.taxon_name from taxon t where t.taxarank in ('species', 'subspecies') and not exists (select 1 from proj_taxon p where p.project_name = 'worldants' and p.taxon_name = t.taxon_name) and not exists (select 1 from specimen s where s.taxon_name = t.taxon_name) union all select t.taxarank, t.status, left(t.source, 30), t.taxon_name from taxon t left join (select distinct subfamily, genus, 1 as hit from specimen) sg on sg.subfamily <=> t.subfamily and sg.genus <=> t.genus where t.taxarank = 'genus' and sg.hit is null and not exists (select 1 from proj_taxon p where p.project_name = 'worldants' and p.taxon_name = t.taxon_name)) x group by taxarank, status, src order by names desc limit 50;"
```

Expect: first block, seven numbers. If `specimens_null_name` (or `specimens_null_subfamily_or_genus` for genera) is above 0, the `_now` numbers are 0 (the clean-up deletes nothing tonight) and the `_corrected` numbers are the backlog the corrected rule would delete at once. Then up to 50 lines: the names the corrected rule would delete, by rank, status and source, with one example each. Paste the output to Claude; nothing is deleted until Brian decides.

**Specimens with a blank name, by group** (read-only, 2026-10-29): the specimens whose taxon name, subfamily, genus or species is NULL or empty, per upload group, with one example code each. A blank species alone is usual for old lines identified only to genus.

```
# ssh root@165.232.137.86   (antweb-prod)
F=/root/antweb/etc/db.properties; docker exec -i -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant <<'SQL'
select s.access_group, g.name as group_name, count(*) as specimens,
  sum(s.taxon_name is null) as name_null, sum(s.taxon_name is not null and trim(s.taxon_name) = '') as name_empty,
  sum(s.subfamily is null or trim(s.subfamily) = '') as subfamily_blank, sum(s.genus is null or trim(s.genus) = '') as genus_blank,
  sum(s.species is null or trim(s.species) = '') as species_blank, min(s.code) as example_code
from specimen s left join ant_group g on g.id = s.access_group
where s.taxon_name is null or trim(s.taxon_name) = '' or s.subfamily is null or trim(s.subfamily) = ''
   or s.genus is null or trim(s.genus) = '' or s.species is null or trim(s.species) = ''
group by s.access_group, g.name order by specimens desc;
select count(*) as all_specimens, sum(taxon_name is null or trim(taxon_name) = '') as blank_name,
  sum(subfamily is null or trim(subfamily) = '' or genus is null or trim(genus) = '') as blank_subfamily_or_genus,
  sum(species is null or trim(species) = '') as blank_species
from specimen;
SQL
```

Expect: one line per group that has such specimens (group number and name, the specimens, how many have each blank, an example code), then one line of totals. `blank_name` and `blank_subfamily_or_genus` above 0 are what stopped the old clean-ups.

## Alerts

All alerts email bfisher@calacademy.org.

| Alert | What it usually means | What to do |
|---|---|---|
| CPU above 90% for 1 hour (antweb-prod) | Between 05:00 and 08:00: the nightly sets (expected heavy load). Otherwise a crawler burst or a stuck Java thread. | Common checks. Outside the nightly window: `docker stats --no-stream`; `tail /var/log/antweb-watchdog.log` (the watchdog restarts a stuck thread within the hour). If it persists, restart (above). |
| Memory above 90% for 30 minutes | Java heap growth (Java may use up to 40% of RAM), MySQL, or many API bulk requests (API is capped at 1 GB). | `free -h; docker stats --no-stream`; `grep "heap used" /root/antweb/logs/web/compute.log \| tail -5` shows the heap at the end of each nightly run. Restart AntWeb at a quiet time. If it recurs, `nightlyRestart=true` (Switches) brings back the daily restart. |
| Disk above 85% for 30 minutes | Logs, Docker images or build cache, journal. | "Disk full" below. |
| "Nightly: Scheduler FAILED in set N" (admin alert, and an email from antweb@antweb.org "tonight's nightly run stopped (a set failed)", since 2026-10-27) | A nightly set failed: its open work was rolled back and the sets after it did not run, so that night's counts are not updated (compute.log "Scheduler FAILED in set N ... : <reason>"). | Read compute.log (Log Manager) and the Tomcat log around the time (`docker logs antweb_antweb_1 --since 6h 2>&1 \| grep -i -B2 -A20 "handleException"`). The next nightly (05:00) tries again; to redo the missing sets the same day, an admin runs them one at a time with `schedule.do?action=run&num=N` (N from the failed set to 5) after 08:15. Paste the reason to Claude if it repeats. |
| "Log size check" (email from antweb@antweb.org and an admin alert, after the nightly run) | A log file over 1 GB, or the disk over 85%. | "A log grows too big" (Logs, above). |
| "the CAS upload used an older TaxonWorks archive" (email from antweb@antweb.org and an admin alert, after the 05:00 nightly) | TaxonWorks did not build a new archive within 30 minutes of the nightly's request, so the upload used the previous day's archive: edits made in TaxonWorks since then arrive with the next run. | Usually nothing. If it repeats, check TaxonWorks (the archive link in `etc/taxonworks.properties`) or ask its developers. |
| "Storage mount problem" (email from antweb@antweb.org, hourly check `MountCheck`, at once and every 6 hours while it lasts; one "working again" email) | `/mnt/antweb` cannot be written and read back (file `web/log/specimen/mountcheck-<env>.txt`), or (production) the daily dump's status line `logs/web/db-dump-status.txt` (written by `bin/db-daily-dump.sh`; the app cannot read the root-only `/mnt/backup`) says failed, is over 26 hours old or names a dump under 1 MB. | "Storage mounts (/mnt/antweb, /mnt/backup)" (Emergencies, below). |
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

- `bin/reboot_checker.sh` (host cron, every 5 minutes) asks the application on `http://127.0.0.1:8081/util.do?action=isRestart`. It restarts the container when the answer is `true` or the application's error page (`case#`) comes back. The application answers `true` when set 1 of the nightly run has been running for 210 minutes, when an admin asked for a restart, or (only with `nightlyRestart=true`) after the nightly run; never while an upload is running (an image upload counts until it is finished or 10 minutes pass without a new file). Before restarting it also asks `util.do?action=isBusy` and waits for its next run while an upload or a background job is running (this also covers the error page `case#`); a data job or the nightly run does not stop it, so a stuck set 1 is still restarted. `--dry-run` shows both answers. An empty answer (application down) is logged and nothing is done. Check without acting: `/root/antweb/bin/reboot_checker.sh --dry-run`.
- `/root/antweb-watchdog.sh` (root crontab, hourly at :15; not in git) takes two thread CPU samples 30 seconds apart; if one thread is above 95% in both, the container has been up more than 12 hours and there was no watchdog restart in the last 6 hours, it saves a thread dump and restarts the container. Log `/var/log/antweb-watchdog.log`.

Why did the container restart? `grep -h "restart" /root/antweb/logs/detail/rebootCheck.log | tail -3; tail -5 /var/log/antweb-watchdog.log; journalctl -t weekly-reboot --no-pager | tail -2`, and the Deploy runs in GitHub Actions.

## Switches

The files are in `etc/` of the project folder, not tracked by git, read-only for the application. Each file must be readable by group 2000: create it with the commands below. The application re-reads `editor`, `upload-email`, `taxonworks`, `mail`, `turnstile` and `cleanup` within a minute; `scheduler.properties` at the end of the next nightly run.

**Blank-name clean-up kept in preview** (2026-10-29; `home/BlankNameCleanup`): the clean-ups keep their old rules and set 2 writes the preview to compute.log every night. Remove the file to let the blank-safe rules delete from the next run; `blankNameCleanup=on` starts them at once.

```
# ssh root@165.232.137.86   (antweb-prod)
cd /root/antweb/etc && printf 'blankNameCleanup=preview\n' > cleanup.properties && chgrp 2000 cleanup.properties && chmod 640 cleanup.properties && cat cleanup.properties
```

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

## One-off data repairs

### Locality codes stored URL-encoded

Found 2026-10-22: until then the specimen upload stored every locality code URL-encoded (`Ahi_<5m` as
`Ahi_%3C5m`, `KM55""805` as `KM55%22%22805`, `MontagnedesFrançais_150` as `MontagnedesFran%E7ais_150`):
1,117 specimens in 67 codes on production (Economo Ant Lab 921 in 18 codes, California Academy of Sciences 196 in
49). The upload now stores codes as written (`docs/flows/specimen-upload.md`, section 3b). The old rows are fixed by:

- **CAS (group 1): nothing to do.** The nightly TaxonWorks upload replaces all CAS specimens every night, so the
  first nightly run after the fix is deployed stores the CAS codes plain. Check the next day (below). A code whose
  original had a character outside ISO-8859-1 (a curly apostrophe, stored as `%3F`) comes back with that character.
- **Every other group (Economo Ant Lab): this repair**, or the group's next upload, whichever comes first. The
  repair decodes the stored codes; it cannot restore a character that was stored as `%3F`.

Run after the code fix is deployed (the repaired codes need the new lookups), staging first, outside 05:00 to
08:00 Pacific and not while the group is uploading. The tool is `tools/data/locality_codes.py` (read its header).

1. Preview (read-only). Lists each stored code with its decoded value, group, specimen count and locality names,
   and marks it `ok`, `merge` (the decoded code already belongs to specimens of the same group and locality name:
   after the repair they share one locality page) or `collision` (the decoded code belongs to another group or
   another locality name, ignoring case and accents as MySQL does: left out of the repair, decide by hand).

```
# ssh root@165.232.136.170   (staging)
cd /root/antweb-next && python3 tools/data/locality_codes.py preview --project /root/antweb-next --out /root/loccode-preview-20261022.tsv
```

   Expect a summary (`N stored codes (per group), N specimens`, counts per status and per group) and one line per
   code. Staging holds production's data as of its last refresh, so the numbers should be close to 67 and 1,117.
   Send the output to Claude before going on if any line says `collision`.

2. Write the repair SQL, leaving out CAS (no database access):

```
# ssh root@165.232.136.170   (staging)
cd /root/antweb-next && python3 tools/data/locality_codes.py sql --preview /root/loccode-preview-20261022.tsv --skip-group 1 --out /root/loccode-repair-20261022.sql
```

   Expect `Repair SQL written ...: N stored codes, N specimens` (Economo: about 18 codes, 921 specimens), the
   codes left out, and the rollback statement.

3. Run it. The script first copies `code, access_group, localitycode` of the affected specimens to
   `specimen_localitycode_bak_20261022` (it stops there with "already exists" if the repair ran before), then
   updates each code in one transaction.

```
# ssh root@165.232.136.170   (staging)
F=/root/antweb-next/etc/db.properties; docker exec -i -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant < /root/loccode-repair-20261022.sql
```

   Expect four small results (a heading line and a value line each; `check_counts` says `counts match`): `backed_up` equal to `expected`, `changed` equal to `expected`, and
   `still_encoded_in_repaired_groups` 0 (or the number of specimens of collisions left out in those groups).
   `backed_up` can be larger than `expected` when a code left out as a collision differs from a repaired one only in
   upper and lower case (MySQL compares them as equal); those extra rows are only copied, not changed.
   If `changed` differs from `expected` the script stops with "Subquery returns more than 1 row" before its
   commit, and nothing is changed. Send the output to Claude; to run it again, first
   `drop table specimen_localitycode_bak_20261022;` (the backup table is made before the transaction).

4. Check: the Integrity Check query `query.do?name=encodedLocalityCodes` lists no Economo codes any more, and a
   repaired code opens its locality page with its specimens, for example `locality.do?code=KM55%22%22805`
   (the code `KM55""805`). Map popups of the group link the old code until the next nightly run rebuilds the maps.

5. Then production: the same three commands with `ssh root@165.232.137.86`, `cd /root/antweb`,
   `--project /root/antweb` and `F=/root/antweb/etc/db.properties`.

Rollback (puts every changed code back, from the backup table). Only before the group uploads again: after an
upload it would put the old encoded codes over the new plain ones.

```
# ssh root@165.232.137.86   (antweb-prod; on staging use /root/antweb-next/etc/db.properties)
F=/root/antweb/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "update specimen s join specimen_localitycode_bak_20261022 b on b.code = s.code and b.access_group = s.access_group set s.localitycode = b.localitycode;"
```

The day after the deploy, check CAS: `query.do?name=encodedLocalityCodes` (or the Integrity Check) should list no
California Academy of Sciences codes once a nightly TaxonWorks upload has run (`taxonworksNightly.log`). Keep the
backup table until the codes have been checked; drop it later (`drop table specimen_localitycode_bak_20261022;`).

### Orphan photos

Orphan photos are photos whose specimen code has no specimen record, so they show on no page (`orphanedImages.do`
lists them). Curators decided, code by code, to delete them (a duplicate) or to move them to the code of the right
specimen. The tool `tools/data/orphan_photos.py` (read its header) carries out a decisions file: for the CAS list of
2026-10 that is `tools/data/orphan_photos_cas_2026-10.tsv` (17 codes to delete, 11 to move; the other 25 codes of
the list stay as they are). The other groups' lists get their own decisions file and the same commands:
casent0005505 (refused on production 2026-10-28: Michele had uploaded mcz1603635w with the same photos at full size) is in `tools/data/orphan_photos_cas_2026-10b.tsv` as a `merge`, run with `--tag c`. EcoFoG's is `tools/data/orphan_photos_ecofog_2026-10.tsv` (Jerome Orivel's answers of 2026-10-28: 147 duplicates
to delete, 25 of them uploaded under the CAS account in 2018-10, so group 1 for those; preview expects
`Accepted: 148 codes to delete` (147 plus the merge's profile) and `1 to move`). Jerome uploads the records of 26 more codes himself; their photos then show and
need nothing. ecofog-hc-0002 (same specimen as ecofog-hc-0002-01, the same photos) is a `merge`: the target has a
profile photo already, so the code's profile is deleted and its head and dorsal photos are moved (preview: `MERGE into
ecofog-hc-0002-01`). A `merge` line works like `move` for a target that has photos: shots the target has are deleted
as duplicates, the others moved; nothing of the target changes.

What it changes: the photo rows (`image`, with their `group_image` and `image_like` rows) and the files in
`/mnt/antweb/images/<code>/` (the four sizes thumbview, low, med, high and the original .tif or .jpg). A moved photo
gets the target's name in every size (`casent0005505_h_1_high.jpg` becomes `mcz1603635w_h_1_high.jpg`, the original
`CASENT0005505_H_1.tif` becomes `MCZ1603635W_H_1.tif`). The upload history (`image_uploaded`) is kept as it is. The
target's specimen page shows the photos at once; taxon, place and home page image numbers follow at the next nightly
run (set 4).

It refuses a code whose state differs from the decision: a code to delete that has a specimen record now, a target
with no record or with photos already, photos of another group. Refused codes are left alone by every step.

Staging's `/mnt/antweb` is a plain folder that most likely does not hold these photos: there the preview says "no
files found" and only the rows change. That is expected and still tests the database part.

Run outside 05:00 to 08:00 Pacific (the nightly run). The tool must be in the checkout (deployed to the server).

1. Preview (read-only). One block per code: `DELETE`, `MOVE to <code>`, `REFUSED (...)` with the reason, or
   `NOTHING TO DO`; its rows (shots and image ids) and its files. Add `--files` to list every file.

```
# ssh root@165.232.136.170   (staging)
cd /root/antweb-next && python3 tools/data/orphan_photos.py preview --project /root/antweb-next --decisions tools/data/orphan_photos_cas_2026-10.tsv
```

   Expect `Accepted: 17 codes to delete (72 rows), 11 to move (45 rows)` (the numbers of the list of 2026-10-24) and no
   `REFUSED`. Send the output to Claude before going on if a code is refused or the numbers differ.

2. Backup. Copies every file that will change to `/mnt/backup/archive/orphan_photos_<date>/images/<code>/` and the
   rows to tables `orphan_photos_bak_<date>_image`, `_group_image`, `_image_like`, `_image_uploaded`, `_specimen`
   (also as TSV files in the `db/` folder next to the images); checks every file size and row count.

```
# ssh root@165.232.136.170   (staging)
cd /root/antweb-next && python3 tools/data/orphan_photos.py backup --project /root/antweb-next --decisions tools/data/orphan_photos_cas_2026-10.tsv
```

   Expect four small checks that each say `ok` (`image_rows 117`, `group_image_rows`, `image_like_rows`,
   `specimen_rows 11`), one `copied` line per file, and `Backup done: ... Manifest: .../manifest.json`.

3. Run. It only starts with a backup of the same photos made in the last 24 hours, shows the SQL, and asks you to
   type `yes`. The rows change in one transaction: a count that differs stops it before the commit, and nothing
   changes. Then the files are deleted or renamed (log: `run.log` in the backup folder). If a file fails, run the
   same command again: it only finishes the files.

```
# ssh root@165.232.136.170   (staging)
cd /root/antweb-next && python3 tools/data/orphan_photos.py run --project /root/antweb-next --decisions tools/data/orphan_photos_cas_2026-10.tsv
```

   Expect the check lines with `ok`, `committed`, one line per file (`deleted ...`, `moved ... -> ...`) and
   `Done: 17 codes deleted, 11 moved`.

4. Check one moved code and one deleted code. The first query should list 4 rows of
   `mcz1603635w` and 4 of `mcz-ent00021675`, none of the old codes; the second `image_count` 4 for both.

```
# ssh root@165.232.136.170   (staging)
F=/root/antweb-next/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "select image_of_id, shot_type, shot_number from image where image_of_id in ('casent0005505', 'mcz1603635w', 'mczent00021675', 'mcz-ent00021675', 'anic32-066597') order by 1, 2, 3; select code, image_count from specimen where code in ('mcz1603635w', 'mcz-ent00021675');"
ls /mnt/antweb/images/mcz1603635w/ /mnt/antweb/images/anic32-066597/
```

   On staging `ls` most likely says "No such file or directory" for both (no files there). Then open
   https://staging.antweb.org/specimen.do?code=mcz1603635w (the photo boxes show; the pictures themselves only where
   the files exist) and https://staging.antweb.org/orphanedImages.do (none of the 28 codes listed).

5. Then production: the same four blocks with `ssh root@165.232.137.86`, `cd /root/antweb`, `--project /root/antweb`
   and `F=/root/antweb/etc/db.properties`. On production `ls` should list the files of `mcz1603635w` (four sizes per photo and the originals) and say
   "No such file or directory" for `anic32-066597`, and https://www.antweb.org/specimen.do?code=mcz1603635w shows
   the four photos.

Rollback (puts the rows and files back from the backup; the newest backup of the decisions file is used):

```
# ssh root@165.232.137.86   (antweb-prod; on staging: cd /root/antweb-next and --project /root/antweb-next)
cd /root/antweb && python3 tools/data/orphan_photos.py rollback --project /root/antweb --decisions tools/data/orphan_photos_cas_2026-10.tsv
```

   It stops before its commit if an image id was taken by a new upload or a moved row changed since; send the output
   to Claude then. To start again after a backup that stopped half-way, or for a second backup on the same day, add
   `--tag b` to backup, run and rollback (the folder and tables get `_b`). Keep the backup folder and tables until the
   curator has checked the pages; drop the tables later (`drop table orphan_photos_bak_<date>_image, ...`).

### Indet form

Brian Fisher, 2026-10-28: a specimen identified only to genus has one name, `Pheidole (indet)` (in parentheses, so
it sorts first and is never read as a species epithet). Uploads file `indet`, `Indet`, `indet.` and `undet` that way
since 2026-10-28; older uploads left names such as `myrmicinaepheidole indet` next to `myrmicinaepheidole (indet)`.
The tool `tools/data/indet_form.py` (read its header) renames the old form in every table that keys on the name:
taxon, specimen, proj_taxon, geolocale_taxon, bioregion_taxon, museum_taxon, taxon_country, taxon_set_parked,
object_map, the description tables, taxon_prop, favorite_images, similar_species and the dispute tables. Where the
`(indet)` name exists already the two merge: in the taxon and list tables the old row is a duplicate and is deleted
(counts follow at the nightly run); in the other tables a clash stops the run (preview says `REFUSED`).

Three-word names (Brian Fisher, 2026-10-29; a specimen counts at each rank it is identified to, and no further; the
same rules as the upload, `UploadUtil.indetColumns`; "indet" is indet, undet, `(indet)` or sp.):

- `<g> indet <x>` becomes `<g> (indet)`: species indet, identified only to genus, the subspecies value dropped
  (`myrmicinaeocymyrmex indet karooicus`, SAM group 66, becomes `myrmicinaeocymyrmex (indet)`, merged into it).
- `<g> <sp> indet` becomes `<g> <sp>`: identified to species (`ectatomminaegnamptogenys striatula-complex indet`,
  UTIC group 12, becomes `ectatomminaegnamptogenys striatula-complex`). Where that taxon does not exist yet the old
  taxon row is renamed. Its status is what an upload gives the name: `morphotaxon` for a morphospecies name (a digit,
  `-`, `_`, `( )` or `.`, as striatula-complex), otherwise the old status, except `indetermined` (given only for the
  word indet), which becomes `unrecognized`. Preview shows each name's status old -> new.

Renamed taxon and specimen rows get subspecies null (three-word names) and taxon rank `species`. Names of four words
or more are only listed ("Not handled"). The upload files these lines the same way since 2026-10-29 and lists them
under "Name corrected" (docs/flows/specimen-upload.md), so the next upload of the group does not bring them back.
History is not changed: specimen_name_update, the log_detail tables and
`specimen.original_taxon_name` (the name as uploaded, so `x indet` stays there).

Every step refuses on production without `--production`. Run outside 05:00 to 08:00 Pacific. Staging first.

1. Preview (read-only: select statements only). Per table: rows with an old name, rows that clash, rows deleted
   (merge) or REFUSED; then the names old -> new with their taxon and specimen counts and the status the renamed
   rows get (every three-word name; two-word names up to 25, `--all` for every name). On production (2026-10-29)
   expect:

```
  ectatomminaegnamptogenys striatula-complex indet -> ectatomminaegnamptogenys striatula-complex: 1 taxon, 6 specimens; status indetermined -> morphotaxon
  myrmicinaeocymyrmex indet karooicus -> myrmicinaeocymyrmex (indet): 1 taxon, 1 specimens [+ if that taxon exists]; status indetermined -> indetermined
```


```
# ssh root@165.232.136.170   (staging)
cd /root/antweb-next && python3 tools/data/indet_form.py preview --project /root/antweb-next
```

   Send the output to Claude before going on.

2. Run. Backs up every row with an old or new name to tables `indet_form_bak_<date>_<table>` (and the names to
   `indet_form_bak_<date>_names`), checks each copy, asks for `yes`, then renames in one transaction: a count that
   differs stops it before the commit. Then restart the app so its name cache sees the change.

```
# ssh root@165.232.136.170   (staging)
cd /root/antweb-next && python3 tools/data/indet_form.py run --project /root/antweb-next
docker restart antweb_antweb_1
```

   Expect check lines that say `ok`, `committed`, and `Done: N names renamed`. The preview run again
   says `Names in the old form: 0`.

3. Production: the same with `ssh root@165.232.137.86`, `cd /root/antweb`, `--project /root/antweb --production`.

Rollback (one transaction; refuses when rows with these names changed since the run, for example by an upload;
`--force` to go on anyway):

```
# ssh root@165.232.136.170   (staging; on production cd /root/antweb, --project /root/antweb --production)
cd /root/antweb-next && python3 tools/data/indet_form.py rollback --project /root/antweb-next --date YYYYMMDD
docker restart antweb_antweb_1
```

## One-off image exports

### Pheidole major-worker heads for Jack Longino (2026-10-27)

Jack sent a list of 1,517 Pheidole species in 41 clades (`tools/data/pheidole_clades_longino_2026-10-27.csv`). The
tool `tools/export/pheidole_major_heads.py` (read its header) finds the head images (shot type h) of major workers
(caste `worker`, subcaste `major/soldier`, the site's own major/soldier filter) of each species and writes, per
clade, a folder with the medium-size images named `<clade>_<Genus_species>_<specimen code>.jpg`, an `index.csv`
and a contact sheet PDF (4 x 6 tiles per page), then one zip per clade. `index.csv` (all clades: specimen code,
country, adm1, collection code, locality, photographer, image and specimen links) and `not_found.csv` (species with
no AntWeb name or no major-worker head image, with the reason and close AntWeb names) go with the zips.

It runs on staging (a copy of production's data as of its last refresh), reads the database once, and downloads
the images from static.antweb.org, 4 at a time. It uses only the server's own python3; nothing is installed and no
tracked file changes. Run it outside 05:00 to 08:00 Pacific (staging has 4 GB and the nightly run is heavy then);
the database query is at the start, the downloads after it do not touch the database.

1. Get the tool and Jack's list (expect `1518 ... .csv`: 1,517 species plus the header line).

```
# ssh root@165.232.136.170   (staging)
mkdir -p /root/pheidole_tool/tools/export /root/pheidole_tool/tools/data && cd /root/pheidole_tool && B=https://raw.githubusercontent.com/calacademy-research/antweb/refs/heads/tools/pheidole-export && curl -fsS -o tools/export/pheidole_major_heads.py $B/tools/export/pheidole_major_heads.py && curl -fsS -o tools/data/pheidole_clades_longino_2026-10-27.csv $B/tools/data/pheidole_clades_longino_2026-10-27.csv && wc -l tools/data/*.csv
```

2. Dry run: queries the database, writes `index.csv` and `not_found.csv`, prints a table per clade (species
   listed, species with no AntWeb name, species with images, images) and the size estimate and free disk. Nothing
   is downloaded. Send the output to Claude before going on. If it says "NOT ENOUGH" disk, or that no sample image
   answered, stop there.

```
# ssh root@165.232.136.170   (staging)
cd /root/pheidole_tool && nice python3 tools/export/pheidole_major_heads.py --dry-run
```

   Optional: `--max-per-species 5` limits each species to 5 specimens (add it to steps 2 and 3).

3. Full run, in the background so a closed terminal does not stop it. Check progress with the `tail` line as often
   as you like; it ends with `zips: ...` and `done`. If it stops or the server reboots, run the same command
   again: images already on disk are kept, failed ones are tried again. On a later day add
   `--out /root/pheidole_export_<first day>` (see `ls -d /root/pheidole_export_*`), or it starts a new folder.

```
# ssh root@165.232.136.170   (staging)
cd /root/pheidole_tool && nohup nice -n 10 python3 tools/export/pheidole_major_heads.py > /root/pheidole_export.log 2>&1 &
sleep 60; tail -5 /root/pheidole_export.log
```

4. Check the result: one zip per clade that has images, plus `index.csv` and `not_found.csv`. A
   `download_failures.csv` in the export folder lists images that could not be fetched (run step 3 again).

```
# ssh root@165.232.136.170   (staging)
ls -lh /root/pheidole_export_*/zips/ ; ls /root/pheidole_export_*/download_failures.csv 2>/dev/null
```

5. Copy the zips to the Mac (in Terminal on the Mac, not on the server). They land in `Downloads/pheidole_export`.

```
# on the Mac
mkdir -p ~/Downloads/pheidole_export && scp 'root@165.232.136.170:/root/pheidole_export_*/zips/*' ~/Downloads/pheidole_export/
```

6. Put the folder in Dropbox and share the link with Jack (the zips, `index.csv` and `not_found.csv`).

7. When Jack has the files, free the staging disk:

```
# ssh root@165.232.136.170   (staging)
rm -r /root/pheidole_export_* /root/pheidole_tool /root/pheidole_export.log
```

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

- `Begin set1` with no end: set 1 may wait up to 30 minutes for TaxonWorks and then upload. After 210 minutes (8:30 AM) the application asks for a restart and `reboot_checker.sh` restarts it within 5 minutes, unless an upload is running.
- A later set running far beyond its usual time (2: about 45 min, 3 and 4: about 15, 5: about 50): no automatic restart, except the watchdog for a thread spinning at full CPU. Check that no upload is running (`serverStatus.do`), then restart.
- `Scheduler FAILED (remaining sets skipped)`: the sets after the failing one did not run. An admin can run one set by hand from the Curate page (`schedule.do?action=run&num=<N>`, confirm page). Avoid busy hours; set 5 is heavy and is skipped on staging.

### Upload stuck

While an upload is running, `serverStatus.do` names it, and the application refuses restart requests (so `reboot_checker.sh` waits). The watchdog and a manual `docker restart` still restart. Since 2026-10-20 uploads and admin data jobs started from a page run in the background, one at a time: `jobStatus.do` (Upload status, linked from the Curate page) lists them with their status and current step, and `serverStatus.do` shows "Background job running". A restart interrupts the running and waiting jobs; their status pages then say "Interrupted" and the curator uploads again (the upload's transaction is rolled back). Database view: `select id, kind, action, status, login_name, created, step from background_job order by id desc limit 20;`

```
# ssh root@165.232.137.86   (antweb-prod)
tail -30 /root/antweb/logs/detail/upload.log; docker logs antweb_antweb_1 --since 30m 2>&1 | grep -i upload | tail -20
```

If the log shows recent progress, wait: large uploads take a long time. If nothing has moved for a long time (no rule yet; judge from the upload's size), restart AntWeb. The upload is rolled back and the curator repeats it. If it was the nightly TaxonWorks upload, it runs again the next night, or an admin starts it by hand at `/utilData.do?action=taxonWorksNightly`.

### Storage mounts (/mnt/antweb, /mnt/backup)

On 2026-10-22 both production mounts stopped working for 26 hours: the Spaces access key they used had been deleted
(it was believed to be staging's only). Upload reports showed "no longer available", the CAS file copy failed and the
01:00 dump was not written. Since 2026-10-23 the hourly `MountCheck` emails within the hour.

**Rule: before deleting or rotating a Spaces key, check every server that may use it**
(`grep access_key_id /root/.config/rclone/rclone.conf` on each droplet shows the key id, never the secret; compare it
with the Access Key column in the DigitalOcean list). A bucket with a bucket policy (`antweb`) accepts only Full Access keys.

```
# ssh root@165.232.137.86   (antweb-prod)
systemctl status rclone rclone-dbarchive --no-pager | grep -E "Active|error" ; findmnt /mnt/antweb ; findmnt /mnt/backup ; ls -l /mnt/antweb/web/log/specimen/mountcheck-* ; ls -lt /mnt/backup/db/daily | head -3 ; cat /root/antweb/logs/web/db-dump-status.txt
```

- "Input/output error" or a hang: the mount is dead. If rclone's log says the key is unknown (`InvalidAccessKeyId`),
  make a new Full Access key in https://cloud.digitalocean.com/spaces/access_keys, then Brian types it in at a prompt
  (never in chat or a file in git):
  `read -rp 'Key id: ' K; read -rsp 'Secret: ' S; echo; rclone config update digitalocean access_key_id="$K" secret_access_key="$S" >/dev/null; unset K S`
- Then `systemctl restart rclone rclone-dbarchive`, check `findmnt` again, and restart AntWeb
  (`docker restart antweb_antweb_1`, 2 to 4 minutes): the container keeps the dead mount until it restarts.
- A dump that was missed: run `/root/antweb/bin/db-daily-dump.sh` by hand (about 2 minutes, about 120 MB).

## Backup facts checked on 2026-10-18

- Production: `/mnt/backup` is the rclone mount of Spaces bucket `antweb-dbarchive` (service `rclone-dbarchive`), `/mnt/antweb` of bucket `antweb` (service `rclone`); both enabled at boot. Check with `findmnt /mnt/backup` (one folder per call).
- Staging: `/mnt/backup` is a plain local folder on staging's own disk (last written October 2025); staging runs no daily dump, so it never touches production's backups.
- `/mnt/backup/db/daily/error.log` holds failures from up to December 2023 only. Since 2026-10-23 the hourly mount check emails when the newest dump is over 26 hours old (Alerts, above).
