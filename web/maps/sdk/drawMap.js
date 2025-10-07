(function (global) {
  // ---- SETTINGS ----
  const USE_CLUSTER = false; // set to true if you ever want the numeric balloons back
  const DOT_STYLE = {
    radius: 4,        // size of each red dot
    color: "#b30000", // stroke
    weight: 1,
    fillColor: "#ff3b3b",
    fillOpacity: 0.9
  };

  let map = null;
  let layer = null;        // LayerGroup (no cluster) OR MarkerClusterGroup
  let dots = [];           // keep references to markers for fitBounds()

  function ensureMap(divName, lat, lon, zoom) {
    if (map) return map;

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

    const cfg = global.__MAP_SVC_CFG__ || {};
    const tileUrl = cfg.TILE_URL || "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";
    const tileAttr = cfg.TILE_ATTR || "© OpenStreetMap contributors";

    // preferCanvas makes thousands of circleMarkers smooth
    map = L.map(el, { preferCanvas: true })
        .setView([parseFloat(lat) || 0, parseFloat(lon) || 0], zoom || 2);

    L.tileLayer(tileUrl, { maxZoom: 19, attribution: tileAttr }).addTo(map);

    layer = USE_CLUSTER
        ? L.markerClusterGroup({ chunkedLoading: true })
        : L.layerGroup();

    map.addLayer(layer);
    return map;
  }

  function addDot(lat, lon, html) {
    if (lat == null || lon == null) return null;
    const y = parseFloat(lat), x = parseFloat(lon);
    if (Number.isNaN(y) || Number.isNaN(x)) return null;

    // circleMarker = small red dot (no default pin, no numbers)
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

  // ---- Public API (keeps your current function names) ----
  global.drawMap = function (divName, latArray, lonArray) {
    const lat0 = latArray?.length ? parseFloat(latArray[0]) : 0;
    const lon0 = lonArray?.length ? parseFloat(lonArray[0]) : 0;
    ensureMap(divName, lat0, lon0, 5);

    const n = Math.min(latArray?.length || 0, lonArray?.length || 0);
    for (let i = 0; i < n; i++) addDot(latArray[i], lonArray[i]);
    fitToDots();
  };

  global.drawMapLocalities = function (divName, latArray, lonArray, nameArray, codeArray) {
    const lat0 = latArray?.length ? parseFloat(latArray[0]) : 0;
    const lon0 = lonArray?.length ? parseFloat(lonArray[0]) : 0;
    ensureMap(divName, lat0, lon0, 5);

    const n = Math.min(latArray?.length || 0, lonArray?.length || 0);
    for (let i = 0; i < n; i++) {
      const name = (nameArray && nameArray[i]) || "";
      const code = (codeArray && codeArray[i]) || "";
      const html = `<div><b>Locality:</b> ${name}<br/><b>Code:</b> ${code}</div>`;
      addDot(latArray[i], lonArray[i], html);
    }
    fitToDots();
  };

  global.drawMapSpecimens = function (divName, latArray, lonArray, nameArray, codeArray, imageArray) {
    const lat0 = latArray?.length ? parseFloat(latArray[0]) : 0;
    const lon0 = lonArray?.length ? parseFloat(lonArray[0]) : 0;
    ensureMap(divName, lat0, lon0, 5);

    const n = Math.min(latArray?.length || 0, lonArray?.length || 0);
    for (let i = 0; i < n; i++) {
      const name = (nameArray && nameArray[i]) || "";
      const code = (codeArray && codeArray[i]) || "";
      const img  = (imageArray && imageArray[i]) || "";
      const imgTag = img ? `<br/><img src="${img}" style="max-width:160px"/>` : "";
      const html = `<div><b>Specimen:</b> ${name}<br/><b>Code:</b> ${code}${imgTag}</div>`;
      addDot(latArray[i], lonArray[i], html);
    }
    fitToDots();
  };

  global.drawMapSinglePoint = function (divName, lat, lon, name, code, img) {
    ensureMap(divName, parseFloat(lat) || 0, parseFloat(lon) || 0, 8);
    const imgTag = img ? `<br/><img src="${img}" style="max-width:160px"/>` : "";
    const html = `<div>${name || ""}<br/>${code || ""}${imgTag}</div>`;
    addDot(lat, lon, html);
    fitToDots();
  };
})(window);
