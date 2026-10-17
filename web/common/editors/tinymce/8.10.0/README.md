# TinyMCE 8.10.0 (community edition, self-hosted)

Rich text editor, licensed under the GNU General Public License version 2 or later (or a
commercial licence from Tiny Technologies; see LICENSE.md and notices.txt for the third-party
libraries it includes). The editor is started with `license_key: 'gpl'`, which TinyMCE 8
requires for the GPL self-hosted build; no Tiny Cloud account or API key is used. Used by
the editor trial page `/editorTrial.do` (EditorTrialAction, `web/curate/editorTrial.jsp`).

- Source: npm package `tinymce@8.10.0` (`npm pack tinymce@8.10.0`), released 2026-10-14.
  Home: https://github.com/tinymce/tinymce
- Only the minified files the trial page needs are kept: `tinymce.min.js`, the silver theme,
  the dom model, the default icons, the oxide skin (`skin.min.css`, `content.min.css`), the
  default content CSS, and the plugins advlist, autolink, charmap, code, fullscreen, image,
  link, lists, table, visualblocks and wordcount. No language packs (English is built in).
- To add a plugin, copy `plugins/<name>/plugin.min.js` from the same npm package version.
- Loaded only from AntWeb itself (no CDN). No build step.
