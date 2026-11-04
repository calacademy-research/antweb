#!/bin/bash
# Host cron, every 5 minutes: restart the AntWeb container when the app asks for it
# (util.do?action=isRestart answers <b>true</b>).
#
# Which site to ask: the AntWeb container on this server, directly on its local port
# (127.0.0.1:8081 on production and staging). Going through the public address
# reached Cloudflare's challenge page, so the app's answer was never seen.
# ANTWEB_URL overrides it (e.g. ANTWEB_URL=http://127.0.0.1:8081).
# --dry-run: print the URL and the answer, restart nothing, write no log.
log_path="../logs/detail/rebootCheck.log"
cd "$(dirname "$0")"

# The log folder belongs to the container's tomcat user. This script runs as root on the host, so it never writes
# through a link (2026-11-02): a link planted as the log file or as its folder could point at any host file.
log_line() {
    local dir
    dir=$(dirname "$log_path")
    if [[ -L "$log_path" || -L "$dir" || -L "$(dirname "$dir")" ]]; then
        echo "reboot_checker: $log_path or its folder is a link; not written" >&2
        return 0
    fi
    echo "$1" >> "$log_path"
}

dry_run=0
[[ "$1" == "--dry-run" ]] && dry_run=1

base_url="${ANTWEB_URL:-http://127.0.0.1:8081}"
base_url="${base_url%/}"

page_content=$(curl -s --max-time 10 "$base_url/util.do?action=isRestart")
now=$(date)

# isRestart already answers false during an upload (including an image upload between two files)
# or a job started from a page. The error page (case#) does not, so ask isBusy too before any
# restart (2026-10-26, a restart cut a curator image upload short). Uploads and background jobs
# make it wait for the next run in 5 minutes; a data job or the nightly run do not, because a set 1
# stuck for 210 minutes is exactly what this script restarts.
busy=$(curl -s --max-time 10 "$base_url/util.do?action=isBusy" | head -c 200 | tr -d '\r\n')
is_busy=0
if [[ "$busy" == busy:* ]]; then
    IFS=',' read -ra kinds <<< "${busy#busy:}"
    for kind in "${kinds[@]}"; do
        kind="${kind# }"
        case "$kind" in
            "data job"|"nightly run") ;;
            *) is_busy=1 ;;
        esac
    done
fi

if [[ $dry_run == 1 ]]; then
    echo "URL: $base_url/util.do?action=isRestart"
    echo "isBusy: ${busy:-no answer}"
    if [[ "$page_content" == *"<b>false</b>"* ]]; then echo "Answer: false (no restart)"
    elif [[ "$page_content" == *"<b>true</b>"* ]]; then echo "Answer: true (would restart antweb_antweb_1)"
    elif [[ "$page_content" == *"case#"* ]]; then echo "Answer: case# (would restart antweb_antweb_1)"
    elif [[ -z "$page_content" ]]; then echo "Answer: empty (app down or unreachable; no action)"
    else echo "Answer: unexpected (no action). First 300 characters:"; echo "${page_content:0:300}"
    fi
    exit 0
fi

if [[ "$page_content" == *"<b>false</b>"* ]]; then
    str="Diagnostic: 'false' found."
    exit_code=0

elif [[ $is_busy == 1 && ( "$page_content" == *"<b>true</b>"* || "$page_content" == *"case#"* ) ]]; then
    str="Restart wanted but AntWeb is $busy; not restarting now, asking again in 5 minutes. $now"
    exit_code=0
    echo "$str"
    log_line "$str"

elif [[ "$page_content" == *"<b>true</b>"* ]]; then
    str="Diagnostic: 'true' found in reboot_checker.sh. Container restart: $now"
    exit_code=0
    echo "$str"
    log_line "$str"
    docker restart antweb_antweb_1

elif [[ "$page_content" == *"case#"* ]]; then
    str="Diagnostic: 'case#' found in reboot_checker.sh. Container restart: $now"
    exit_code=1
    echo "$str"
    log_line "$str"
    docker restart antweb_antweb_1

elif [[ -z "$page_content" ]]; then
    str="Warning: Empty response (app may be down). No action taken. $now"
    exit_code=1
    echo "$str"
    log_line "$str"

else
    str="Warning: Unexpected response. No action taken. $now"
    exit_code=1
    echo "$str"
    log_line "$str"
fi

exit $exit_code
