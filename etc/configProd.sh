# execute from /antweb as such:   
#
#    sh etc/configProd.sh
#

if [ ! -d WEB-INF/classes ]
then
   mkdir WEB-INF/classes
fi

ln -fs ../../etc/log4jAntweb.properties WEB-INF/classes/log4j.properties
ln -fs ../../etc/AppResProd.properties WEB-INF/classes/AntwebResources.properties # requires Mods
ln -fs struts-configDbAnt.xml WEB-INF/struts-configDb.xml
ln -fs webHttps.xml WEB-INF/web.xml
