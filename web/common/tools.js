/* Tool pages (see common/tools.css and docs/guides/page-style.md). Two small helpers for the tables of a
   page inside <div class="aw-tools">; the page works the same without them:
   1. each table is put in a <div class="aw-table-wrap">, so a wide table scrolls inside its card on a
      phone instead of widening the page;
   2. a column whose cells are all numbers (1,234  12.5  3.  -7  45%) gets class aw-num: right-aligned.
   Many of these tables are built in Java (named queries, the count check), so the columns are found
   here rather than marked in each place. */
(function () {
  "use strict";
  var NUM = /^[-+]?(\d{1,3}(,\d{3})+|\d+)(\.\d*)?\s*%?$/;

  function markNumericColumns(table) {
    var rows = table.rows, counts = [], i, j, row, cell, text, col;
    for (i = 0; i < rows.length; i++) {
      row = rows[i];
      col = 0;
      for (j = 0; j < row.cells.length; j++) {
        cell = row.cells[j];
        if (cell.colSpan > 1) { col = -1; break; }   // a spanning row: column positions are off
        if (cell.tagName === "TD") {
          text = (cell.textContent || "").replace(/ /g, " ").trim();
          if (text !== "") {
            counts[col] = counts[col] || { num: 0, other: 0 };
            if (NUM.test(text)) counts[col].num++; else counts[col].other++;
          }
        }
        col++;
      }
    }
    for (i = 0; i < rows.length; i++) {
      row = rows[i];
      if (row.cells.length && row.cells[0].colSpan > 1) continue;
      for (j = 0; j < row.cells.length; j++) {
        if (row.cells[j].colSpan > 1) break;
        if (counts[j] && counts[j].num > 0 && counts[j].other === 0) row.cells[j].classList.add("aw-num");
      }
    }
  }

  function run() {
    var tables = document.querySelectorAll(".aw-tools table"), k, t, wrap;
    for (k = 0; k < tables.length; k++) {
      t = tables[k];
      if (!t.classList.contains("aw-kv")) markNumericColumns(t);
      if (t.parentNode && !(t.parentNode.classList && t.parentNode.classList.contains("aw-table-wrap"))
          && !t.parentNode.closest("td, th")) {
        wrap = document.createElement("div");
        wrap.className = "aw-table-wrap";
        t.parentNode.insertBefore(wrap, t);
        wrap.appendChild(t);
      }
    }
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", run);
  else run();
})();
