package org.calacademy.antweb.search;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class TypeSearchAction extends Action {

    private static Log s_log = LogFactory.getLog(TypeSearchAction.class);
    
    public TypeSearchResults getResults(HttpServletRequest request,  HttpServletResponse response,
    	SearchParameters searchParameters) throws IOException, ServletException {

		// Extract attributes we will need
		HttpSession session = request.getSession();
		
		String name = searchParameters.getName().toLowerCase();
		String searchType = searchParameters.getSearchType();
		String types = searchParameters.getTypes();
		String imagesOnly = searchParameters.getImagesOnly();
        String project = searchParameters.getProject();

        TypeSearchResults results = new TypeSearchResults();

		return results;
    }
}
