package org.calacademy.antweb.upload;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;

public class WorldAuthorityGenerator {

    private static Log s_log = LogFactory.getLog(WorldAuthorityGenerator.class);
    
    private String authorityFilesDir = new Utility().getDocRoot() + "/worldAuthorityFiles";
    private String uncertain = "Incertae_sedis";
    private Pattern red = Pattern.compile("color:red",Pattern.CASE_INSENSITIVE);
    private Pattern purple = Pattern.compile("color:purple",Pattern.CASE_INSENSITIVE);
    private Pattern black = Pattern.compile("color:black",Pattern.CASE_INSENSITIVE);
    private Pattern color = Pattern.compile("color:",Pattern.CASE_INSENSITIVE);
    
    private Pattern green = Pattern.compile("color:green",Pattern.CASE_INSENSITIVE);
    private Pattern blue = Pattern.compile("#.*color:blue",Pattern.CASE_INSENSITIVE);
    private Pattern boldItalic = Pattern.compile("<b><i>.*?([A-Z]{2,}).*?</i></b>");
    private Pattern boldItalicSpecies = Pattern.compile("<b><i>.*?([A-Z]{2,}).*?</i></b>",Pattern.CASE_INSENSITIVE);
    private Pattern extinctStar = Pattern.compile(">\\*#?<");
    private Pattern closeP = Pattern.compile("</p>",Pattern.CASE_INSENSITIVE);
    private Pattern startP = Pattern.compile("^<p",Pattern.CASE_INSENSITIVE);
    private Pattern italics = Pattern.compile("<i>(.*?)</i>",Pattern.CASE_INSENSITIVE);
    private Pattern brackets = Pattern.compile("[\\(\\[](.*?)[\\)\\]]");
    private Pattern incertae = Pattern.compile("incertae sedis in (.*)",Pattern.CASE_INSENSITIVE);
    private Pattern leadStar = Pattern.compile("^\\*+");
    private Pattern startItal = Pattern.compile("<i>",Pattern.CASE_INSENSITIVE);
    private Pattern endItal = Pattern.compile("</i>",Pattern.CASE_INSENSITIVE);
    private Pattern tag = Pattern.compile("<.*?>");
    private Pattern htmlElement = Pattern.compile("&.*?;");
    private Pattern foofoo = Pattern.compile("FOOFOO");
    private Pattern barbar = Pattern.compile("BARBAR");
    //private Pattern speciesP = Pattern.compile("<b><i>.*>[a-z]+<.*</i></b>");
    private Pattern notSpeciesP = Pattern.compile("<i>.*?[A-Z]{2,}</i>");
    private Pattern betweenParens = Pattern.compile("\\(.*?\\)");
    private Pattern firstCap = Pattern.compile("[A-Z][a-z]+");
    private Pattern authorIn = Pattern.compile(", in .* (\\d)");
    private Pattern genusSpecies = Pattern.compile("([A-Z][a-z]+)\\s+([a-z]+)");
    private Pattern juniorSynOf = Pattern.compile("junior synonym of <i>(.*?)</i>",Pattern.CASE_INSENSITIVE);
    private Pattern genusP = Pattern.compile("<i>([A-Z]{2,}).*?</i>");
    private Pattern originalCombination = Pattern.compile(".*?\\.\\s*([A-Z].*?) [A-Z]");
    private Pattern invalidSpecies = Pattern.compile("([a-z]+?)\\. ([A-Z][a-z]+)");
    private Pattern datePat = Pattern.compile("(\\d\\d\\d\\d)");
    private Pattern adjacentI = Pattern.compile("<i>(.*?)</i><i>(.*?)</i>");
    private Pattern firstCappedWord = Pattern.compile("([A-Z][a-z]+)");
    //private Pattern quatrenary = Pattern.compile("\\.\\s+(.*?)\\s+.*?\\s(.*?)</i>(.*?)<i>(.*?)</i>(.*?)<i>(.*?)</i>");
    private Pattern quatrenary = Pattern.compile("([A-Z][a-z]+)\\s+([^A-Z]*)[A-Z]");
    private Pattern countryPat = Pattern.compile("([A-Z][A-Z\\s]{2,}).*?\\.");
    private Pattern usaPat = Pattern.compile("U.S.A.");
    private Pattern islandPat = Pattern.compile("(.*?) I[S]*");
    private Pattern seeUnderPat = Pattern.compile("see under");
    private Pattern startsWithCap = Pattern.compile("^\\s*\\*?[A-Z]{2,}");
    private Pattern synSubfamily = Pattern.compile("^Subfamily \\*?([A-Z]+)\\s");
    private Pattern synGenus = Pattern.compile("^Genus \\*?([A-Z]+)\\s");
    private Pattern synTribe = Pattern.compile("^Tribe \\*?([A-Z]+)\\s");
    private Pattern currentlySub = Pattern.compile("Currently subspecies of <i>(.*?)</i>");
    private Pattern alphanum = Pattern.compile("[A-Za-z0-9]");
    

    /** 
     * There are two parts, generating the subfamily/genus part and generating the species part
     * there should be internal checks making sure that each genus in the species list has a subfamily
     * making sure that the same genus doesn't have more than one subfamily, making sure that the 
     * same species doesn't have more than one genus or tribe making sure each tribe has only one genus
     * It returns an array of two values, a list of errors and a tsv file
     */ 
    public ArrayList<String> generateTSV(String type, ArrayList<HashMap<String, String>> allSubfamilies, ArrayList<HashMap<String, String>> theseSubfamilies) {
        ArrayList<String> result = new ArrayList<>();
        ArrayList<String> headers = new ArrayList<>();
        headers.add("subfamily");
        headers.add("tribe");
        headers.add("genus");
        headers.add("species");
        headers.add("species author date");
        headers.add("country");
        headers.add("valid");
        headers.add("available");
        headers.add("current valid name");
        headers.add("original combination");
        headers.add("taxonomic history");
        
        char delim = '\t';
        
        ArrayList<HashMap<String, String>> species = getSpecies(allSubfamilies, type);
        //s_log.info("got species:" + species.size() + " type:" + type + " allSubfamilies:" + allSubfamilies);
        //String subFamilyErrors = getSubfamilyErrors(theseSubfamilies);
        //s_log.info("got subfam errors");
        String speciesErrors = getSpeciesErrors(allSubfamilies, species);
        s_log.info("got species errors");
        //result.add(subFamilyErrors + speciesErrors);
        result.add(speciesErrors);
        s_log.info("about to flatten");
        result.add(arrayJoin(headers,delim) + "\n" + arrayJoin(flatten(theseSubfamilies, headers, delim),'\n') + "\n" + arrayJoin(flatten(species,headers, delim),'\n'));
        
        return result;
    }
    
    public ArrayList<String> generateTSVSynopsisOnly(String type, ArrayList<HashMap<String, String>> allSubfamilies, ArrayList<HashMap<String, String>> theseSubfamilies) {
        ArrayList<String> result = new ArrayList<>();
        ArrayList<String> headers = new ArrayList<>();
        headers.add("subfamily");
        headers.add("tribe");
        headers.add("genus");
        headers.add("species");
        headers.add("species author date");
        headers.add("country");
        headers.add("valid");
        headers.add("available");
        headers.add("current valid name");
        headers.add("original combination");
        headers.add("taxonomic history");
        
        char delim = '\t';
        
        //ArrayList<HashMap<String, String>> species = getSpecies(allSubfamilies, type);
        //s_log.info("got species");
        //String subFamilyErrors = getSubfamilyErrors(theseSubfamilies);
        //s_log.info("got subfam errors");
        //String speciesErrors = getSpeciesErrors(allSubfamilies, species);
        //s_log.info("got species errors");
        //result.add(subFamilyErrors + speciesErrors);
        result.add("");
        s_log.info("about to flatten");
        result.add(arrayJoin(headers,delim) + "\n" + arrayJoin(flatten(theseSubfamilies, headers, delim),'\n'));

        return result;
    }
    
    public ArrayList<HashMap<String, String>> getSubfamilies(String type) {
        ArrayList<HashMap<String, String>> result = new ArrayList<>();
        String fileName = authorityFilesDir + "/subfamily_genus.txt";
        s_log.info("getSubfamilies:" + fileName);

        ArrayList<String> lines = getLines(fileName);        
       s_log.info("  Lines:" + lines.size());    
        
        Iterator<String> lineIter = lines.iterator();    
        String line = "";
        boolean fossil = false;
        boolean valid = false;
        boolean available = false;
        boolean unidentifiable = false;
        boolean isGenus = false;
        
        int validCount = 0;
        int unidentifiableCount = 0;
        
        while (lineIter.hasNext()) {
            line = lineIter.next();
            fossil = isFossil(line);
            valid = isValid(line);
            unidentifiable = isGreen(line);
            available = isAvailable(line);
            isGenus = isGenus(line);

            if (valid) ++validCount;
            if (unidentifiable) ++unidentifiableCount;

            if (!unidentifiable) {
                if (type.equals("extinct") && fossil && isGenus) {
                    result.add(getGenus(line, valid, available));
                } else if (type.equals("extant") && !fossil && isGenus) {
                    
                    //s_log.info("calling get subfamilies with " + valid + " " + available + " " + line);
                    result.add(getGenus(line, valid, available));
                }
            }
        }

        s_log.info("validCount " + validCount + " unidentifiableCount:" + unidentifiableCount);

        //result = addSynposisInfo(result, type);
        return result;
    }
    
    public ArrayList<HashMap<String, String>> addSynposisInfo(ArrayList<HashMap<String, String>> data, String type) {
                
        ArrayList<HashMap<String, String>> result = new ArrayList<>();
        
        // read the synposis file
        HashMap<String, String> synopsisInfo = readSynopsisFile(type);
        
        // add the synopsis info into the data
        HashMap<String, String> thisTaxon;
        String thisName;
        for (HashMap<String, String> datum : data) {
            thisTaxon = datum;
            thisName = thisTaxon.get("subfamily") + ":" + thisTaxon.get("genus");
            //s_log.info("searching for " + thisName + " in the data ");
            if (synopsisInfo.containsKey(thisName)) {
                thisTaxon.put("taxonomic history", synopsisInfo.get(thisName));
                //s_log.info("found it! added  " + synopsisInfo.get(thisName));
            }
            result.add(thisTaxon);
        }
        
        // now also add the subfamily information
        Set<String> synKeys = synopsisInfo.keySet();
        Iterator<String> synIter = synKeys.iterator();
        String thisKey;
        
        while (synIter.hasNext()) {
            thisKey = synIter.next();
            if (!thisKey.contains(":")) {
                thisTaxon = new HashMap<>();
                thisTaxon.put("subfamily", thisKey);
                thisTaxon.put("taxonomic history", synopsisInfo.get(thisKey));
                thisTaxon.put("valid", "TRUE");
                thisTaxon.put("avaliable", "TRUE");
                result.add(thisTaxon);
            }
        }
        return result;
    }
    
    private boolean synStop(String line) {
        boolean result = false;
        Matcher m = red.matcher(line);
        if (line.contains("</body>")) {
            result = true;
        } else if (line.contains("SUBFAMILY")) {
            result = true;
        } else if (line.contains("SUBFAMILIES")) {
            result = true;
        } else if (m.find()) {
            if (line.contains("Genera of")) {
                result = true;
            } else {
                String newLine = removeAllTags(line);
                Matcher m2 = synTribe.matcher(newLine);
                if (m2.find()) {
                    result = true;
                } else if (newLine.contains("Genus incertae sedis in")) {
                    result = true;
                }
            }
        }            
        return result;
    }

        
    private HashMap<String, String> readSynopsisFile(String type) {
        HashMap<String, String> result = new HashMap<>();
        
        // right now this just reads genus information.
        // it'll have to be extended to also read subfamily information
        
        String fileName = authorityFilesDir + "/synopsis.txt";
        ArrayList<String> lines = getLines(fileName);
        Iterator<String> lineIter = lines.iterator();
        String line = "";
        boolean fossil = false;

        HashMap<String, String> thisTaxon;

        String currName = "";
        String rank = "";
        String name = "";
        String[] parts;
        String contents = "";
        Formatter format = new Formatter();
        boolean recording = false;

        while (lineIter.hasNext()) {
            line = lineIter.next();
            fossil = isFossil(line);
            thisTaxon = getSynopsisTaxon(line);
                        
            if ((type.equals("extinct") && fossil && (thisTaxon != null)) ||
            (type.equals("extant") && !fossil && (thisTaxon != null)))    {
                
                if (recording) {
                    recording = false;
                    if (contents.length() > 0) {
                        result.put(currName, contents);
                        s_log.info("adding " + currName + " to syn info");
                    }
                    contents = "";
                }
                
                rank = thisTaxon.get("rank");
                name = format.capitalizeFirstLetter(thisTaxon.get("name").toLowerCase());
                
                if (rank.equals("subfamily")) {
                    currName = name;
                } else if (rank.equals("genus")) {
                    parts = currName.split(":");
                    if (parts[0].length() == 0) {
                        parts[0] = "Incertae_sedis";
                        s_log.info("uncertain");
                    }
                    currName = parts[0] + ":" + name;
                }
                //if (rank.equals("subfamily")) {
                //    s_log.info("turning recording on with subfamily " + name);
                //}
                recording = true;
            } else if (synStop(line)) {
                //s_log.info("turning recording off with rank " + rank + " and name " + currName + " and line " + line);
                recording = false;
                if (contents.length() > 0) {
                    result.put(currName, contents);
                    s_log.info("adding " + currName + " to syn info");
                }
                contents = "";
                
            } else if (recording) {
                contents = contents + line;
            }
        }        
        return result;
    }
    
    private HashMap<String, String> getSynopsisTaxon(String line) {
        HashMap<String, String> result = new HashMap<>();
        String thisLine = removeAllTags(line);
        Formatter format = new Formatter();
        Matcher m = synSubfamily.matcher(thisLine);
        if (m.find()) {
            result.put("rank", "subfamily");
            //s_log.info("it's a subfamily");
            result.put("name", format.capitalizeFirstLetter(m.group(1).toLowerCase()));
        } else {
            m = synGenus.matcher(thisLine);
            if (m.find()) {
                result.put("rank", "genus");
                //s_log.info("it's a genus");
                result.put("name", format.capitalizeFirstLetter(m.group(1).toLowerCase()));
                if (result.get("name").equals("noonilla")) {
                    s_log.info("found noonilla");
                }
            }
        }
        if (result.get("name") == null) {
            result = null;
        }
        return result;
    }
    
    /*
    private boolean isSynopsisTaxon(String line) {
        boolean result = false;
        Matcher m2 = red.matcher(line);
        Matcher m;
        if (m2.find()) {
            line = removeAllTagsButItalics(line);
            m = synSubfamily.matcher(line);
            if (m.find()) {
                s_log.info("is synSubfamily matched on " + m.group(1));
                result = true;
            } else {
                m = synGenus.matcher(line);
                if (m.find()) {
                    s_log.info("is synGenus matched on " + m.group(1));
                    result = true;
                }
            }
        } 
        return result;
    }
    */
    
    /*
    private boolean isBlank(String line) {
        boolean result = true;
        String newLine = removeAllTags(line);
        newLine = newLine.replaceAll("&nbsp;", "");
        if (alphanum.matcher(newLine).find()) {
            result = false;
        }
        return result;
    }
    */
    
    private ArrayList<String> getLines(String fileName) {
        ArrayList<String> result = new ArrayList<>();
        try {
            FileInputStream theFile = new FileInputStream(fileName);
            Scanner scan = new Scanner(theFile);
            
            /*
            String firstLine = scan.nextLine();
            theFile.close();
            
            Pattern badDelim = Pattern.compile("\\x0D");
            Matcher m = badDelim.matcher(firstLine);
            theFile = new FileInputStream(fileName);
            if (m.find()) {
                s_log.info("bad delimiter!");
                scan = new Scanner(theFile).useDelimiter(badDelim);
            } else {
                scan = new Scanner(theFile);
            }
            */
            
            String line = "";
            boolean open = false;
            StringBuffer newLine = new StringBuffer();
            
            while (scan.hasNextLine()) {
                line = scan.nextLine();
                line = line.trim();
                line = line.replace('\t', ' ');
                line = line.replaceAll("<h1>", "<p>");
                line = line.replaceAll("</h1>", "</p>");
                //if (isBlank(line)) {
                //    continue;
                //}
                if (open) {
                    newLine.append(line + " ");
                    if (closeP.matcher(line).find()) {
                        open = false;
                        result.add(fixKnownProblems(newLine.toString()));
                    }
                } else if (startP.matcher(line).find()) {
                    if (closeP.matcher(line).find()) {
                        result.add(fixKnownProblems(line));
                    } else {
                        open = true;
                        newLine = new StringBuffer();
                        newLine.append(line + " ");
                    }
                } 
            }
        } catch (FileNotFoundException e) {
            s_log.error("could not open " + fileName + ": " + e);
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
       // } catch (IOException e) {
       //     s_log.error("could not close " + fileName + ": " + e);
       //     org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        }
        return result;
    }
    
    private boolean isFossil(String line) {
        boolean result = false;
        if (extinctStar.matcher(line).find()) {
            result = true;
        }
        return result;
    }
    
    private boolean isValid(String line) {
        boolean result = false;
        if ((red.matcher(line).find() || blue.matcher(line).find()) && boldItalic.matcher(line).find()) {
            if (line.contains("imorpho")) {
                s_log.info("in isValid() imorpho is valid: " + line);
            }
            result = true;
        } else {
            if (line.contains("imorpho")) {
                s_log.info("in isValid() imorpho is not valid: " + line);
            }
        }
        return result;
    }
    
    private boolean isValidSpecies(String line) {
        boolean result = false;
        
        if ((red.matcher(line).find() || blue.matcher(line).find()) && boldItalicSpecies.matcher(line).find()) {
            result = true;
        }
        return result;
    }
    
    private boolean isAvailable(String line) {
        boolean result = true;
        if (purple.matcher(line).find() || black.matcher(line).find() ||
                !(color.matcher(line).find())) {
            result = false;
        }
        return result;
    }
    
    private boolean isGreen(String line) {
        boolean result = false;
        if (green.matcher(line).find()) {
            result = true;
        }
        return result;
    }
    
    private boolean isExtant(String line) {
        boolean result = false;
        if (red.matcher(line).find() && boldItalic.matcher(line).find() && (!extinctStar.matcher(line).find())) {
            result = true;
        }
        return result;
    }
    
    private HashMap<String, String> getGenus(String line, boolean valid, boolean available) {
        
        HashMap<String, String> result = new HashMap<>();
        String genus = "", subfamily = "", tribe = "", currentValid = "";
        String notes = line;
        line = removeAllTagsButItalics(line);
        //s_log.info("in get genus line is " + line);
        Matcher thisMatch = italics.matcher(line);
        if (thisMatch.find()) {
            genus = thisMatch.group(1);
        }
        
        genus = genus.toLowerCase();
        
        if (genus.contains("imorpho")) {
            //s_log.info("in get genus dimorpho valid is " + valid);
            //s_log.info("in get genus dimorpho available is " + available);
        }
        
        genus = new Formatter().capitalizeFirstLetter(genus.toLowerCase());
        thisMatch =  brackets.matcher(line);
        String bracketInfo = "";
        if (thisMatch.find()) {
            bracketInfo = thisMatch.group(1);
        }
        bracketInfo = bracketInfo.replaceAll("<i>", "");
        bracketInfo = bracketInfo.replaceAll("</i>", "");
        
        //s_log.info("bracket info is " + bracketInfo);
        if (bracketInfo.contains(":")) {
            String[] bracketParts = bracketInfo.split(":");
            subfamily = bracketParts[0].trim();
            tribe = bracketParts[1].trim();
        } else if (bracketInfo.toLowerCase().contains("incertae sedis in formicidae")) {
            subfamily = uncertain;
            tribe = "";
        } else if (bracketInfo.toLowerCase().contains("incertae sedis in")) {
            thisMatch = incertae.matcher(bracketInfo);
            if (thisMatch.find()) {
                subfamily = thisMatch.group(1);
            }
            subfamily = subfamily.trim();
            tribe = "";
            //s_log.info("matched ok and subfamily is: " + subfamily);
        } else {
            subfamily = bracketInfo;
            tribe = "";
        }
        
        //subfamily = leadStar.matcher(subfamily).replaceFirst("");
        subfamily = subfamily.replaceAll("\\*", "");
        int badParen = subfamily.indexOf(" (");
        if (badParen != -1) {
            subfamily = subfamily.substring(0,badParen);
        }
        
        tribe = leadStar.matcher(tribe).replaceFirst("");
        
        if (available) {
            currentValid = genus;
        }
        if (!valid) {
            currentValid = getCurrentValid(line);
        }
    
        if (genus.contains("imorpho")) {
            //s_log.info("out of get genus dimorpho valid is " + valid);
            //s_log.info("out of get genus dimorpho available is " + available);
        }
        result.put("subfamily", subfamily);
        result.put("genus", genus);
        result.put("tribe",tribe);
        result.put("taxonomic history", notes);
        result.put("current valid name", currentValid);
        result.put("available", Boolean.valueOf(available).toString());
        result.put("valid", Boolean.valueOf(valid).toString());
        //s_log.info("in get genus: " + subfamily + ":" + genus);
        return result;
    }
    
    private String getCurrentValid(String line) {
        String result = "";
        Matcher m = juniorSynOf.matcher(line);
        
        if (m.find()) {
            result = m.group(1);
        }
        // get rid of asterisk
        result = result.replaceAll("\\*", "");
        return result;
    }
    
    private HashMap<String, String> parseSpecies(String subfamily, String tribe, String genus, String line, boolean valid, boolean available) {
        
        HashMap<String, String> result = null;
        
        String species = "", origGenus = "", author = "", originalCombination = "";
        String notes = line;
        
        String test = "adsfasdfasdfasdf";  // species
        
        line = removeAllTagsButItalics(line);
        line = removeSquareBrackets(line);
        if (line.contains(test)) {
            s_log.info("valid: " + valid + " available: " + available);
        }
        Matcher m = italics.matcher(line);
        
        if (m.find()) {
            result = new HashMap<>();
            if (line.contains(test)) {
                s_log.info("italics matched");
            }
            species = m.group(1);
            if (line.contains(test)) {
                s_log.info("species is " + species);
            }

            if (valid || !available) {
                if (line.contains(test)) {
                    s_log.info("valid or not available");
                }
                if (m.find()) {
                    if (line.contains(test)) {
                        s_log.info("second italics matched");
                    }
                    origGenus = m.group(1);
                    if (line.contains(test)) {
                        s_log.info("orig genus found: " + origGenus);
                    }
                }
            } else {
                
                Matcher invalidM = invalidSpecies.matcher(species);
                if(invalidM.find()) {
                    species = invalidM.group(1);
                    origGenus = invalidM.group(2);
                }
            }
            String country = getCountry(line);
            
            
            // some unavailable names are quatranyms
            // these guys look like this:
            //    <p class=MsoNormal style='margin-left:.5in;text-align:justify;text-indent:-.5in'><i><span
            //    style='font-size:12.0pt;color:purple'>angustata</span></i><i><span
            //    style='font-size:12.0pt'>. Atta (Acromyrmex) moelleri</span></i><span
            //    style='font-size:12.0pt'> subsp. <i>panamensis</i> var. <i>angustata </i>Forel,
            //  1908b: 41 (w.q.) COSTA RICA. <b>Unavailable name</b> (Bolton, 1995b: 54).</span></p>
            //  /\.\s+(.*?)\s+.*?\s(.*?)<\/i>(.*?)<i>(.*?)<\/i>(.*?)<i>(.*?)<\/i>/
            String tempLine = line;
            if ((species.contains(".")) && (!available)) {
                
                tempLine = tempLine.replace("<i>","");
                tempLine = tempLine.replace("</i>", "");
                tempLine = betweenParens.matcher(tempLine).replaceAll("");
                //s_log.info("templine: " + tempLine);
                Matcher temp = quatrenary.matcher(tempLine);
                if (temp.find()) {
                    origGenus = temp.group(1);
                    species = temp.group(2);
                    //s_log.info("orig:*" + origGenus + "* species *" + species + "*");
                }
            }
            
            //s_log.info("origGenus is" + origGenus + " species is " + species);
            if ((origGenus != null) && (origGenus.length() > 0)) {
                origGenus = betweenParens.matcher(origGenus).replaceAll("");
                //s_log.info("jode: " + origGenus);
                Matcher temp = firstCap.matcher(origGenus);
                if (temp.find()) {
                    origGenus = temp.group();
                    if (line.contains(test)) {
                        s_log.info("origgenus2 matched " + origGenus);
                    }
                }
                String regExpItal;
                if (m.groupCount() > 1) {        
                    regExpItal = escapeRegExpChars(m.group(1));
                } else {
                    regExpItal = escapeRegExpChars(species);
                }
                //s_log.info("regexpital is " + regExpItal);
                
                //regExpItal = regExpItal.replaceAll("\\(","\\\\(");
                //regExpItal = regExpItal.replaceAll("\\)","\\\\)");
                
                Pattern thisPat = Pattern.compile(regExpItal + ".*([A-Z].*?\\d\\d\\d\\d)");
                if (m.groupCount() > 1) {
                    temp = thisPat.matcher(line);
                } else {
                    temp = thisPat.matcher(tempLine);
                }
                if (temp.find()) {
                    author = cleanAuthor(temp.group(1));
                    if (line.contains(test)) {
                        //s_log.info("author: " + author);
                    }
                }
                if (!genus.equals(origGenus)) {
                    author = "(" + author + ")";
                }
                originalCombination = getOriginalCombination(line);
            }
            if ((species.length() == 0) || (origGenus.length() == 0) || (author.length() == 0)) {  
                if (line.contains(test)) {
                    s_log.info("ERROR: *species:$species* *origGenus:$origGenus* *author:$author* line:$line\n");
                }            
                if ((notes.length() > 0) && (!knownProblem(notes))) {
                    result.put("taxonomic history",notes);
                } else {
                    result = null;
                }

            } else {
                
                result.put("subfamily", subfamily);
                result.put("tribe", tribe);
                result.put("genus", genus);
                result.put("species", species);
                result.put("species author date", author);
                result.put("taxonomic history",notes);
                result.put("valid", Boolean.valueOf(valid).toString());
                result.put("available", Boolean.valueOf(available).toString());
                result.put("original combination",originalCombination);
                result.put("country", country);
                if (line.contains(test)) {
                    s_log.info("success: *" + genus + "* *" + species + "* *"+author + "* *"+valid);
                }
            }        
        }
    
        return result;
    }
    
    private String getOriginalCombination(String line) {
        String result = "";
        
        // first get rid of all tags
        line = tag.matcher(line).replaceAll("");
        //s_log.info("in gOC line is " + line);
        Matcher m = originalCombination.matcher(line);
        if (m.find()) {
            result = m.group(1);
            //s_log.info("MATCHED! result is " + result);
        }
        return result;
    }
    
    private HashMap<String, String> parseSubspecies(String subfamily, String tribe, String genus, String line, boolean valid, boolean available) {
        HashMap<String, String> result = null;
        
        String subspecies = "", species = "", origGenus = "", author = "", originalCombination = "";
        String notes = line;
        
        line = removeAllTagsButItalics(line);
        Matcher m = italics.matcher(line);

        if (m.find()) {
            result = new HashMap<>();
            subspecies = m.group(1);
            if (m.find()) {
                String regExpItal = m.group(1);
                regExpItal = escapeRegExpChars(regExpItal);
                
                //regExpItal = regExpItal.replaceAll("\\(","\\\\(");
                //regExpItal = regExpItal.replaceAll("\\)","\\\\)");
                Pattern thisPat = Pattern.compile(regExpItal + ".*?([A-Z].*?\\d\\d\\d\\d)");
                Matcher temp = thisPat.matcher(line);
                if (temp.find()) {
                    author = cleanAuthor(temp.group(1));
                    
                }
                String secondItal = m.group(1);
                
                secondItal = betweenParens.matcher(secondItal).replaceAll("");
                
                temp = genusSpecies.matcher(secondItal);
                if (temp.find()) {
                    origGenus = temp.group(1);
                    species = temp.group(2);
                    
                }
                if (!genus.equals(origGenus)) {
                    author = "(" + author + ")";
                }
                
                Matcher current = currentlySub.matcher(line);
                if (current.find()) {
                    species = current.group(1);
                }
                species = species + " " + subspecies;
                originalCombination = getOriginalCombination(line);
            }
            String country = getCountry(line);
            
            if ((species.length() == 0) || (origGenus.length() == 0) || (author.length() == 0)) {   
                if ((notes.length() > 0) && (!knownProblem(notes))) {
                    result.put("taxonomic history",notes);
                } else {
                    result = null;
                }
                //s_log.info("ERROR: *subspecies:" + subspecies + "*species:" + species +"* *origGenus:" + origGenus+ "* *author:" + author+ "* line:" + line + "\n");
            } else {
                result.put("subfamily", subfamily);
                result.put("tribe", tribe);
                result.put("genus", genus);
                result.put("species", species);
                result.put("species author date", author);
                result.put("country", country);
                result.put("taxonomic history",notes);
                result.put("valid", Boolean.valueOf(valid).toString());
                result.put("available", Boolean.valueOf(available).toString());
                result.put("original combination",originalCombination);
                //s_log.info("success: *" + genus + "* *" + species + "* *"+author + "* *"+notes);
            }        
        }
        return result;
    }
    
    private String cleanAuthor(String author) {
        Matcher m = authorIn.matcher(author);
        if (m.find()) {
            String matched = m.group(1);
            author.replace(", in .* " + matched, ", " + matched);
        }
        author = author.replaceAll("&amp;", "&");
        author = author.trim();
        return author;
    }
    
    private String removeAllTagsButItalics(String theString) {
        theString = startItal.matcher(theString).replaceAll("FOOFOO");
        theString = endItal.matcher(theString).replaceAll("BARBAR");
        theString = tag.matcher(theString).replaceAll("");
        theString = foofoo.matcher(theString).replaceAll("<i>");
        theString = barbar.matcher(theString).replaceAll("</i>");
        return theString;    
    }
    
    private String removeAllTags(String theString) {
        theString = tag.matcher(theString).replaceAll("");
        theString = htmlElement.matcher(theString).replaceAll("");
        return theString;
    }
    
    public HashMap<String, ArrayList<String>> getSubfamilyLookup(ArrayList<HashMap<String, String>> subfamilies) {
        HashMap<String, ArrayList<String>> lookup = new HashMap<>();
        
        HashMap<String, String> tempHash;
        ArrayList<String> tempList;
        String key = null;
        String subfamily = null;
        String tribe = null;
        for (HashMap<String, String> stringStringHashMap : subfamilies) {
            subfamily = "";
            tribe = "";
            tempHash = stringStringHashMap;
            key = tempHash.get("genus");
            subfamily = tempHash.get("subfamily");
            tribe = tempHash.get("tribe");
            tempList = new ArrayList<>();
            tempList.add(subfamily);
            tempList.add(tribe);
            lookup.put(key, tempList);
            //s_log.info("adding key " + key);
        }
        
        Set<String> newSet = lookup.keySet();
        String tempKey;
        for (String s : newSet) {
            tempKey = s;
            //s_log.info(tempKey + " : " + lookup.get(tempKey).get(0) + " : " + lookup.get(tempKey).get(1));
        }
        return lookup;
    }
    
    private ArrayList<HashMap<String, String>> getSpecies(ArrayList<HashMap<String, String>> subfamilies, String type) {
        
        ArrayList<HashMap<String, String>> result = new ArrayList<>();
        HashMap<String, ArrayList<String>> subfamilyLookup = getSubfamilyLookup(subfamilies);
        
        HashMap<String, String> speciesResult = null;
        String fileName = null;
        String genus = "UNKNOWN";
        ArrayList<String> files = getSpeciesFiles(authorityFilesDir);
        Iterator<String> iter = files.iterator();
        String subfamily = "";
        String tribe = "";
        boolean fossil = false, available, valid, unidentifiable = false;
        int lineCount = 0;
        while (iter.hasNext()) {
            fileName = iter.next();
            s_log.info("looking at file " + fileName);
            lineCount = 0;
            ArrayList<String> lines = getLines(fileName);
            //s_log.info("got " + lines.size() + " lines." );
            Iterator<String> lineIter = lines.iterator();
            String line = "";
            while (lineIter.hasNext()) {    
                line = lineIter.next();
                lineCount++;
                //s_log.info("looking at " + fileName + ": " + lineCount + " ===> " + line);
                if ((isGenusExtant(line) || isGenusFossil(line))) {
                    
                    genus = parseGenus(line);
                    //System.out.print(" found genus!" + genus);
                    if (subfamilyLookup.get(genus) == null) {
                        //s_log.info("genus " + genus + " has no subfamily info!!!!");
                    } else {
                        subfamily = subfamilyLookup.get(genus).get(0);
                        tribe = subfamilyLookup.get(genus).get(1);
                        //s_log.info(" parsed genus is: " + genus + " from line " + line);
                    }
                } else {
                    fossil = isFossil(line);
                    available = isAvailable(line);
                    valid = isValidSpecies(line);
                    unidentifiable = isGreen(line);
                    if (unidentifiable) {
                        available = false;
                        valid = false;                        
                    }
                    if (type.equals("extinct") && fossil) {
                        if (isSpecies(line)) {
                            
                            //s_log.info("is extinct species ");
                            speciesResult = parseSpecies(subfamily, tribe, genus, line, valid, available);
                            if (speciesResult != null) {
                                result.add(speciesResult);
                            }
                        } else if (isSubspecies(line)) {
                            //s_log.info("is extinct subspecies!");
                            speciesResult = parseSubspecies(subfamily, tribe, genus, line, valid, available);
                            if (speciesResult != null) {
                                result.add(parseSubspecies(subfamily, tribe, genus, line, valid, available));
                            }
                        }
                    } else if (type.equals("extant") && !fossil) {
                        if (isSpecies(line)) {
                            //s_log.info("is extant species and valid is " + valid);
                            speciesResult = parseSpecies(subfamily, tribe, genus, line, valid, available);
                            if (speciesResult != null) {
                                result.add(speciesResult);
                            }
                        } else if (isSubspecies(line)) {
                            //s_log.info("is extant subspecies!");
                            speciesResult = parseSubspecies(subfamily, tribe, genus, line, valid, available);
                            if (speciesResult != null) {
                                result.add(speciesResult);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }
    
    
    private String parseGenus(String line) {
        Matcher m = boldItalic.matcher(line);
        String genus = "";
        if (m.find()) {
            genus = m.group(1);
            genus = new Formatter().capitalizeFirstLetter(genus.toLowerCase());
        }
        //s_log.info("parse genus outputs: " + genus);
        return genus;    
    }
    
    private boolean isGenusExtant(String line) {
        boolean result = false;
        String newLine;
        if (red.matcher(line).find() && boldItalic.matcher(line).find() && !(seeUnderPat.matcher(line).find()) && !(extinctStar.matcher(line).find())) {
            newLine = removeAllTags(line);
            
            if (startsWithCap.matcher(newLine).find()) {
                result = true;
            } else {
                s_log.info("weird is genus extant parse: " + newLine);
            }
        }
        return result;
    }
    
    private boolean isGenusFossil(String line) {
        boolean result = false;
        String newLine;
        if (red.matcher(line).find() && boldItalic.matcher(line).find() && !(seeUnderPat.matcher(line).find()) && extinctStar.matcher(line).find()) {
            newLine = removeAllTags(line);
            if (startsWithCap.matcher(newLine).find()) {
                result = true;
            } else {
                s_log.info("weird genus extinct parse " + newLine);
            }
        }
        return result;
    }
    private boolean isSpecies(String line) {
        boolean result = false;
        
        if (!notSpeciesP.matcher(line).find() && !isSubspecies(line)) {
            result = true;
        }
        return result;
    }
    
    private boolean isSubspecies(String line) {
        boolean result = false;
        if (blue.matcher(line).find()) {
            result = true;
        }
        return result;
    }
    
    private boolean isGenus(String line) {
        boolean result = false;
        Matcher m2 = blue.matcher(line);
        if (!(m2.find())) {
            line = removeAllTagsButItalics(line);
            Matcher m = genusP.matcher(line);
            if (m.find()) {
                result = true;
            } 
        } 
        return result;
    }
    
    private String getSubfamilyErrors(ArrayList<HashMap<String, String>> subfamilies) {
        
        // make sure that each genus has only one subfamily
        // make sure that each genus has only one tribe
        // make sure that each tribe has only one subfamily
        
        String result = "";
        
        String genusSubfamily = getDuplicates(subfamilies,"genus","subfamily");
        String genusTribe = getDuplicates(subfamilies,"genus","tribe");
        String tribeSubfamily = getDuplicates(subfamilies,"tribe","subfamily");
        
        result = genusSubfamily + "<br>" + genusTribe + "<br>" + tribeSubfamily;
        
        return result;
    }
    
    private String duplicatesToString(HashMap<String, ArrayList<String>> contents, String keyLabel, String valueLabel) {
        StringBuffer result = new StringBuffer();
        String key = "";
        for (String s : contents.keySet()) {
            key = s;
            if ((key.length() > 0) && contents.get(key).size() > 1) {
                result.append(keyLabel + " " + key + " has " + contents.get(key).size() + " instances of " + valueLabel + ":" + contents.get(key) + "<br>");
            }
        }
        
        return result.toString();
    }
    
    // this takes an array of hashes where each hash has a genus, subfamily, tribe etc
    // it takes a key like genus and compiles a list of values, like subfamily, for each
    // instance of the key.  So, there should only be one value in the array for each key - if
    // not, the key has two different values
    //
    private String getDuplicates(ArrayList<HashMap<String, String>> contents, String key, String value) {
        HashMap<String, ArrayList<String>> result = new HashMap<>();
        Iterator<HashMap<String,String>> iter = contents.iterator();
        HashMap<String, String> temp;
        String keyString;
        while (iter.hasNext()) {
            temp = iter.next();
            keyString = temp.get(key);
            
            if (result.containsKey(keyString)) {
                if (!result.get(keyString).contains(temp.get(value))) {
                    result.get(keyString).add(temp.get(value));
                }
            } else {
                result.put(keyString, new ArrayList<>());
                result.get(keyString).add(temp.get(value));
            }
        }
        return duplicatesToString(result, key, value);
    }
    
    private String getSpeciesErrors(ArrayList<HashMap<String, String>> subfamilies, ArrayList<HashMap<String, String>> species) {
        
        // here are the types of errors this thing looks for 
        // genera that are not part of the subfamily/genus list
        // lines that don't have authors.... mmm... that's about it!
        
        Iterator<HashMap<String, String>> iter = species.iterator();
        HashMap<String,String> temp;
        String subfamily, genus, author, notes, speciesEp;
        StringBuffer result = new StringBuffer();
        while (iter.hasNext()) {
            temp = iter.next();
            subfamily = temp.get("subfamily");
            genus = temp.get("genus");
            author = temp.get("species author date");
            notes = temp.get("taxonomic history");
            speciesEp = temp.get("species");
            if ((notes != null) && (notes.length() > 0)) {
                if ((speciesEp == null) || (speciesEp.length()==0)) {
                    result.append("could not parse line: " + notes);
                } else {
                    if ((subfamily == null) || (subfamily.length() == 0)) {
                        result.append("could find no subfamily for genus " + genus + " in line " + notes + "<br>");
                    }
                    if ((author == null) || (author.length() == 0)) {
                        result.append("could find no author for genus " + genus + " " + speciesEp + " in line " + notes + "<br>");
                    }
                }
            }
        }
        
        return result.toString();
    }
    
    private ArrayList<String> flatten(ArrayList<HashMap<String, String>> contents, ArrayList<String> header, char delim) {
        Iterator<HashMap<String, String>> contentsIter = contents.iterator();
        Iterator<String> headerIter;
        ArrayList<String> resultArray = new ArrayList<>();
        ArrayList<String> tempArray = new ArrayList<>();
        HashMap<String, String> temp;
        String thisHeader = "";
        // for each element in the array, go through the header list put the items in the right order,
        // join them with the delimiter and add the resulting string to the result array
        while (contentsIter.hasNext()) {
            temp = contentsIter.next();
            tempArray.clear();
            headerIter = header.iterator();
            while (headerIter.hasNext()) {
                thisHeader = headerIter.next();
            
                if ((temp.containsKey(thisHeader)) && (temp.get(thisHeader) != null)) {
                    s_log.info(thisHeader + " ----> " + temp.get(thisHeader));
                    tempArray.add(temp.get(thisHeader));
                } else {
                    tempArray.add("");
                }
            }
            resultArray.add(arrayJoin(tempArray, delim));
        }
        
        // return the resultArray joined with newlines
        return resultArray;
    }
    
    private String arrayJoin(ArrayList<String> theList, char delim) {
        Iterator<String> iter = theList.iterator();
        StringBuffer result = new StringBuffer();
        String temp = null;
        boolean beenThere = false;
        while (iter.hasNext()) {
            if (beenThere) {
                result.append(delim);
            } else {
                beenThere = true;
            }
            temp = iter.next();
            if (temp.length() > 0) {
                result.append(temp);
            } 
        }
        return result.toString();
    }
    
    /*
    public ArrayList<HashMap<String, String>> getSubfamiliesForJuniorSynonyms(ArrayList<HashMap<String, String>> thisList, HashMap<String, ArrayList<String>> lookup) {
        ArrayList<HashMap<String, String>> result = new ArrayList<HashMap<String, String>>();
        Iterator<HashMap<String, String>> iter = thisList.iterator();
        HashMap<String, String> temp;
        String currentValid, currentSubfamily;
        while (iter.hasNext()) {
            temp = iter.next();
            if (temp.get("subfamily").indexOf("junior synonym of") != -1) {
                currentValid = temp.get("current valid");
                if ((currentValid != null) && (lookup.get(currentValid) != null) && (lookup.get(currentValid).size() > 0)) {
                    currentSubfamily = lookup.get(currentValid).get(0);
                    temp.put("subfamily", currentSubfamily);
                }
            }
            result.add(temp);
        }
        return result;
    }
    */
    public ArrayList<HashMap<String, String>> getSubfamiliesForSynonyms(ArrayList<HashMap<String, String>> thisList, HashMap<String, ArrayList<String>> lookup) {
        ArrayList<HashMap<String, String>> result = new ArrayList<>();
        Iterator<HashMap<String, String>> iter = thisList.iterator();
        HashMap<String, String> temp;
        String currentValid, currentSubfamily, tempGenus;
        while (iter.hasNext()) {
            temp = iter.next();
            //if (temp.get("subfamily").indexOf("junior synonym of") != -1) {
            if (temp.get("subfamily").contains(" ")) {
                //s_log.info("subfamily is " + temp.get("subfamily"));
                currentValid = temp.get("current valid name");
                if ((currentValid != null) && (lookup.get(currentValid) != null) && (lookup.get(currentValid).contains(" ")) && (lookup.get(currentValid).size() > 0)) {
                    currentSubfamily = lookup.get(currentValid).get(0);
                    temp.put("subfamily", currentSubfamily);
                    //s_log.info("from currentvalid, putting " + currentSubfamily + " into subfamily");
                } else {
                    Matcher m = firstCappedWord.matcher(temp.get("subfamily"));
                    if (m.find()) {
                        tempGenus = m.group(1);
                        //s_log.info("tempgenus is " + tempGenus);
                        if ((lookup.get(tempGenus) != null) && (lookup.get(tempGenus).size() > 0)) {
                            currentSubfamily = lookup.get(tempGenus).get(0);
                            temp.put("subfamily", currentSubfamily);
                            temp.put("current valid name",tempGenus);
                            //s_log.info("putting " + currentSubfamily + " into subfamily");
                        } else {
                            //s_log.info("couldn't find " + tempGenus + " in lookup");
                        }
                    } else {
                        //s_log.info("synonym firstCappedWord misses on " + temp.get("subfamily"));
                    }
                }
            }
            result.add(temp);
        }
        return result;
    }
    
    private ArrayList<String> getSpeciesFiles(String directory) {
        ArrayList<String> results = new ArrayList<>();
        File f1 = new File (directory) ;
    
        File[] strFilesDirs = f1.listFiles();
        for (File strFilesDir : strFilesDirs) {
            if (strFilesDir.getName().endsWith(".html")) {
                results.add(authorityFilesDir + "/" + strFilesDir.getName());
            }
        }
        return results;
    }

    private String getCountry(String theString) {
        String result = "";
        
        Matcher usa = usaPat.matcher(theString);
        if (usa.find()) {
            result = "U.S.A.";
        } else {
            Matcher country = countryPat.matcher(theString);
            if (country.find()) {
                result = country.group(1);
                //Matcher islands = islandPat.matcher(result);
                //if (islands.find()) {
                //    result = islands.group(1);
                //}
                
                result = result.trim();
                
            }
        }
        if (!(result.equals("U.S.A"))) {
            result = new Formatter().capitalizeEachWord(result);
        }
        return result;
    }

    private String escapeRegExpChars(String theString) {
        theString = theString.replaceAll("\\(","\\\\(");
        theString = theString.replaceAll("\\)","\\\\)");
        theString = theString.replaceAll("\\.", "\\\\.");
        theString = theString.replaceAll("\\*", "\\\\*");
        theString = theString.replaceAll("\\[", "\\\\[");
        theString = theString.replaceAll("\\]", "\\\\]");
        theString = theString.replaceAll("\\,", "\\\\,");
        theString = theString.replaceAll("\\?", "\\\\?");
        return theString;
    }
    
    private boolean knownProblem(String line) {
        boolean result = false;
        if (line.contains("see under")) {
            result = true;
        }
        return result;
    }

    private String fixKnownProblems(String line) {
        String newLine = line;
        
        // some lines have adjacent <i></i><i></i> tags which should really be merged
        if (newLine.contains("</i><i>")) {
            Matcher m = adjacentI.matcher(newLine);
            if (m.find()) {
                String one = m.group(1);
                String two = m.group(2);
                String oldI = "<i>" + one + "</i><i>" + two + "</i>";
                String newI = "<i>" + one + two + "</i>";
                newLine = newLine.replace(oldI,newI);
            }
        }
        
        return newLine;
    }
    
    private String removeSquareBrackets(String line) {
        line = line.replace("[", "");
        line = line.replace("]", "");
        return line;
    }

    // bolds late in a line confusing things
    /*
    private String removeBoldsFollowingDate(String line) {
        String newLine = line;
        String date;
        Matcher m = datePat.matcher(line);
        if (m.find()) {
            date = m.group(1);
            int dateStart = line.indexOf(date);
            String start = date.substring(0,dateStart);
            String end = date.substring(dateStart+4);
            end = end.replaceAll("<b>", "");
            end = end.replaceAll("</b>", "");
            newLine = start + date + end;
        }
        return newLine;
    }
    */

}
