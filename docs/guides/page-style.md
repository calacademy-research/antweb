# Tool page style guide

The administrator and curator "report" and "form" pages share one look: left-aligned, one column of
white-grey cards, bold links with a plain explanation after them, blue buttons, clean tables. It started
on the Curate page (22 October 2026); Brian Fisher: "I think it is a good style to use for all the
'report' and 'form' pages".

The look lives in two files:

- `web/common/tools.css`: every rule starts with `.aw-tools`, so it changes nothing outside a page that
  opts in. Public pages (home, taxon, specimen, place pages) never load it.
- `web/common/tools.js`: two small table helpers (below). The page works the same without it.

Both are loaded by one include, `web/common/toolsHead.jsp`, which adds the version token to their
addresses (`Asset.url`, so browsers and Cloudflare fetch a changed file at once).

Pages in this look (batch 1, 2026-10-22): Curate page, Technical page, Upload status and each job's
page (`jobStatus.do`), Server Status, Check the counts, Log Manager, Name update report, Specimen and
Image upload lists, named query results and the other admin result pages (`curate/adminHtmlMessage.jsp`),
admin messages (`common/message-body.jsp`, `unboldMessage-body.jsp` inside the admin layout). The upload reports (static files written at upload time, `upload/UploadReportPage`, 2026-10-29) link
`tools.css` and use its classes, with their own rules inline (scoped to `.aw-report`). The Query
Manager keeps its own scoped styles (`.qm`, in `Queries.java`).

## How a page opts in

In the page's body JSP (the one named in `<tiles:put name="body-content" ...>`):

```jsp
<%@include file="/common/toolsHead.jsp" %>
<div class="aw-tools" id="my-page">
  <p class="aw-crumbs"><a href="<%= domainApp %>">Home</a> | <a href="<%= domainApp %>/curate.do">Curator Tools</a></p>
  <h1>Page title</h1>
  <div class="aw-intro"><p>What the page shows, why it matters, what to do with it.</p></div>

  <div class="aw-card" id="my-page-first-section">
    <h2>Section</h2>
    ...
  </div>
</div>
```

- The wrapper must be `<div class="aw-tools">` with an id of its own (tests look for it).
- No `admin_left`, `left`, `wide_left`, `align="center"`, `text-align:center`, fixed widths or
  `cellpadding`: the admin layout centres its content (`<div align="center">` in
  `layouts/admLayout.jsp`); `.aw-tools` sets it back to the left.
- A body JSP shared with the public layout (the message pages) takes the look only when
  `request.getAttribute("awAdminLayout")` is true (set by `admLayout.jsp`), and keeps its old markup
  otherwise. Never include `toolsHead.jsp` from a public layout or public page.
- A page with no layout that public visitors can reach (`curate/adminHtmlMessage.jsp`) uses the admin
  layout and the look for logged-in viewers only.

## Every page explains itself

Under the title, before anything else, a short `aw-intro` (or the admin reports' `report-help` box,
`ReportHelp.box`) answers three questions in plain words:

1. **What it shows** (what the rows, numbers or links are).
2. **Why** it matters (what goes wrong on the site if the problem is not fixed).
3. **What to do** (usually: fix the source, or nothing, the nightly run corrects it).

One or two sentences each. A card can add its own `aw-lead` line for its section.

## Patterns

| Pattern | Markup | Notes |
|---|---|---|
| Links above the title | `<p class="aw-crumbs">Home \| Curator Tools</p>` | small grey line |
| Title | `<h1>` (or `<h2 class="aw-title">` where tests read the first `h2`) | one per page |
| Explanation | `<div class="aw-intro"><p>...</p></div>` | at most about 60 words wide |
| Section | `<div class="aw-card" id="..."><h2>Section</h2>...</div>` | full width; a wide table scrolls inside it |
| Admin-only section | `class="aw-card aw-admin"`, `<span class="aw-badge">Admin</span>` in its `h2` | |
| Section intro | `<p class="aw-lead">` right after the card's `h2` | |
| Small heading | `<h3>` | uppercase grey label; an `h3` with its own inline style keeps it |
| Link list | `<ul class="aw-links"><li><a>Name</a> (plain explanation)</li></ul>` | link bold, explanation normal |
| Short links side by side | `<ul class="aw-inline"><li><a>file.log</a></li></ul>` | pills |
| Form row | `<div class="aw-row"><label>...</label> <select>...</div>` | stacks on a phone |
| Buttons | `<div class="aw-actions"><input class="aw-btn" type="submit" value="Save"> <input class="aw-btn aw-btn-secondary" ...></div>` | every plain submit in `.aw-tools` is blue too |
| Notes, warnings | `<p class="aw-note">`, `<div class="aw-warn">`, `<span class="aw-alert">` | |
| Message from the server | `<div class="aw-msg">` | red box, hidden when empty |
| Status word | `<span class="aw-status aw-status-running">Running</span>` (queued, running, finished, failed, interrupted) | coloured pill |
| True / false | `<span class="aw-ok">false</span>`, `<span class="aw-bad">true</span>` | green / red pill |
| Label / value table | `<table class="aw-kv"><tr><th scope="row">Label</th><td>value</td></tr></table>` | no header row, no stripes |
| Log text | `<pre>` | scrolls sideways inside its card |

Forms keep their CSRF field (`Csrf.field(request)`), field names and actions unchanged; a style change
never changes what a form sends.

## Tables

Any `<table>` inside `.aw-tools` gets the table look: a grey header row (`th`), a line under each row,
light zebra stripes, a hover colour. Tables built in Java (named queries, the count check) need no
change; old `border=1`, `cellpadding` and `width` attributes are overridden.

- **Numbers right-aligned.** `tools.js` marks every column whose cells are all numbers (`1,234`,
  `12.5`, `3.`, `45%`) with `aw-num`. A page can set `class="aw-num"` on cells itself.
- **Phones.** `tools.js` puts each table in a `<div class="aw-table-wrap">`, which scrolls sideways;
  without the script the card scrolls. The page itself never scrolls sideways.
- Keep plain `<tr>` and `<td>` where tests split rows on them (upload lists).

## Tests

- `tests/e2e/test_tool_pages_style.py`: each converted page includes `toolsHead.jsp` and has its
  `div.aw-tools`; no old layout markup or centring; only the known pages load the stylesheet; the shared
  message bodies change only inside the admin layout; on staging each page loads with its key links and
  forms; a finished data job's page has no second site page inside it.
- `tests/e2e/test_curate_page_layout.py::test_source_curate_stylesheet_is_scoped`: every rule of
  `tools.css` starts with `.aw-tools`, nothing centred, a phone-width block.

When you convert a page: add it to `CONVERTED` and `PAGES` in `test_tool_pages_style.py`, and to the list
at the top of this guide.
