// google.load('earth', '1');
var map = null;
// var googleEarth;
var POINT_CUTOFF = 10000;
var calls = new Array();
var the_infowindow = null;

    $(document).ready(function () { initialize();  });

    function initialize() {
        runThese();
        var mapOptions;
        // googleEarth = new GoogleEarth(map);
        the_infowindow = new google.maps.InfoWindow({
            content: "Loading..."
        });
    }

    function runThese() {
        for (var loop = 0; loop < calls.length; loop++) {
            eval(calls[loop]);
        }
    }

    function getNewMap(mapType, divName) {
        if (mapType == "big") {
            mapOptions = {
                zoom: 5,
                center: new google.maps.LatLng((0,0) ,0),
                mapTypeControl: true,
                largeMapControl: true,
                overviewMapControl: true,
                overviewMapControlOptions:{opened:true},
                scaleControl: true,
                streetViewControl: false,
                mapTypeId: google.maps.MapTypeId.TERRAIN
            };
        } else {
            mapOptions = {
                zoom: 5,
                center: new google.maps.LatLng((0,0) ,0),
                navigationControl: true,
                navigationControlOptions: {style: google.maps.NavigationControlStyle.SMALL},
                mapTypeControl: false,
                streetViewControl: false,
                mapTypeId: google.maps.MapTypeId.TERRAIN
            };
        }
        map = new google.maps.Map(document.getElementById(divName), mapOptions);
    }

    function forceIntoArray(theItem) {
        if (!(theItem instanceof Array)) {
            var temp = theItem;
            theItem = new Array();
            theItem.push(temp);
        }
      return theItem;
    }

    function drawGoogleMapSpecimens(mapType, divName, latArray, lonArray, nameArray, codeArray, imageArray, domainApp) {
      /* Called from:
           /mapComparison.do?subfamily=dolichoderinae&rank=subfamily&project=allantwebants&pr=b
           /description.do?genus=azteca&species=alfari&rank=species&project=allantwebants
           /bigMap.do?taxonName=dolichoderinaeazteca%20alfari&project=allantwebants
           /fieldGuide.do?countryName=Bermuda&rank=species");
           /specimen.do?name=ufv-labecol-000383
      */
      //alert("drawGoogleMapSpecimens)");
        getNewMap(mapType, divName);
        latArray = forceIntoArray(latArray);
        lonArray = forceIntoArray(lonArray);
        if ((map != null) && (latArray.length < POINT_CUTOFF)) {
            var bounds = new google.maps.LatLngBounds();
            var point;
            var marker;
            var infoWindow = null;
            var nameParts;
            var nameUrl;           
            for (var i = 0; i < latArray.length; i++) {
                point = new google.maps.LatLng(latArray[i], lonArray[i]);
                bounds.extend(point);
                var thisName = nameArray[i];
                var thisCode = codeArray[i];
                var thisImage = imageArray[i];
                var url = domainApp + "specimen.do?name=" + thisCode;
                nameParts = thisName.split(" ");
                nameUrl = domainApp + "description.do?rank=species&name=" + nameParts[1] + "&genus=" + nameParts[0];
                if (mapType == "big") {
                    infoWindow = infoWindowLayout(thisImage, nameUrl, thisName, url, thisCode);
                    // var kmlURL = $("#kmlURL").val();
                    // var ctaLayer = new google.maps.KmlLayer(
                    //     kmlURL, {
                    //     preserveViewport : true
                    // });
                    // ctaLayer.setMap(map);
                }
                marker = createMarker(map, point, url, infoWindow, mapType);
            }
            map.setCenter(bounds.getCenter(), "TERRAIN");
            
            //alert("drawGoogleMapSpecimens() bounds:" + bounds + " zoom:" + map.getZoom());
            // This one seems to work well as is.
            if (map.getZoom() > 5) {
                map.setZoom(5);
            }
            if (i > 1) map.fitBounds(bounds);
       	
        }
    }

    function drawGoogleMapLocalities(mapType, divName, latArray, lonArray, nameArray, codeArray, domainApp) { //imageArray, 
      /* Called from:
           Map constructed here: MapResultsAction.java. Map.getGoogleMapFunction writes this method call.
           This will be displayed for locality count links on group.do pages.
      */
        //alert("drawGoogleMapLocalities() map:" + map);        
        getNewMap(mapType, divName);
        latArray = forceIntoArray(latArray);
        lonArray = forceIntoArray(lonArray);
        if ((map != null) && (latArray.length < POINT_CUTOFF)) {

        //alert("drawGoogleMapLocalities() map:" + map + " zoom:" + map.getZoom());        

            var bounds = new google.maps.LatLngBounds();
            var point;
            var marker;
            var infoWindow = null;
            for (var i = 0; i < latArray.length; i++) {
                point = new google.maps.LatLng(latArray[i], lonArray[i]);
                bounds.extend(point);
                var name = nameArray[i];
                var code = codeArray[i];
                var lat = latArray[i];
                var lon = lonArray[i];                
                var url = getLocalityUrl(name, code, domainApp)
                if (mapType == "big") {
                    infoWindow = infoWindowLocalityLayout(url, name, code, lat, lon);
                }
                marker = createMarker(map, point, url, infoWindow, mapType);
            }
            map.setCenter(bounds.getCenter(), "TERRAIN");
            
            //alert("drawGoogleMapLocalities() bounds:" + bounds + " zoom:" + map.getZoom());
            // This one seems to work well as is.
            if (map.getZoom() >= 5) {
                map.setZoom(5);
            }

/*
Utah localities look great: 
  http://localhost/antweb/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=Formicidae&groupName=University%20of%20Utah&output=mapLocality
  drawGoogleMapLocalities() bounds:((-13, -110.40116999999998), (35.2, 142.29663000000005)) zoom:5
Field Museum do not:
  http://localhost/antweb/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=Formicidae&groupName=Field%20Museum&output=mapLocality
  drawGoogleMapLocalities() bounds:((-38.416096, 98.81666599999994), (60.416023, 80.63373000000001)) zoom:5

     Don't seem able to force...
     
     Seems here that zoom level is ignored if there are bounds. Have not found a good
     bounds setting so that the map is displayed at global level. Still getting 
     multiple globes on a map. Boo.
            // bounds:((-38.416096, 98.81666599999994), (60.416023, 80.63373000000001)

			var bigBounds = new google.maps.LatLngBounds();
            pointA = new google.maps.LatLng(-13, -110.40116999999998);
            pointB = new google.maps.LatLng(35.2, 142.29663000000005);
            bigBounds.extend(pointA);
            bigBounds.extend(pointB);
            map.fitBounds(bigBounds);
*/
            if (i > 1) map.fitBounds(bounds);
       	
        } else {
          alert("drawGoogleMapLocalities() else");
          
        }
        //console.log("drawGoogleMapLocalities() i:" + latArray.length + " cutoff:" + POINT_CUTOFF);        
    }
    
    function drawGoogleMapLocality(mapType, divName, latArray, lonArray, nameArray, codeArray, domainApp) {
      /* Called from:
         /locality.do?name=JTL022303
         /bigMap.do?locality=JTL022303&project=allantwebants");    
      */
        //alert("drawGoogleMapLocality()");
        //console.log("drawGoogleMapLocality()");
        getNewMap(mapType, divName);
        latArray = forceIntoArray(latArray);
        lonArray = forceIntoArray(lonArray);
        if ((map != null) && (latArray.length < POINT_CUTOFF)) {
            var bounds = new google.maps.LatLngBounds();
            var point;
            var marker;
            var infoWindow = null;
            var nameUrl;
            var thisImage;
            for (var i = 0; i < latArray.length; i++) {
                point = new google.maps.LatLng(latArray[i], lonArray[i]);
                bounds.extend(point);
                var name = nameArray[i];
                var code = codeArray[i];
                var lat = latArray[i];
                var lon = lonArray[i];
                var url = getLocalityUrl(name, code, domainApp)
                if (mapType == "big") {
                    infoWindow = infoWindowLocalityLayout(url, name, code, lat, lon);
                    //alert("drawGoogleMapLocality() infoWindow:" + infoWindow);
                }
                marker = createMarker(map,point,url,infoWindow,mapType);
            }

            //alert("drawGoogleMapLocality() bounds:" + bounds + " zoom:" + map.getZoom());
            map.setZoom(7);
/*
            if (map.getZoom() > 5) {
                map.setZoom(5);
            }
            map.fitBounds(bounds);  // If the bounds are just a point it will be a very small box.
*/
            map.setCenter(bounds.getCenter(), "TERRAIN");
        }
    }

    function drawGoogleMapCollection(mapType, divName, latArray, lonArray, codeArray, domainApp) {
      /* Called from:
          /collection.do?name=Wm-B-05-2-01"); 
       */
       //alert("drawGoogleMapCollection()");
        getNewMap(mapType, divName);
        latArray = forceIntoArray(latArray);
        lonArray = forceIntoArray(lonArray);
        if ((map != null) && (latArray.length < POINT_CUTOFF)) {
            var bounds = new google.maps.LatLngBounds();
            var point;
            var marker;
            var infoWindow = null;
            var nameParts;
            var nameUrl;
            var thisImage;
            for (var i = 0; i < latArray.length; i++) {
                point = new google.maps.LatLng(latArray[i], lonArray[i]);
                bounds.extend(point);
                var thisCode = codeArray[i];
                var thisLat = latArray[i];
                var thisLon = lonArray[i];
                var url = domainApp + "collection.do?name=" + thisCode;
                if (mapType == "big") {
                    infoWindow = infoWindowCollectionLayout(url, thisCode, thisLat, thisLon);
                }
                marker = createMarker(map,point,url,infoWindow,mapType);
            }
            
            map.setZoom(7);
            /*
            if (map.getZoom() > 5) {
                map.setZoom(5);
            }
            map.fitBounds(bounds);
            */

            map.setCenter(bounds.getCenter(), "TERRAIN");
       }
    }

    function drawGoogleMap(mapType, divName, latArray, lonArray) {
        getNewMap(mapType, divName);
        if ((map != null) && (latArray.length < POINT_CUTOFF)) {
        var bounds = new google.maps.LatLngBounds();
        var point;
        var marker;
            for (var i = 0; i < latArray.length; i++) {
                var properties = {
                    point: new google.maps.LatLng(latArray[i], lonArray[i]),
                    map:map,
                    icon:"/maps/new_icon4.png"
                };
                bounds.extend(point);
                newMarker = new google.maps.Marker(properties);
                markers.push(newMarker);
            }
            if (map.getZoom() > 5) {
                map.setZoom(5);
            }
            map.fitBounds(bounds);
            map.setCenter(bounds.getCenter(), "TERRAIN");
        }
    }

    function drawGoogleMapSinglePoint(mapType, divName, lat, lon, nameArray, codeArray, imageArray) {
        getNewMap(mapType, divName);
        if (map != null) {
            var bounds = new google.maps.LatLngBounds();
            var point = new google.maps.LatLng(lat, lon);
            bounds.extend(point);
            map.addOverlay(new google.maps.Marker(point));
            if (map.getZoom() > 5) {
                map.setZoom(5);
            }
            map.fitBounds(bounds);
            map.setCenter(bounds.getCenter(), "TERRAIN");
        }
    }

    function createMarker(map,point,url,infoWindow,mapType) {
        var imageUrl = '/maps/new_icon4.png';
        var markerImage = new google.maps.MarkerImage(imageUrl, new google.maps.Size(10, 10));
        var marker = new google.maps.Marker({
            position: point,
            map: map, 
            icon: markerImage
        });
        if (mapType == "big") {
            var contentString = infoWindow;
            if (infoWindow != null) {
                google.maps.event.addListener(marker, "click", function () {
                    the_infowindow.setContent(infoWindow);
                    the_infowindow.setPosition(point);
                    the_infowindow.open(map, this);
                });
            }
        }
    }

    function infoWindowLayout(image,nameUrl,name,url,code) {
       infoWindow = "<div class='infoWindow'>";
        if (image.length > 0) {
            infoWindow += "<img src='" + image + "'><br>";
        } else {
            infoWindow += "";
        }
        infoWindow += "Species: <a href='" + nameUrl + "' target='new'>" + name + "</a><br>";
        infoWindow += "Specimen: <a href='" + url  + "' target='new'>" +  code + "</a>";
       infoWindow += "</div>";
        return infoWindow;
    }

   function getLocalityUrl(name, code, domainApp) {
       code = code.replace(/\+/g, '%20'); // 'Friday%20September%2013th'
       code = decodeURIComponent(code);
       name = name.replace(/\+/g, '%20'); // 'Friday%20September%2013th'
       name = decodeURIComponent(name);       
       var url = domainApp;
       if (code !== null && code !== "null") {
         url += "locality.do?code=" + code;
       } else if (name !== null) {
         url += "locality.do?name=" + name;
       } else {
         return null;
       }
       return url;
   }

   function infoWindowLocalityLayout(url, name, code, lat, lon) {
       code = code.replace(/\+/g, '%20'); // 'Friday%20September%2013th'
       code = decodeURIComponent(code);
       name = name.replace(/\+/g, '%20'); // 'Friday%20September%2013th'
       name = decodeURIComponent(name);
       if (name === null || name === "null") name = code;   
       
       infoWindow = "<div class='infoWindow'>";
       infoWindow += "Locality: <a href='" + url + "' target='new'>" + name + "</a><br>";
       infoWindow += "Latitude: " +  lat + "<br>";
       infoWindow += "Longitude: " + lon + "<br>";
       infoWindow += "</div>";
       return infoWindow;
   }

   function infoWindowCollectionLayout(url,code,lat,lon) {
       infoWindow = "<div class='infoWindow'>";
       infoWindow += "Collection: <a href='" + url + "' target='new'>" + code + "</a><br>";
       infoWindow += "Latitude: " +  lat + "<br>";
       infoWindow += "Longitude: " +  lon + "<br>";
       infoWindow += "</div>";
       return infoWindow;
   }

