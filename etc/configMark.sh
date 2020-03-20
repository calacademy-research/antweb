# execute from /antweb as such:   
#
#    sh etc/configMark.sh

if [ ! -d WEB-INF/classes ]
then
   mkdir WEB-INF/classes
fi
ln -fs ../../etc/log4jAntweb.properties WEB-INF/classes/log4j.properties
ln -fs ../../etc/AppResMarkAntweb.properties WEB-INF/classes/AntwebResources.properties
ln -fs struts-configDbAnt.xml WEB-INF/struts-configDb.xml

if [ ! -d build ]
then
   mkdir build
fi
