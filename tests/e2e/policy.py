"""Which actions the tests may call, and how.

Every action in struts-config.xml must appear in exactly one of these places,
or in expectations.json once the baseline is recorded. test_actions.py fails
when a new action is added without a decision here.
"""

# Never requested by the automated tests, even anonymously: they start jobs,
# reload caches, change files or are upload endpoints. Tested by hand on staging.
NEVER = {
    "/util": "admin utilities (reload managers, postInstantiate); called internally",
    "/utilData": "admin data jobs (crawls, recounts)",
    "/schedule": "starts the nightly scheduled tasks",
    "/taxonomicPageGen": "generates static taxonomic pages",
    "/cache": "flushes or rebuilds caches",
    "/adminAlert": "admin alert controls",
    "/upload": "specimen and image upload",
    "/imageUploader": "image upload",
    "/test": "developer test action (updates counts)",
    "/mobile": "developer test action (updates counts)",
    "/pageTracker": "records page views",
    "/pictureLike": "records a like",
    "/logout": "would end the test's own session",
}

# Change data or write files when called with parameters. Since #75 they
# require a login; the tests call them anonymously, without parameters, and
# expect the log-in page. Only allowed where the fix is deployed
# (ANTWEB_PROBE_WRITE_ACTIONS=1), never against production.
WRITE_ACTIONS = {
    "/saveGroup", "/manageMuseum", "/viewMuseum", "/saveMuseum", "/saveHomePage",
    "/previewHomePage", "/worldAuthoritySave", "/newBioRegion", "/editCredit",
    "/teamMemberImgUpload", "/saveProject", "/deleteImages", "/saveLogin",
    "/editLogin", "/antwebInvite", "/forgotPassword", "/simpleContentEditorWrite",
    "/editGeolocale", "/speciesListMove", "/speciesListToolAdd", "/moveToValid",
    "/descEditImageUpload", "/saveHomePage", "/teamMemberImgDownload",
    "/editHomePage", "/editProject", "/bioregionMapMgr", "/introducedMapMgr",
    "/typeStatus", "/disputeMgr",
}

# Example requests for public pages, used in addition to the bare URL.
# The crawl in survey.py adds more from links on these pages.
SEEDS = [
    "/",
    "/browse.do?subfamily=formicinae&rank=subfamily",
    "/description.do?genus=camponotus&rank=genus",
    "/images.do?genus=camponotus&rank=genus",
    "/taxonomicPage.do?rank=subfamily&project=allantwebants",
    "/taxonomicPage.do?rank=genus&project=worldants",
    "/regions.do",
    "/countries.do",
    "/bioregions.do",
    "/museums.do",
    "/projects.do",
    "/curators.do",
    "/artists.do",
    "/statistics.do",
    "/search.do",
    "/advSearch.do",
]
