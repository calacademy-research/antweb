/*
 * antwebEditor.js: the one TinyMCE 8 setup for AntWeb page text. Used by the curator edit forms
 * (web/common/descEdit/ckEditorMinConfigForm.jsp, with editSafety.js) and by the editor trial
 * page (/editorTrial.do, editorTrial.js), so both always run the same settings.
 *
 * TinyMCE 8 is self-hosted under its GPL 2+ licence (web/common/editors/tinymce/<version>/,
 * LICENSE.md and notices.txt there). TinyMCE 8 opens READ ONLY unless license_key is set, so
 * config() always sets license_key: 'gpl' for TinyMCE (tests/e2e/test_editor_roundtrip.py checks it).
 * HugeRTE (MIT fork, the exit plan) takes the same settings without license_key and promotion.
 *
 * Text outside paragraphs ("loose" text): TinyMCE puts any text that is not inside a block
 * (p, table, list, heading...) into a <p>. Much stored AntWeb text has no <p> (for example
 * "Line one<br>Line two"), and that <p> would add paragraph spacing on the page. So, before
 * TinyMCE reads a text, every run of loose text is put into <p data-aw-loose="1">, and when the
 * editor's HTML is read (getContent: save, drafts, source view) those paragraphs are unwrapped
 * again. If the curator splits such a run with Enter, the parts are joined with <br><br>
 * (two loose runs are never next to each other in stored text). A loose paragraph the curator
 * gave a style or alignment stays a <p>. Unchanged text is sent exactly as stored (editSafety.js).
 *
 * Plain ES5 apart from what TinyMCE itself needs; no libraries.
 */
(function (window, document) {
  'use strict';

  var LOOSE = 'data-aw-loose';
  var LICENSE_KEY = 'gpl';

  // ---- Paste clean-up (Word, Google Docs) --------------------------------------------------

  function unwrapDom(node) {
    var parent = node.parentNode;
    if (!parent) return;
    while (node.firstChild) parent.insertBefore(node.firstChild, node);
    parent.removeChild(node);
  }

  // Inline styles Word and Google Docs use for bold, italic, underline, super- and subscript,
  // turned into tags; their "normal" values (and Word's mso- styles) are dropped.
  var STYLE_TAGS = [
    [/^font-weight:\s*(bold|bolder|[6-9]00)$/i, 'b'],
    [/^font-style:\s*italic$/i, 'i'],
    [/^text-decoration(-line)?:\s*underline$/i, 'u'],
    [/^vertical-align:\s*super$/i, 'sup'],
    [/^vertical-align:\s*sub$/i, 'sub']
  ];
  var NOISE = /^(mso-|font-weight:\s*(normal|400)$|font-style:\s*normal$|vertical-align:\s*baseline$|text-decoration(-line)?:\s*none$)/i;

  function cleanPasted(node) {
    // Comments (<!--StartFragment--> and Word's conditional comments).
    var walker = document.createTreeWalker(node, NodeFilter.SHOW_COMMENT), comments = [];
    while (walker.nextNode()) comments.push(walker.currentNode);
    comments.forEach(function (c) { c.parentNode.removeChild(c); });

    var all = node.querySelectorAll('*');
    for (var i = 0; i < all.length; i++) {
      var e = all[i];
      var cls = e.getAttribute('class');
      if (cls != null) {
        var keep = cls.split(/\s+/).filter(function (c) { return c && !/^mso/i.test(c); });
        if (keep.length) e.setAttribute('class', keep.join(' ')); else e.removeAttribute('class');
      }
      e.removeAttribute('lang');
      e.removeAttribute('dir');
      e.removeAttribute(LOOSE);
      var st = e.getAttribute('style');
      if (st == null) continue;
      var decl = st.split(';').map(function (d) { return d.trim(); }).filter(function (d) { return d; });
      // Google Docs wraps the whole paste in <b style="font-weight:normal">: unwrap it below.
      var normalB = e.nodeName === 'B' && decl.some(function (d) { return /^font-weight:\s*(normal|400)$/i.test(d); });
      var wrap = [];
      if (e.nodeName === 'SPAN') {
        decl = decl.filter(function (d) {
          for (var t = 0; t < STYLE_TAGS.length; t++) {
            if (STYLE_TAGS[t][0].test(d)) { wrap.push(STYLE_TAGS[t][1]); return false; }
          }
          return true;
        });
      }
      decl = decl.filter(function (d) { return !NOISE.test(d); });
      if (decl.length) e.setAttribute('style', decl.join('; ')); else e.removeAttribute('style');
      if (normalB) e.setAttribute('data-et-unwrap', '1');
      if (wrap.length) {
        var inner = e;
        wrap.forEach(function (tag) {
          var w = document.createElement(tag);
          while (inner.firstChild) w.appendChild(inner.firstChild);
          inner.appendChild(w);
          inner = w;
        });
      }
    }
    var marked = node.querySelectorAll('[data-et-unwrap]');
    for (var j = 0; j < marked.length; j++) unwrapDom(marked[j]);
    // Spans left with no attributes do nothing.
    var spans = node.querySelectorAll('span');
    for (var k = 0; k < spans.length; k++) {
      if (!spans[k].attributes.length) unwrapDom(spans[k]);
    }
    // Empty paragraphs (left by the removed comments).
    var ps = node.querySelectorAll('p');
    for (var m = 0; m < ps.length; m++) {
      if (!ps[m].attributes.length && !ps[m].textContent.trim() && !ps[m].querySelector('img,br,iframe')) {
        ps[m].parentNode.removeChild(ps[m]);
      }
    }
  }

  // ---- Loose text (see the note at the top) ------------------------------------------------

  /** The valid_elements rule with data-aw-loose allowed on p (editor only; the server never keeps it). */
  function withLooseMarker(validElements) {
    if (!validElements) return validElements;
    var found = false;
    var parts = validElements.split(',').map(function (part) {
      var m = /^p(?:\[(.*)\])?$/.exec(part);
      if (!m) return part;
      found = true;
      return 'p[' + (m[1] ? m[1] + '|' : '') + LOOSE + ']';
    });
    if (!found) parts.push('p[' + LOOSE + ']');
    return parts.join(',');
  }

  function isBlockName(name, blocks) {
    return !!(blocks[name] || blocks[name.toUpperCase()]);
  }

  /**
   * Put each run of top-level loose content (text and inline tags between blocks) into
   * <p data-aw-loose="1">. blocks: TinyMCE's schema.getBlockElements(). Returns the html unchanged
   * when there is no loose content.
   */
  function markLoose(html, blocks) {
    if (typeof html !== 'string' || html === '') return html;
    var tpl = document.createElement('template');
    tpl.innerHTML = html;
    var root = tpl.content;
    var nodes = Array.prototype.slice.call(root.childNodes);
    var run = [];
    var changed = false;

    function meaningful(n) {
      if (n.nodeType === 1) return true;
      return n.nodeType === 3 && /[^ \t\r\n\f]/.test(n.nodeValue);
    }
    function flush() {
      // Leading and trailing white space stays outside the paragraph, as it was.
      var a = 0, b = run.length - 1;
      while (a <= b && !meaningful(run[a])) a++;
      while (b >= a && !meaningful(run[b])) b--;
      if (a <= b) {
        var p = document.createElement('p');
        p.setAttribute(LOOSE, '1');
        root.insertBefore(p, run[a]);
        for (var i = a; i <= b; i++) p.appendChild(run[i]);
        changed = true;
      }
      run = [];
    }
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      var inline = n.nodeType === 3 || n.nodeType === 8
        || (n.nodeType === 1 && !isBlockName(n.nodeName.toLowerCase(), blocks));
      if (inline) run.push(n); else flush();
    }
    flush();
    return changed ? tpl.innerHTML : html;
  }

  // TinyMCE AstNode helpers for the serializer filter.
  function isWhiteText(n) {
    return n.type === 3 && !/[^ \t\r\n\f]/.test(n.value || '');
  }
  function isEmptyLoose(n) {
    var nonEmpty = { img: 1, iframe: 1, hr: 1, table: 1, video: 1, audio: 1, object: 1, embed: 1 };
    var hasContent = false;
    (function walk(x) {
      for (var c = x.firstChild; c && !hasContent; c = c.next) {
        if (c.type === 3) {
          if (/[^ \t\r\n\f ]/.test(c.value || '')) hasContent = true;
        } else if (c.type === 1) {
          if (nonEmpty[c.name] || c.attr('data-mce-bogus') == null && c.name === 'br' && c.next) hasContent = true;
          else walk(c);
        }
      }
    })(n);
    return !hasContent;
  }

  function unwrapLooseNodes(lib, nodes) {
    var unwrap = [], drop = [];
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      n.attr(LOOSE, null);
      var top = n.parent && (n.parent.type === 11 || n.parent.name === 'body');
      if (n.name !== 'p' || !top || (n.attributes && n.attributes.length)) continue;
      if (isEmptyLoose(n)) drop.push(n); else unwrap.push(n);
    }
    for (var d = 0; d < drop.length; d++) drop[d].remove();
    for (var j = 0; j < unwrap.length; j++) {
      var p = unwrap[j];
      var prev = p.prev;
      while (prev && isWhiteText(prev)) prev = prev.prev;
      if (prev && unwrap.indexOf(prev) >= 0) {
        // The curator split a loose run with Enter: keep a visible break.
        p.parent.insert(new lib.html.Node('br', 1), p, true);
        p.parent.insert(new lib.html.Node('br', 1), p, true);
      }
    }
    for (var k = unwrap.length - 1; k >= 0; k--) unwrap[k].unwrap();
  }

  // ---- Settings ------------------------------------------------------------------------------

  /**
   * The editor settings. lib: 'tinymce' or 'hugerte'. opts:
   *   target / selector   the textarea
   *   baseUrl             folder of the editor files, e.g. /common/editors/tinymce/8.10.0
   *   cacheSuffix         e.g. "?v=1a2b3c4d" (util/Asset token of tinymce.min.js) for the files TinyMCE loads itself
   *   validElements       HtmlSanitizer.editorValidElements(): keep exactly what the server keeps
   *   cleanPaste          true, or a function returning true when pasted Word/Google markup is cleaned
   *   keepLooseText       default true: loose text stays loose (see the note at the top)
   *   height              editor height in pixels
   *   onInit(editor)      the editor has loaded its text
   *   onChange(editor)    the text may have changed (typing, paste, undo, formatting)
   */
  function config(lib, opts) {
    opts = opts || {};
    var keepLoose = opts.keepLooseText !== false;
    var libObj = window[lib];
    var c = {
      base_url: opts.baseUrl,
      suffix: '.min',
      plugins: 'advlist autolink charmap code fullscreen image link lists table visualblocks wordcount',
      menubar: 'edit view insert format table',
      toolbar: 'undo redo | blocks | bold italic underline strikethrough | subscript superscript | '
        + 'bullist numlist outdent indent | link image table charmap | removeformat | code visualblocks fullscreen',
      toolbar_mode: 'sliding',
      block_formats: 'Paragraph=p; Heading 3=h3; Heading 4=h4; Heading 5=h5; Heading 6=h6; Preformatted=pre',
      // Keep exactly what the server's sanitizer keeps (HtmlSanitizer.editorValidElements).
      valid_elements: keepLoose ? withLooseMarker(opts.validElements) : opts.validElements,
      entity_encoding: 'raw',
      // Keep <font>, <strike> and align attributes as stored (the default turns them into styles).
      inline_styles: false,
      convert_urls: false,
      relative_urls: false,
      remove_script_host: false,
      paste_data_images: false,
      // Chrome and Safari drop all pasted styles by default; keep the ones that carry meaning.
      paste_webkit_styles: 'font-weight font-style text-decoration text-decoration-line vertical-align color',
      table_use_colgroups: false,
      table_default_attributes: { border: '1' },
      table_default_styles: { 'border-collapse': 'collapse' },
      image_dimensions: true,
      link_default_protocol: 'https',
      contextmenu: 'link image table',
      browser_spellcheck: true,
      branding: false,
      // Video embeds (videos field) show in the editor; any other iframe is sandboxed there.
      sandbox_iframes_exclusions: ['youtube.com', 'youtu.be', 'youtube-nocookie.com', 'vimeo.com', 'player.vimeo.com'],
      height: opts.height || 460,
      min_height: 260,
      width: '100%',
      content_style: 'body { font-family: Arial, Helvetica, sans-serif; font-size: 14px; line-height: 1.45; }'
        + ' img { max-width: 100%; height: auto; }',
      paste_postprocess: function (editor, args) {
        var clean = typeof opts.cleanPaste === 'function' ? opts.cleanPaste() : opts.cleanPaste !== false;
        if (clean) cleanPasted(args.node);
        else {
          var marked = args.node.querySelectorAll('[' + LOOSE + ']');
          for (var i = 0; i < marked.length; i++) marked[i].removeAttribute(LOOSE);
        }
      },
      setup: function (ed) {
        if (keepLoose) {
          ed.on('PreInit', function () {
            ed.serializer.addAttributeFilter(LOOSE, function (nodes) { unwrapLooseNodes(libObj, nodes); });
          });
          ed.on('BeforeSetContent', function (e) {
            if (e.selection || e.format === 'raw' || e.format === 'tree' || typeof e.content !== 'string') return;
            e.content = markLoose(e.content, ed.schema.getBlockElements());
          });
        }
        if (opts.onChange) {
          ed.on('input change undo redo SetContent ExecCommand paste', function () { opts.onChange(ed); });
        }
        ed.on('init', function () {
          if (opts.onInit) opts.onInit(ed);
        });
      }
    };
    if (opts.target) c.target = opts.target; else c.selector = opts.selector;
    if (opts.cacheSuffix) c.cache_suffix = opts.cacheSuffix;
    if (lib === 'tinymce') {
      // Required by TinyMCE 8 for the self-hosted GPL build; without it the editor is read only.
      c.license_key = LICENSE_KEY;
      c.promotion = false;
    }
    return c;
  }

  /** Start TinyMCE on a textarea. Returns TinyMCE's promise, or null if TinyMCE did not load. */
  function replace(textarea, opts) {
    if (!window.tinymce || typeof window.tinymce.init !== 'function' || !textarea) return null;
    var o = {};
    for (var k in opts) if (Object.prototype.hasOwnProperty.call(opts, k)) o[k] = opts[k];
    o.target = textarea;
    return window.tinymce.init(config('tinymce', o));
  }

  /** True if TinyMCE opened the editor read only (for example without a licence key). */
  function isReadOnly(ed) {
    try {
      if (ed.mode && typeof ed.mode.isReadOnly === 'function') return ed.mode.isReadOnly();
      return !!(ed.mode && ed.mode.get && ed.mode.get() === 'readonly');
    } catch (e) {
      return false;
    }
  }

  window.AntwebEditor = {
    LICENSE_KEY: LICENSE_KEY,
    config: config,
    replace: replace,
    isReadOnly: isReadOnly,
    cleanPasted: cleanPasted,
    markLoose: markLoose,
    withLooseMarker: withLooseMarker
  };
})(window, document);
