package org.calacademy.antweb.geolocale;

import java.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.*;

public class LatLon {

    private static final Log s_log = LogFactory.getLog(LatLon.class);

    private double latitude = 0;
    private double longitude = 0;

    public LatLon(String lat, String lon) {
        if (lat == null || lon == null || "".equals(lat) || "".equals(lon)) return;

        this.latitude = Double.parseDouble(lat);
        this.longitude = Double.parseDouble(lon);
    }

    public LatLon(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public boolean isValid() {
      if (latitude < -90 || latitude > 90 && longitude < -180 || longitude > 180) return false;

      if (latitude == 0 && longitude == 0) return false; // Apparently unset.

      return true;
    }

    public String getLat() {
        return latitude + "";
    }
    public String getLon() {
        return longitude + "";
    }
    public double getLatitude() {
        return latitude;
    }
    public double getLongitude() {
        return longitude;
    }

    public String toString() {
        return "lat:" + getLatitude() + " lon:" + getLongitude();
    }
}

