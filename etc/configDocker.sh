#!/usr/bin/env sh

# Called from /antweb/deploy as `sh etc/configDocker.sh`
# Selects the correct AppRes.properties file depending on the argument

# If arg is passed to script, use it instead of environment variable ENV
# If no arg and ENV isn't set, default to PROD
if [ -n "$1" ]; then
  ENV=$1
elif [ -z "$ENV" ]; then
    ENV=PROD
fi

cp etc/log4jAntweb.properties WEB-INF/classes/log4j.properties
cp WEB-INF/struts-configDbAnt.xml WEB-INF/struts-configDb.xml

case $ENV in
  "PROD") cp etc/AppResProdDocker.properties WEB-INF/classes/AntwebResources.properties;;
  "STAGE") cp etc/AppResStageDocker.properties WEB-INF/classes/AntwebResources.properties;;
  "DEV") cp etc/AppResDevDocker.properties WEB-INF/classes/AntwebResources.properties;;
  *) echo "ENV not recognized";;
esac

