"""Source checks of the 2026-10-27 fixes to the count check, the curator tools and the database classes
that need no server or database (the parts without a database are also in the Java unit tests
AdminDataFixesTest and ValidateSpeciesReportTest).

Count check (countCheck.do):
- a place table that cannot be read is listed as failed, not as checks with 0 numbers compared;
- names changed since the nightly run are compared as MySQL compares them (case);
- the Global column (allantwebants) counts every specimen of the name (docs/numbers.md #43), and the rows
  under a list row use the same status rule as CountInvariants.childStatus;
- the Subfamilies number is checked also before the place image numbers exist;
- the rotating sample counts days from 1970; the latest report is read once; the home page check reads
  the newest statistics row as the other checks do (created, then id).
Curator tools: the corrected species list holds only taxon names; the worldants upload record keeps the
count from before the reload; a name not found is skipped in the Species List Tool; the project form's
Live box can be unticked; group, museum and adm1 pages refuse or show bad values plainly; downloads are UTF-8.
Database classes: values from uploaded files and place names are quoted or bound in SQL; admin lists escape
names; the species-list history keeps each place's rows; all copyrights are read; taxon page text with
http:// addresses no longer fails; and more (see each check).

Run: python3 tests/unit/test_admin_data_fixes.py   (also in .github/workflows/tests.yaml)
"""
import os
import re

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SRC = os.path.join(ROOT, "src", "org", "calacademy", "antweb")


def read(rel):
    with open(os.path.join(SRC, rel), encoding="utf-8", errors="replace") as f:
        return f.read().replace("\r\n", "\n")


def method(src, start, end):
    body = src[src.index(start):]
    return body[:body.index(end)] if end else body


def code(text):
    """Without // comment lines."""
    return "\n".join(l for l in text.splitlines() if not l.strip().startswith("//"))


def test_count_check():
    inv = read("util/CountInvariants.java")
    totals = method(inv, "void totals(String type)", "String[] ranks")
    assert "checks.get(checks.size() - 1).error == null" in totals, "a failed place table read is dropped from the table"
    assert "order by created desc, id desc limit 1" in inv and "order by created desc limit 1" not in inv
    cc = read("util/CountCheck.java")
    since = method(cc, "Set<String> changedNamesSince(Result result)", "void imagesSince")
    assert "CountInvariants.keySet()" in since and "new HashSet<>()" not in since
    filtered = method(cc, "boolean statusFiltered()", "void setKey")
    assert "allantwebants" not in code(filtered), "the Global column counts every status (numbers.md #43)"
    children = method(cc, "int checkChildren(", "int checkGlobal(")
    assert "childStatusCondition()" in children and "statusFiltered()" not in children
    recount = method(cc, "void recount(Place place", "private static final java.util.regex.Pattern LOG_LINE")
    assert "checkSubfamilyTotal(" in recount, "the Subfamilies number is checked only with place images"
    action = read("util/CountCheckAction.java")
    job = read("util/CountCheckJob.java")
    assert "getDayOfYear" not in action and "getDayOfYear" not in job
    assert "rotationDay()" in action and "rotationDay()" in job and "toEpochDay()" in action
    execute = method(action, "public ActionForward execute(", "static java.util.Map<Integer, int[]> curatorNumbers()")
    assert execute.count("CountCheckJob.latest()") == 1, "the latest job is read more than once"
    print("Count check: failed reads shown, rules shared, rotation across the new year.")


def test_curator_tools():
    report = read("curate/speciesList/ValidateSpeciesReport.java")
    assert "isUpperCase" not in code(report) and "hasSuggestedValidName()" in report
    up = read("curate/speciesList/SpeciesListUploader.java")
    reload_ = method(up, "public UploadDetails worldantsReload()", "public static final int WORLDANTS_LOW_COUNT")
    assert reload_.index("getWorldantsCount()") < reload_.index("reloadSpeciesList("), "count read after the reload"
    tool = read("curate/speciesList/SpeciesListToolAction.java")
    assert "if (searchTaxon == null) continue;" in tool
    form = method(read("curate/project/EditProjectForm.java"), "public void reset(", "public Project getProject()")
    assert re.search(r"^\s*isLive = false;", form, re.M), "an unticked Live box keeps its old value"
    group = read("curate/group/SaveGroupAction.java")
    assert "int adminLoginId = Integer.parseInt(form.getAdminLoginId());" not in group
    adm1 = read("curate/geolocale/Adm1MgrAction.java")
    assert "stmt.setString(1, country.getName());" in adm1 and "limit 21" in adm1
    museums = read("curate/ManageMuseumsAction.java")
    assert "museumCode == null || museumCode.isEmpty()" in museums
    assert "text/tab-separated-values; charset=UTF-8" in read("curate/speciesList/ValidateSpeciesListAction.java")
    for f in ("curate/speciesList/ProjTaxonLog.java", "curate/speciesList/GeolocaleTaxonLog.java"):
        assert "Html.esc(login.getName())" in read(f), f
    names = read("curate/NameUpdatesAction.java")
    assert "Html.escAttr(from)" in names and "Html.escAttr(to)" in names
    print("Curator tools: corrected list, worldants record, project Live box, group, museum, adm1 pages.")


def test_sql_values_quoted():
    upload = read("home/UploadDb.java")
    for name in ("private int insertGenus(", "public int insertSubfamily(", "public String getAntwebSubfamily(", "public void addUploadLine("):
        body = code(method(upload, name, "\n    }\n"))
        assert not re.search(r"'\" \+ (taxonName|family|subfamily|genus|source|fileName)\b", body), name
    antweb = read("home/AntwebDb.java")
    for name in ("public boolean isExists(", "public boolean isGenusExists(", "public int delete("):
        assert not re.search(r"'\" \+ (taxonName|subfamily|genus)\b", method(antweb, name, "\n    }")), name
    homonym = read("home/HomonymDb.java")
    for name in ("public boolean isHomonym(", "public boolean isExistingSubfamilyForAGenus(", "public boolean isValidSubfamilyForGenus("):
        assert not re.search(r"'\" \+ (taxonName|family|genus)\b", method(homonym, name, "\n    }")), name
    maps = read("home/ObjectMapDb.java")
    assert ", '\" + map.get" not in maps and "museum_code = '\" +" not in maps
    worldants = read("home/WorldantsUploadDb.java")
    assert "'\" + validateMessage" not in worldants and "'\" + logFileName" not in worldants
    geo = read("home/GeolocaleDb.java")
    children = method(geo, "private ArrayList<Geolocale> getChildrenWithTaxonDB(", "// HashMap implementation.")
    assert "gt.taxon_name = ?" in children and "'\" + taxonName" not in children
    assert "Formatter.escapeQuotes(parent.getName())" not in geo
    assert "where name like ?" in geo and "like '%\" + text" not in geo
    build = method(geo, "public static void buildGetChildrenWithTaxonHash(", "public ArrayList<Geolocale> getChildrenWithTaxonHash(")
    assert "childrenWithTaxonHash = hash;" in build
    assert "project_name='\" + name" not in read("home/ProjectDb.java")
    assert "name = '\" + bioregionName" not in method(read("home/BioregionDb.java"), "public Bioregion getBioregion(String", "public Bioregion getBioregionWithClause(")
    print("Uploaded values and place names are quoted or bound in SQL.")


def test_database_classes():
    for f, scope in (("home/ProjTaxonLogDb.java", "project_name"), ("home/GeolocaleTaxonLogDb.java", "geolocale_id")):
        src = read(f)
        line = [l for l in src.splitlines() if "set log_id = \" + maxLogId" in l][0]
        nxt = src[src.index(line):src.index(line) + len(line) + 120]
        assert scope in nxt, f + ": the log rows of other places get this place's log id"
    assert "from geolocale_taxon gt\"\n              + \" where gt.geolocale_id" in read("home/GeolocaleTaxonLogDb.java")
    login = read("home/LoginDb.java")
    assert login.count("Html.esc(name)") >= 2 and login.count("Html.esc(groupName)") >= 2
    spec = method(read("home/SpecimenDb.java"), "getIntroducedByGroup", "public boolean hasSpecimen(")
    assert "Html.esc(group)" in spec and "Html.url(code)" in spec
    stats = read("home/StatisticsDb.java")
    assert "Html.esc(action)" in stats and "Html.esc(groupName)" in stats
    copy = method(read("home/CopyrightDb.java"), "private void getCopyrights()", None)
    loop = copy[copy.index("while (rset.next())"):copy.index("} finally")]
    assert "new ArrayList" not in loop, "the copyright list is made again for each row"
    desc = read("home/DescEditDb.java")
    assert "value.substring(indexOfHttp - 1" not in desc
    sl = read("home/SpeciesListDb.java")
    assert "            taxonSet.setCuratorId(login.getId());" not in sl
    reset = method(read("home/PasswordResetDb.java"), "public Account findAccount(", "public Account getAccount(")
    assert reset.index("email = ?") < reset.index("name = ?"), "an email address is looked up as a username first"
    status = method(read("home/UploadDb.java"), "public Status getStatus(String taxonName)", "String currentValidName")
    assert "dummyTaxon == null" in status
    museum = read("home/MuseumDb.java")
    assert "count(distinct s.code) specimenCount" in museum
    higher = method(museum, "private void insertHigherTaxon(", "private boolean insertTaxon(")
    assert re.search(r"s_lastCode = code;\s*(//[^\n]*\n\s*)*s_lastSubfamily = \"\";\s*s_lastGenus = \"\";", higher)
    bio = method(read("home/BioregionDb.java"), "private int updateBioregionTaxonField(", "\n    }\n")
    assert "taxonName == null" in bio, "calcIntroduced's reset of a bioregion does nothing"
    for f in ("home/GeolocaleDb.java", "home/BioregionDb.java", "home/MuseumDb.java"):
        assert "colors[i]);" not in code(read(f)), f
    assert "latitude > 90 &&" not in read("geolocale/LatLon.java")
    print("Database classes: escaped admin lists, scoped history, copyrights, museum and bioregion counts.")


def main():
    test_count_check()
    test_curator_tools()
    test_sql_values_quoted()
    test_database_classes()


if __name__ == "__main__":
    main()
