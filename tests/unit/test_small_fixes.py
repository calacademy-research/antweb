"""Checks of the 2026-10-21 fixes that need no server or database.

1. Every SQL update of the login table in src/ keeps login.created ("created = created"): the
   column is ON UPDATE CURRENT_TIMESTAMP in production unless
   db/upgrade/8.113/2026-10-21-login-created.sql has been run.
2. Nothing in src/ deletes all rows of object_edit or object_hist (page text and its history);
   the unused DescEditDb.populateObjectEdit() / emptyObjectEdit() are gone.
3. Page titles: no "Subegion" (subregion.jsp says "Subregion: ...").
4. The old login page copy web/curate/manageLogin.jsp and manageLogin-body.jsp are gone and
   nothing refers to them.
5. At phone width the admin pages' column of links (div.right next to div.admin_left) does not
   float: admLayout.jsp sets the viewport, admin_style.css and responsive.css have the rules.
6. (2026-10-26, public-pages finding F1) Every "Too busy for bots." answer is returned: the
   ActionForward of HttpUtil.sendMessage() was ignored and the page was built anyway.
7. (2026-10-26, finding F2) The limits on simultaneous maps, comparisons and field guides use
   ConcurrencyLimiter (thread-safe), skip logged-in users and release only a slot they took.
8. (2026-10-26, low public-pages findings) F9 bigMap.do?specimen= maps the specimen, not its species;
   F11 the View in AntCat link is https with the name URL-encoded; F12 not-found messages keep the request
   details in the log; F15 the comparisons give the explained log-in page; F17 a database error on a place
   page is a message, not a blank page; F19 the dead "getCompare" line is gone; F21 bigPicture.do without
   code, name or imageId is a message, not the home page; F13 the specimen search limit is per thread.
9. (2026-10-27, finding B44) The nightly recounts every valid adm1, also those that are not live
   (GeolocaleDb.updateAdm1Counts): their pages showed old numbers next to a current list. The count check covers
   them once a nightly run has recounted them (a later change).
9. (2026-10-27, B65, B66) Set 2 recounts the Global column after its clean-up; the count check bounds a place's
   synonym rows by the specimen records of the name and its synonyms.
10. (2026-10-27, B74, B75) CopyrightDb keeps every copyright row (the image upload report of an earlier year's batch
   failed); Compare of a search by species does not call Taxon.setHasImages(connection) (it failed) and the page
   prints the preparation's count when nothing is left.
11. (2026-10-27) A form post counts only when sent as POST (HttpUtil.isPost), the token check in
   SessionRequestFilter covers every method other than GET, HEAD and OPTIONS (Csrf.methodNeedsCheck), and
   LoginAction accepts a password only in a post; only administrators create a login in SaveLoginAction. Unit test: test/.../util/PostMethodTest.java.
12. (2026-10-27, PR #291 staging run 2) No if statement whose action is lost in a trailing comment (GeolocaleMgr: the
   North Dakota return was inside the comment, so the Ohio line below became its body); the home page slideshow is
   built before it is written (an error leaves it empty instead of cutting the page off) and TaxonMgr publishes its
   name list and taxon map only once they are filled (a reload runs after every specimen upload).
13. (2026-10-28, nightly and count check triage) Data job messages escape the place names they repeat and refuse an
   unknown museum or project; the bulk flag and the specimen search limit are restored in finally; set 4 commits the
   image counts before the statistics and rolls back only those on a failure; the B36 alert has its own try; the
   bioregion list rows count each specimen once (not once per image); the group_count table is created at start-up,
   not inside set 3; the steps after the nightly run each have their own try; a name is deleted outright only when no
   other list holds it. Unit test (no database): test/.../util/NightlyTriageThTest.java.

13. (2026-10-28) Logins and sessions, curator tools and uploads: deleting a login ends its sessions; sign-in posts
   from another site are refused; the login list is a request attribute; staging's bot list is production's; an
   encoded dot is refused in either case; a failed invite link after "Add an uploader" does not say nothing was
   changed; a curator without a real group sees no name updates; small null checks. Unit test:
   test/.../audit/LoginSessionFixesTest.java.

13. (2026-10-28, places and search audits) A failure while a museum's list or its specimens' museum code is rebuilt
   is thrown (the data job is rolled back) and the museum's inserts bind their values; the unused ProjectDb.updateProjects
   is gone and ProjectDb.update reports a failure; the nightly endemism step skips a place missing from GeolocaleMgr;
   the place navigation names are escaped; the advanced search closes its statements, holds one connection, counts
   truncation before the Images only and Types only filters, limits the full searches of visitors not logged in, adds
   microhabitat once, sorts by caste with each item's own subcaste and gives a message instead of a blank page.
   Unit test: test/.../audit/PlacesSearchFixesTest.java.
Run: python3 tests/unit/test_small_fixes.py   (also in .github/workflows/tests.yaml)
"""
import os
import re

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def files(top, extensions):
    for dirpath, _dirs, names in os.walk(os.path.join(ROOT, top)):
        for name in names:
            if name.endswith(extensions):
                yield os.path.join(dirpath, name)


def read(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def java_sources():
    return {os.path.relpath(p, ROOT): read(p) for p in files("src", (".java",))}


def sql_text(java):
    """The Java source with string literals joined ("a" + "b" -> "ab"), comments removed."""
    java = re.sub(r"/\*.*?\*/", " ", java, flags=re.S)
    java = re.sub(r"//[^\n]*", " ", java)
    return re.sub(r'"\s*\+\s*"', "", java)


def test_login_updates_keep_created(sources):
    checked = 0
    problems = []
    for path, src in sources.items():
        text = sql_text(src)
        for m in re.finditer(r'"[^"]*\bupdate\s+login\s[^"]*"', text, re.I):
            stmt = m.group(0)
            if re.search(r"update\s+login\s+(l\s+)?set\b", stmt, re.I) is None:
                continue
            checked += 1
            set_part = re.split(r"\bwhere\b", stmt, flags=re.I)[0]
            if not re.search(r"\b(l\.)?created\s*=\s*(l\.)?created\b", set_part, re.I):
                problems.append(f"{path}: {stmt[:160]}")
    assert checked >= 10, f"only {checked} login updates found"
    assert not problems, "update of login without 'created = created':\n" + "\n".join(problems)
    print(f"{checked} updates of login keep created.")


def test_no_delete_all_page_text(sources):
    problems = []
    for path, src in sources.items():
        text = sql_text(src)
        for m in re.finditer(r'"\s*(delete\s+from|truncate(\s+table)?)\s+(object_edit|object_hist)\b([^"]*)"', text, re.I):
            if not re.search(r"\bwhere\b", m.group(4), re.I):
                problems.append(f"{path}: {m.group(0)}")
    assert not problems, "deletes all page text:\n" + "\n".join(problems)
    desc = sources[os.path.join("src", "org", "calacademy", "antweb", "home", "DescEditDb.java")]
    for name in ("populateObjectEdit", "emptyObjectEdit", "insertObjectEdit"):
        assert name not in desc, name + " is back in DescEditDb"
    print("No statement deletes all of object_edit or object_hist.")


def test_titles():
    found = [p for p in files("web", (".jsp", ".jspf")) if "Subegion" in read(p)]
    assert not found, found
    assert 'String titleStr = "Subregion: "' in read(os.path.join(ROOT, "web", "subregion.jsp"))
    print("Page titles: no 'Subegion'.")


def test_core_util_fixes(sources):
    """(2026-10-27, core and util fixes) Request and stored values reach SQL quoted (subgenus filter, place names
    in maps and place criteria, specimen taxa), links built from the request address are escaped, the group page
    uses the group found by name, the map cache is refreshed by curators only, an administrator's postInstantiate
    goes through the POST confirmation, and four unused classes that built SQL from their inputs (or tested
    nothing) are gone."""
    def code(path):  # without comments
        text = re.sub(r"/\*.*?\*/", " ", sources[path], flags=re.S)
        return "\n".join(l for l in text.splitlines() if not l.strip().startswith("//"))
    p = "src/org/calacademy/antweb/"
    genus = code(p + "Genus.java")
    assert "subgenus = '\" + subgenus" not in genus and "DBUtil.sqlLiteral(subgenus)" in genus, "subgenus reaches SQL unquoted"
    for path in (p + "Map.java", p + "Overview.java", p + "TaxaPage.java"):
        text = code(path)
        assert not re.search(r"= '\"\s*\+\s*(overview(\.getName\(\)|\.getTitle\(\))?|getName\(\)|name|\(\(Adm1\) overview\)\.getParent\(\))\s*\+\s*\"'", text), path
    taxa = code(p + "TaxaPage.java")
    assert "overview instanceof Country || overview instanceof Adm1 || overview instanceof Project)) withSpecimen = false" in taxa
    group = code(p + "GroupAction.java")
    assert "groupId = group.getId();" in group, "group page by name uses the id parameter again"
    assert "refresh && LoginMgr.isCurator(request)" in code(p + "BigMapAction.java")
    for path in (p + "Museum.java", p + "Project.java", p + "geolocale/Bioregion.java", p + "geolocale/Geolocale.java"):
        assert not re.search(r"<a href='\" \+ otherUrl", code(path)), path + ": request address in a link, unescaped"
    util = code(p + "util/UtilAction.java")
    assert '"postInstantiate".equals(action) && !SchedulerCall.isPostInitialize(request)))' in util
    mgr = code(p + "util/AntwebMgr.java")
    assert "s_oneAtATime.set(false);" in mgr[mgr.index("} finally {"):], "populate() can leave its guard set"
    assert "where login_id" not in code(p + "util/ListUploadsAction.java").replace('" where") + " login_id', "")
    for gone in ("MapLocality.java", "SlideShowSearch.java", "SpecimenFieldSummary.java", "test/Rest.java"):
        assert not os.path.exists(os.path.join(ROOT, p + gone)), gone
    print("Core and util fixes in place.")


def test_old_manage_login_page_gone():
    for name in ("manageLogin.jsp", "manageLogin-body.jsp"):
        assert not os.path.exists(os.path.join(ROOT, "web", "curate", name)), name
    refs = []
    for top in ("src", "web", "WEB-INF", "docker"):
        for p in files(top, (".java", ".jsp", ".jspf", ".xml", ".properties", "Caddyfile", ".common", ".staging")):
            # A path in quotes (include, tiles value, action forward); old notes in comments do not count.
            if re.search(r"[\"'](/?curate/|/?web/curate/)?manageLogin(-body)?\.jsp", read(p)):
                refs.append(os.path.relpath(p, ROOT))
    assert not refs, refs
    print("manageLogin.jsp and manageLogin-body.jsp are gone and not referred to.")


def test_admin_links_column_stacks_on_phones():
    css = read(os.path.join(ROOT, "web", "common", "responsive.css"))
    start = css.index("@media (max-width: 760px)")
    phone = css[start:]
    for selector in (".admin_left ~ .right", ".right:has(~ .admin_left)"):
        m = re.search(re.escape(selector) + r"\s*\{([^}]*)\}", phone)
        assert m, selector + " missing in the phone rules"
        assert "float: none" in m.group(1), selector
    # The :has() rule is on its own: a browser without :has() would drop a whole selector list.
    assert not re.search(r",\s*\.right:has|\.right:has\([^)]*\)\s*,", phone)
    # The admin pages use admLayout.jsp (not layout.jsp): it must set the viewport to the device
    # width (else phones lay the page out 980 px wide and no max-width rule applies) and
    # admin_style.css must have the same rules.
    adm = read(os.path.join(ROOT, "web", "layouts", "admLayout.jsp"))
    assert '<meta name="viewport" content="width=device-width, initial-scale=1"/>' in adm
    assert "/common/admin_style.css" in adm
    admin_css = read(os.path.join(ROOT, "web", "common", "admin_style.css"))
    phone = admin_css[admin_css.index("@media (max-width: 760px)"):]
    for selector in (".admin_left ~ .right", ".right:has(~ .admin_left)", ".admin_left", "#admin_layout"):
        m = re.search(r"(^|\n)\s*" + re.escape(selector) + r"\s*\{([^}]*)\}", phone)
        assert m, selector + " missing in admin_style.css phone rules"
    for selector in (".admin_left ~ .right", ".right:has(~ .admin_left)", ".admin_left"):
        m = re.search(r"(^|\n)\s*" + re.escape(selector) + r"\s*\{([^}]*)\}", phone)
        assert "float:none" in m.group(2).replace(" ", ""), selector
    print("Admin link column stacks at phone width.")


def test_too_busy_for_bots_is_returned(sources):
    found = 0
    for path, src in sources.items():
        for line in src.splitlines():
            if "tooBusyForBots(" in line and "sendMessage(" in line:
                found += 1
                assert "return HttpUtil.sendMessage(" in line, f"{path}: {line.strip()}"
    assert found >= 11, found
    print(f"Too busy for bots: {found} answers returned.")


def test_simultaneous_limits(sources):
    for name in ("BigMapAction.java", "BrowseAction.java", "search/FieldGuideAction.java"):
        path = "src/org/calacademy/antweb/" + name
        src = sources[path]
        assert "ConcurrencyLimiter" in src, path
        assert "LoginMgr.getAccessLogin(request)" in src, path
        assert ".release()" in src and ".tryAcquire()" in src, path
        assert not re.search(r"(\+\+|--)\s*s_\w*(Count|Executes)\b", src), path
    print("Simultaneous limits are thread-safe and skip logged-in users.")


def test_low_public_pages_findings(sources):
    a = "src/org/calacademy/antweb/"
    big_map = sources[a + "BigMapAction.java"]
    assert "taxonName = specimen.getTaxonName();" not in re.sub(r"//[^\n]*", "", big_map), "F9"
    antcat = read(os.path.join(ROOT, "web/common/viewInAntCat.jsp"))
    assert "https://www.antcat.org/catalog/search" in antcat and "URLEncoder.encode(" in antcat, "F11"
    assert "http://www.antcat.org" not in antcat, "F11"
    assert "isIdentifiedToGenus(taxon.getGenus())" in antcat and "isEmpty()" in antcat, \
        "F11: no View in AntCat link for a name not identified to genus (empty search name)"
    locality, collection = sources[a + "LocalityAction.java"], sources[a + "CollectionAction.java"]
    browse = sources[a + "BrowseAction.java"]
    assert 'message = message + "execute() "' not in locality and 'message += " isBot:"' not in locality, "F12"
    assert 'message += "  No upload in process.  RequestInfo:"' not in collection, "F12"
    assert 'message = "Bad rank:" + Html.esc(rank);' in browse, "F12"
    assert "Login.MUST_LOGIN_MESSAGE" not in browse and "Check.loginForFeature(request)" in browse, "F15"
    site_nav = read(os.path.join(ROOT, "web/common/siteNav.jsp"))
    assert re.search(r'^\s*\|\| \(uri\.contains\("login\.jsp"\)\)', site_nav, re.M), \
        "F15: the log-in page has a Change View menu (its links drop name= of the page asked for)"
    assert "log in anonymously" not in sources[a + "Login.java"], "F15"
    overview = sources[a + "OverviewAction.java"]
    end = overview[overview.index("} catch (SQLException e) {"):overview.index("static void setPlaceImages(")]
    assert "return null;" not in end and 'mapping.findForward("message")' in end, "F17"
    assert '"getCompare".equals(cacheType)' not in browse, "F19"
    form = sources[a + "SpecimenImageForm.java"]
    assert "error.name.required" not in form, "F21"
    assert 'request.setAttribute("message", "Must specify code and shot, or imageId.");' in sources[a + "BigPictureAction.java"], "F21"
    search = sources[a + "search/SearchAction.java"]
    assert "ThreadLocal<Integer> tempSpecimenSearchLimit" in search, "F13"
    print("Low public-pages findings: F9, F11, F12, F13, F15, F17, F19, F21 checked.")


def test_adm1_totals_not_only_live(sources):
    src = sources["src/org/calacademy/antweb/home/GeolocaleDb.java"]
    body = src[src.index("public void updateAdm1Counts()"):]
    body = body[:body.index("public void updateCountryCounts()")]
    code = "\n".join(l for l in body.splitlines() if not l.strip().startswith("//"))
    assert "updateCounts(geolocale.getId())" in code, "updateAdm1Counts no longer recounts each adm1"
    assert not re.search(r"if\s*\(\s*geolocale\.isLive\(\)\s*\)", code), "updateAdm1Counts skips adm1s that are not live again"
    print("Every valid adm1 is recounted, live or not.")


def test_global_recounted_after_cleanup_and_synonym_bound(sources):
    src = sources["src/org/calacademy/antweb/util/UtilDataAction.java"]
    i = src.index('doAction("dataCleanup"')
    assert "recountGlobal(connection)" in src[i:i + 900], "set 2 no longer recounts the Global column after the clean-up (B65)"
    inv = sources["src/org/calacademy/antweb/util/CountInvariants.java"]
    body = inv[inv.index("void globalPerPlace()"):]
    body = body[:body.index("compare(")]
    assert "from specimen group by taxon_name" in body, "the Global bound of a synonym is not its specimen records (B66)"
    print("Global recounted after the clean-up; synonyms bounded by their specimen records.")


def test_copyright_rows_and_compare_by_species(sources):
    db = sql_text(sources["src/org/calacademy/antweb/home/CopyrightDb.java"])
    body = db[db.index("void getCopyrights()"):]
    loop = body[body.index("while (rset.next())"):body.index("finally")]
    assert "new ArrayList" not in loop, "CopyrightDb makes a new list for each row again (B74)"
    report = read(os.path.join(ROOT, "web/imageUploader/imageUploadReport-body.jsp"))
    assert "imageUpload.getCopyright() != null" in report, "the image upload report fails on a batch with no copyright (B74)"
    prep = sql_text(sources["src/org/calacademy/antweb/search/PrepareCompareResultsAction.java"])
    assert "setHasImages(connection)" not in prep, "Compare by species calls Taxon.setHasImages(connection) again (B75)"
    assert "antImagedNames(" in prep, "Compare by species no longer uses StatisticsDb.antImagedNames (B75)"
    jsp = read(os.path.join(ROOT, "web/search/advancedSearchResultsByTaxon-body.jsp"))
    empty = jsp[jsp.index("getResults().size() < 1"):jsp.index("Sorry, nothing matches your request.")]
    assert 'class="display_count"' in empty, "a preparation that leaves no species prints no count (B75)"
    print("Copyright rows kept; Compare by species counts imaged names; an empty preparation prints its count.")


def test_post_means_post(sources):
    http = sources["src/org/calacademy/antweb/util/HttpUtil.java"]
    body = http[http.index("public static boolean isPost("):]
    body = body[:body.index("\n    }")]
    assert 'request.getMethod()' in body, "HttpUtil.isPost no longer looks at the method"
    filt = sql_text(sources["src/org/calacademy/antweb/util/SessionRequestFilter.java"])
    assert "Csrf.methodNeedsCheck(request.getMethod())" in filt, "the filter checks the token only for some methods again"
    login = sql_text(sources["src/org/calacademy/antweb/LoginAction.java"])
    assert re.search(r"password\s*!=\s*null\s*&&\s*!HttpUtil\.isPost\(request\)", login), \
        "LoginAction accepts a password outside a post again"
    save = sql_text(sources["src/org/calacademy/antweb/curate/login/SaveLoginAction.java"])
    assert re.search(r"if \(login == null && !isAdminUser\) \{\s*s_log\.warn\(.*?\);\s*return mapping\.findForward\(\"permissionDenied\"\)", save, re.S), \
        "SaveLoginAction lets someone other than an administrator create a login again"
    print("Posts are posts; the token check covers every method that can change something; passwords only by post.")


def test_no_lost_ifs_and_whole_home_page(sources):
    lost = []
    for path, src in sources.items():
        for n, line in enumerate(src.splitlines(), 1):
            # "if (...)  // ... return x;": the comment holds the statement, the next line becomes the if's body
            if re.match(r"\s*(if|else if)\s*\(.*\)\s*//.*\breturn\b", line):
                lost.append(f"{path}:{n}")
    assert not lost, "an if statement whose return is inside a comment: " + ", ".join(lost)
    geo = sources["src/org/calacademy/antweb/util/GeolocaleMgr.java"]
    assert re.search(r'"northdakotaants"\.equals\(projectName\).*\) return GeolocaleMgr\.getAdm1\("North Dakota"', geo)
    with open(os.path.join(ROOT, "web/common/slideshow_homepage.jsp"), encoding="utf-8") as f:
        show = f.read()
    assert "StringBuilder slides" in show and "catch (RuntimeException e)" in show and "<%= slides %>" in show, \
        "the home page slideshow is written while it is built again"
    assert "if (subfamilyName == null) continue;" in show
    mgr = sources["src/org/calacademy/antweb/util/TaxonMgr.java"]
    body = mgr[mgr.index("public static void populate(Connection connection, boolean forceReload, boolean initialRun)"):]
    body = body[:body.index("s_populateTime")]
    assert "prettyTaxaNamesList = new ArrayList" not in body and "s_taxa = new HashMap" not in body, \
        "TaxonMgr.populate publishes a list or map before filling it"
    print("No if loses its statement to a comment; the home page slideshow and TaxonMgr's reload are whole.")


def test_triage_20261028(sources):
    save = sql_text(sources["src/org/calacademy/antweb/curate/login/SaveLoginAction.java"])
    delete = save[save.index('if ("delete".equals(form.getDelete()))'):save.index("login = loginDb.getLogin(id);")]
    assert delete.index("bumpSessionVersion(id)") < delete.index("deleteById(id)"), "deleting a login leaves its sessions"
    assert "LoginEdit.removesRights(result.before, result.after)" in save, "removed rights wait for the next login again"
    assert "passPwdCheck2" not in save
    filt = sources["src/org/calacademy/antweb/util/SessionRequestFilter.java"]
    assert "Csrf.isSignInPath(request)" in filt and "Csrf.crossSite(request)" in filt, "sign-in posts from another site are accepted again"
    assert "AdminAlertMgr.add(spaceMessage, connection)" not in filt, "the space alert gets a closed connection again"
    views = sources["src/org/calacademy/antweb/curate/login/ViewLoginsAction.java"]
    assert 'request.setAttribute("antwebLogins"' in views and 'getSession().setAttribute("antwebLogins"' not in views
    jsp = read(os.path.join(ROOT, "web/curate/viewLogins-body.jsp"))
    assert 'request.getAttribute("antwebLogins")' in jsp, "the login list page reads the session again"
    bots = []
    for name in ("docker/caddy/Caddyfile", "docker/caddy/Caddyfile.common"):
        m = re.search(r'header_regexp User-Agent "([^"]*)"', read(os.path.join(ROOT, name)))
        bots.append(m.group(1))
    assert bots[0] == bots[1], "the bot list of Caddyfile.common (staging) differs from production's Caddyfile"
    http = sources["src/org/calacademy/antweb/util/HttpUtil.java"]
    assert 'lowerQuery.contains("%2e")' in http, "an encoded dot in upper case (%2E) passes again"
    add = sources["src/org/calacademy/antweb/curate/group/AddUploaderAction.java"]
    assert "done.lines.add(INVITE_LINK_FAILED)" in add, "a failed invite link after Add an uploader says nothing was changed again"
    names = sources["src/org/calacademy/antweb/curate/NameUpdatesAction.java"]
    assert "group.getId() <= 0) ? -1" in names, "a curator with group 0 would see every group's name updates"
    geo = sources["src/org/calacademy/antweb/curate/geolocale/EditGeolocaleAction.java"]
    assert 'message += " Updated."' not in geo, '"null Updated." can be shown again'
    for name in ("ManageGroupsAction", "ViewGroupAction"):
        src = sources[f"src/org/calacademy/antweb/curate/group/{name}.java"]
        assert "if (connection != null) connection.close();" in src, name
    form = sources["src/org/calacademy/antweb/curate/login/SaveLoginForm.java"]
    assert '!"delete".equals(getDelete())' in form, "the delete form is checked as an edit again"
    camp = sources["src/org/calacademy/antweb/curate/login/PasswordResetCampaignAction.java"]
    assert "FAILED after" in camp, "a failed campaign mark leaves no line in admin.log again"
    login = sources["src/org/calacademy/antweb/LoginAction.java"]
    created = login[login.index("login = loginDb.createAccount("):]
    assert created.index("if (login != null)") < created.index("login.setGroupId(-1)")
    up = sources["src/org/calacademy/antweb/upload/UploadDetails.java"]
    assert "!uploadDetails.getMessage().equals(" not in up and "accessCurator != getCurator()" not in up
    util = sources["src/org/calacademy/antweb/upload/UploadUtil.java"]
    assert 'new SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH)' in util
    assert "theLine = theLine == null" in util
    nightly = sources["src/org/calacademy/antweb/upload/TaxonWorksNightly.java"]
    assert "(details in the server log)\", e)" not in nightly, "the nightly logs the exception text (it can hold the URL) again"
    print("Logins, sessions, curator tools and upload fixes of 2026-10-28 in place.")


def test_nightly_triage_2026_10_28(sources):
    uda = sources["src/org/calacademy/antweb/util/UtilDataAction.java"]
    for raw in ('appendStr += ":" + bioregion;', 'appendStr += ":" + museumCode;', 'appendStr += ":" + projectName;'):
        assert raw not in uda, "a data job message repeats a form value unescaped: " + raw
    assert '"Museum not found:" + Html.esc(museumCode)' in uda and '"Project not found:" + Html.esc(projectName)' in uda
    i = uda.index("CountDb.s_isBulk = true;")
    assert "finally" in uda[i:i + 300], "CountDb.s_isBulk is not reset in finally"
    i = uda.index("SearchAction.setTempSpecimenSearchLimit(SearchAction.noSpecimenSearchLimit);")
    assert "finally" in uda[i:i + 500], "the specimen search limit is not restored in finally"
    i = uda.index('populateStatistics("nightly"')
    assert "connection.commit();" in uda[i - 300:i] and "connection.rollback()" in uda[i:i + 500], "set 4 statistics not isolated"
    i = uda.index("AdminAlertMgr.addIfFresh(result.alert()")
    assert "try {" in uda[i - 200:i], "the B36 alert can still skip the recount"
    assert 'return denied != null ? denied : mapping.findForward("notLoggedIn");' in uda
    spec = sources["src/org/calacademy/antweb/home/SpecimenDb.java"]
    assert "\" code = '\" + code + \"'\"" not in spec and 'DBUtil.sqlLiteral(code)' in spec, "calcCaste(code) quotes the code by hand"
    img = sources["src/org/calacademy/antweb/home/ImageCountDb.java"]
    assert 'taxonName.replace("\'", "\'\'")' not in img
    bio = sources["src/org/calacademy/antweb/home/BioregionTaxonDb.java"]
    assert "count(distinct s.code) specimenCount" in bio and "count(*) specimenCount" not in bio
    login = sources["src/org/calacademy/antweb/util/LoginMgr.java"]
    assert "GroupCountDb.ensureTable(conn)" in login, "group_count is created inside nightly set 3 (implicit commit)"
    sch = sources["src/org/calacademy/antweb/util/Scheduler.java"]
    i = sch.index("StatsPageAction.recount(connection)")
    assert "try {" in sch[i - 150:i] and "TaxonApiCountDb.copyGlobalCounts" in sch[i:i + 600]
    j = sch.index("TaxonApiCountDb.copyGlobalCounts")
    assert "try {" in sch[j - 150:j], "a failed statistics recount skips the API counts"
    for f in ("GeolocaleTaxonDb.java", "ProjTaxonDb.java"):
        s = sources["src/org/calacademy/antweb/home/" + f]
        i = s.index("deleteTaxon(taxonName);")
        assert "isOnAnyList(taxonName)" in s[i - 400:i], f + " deletes a name another list still holds"
    print("Nightly and count check triage of 2026-10-28: messages, finally blocks, set 4, deletions.")


def test_places_and_search_fixes_20261028(sources):
    def body(path, start, end="\n    }\n"):
        src = sources[path].replace("\r\n", "\n")
        b = src[src.index(start):]
        return b[:b.index(end) + len(end)]
    museum = "src/org/calacademy/antweb/home/MuseumDb.java"
    for start in ("private void populateSpecies(String museumCode)", "private void populateSpecimenMuseum(String museumCode)"):
        b = body(museum, start)
        assert "throws SQLException" in b.splitlines()[0] and re.search(r"catch \(SQLException e\) \{[^}]*throw e;", b), \
            start + " swallows a failure again"
    for start in ("private void insertSpecies(", "private boolean insertTaxon("):
        b = body(museum, start)
        assert "prepareStatement" in b and "+ taxonName +" not in b, start + " builds its SQL from the name again"
    project = sources["src/org/calacademy/antweb/home/ProjectDb.java"]
    assert "updateProjects(" not in project, "ProjectDb.updateProjects is back (unused, built SQL from names)"
    b = body("src/org/calacademy/antweb/home/ProjectDb.java", "public void update(Project project)")
    assert "throws SQLException" in b.splitlines()[0] and "throw e;" in b and "createStatement()" not in b
    geo = sources["src/org/calacademy/antweb/home/GeolocaleDb.java"]
    for name in ("calcHigherEndemismAdm1", "calcHigherEndemismCountry", "calcHigherEndemismSubregion"):
        b = body("src/org/calacademy/antweb/home/GeolocaleDb.java", "private int " + name + "()")
        assert "continue;" in b and "(Subregion) GeolocaleMgr" not in b and "(Region) GeolocaleMgr" not in b, name
    assert "g.parent = '\" + parent" not in geo
    for nav in ("regionNav", "subregionNav", "countryNav", "adm1Nav"):
        with open(os.path.join(ROOT, "web/common", nav + ".jsp"), encoding="utf-8") as f:
            jsp = f.read()
        assert not re.search(r"\.getName\(\) \+ \"</a>", jsp), nav + ".jsp prints a place name unescaped"
    adv = body("src/org/calacademy/antweb/search/AdvancedSearch.java", "protected ArrayList<ResultItem> createInitialResults()")
    assert "try (Statement stmt = connection.createStatement())" in adv
    where = sources["src/org/calacademy/antweb/search/AdvancedSearch.java"]
    assert where.count('getSearchString("sp.microhabitat"') == 1, "microhabitat is added twice"
    assert "elevationValue(elevation)" in where
    recent = sources["src/org/calacademy/antweb/search/RecentImageSearch.java"]
    assert recent.count("executeQuery(theQuery)") == 1, "the recent image search runs its query twice"
    search = sources["src/org/calacademy/antweb/search/SearchAction.java"].replace("\r\n", "\n")
    ex = search[search.index("public ActionForward execute("):search.index("private void searchLog(")]
    assert re.search(r"Check\.busy\(connection, request, mapping\);[^\n]*\n(\s*//[^\n]*\n)*\s*DBUtil\.close\(connection", ex), \
        "SearchAction keeps the busy-check connection during the search"
    assert "return null;" not in ex, "SearchAction.execute gives a blank page on an error"
    assert "queryRowCount >= SearchAction.getSpecimenSearchLimit()" in search and "resultSetSize >= SearchAction.getSpecimenSearchLimit()" not in search
    assert "s_fullSearchLimiter.tryAcquire()" in search and "s_fullSearchLimiter.release()" in search
    caste = sources["src/org/calacademy/antweb/search/AdvancedSearchResults.java"]
    assert "resultItemO2.getCaste() + resultItemO2.getSubcaste()" in caste
    for path in ("src/org/calacademy/antweb/search/CompareResultsAction.java", "src/org/calacademy/antweb/search/FieldGuideResultsAction.java"):
        assert "thisItem.getSpecies().equals(" not in sources[path], path
    prep = sources["src/org/calacademy/antweb/search/PrepareMapResultsAction.java"]
    assert re.search(r"\n\s*if \(results == null\) \{", prep), "PrepareMapResultsAction has no check for missing results"
    group = sources["src/org/calacademy/antweb/search/GenericSearchResults.java"]
    assert "GroupMgr.getGroup(thisItem.getGroupName()).getId()" not in group
    print("Museum lists, project updates, endemism, place navigation and the advanced search fixes of 2026-10-28 hold.")


def main():
    sources = java_sources()
    assert len(sources) > 400, len(sources)
    test_login_updates_keep_created(sources)
    test_no_delete_all_page_text(sources)
    test_titles()
    test_core_util_fixes(sources)
    test_old_manage_login_page_gone()
    test_admin_links_column_stacks_on_phones()
    test_too_busy_for_bots_is_returned(sources)
    test_simultaneous_limits(sources)
    test_low_public_pages_findings(sources)
    test_adm1_totals_not_only_live(sources)
    test_global_recounted_after_cleanup_and_synonym_bound(sources)
    test_copyright_rows_and_compare_by_species(sources)
    test_post_means_post(sources)
    test_no_lost_ifs_and_whole_home_page(sources)
    test_triage_20261028(sources)
    test_nightly_triage_2026_10_28(sources)
    test_places_and_search_fixes_20261028(sources)


if __name__ == "__main__":
    main()
