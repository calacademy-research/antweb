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
    str="Diagnostic: 'true' found in reboot_checker.sh. Reboot: $now"
    exit_code=1
    echo $str
    echo $str >> $log_path
    reboot
elif [[ "$page_content" == *"case#"* ]]; then
    str="Diagnostic: 'case#' found in reboot_checker.sh. Reboot: $now"
    exit_code=1
    echo $str
    echo $str >> $log_path
    reboot
else
    str="Warning: Neither 'true' nor 'false' found in page. $now"
    exit_code=1
fi

exit $exit_code

