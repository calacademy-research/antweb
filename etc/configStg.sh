# This one seems to be the one that is use on stage.  configStage.sh seems to have been deprecated.
# Szu-Ching change?


# execute from /antweb as such:   
#
#    sh etc/configStage.sh
#
# You NEED a ProjectResources.properties but you DON'T want to overwrite the existing one in the
# build directory.  If making a fresh live installation, manually install the latest and all of the 
# supporting files (?!?!), for instance *GEN_INC*.jsp.  

if [ ! -d WEB-INF/classes ]
then
   mkdir WEB-INF/classes
fi
ln -fs ../../etc/log4jAntweb.properties WEB-INF/classes/log4j.properties
ln -fs ../../etc/AppResStgAntweb.properties WEB-INF/classes/AntwebResources.properties
ln -fs etc/buildAntweb.properties build.properties
ln -fs struts-configDbAnt.xml WEB-INF/struts-configDb.xml
ln -fs webHttps.xml WEB-INF/web.xml

# No.  Do not create a softlink.  This would result in overwrite of the dynamically generated one
#   in the build tree.
# ln -fs ../../etc/ProjectResources.properties WEB-INF/classes/ProjectResources.properties

