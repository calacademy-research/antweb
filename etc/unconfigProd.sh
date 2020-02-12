# To be executed from the antweb directory as:
#
# sh etc/unconfigProd.sh

rm WEB-INF/classes/AntwebResources.properties
rm WEB-INF/web.xml
rm build.properties

git rm WEB-INF/classes/AntwebResources.properties
git rm WEB-INF/web.xml
git rm build.properties
