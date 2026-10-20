# TinyMCE 8.10.0 (community edition, self-hosted)

Rich text editor, licensed under the GNU General Public License version 2 or later (or a
commercial licence from Tiny Technologies; see LICENSE.md and notices.txt for the third-party
libraries it includes). The editor is started with `license_key: 'gpl'`, which TinyMCE 8
requires for the GPL self-hosted build; no Tiny Cloud account or API key is used (without
the key TinyMCE 8 opens read only; `tests/e2e/test_editor_roundtrip.py` checks it).

Used by the curator page-text edit forms (taxon, specimen and overview pages:
`web/common/descEdit/ckEditorMinConfigForm.jsp`, `editorScripts.jsp`, `editSafety.js`). They take
their settings from one file, `web/common/editors/antwebEditor.js`. The version and folder are named once, in
`src/org/calacademy/antweb/util/PageEditor.java` (`TINYMCE_VERSION`).

## Licence notice and source

The files in this folder are TinyMCE's own, unmodified. Licence: `LICENSE.md` (GPL 2+),
third-party notices: `notices.txt`; both are kept here and served with the editor. The
matching source code is the upstream tag https://github.com/tinymce/tinymce/tree/8.10.0
(npm `tinymce@8.10.0`). Every edit form shows a short notice with links to the licence and
the source. If these files are ever patched, publish the patch with them.

## Switching back to CKEditor 3 (rollback, until phase 4 removes it)

No deploy needed. On the server (prod: `ssh root@165.232.137.86`):

    echo 'pageEditor=ckeditor' > ~/antweb/etc/editor.properties

All edit forms use CKEditor 3 again within a minute (no restart). To return to TinyMCE:
`rm ~/antweb/etc/editor.properties`. For one page only, add `&pageEditor=ckeditor` to the
edit page's address (the form also links "Use the old editor for this field"). Or revert
the pull request. See `PageEditor.java`.

Note: `web/ckeditor/` in git holds only CKEditor 3's loader (`ckeditor.js`, a copy of
`ckeditor_source.js`); the core files it loads (`_source/...`) are not in the repository. Where
they are missing, the "CKEditor" setting gives the plain HTML box instead (editSafety.js), which
saves with the same safety rules. Check on staging before relying on CKEditor as the rollback.

## Upgrading

Copy the new version's files into `web/common/editors/tinymce/<version>/`, change
`PageEditor.TINYMCE_VERSION`, push to staging and run the e2e tests (they open the editor,
check it is editable, round-trip sample text and save the test field), then delete the old folder.

- Source: npm package `tinymce@8.10.0` (`npm pack tinymce@8.10.0`), released 2026-10-14.
  Home: https://github.com/tinymce/tinymce
- Only the minified files the editor needs are kept: `tinymce.min.js`, the silver theme,
  the dom model, the default icons, the oxide skin (`skin.min.css`, `content.min.css`), the
  default content CSS, and the plugins advlist, autolink, charmap, code, fullscreen, image,
  link, lists, table, visualblocks and wordcount. No language packs (English is built in).
- To add a plugin, copy `plugins/<name>/plugin.min.js` from the same npm package version.
- Loaded only from AntWeb itself (no CDN). No build step.
