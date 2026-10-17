/*
 * fieldHistory.js: the "Restore this version" buttons on the page-text History page
 * (fieldHistory-body.jsp). Asks for confirmation, then checks the login is still valid and puts
 * the session's current form token in the form (as editSafety.js does before Save), so a page
 * left open a long time or a login in another tab does not end in "Request refused".
 * The server applies all the rules; this only avoids a wasted click.
 */
(function () {
    'use strict';
    var cfg = window.AntwebFieldHistory || {};

    function setToken(form, token) {
        var input = form.querySelector('input[name=_csrf]');
        if (input && token) input.value = token;
    }

    function busy(form, on) {
        var b = form.querySelector('button');
        if (!b) return;
        if (!b.getAttribute('data-label')) b.setAttribute('data-label', b.textContent);
        b.disabled = on;
        b.textContent = on ? 'Restoring…' : b.getAttribute('data-label');
    }

    function onSubmit(e) {
        var form = e.target;
        if (!form || !form.classList || !form.classList.contains('fh-restore')) return;
        e.preventDefault();
        var msg = 'Replace the current text of ' + (form.getAttribute('data-field') || 'this field')
            + ' with the version of ' + (form.getAttribute('data-date') || '') + '?\n\n'
            + 'The current text is kept in the history, so this can be undone.';
        if (!window.confirm(msg)) return;
        busy(form, true);
        if (!cfg.sessionUrl || !window.fetch) { form.submit(); return; }
        window.fetch(cfg.sessionUrl, { credentials: 'same-origin', cache: 'no-store' })
            .then(function (r) { return r.json(); })
            .then(function (s) {
                if (!s || !s.loggedIn) {
                    busy(form, false);
                    window.alert('You are not logged in any more. Log in (a new tab opens), then press Restore again.');
                    if (cfg.loginUrl) window.open(cfg.loginUrl, '_blank', 'noopener');
                    return;
                }
                setToken(form, s.csrf);
                form.submit();
            })
            .catch(function () { form.submit(); });   // the server still checks everything
    }

    document.addEventListener('submit', onSubmit, true);
})();
