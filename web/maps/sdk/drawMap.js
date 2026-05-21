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

  // --- Localities ---
  global.drawMapLocalities = function (divName, latArray, lonArray, nameArray, codeArray) {
    const bucket = initMap(divName, latArray?.[0] || 0, lonArray?.[0] || 0, 5);
    for (let i = 0; i < latArray.length; i++) {
      const name = nameArray?.[i] || "";
      const code = codeArray?.[i] || "";
      const url = `/locality.do?code=${encodeURIComponent(code)}`;
      const html = `
        <div>
          <b>${name || code}</b><br>
          <a href="${url}" target="_blank">${code}</a>
        </div>`;
      addDot(bucket, latArray[i], lonArray[i], html);
    }
    fitToDots(bucket);
  };

  // --- Specimens ---
  global.drawMapSpecimens = function (divName, latArray, lonArray, nameArray, codeArray, imageArray) {
    const bucket = initMap(divName, latArray?.[0] || 0, lonArray?.[0] || 0, 5);
    for (let i = 0; i < latArray.length; i++) {
      const name = nameArray?.[i] || "";
      const code = codeArray?.[i] || "";
      const img = imageArray?.[i] || "";
      const url = `/specimen.do?name=${encodeURIComponent(code)}`;
      const imgTag = img ? `<br><img src="${img}" alt="${name}">` : "";
      const html = `
        <div>
          <b>${name}</b><br>
          <a href="${url}" target="_blank">${code}</a>${imgTag}
        </div>`;
      addDot(bucket, latArray[i], lonArray[i], html);
    }
    fitToDots(bucket);
  };

  // --- Single Point ---
  global.drawMapSinglePoint = function (divName, lat, lon, name, code, img) {
    const bucket = initMap(divName, lat, lon, 8);
    const url = `/specimen.do?name=${encodeURIComponent(code)}`;
    const imgTag = img ? `<br><img src="${img}" alt="${name}">` : "";
    const html = `
      <div>
        <b>${name}</b><br>
        <a href="${url}" target="_blank">${code}</a>${imgTag}
      </div>`;
    addDot(bucket, lat, lon, html);
  };

  // --- Resize all maps on load ---
  document.addEventListener("DOMContentLoaded", () => {
    setTimeout(() => {
      Object.values(mapRegistry).forEach(b => b.map.invalidateSize());
    }, 800);
  });
})(window);
