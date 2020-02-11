
sh /etc/init.d/tomcat stop

wait

killall java -9

sh /etc/init.d/tomcat start