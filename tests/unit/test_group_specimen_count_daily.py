"""The daily update recounts every group's stored specimen count (ant_group.upload_specimens), 2026-11-11.

Only a group's own upload wrote the number, so anything else that changed a group's specimens (a correction, a
deletion, a clean-up) left it stale, and the count check reported "specimens (statistics.do by upload, live) =
ant_group.upload_specimens" as a real difference every day until the group's next upload (production 2026-11-11:
group 52, no upload for months, 21,102 specimens, stored 21,099).

Source checks (the behaviour is tested against a database by home.GroupSpecimenCountDbTest):
  - set 3, which production and staging both run (staging skips set 5), calls GroupDb.recountUploadSpecimens and
    writes its line to compute.log, before the group counts (GroupCountDb.recountAll lists the groups by that number);
  - the recount is two selects and a bound update by id, not one UPDATE with the specimen count as a subquery (that
    statement keeps shared locks on the specimen rows it read until the set commits);
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


def test_set_3_recounts_every_groups_specimens_before_the_group_counts():
    src = read("src/org/calacademy/antweb/util/UtilDataAction.java")
    start = src.index('if ("set3".equals(action)) {')
    set3 = code(src[start:src.index('if ("set4".equals(action)) {', start)])
    call = "String groupSpecimens = new GroupDb(connection).recountUploadSpecimens();"
    log = 'LogMgr.appendLog("compute.log", "  " + groupSpecimens, true);'
    counts = "new GroupCountDb(connection).recountAll();"
    assert call in set3, "set 3 no longer recounts every group's stored specimen count"
    assert log in set3, "its line no longer goes to compute.log"
    assert set3.index('doAction("populateBioregion"') < set3.index(call) < set3.index(log) < set3.index(counts) < set3.index("Set 3 done")
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
    assert 'prepareStatement("update ant_group set upload_specimens = ? where id = ?")' in recount
    # one statement that updates ant_group, and it does not read the specimen table
    assert recount.count("update ant_group") == 1
    # a group without specimens gets 0
    assert "int now = specimens.getOrDefault(id, 0);" in recount
    # in the set's transaction: nothing here commits, rolls back or changes autocommit
    for word in ("commit(", "rollback(", "setAutoCommit("):
        assert word not in recount, word
    # a failure is said in the line, not thrown
    assert re.search(r'catch \(SQLException \| RuntimeException e\) \{.*?return SPECIMENS_LOG \+ ": FAILED \(" \+ e \+ "\)";', recount, re.S)
    # the uploading group's own number is still written at the end of its upload
    assert "groupDb.updateUploadSpecimens(group);" in read("src/org/calacademy/antweb/upload/UploadAction.java")


if __name__ == "__main__":
    test_set_3_recounts_every_groups_specimens_before_the_group_counts()
    test_the_recount_is_two_selects_and_a_bound_update()
    print("ok")
