package org.calacademy.antweb.util;

import org.calacademy.antweb.*;

import java.util.*;


/**
 * SQL helper: getSetStr turns a list of strings into a quoted, escaped SQL set "('a', 'b')" for an "in" clause
 * (null for an empty list). Used by Taxon (species of a taxon set) and ImagePickDb. No table of its own.
 */
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