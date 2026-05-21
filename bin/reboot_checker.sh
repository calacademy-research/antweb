#!/bin/bash
log_path="../logs/detail/rebootCheck.log"
cd "$(dirname "$0")"

page_content=$(curl -s --max-time 10 "https://www.antweb.org/util.do?action=isRestart")
now=$(date)

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
