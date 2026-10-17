"""TinyMCE page editor, tested locally in a real browser (no AntWeb server needed).

Serves this repository's web/ folder from a small local server, with a stand-in for the curator
edit form (the TinyMCE branch of web/common/descEdit/ckEditorMinConfigForm.jsp: the same scripts,
editSafety.js and antwebEditor.js, a stub editSession.do, and a save address that only records
what the form sent). The server's sanitizer is the real one: tests/e2e/editor/EditorRules.java
is compiled with src/.../util/HtmlSanitizer.java (JDK only) and gives editorValidElements() and
sanitize().

Checks:
  - the shared setup sets license_key 'gpl', and TinyMCE 8 really opens read only without it;
  - round trip: representative stored HTML (tables, font/center/div, images, video iframe, &nbsp;,
    escaped &lt;, loose text with <br>, Word styles) opened and saved unchanged is sent back
    byte for byte; the editor's own HTML, after the sanitizer, shows the same page (normalised
    DOM) and adds no <p> around text that had none;
  - an edit adds no paragraph to a field stored without <p>; Enter inside loose text gives <br><br>;
  - the ready flag, empty-save block, draft restore after a reload (the "Leave site?" dialog is
    accepted) and the phone-width layout of the form.

Skipped when Playwright/Chromium or a JDK (javac) is missing, except on GitHub Actions.
Never touches any AntWeb server.
"""
import functools
import html
import http.server
import json
import re
import os
import shutil
import socketserver
import subprocess
import tempfile
import threading
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

import pytest

sync_api = pytest.importorskip("playwright.sync_api", reason="Playwright not installed")

REPO = Path(__file__).resolve().parents[2]
WEB = REPO / "web"
SETUP_JS = WEB / "common" / "editors" / "antwebEditor.js"
TINYMCE_BASE = "/common/editors/tinymce/8.10.0"
IN_CI = bool(os.environ.get("GITHUB_ACTIONS"))
READY_MS = 30000

# Stored HTML as found in description_edit / object_edit (field, html).
SAMPLES = [
    ("overview", '<p>Workers of <i>Pheidole</i> are <b>small</b>&nbsp;and <font color="#990000" face="Arial">red</font>.</p>\n<center>Fig. 1</center>'),
    ("identification", '<table border="1" cellpadding="2" width="100%"><tr><td colspan="2" align="center"><b>Head</b></td></tr><tr><td>a</td><td valign="top">b</td></tr></table>'),
    ("images", '<br><a href="https://www.antweb.org/web/curator/23/x.jpg"><img class="taxon_page_img" src="https://www.antweb.org/web/curator/23/x.jpg"></a>'),
    ("images", '<a href="https://www.antweb.org/web/curator/23/a.jpg"><img class="taxon_page_img" src="https://www.antweb.org/web/curator/23/a.jpg"></a><br><a href="https://www.antweb.org/web/curator/23/b.jpg"><img class="taxon_page_img" src="https://www.antweb.org/web/curator/23/b.jpg"></a>'),
    ("videos", '<iframe width="560" height="315" src="https://www.youtube.com/embed/abc123" frameborder="0" allowfullscreen></iframe>'),
    ("comments", '<p class="MsoNormal" style="margin-bottom: 0in; mso-margin-top-alt: auto;"><span style="font-size: 10pt; font-family: Arial;">Text</span></p>'),
    ("notes", 'Line one<br>Line two<br />Line &amp; three &lt;tag&gt; and "quotes"'),
    ("notes", 'Collected by B.L. Fisher.&nbsp;&nbsp;See <a href="https://www.antweb.org/specimen.do?name=casent0103190">CASENT0103190</a>.'),
    ("references", '<ol><li>Bolton, B. 1995. <i>A new general catalogue</i>. 504 pp.</li></ol><p>H<sub>2</sub>O x<sup>2</sup> <a href="http://www.antcat.org/catalog/1" target="_blank">AntCat</a></p>'),
    ("biology", '<div align="center"><h3>Title</h3><span style="font-size: 10pt; color: rgb(0, 0, 0);">x</span></div><p align="justify">Para <u>under</u> <strike>old</strike> <s>s</s></p>'),
    ("distribution", '<p>Madagascar: Toamasina &mdash; 12&deg;S</p><ul><li>one</li><li>two<ul><li>nested</li></ul></li></ul><hr><blockquote>quote</blockquote><pre>  pre  text</pre>'),
    ("taxanomicnotes", 'Intro text with <i>Camponotus</i> &lt;b&gt;<table border="1"><tr><td>1</td></tr></table>after the table<br>second line'),
    ("contents", '<center><font size="2">Old <b>centred</b> text</font></center><br>Loose line after'),
]

# Normalised page: attribute order, quoting, entities, style spacing, rgb()/hex and white space
# between block tags (the same rule as the trial page's "same after normalising").
CANON_JS = r"""
(html) => {
  const BLOCK = /[ \t\r\n]*(<\/?(?:p|div|h[1-6]|ul|ol|li|dl|dt|dd|table|thead|tbody|tfoot|tr|td|th|caption|blockquote|pre|center|br|hr)\b[^>]*>)[ \t\r\n]*/g;
  const doc = new DOMParser().parseFromString('<!DOCTYPE html><body>' + html, 'text/html');
  const walk = (node) => {
    let out = '';
    node.childNodes.forEach((c) => {
      if (c.nodeType === 3) {
        out += c.nodeValue.replace(/[ \t\r\n]+/g, ' ').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
      } else if (c.nodeType === 1) {
        const name = c.nodeName.toLowerCase();
        const attrs = Array.prototype.map.call(c.attributes, (a) => {
          let v = a.value;
          if (a.name === 'style') {
            v = v.split(';').map((d) => d.trim().replace(/\s*:\s*/, ': ')).filter(Boolean).join('; ')
              .replace(/rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)/gi, (m, r, g, b) =>
                '#' + [r, g, b].map((x) => ('0' + (+x).toString(16)).slice(-2)).join('')).toLowerCase();
          } else { v = v.trim(); }
          return a.name + '="' + v.replace(/&/g, '&amp;').replace(/"/g, '&quot;') + '"';
        }).sort();
        out += '<' + name + (attrs.length ? ' ' + attrs.join(' ') : '') + '>';
        if (!/^(br|hr|img|iframe)$/.test(name)) out += walk(c) + '</' + name + '>';
        else if (name === 'iframe') out += '</iframe>';
      }
    });
    return out;
  };
  return walk(doc.body).replace(BLOCK, '$1').trim();
}
"""


# ---------- the server's sanitizer (Java) ----------

class Rules:
    def __init__(self, classes: Path):
        self.classes = classes
        out = self._run(["valid"])
        self.valid, self.valid_videos = out.split("\n", 1)

    def _run(self, args, stdin=""):
        r = subprocess.run(["java", "-cp", str(self.classes), "EditorRules", *args], input=stdin.encode("utf-8"),
                           capture_output=True, check=True)
        return r.stdout.decode("utf-8")

    def sanitize(self, items):
        """items: [(html, videos)] -> [sanitized html]"""
        data = "\0".join(("1|" if v else "0|") + h for h, v in items)
        return self._run(["sanitize"], data).split("\0")


@pytest.fixture(scope="module")
def rules():
    if not shutil.which("javac") or not shutil.which("java"):
        if IN_CI:
            pytest.fail("javac not found")
        pytest.skip("javac not found (needed to run the server's HtmlSanitizer)")
    tmp = Path(tempfile.mkdtemp(prefix="editor-rules-"))
    subprocess.run(["javac", "-nowarn", "-encoding", "UTF-8", "-d", str(tmp),
                    str(REPO / "src/org/calacademy/antweb/util/HtmlSanitizer.java"),
                    str(Path(__file__).parent / "editor" / "EditorRules.java")], check=True, capture_output=True)
    yield Rules(tmp)
    shutil.rmtree(tmp, ignore_errors=True)


# ---------- local stand-in server ----------

def form_page(stored: str, field: str, valid: str, had_text: bool, refused=False) -> str:
    """The TinyMCE branch of ckEditorMinConfigForm.jsp (and editorScripts.jsp), for a local test."""
    js = json.dumps
    return f"""<!DOCTYPE html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>edit form stand-in</title>
<script src="{TINYMCE_BASE}/tinymce.min.js?v=test"></script>
<script src="/common/editors/antwebEditor.js?v=test"></script>
</head><body><div id="page_contents">
<form method="POST" action="/__save"><input type="hidden" name="_csrf" value="old">
<input type="hidden" name="editField" value="{html.escape(field)}">
<div id="descEditSafetyBar" style="display:none"></div>
<input type="hidden" name="loadedHash" value="{'h' if had_text else 'absent'}">
<input type="hidden" name="editorReady" value="0">
<input type="hidden" name="clearField" value="0">
<textarea id="editor1" name="contents" rows="20" style="width:100%">{html.escape(stored, quote=False)}</textarea>
<script src="/common/descEdit/editSafety.js?v=test"></script>
<script>
  AntwebEditSafety.watch('editor1', {{
    draftKey: 'antweb.draft.1.taxon:test.{field}', loadedHash: '{'h' if had_text else 'absent'}',
    hadText: {js(had_text)}, refused: {js(refused)}, fieldName: {js(field)},
    sessionUrl: '/editSession.do', loginUrl: '/login.do', editor: 'tinymce',
    tinymce: {{ baseUrl: '{TINYMCE_BASE}', cacheSuffix: '?v=test', validElements: {js(valid)}, height: 420 }}
  }});
</script>
<input type="submit" value="Save"> <button type="button" class="descEditClear">Clear this field</button>
</form></div></body></html>"""


class Site:
    def __init__(self, rules_):
        self.rules = rules_
        self.pages = {}
        self.posts = []
        site = self

        class H(http.server.SimpleHTTPRequestHandler):
            def log_message(self, *a):
                pass

            def _send(self, code, ctype, body: str):
                b = body.encode("utf-8")
                self.send_response(code)
                self.send_header("Content-Type", ctype)
                self.send_header("Content-Length", str(len(b)))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                self.wfile.write(b)

            def do_GET(self):
                path = urlsplit(self.path).path
                if path == "/editSession.do":
                    return self._send(200, "application/json", '{"loggedIn":true,"canEdit":true,"csrf":"fresh"}')
                if path in site.pages:
                    return self._send(200, "text/html;charset=utf-8", site.pages[path])
                if path == "/__blank":
                    return self._send(200, "text/html;charset=utf-8", "<!DOCTYPE html><html><body><textarea id=t>x</textarea></body></html>")
                return super().do_GET()

            def do_POST(self):
                n = int(self.headers.get("Content-Length", "0"))
                data = parse_qs(self.rfile.read(n).decode("utf-8"), keep_blank_values=True)
                site.posts.append({k: v[0] for k, v in data.items()})
                return self._send(200, "text/html;charset=utf-8", "<!DOCTYPE html><p id=saved>saved</p>")

        self.server = socketserver.ThreadingTCPServer(("127.0.0.1", 0), functools.partial(H, directory=str(WEB)))
        self.server.daemon_threads = True
        self.base = f"http://127.0.0.1:{self.server.server_address[1]}"
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def add_form(self, name, stored, field, had_text=None):
        valid = self.rules.valid_videos if field == "videos" else self.rules.valid
        if had_text is None:
            had_text = bool(stored.strip())
        self.pages["/" + name] = form_page(stored, field, valid, had_text)
        return self.base + "/" + name


@pytest.fixture(scope="module")
def site(rules):
    s = Site(rules)
    yield s
    s.server.shutdown()


@pytest.fixture(scope="module")
def browser():
    with sync_api.sync_playwright() as p:
        try:
            b = p.chromium.launch()
        except Exception as e:
            if IN_CI:
                raise
            pytest.skip(f"Chromium not available: {str(e).splitlines()[0]}")
        yield b
        b.close()


@pytest.fixture()
def page(browser):
    ctx = browser.new_context(viewport={"width": 1280, "height": 900})
    pg = ctx.new_page()
    pg.errors = []
    pg.on("pageerror", lambda e: pg.errors.append(str(e)))
    yield pg
    ctx.close()


def open_form(page, url):
    page.goto(url)
    page.wait_for_function("document.querySelector('input[name=editorReady]').value === '1'", timeout=READY_MS)
    return page


def content(page):
    return page.evaluate("tinymce.get('editor1').getContent()")


def save(page, site):
    before = len(site.posts)
    page.click("input[type=submit]")
    page.wait_for_selector("#saved", timeout=15000)
    assert len(site.posts) == before + 1
    return site.posts[-1]


def canon(page, h):
    return page.evaluate(CANON_JS, h)


# ---------- licence key ----------

def test_shared_setup_sets_gpl_license_key():
    """TinyMCE 8 opens read only without license_key; the shared setup must always set 'gpl'."""
    src = SETUP_JS.read_text(encoding="utf-8")
    assert "var LICENSE_KEY = 'gpl';" in src
    assert "c.license_key = LICENSE_KEY;" in src


def test_license_key_in_config_and_editor_is_editable(site, page):
    page.goto(site.base + "/__blank")
    page.add_script_tag(url=f"{TINYMCE_BASE}/tinymce.min.js")
    page.add_script_tag(url="/common/editors/antwebEditor.js")
    mode = page.evaluate(f"""async () => {{
        const c = AntwebEditor.config('tinymce', {{ selector: '#t', baseUrl: '{TINYMCE_BASE}', validElements: 'p,b' }});
        if (c.license_key !== 'gpl') return 'license_key is ' + c.license_key;
        const eds = await tinymce.init(c);
        await new Promise(r => setTimeout(r, 1500));
        return eds[0].mode.get();
    }}""")
    assert mode == "design", mode
    assert not page.errors, page.errors


def test_without_license_key_tinymce_is_read_only(site, page):
    """Proves the check above means something: the same setup without the key is read only."""
    page.goto(site.base + "/__blank")
    page.add_script_tag(url=f"{TINYMCE_BASE}/tinymce.min.js")
    page.add_script_tag(url="/common/editors/antwebEditor.js")
    mode = page.evaluate(f"""async () => {{
        const c = AntwebEditor.config('tinymce', {{ selector: '#t', baseUrl: '{TINYMCE_BASE}', validElements: 'p,b' }});
        delete c.license_key;
        const eds = await tinymce.init(c);
        await new Promise(r => setTimeout(r, 1500));
        return eds[0].mode.get();
    }}""")
    assert mode == "readonly", mode


def test_read_only_editor_is_never_marked_ready(site, page):
    url = site.add_form("nolicense", "<p>stored</p>", "comments")
    site.pages["/nolicense"] = site.pages["/nolicense"].replace(
        "<script src=\"/common/descEdit/editSafety.js",
        "<script>(function(){var i=tinymce.init.bind(tinymce);tinymce.init=function(c){delete c.license_key;return i(c);};})();</script>"
        "<script src=\"/common/descEdit/editSafety.js")
    page.goto(url)
    page.wait_for_function("document.getElementById('descEditSafetyBar').style.display === 'block'", timeout=READY_MS)
    assert "read only" in page.inner_text("#descEditSafetyBar")
    assert page.input_value("input[name=editorReady]") == "0"


# ---------- round trip ----------

@pytest.mark.parametrize("i", range(len(SAMPLES)), ids=[f"{f}-{n}" for n, (f, _) in enumerate(SAMPLES)])
def test_round_trip_unchanged(site, page, i):
    field, stored = SAMPLES[i]
    videos = field == "videos"
    open_form(page, site.add_form(f"rt{i}", stored, field))
    assert not page.errors, page.errors
    editor_html = content(page)
    san_stored, san_editor = site.rules.sanitize([(stored, videos), (editor_html, videos)])
    # The editor shows the same page as the stored text, after the server's sanitizer.
    ce, cs = canon(page, san_editor), canon(page, san_stored)
    assert ce == cs, f"\nstored: {cs!r}\neditor: {ce!r}"
    # It adds no paragraph around text that had none.
    assert san_editor.count("<p") == san_stored.count("<p"), san_editor
    # Save without changes: the form sends exactly the stored text (the browser sends a text box's
    # line breaks as CRLF; DescEditSafety.sameText treats that as unchanged, so nothing is written).
    sent = save(page, site)
    assert sent["contents"].replace("\r\n", "\n") == stored
    assert sent["editorReady"] == "1" and sent["_csrf"] == "fresh" and sent["clearField"] == "0"


@pytest.mark.parametrize("i", range(len(SAMPLES)), ids=[f"{f}-{n}" for n, (f, _) in enumerate(SAMPLES)])
def test_edit_adds_no_paragraphs(site, page, i):
    field, stored = SAMPLES[i]
    videos = field == "videos"
    open_form(page, site.add_form(f"ed{i}", stored, field))
    page.evaluate("""() => { const ed = tinymce.get('editor1');
        ed.focus(); ed.selection.select(ed.getBody(), true); ed.selection.collapse(true);
        ed.insertContent('EDITED '); }""")
    sent = save(page, site)
    assert "EDITED" in sent["contents"]
    san_stored, san_sent = site.rules.sanitize([(stored, videos), (sent["contents"], videos)])
    assert san_sent.count("<p") == san_stored.count("<p"), f"\nstored: {san_stored}\nsent:   {san_sent}"
    assert "data-aw-loose" not in sent["contents"]
    expected = canon(page, san_stored)
    got = re.sub("EDITED[ \u00a0]", "", canon(page, san_sent), count=1)
    assert got == expected, f"\nstored: {expected}\nsent:   {got}"


def test_enter_in_loose_text_gives_line_breaks(site, page):
    open_form(page, site.add_form("enter", "alpha beta<br>gamma", "notes"))
    page.evaluate("""() => { const ed = tinymce.get('editor1'); ed.focus();
        const t = ed.getBody().querySelector('p').firstChild;
        const r = ed.dom.createRng(); r.setStart(t, 5); r.setEnd(t, 5); ed.selection.setRng(r); }""")
    page.keyboard.press("Enter")
    sent = save(page, site)
    assert "<p" not in sent["contents"], sent["contents"]
    assert re.sub("[ \u00a0]", "", sent["contents"]) == "alpha<br><br>beta<br>gamma", sent["contents"]


def test_new_text_in_empty_field_and_paste_cleanup(site, page):
    open_form(page, site.add_form("empty", "", "comments"))
    page.click("iframe.tox-edit-area__iframe") if page.locator("iframe.tox-edit-area__iframe").count() else None
    page.evaluate("""() => { const ed = tinymce.get('editor1'); ed.focus();
        const dt = new DataTransfer();
        dt.setData('text/html', '<p class=MsoNormal><span style="mso-bidi-font-size:11.0pt;font-weight:bold">Word</span> text<o:p></o:p></p>');
        dt.setData('text/plain', 'Word text');
        ed.getBody().dispatchEvent(new ClipboardEvent('paste', {clipboardData: dt, bubbles: true, cancelable: true})); }""")
    sent = save(page, site)
    assert "Word" in sent["contents"] and "mso" not in sent["contents"].lower(), sent["contents"]
    assert "<b>Word</b>" in sent["contents"], sent["contents"]


# ---------- safety behaviour ----------

def test_empty_save_is_blocked(site, page):
    open_form(page, site.add_form("emptysave", "<p>Stored text</p>", "comments"))
    page.evaluate("tinymce.get('editor1').setContent('')")
    before = len(site.posts)
    page.click("input[type=submit]")
    page.wait_for_function("document.getElementById('descEditSafetyBar').style.display === 'block'")
    assert "box is empty" in page.inner_text("#descEditSafetyBar")
    page.wait_for_timeout(500)
    assert len(site.posts) == before


def test_clear_button_confirms_then_clears(site, page):
    open_form(page, site.add_form("clear", "<p>Stored text</p>", "comments"))
    page.once("dialog", lambda d: d.accept())
    page.click("button.descEditClear")
    page.wait_for_selector("#saved", timeout=15000)
    assert site.posts[-1]["clearField"] == "1"


def test_draft_is_offered_after_leaving_the_page(site, page):
    """Type, leave the page (accept the browser's "Leave site?" dialog), come back: the draft is offered."""
    url = site.add_form("draft", "<p>Stored text</p>", "comments")
    open_form(page, url)
    page.evaluate("() => { const ed = tinymce.get('editor1'); ed.focus(); ed.selection.select(ed.getBody(), true);"
                  " ed.selection.collapse(false); ed.insertContent(' typed DRAFT words'); }")
    page.wait_for_timeout(3500)   # the draft is written every 3 seconds and shortly after a change
    page.on("dialog", lambda d: d.accept())   # "Leave site?" (beforeunload): Leave
    page.goto(site.base + "/__blank")
    open_form(page, url)
    page.wait_for_function("document.getElementById('descEditSafetyBar').style.display === 'block'")
    assert "unsaved text" in page.inner_text("#descEditSafetyBar")
    page.click("#descEditSafetyBar button:has-text('Restore my unsaved text')")
    assert "DRAFT" in content(page)
    sent = save(page, site)
    assert "DRAFT" in sent["contents"] and "<p" in sent["contents"]


def test_phone_width_form_fits(site, browser):
    ctx = browser.new_context(viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True)
    pg = ctx.new_page()
    errors = []
    pg.on("pageerror", lambda e: errors.append(str(e)))
    try:
        open_form(pg, site.add_form("phone", SAMPLES[1][1], "identification"))
        box = pg.locator(".tox-tinymce").bounding_box()
        assert box and box["x"] >= 0 and box["x"] + box["width"] <= 390 + 1, box
        assert pg.evaluate("document.documentElement.scrollWidth") <= 390 + 1
        assert pg.locator(".tox-toolbar__primary, .tox-toolbar-overlord").count() > 0
        assert not errors, errors
    finally:
        ctx.close()


def test_ckeditor_fallback_path_still_works(site, page):
    """The rollback switch (util/PageEditor.java, pageEditor=ckeditor): the form must stay usable.
    The repository's web/ckeditor/ holds only CKEditor 3's loader (its core files are not in git),
    so here the editor does not start and editSafety.js falls back to the plain HTML box: ready
    flag set, unchanged save sends the stored text, empty save blocked, no JavaScript error."""
    stored = "<p>Stored <b>text</b></p>"
    url = site.add_form("ck", stored, "comments")
    p = site.pages["/ck"]
    p = p.replace(f'<script src="{TINYMCE_BASE}/tinymce.min.js?v=test"></script>\n'
                  '<script src="/common/editors/antwebEditor.js?v=test"></script>',
                  '<script src="/ckeditor/ckeditor.js?v=test"></script>')
    p = re.sub(r"editor: 'tinymce',\s*tinymce: \{[^}]*\}", "editor: 'ckeditor'", p)
    p = p.replace("</script>\n<input type=\"submit\"", "if (window.CKEDITOR && typeof CKEDITOR.on === 'function' && typeof CKEDITOR.replace === 'function')"
                  " CKEDITOR.replace('editor1', {enterMode: CKEDITOR.ENTER_P});\n</script>\n<input type=\"submit\"")
    assert "CKEDITOR.replace" in p
    site.pages["/ck"] = p
    open_form(page, url)
    assert page.evaluate("!window.tinymce")
    started = page.evaluate("!!(window.CKEDITOR && CKEDITOR.instances && CKEDITOR.instances.editor1)")
    sent = save(page, site)
    assert sent["contents"].replace("\r\n", "\n") == stored and sent["editorReady"] == "1"
    open_form(page, url)
    if started:
        page.evaluate("CKEDITOR.instances.editor1.setData('')")
    else:
        page.fill("#editor1", "")
    page.wait_for_timeout(300)
    before = len(site.posts)
    page.click("input[type=submit]")
    page.wait_for_function("document.getElementById('descEditSafetyBar').style.display === 'block'")
    assert "box is empty" in page.inner_text("#descEditSafetyBar") and len(site.posts) == before
    assert not page.errors, page.errors
