/*
 * Editor trial page (/editorTrial.do): HugeRTE and TinyMCE 8 side by side on the same stored text.
 * Nothing here saves page text. The only request is POST op=sanitize, which returns the
 * sanitized form of the editor's HTML (HtmlSanitizer, as on save) and changes nothing.
 */
(function () {
  'use strict';

  var root = document.getElementById('et');
  if (!root) return;
  var cfg = {
    sanitizeUrl: root.getAttribute('data-sanitize-url'),
    csrf: root.getAttribute('data-csrf'),
    field: root.getAttribute('data-field') || '',
    validElements: root.getAttribute('data-valid-elements'),
    bases: {
      hugerte: root.getAttribute('data-hugerte-base'),
      tinymce: root.getAttribute('data-tinymce-base')
    },
    cacheSuffix: {
      hugerte: root.getAttribute('data-hugerte-suffix'),
      tinymce: root.getAttribute('data-tinymce-suffix')
    }
  };
  var stored = document.getElementById('et-stored').value;
  var storedSanitized = document.getElementById('et-stored-sanitized').value;
  var editors = {};

  function $(id) { return document.getElementById(id); }
  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  function status(key, text, cls) {
    var s = $('status-' + key);
    s.textContent = text;
    s.className = 'et-status' + (cls ? ' ' + cls : '');
  }

  // ---- Editor setup (the same for both, apart from the licence key) ----------------------

  // The settings come from antwebEditor.js, the same file the curator edit forms use
  // (license_key 'gpl' and promotion are added for TinyMCE only).
  function config(key) {
    return window.AntwebEditor.config(key, {
      selector: '#ed-' + key,
      baseUrl: cfg.bases[key],
      cacheSuffix: cfg.cacheSuffix[key],
      validElements: cfg.validElements,
      cleanPaste: function () { return $('et-cleanpaste').checked; },
      onInit: function (ed) {
        editors[key] = ed;
        status(key, 'Loaded.');
        check(key, true);
      }
    });
  }

  function start(key) {
    var lib = window[key];
    if (!lib || typeof lib.init !== 'function' || !window.AntwebEditor) {
      status(key, 'The editor files did not load.', 'et-error');
      return;
    }
    try {
      var p = lib.init(config(key));
      if (p && typeof p.then === 'function') {
        p.then(function (list) {
          if (!list || !list.length) status(key, 'The editor did not start (see the browser console).', 'et-error');
        });
      }
    } catch (e) {
      status(key, 'The editor did not start: ' + e, 'et-error');
    }
  }

  // ---- Comparison -------------------------------------------------------------------------

  // Tags, runs of white space, words, and single other characters.
  function tokens(s) {
    return s.match(/<[^>]*>|[ \t\r\n]+|[^<\s]+?(?=[<\s]|$)|</g) || [];
  }

  // Token diff: common prefix and suffix, then LCS on the middle when it is small enough.
  function diff(a, b) {
    var A = tokens(a), B = tokens(b);
    var pre = 0;
    while (pre < A.length && pre < B.length && A[pre] === B[pre]) pre++;
    var suf = 0;
    while (suf < A.length - pre && suf < B.length - pre && A[A.length - 1 - suf] === B[B.length - 1 - suf]) suf++;
    var a1 = A.slice(pre, A.length - suf), b1 = B.slice(pre, B.length - suf);
    var ops = [];
    for (var i = 0; i < pre; i++) ops.push(['=', A[i]]);
    var n = a1.length, m = b1.length;
    if (n * m > 4000000) {
      a1.forEach(function (t) { ops.push(['-', t]); });
      b1.forEach(function (t) { ops.push(['+', t]); });
    } else {
      var L = [];
      for (var x = 0; x <= n; x++) L.push(new Uint32Array(m + 1));
      for (x = n - 1; x >= 0; x--) {
        for (var y = m - 1; y >= 0; y--) {
          L[x][y] = a1[x] === b1[y] ? L[x + 1][y + 1] + 1 : Math.max(L[x + 1][y], L[x][y + 1]);
        }
      }
      x = 0; y = 0;
      while (x < n && y < m) {
        if (a1[x] === b1[y]) { ops.push(['=', a1[x]]); x++; y++; }
        else if (L[x + 1][y] >= L[x][y + 1]) { ops.push(['-', a1[x]]); x++; }
        else { ops.push(['+', b1[y]]); y++; }
      }
      while (x < n) ops.push(['-', a1[x++]]);
      while (y < m) ops.push(['+', b1[y++]]);
    }
    for (i = A.length - suf; i < A.length; i++) ops.push(['=', A[i]]);
    return ops;
  }

  // Render a diff with unchanged stretches shortened to a few tokens of context.
  function renderDiff(ops) {
    var box = el('div', 'et-diff');
    var CONTEXT = 6;
    var i = 0;
    while (i < ops.length) {
      if (ops[i][0] === '=') {
        var j = i;
        while (j < ops.length && ops[j][0] === '=') j++;
        var run = ops.slice(i, j).map(function (o) { return o[1]; });
        var head = i === 0 ? 0 : CONTEXT, tail = j === ops.length ? 0 : CONTEXT;
        if (run.length > head + tail + 3) {
          if (head) box.appendChild(document.createTextNode(run.slice(0, head).join('')));
          box.appendChild(el('span', 'gap', ' ... '));
          if (tail) box.appendChild(document.createTextNode(run.slice(run.length - tail).join('')));
        } else {
          box.appendChild(document.createTextNode(run.join('')));
        }
        i = j;
      } else {
        var kind = ops[i][0], k = i, txt = '';
        while (k < ops.length && ops[k][0] === kind) txt += ops[k++][1];
        box.appendChild(el(kind === '-' ? 'del' : 'ins', null, txt));
        i = k;
      }
    }
    return box;
  }

  // Same page after normalising: attribute order, quoting, entity spelling, style spacing,
  // rgb() or hex colours, and white space between block tags. Parsed with DOMParser, which never runs scripts or
  // loads images.
  var BLOCK = /[ \t\r\n]*(<\/?(?:p|div|h[1-6]|ul|ol|li|dl|dt|dd|table|thead|tbody|tfoot|tr|td|th|caption|blockquote|pre|center|br|hr)\b[^>]*>)[ \t\r\n]*/g;
  function canon(html) {
    var doc = new DOMParser().parseFromString('<!DOCTYPE html><body>' + html, 'text/html');
    function walk(node) {
      var out = '';
      node.childNodes.forEach(function (c) {
        if (c.nodeType === 3) {
          out += c.nodeValue.replace(/[ \t\r\n]+/g, ' ').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        } else if (c.nodeType === 1) {
          var name = c.nodeName.toLowerCase();
          var attrs = Array.prototype.map.call(c.attributes, function (a) {
            var v = a.value;
            if (a.name === 'style') {
              v = v.split(';').map(function (d) { return d.trim().replace(/\s*:\s*/, ': '); })
                .filter(Boolean).join('; ')
                .replace(/rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)/gi, function (m, r, g, b) {
                  return '#' + [r, g, b].map(function (x) { return ('0' + (+x).toString(16)).slice(-2); }).join('');
                }).toLowerCase();
            } else {
              v = v.trim();
            }
            return a.name + '="' + v.replace(/&/g, '&amp;').replace(/"/g, '&quot;') + '"';
          }).sort();
          out += '<' + name + (attrs.length ? ' ' + attrs.join(' ') : '') + '>';
          if (!/^(br|hr|img)$/.test(name)) out += walk(c) + '</' + name + '>';
        }
      });
      return out;
    }
    return walk(doc.body).replace(BLOCK, '$1').trim();
  }

  function postSanitize(html) {
    var body = new URLSearchParams();
    body.set('op', 'sanitize');
    body.set('field', cfg.field);
    body.set('html', html);
    return fetch(cfg.sanitizeUrl, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', 'X-CSRF-Token': cfg.csrf },
      body: body.toString()
    }).then(function (r) {
      var ct = r.headers.get('Content-Type') || '';
      if (ct.indexOf('application/json') < 0) {
        throw new Error(r.status === 403 ? 'Refused (403). Your login may have ended: reload the page.'
          : 'Unexpected answer (' + r.status + '). Your login may have ended: reload the page.');
      }
      return r.json().then(function (j) {
        if (!r.ok || j.error) throw new Error(j.error || ('Error ' + r.status));
        return j.sanitized;
      });
    });
  }

  function check(key, auto) {
    var ed = editors[key];
    var box = $('result-' + key);
    if (!ed) { box.textContent = 'The editor has not loaded.'; return; }
    var html = ed.getContent();
    box.textContent = 'Checking...';
    postSanitize(html).then(function (san) {
      box.textContent = '';
      var head = el('p');
      var exact = san === storedSanitized;
      var cSan = canon(san), cStored = canon(storedSanitized);
      var same = exact || cSan === cStored;
      // Paragraph tags without attributes removed: the editors put loose text in <p>.
      var noP = function (c) { return c.replace(/<\/?p>/g, ''); };
      var onlyP = !same && noP(cSan) === noP(cStored);
      var label = auto ? 'On load: ' : 'Round trip: ';
      if (exact) {
        head.appendChild(el('span', 'ok', label + 'identical.'));
        head.appendChild(document.createTextNode(' Saving now would store exactly the current text.'));
      } else if (same) {
        head.appendChild(el('span', 'near', label + 'same after normalising.'));
        head.appendChild(document.createTextNode(' Only attribute order, quoting, colour notation or white space differ; the page would look the same.'));
      } else if (onlyP) {
        head.appendChild(el('span', 'near', label + 'only paragraph tags added.'));
        head.appendChild(document.createTextNode(' The editor put text that was not in a paragraph into <p> tags; the page would gain paragraph spacing.'));
      } else {
        var ops = diff(storedSanitized, san);
        var del = 0, ins = 0;
        ops.forEach(function (o) { if (o[0] === '-') del++; else if (o[0] === '+') ins++; });
        head.appendChild(el('span', 'bad', label + 'differs.'));
        head.appendChild(document.createTextNode(' ' + del + ' pieces would be removed (red) and ' + ins + ' added (green).'));
      }
      box.appendChild(head);
      if (html !== san) {
        box.appendChild(el('p', null, 'Note: a save would also change the editor\'s HTML (sanitizer: ' + html.length + ' to ' + san.length
          + ' characters), so the editor produces something the server rewrites or removes. Compare with Show HTML.'));
      }
      if (!exact) {
        var d = document.createElement('details');
        if (!same && !onlyP) d.open = true;
        d.appendChild(el('summary', null, 'Differences (stored text to editor text, both sanitized)'));
        d.appendChild(renderDiff(diff(storedSanitized, san)));
        box.appendChild(d);
      }
    }).catch(function (e) {
      box.textContent = '';
      box.appendChild(el('p', 'bad', 'Check failed: ' + e.message));
    });
  }

  function showHtml(key) {
    var ed = editors[key], pre = $('html-' + key);
    if (!ed) return;
    if (!pre.hidden && pre.getAttribute('data-shown') === '1') { pre.hidden = true; pre.setAttribute('data-shown', '0'); return; }
    pre.textContent = ed.getContent();
    pre.hidden = false;
    pre.setAttribute('data-shown', '1');
  }

  function reset(key) {
    var ed = editors[key];
    if (!ed) return;
    ed.setContent(stored);
    ed.undoManager.clear();
    ed.setDirty(false);
    $('result-' + key).textContent = '';
    $('html-' + key).hidden = true;
  }

  root.addEventListener('click', function (ev) {
    var b = ev.target.closest('button[data-act]');
    if (!b) return;
    var key = b.getAttribute('data-ed'), act = b.getAttribute('data-act');
    if (act === 'check') check(key, false);
    else if (act === 'html') showHtml(key);
    else if (act === 'reset') reset(key);
  });

  start('hugerte');
  start('tinymce');
}());
