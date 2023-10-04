#!/bin/bash

# Fetch the webpage
page_content=$(curl -s https://www.antweb.org/util.do?action=isRestart)

# Check for 'true' or 'false' in the fetched content
if [[ $page_content == *"<b>false</b>"* ]]; then
    echo "Diagnostic: 'false' found."
    exit 0
elif [[ $page_content == *"<b>true</b>"* ]]; then
    echo "Diagnostic: 'true' found."
    exit 1
else
    echo "Warning: Neither 'true' nor 'false' found."
    exit 128
fi


