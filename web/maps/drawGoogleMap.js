// /web/maps/drawGoogleMap.js  (Leaflet adapter)
(function (global) {
    var calls = global.calls || (global.calls = []);
    document.addEventListener("DOMContentLoaded", function(){
        try { runThese(); } catch (e) { console.error("runThese() failed:", e); }
    });

    function runThese() {
        for (var i = 0; i < calls.length; i++) {
            try { /* eslint no-eval:0 */ eval(calls[i]); }
            catch (e) { console.error("Error in calls["+i+"]: ", calls[i], e); }
        }
    }

    function toArray(x){ return Array.isArray(x)? x : [x]; }
    function toNumArray(a){ return toArray(a).map(function(v){ return parseFloat(v); }); }
    function need(fnName){
        if (typeof global[fnName] !== "function") {
            console.error(fnName+" not available. Did your microservice /sdk/drawMap.js load?");
            return false;
        }
        return true;
    }

    // Simple arrays (no popups)
    global.drawGoogleMap = function (mapType, divName, latArray, lonArray) {
        var lats = toNumArray(latArray), lons = toNumArray(lonArray);
        if (!lats.length || !lons.length) return;
        if (!need("drawMap")) return;
        global.drawMap(divName, lats, lons);
    };

    // Localities (name + code)
    global.drawGoogleMapLocalities = function (mapType, divName, latArray, lonArray, nameArray, codeArray) {
        var lats = toNumArray(latArray), lons = toNumArray(lonArray);
        var names = toArray(nameArray||[]), codes = toArray(codeArray||[]);
        if (need("drawMapLocalities")) global.drawMapLocalities(divName, lats, lons, names, codes);
        else if (need("drawMap"))      global.drawMap(divName, lats, lons);
    };

    // Locality (singular)
    global.drawGoogleMapLocality = function (mapType, divName, latArray, lonArray, nameArray, codeArray) {
        global.drawGoogleMapLocalities(mapType, divName, latArray, lonArray, nameArray, codeArray);
    };

    // Specimens (name + code + optional image)
    global.drawGoogleMapSpecimens = function (mapType, divName, latArray, lonArray, nameArray, codeArray, imageArray) {
        var lats = toNumArray(latArray), lons = toNumArray(lonArray);
        var names = toArray(nameArray||[]), codes = toArray(codeArray||[]), imgs = toArray(imageArray||[]);
        if (need("drawMapSpecimens"))       global.drawMapSpecimens(divName, lats, lons, names, codes, imgs);
        else if (need("drawMapLocalities")) global.drawMapLocalities(divName, lats, lons, names, codes);
        else if (need("drawMap"))           global.drawMap(divName, lats, lons);
    };

    // Collections (codes only → use as both name & code)
    global.drawGoogleMapCollection = function (mapType, divName, latArray, lonArray, codeArray) {
        var codes = toArray(codeArray||[]);
        global.drawGoogleMapLocalities(mapType, divName, latArray, lonArray, codes, codes);
    };

    // Single point
    global.drawGoogleMapSinglePoint = function (mapType, divName, lat, lon, nameArray, codeArray, imageArray) {
        var name = Array.isArray(nameArray)? nameArray[0] : nameArray;
        var code = Array.isArray(codeArray)? codeArray[0] : codeArray;
        var img  = Array.isArray(imageArray)? imageArray[0] : imageArray;
        var latNum = parseFloat(lat), lonNum = parseFloat(lon);
        if (isNaN(latNum)||isNaN(lonNum)) return;

        if (need("drawMapSinglePoint")) global.drawMapSinglePoint(divName, latNum, lonNum, name||"", code||"", img||"");
        else if (need("drawMap"))       global.drawMap(divName, [latNum], [lonNum]);
    };
})(window);
