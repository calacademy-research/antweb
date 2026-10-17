# HugeRTE 1.0.14

Rich text editor (MIT licence, see LICENSE.txt), a fork of TinyMCE 6. Used by the editor
trial page `/editorTrial.do` (EditorTrialAction, `web/curate/editorTrial.jsp`).

- Source: npm package `hugerte@1.0.14` (`npm pack hugerte@1.0.14`), released 2026-09-05.
  Home: https://github.com/hugerte/hugerte
- Only the minified files the trial page needs are kept: `hugerte.min.js`, the silver theme,
  the dom model, the default icons, the oxide skin (`skin.min.css`, `content.min.css`), the
  default content CSS, and the plugins advlist, autolink, charmap, code, fullscreen, image,
  link, lists, table, visualblocks and wordcount. No language packs (English is built in).
- To add a plugin, copy `plugins/<name>/plugin.min.js` from the same npm package version.
- Loaded only from AntWeb itself (no CDN). No build step.
