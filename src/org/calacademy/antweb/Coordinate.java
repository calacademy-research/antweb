/*
 * Created on May 12, 2005
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package org.calacademy.antweb;

/**
 * @author thau
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class Coordinate {
	float lat;
	float lon;

	/**
	 * 
	 */
	public Coordinate() {
		super();
	}
	
	/**
	 * @param lat
	 * @param lon
	 */
	public Coordinate(float lon, float lat) {
		super();
		this.lat = lat;
		this.lon = lon;
	}
	/**
	 * @return Returns the lat.
	 */
	public float getLat() {
		return lat;
	}
	/**
	 * @param lat The lat to set.
	 */
	public void setLat(float lat) {
		this.lat = lat;
	}
	/**
	 * @return Returns the lon.
	 */
	public float getLon() {
		return lon;
	}
	/**
	 * @param lon The lon to set.
	 */
	public void setLon(float lon) {
		this.lon = lon;
	}
	
	public String toString() {
		return getLat() + "," + getLon();
	}
}
