#!/usr/bin/env sh
service cron start

su - tomcat
catalina.sh run