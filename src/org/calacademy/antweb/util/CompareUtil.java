package org.calacademy.antweb.util;

import java.util.Date;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.Utility;

public class CompareUtil {

    private static final Log s_log = LogFactory.getLog(CompareUtil.class);

    // Every method returns 0 for equal values (and for two empty or null values) and is symmetric: they returned -1
    // for equal dates, floats, zeros and empty strings, which breaks the Comparator contract (TimSort can throw
    // "Comparison method violates its general contract", and smaller lists sorted inconsistently). The order is
    // unchanged otherwise: empty and null first, zeros first.

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public static int compareString(String s1, String s2) {
        if (isEmpty(s1) && isEmpty(s2)) return 0;
        if (isEmpty(s1)) return -1;
        if (isEmpty(s2)) return 1;
        return s1.compareToIgnoreCase(s2);   
    }

    public static int compareStringDesc(String s1, String s2) {
        if (isEmpty(s1) && isEmpty(s2)) return 0;
        if (isEmpty(s1)) return -1;
        if (isEmpty(s2)) return 1;
        return s2.compareToIgnoreCase(s1);   
    }

    public static int compareInt(int i1, int i2) {
        return Integer.compare(i1, i2);
    }    

    public static int compareIntNoZero(int i1, int i2) {
        if (i1 == i2) return 0;
        if (i1 == 0) return -1;
        if (i2 == 0) return 1;
        return i1 > i2 ? 1 : -1;
    }    
    
    public static int compareIntString(String s1, String s2) {
        if (isEmpty(s1) && isEmpty(s2)) return 0;
        if (isEmpty(s1)) return -1;
        if (isEmpty(s2)) return 1;
        Integer i1 = parseInt(s1);
        Integer i2 = parseInt(s2);
        if (i1 != null && i2 != null) return Integer.compare(i1, i2);
        if (i1 != null) return -1;  // numbers before text (Integer.parseInt threw on text)
        if (i2 != null) return 1;
        return s1.compareToIgnoreCase(s2);
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int compareDate(Date d1, Date d2) {
        if (d1 == null && d2 == null) return 0;
        if (d1 == null) return -1;
        if (d2 == null) return 1;
        return d1.compareTo(d2);
    }

    public static int compareFloat(float f1, float f2) {
        int returnInt = Utility.compareFloats(f1, f2);  // equal within 0.001
        if (returnInt == 0) return 0;
	   	if (f1 == 0) return -1;
	   	if (f2 == 0) return 1;
        return returnInt;
    }
}