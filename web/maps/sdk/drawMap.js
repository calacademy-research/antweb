(function (global) {
  const USE_CLUSTER = false;
  const DOT_STYLE = {
    radius: 5,
    color: "#ffffff",      // white border
    weight: 2,
    fillColor: "#ff3b3b",  // red dot
    fillOpacity: 1
  };

  // ==============================
  // GOOGLE MAPS–STYLE LAYERING FIX
  // ==============================
  const style = document.createElement("style");
  style.textContent = `
    /* --- Map base container (behind everything) --- */
    .leaflet-container {
      position: absolute !important;
      inset: 0 !important;
      z-index: 0 !important;
    }

    /* --- Leaflet internal layers, no stacking context --- */
    .leaflet-pane,
    .leaflet-tile-pane,
    .leaflet-overlay-pane,
    .leaflet-shadow-pane,
    .leaflet-marker-pane,
    .leaflet-popup-pane {
      z-index: auto !important;
    }

    /* --- Popups and markers visible but not dominant --- */
    .leaflet-marker-icon,
    .leaflet-popup {
      z-index: 10 !important;
    }

    /* --- Control containers --- */
    .leaflet-control-container {
      z-index: 20 !important;
      pointer-events: auto !important;
    }

    /* --- Zoom control bottom-right --- */
    .leaflet-control-zoom {
      border-radius: 6px;
      background: rgba(255,255,255,0.9);
      box-shadow: 0 0 3px rgba(0,0,0,0.25);
    }
    .leaflet-control-zoom a {
      color: #333 !important;
      text-decoration: none;
    }

    /* --- Fullscreen control top-right --- */
    .leaflet-control-fullscreen {
      border-radius: 6px;
      background: rgba(255,255,255,0.9);
      box-shadow: 0 0 3px rgba(0,0,0,0.25);
    }
    .leaflet-control-fullscreen a {
      color: #333 !important;
      text-decoration: none;
    }

    /* --- Attribution bottom-left --- */
    .leaflet-control-attribution {
      font-size: 10px !important;
      background: rgba(255,255,255,0.8);
      border-radius: 3px;
      padding: 2px 4px;
      margin: 4px;
      z-index: 15 !important;
    }

    /* --- Keep AntWeb UI above map --- */
    .ui-autocomplete,
    .dropdown-menu,
    .select2-container,
    .suggestions,
    div[id*="taxaList"],
    .ui-menu,
    .autocomplete-suggestions,
    .modal,
    .popover {
      position: relative;
      z-index: 9999 !important;
    }
  `;
  document.head.appendChild(style);

  // ==============================
  // STATE
  // ==============================
  let map = null;
  let layer = null;
  let dots = [];

  // ==============================
  // CREATE MAP
  // ==============================
  function ensureMap(divName, lat, lon, zoom) {
    if (map) return map;

    let el =
        document.getElementById(divName) ||
        document.getElementById("map") ||
        document.getElementById("map-canvas");

    if (!el) {
      el = document.createElement("div");
      el.id = divName || "map";
      el.style.height = "500px";
      el.style.width = "100%";
      el.style.position = "absolute";
      el.style.top = "0";
      el.style.left = "0";
      document.body.appendChild(el);
    }

    const cfg = global.__MAP_SVC_CFG__ || {};
    const tileUrl = cfg.TILE_URL || "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";
    const tileAttr = cfg.TILE_ATTR || "© OpenStreetMap contributors";

    map = L.map(el, {
      preferCanvas: true,
      zoomControl: false,
      attributionControl: false,
      fullscreenControl: true   // enable plugin control
    }).setView([parseFloat(lat) || 0, parseFloat(lon) || 0], zoom || 2);

    // Add controls
    L.control.zoom({ position: "bottomright" }).addTo(map);
    L.control.fullscreen({ position: "topright", title: "View large map" }).addTo(map);
    L.control.attribution({ position: "bottomleft", prefix: "Leaflet" }).addTo(map);

    // Base tiles
    L.tileLayer(tileUrl, { maxZoom: 19, attribution: tileAttr }).addTo(map);

    // Marker/cluster layer
    layer = USE_CLUSTER
        ? L.markerClusterGroup({
          chunkedLoading: true,
          maxClusterRadius: 40,
          iconCreateFunction: (cluster) => {
            const count = cluster.getChildCount();
            return L.divIcon({
              html: `<div style="
                background: rgba(255,59,59,0.9);
                border: 2px solid #fff;
                color: #fff;
                border-radius: 50%;
                width: 26px; height: 26px;
                display: flex; align-items: center; justify-content: center;
                font-size: 11px; font-weight: 600;">${count}</div>`,
              className: "dot-cluster-wrap",
              iconSize: [26, 26]
            });
          }
        })
        : L.layerGroup();

    map.addLayer(layer);
    return map;
  }

  // ==============================
  // MARKERS
  // ==============================
  function addDot(lat, lon, html) {
    if (lat == null || lon == null) return null;
    const y = parseFloat(lat), x = parseFloat(lon);
    if (Number.isNaN(y) || Number.isNaN(x)) return null;

    const m = L.circleMarker([y, x], DOT_STYLE);
    if (html) m.bindPopup(html);
    layer.addLayer(m);
    dots.push(m);
    return m;
  }

  function fitToDots(padding) {
    if (!dots.length) return;
    const group = L.featureGroup(dots);
    map.fitBounds(group.getBounds(), { padding: padding || [30, 30] });
  }

  // ==============================
  // PUBLIC FUNCTIONS (AntWeb compatible)
  // ==============================
  global.drawMap = function (divName, latArray, lonArray) {
    const lat0 = latArray?.[0] ? parseFloat(latArray[0]) : 0;
    const lon0 = lonArray?.[0] ? parseFloat(lonArray[0]) : 0;
    ensureMap(divName, lat0, lon0, 5);
    for (let i = 0; i < Math.min(latArray?.length || 0, lonArray?.length || 0); i++)
      addDot(latArray[i], lonArray[i]);
    fitToDots();
  };

  global.drawMapLocalities = function (divName, latArray, lonArray, nameArray, codeArray) {
    const lat0 = latArray?.[0] ? parseFloat(latArray[0]) : 0;
    const lon0 = lonArray?.[0] ? parseFloat(lonArray[0]) : 0;
    ensureMap(divName, lat0, lon0, 5);
    for (let i = 0; i < Math.min(latArray?.length || 0, lonArray?.length || 0); i++) {
      const html = `<div><b>Locality:</b> ${nameArray?.[i] || ""}<br/><b>Code:</b> ${codeArray?.[i] || ""}</div>`;
      addDot(latArray[i], lonArray[i], html);
    }
    fitToDots();
  };

  global.drawMapSpecimens = function (divName, latArray, lonArray, nameArray, codeArray, imageArray) {
    const lat0 = latArray?.[0] ? parseFloat(latArray[0]) : 0;
    const lon0 = lonArray?.[0] ? parseFloat(lonArray[0]) : 0;
    ensureMap(divName, lat0, lon0, 5);
    for (let i = 0; i < Math.min(latArray?.length || 0, lonArray?.length || 0); i++) {
      const img = imageArray?.[i] || "";
      const html = `<div><b>Specimen:</b> ${nameArray?.[i] || ""}<br/><b>Code:</b> ${codeArray?.[i] || ""}${img ? `<br/><img src="${img}" style="max-width:160px"/>` : ""}</div>`;
      addDot(latArray[i], lonArray[i], html);
    }
    fitToDots();
  };

  global.drawMapSinglePoint = function (divName, lat, lon, name, code, img) {
    ensureMap(divName, parseFloat(lat) || 0, parseFloat(lon) || 0, 8);
    const html = `<div>${name || ""}<br/>${code || ""}${img ? `<br/><img src="${img}" style="max-width:160px"/>` : ""}</div>`;
    addDot(lat, lon, html);
    fitToDots();
  };
})(window);
