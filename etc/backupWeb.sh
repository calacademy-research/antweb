# sudo sh ../deploy/etc/backupWeb.sh

cp -r /data/antweb/web/curator .
cp -r /data/antweb/web/documentation .
cp -r /data/antweb/web/genInc .
cp -r /data/antweb/web/homepage .
cp -r /data/antweb/web/speciesList .
cp -r /data/antweb/web/team .
cp -r /data/antweb/web/testdir .
cp -r /data/antweb/web/workingdir .
cp -r /data/antweb/web/imageUpload .

rm -rf fiji/uploaded
rm -rf introduced/AntTool_v0_3
rm -r *.pdf
rm -r *.tif
