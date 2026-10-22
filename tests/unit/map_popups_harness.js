// Runs AntWeb's real map scripts (web/maps/sdk/drawMap.js and web/maps/drawGoogleMap.js) under
// Node with a stand-in for Leaflet and the page, then runs map functions the way a page does and
// prints every marker popup's HTML as JSON. Used by tests/unit/test_map_popups.py.
//
// Usage: node map_popups_harness.js <repo root> <file with one map function call per line>
"use strict";
const fs = require("fs");
const path = require("path");
const vm = require("vm");

const [root, callsFile] = process.argv.slice(2);
const popups = [];

function chain() {
  // Any Leaflet object: every method returns the object itself.
  const o = new Proxy(function () {}, {
    get: (t, k) => (k === "then" ? undefined : (...a) => o),
    apply: () => o,
  });
  return o;
}

const L = {
  map: () => ({ setView() { return this; }, fitBounds() {}, invalidateSize() {} }),
  tileLayer: () => chain(),
  layerGroup: () => ({ addTo() { return this; }, addLayer() {} }),
  control: Object.assign(() => chain(), { layers: () => chain(), zoom: () => chain(), attribution: () => chain() }),
  featureGroup: () => ({ getBounds: () => null }),
  circleMarker: () => {
    const m = { bindPopup(html) { popups.push(html); return m; }, on() { return m; } };
    return m;
  },
};

const element = () => ({ style: {}, appendChild() {}, set textContent(v) {}, id: "" });
const document = {
  createElement: element,
  head: { appendChild() {} },
  body: { appendChild() {} },
  getElementById: () => element(),
  addEventListener() {},
};

const sandbox = { L, document, console, setTimeout: () => 0 };
sandbox.window = sandbox;
vm.createContext(sandbox);
for (const f of ["web/maps/sdk/drawMap.js", "web/maps/drawGoogleMap.js"]) {
  vm.runInContext(fs.readFileSync(path.join(root, f), "utf8"), sandbox, { filename: f });
}

const out = [];
for (const line of fs.readFileSync(callsFile, "utf8").split("\n")) {
  if (!line.trim()) continue;
  popups.length = 0;
  vm.runInContext(line, sandbox);
  out.push(popups.slice());
}
process.stdout.write(JSON.stringify(out));
