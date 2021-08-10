package org.calacademy.antweb.upload;

import java.io.*;
import java.util.*;
import java.sql.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;  
    
public class LineNumMgr {

    private static Log s_log = LogFactory.getLog(LineNumMgr.class);
	private static ArrayList<Integer> badCarriageReturnLines = null;


// --------------------------------------------------------------------------------------

    // Updated in real time as we progress through the specimen upload.
	static int lineNum = 0;
					
	public static void setLineNum(int thisLineNum) {
	  lineNum = thisLineNum;
	}
	public static int getLineNum() {
	  return lineNum;
	}
    public static int getDisplayLineNum() {
      return getDisplayLineNum(getLineNum());
    }

// --------------------------------------------------------------------------------------

    // Parse through the upload file. Recording and reporting which lines are bad, 
    //   and what the effective line number is (as it would appear in Excel.
    public static void init(UploadFile uploadFile, MessageMgr messageMgr, Connection connection) 
      throws IOException {
		
		UploadDb uploadDb = new UploadDb(connection);
		uploadDb.removeUploadLines(UploadHelper.getGroup());
		
  	    badCarriageReturnLines = new ArrayList<>();
		
		int componentCountChanges = 0;
		String[] components = null;
		String theLine = null;
		String theHeader = null;
		String lastLine = null;

        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(uploadFile.getFileLoc()), uploadFile.getEncoding()));            
        theLine = in.readLine();
        int lineNum = 1;
        int componentCount = 0;
        int lastComponentCount = 0;
        int extraCarriageReturnCount = 0;
        int minComponentCount = 0;
		int headerComponentCount = 0;
        while (theLine != null) {
          components = getComponents(theLine);
          componentCount = components.length;

          if (lineNum == 1) {  // this is the header
            theHeader = theLine; 
            headerComponentCount = componentCount;
            minComponentCount = headerComponentCount;
          }

          if (lineNum < 5) {
            if (componentCount < minComponentCount && componentCount > 0) minComponentCount = componentCount;
          }

          int fudgeFactor = 1;
          if (lineNum > 1 &&
               ( componentCount < (lastComponentCount - fudgeFactor)
             ||  componentCount > (lastComponentCount + fudgeFactor)
               )
             && componentCount < minComponentCount  
             ) {
            ++componentCountChanges;

			setIsBadCarriageReturnLine(lineNum);

			// Persist this line in the database for retrieval from log file.
			int displayLineNum = getDisplayLineNum(lineNum);
			uploadDb.addUploadLine(uploadFile.getBackupFileName(), lineNum, displayLineNum, theLine, UploadHelper.getGroup());

			//A.log("extraCarriageReturnCheck() lineNum:" + lineNum + " displayLineNum:" + getModifier(lineNum));
			String lineLink = "<a href='" + AntwebProps.getDomainApp() + "/showLog.do?action=uploadLine&file=" + uploadFile.getBackupFileName() + "&line=" + lineNum + "'>" + displayLineNum + "</a>";
			String message = "On (approx) line:" + lineLink + " there were only " + componentCount;
			messageMgr.addToMessages(MessageMgr.extraCarriageReturn, message);          
		  }          
          
          lastComponentCount = componentCount;
          lastLine = theLine;
          theLine = in.readLine();
          ++lineNum; 
        }

		//A.log("init() componentCountChanges:" + componentCountChanges + " report:" + report());
    }

	public static void setIsBadCarriageReturnLine(int lineNum) {
	  badCarriageReturnLines.add(lineNum);
	}

	public static boolean isGoodCarriageReturnLine(int lineNum) {
	  return !isBadCarriageReturnLine(lineNum);
	}
	public static boolean isBadCarriageReturnLine(int lineNum) {
	  for (Integer l : badCarriageReturnLines) {
		if (l.intValue() == lineNum) return true;
	  }
	  return false;
	}        

    public static int getBadLinesLessThan(int lineNum) {
      int c = 0;
	  for (Integer l : badCarriageReturnLines) {
		if (l.intValue() <= lineNum) ++c;
		//if (l.intValue() > lineNum) break;
	  }
      //A.log("getBadLineLessThan() lineNum:" + lineNum + " c:" + c);
	  return c;
    }
    
    public static int getModifier(int lineNum) {
      int c = getBadLinesLessThan(lineNum);
      int d = 0;
      d = c / 2;
      return d;
    }

	public static int getDisplayLineNum(int lineNum) {
	  int displayLineNum = lineNum - getModifier(lineNum);
	  //A.log("getDisplayLineNum() lineNum:" + lineNum + " display:" + displayLineNum + " bads:" + getBadLinesLessThan(lineNum) + " modifier:" + getModifier(lineNum));
	  return displayLineNum;
	}
	
	public static String report() {
	  String report = "";
	  int i = 0;
	  for (Integer bad : badCarriageReturnLines) {
        if (i > 0) report += ", ";
	    report += bad;
        ++i;
	  }
	  return report;
	}
    

    private static String[] getComponents(String theLine) {
       //if (true) return getComponentsRE(theLine);

        return theLine.split("\t");

//       ArrayList<String> list = new ArrayList<String>();
//       int i = 0;
//       while (theLine.contains("\t")) {
//         ++i;
//         int tabIndex = theLine.indexOf("\t");
//         String value = theLine.substring(0, tabIndex);
//         list.add(value);
//         //A.log("getComponents() i:" + i + " tabIndex:" + tabIndex + " value:" + value);
//         theLine = theLine.substring(tabIndex + 1);
//       }
//       //A.log("getComponents() i:" + (i+1) + " value:" + theLine);
//       list.add(theLine);
//
//       //A.log("getComponents() size:" + list.size() + " list:" + list);
//        return list.toArray(new String[0]);
    }

}