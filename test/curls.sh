# To be executed from the antweb/test directory as such:
#   sh curls.sh
#
# or:
#
#   time sh curls.sh > curls.log

# Must change " " to %20

#v3
curl -# -l "http://api.antweb.org/v3/specimens?minDate=1970-01-01&maxDate=1979-12-31&genus=Tetramorium&limit=5"
curl -# -l "http://api.antweb.org/v3/geoSpecimens?coords=37,%20-122&radius=2&limit=5"
curl -# -l "http://api.antweb.org/v3/distinctTaxa?rank=genus&limit=5"
curl -# -l "http://api.antweb.org/v3/images?since=60&shotType=p&limit=5" 
curl -# -l "http://api.antweb.org/v3/taxa?taxonName=myrmicinaecrematogaster%20modiglianii&limit=5" 
curl -# -l "http://api.antweb.org/v3/taxaImages?taxonName=myrmicinaecataulacus%20oberthueri&limit=5" 
curl -# -l "http://api.antweb.org/v3/geolocales?name=California&parent=United%20States&limit=5" 
curl -# -l "http://api.antweb.org/v3/geolocaleTaxa?rank=species&country=Comoros&status=valid&limit=5"
curl -# -l "http://api.antweb.org/v3/bioregions?name=Nearctic&limit=5"
curl -# -l "http://api.antweb.org/v3/bioregionTaxa?rank=species&bioregionName=nearctic&limit=5"

# v3.1
curl -# -l "http://api.antweb.org/v3.1/specimens?specimenCode=casent0922626"
curl -# -l "http://api.antweb.org/v3.1/specimens?minDate=1970-01-01&maxDate=1979-12-31&genus=Tetramorium&limit=5"
curl -# -l "http://api.antweb.org/v3.1/geoSpecimens?coords=37,%20-122&radius=2&limit=5"
curl -# -l "http://api.antweb.org/v3.1/taxa?taxonName=myrmicinaecrematogaster%20modiglianii&limit=5" 
curl -# -l "http://api.antweb.org/v3.1/distinctTaxa?rank=genus&limit=5"
curl -# -l "http://api.antweb.org/v3.1/images?since=60&shotType=p&limit=5" 
curl -# -l "http://api.antweb.org/v3.1/taxaImages?taxonName=myrmicinaecataulacus%20oberthueri&limit=5" 
curl -# -l "http://api.antweb.org/v3.1/geolocales?geolocaleName=California&parent=United States&limit=5" 
curl -# -l "http://api.antweb.org/v3.1/geolocaleTaxa?rank=species&country=Comoros&status=valid&limit=5"
curl -# -l "http://api.antweb.org/v3.1/bioregions?bioregionName=Nearctic&limit=5"
curl -# -l "http://api.antweb.org/v3.1/bioregionTaxa?rank=species&bioregionName=nearctic&limit=5"
curl -# -l "http://api.antweb.org/v3.1/unimagedGeolocaleTaxa?geolocaleName=California&byCaste=1&limit=5"

echo done
