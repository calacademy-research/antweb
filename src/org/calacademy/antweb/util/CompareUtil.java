package org.calacademy.antweb.util;

import java.util.Date;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.Utility;

public class CompareUtil {

    private static final Log s_log = LogFactory.getLog(CompareUtil.class);

    public static int compareString(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null || "".equals(s1)) return -1;
        if (s2 == null || "".equals(s2)) return 1;
        return s1.compareToIgnoreCase(s2);   
    }

    public static int compareStringDesc(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null || "".equals(s1)) return -1;
        if (s2 == null || "".equals(s2)) return 1;
        return s2.compareToIgnoreCase(s1);   
    }

    public static int compareInt(int i1, int i2) {
        if (i1 == i2) return 0;
        return (i1 > i2) ? 1 : -1;
    }    

    public static int compareIntNoZero(int i1, int i2) {
        if (i1 == 0) return -1;
        if (i2 == 0) return 1;
        if (i1 == i2) return 0;
        return (i1 > i2) ? 1 : -1;
    }    
    
    public static int compareIntString(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;

        if (s1 == null || "".equals(s1)) return -1;
	    if (s2 == null  || "".equals(s2)) return 1;
	    int s1Int = (Integer.valueOf(s1)).intValue();
	    int s2Int = (Integer.valueOf(s2)).intValue();
	    if (s1Int == s2Int) return 0;
	    return (s1Int > s2Int) ? 1 : -1;    
    }

    public static int compareDate(Date d1, Date d2) {
        if (d1 == null) return -1;
        if (d2 == null) return 1;
        return (d1.after(d2)) ? 1 : -1;
    }

    public static int compareFloat(float f1, float f2) {
	   	if (f1 == 0) return -1;
	   	if (f2 == 0) return 1;
	    int returnInt = Utility.compareFloats(f1, f2);
        //s_log.warn("sortBy() compare f1:" + f1 + " f2:" + f2 + " returnInt:" + returnInt);
        return returnInt;    
    }
}