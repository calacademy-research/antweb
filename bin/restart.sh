# sh bin/restart.sh

sudo killall java -9
sudo /etc/init.d/tomcat start
tail -f ../links/antweb.log