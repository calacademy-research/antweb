"""API v3.1 requests used by the survey and test_api.py (Caddy serves the API under /v3.1/)."""

API_CASES = {
    "index": "/v3.1/",
    "taxa_genus": "/v3.1/taxa?genus=camponotus&limit=5",
    "taxa_subfamily": "/v3.1/taxa?subfamily=formicinae&taxarank=genus&limit=5",
    "specimens_genus": "/v3.1/specimens?genus=camponotus&limit=5",
    "specimens_country": "/v3.1/specimens?country=Madagascar&limit=5",
    "geoSpecimens": "/v3.1/geoSpecimens?coords=37.77,-122.47&radius=10&limit=5",
    "geoSpeciesStats": "/v3.1/geoSpeciesStats?geolocaleName=Madagascar",
    "distinctTaxa": "/v3.1/distinctTaxa?country=Madagascar&taxarank=genus&limit=5",
    "images": "/v3.1/images?since=30&limit=5",
    "measurements": "/v3.1/measurements?genus=camponotus&limit=5",
    "taxaImages": "/v3.1/taxaImages?genus=camponotus&limit=5",
    "geolocales_country": "/v3.1/geolocales?georank=country&limit=5",
    "geolocaleTaxa": "/v3.1/geolocaleTaxa?geolocaleName=Madagascar&taxarank=genus&limit=5",
    "bioregions": "/v3.1/bioregions",
    "bioregionTaxa": "/v3.1/bioregionTaxa?bioregionName=Malagasy&taxarank=genus&limit=5",
    "unimagedGeolocaleTaxa": "/v3.1/unimagedGeolocaleTaxa?geolocaleName=Madagascar&limit=5",
}
