"""Two calls that only the wiring keeps, each with tests of its parts but none of the call itself (review of PR #341,
2026-11-11). Source checks: driving either call from a test needs the whole upload or the web application's start.

1. The specimen upload stores a country written with other capitals, accents or blanks under AntWeb's name ("México"
   becomes Mexico) and lists the change in its report. CountrySpellingTest (Java) tests the lookup
   (GeolocaleMgr.getCountrySpelledLike), the report line and the report section; this test fails when
   SpecimenUploadParse.parseLine stops calling the lookup, or stops adding the report line.
2. The TaxonWorks archive is requested 25 minutes before the daily update, so that TaxonWorks has built a fresh one when
   set 1 reaches the CAS upload. TaxonWorksNightlyDecisionsTest (Java) tests when and what; this test fails when
   SessionRequestFilter.runTask, which starts the server's timers, no longer schedules it.
"""
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def body(src, signature):
    """The text of a method from its signature to the closing brace of its body (comments and strings have no
    unbalanced braces in the two methods read here)."""
    start = src.index(signature)
    depth, i = 0, src.index("{", start)
    while True:
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return src[start:i + 1]
        i += 1


def code(text):
    """Java source without its comments (a call that is commented out is not a call)."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def test_the_upload_stores_a_respelled_country_and_reports_it():
    src = read("src/org/calacademy/antweb/upload/SpecimenUploadParse.java")
    parse = code(body(src, "protected String parseLine(String theLine, int lineNum, Hashtable specimenItem, Hashtable taxonItem"))
    exact = parse.index("validCountry = GeolocaleMgr.getValidCountry(country);")
    stored = parse.index('specimenItem.put("country", country);', exact)
    step = parse[exact:stored]
    # only when the exact name finds no country: the country spelled like it
    lookup = re.search(r"if \(validCountry == null\) \{\s*validCountry = GeolocaleMgr\.getCountrySpelledLike\(country\);", step)
    assert lookup, "parseLine no longer looks the country up by its spelling when the exact name finds none"
    # the report line, only when the spelling found one, with what the file had and what is stored
    report = re.search(r"if \(validCountry != null\) \{\s*getMessageMgr\(\)\.addToMessages\(MessageMgr\.countrySpellingCorrected, "
                       r"countrySpellingLine\(country, validCountry\.getName\(\)\)\);", step[lookup.end():])
    assert report, "parseLine no longer lists the corrected spelling in the upload report"
    # and AntWeb's name is what is stored: after the lookup, before the specimen's country is set
    rest = step[lookup.end():]
    assert re.search(r"if \(validCountry != null\) \{\s*country = validCountry\.getName\(\);", rest), "the valid country's name is no longer stored"
    # the parts the Java test covers are the ones called
    mgr = read("src/org/calacademy/antweb/util/GeolocaleMgr.java")
    assert "public static @Nullable Country getCountrySpelledLike(String name)" in mgr
    assert "public static String countrySpellingLine(String uploaded, String stored)" in src
    assert 'public static final String countrySpellingCorrected = "countrySpellingCorrected";' in read("src/org/calacademy/antweb/upload/MessageMgr.java")


def test_the_server_start_schedules_the_early_taxonworks_request():
    src = read("src/org/calacademy/antweb/util/SessionRequestFilter.java")
    run = code(body(src, "public void runTask()"))
    call = "org.calacademy.antweb.upload.TaxonWorksNightly.scheduleEarlyRequest(new Timer(\"taxonworks-early-request\", true), Scheduler.LAUNCHTIME);"
    assert call in run, "runTask no longer schedules the early TaxonWorks request"
    # not in dev (after the return), after the daily update's own timer, and a failure there stops neither that timer
    # nor the mount check
    dev, daily, early, mounts = (run.index(x) for x in ("if (AntwebProps.isDevMode()) return;", "scheduleNextRun(new Timer());", call, "MountCheck.start();"))
    assert dev < daily < early < mounts
    assert re.search(r"try \{\s*" + re.escape(call) + r"\s*\} catch \(RuntimeException e\) \{", run), "the early request is scheduled in its own try"
    # runTask is what the web application's start calls
    assert re.search(r"\brunTask\(\);", code(src.replace(body(src, "public void runTask()"), ""))), "nothing calls runTask"
    nightly = read("src/org/calacademy/antweb/upload/TaxonWorksNightly.java")
    assert "public static void scheduleEarlyRequest(final java.util.Timer timer, final int launchHour)" in nightly


if __name__ == "__main__":
    test_the_upload_stores_a_respelled_country_and_reports_it()
    test_the_server_start_schedules_the_early_taxonworks_request()
    print("ok")
