#!/usr/bin/env sh
service cron start

cd /usr/local/tomcat || exit

su - tomcat
catalina.sh run