# execute from /antweb as such:   
#
#    sh etc/configAntwebTest.sh
#
# Copy and modify to create your own...


if [ ! -d WEB-INF/classes ]
then
   mkdir WEB-INF/classes
fi
ln -fs ../../etc/log4jAntwebTest.properties WEB-INF/classes/log4j.properties
ln -fs ../../etc/AppResLiveAntwebTest.properties WEB-INF/classes/AntwebResources.properties
ln -fs etc/buildAntwebTest.properties build.properties
ln -fs ../../etc/ProjectResources.properties WEB-INF/classes/ProjectResources.properties

#ln -fs struts-configDbAnt.xml WEB-INF/struts-configDb.xml
ln -fs struts-configDbAntTest.xml WEB-INF/struts-configDb.xml
# If we get a test database created, then change to the above from the line above that.
# And create that file: ...Test.xml.

if [ ! -d build ]
then
   mkdir build
fi

cp WEB-INF/classes/ProjectResources.properties build/WEB-INF/classes/

cp -r etc/add/* build/

#cp etc/add/arizona.jsp build/
#cp etc/add/arizona-body.jsp build/
#cp etc/add/index-body.jsp build/
#cp etc/add/recentImages_gen_inc.jsp build/
#cp etc/add/group_gen_inc.jsp build/
#cp etc/add/statistics.jsp build/

#cp -r etc/add/images build/
