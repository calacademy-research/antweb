/*
  responsive.js: small-screen behaviour that CSS alone cannot do (see responsive.css).

  1. The phone menu button (#aw_menu_toggle in academyHeader.jsp) opens and closes the
     header menu by toggling body.aw-nav-open.
  2. Up to 1000 px wide, a table wider than the box it sits in is wrapped in
     div.aw-table-scroll, so it scrolls sideways inside that box instead of making
     the whole page scroll. Tables in the search bar and in editors are left alone.
     The div lists (div.aw-list-scroll, written by the list JSPs) scroll the same way.
  3. Each scroll box gets a div.aw-scroll-frame around it, whose classes say whether
     more columns are hidden to the right (aw-more-right: edge shadow) and whether the
     box is scrolled (aw-scrolled: shadow beside the sticky first column).
  4. At phone width, simple data tables (no merged cells, first column under half the
     box) get class aw-sticky-first: the first column stays in view, each first cell
     taking its row's background colour so the row shading continues under it.

  Plain JavaScript (no jQuery needed); loaded with defer by layout.jsp.
*/
(function () {
    'use strict';

    var PHONE = '(max-width: 760px)';
    var NARROW = '(max-width: 1000px)';
    var SKIP = '.aw-table-scroll, .aw-list-scroll, #masthead, #navigation, .tox, .mce-container, .cke';
    var STICKY_MAX_ROWS = 3000;

    function matches(query) {
        return !!(window.matchMedia && window.matchMedia(query).matches);
    }

    function setupMenuButton() {
        var button = document.getElementById('aw_menu_toggle');
        if (!button) return;
        button.addEventListener('click', function () {
            var open = document.body.classList.toggle('aw-nav-open');
            button.setAttribute('aria-expanded', open ? 'true' : 'false');
        });
    }

    // Edge shadow and scrolled state for one scroll box (wrapped once in div.aw-scroll-frame).
    function frame(box) {
        var f = box.parentNode;
        if (f && f.classList && f.classList.contains('aw-scroll-frame')) return f;
        f = document.createElement('div');
        f.className = 'aw-scroll-frame';
        box.parentNode.insertBefore(f, box);
        f.appendChild(box);
        var update = function () {
            var max = box.scrollWidth - box.clientWidth;
            f.classList.toggle('aw-more-right', max > 1 && box.scrollLeft < max - 1);
            f.classList.toggle('aw-scrolled', box.scrollLeft > 0);
        };
        box.addEventListener('scroll', update, {passive: true});
        box.awUpdate = update;
        update();
        return f;
    }

    function isTransparent(color) {
        return !color || color === 'transparent' || /rgba\(.*,\s*0\)$/.test(color);
    }

    // Background a row shows: its own, else the nearest ancestor's that is not transparent.
    function rowBackground(row) {
        for (var el = row; el && el.nodeType === 1; el = el.parentNode) {
            var c = window.getComputedStyle(el).backgroundColor;
            if (!isTransparent(c)) return c;
        }
        return '#fff';
    }

    function makeFirstColumnSticky(table, box) {
        if (table.classList.contains('aw-sticky-first')) return;
        var rows = table.rows;
        if (rows.length < 2 || rows.length > STICKY_MAX_ROWS) return;
        var limit = box.clientWidth / 2;
        var i, cell;
        for (i = 0; i < rows.length; i++) {
            cell = rows[i].cells[0];
            if (!cell) continue;
            if (cell.colSpan > 1 || cell.rowSpan > 1 || cell.offsetWidth > limit) return;
        }
        for (i = 0; i < rows.length; i++) {
            cell = rows[i].cells[0];
            if (cell && isTransparent(window.getComputedStyle(cell).backgroundColor)) {
                cell.style.backgroundColor = rowBackground(rows[i]);
            }
        }
        table.classList.add('aw-sticky-first');
    }

    function wrapWideTables() {
        if (!matches(NARROW)) return;
        var phone = matches(PHONE);
        var root = document.getElementById('contents') || document.body;
        // copy first: wrapping changes the live collection
        var list = Array.prototype.slice.call(root.getElementsByTagName('table'));
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
        var boxes = root.querySelectorAll('.aw-table-scroll, .aw-list-scroll');
        for (var j = 0; j < boxes.length; j++) {
            frame(boxes[j]);
            if (phone && boxes[j].classList.contains('aw-table-scroll')) {
                var t = boxes[j].firstElementChild;
                if (t && t.tagName === 'TABLE') makeFirstColumnSticky(t, boxes[j]);
            }
            boxes[j].awUpdate();
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
