# execute from /antweb as such:   
#
#    sh etc/configDev.sh
#
 
if [ ! -d WEB-INF/classes ]
then
   mkdir WEB-INF/classes
fi
ln -fs ../../etc/log4jAntweb.properties WEB-INF/classes/log4j.properties
ln -fs ../../etc/AppResDevAntweb.properties WEB-INF/classes/AntwebResources.properties
ln -fs struts-configDbAnt.xml WEB-INF/struts-configDb.xml

