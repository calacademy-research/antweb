# execute from /antweb as such:   
#
#    sh etc/configMark.sh
#
# Copy and modify to create your own...


if [ ! -d WEB-INF/classes ]
then
   mkdir WEB-INF/classes
fi
ln -fs ../../etc/log4jAntweb.properties WEB-INF/classes/log4j.properties
ln -fs ../../etc/AppResMarkAntweb.properties WEB-INF/classes/AntwebResources.properties
ln -fs etc/buildAntwebMark.properties build.properties
ln -fs struts-configDbAnt.xml WEB-INF/struts-configDb.xml
ln -fs webHttp.xml WEB-INF/web.xml

if [ ! -d build ]
then
   mkdir build
fi

#cp -r etc/add/* build/
