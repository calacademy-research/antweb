/*
 * editSafety.js: keeps a curator's page text safe while they edit a taxon, specimen or
 * overview page field with CKEditor 3. Server side: DescEditSafety.java, DescriptionAction.java.
 *
 * - Sets the form's editorReady field to 1 only once the editor shows the stored text (if the
 *   field has text and the editor shows nothing, Save stays refused).
 * - Keeps a draft in this browser's localStorage (per login, page and field) every 3 seconds,
 *   offers to restore it when the field is opened again, and leaves it until a save succeeds
 *   (the page after a successful save removes it).
 * - While there are unsaved changes, asks editSession.do every 5 minutes (keeps the login alive,
 *   and warns if it has ended). Before Save, asks again: if logged out, keeps the text and
 *   asks the curator to log in in a new tab; if logged in, puts the session's current form token
 *   into the form, then submits.
 * - "Clear this field" asks for confirmation, then submits clearField=1.
 * - Warns near the daily restart (about 8 AM Pacific).
 * Plain ES5, no libraries. Every storage access is in try/catch (private windows, blocked storage).
 */
(function (window, document) {
    'use strict';

    var POLL_MS = 3000;
    var KEEPALIVE_MS = 5 * 60 * 1000;
    var CHECK_TIMEOUT_MS = 15000;
    var READY_TIMEOUT_MS = 30000;

    function storageGet(key) {
        if (!key) return null;
        try {
            var v = window.localStorage.getItem(key);
            return v ? JSON.parse(v) : null;
        } catch (e) {
            return null;
        }
    }

    function storageSet(key, value) {
        if (!key) return false;
        try {
            window.localStorage.setItem(key, JSON.stringify(value));
            return true;
        } catch (e) {
            return false;
        }
    }

    function storageRemove(key) {
        if (!key) return;
        try { window.localStorage.removeItem(key); } catch (e) { /* ignore */ }
    }

    // Same rule as DescEditSafety.isBlank on the server.
    function isBlank(html) {
        if (!html) return true;
        if (/<\s*(img|iframe|table|hr)\b/i.test(html)) return false;
        var t = html.replace(/<[^>]*>/g, ' ').replace(/&nbsp;|&#160;|&#xa0;| /gi, ' ').replace(/^\s+|\s+$/g, '');
        return t === '' || /^add your content here\s*\.?$/i.test(t);
    }

    // Minutes since midnight in California, or -1 if the browser cannot tell.
    function pacificMinutes() {
        try {
            var parts = new Intl.DateTimeFormat('en-US', {
                timeZone: 'America/Los_Angeles', hour: 'numeric', minute: 'numeric', hourCycle: 'h23'
            }).formatToParts(new Date());
            var h = -1, m = 0;
            for (var i = 0; i < parts.length; i++) {
                if (parts[i].type === 'hour') h = parseInt(parts[i].value, 10) % 24;
                if (parts[i].type === 'minute') m = parseInt(parts[i].value, 10);
            }
            return h < 0 ? -1 : h * 60 + m;
        } catch (e) {
            return -1;
        }
    }

    function el(tag, text) {
        var e = document.createElement(tag);
        if (text) e.appendChild(document.createTextNode(text));
        return e;
    }

    function button(label, onClick) {
        var b = el('button', label);
        b.type = 'button';
        b.style.marginLeft = '8px';
        b.onclick = onClick;
        return b;
    }

    function Guard(textareaId, opts) {
        this.textarea = document.getElementById(textareaId);
        this.form = this.textarea ? this.textarea.form : null;
        this.opts = opts || {};
        this.instance = null;      // CKEditor instance, or null for the plain text box
        this.ready = false;
        this.baseline = null;      // text when the editor became ready
        this.lastText = null;
        this.dirty = !!this.opts.refused;
        this.submitting = false;
        this.clearing = false;
        this.restartWarned = false;
        this.notReadyShown = false;
        this.bar = document.getElementById('descEditSafetyBar');
    }

    Guard.prototype.field = function (name) {
        return this.form ? this.form.elements[name] : null;
    };

    Guard.prototype.getText = function () {
        if (this.instance) return this.instance.getData();
        return this.textarea ? this.textarea.value : '';
    };

    Guard.prototype.setText = function (text) {
        if (this.instance) this.instance.setData(text);
        else if (this.textarea) this.textarea.value = text;
    };

    // Show a message (plain text) with optional buttons in the bar above the editor.
    Guard.prototype.say = function (message, buttons, isError) {
        if (!this.bar) {
            if (isError) window.alert(message);
            return;
        }
        while (this.bar.firstChild) this.bar.removeChild(this.bar.firstChild);
        var b = el('b', message);
        this.bar.appendChild(b);
        for (var i = 0; buttons && i < buttons.length; i++) this.bar.appendChild(buttons[i]);
        this.bar.style.borderColor = isError ? '#b00000' : '#b07000';
        this.bar.style.background = isError ? '#fff3f3' : '#fff8e6';
        this.bar.style.display = 'block';
        try { this.bar.scrollIntoView({block: 'nearest'}); } catch (e) { /* ignore */ }
    };

    Guard.prototype.hideBar = function () {
        if (this.bar) this.bar.style.display = 'none';
    };

    Guard.prototype.saveDraft = function () {
        if (!this.ready) return;
        var text = this.getText();
        if (!this.dirty && text === this.baseline) return;
        storageSet(this.opts.draftKey, {
            text: text, loadedHash: this.opts.loadedHash, savedAt: new Date().getTime(), field: this.opts.fieldName
        });
    };

    Guard.prototype.markReady = function () {
        if (this.ready) return;
        var text = this.getText();
        if (this.opts.hadText && !this.opts.refused && isBlank(text)) {
            this.notReadyShown = true;
            this.say('The editor did not show the stored text of this field. Do not type here: reload the page.'
                + ' (Saving is blocked so the stored text cannot be overwritten.)', null, true);
            return;
        }
        this.ready = true;
        this.baseline = text;
        this.lastText = text;
        var r = this.field('editorReady');
        if (r) r.value = '1';
        this.offerDraft();
        var self = this;
        window.setInterval(function () { self.poll(); }, POLL_MS);
        window.setInterval(function () { self.keepAlive(); }, KEEPALIVE_MS);
        window.setInterval(function () { self.restartWarning(); }, 60000);
        this.restartWarning();
    };

    Guard.prototype.offerDraft = function () {
        // After a refused save the editor already holds the text that was sent.
        if (this.opts.refused) return;
        var d = storageGet(this.opts.draftKey);
        if (!d || typeof d.text !== 'string' || isBlank(d.text) || d.text === this.baseline) return;
        var when = '';
        try { when = ' from ' + new Date(d.savedAt).toLocaleString(); } catch (e) { /* ignore */ }
        var msg = 'This browser has unsaved text for this field' + when + '.';
        if (d.loadedHash && d.loadedHash !== this.opts.loadedHash) {
            msg += ' The stored text has changed since then, so compare before you save.';
        }
        var self = this;
        this.say(msg, [
            button('Restore my unsaved text', function () {
                self.setText(d.text);
                self.dirty = true;
                self.hideBar();
            }),
            button('Discard it', function () {
                storageRemove(self.opts.draftKey);
                self.hideBar();
            })
        ], false);
    };

    Guard.prototype.poll = function () {
        if (!this.ready || this.submitting) return;
        var text = this.getText();
        if (text === this.lastText) return;
        this.lastText = text;
        if (text !== this.baseline) this.dirty = true;
        this.saveDraft();
    };

    // Ask the server whether the login is still valid. callback({ok, loggedIn, canEdit, csrf}).
    Guard.prototype.checkSession = function (callback) {
        var done = false;
        function finish(result) {
            if (done) return;
            done = true;
            callback(result);
        }
        try {
            var xhr = new XMLHttpRequest();
            var sep = this.opts.sessionUrl.indexOf('?') < 0 ? '?' : '&';
            xhr.open('GET', this.opts.sessionUrl + sep + 't=' + new Date().getTime(), true);
            xhr.timeout = CHECK_TIMEOUT_MS;
            xhr.setRequestHeader('Accept', 'application/json');
            xhr.onload = function () {
                var data = null;
                try { data = JSON.parse(xhr.responseText); } catch (e) { data = null; }
                if (xhr.status !== 200 || !data || typeof data.loggedIn !== 'boolean') finish({ok: false});
                else finish({ok: true, loggedIn: data.loggedIn, canEdit: !!data.canEdit, csrf: data.csrf});
            };
            xhr.onerror = function () { finish({ok: false}); };
            xhr.ontimeout = function () { finish({ok: false}); };
            xhr.send();
        } catch (e) {
            finish({ok: false});
        }
    };

    Guard.prototype.setToken = function (token) {
        if (!token || !this.form) return;
        var inputs = this.form.querySelectorAll('input[name="_csrf"]');
        for (var i = 0; i < inputs.length; i++) inputs[i].value = token;
        // A token in the action URL would be checked first: never keep a stale one there.
        var action = this.form.getAttribute('action') || '';
        if (action.indexOf('_csrf=') >= 0) {
            action = action.replace(/([?&])_csrf=[^&#]*&?/, '$1').replace(/[?&](#|$)/, '$1');
            this.form.setAttribute('action', action);
        }
    };

    Guard.prototype.loggedOutMessage = function () {
        var login = el('a', 'Log in (opens a new tab)');
        login.href = this.opts.loginUrl;
        login.target = '_blank';
        login.rel = 'noopener';
        login.style.marginLeft = '8px';
        this.say('You are not logged in any more (AntWeb restarts every morning around 8 AM Pacific, and logins end'
            + ' after 4 hours without use). Your text is kept in this browser. Log in in the new tab, come back here,'
            + ' and press Save again.', [login], true);
    };

    Guard.prototype.keepAlive = function () {
        if (!this.ready || !this.dirty || this.submitting) return;
        var self = this;
        this.saveDraft();
        this.checkSession(function (res) {
            if (res.ok && !res.loggedIn) self.loggedOutMessage();
            else if (res.ok && res.csrf) self.setToken(res.csrf);
        });
    };

    Guard.prototype.restartWarning = function () {
        if (this.restartWarned || !this.dirty) return;
        var m = pacificMinutes();
        // The nightly jobs end with an automatic restart around 8 AM Pacific.
        if (m >= 7 * 60 + 15 && m <= 8 * 60 + 45) {
            this.restartWarned = true;
            this.say('AntWeb restarts automatically around 8 AM Pacific time. Please save soon. If a save fails,'
                + ' your text is kept in this browser and offered again when you reopen this field.', null, false);
        }
    };

    Guard.prototype.submitNow = function () {
        this.submitting = true;
        this.saveDraft();
        if (this.instance && this.instance.updateElement) this.instance.updateElement();
        var c = this.field('clearField');
        if (c) c.value = this.clearing ? '1' : '0';
        // The native submit() does not fire the submit event again.
        HTMLFormElement.prototype.submit.call(this.form);
    };

    Guard.prototype.trySubmit = function () {
        var self = this;
        if (!this.ready) {
            this.say('Nothing was saved: the editor has not finished loading the stored text. Reload the page.', null, true);
            return;
        }
        if (!this.clearing && this.opts.hadText && isBlank(this.getText())) {
            this.say('The box is empty, and an empty box is never saved over the stored text. To remove all text from'
                + ' this field, use the "Clear this field" button.', null, true);
            return;
        }
        this.saveDraft();
        var buttons = this.form.querySelectorAll('input[type="submit"], button.descEditClear');
        for (var i = 0; i < buttons.length; i++) buttons[i].disabled = true;
        function enable() {
            for (var j = 0; j < buttons.length; j++) buttons[j].disabled = false;
        }
        this.checkSession(function (res) {
            if (res.ok && res.loggedIn && res.canEdit) {
                self.setToken(res.csrf);
                self.submitNow();
                return;
            }
            enable();
            self.clearing = false;
            if (res.ok && !res.loggedIn) {
                self.loggedOutMessage();
            } else if (res.ok) {
                self.say('Your account cannot edit page text, so nothing was saved. Your text is kept in this browser.', null, true);
            } else {
                self.say('AntWeb did not answer the login check (it may be busy or restarting). Your text is kept in this'
                    + ' browser. Try Save again in a few minutes.', [button('Save anyway', function () {
                        self.hideBar();
                        self.submitNow();
                    })], true);
            }
        });
    };

    Guard.prototype.start = function () {
        var self = this;
        if (!this.textarea || !this.form) return;

        this.form.addEventListener('submit', function (e) {
            if (self.submitting) return;
            e.preventDefault();
            self.clearing = false;
            self.trySubmit();
        });

        var clearButtons = this.form.querySelectorAll('button.descEditClear');
        for (var i = 0; i < clearButtons.length; i++) {
            clearButtons[i].addEventListener('click', function () {
                if (!window.confirm('Remove all text from ' + (self.opts.fieldName || 'this field')
                        + '? The old text is kept in the page history.')) return;
                self.clearing = true;
                self.trySubmit();
            });
        }

        window.addEventListener('beforeunload', function (e) {
            if (self.submitting || !self.ready) return undefined;
            self.poll();
            if (!self.dirty || self.getText() === self.baseline) return undefined;
            self.saveDraft();
            var msg = 'You have unsaved text. It is kept in this browser as a draft.';
            (e || window.event).returnValue = msg;
            return msg;
        });

        if (window.CKEDITOR) {
            window.CKEDITOR.on('instanceReady', function (ev) {
                if (!ev.editor || ev.editor.name !== self.textarea.id) return;
                self.instance = ev.editor;
                self.markReady();
            });
            window.setTimeout(function () {
                if (!self.ready && !self.notReadyShown) {
                    self.say('The editor did not finish loading. Reload the page before typing.'
                        + ' (Saving is blocked until the editor shows the stored text.)', null, true);
                }
            }, READY_TIMEOUT_MS);
        } else {
            // The rich-text editor did not load: the plain box holds exactly the stored HTML.
            var ready = function () {
                var note = el('p', 'The rich-text editor did not load, so you are editing the HTML source directly.'
                    + ' Save works as usual.');
                note.className = 'descEditPlainNote';
                self.textarea.parentNode.insertBefore(note, self.textarea);
                self.markReady();
            };
            if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ready);
            else ready();
        }
    };

    window.AntwebEditSafety = {
        watch: function (textareaId, opts) {
            var guard = new Guard(textareaId, opts);
            guard.start();
            return guard;
        },
        isBlank: isBlank,
        clearDraft: storageRemove
    };
})(window, document);
