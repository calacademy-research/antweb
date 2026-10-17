/*
  responsive.js: small-screen behaviour that CSS alone cannot do (see responsive.css).

  1. The phone menu button (#aw_menu_toggle in academyHeader.jsp) opens and closes the
     header menu by toggling body.aw-nav-open.
  2. At phone width, a table wider than the box it sits in is wrapped in
     div.aw-table-scroll, so it scrolls sideways inside that box instead of making
     the whole page scroll. Tables in the search bar and in editors are left alone.

  Plain JavaScript (no jQuery needed); loaded with defer by layout.jsp.
*/
(function () {
    'use strict';

    var PHONE = '(max-width: 760px)';
    var SKIP = '.aw-table-scroll, #masthead, #navigation, .tox, .mce-container, .cke';

    function isPhone() {
        return !!(window.matchMedia && window.matchMedia(PHONE).matches);
    }

    function setupMenuButton() {
        var button = document.getElementById('aw_menu_toggle');
        if (!button) return;
        button.addEventListener('click', function () {
            var open = document.body.classList.toggle('aw-nav-open');
            button.setAttribute('aria-expanded', open ? 'true' : 'false');
        });
    }

    function wrapWideTables() {
        if (!isPhone()) return;
        var root = document.getElementById('contents') || document.body;
        var tables = root.getElementsByTagName('table');
        // copy first: wrapping changes the live collection
        var list = Array.prototype.slice.call(tables);
        for (var i = 0; i < list.length; i++) {
            var table = list[i];
            var parent = table.parentNode;
            if (!parent || !table.closest || table.closest(SKIP)) continue;
            if (table.offsetWidth <= parent.clientWidth + 1) continue;
            var box = document.createElement('div');
            box.className = 'aw-table-scroll';
            parent.insertBefore(box, table);
            box.appendChild(table);
        }
    }

    function start() {
        setupMenuButton();
        wrapWideTables();
        var timer = null;
        window.addEventListener('resize', function () {
            clearTimeout(timer);
            timer = setTimeout(wrapWideTables, 250);
        });
        // content that appears later (e.g. images loaded, editor preview) can widen a table
        window.addEventListener('load', wrapWideTables);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start);
    } else {
        start();
    }
})();
