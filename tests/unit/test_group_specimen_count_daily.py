"""The daily update recounts every group's stored specimen count (ant_group.upload_specimens), 2026-11-11.

Only a group's own upload wrote the number, so anything else that changed a group's specimens (a correction, a
deletion, a clean-up) left it stale, and the count check reported "specimens (statistics.do by upload, live) =
ant_group.upload_specimens" as a real difference every day until the group's next upload (production 2026-11-11:
group 52, no upload for months, 21,102 specimens, stored 21,099).

Source checks (the behaviour is tested against a database by home.GroupSpecimenCountDbTest):
  - set 3, which production and staging both run (staging skips set 5), calls GroupDb.recountUploadSpecimens and
    writes its line to compute.log;
  - the call is the set's last statement, and the set commits when it returns: the ant_group rows the recount changes
    are locked until that commit, and an upload of such a group that ends meanwhile waits for them (review of
    2026-11-11: it ran before the group counts and the type crawl, so the rows were locked for minutes);
  - the recount is two selects and a bound update by id, not one UPDATE with the specimen count as a subquery (that
    statement keeps shared locks on the specimen rows it read until the set commits); the update writes only where
    the stored number is still the one read (an upload that ended since then wrote a newer one), one row at a time;
  - nothing in it commits.
"""
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8").replace("\r\n", "\n")


def body(src, signature):
    """The text of a method from its signature to the closing brace of its body."""
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
    return re.sub(r"(?m)^\s*//[^\n]*", "", text)


def test_set_3_recounts_every_groups_specimens_as_its_last_statement():
    src = read("src/org/calacademy/antweb/util/UtilDataAction.java")
    start = src.index('if ("set3".equals(action)) {')
    set3 = code(src[start:src.index('if ("set4".equals(action)) {', start)])
    call = "String groupSpecimens = new GroupDb(connection).recountUploadSpecimens();"
    log = 'LogMgr.appendLog("compute.log", "  " + groupSpecimens, true);'
    assert call in set3, "set 3 no longer recounts every group's stored specimen count"
    assert log in set3, "its line no longer goes to compute.log"
    before, after = set3.split(call)
    # everything else of the set is before it: the group counts and the type crawl among them
    for step in ('doAction("populateBioregion"', "new GroupCountDb(connection).recountAll();", 'doAction("crawlForType"'):
        assert step in before, step
    # after it the set only writes its lines: no job, no statement on the set's connection
    assert "doAction(" not in after and "connection" not in after, after
    assert after.index(log) < after.index("Set 3 done")
    assert re.fullmatch(r'\s*LogMgr\.appendLog\("compute\.log", "  " \+ groupSpecimens, true\);\s*message \+= " " \+ groupSpecimens;\s*'
                        r'LogMgr\.appendLog\("compute\.log", "  Set 3 done \(".*?, true\);\s*\}\s*', after, re.S), after
    # and the set commits when doAction returns: in the request (the daily update) and as a background job (an admin)
    for caller in ("OperationDetails operationDetails = doAction(action, theForm, accessLogin, accessGroup, connection, request, mapping);",
                   "OperationDetails operationDetails = doAction(action, form, accessLogin, accessGroup, connection, null, null);"):
        rest = code(src.split(caller, 1)[1])
        assert re.match(r"\s*(String link = null;\s*)?if \(operationDetails instanceof UploadDetails\) \{[^}]*\}\s*connection\.commit\(\);", rest), caller
    # every run reaches set 3: the Scheduler starts it on production and on staging alike; only set 5 is skipped on staging
    scheduler = read("src/org/calacademy/antweb/util/Scheduler.java")
    assert 'DailyUpdate.start("set3");' in scheduler
    assert 'DailyUpdate.skip("set5", "skipped on staging (memory)");' in scheduler
    assert not re.search(r'DailyUpdate\.skip\("set3"', scheduler)


def test_the_recount_is_two_selects_and_a_bound_update():
    src = read("src/org/calacademy/antweb/home/GroupDb.java")
    assert 'public static final String SPECIMENS_LOG = "Group specimen counts";' in src
    recount = code(body(src, "public String recountUploadSpecimens()"))
    assert '"select access_group, count(*) from specimen where access_group is not null group by access_group"' in recount
    assert '"select id, upload_specimens from ant_group order by id"' in recount
    # written only where the stored number is still the one read; one row at a time, each result counted
    assert '"update ant_group set upload_specimens = ? where id = ? and upload_specimens <=> ?"' in recount
    assert "addBatch" not in recount and "executeBatch" not in recount
    assert re.search(r"if \(stmt\.executeUpdate\(\) > 0\) \{\s*\+\+changed;", recount)
    assert "left.add(change[0]);" in recount
    # one statement that updates ant_group, and it does not read the specimen table
    assert recount.count("update ant_group") == 1
    # a group without specimens gets 0
    assert "int now = specimens.getOrDefault(id, 0);" in recount
    # in the set's transaction: nothing here commits, rolls back or changes autocommit
    for word in ("commit(", "rollback(", "setAutoCommit("):
        assert word not in recount, word
    # a failure is said in the line, not thrown
    assert re.search(r'catch \(SQLException \| RuntimeException e\) \{.*?return SPECIMENS_LOG \+ ": " \+ \(changed == 0 \? "" : .*?", then "\)\s*'
                     r'\+ "FAILED \(" \+ e \+ "\)";', recount, re.S)
    # the uploading group's own number is still written at the end of its upload
    assert "groupDb.updateUploadSpecimens(group);" in read("src/org/calacademy/antweb/upload/UploadAction.java")


if __name__ == "__main__":
    test_set_3_recounts_every_groups_specimens_as_its_last_statement()
    test_the_recount_is_two_selects_and_a_bound_update()
    print("ok")
