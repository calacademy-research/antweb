package org.calacademy.antweb;

import java.io.Serializable;
import java.util.StringTokenizer;
import java.util.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import org.apache.regexp.*;
import java.text.*;
import java.math.*;

import org.apache.commons.text.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
  
import java.util.regex.Matcher;
import java.util.regex.Pattern;

    
/** Various formatting methods **/
public class Formatter implements Serializable {

    private static Log s_log = LogFactory.getLog(Formatter.class);

	//static final long serialVersionUID = 1;
	
	
	public static String commaFormat(String num) {
	  return commaFormat((new Long(num)).intValue());
	}	
	
	public static String commaFormat(long num) {
	  return commaFormat((new Long(num)).intValue());
	}
	
	public static String commaFormat(int num) {
      return NumberFormat.getNumberInstance(Locale.US).format(num);		
	}
	
	public static String stripAccents(String s) {
		s = Normalizer.normalize(s, Normalizer.Form.NFD);
		s = s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
		return s;
	}    
	
	public String singleQuote(String theString) {
		return "'" + clearNull(theString) + "'";
	}
	
	/** Print an empty string instead of null **/
	public String clearNull(String theString) {
		String returnString = "";
		if (theString != null) {
			returnString = theString;
		}
		//s_log.warn("clearnNull String:" + theString);
		return returnString;
	}

	public String clearNull(Date theDate) {
		String returnString = "";
		if (theDate != null) {
			returnString = theDate.toString();
/*
        Calendar cal = new GregorianCalendar();
        cal.setTime(theDate);
        Object o = cal.get(Calendar.HOUR);
        s_log.warn("clearnNull() o:" + o);
*/
/*
    cal.get(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    Date oneDate = cal.getTime();
    cal.add(Calendar.DATE, 1);
    Date twoDate = cal.getTime();
*/
		}
		return returnString;
	}
	
	/** append a delimiter to a string if that string is not null or empty **/
	public String appendToNonNull(String theString, String del) {
		
		if ((theString == null) || (del == null)) {
			return "";
		}

		StringBuffer sb = new StringBuffer(theString);
		if ((theString != null) && (theString.length() > 0)) {
			sb.append(del);
		}
		return sb.toString();
	}

	/** Escape single and double quotes
	 * @param theString String with quotes to escpape
	 * @returns the string with escaped quotes
	 */
/*	 
	public String escapeQuotes(String theString) {
		theString = replace(theString, "'", "\\\'");
		theString = replace(theString, "\"", "\\\"");
		return theString;
	}
*/
	/** Remove escaping back slashes
	 * Right now this will get rid of ALL backslashes - even escaped ones (i.e. //) .
	 * @param theString String with quotes to escpape
	 * @returns the string with escaping backslashes removed
	 */
	public String unescapeCharacters(String theString) {
		StringBuffer oldSb = new StringBuffer(theString);
		StringBuffer newSb = new StringBuffer();
		for (int i = 0; i < oldSb.length(); i++) {
			if (oldSb.charAt(i) != '\\') {
				newSb.append(oldSb.charAt(i));
			}
		}
		return newSb.toString();
	}

	public static String escapeSingleQuotes(String theString) {
		if (theString == null) return null;
		
		theString = replace(theString, "'", "''");
		//theString = replace(theString, "\"", "\"\"");
		return theString;
	}
	
	public static String escapeQuotes(String theString) {
		if (theString == null) return null;
		Formatter formatter = new Formatter();
		theString = formatter.replace(theString, "'", "''");
		theString = formatter.replace(theString, "\"", "\"\"");
		return theString;
	}

	/** Microsoft version of unescaping 
	 * Right now this will get rid of ALL backslashes - even escaped ones (i.e. //) .
	 * @param theString String with quotes to escpape
	 * @returns the string with escaping backslashes removed
	 */
	public String MSSQLunescapeCharacters(String theString) {

		if (theString == null) return null;

		StringBuffer oldSb = new StringBuffer(theString);
		StringBuffer newSb = new StringBuffer();
		for (int i = 0; i < oldSb.length(); i++) {
			if ((oldSb.charAt(i) != '\'') && (oldSb.charAt(i) != '"')) {
				newSb.append(oldSb.charAt(i));
			} else if (
				(i == oldSb.length() - 1) || ((oldSb.charAt(i) == '\'') && (oldSb.charAt(i + 1) != '\''))
					|| ((oldSb.charAt(i) == '"') 
						&& (oldSb.charAt(i + 1) != '"'))) {
				newSb.append(oldSb.charAt(i));
			}
		}
		return newSb.toString();
	}

	/** Removes all quotes from the beginning and end of a string
	 *  and reduces multiple quotes to single quotes inside the string
	 */

	public String dequote(String oldString) {

		if (oldString == null) {
			return null;
		}
		String newString = oldString;
		
		try {
			RE startQuote = new RE("^\"+");
			RE endQuote = new RE("\"+$");
			RE multiQuote = new RE("\"\"");
			RE multiApos = new RE("\'\'");
			RE entityMultiQuote = new RE("&quot;&quot;");
			RE entityMultiApos = new RE("&apos;&apos;");

			newString = startQuote.subst(oldString, "");
			newString = endQuote.subst(newString, "");
			newString = multiQuote.subst(newString, "\"");
			newString = multiApos.subst(newString, "\'");
			newString = entityMultiQuote.subst(newString, "&quot;");
			newString = entityMultiApos.subst(newString, "&apos;");
		} catch (RESyntaxException e) {
			s_log.error("Found error in dequote: " + e);
		}
		return newString;
	}

	public String replaceBadXML(String theText) {

		theText = replace(theText, "&", "&amp;");
		theText = replace(theText, "<", "&lt;");
		theText = replace(theText, ">", "&gt;");
		theText = replace(theText, "\'", "&apos;");
		theText = replace(theText, "\"", "&quot;");
		theText = replace(theText, "/", "");

		return theText;
	}


    public String removeSpaces(String str) {
      return replace(str, " ", "");
    }

    public static String stripString(String baseString, String stripString) {
        // Strip from front and back?
        if (baseString.contains(stripString)) {
            int index = baseString.indexOf(stripString);
            String stripBaseString = baseString.substring(0, index);
            int tailIndex = index + stripString.length();
            //s_log.warn("stripString() baseString:" + baseString + " tail:" + tailIndex + " length:");           
            if (baseString.length() > tailIndex) {
              // then there are params after the stripString
              stripBaseString += baseString.substring(tailIndex); 
            }
            //s_log.warn("stripString() stripBaseString:" + stripBaseString + " tail:" + tailIndex + " length:" + baseString.length());           
            baseString = stripBaseString;
        }   
        return baseString;         
    }

    public static String replace(String main, String oldStr, String newStr) {
      return (new Formatter()).stringReplace(main, oldStr, newStr);
    }

	/** Replace part of a string with new stuff
	 * @param main String to have stuff replaced
	 * @param oldStr String substring to replace
	 * @param newStr String substring to insert
	 * @returns the string with the substitutions
	 */
	private String stringReplace(String main, String oldStr, String newStr) {
		
		if ((main == null) || (oldStr == null)) return main;
		
		if (main.equals(""))
			return "";
		StringBuffer result = new StringBuffer();
		int i = main.indexOf(oldStr);
		int lastpos = 0;

		while (i != -1) {
			result.append(main.substring(lastpos, i));
			result.append(newStr);
			lastpos = i + oldStr.length();
			i = main.indexOf(oldStr, lastpos);
		}
		result.append(main.substring(lastpos));
		return result.toString();
	}
	
	public static String replaceOne(String main, String oldStr, String newStr) {
		
		if ((main == null) || (oldStr == null)) return main;
		
		if (main.equals(""))
			return "";
		StringBuffer result = new StringBuffer();
		int i = main.indexOf(oldStr);
		int lastpos = 0;

		if (i != -1) {
			result.append(main.substring(lastpos, i));
			result.append(newStr);
			lastpos = i + oldStr.length();
		}
		result.append(main.substring(lastpos));
		return result.toString();
	}
		
	public static String formDisplay(String text) {
	  if (text == null) return "";
	  return text;
	}

    public static boolean isAlpha(String s) {
		char[] charArr = s.toCharArray();

		for(char c : charArr) {
			if(!Character.isLetter(c)) {
				return false;
			}
		}
		return true;
    }

    public static boolean hasAlpha(String s) {
		char[] charArr = s.toCharArray();

		for(char c : charArr) {
			if(!Character.isLetter(c)) {
				return true;
			}
		}
		return false;
    }
    
	public static boolean isAlphanumeric(String str)
	{
		char[] charArray = str.toCharArray();
		for(char c:charArray)
		{
			if (!Character.isLetterOrDigit(c))
				return false;
		}
		return true;
	}

	public static boolean containsUppercase(String str) {
		char ch;
		boolean capitalFlag = false;
		boolean lowerCaseFlag = false;
		boolean numberFlag = false;
		for(int i=0 ; i < str.length() ; i++) {
       	  ch = str.charAt(i);
          if (Character.isUpperCase(ch)) {
			return true;
		  }
		}
		return false;
	}	

	public static String formFieldEncode(String theWord) {
		theWord.replaceAll(".", "%46");
		return theWord;
		
	}

	public static String initCap(String thePhrase) {
	  return WordUtils.capitalize(thePhrase); 
    }
    
	public String capitalizeEachWord(String thePhrase) {
	  return Formatter.initCap(thePhrase);	
	}	
	public String capitalizeFirstLetter(String theWord) {
		if (theWord == null)
			return null;
		if (theWord.length() < 1)
			return "";

		String first = theWord.substring(0, 1).toUpperCase();
		String rest = theWord.substring(1);
		StringBuffer newString = new StringBuffer().append(first).append(rest);
        //A.log("capitalizeFirstLetter() theWord:" + theWord + " first:" + first + " rest:" + rest);
		return newString.toString();
	}


	public String capitalizeSubgenus(String theWord) {
		String newWord = theWord;
		int theParen = theWord.indexOf('(');
		if (theParen != -1) {
			char oldChar = theWord.charAt(theParen + 1);
			char newChar = Character.toUpperCase(oldChar);
			String first = theWord.substring(0, theParen + 1);
			String rest = theWord.substring(theParen + 2);
			newWord = first + newChar + rest;
		}

		return newWord;
	}

	/** Replace newlines with HTML linebreaks.  Also trims leading and trailing whitespace.
	 *  Also turns http://... into a link.
	 *
	 *  @param textString String to convert to HTML
	 *
	 *  @return the string in HTML.
	 */
	public String toHtml(String textString) {
		if (textString != null) {
			// trim leading and trailing whitespace
			String workingCopy = textString.trim();

			// replace newlines with <br>'s
			workingCopy = replace(workingCopy, "\n", "<br>");

			StringBuffer out = new StringBuffer();
			StringTokenizer toke = new StringTokenizer(workingCopy);

			// turn urls into links.
			while (toke.hasMoreTokens()) {
				out.append(urlToHtml(toke.nextToken()) + " ");
			}

			return out.toString();

		} else {
			return "";
		}

	}

	/**
	 * This turns a raw url into an html link.  If the urlStr is not recognized as an url (does not begin with
	 * "http://") it is left alone.  If there is a space and a string after the url, it is used as the text of the
	 * link.
	 *
	 * @param urlStr string containing and url
	 *
	 * @return htmlized url
	 */
	public String urlToHtml(String urlStr) {
		String url = urlStr.trim();
		String link = url;

		if (url.indexOf(' ') > 0) {
			link = url.substring(url.indexOf(' ') + 1);
			url = url.substring(0, url.indexOf(' '));
		}

		if (url.toLowerCase().startsWith("www.")) {
			url = "http://" + url;
		}

		if (url.toLowerCase().startsWith("http://")) {
			return "<a href=\"" + url + "\" target=\"other\">" + link + "</a>";
		} else {
			return urlStr;
		}
	}
	/* convert from UTF-8 encoded HTML-Pages -> internal Java String Format */
	public static String convertFromUTF8(String s) {
		String out = null;
		if ((s == null) || (s.length() <= 0)) {
			return null;
		}

		try {
			out = new String(s.getBytes("ISO-8859-15"), "UTF-8");
		} catch (java.io.UnsupportedEncodingException e) {
		    s_log.error("convertFromUTF8() s:" + s + " exception:" + e);  // Mark added Jan 3, 2011 
			return null;
		}
		return out;
	}

	/* convert from internal Java String Format -> UTF-8 encoded HTML/JSP-Pages  */
	public static String convertToUTF8(String s) {
		String out = null;
		try {
			out = new String(s.getBytes("UTF-8"));
		} catch (java.io.UnsupportedEncodingException e) {
		    s_log.error("convertToUTF8() s:" + s + " exception:" + e);  // Mark added Jan 3, 2011 
			return null;
		}
		//if (AntwebProps.isDevMode()) s_log.info("convertToUtf8() original:" + s + " converted:" + out);
		return out;
	}

	public static String replaceAttribute(String attribute, String s, String replacement) {
		String newString = s;
		try {
			RE style = new RE(attribute + "='.*?'",RE.REPLACE_ALL);
			newString = style.subst(s, replacement);
		} catch (RESyntaxException e) {
			s_log.error("Found error in dequote: " + e);
		}
		return newString;
	}
	
	public static String removeTag(String s, String tag) {
		String newString = s;
		try {
			RE startTag = new RE("<" + tag + ".*?>",RE.REPLACE_ALL);
			RE endTag = new RE("</" + tag + ">",RE.REPLACE_ALL);
			newString = startTag.subst(s, "");
			newString = endTag.subst(newString, "");
		} catch (RESyntaxException e) {
			s_log.error("Found error in dequote: " + e);
		}
		return newString;
	}

   
        
  public static String formatMB(long num) {
    long longNum = num / 1024 / 1024;
    String stringNum = (new Long(longNum)).toString();
    return Formatter.commaFormat(longNum) + "MB";
  }
        
  public static String ignoreUtf8 = "()/_.,&-]";  
  public static boolean hasSpecialCharacter(String str) {
    String extras = "äáëéìöü";
    String patternStr = "[^A-Za-z0-9 " + extras + ignoreUtf8;
    return hasSpecialCharacter(str, patternStr);
  }

	public static boolean hasLoginSpecialCharacter(String str) {
		String extras = "?&=%:,;+äáëéìöü@*";
		String patternStr = "[^A-Za-z0-9 " + extras + ignoreUtf8;
		return hasSpecialCharacter(str, patternStr);
	}

	public static boolean hasWebSpecialCharacter(String str) {
    String extras = "?&=%:,;+äáëéìöü";
    String patternStr = "[^A-Za-z0-9 " + extras + ignoreUtf8;
    return hasSpecialCharacter(str, patternStr);
  }

  // Used for things like locality. Allows single quote.
  public static boolean hasTextSpecialCharacter(String str) {
    String extras = "?&=%:,';+äáëéìöü\\[\\]";
    String patternStr = "[^A-Za-z0-9 " + extras + ignoreUtf8;
    return hasSpecialCharacter(str, patternStr);
  }

  public static boolean hasSpecialCharacter(String str, String patternStr) {
    if (str == null || str.trim().isEmpty()) {
        A.log("Incorrect format of string");
        return false;
    }
    
    Pattern p = Pattern.compile(patternStr);

    Matcher m = p.matcher(str);
    if (m.find()) {
       return true;
    }
    return false;
  }

// doesn't seem to need 0-9 in order to allow 0 in text.
// - does not seem to be recognized in this strin.
/*(    
    Pattern p = Pattern.compile("[^A-Za-z0-9 ()/_.-]");
    
    //Pattern p = Pattern.compile("[$&+,:;=\\\\?@#|/'<>.^*()%!-]");
    
    Matcher m = p.matcher(str);
    out.println("Str:" + str + " find:" + m.find() + " match:" + m.matches());

  } else {
      String str = "amblyoponinaemystrium mysticum-a";


      //String str = "ab -c()$de_fghij/kl";

      Pattern p = Pattern.compile("[^()/_.a-z0-9 -]", Pattern.CASE_INSENSITIVE);
      Matcher m = p.matcher(str);

      System.out.println(str);
      int count = 0;
      while (m.find()) {
         count = count+1;
         out.println("<br><br>position "  + m.start() + ": " + str.charAt(m.start()));
      }
      out.println("<br><br>There are " + count + " special characters");
    */


}
