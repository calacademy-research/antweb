package org.calacademy.antweb.upload;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.*;

import javax.servlet.http.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

// See documentation of Type Status at end of file.

public class TypeStatusMgr extends Action {

    private static Log s_log = LogFactory.getLog(TypeStatusMgr.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
                                 HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        String message = null;

        LogMgr.emptyLog("typeStatusSpeciesFound.txt");
        LogMgr.emptyLog("typeStatusSpeciesNotFound.txt");
        LogMgr.emptyLog("typeStatusNoTaxonName.txt");

        java.sql.Connection connection = null;
        try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "TypeStatusMgr.execute()");
            int speciesFound = 0;
            int speciesNotFound = 0;
            int noTaxonName = 0;
            
                SpecimenDb specimenDb = new SpecimenDb(connection);
                ArrayList<String> typeStatusList = specimenDb.getTypeStatusList(1);
                for (String typeStatus : typeStatusList) {
                    //String typeStatus = "Holotype of Anochetus daedalus";
                    // This shows some functionality that would handle the specimen.type_status field entries.
                    String taxonName = TypeStatusMgr.getTypeStatusSpecies(typeStatus);

                    //String taxonName = "Anochetus daedalus";
                    //String taxonName = "ponerinaeanochetus daedalus";
                    if (taxonName != null) {
                        Species species = TaxonMgr.getSpecies(connection, taxonName);
                        if (species != null) {
                            ++speciesFound;
                            LogMgr.appendLog("typeStatusSpeciesFound.txt", speciesFound + ". taxonName:" + taxonName + " species:" + species.getFullName());

                            if ("philippinensis".equals(species.toString())) A.log("execute() species:" + species + " taxarank:" + species.getRank());
                        } else {
                            ++speciesNotFound;
                            LogMgr.appendLog("typeStatusSpeciesNotFound.txt", speciesNotFound + ". " + taxonName);
                        }
                    } else {
                        ++noTaxonName;
                        LogMgr.appendLog("typeStatusNoTaxonName.txt", noTaxonName + ". " + typeStatus);
                    }
                }

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
        } finally {
            DBUtil.close(connection, this, "TypeStatusMgr.execute()");
        }

        message = "<h3>Type Status Reports</h3>";
        message += "<br><br><a href='" + AntwebProps.getDomainApp() + "/web/log/typeStatusSpeciesFound.txt" + "'>Species Found</a>";
        message += "<br><br><a href='" + AntwebProps.getDomainApp() + "/web/log/typeStatusSpeciesNotFound.txt" + "'>Species Not Found</a>";
        message += "<br><br><a href='" + AntwebProps.getDomainApp() + "/web/log/typeStatusNoTaxonName.txt" + "'>No Taxon Name</a>";

        A.log("execute() complete.");
        request.setAttribute("message", message);
        return (mapping.findForward("message"));
    }


    // During specimen upload, or perhaps later, we want to ascertain types.
    // Read in the types of specimen and parse. If we can find...
    // [type] of [Genus] [species] then it is a hit :)
    public static String getTypeStatusSpecies(String typeString) {
        String taxonName = null;
        if (typeString == null) return null;
        int i = typeString.indexOf(" of ");
        int nameI = i + 4;
        if (nameI > 8) { // Could be "type of ..."
          String name = typeString.substring(nameI);
          //A.log("name:" + name);
          int spaceI = name.indexOf(" ");
          if (name.contains(" ")) { // Could be a good taxon name of format: [Genus] [species]
              String genusName = name.substring(0, spaceI);
              String speciesName = name.substring(spaceI + 1);
              String subspeciesName = null;
              if (speciesName.contains(" ")) { // Could be a subspecies?
                  spaceI = speciesName.indexOf(" ");
                  subspeciesName = speciesName.substring(spaceI + 1);
                  speciesName = speciesName.substring(0, spaceI);
              }
              Genus genus = TaxonMgr.getGenusFromName(genusName.toLowerCase());
              if (genus == null) {
                  //A.log("getTypeStatusSpecies() genusName not found:" + genusName);
                  return null;
              }
              taxonName = genus.getSubfamily() + genus.getGenus() + " " + speciesName;
              if (subspeciesName != null) taxonName += " " + subspeciesName;
          }
        }

        return taxonName;
    }
}


/*
Have the type status field as part of the specimen data check during specimen upload.

---Brian Fisher Jul 25, 2020
We  need to link type specimens to the published name, and somehow associate the type specimens in Antweb with the name
in AntCat.  The first step is to see if we can recognize the name in the type status field.  We don't want to convert
this name to the current valid name, we just need to recognize the name and know its protonym ID in AntCat.  Every
available protonym in AntCat has a type and we want to image it.  We don't know how many of them we have imaged or have
a way to monitor progress.

In Antweb, the type status field should be in the format "Kind of Type" of "name".   Kind of type is like Holotype,
syntype, paratype, etc.  We are going to have to parse this text field and look for inconsistencies in the structure
and words used.

The "name" is not the current VALID name. It refers to the described name which can take many forms.  Maybe already you
know that in AntCat we use these terms to describe forms [avatars] of the same name:
protonym [the way the name was first published]
original combination [cleaned up version of the protonym - without var. or subsp. no subgenera, etc.]
current combination - currently used genus-species combination
obsolete combination - past used genus-species combination

Ideally, the type status field includes only names in the format original combination or protonym.  On upload, we need
to check if the type status field name can be recognized, and if recognized, can we hyperlink to the name in AntCat?
Can we also provide the antcat ID to the protonym?  This is the part now I am not sure what to do.  From AntCat, from
the protonym page, I wan to add the casent number of the type(s) to the protonym page.  If the protonym ID is added to
Antweb type status field, maybe this will help create a lookup from AntCat?


I have done this manually so far in AntCat, but we need to make this seamless.  Here is a page I have done manually in
AntCat:
https://antcat.org/protonyms/156568
https://antcat.org/protonyms/168830

I am not clear on the details of how we can make this work but it seems the first step is to check if we can recognize
names in the type status field and flag those specimens where we can't recognize names or those that don't have the
correct structure [ for example, we need to flag those that just say "type" or "Holotype" without providing the name.

---Brian Fisher July 28, 2020
Regarding types, names can have multiple types and glad you were able to match 9975.  I think the first phase which we
can do just on AntWeb side is to curate the data to increase the number we can match. Do do that we need to add to the
report feedback on which specimens have type status fields that do not match a name.

Second, we will need to figure out how to deal with homonyms.  If the name matches a homonym, we need more information.
In those cases, we can not link the name to a protonym ID from AntCat.  Could we have the user enter manually the
Protonym ID into the type status field?
 */


