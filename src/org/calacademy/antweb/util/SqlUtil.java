package org.calacademy.antweb.util;

import org.calacademy.antweb.*;

import java.util.*;


public class SqlUtil {

    public static String getSetStr(ArrayList<String> set) {
		String setStr = "(";
		int i = 0;
        for (String str : set) {
          ++i;
		  if (i > 1) setStr += ", ";
		  setStr += "'" + AntFormatter.escapeQuotes(str) + "'";
        }
        if (i == 0) return null;
		setStr += ")";  
        return setStr;
    }		
}