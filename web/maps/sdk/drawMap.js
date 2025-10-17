(function (global) {
  // ---- SETTINGS ----
  const DOT_STYLE = {
    radius: 5,
    color: "#ffffff",       // white border stroke
    weight: 2,              // thicker border
    fillColor: "#ff3b3b",   // inner red
    fillOpacity: 1
  };

  // ---- Z-index + layout fixes ----
  const style = document.createElement("style");
  style.textContent = `
    /* Keep map behind everything else */
    .leaflet-container {
      position: relative !important;
      z-index: 0 !important;
    }

    /* Allow menus and dropdowns above map */
    .ui-autocomplete,
    .dropdown-menu,
    .select2-container,
    .suggestions,
    div[id*="taxaList"],
    .ui-menu {
      position: relative !important;
      z-index: 9999 !important;
    }

    /* Prevent control overlap */
    .leaflet-control-container {
      z-index: 200 !important;
    }

    /* Layer switcher style */
    .leaflet-control-layers {
      background: rgba(255,255,255,0.95);
      border-radius: 6px;
      box-shadow: 0 2px 6px rgba(0,0,0,0.25);
    }

    .leaflet-control-fullscreen-button {
      background: white !important;
      border-radius: 4px;
    }

    /* Attribution small and subtle */
    .leaflet-control-attribution {
      font-size: 11px !important;
      background: rgba(255,255,255,0.8);
      padding: 2px 6px;
      border-radius: 4px;
    }
  `;
  document.head.appendChild(style);

  // ---- Registry for multiple maps ----
  const mapRegistry = {};

  function initMap(divName, lat, lon, zoom) {
    // return existing if already created
    if (mapRegistry[divName]) return mapRegistry[divName];

    let el = document.getElementById(divName) ||
        document.getElementById("map") ||
        document.getElementById("map-canvas");

    if (!el) {
      el = document.createElement("div");
      el.id = divName || "map";
      el.style.height = "500px";
      el.style.width = "100%";
      document.body.appendChild(el);
    }

    const map = L.map(el, {
      preferCanvas: true,
      zoomControl: false,
      attributionControl: false
    }).setView([parseFloat(lat) || 0, parseFloat(lon) || 0], zoom || 2);

    // ---- Base layers ----
    const mapLayer = L.tileLayer(
        "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
        { maxZoom: 19, attribution: "© OpenStreetMap contributors" }
    );

    const terrainLayer = L.tileLayer(
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Terrain_Base/MapServer/tile/{z}/{y}/{x}",
        { maxZoom: 13, attribution: "Terrain © Esri" }
    );

    const satelliteLayer = L.tileLayer(
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
        { maxZoom: 19, attribution: "Imagery © Esri, Maxar, Earthstar Geographics" }
    );

    const labelOverlay = L.tileLayer(
        "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}",
        { maxZoom: 19, attribution: "Labels © Esri" }
    );

    const hybridLayer = L.layerGroup([satelliteLayer, labelOverlay]);

    // ---- Default + control ----
    mapLayer.addTo(map);
    const baseLayers = {
      "Map": mapLayer,
      "Terrain": terrainLayer,
      "Satellite": hybridLayer
    };
    L.control.layers(baseLayers, null, { position: "topright" }).addTo(map);

    // ---- Controls ----
    L.control.zoom({ position: "bottomright" }).addTo(map);
    L.control.attribution({ prefix: "", position: "bottomleft" }).addTo(map);
    if (L.control.fullscreen) {
      L.control.fullscreen({ position: "topright" }).addTo(map);
    }

    // ---- Marker layer ----
    const layer = L.layerGroup().addTo(map);
    mapRegistry[divName] = { map, layer, dots: [] };

    // Fix for grid/hidden div rendering
    setTimeout(() => map.invalidateSize(), 400);

    return mapRegistry[divName];
  }

  // ---- Add dots ----
  function addDot(bucket, lat, lon, html) {
    if (!bucket) return null;
    const y = parseFloat(lat), x = parseFloat(lon);
    if (isNaN(y) || isNaN(x)) return null;

    const m = L.circleMarker([y, x], DOT_STYLE);
    if (html) m.bindPopup(html);
    bucket.layer.addLayer(m);
    bucket.dots.push(m);
    return m;
  }

  function fitToDots(bucket) {
    if (!bucket || !bucket.dots.length) return;
    const group = L.featureGroup(bucket.dots);
    bucket.map.fitBounds(group.getBounds(), { padding: [30, 30] });
  }

  // ---- Public APIs ----
  global.drawMap = function (divName, latArray, lonArray) {
    const lat0 = parseFloat(latArray?.[0]) || 0;
    const lon0 = parseFloat(lonArray?.[0]) || 0;
    const bucket = initMap(divName, lat0, lon0, 5);
    const n = Math.min(latArray?.length || 0, lonArray?.length || 0);
    for (let i = 0; i < n; i++) addDot(bucket, latArray[i], lonArray[i]);
    fitToDots(bucket);
  };

  global.drawMapLocalities = function (divName, latArray, lonArray, nameArray, codeArray) {
    const lat0 = parseFloat(latArray?.[0]) || 0;
    const lon0 = parseFloat(lonArray?.[0]) || 0;
    const bucket = initMap(divName, lat0, lon0, 5);
    const n = Math.min(latArray?.length || 0, lonArray?.length || 0);
    for (let i = 0; i < n; i++) {
      const name = nameArray?.[i] || "";
      const code = codeArray?.[i] || "";
      const html = `<b>${name}</b><br>${code}`;
      addDot(bucket, latArray[i], lonArray[i], html);
    }
    fitToDots(bucket);
  };

  global.drawMapSpecimens = function (divName, latArray, lonArray, nameArray, codeArray, imageArray) {
    const lat0 = parseFloat(latArray?.[0]) || 0;
    const lon0 = parseFloat(lonArray?.[0]) || 0;
    const bucket = initMap(divName, lat0, lon0, 5);
    const n = Math.min(latArray?.length || 0, lonArray?.length || 0);
    for (let i = 0; i < n; i++) {
      const name = nameArray?.[i] || "";
      const code = codeArray?.[i] || "";
      const img = imageArray?.[i] || "";
      const imgTag = img ? `<br><img src="${img}" style="max-width:160px">` : "";
      const html = `<div><b>${name}</b><br>${code}${imgTag}</div>`;
      addDot(bucket, latArray[i], lonArray[i], html);
    }
    fitToDots(bucket);
  };

  global.drawMapSinglePoint = function (divName, lat, lon, name, code, img) {
    const bucket = initMap(divName, lat, lon, 8);
    const imgTag = img ? `<br><img src="${img}" style="max-width:160px">` : "";
    const html = `<div>${name || ""}<br>${code || ""}${imgTag}</div>`;
    addDot(bucket, lat, lon, html);
    fitToDots(bucket);
  };

  // ---- Auto-refresh all maps after render ----
  document.addEventListener("DOMContentLoaded", () => {
    setTimeout(() => {
      Object.values(mapRegistry).forEach(b => b.map.invalidateSize());
    }, 800);
  });
})(window);
