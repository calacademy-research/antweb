#!/bin/bash

# Fetch the webpage
page_content=$(curl -s "https://www.antweb.org/util.do?action=isRestart")
#echo page_content: $page_content"" >> logs/detail/rebootCheck.log

now=$(date)

# Check for 'true' or 'false' in the fetched content
if [[ $page_content == *"<b>false</b>"* ]]
then
    echo "Diagnostic: 'false' found."
    exit 0
elif [[ $page_content == *"<b>true</b>"* ]]
then
    str="Diagnostic: 'true' found in rebootCheck.log. reboot: $now"
    echo $str
    echo $str >> logs/detail/rebootCheck.log
    exit 1
elif [[ $page_content == *"case#"* ]]
then
    str="Diagnostic: 'case#' found in rebootCheck.log. reboot: $now"
    echo $str
    echo $str >> logs/detail/rebootCheck.log
    exit 1
else
    echo "Warning: Neither 'true' nor 'false' found."
    exit 1
fi


