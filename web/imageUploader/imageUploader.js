/*
 * Image Uploader: sends the selected images one per request (imageUploadBatch.do), two at a time,
 * so no request comes near Cloudflare's limits (100 MB per request, an answer within 100 s).
 *
 * 1. start:  all file names are checked on the server (naming rules, file type, specimen exists);
 *            the bad ones are listed and not sent. The server creates one upload record.
 * 2. add:    each good file is sent on its own, with progress. Network errors, 5xx and Cloudflare
 *            524 are retried by themselves (4 tries, 5 s / 20 s / 60 s apart). "Busy" answers wait
 *            and try again without using up a try. A login that ended or an expired Cloudflare
 *            check pauses the queue until the curator presses Resume.
 * 3. finish: when every file is done (or the curator chooses to finish without the failed ones),
 *            the upload is marked complete and the report link is shown.
 * Without JavaScript the form posts everything to imageUploader.do as before (small uploads only).
 */
(function () {
  'use strict';

  var form = document.getElementById('imageUploadForm');
  if (!form || !window.FormData || !window.XMLHttpRequest) return;

  var BATCH_URL = form.getAttribute('data-batch-url');
  var SESSION_URL = form.getAttribute('data-session-url');
  var MAX_BYTES = parseInt(form.getAttribute('data-max-bytes'), 10) || 95000000;
  var DRY_RUN = form.getAttribute('data-dry-run') === '1';
  var csrf = form.getAttribute('data-csrf') || '';

  var PARALLEL = 2;
  var MAX_TRIES = 4;
  var BACKOFF = [5, 20, 60];          // seconds before try 2, 3, 4
  var MAX_BUSY_WAITS = 40;            // "busy" answers per file (about 20 minutes)

  var fileInput = document.getElementById('file');
  var submit = document.getElementById('iuSubmit');
  var box = document.getElementById('iuStatus');
  var banner = document.getElementById('iuBanner');
  var totalsEl = document.getElementById('iuTotals');
  var list = document.getElementById('iuList');
  var btnPause = document.getElementById('iuPause');
  var btnResume = document.getElementById('iuResume');
  var btnRetry = document.getElementById('iuRetry');
  var btnFinish = document.getElementById('iuFinish');
  var btnCopy = document.getElementById('iuCopyFailed');

  var items = [];          // {file, name, state, tries, busy, sent, el, why}
  var batchId = 0;
  var batchStarted = false;   // the start step answered
  var startTries = 0;
  var running = 0;
  var paused = false;
  var started = false;
  var finished = false;
  var timers = [];

  function show(el, on) { if (el) el.hidden = !on; }

  function mb(bytes) {
    return bytes >= 1e6 ? (bytes / 1e6).toFixed(1) + ' MB' : Math.max(1, Math.round(bytes / 1e3)) + ' KB';
  }

  function setBanner(html) {
    if (html) { banner.innerHTML = html; show(banner, true); } else { banner.innerHTML = ''; show(banner, false); }
  }

  function addRow(it) {
    var li = document.createElement('li');
    var name = document.createElement('span'); name.className = 'iu-name'; name.textContent = it.name;
    var size = document.createElement('span'); size.className = 'iu-size'; size.textContent = it.file ? mb(it.file.size) : '';
    var bar = document.createElement('progress'); bar.max = 100; bar.value = 0;
    var state = document.createElement('span'); state.className = 'iu-state';
    li.appendChild(name); li.appendChild(size); li.appendChild(bar); li.appendChild(state);
    list.appendChild(li);
    it.el = li; it.bar = bar; it.stateEl = state;
  }

  var LABELS = { queued: 'waiting', checking: 'checking name', uploading: 'sending', processing: 'saving and making sizes',
                 wait: 'waiting to try again', done: 'done', failed: 'failed', rejected: 'not sent' };

  function setState(it, state, text, pct) {
    it.state = state;
    it.el.className = 'iu-' + state;
    it.stateEl.textContent = text || LABELS[state] || state;
    if (typeof pct === 'number') it.bar.value = pct;
    if (state === 'done') it.bar.value = 100;
    updateTotals();
  }

  function count(state) {
    var n = 0;
    for (var i = 0; i < items.length; i++) if (items[i].state === state) n++;
    return n;
  }

  function updateTotals() {
    var done = count('done'), failed = count('failed'), rejected = count('rejected');
    var active = count('uploading') + count('processing'), waiting = count('queued') + count('wait');
    var sendable = items.length - rejected;
    var bytesAll = 0, bytesSent = 0;
    for (var i = 0; i < items.length; i++) {
      var it = items[i];
      if (it.state === 'rejected' || !it.file) continue;
      bytesAll += it.file.size;
      bytesSent += it.state === 'done' ? it.file.size : (it.sent || 0);
    }
    var t = done + ' of ' + sendable + ' done';
    if (active) t += ', ' + active + ' sending';
    if (waiting) t += ', ' + waiting + ' waiting';
    if (failed) t += ', ' + failed + ' failed';
    if (rejected) t += ', ' + rejected + ' not sent (see reasons)';
    if (bytesAll) t += ' · ' + mb(bytesSent) + ' of ' + mb(bytesAll);
    if (finished) t = 'Upload finished: ' + t;
    else if (paused) t = 'Paused: ' + t;
    totalsEl.textContent = t;

    var idle = !active && !waiting;
    show(btnPause, started && !finished && !paused && !idle);
    show(btnResume, started && !finished && paused);
    show(btnRetry, started && !finished && idle && failed > 0);
    show(btnFinish, started && !finished && idle && failed > 0 && done > 0);
    show(btnCopy, failed + rejected > 0);
  }

  function post(url, body, onDone) {
    var xhr = new XMLHttpRequest();
    xhr.open('POST', url, true);
    xhr.setRequestHeader('X-CSRF-Token', csrf);
    xhr.setRequestHeader('Accept', 'application/json');
    xhr.onload = function () { onDone(xhr); };
    xhr.onerror = function () { onDone(xhr); };
    xhr.ontimeout = function () { onDone(xhr); };
    xhr.send(body);
    return xhr;
  }

  function parse(xhr) {
    var type = xhr.getResponseHeader && (xhr.getResponseHeader('Content-Type') || '');
    if (type && type.indexOf('application/json') >= 0) {
      try { return JSON.parse(xhr.responseText); } catch (e) { /* not JSON */ }
    }
    return null;
  }

  // Why a request did not go through, and what to do about it.
  function classify(xhr) {
    var j = parse(xhr);
    var s = xhr.status;
    var mitigated = xhr.getResponseHeader && xhr.getResponseHeader('cf-mitigated');
    if (mitigated === 'challenge') return { kind: 'pause', why: 'challenge' };
    if (j && j.ok) return { kind: 'ok', json: j };
    if (j && j.login) return { kind: 'pause', why: 'login' };
    if (s === 403 && !j) return { kind: 'pause', why: 'token' };     // form token refused (HTML page)
    if (j && j.retry && j.retryAfter) return { kind: 'busy', after: j.retryAfter, msg: j.error };
    if (j && j.retry) return { kind: 'retry', msg: j.error };
    if (j) return { kind: 'fail', msg: j.error || ('error ' + s) };
    if (s === 413) return { kind: 'fail', msg: 'too large to send (over 100 MB)' };
    if (s === 0) return { kind: 'retry', msg: 'network error' };
    if (s === 408 || s === 429 || s >= 500) {
      return { kind: 'retry', msg: s === 524 ? 'the server took too long (524)' : 'server error ' + s };
    }
    return { kind: 'fail', msg: 'refused (' + s + ')' };
  }

  function pause(why) {
    if (paused) return;
    paused = true;
    var check = '<a href="' + window.location.pathname + '" target="_blank" rel="noopener">open this page in a new tab</a>';
    if (why === 'login') {
      setBanner('Your login has ended. Log in again in a new tab (' + check + '), then press <b>Resume</b>. Files already sent are kept.');
    } else if (why === 'challenge') {
      setBanner('The site’s security check (Cloudflare) needs to run again. Please ' + check +
        ', wait until it shows the uploader, then come back and press <b>Resume</b>.');
    } else if (why === 'token') {
      setBanner('The server refused the form token (you may have logged in again in another tab). Press <b>Resume</b> to continue.');
    } else {
      setBanner('Paused. Files being sent now will finish. Press <b>Resume</b> to continue.');
    }
    updateTotals();
  }

  function resume() {
    // Get this session's current form token first (it changes when you log in again).
    var xhr = new XMLHttpRequest();
    xhr.open('GET', SESSION_URL + (SESSION_URL.indexOf('?') >= 0 ? '&' : '?') + 't=' + Date.now(), true);
    xhr.onload = function () {
      var j = parse(xhr);
      if (j && j.loggedIn && j.csrf) {
        csrf = j.csrf;
        paused = false;
        setBanner('');
        if (!batchStarted) { startBatch(); } else { pump(); }
        updateTotals();
      } else if (xhr.getResponseHeader('cf-mitigated') === 'challenge') {
        setBanner('The security check has not run yet. Open the page in a new tab first, then press Resume again.');
      } else {
        setBanner('You are not logged in in this browser. Log in (in a new tab), then press Resume again.');
      }
    };
    xhr.onerror = function () { setBanner('Could not reach AntWeb. Check your connection, then press Resume again.'); };
    xhr.send();
  }

  function checkSession() {
    var x = new XMLHttpRequest();
    x.open('GET', SESSION_URL + (SESSION_URL.indexOf('?') >= 0 ? '&' : '?') + 't=' + Date.now(), true);
    x.onload = function () {
      var j = parse(x);
      if (j && j.loggedIn && j.csrf) csrf = j.csrf;
      else if (j && j.loggedIn === false) pause('login');
      else if (x.getResponseHeader('cf-mitigated') === 'challenge') pause('challenge');
    };
    x.send();
  }

  function pump() {
    if (paused || finished) return;
    for (var i = 0; i < items.length && running < PARALLEL; i++) {
      if (items[i].state === 'queued') send(items[i]);
    }
    if (running === 0 && count('queued') === 0 && count('wait') === 0) allSettled();
  }

  function later(it, seconds, text) {
    setState(it, 'wait', text + ' – trying again in ' + seconds + ' s');
    timers.push(setTimeout(function () {
      if (it.state === 'wait') { setState(it, 'queued'); pump(); }
    }, seconds * 1000));
  }

  function send(it) {
    running++;
    it.tries++;
    it.sent = 0;
    setState(it, 'uploading', 'sending' + (it.tries > 1 ? ' (try ' + it.tries + ')' : ''), 0);
    var fd = new FormData();
    fd.append('file', it.file, it.name);
    var url = BATCH_URL + '?step=add&batch=' + batchId + (DRY_RUN ? '&dryRun=1' : '');
    var xhr = new XMLHttpRequest();
    xhr.upload.onprogress = function (e) {
      if (e.lengthComputable && it.state === 'uploading') {
        it.sent = e.loaded;
        var pct = Math.round(e.loaded * 100 / e.total);
        if (pct >= 100) setState(it, 'processing', null, 100);
        else { it.bar.value = pct; it.stateEl.textContent = 'sending ' + pct + '%' + (it.tries > 1 ? ' (try ' + it.tries + ')' : ''); updateTotals(); }
      }
    };
    xhr.upload.onload = function () { if (it.state === 'uploading') setState(it, 'processing', null, 100); };
    xhr.open('POST', url, true);
    xhr.setRequestHeader('X-CSRF-Token', csrf);
    xhr.setRequestHeader('Accept', 'application/json');
    var settled = false;
    function done() {
      if (settled) return;
      settled = true;
      running--;
      var r = classify(xhr);
      if (r.kind === 'ok') {
        var st = r.json.status;
        var text = st === 'replaced' ? 'done (replaced an existing image; the old one is kept as a backup)'
          : st === 'alreadySaved' ? 'done (already saved on an earlier try)'
          : st === 'checked' ? 'checked (test, not saved)' : 'done';
        setState(it, 'done', text);
      } else if (r.kind === 'pause') {
        it.tries--;                       // not the file's fault
        setState(it, 'queued', 'waiting (paused)', 0);
        pause(r.why);
      } else if (r.kind === 'busy' && it.busy < MAX_BUSY_WAITS) {
        it.busy++;
        it.tries--;
        later(it, Math.min(Math.max(r.after, 5), 120), r.msg || 'server busy');
      } else if ((r.kind === 'retry' || r.kind === 'busy') && it.tries < MAX_TRIES) {
        later(it, BACKOFF[Math.min(it.tries - 1, BACKOFF.length - 1)], r.msg);
        // A refusal can arrive as a cut connection (the server stops reading the file): check the
        // login and refresh the form token before the next try.
        if (xhr.status === 0) checkSession();
      } else {
        it.why = r.msg;
        setState(it, 'failed', 'failed: ' + r.msg + (r.kind === 'fail' ? '' : ' (after ' + it.tries + ' tries)'));
      }
      pump();
    }
    xhr.onload = done;
    xhr.onerror = done;
    xhr.onabort = done;
    xhr.send(fd);
  }

  function allSettled() {
    if (!started || finished) return;
    if (count('failed') > 0) {
      setBanner(count('failed') + ' file(s) failed. Press <b>Retry failed files</b> to send them again, or <b>Finish without the failed files</b>.');
      updateTotals();
      return;
    }
    finish();
  }

  function finish() {
    if (finished) return;
    if (!DRY_RUN && !batchId) return;
    var body = new URLSearchParams();
    body.set('batch', String(batchId));
    post(BATCH_URL + '?step=finish' + (DRY_RUN ? '&dryRun=1' : ''), body, function (xhr) {
      var r = classify(xhr);
      if (r.kind === 'ok') {
        finished = true;
        window.removeEventListener('beforeunload', onLeave);
        fileInput.disabled = false;
        if (r.json.reportUrl) {
          setBanner('Upload finished. <a href="' + escapeHtml(r.json.reportUrl) + '">See the upload report</a> (' +
            escapeHtml(r.json.images) + ' images).');
        } else {
          setBanner('Upload finished (test mode, nothing saved).');
        }
        updateTotals();
      } else if (r.kind === 'pause') {
        pause(r.why);
        // Resume calls pump(), which finishes when nothing is left.
      } else {
        setBanner('The images are saved, but the upload could not be marked finished (' + (r.msg || r.why) +
          '). <button type="button" id="iuFinishAgain">Try again</button>');
        var b = document.getElementById('iuFinishAgain');
        if (b) b.onclick = finish;
      }
    });
  }

  function startBatch() {
    var names = [];
    for (var i = 0; i < items.length; i++) {
      if (items[i].state === 'checking') names.push(items[i].name);
    }
    if (!names.length) { allRejected(); return; }
    var body = new URLSearchParams();
    body.set('artist', form.elements.artist ? form.elements.artist.value : '');
    body.set('names', names.join('\n'));
    post(BATCH_URL + '?step=start' + (DRY_RUN ? '&dryRun=1' : ''), body, function (xhr) {
      var r = classify(xhr);
      if (r.kind === 'pause') { pause(r.why); return; }
      if ((r.kind === 'busy' || r.kind === 'retry') && startTries < 5) {
        startTries++;
        var wait = Math.min(Math.max(r.after || BACKOFF[Math.min(startTries - 1, BACKOFF.length - 1)], 5), 120);
        setBanner('Checking the file names: ' + escapeHtml(r.msg || 'server busy') + '. Trying again in ' + wait + ' s.');
        timers.push(setTimeout(startBatch, wait * 1000));
        return;
      }
      if (r.kind !== 'ok') {
        setBanner('Could not start the upload: ' + escapeHtml(r.msg || '') + '. Nothing was sent. ' +
          '<button type="button" id="iuStartAgain">Try again</button>');
        var b = document.getElementById('iuStartAgain');
        if (b) b.onclick = function () { setBanner(''); startBatch(); };
        return;
      }
      batchStarted = true;
      setBanner('');
      var bad = {};
      (r.json.rejected || []).forEach(function (x) { bad[x.name] = x.reason; });
      items.forEach(function (it) {
        if (it.state !== 'checking') return;
        if (bad.hasOwnProperty(it.name)) { it.why = bad[it.name]; setState(it, 'rejected', 'not sent: ' + bad[it.name]); }
        else setState(it, 'queued');
      });
      batchId = r.json.batchId || 0;
      if (!batchId && !DRY_RUN) { allRejected(); return; }
      if (count('rejected')) {
        setBanner(count('rejected') + ' file(s) will not be sent (reasons in the list). Fix their names or upload the specimen data, then upload them again.');
      }
      pump();
    });
  }

  function allRejected() {
    finished = true;
    window.removeEventListener('beforeunload', onLeave);
    fileInput.disabled = false;
    submit.disabled = false;
    setBanner('No file can be uploaded: see the reasons in the list. Nothing was sent.');
    updateTotals();
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function onLeave(e) {
    if (!started || finished) return undefined;
    e.preventDefault();
    e.returnValue = 'Images are still uploading. Leave this page?';
    return e.returnValue;
  }

  form.addEventListener('submit', function (e) {
    var files = fileInput.files;
    if (!files || !files.length) return;           // let the server say "No files uploaded."
    e.preventDefault();
    if (started && !finished) return;

    // A new upload: reset the list.
    timers.forEach(clearTimeout);
    items = []; batchId = 0; batchStarted = false; startTries = 0; running = 0; paused = false; finished = false; timers = [];
    list.innerHTML = '';
    setBanner('');
    show(box, true);

    var seen = {};
    for (var i = 0; i < files.length; i++) {
      var f = files[i];
      var it = { file: f, name: f.name, state: 'checking', tries: 0, busy: 0, sent: 0 };
      addRow(it);
      items.push(it);
      if (seen[f.name]) { it.why = 'selected twice'; setState(it, 'rejected', 'not sent: selected twice'); continue; }
      seen[f.name] = true;
      if (f.size > MAX_BYTES) {
        it.why = 'larger than ' + Math.round(MAX_BYTES / 1e6) + ' MB';
        setState(it, 'rejected', 'not sent: larger than ' + Math.round(MAX_BYTES / 1e6) + ' MB (the limit for one image)');
        continue;
      }
      if (f.size === 0) { it.why = 'empty file'; setState(it, 'rejected', 'not sent: empty file'); continue; }
      setState(it, 'checking');
    }
    started = true;
    fileInput.disabled = true;
    submit.disabled = true;
    window.addEventListener('beforeunload', onLeave);
    startBatch();
  });

  btnPause.addEventListener('click', function () { pause('user'); });
  btnResume.addEventListener('click', resume);
  btnRetry.addEventListener('click', function () {
    items.forEach(function (it) {
      if (it.state === 'failed') { it.tries = 0; it.busy = 0; setState(it, 'queued'); }
    });
    setBanner('');
    pump();
  });
  btnFinish.addEventListener('click', function () {
    if (window.confirm('Finish this upload without the ' + count('failed') + ' failed file(s)? You can upload them later as a new upload.')) {
      finish();
    }
  });
  btnCopy.addEventListener('click', function () {
    var lines = items.filter(function (it) { return it.state === 'failed' || it.state === 'rejected'; })
      .map(function (it) { return it.name + '\t' + (it.why || ''); });
    var text = lines.join('\n');
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(function () { btnCopy.textContent = 'Copied'; },
        function () { window.prompt('Failed files:', text); });
    } else {
      window.prompt('Failed files:', text);
    }
  });

  // Re-enable "Upload Files" after a finished upload, for the next set.
  fileInput.addEventListener('change', function () { if (finished) submit.disabled = false; });
})();
