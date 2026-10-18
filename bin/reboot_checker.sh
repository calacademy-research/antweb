#!/bin/bash
# Host cron, every 5 minutes: restart the AntWeb container when the app asks for it
# (util.do?action=isRestart answers <b>true</b>).
#
# Which site to ask: the server this runs on, never another one (on staging, asking
# production restarted staging on production's flag).
#   1. ANTWEB_URL, if set (e.g. ANTWEB_URL=https://staging.antweb.org), or
#   2. https://staging.antweb.org when the host name contains "staging", otherwise
#   3. https://www.antweb.org (production; unchanged).
# --dry-run: print the URL and the answer, restart nothing, write no log.
log_path="../logs/detail/rebootCheck.log"
cd "$(dirname "$0")"

dry_run=0
[[ "$1" == "--dry-run" ]] && dry_run=1

if [[ -n "$ANTWEB_URL" ]]; then
    base_url="${ANTWEB_URL%/}"
elif [[ "$(hostname)" == *staging* ]]; then
    base_url="https://staging.antweb.org"
else
    base_url="https://www.antweb.org"
fi

page_content=$(curl -s --max-time 10 "$base_url/util.do?action=isRestart")
now=$(date)

if [[ $dry_run == 1 ]]; then
    echo "URL: $base_url/util.do?action=isRestart"
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

elif [[ "$page_content" == *"<b>true</b>"* ]]; then
    str="Diagnostic: 'true' found in reboot_checker.sh. Container restart: $now"
    exit_code=0
    echo "$str"
    echo "$str" >> "$log_path"
    docker restart antweb_antweb_1

elif [[ "$page_content" == *"case#"* ]]; then
    str="Diagnostic: 'case#' found in reboot_checker.sh. Container restart: $now"
    exit_code=1
    echo "$str"
    echo "$str" >> "$log_path"
    docker restart antweb_antweb_1

elif [[ -z "$page_content" ]]; then
    str="Warning: Empty response (app may be down). No action taken. $now"
    exit_code=1
    echo "$str"
    echo "$str" >> "$log_path"

else
    str="Warning: Unexpected response. No action taken. $now"
    exit_code=1
    echo "$str"
    echo "$str" >> "$log_path"
fi

exit $exit_code
