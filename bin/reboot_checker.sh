#!/bin/bash

log_path="../logs/detail/rebootCheck.log"

cd "$(dirname "$0")"

# Fetch the webpage
page_content=$(curl -s "https://www.antweb.org/util.do?action=isRestart")
now=$(date)

# Define diagnostic string based on page content
if [[ "$page_content" == *"<b>false</b>"* ]]; then
    str="Diagnostic: 'false' found."
    exit_code=0
elif [[ "$page_content" == *"<b>true</b>"* ]]; then
    str="Diagnostic: 'true' found in rebootCheck.log. reboot: $now"
    exit_code=1
elif [[ "$page_content" == *"case#"* ]]; then
    str="Diagnostic: 'case#' found in rebootCheck.log. reboot: $now"
    exit_code=1
else
    str="Warning: Neither 'true' nor 'false' found in page. $now"
    exit_code=1
fi

# Output diagnostic string
echo $str
echo $str >> $log_path
exit $exit_code

