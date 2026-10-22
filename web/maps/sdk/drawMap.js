(function (global) {
  const DOT_STYLE = {
    radius: 6,
    color: "#ffffff",
    weight: 2,
    fillColor: "#ff3b3b",
    fillOpacity: 1
  };

  const style = document.createElement("style");
  style.textContent = `
    .leaflet-container { position: relative !important; z-index: 0 !important; }
    .leaflet-popup-content a {
      color: #0074d9;
      text-decoration: none;
      font-weight: 600;
    }
    .leaflet-popup-content a:hover { text-decoration: underline; }
    .leaflet-popup-content img { 
      max-width: 160px; 
      margin-top: 4px; 
      border-radius: 6px; 
      display: block;
    }
  `;
  document.head.appendChild(style);

  const mapRegistry = {};

  function initMap(divName, lat, lon, zoom) {
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

    // --- Base layers ---
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

    mapLayer.addTo(map);
    L.control.layers(
        { "Map": mapLayer, "Terrain": terrainLayer, "Satellite": hybridLayer },
        null,
        { position: "topright" }
    ).addTo(map);

    L.control.zoom({ position: "bottomright" }).addTo(map);
    L.control.attribution({ prefix: "", position: "bottomleft" }).addTo(map);
    if (L.control.fullscreen) L.control.fullscreen({ position: "topright" }).addTo(map);

    // --- No clustering: show all dots directly ---
    const layer = L.layerGroup().addTo(map);

    mapRegistry[divName] = { map, layer, dots: [] };

    // Fix sizing for hidden grid cells
    setTimeout(() => map.invalidateSize(), 400);
    return mapRegistry[divName];
  }

  function addDot(bucket, lat, lon, html) {
    if (!bucket) return null;
    const y = parseFloat(lat), x = parseFloat(lon);
    if (isNaN(y) || isNaN(x)) return null;

    const marker = L.circleMarker([y, x], DOT_STYLE);
    if (html) marker.bindPopup(html, { autoClose: true, closeButton: true });
    marker.on("click", () => marker.openPopup());
    bucket.layer.addLayer(marker);
    bucket.dots.push(marker);
    return marker;
  }

  function fitToDots(bucket) {
    if (!bucket || !bucket.dots.length) return;
    const group = L.featureGroup(bucket.dots);
    bucket.map.fitBounds(group.getBounds(), { padding: [30, 30] });
  }

  // --- Basic dot map ---
  global.drawMap = function (divName, latArray, lonArray) {
    const bucket = initMap(divName, latArray?.[0] || 0, lonArray?.[0] || 0, 4);
    for (let i = 0; i < latArray.length; i++) {
      addDot(bucket, latArray[i], lonArray[i]);
    }
    fitToDots(bucket);
  };

  // --- Popup text and links (antwebMapPopup is also used by tests/unit/test_map_popups.py) ---
  // Locality names and codes, and collection codes, arrive form-encoded ("West+Bengal%2C+India",
  // "loc22.8%2C88.3"): Map.java writes them that way and the maps stored in object_map hold them so.
  // Decode once, then build the link with one encodeURIComponent. Showing them undecoded and encoding
  // them again gave "locality.do?code=loc22.8%252C88.3" (Locality not found), 2026-10-22.
  function formDecode(value) {
    const s = value == null ? "" : String(value);
    try { return decodeURIComponent(s.replace(/\+/g, " ")); } catch (e) { return s; }
  }
  function present(value) {
    const s = value == null ? "" : String(value).trim();
    return s && s !== "null" && s !== "undefined" ? s : "";
  }
  function esc(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, c =>
        ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
  }
  // kind: "locality" (locality.do?code=), "collection" (collection.do?name=) or "specimen" (specimen.do?name=).
  const LINKS = { locality: "/locality.do?code=", collection: "/collection.do?name=", specimen: "/specimen.do?name=" };
  function popupHtml(kind, name, code, img) {
    const encoded = kind === "locality" || kind === "collection";
    const n = present(encoded ? formDecode(name) : name);
    const c = present(encoded ? formDecode(code) : code);
    const title = n || c;
    let html = title ? `<b>${esc(title)}</b>` : "";
    // No link without a code: "locality.do?code=" or "?code=null" only finds "Locality not found".
    if (c) html += `${html ? "<br>" : ""}<a href="${esc(LINKS[kind] + encodeURIComponent(c))}" target="_blank">${esc(c)}</a>`;
    if (present(img)) html += `<br><img src="${esc(img)}" alt="${esc(n)}">`;
    return `<div>${html}</div>`;
  }
  global.antwebMapPopup = { popupHtml, formDecode };

  // --- Localities (and collections: kind "collection") ---
  global.drawMapLocalities = function (divName, latArray, lonArray, nameArray, codeArray, kind) {
    const bucket = initMap(divName, latArray?.[0] || 0, lonArray?.[0] || 0, 5);
    const k = kind === "collection" ? "collection" : "locality";
    for (let i = 0; i < latArray.length; i++) {
      addDot(bucket, latArray[i], lonArray[i], popupHtml(k, nameArray?.[i], codeArray?.[i]));
    }
    fitToDots(bucket);
  };

  // --- Specimens ---
  global.drawMapSpecimens = function (divName, latArray, lonArray, nameArray, codeArray, imageArray) {
    const bucket = initMap(divName, latArray?.[0] || 0, lonArray?.[0] || 0, 5);
    for (let i = 0; i < latArray.length; i++) {
      addDot(bucket, latArray[i], lonArray[i], popupHtml("specimen", nameArray?.[i], codeArray?.[i], imageArray?.[i]));
    }
    fitToDots(bucket);
  };

  // --- Single Point ---
  global.drawMapSinglePoint = function (divName, lat, lon, name, code, img) {
    const bucket = initMap(divName, lat, lon, 8);
    addDot(bucket, lat, lon, popupHtml("specimen", name, code, img));
  };

  // --- Resize all maps on load ---
  document.addEventListener("DOMContentLoaded", () => {
    setTimeout(() => {
      Object.values(mapRegistry).forEach(b => b.map.invalidateSize());
    }, 800);
  });
})(window);
