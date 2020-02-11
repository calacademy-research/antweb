package test.org.calacademy.antweb;
 
import servletunit.struts.MockStrutsTestCase;
import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;

import java.io.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.search.*;

public class AntWebStrutsTest extends MockStrutsTestCase {
 
  public AntWebStrutsTest(String name) {
    super(name);
  }

  public void setUp() throws Exception {
	super.setUp();
    setContextDirectory(new File("/Users/mark/dev/calacademy/antweb/WEB-INF"));
	setInitParameter("validating","false");
  }

  public static void main (String[] args) {
    junit.textui.TestRunner.run(AntWebStrutsTest.class);
  }
 
  public void testJUnit() {
    assertEquals("testing to see if junit is working", 1,1);
  } 

  public void testSimpleSearchMadantsSuccess() {
    setRequestPathInfo("/search");
    addRequestParameter("searchMethod", "otherSearch");
    addRequestParameter("project", "madants");
    addRequestParameter("searchType", "contains");
    addRequestParameter("name", "myrm");
    actionPerform();
    verifyForward("success");
    assertFalse("good search madants found something", ((SearchResults) getSession().getAttribute("searchResults")).getResults().size() == 0);
  } 

  public void testSimpleSearchCalantsSuccess() {
	setRequestPathInfo("/search");
	addRequestParameter("searchMethod", "otherSearch");
	addRequestParameter("project", "calants");
	addRequestParameter("searchType", "equals");
	addRequestParameter("name", "Acromyrmex");
	actionPerform();
	verifyForward("success");
	assertFalse("good search calants found something", ((SearchResults) getSession().getAttribute("searchResults")).getResults().size() == 0);
  } 

  public void testSimpleSearchWorldSuccess() {
	setRequestPathInfo("/search");
	addRequestParameter("searchMethod", "otherSearch");
	addRequestParameter("searchType", "contains");
	addRequestParameter("name", "Azteca");
	actionPerform();
	verifyForward("success");
	assertFalse("good search world ants found something", ((SearchResults) getSession().getAttribute("searchResults")).getResults().size() == 0);
  } 

  public void testSimpleSearchNoName() {
	setRequestPathInfo("/search");
	addRequestParameter("searchMethod", "otherSearch");
	addRequestParameter("project", "madants");
	addRequestParameter("searchType", "contains");
	addRequestParameter("name", "");
	actionPerform();
	String [] actionErrors = {"error.name.required"};
	verifyActionErrors(actionErrors);
  } 

  public void testSimpleSearchFail() {
    setRequestPathInfo("/search");
    addRequestParameter("searchMethod", "otherSearch");
    addRequestParameter("project", "madants");
    addRequestParameter("searchType", "contains");
    addRequestParameter("name", "antsaremyfriends");
    actionPerform();
    verifyForward("success");
    assertTrue("bad search found nothing", ((SearchResults) getSession().getAttribute("searchResults")).getResults().size() == 0);
  } 
 
  public void testBayAreaSearchSuccess() {
	setRequestPathInfo("/bayAreaSearch");
	addRequestParameter("searchMethod", "bayAreaSearch");
	String [] adm2s = {"alameda", "solano"};
	addRequestParameter("adm2", adm2s);
	actionPerform();
	verifyForward("success");
	assertFalse("good Bay Area Search found something", ((BayAreaSearchResults) getSession().getAttribute("searchResults")).getResults().size() == 0);
  } 
  
  public void testBayAreaSearchFail() {
	 setRequestPathInfo("/bayAreaSearch");
	 addRequestParameter("searchMethod", "bayAreaSearch");
	 String [] adm2s = {"east bay"};
	 addRequestParameter("adm2s", adm2s);
	 actionPerform();
	 verifyForward("success");
	 assertTrue("bad Bay Area Search found nothing", ((BayAreaSearchResults) getSession().getAttribute("searchResults")).getResults().size() == 0);
   } 
  
  public void testAdvancedSearchSuccess() {
	setRequestPathInfo("/advancedSearch");
	addRequestParameter("searchMethod", "advancedSearch");
	addRequestParameter("project", "");
	addRequestParameter("genusSearchType", "contains");
	addRequestParameter("genus", "ba");
	addRequestParameter("speciesSearchType", "contains");
	addRequestParameter("species", "un");
	actionPerform();
	verifyForward("advanced");
	assertFalse("good advanced search found something", ((AdvancedSearchResults) getSession().getAttribute("advancedSearchResults")).getResults().size() == 0);
  } 
    
     /* Checks for sa and ta. Both exists individually, but not together. */
  public void testAdvancedSearchFail() {
	setRequestPathInfo("/advancedSearch");
	addRequestParameter("searchMethod", "advancedSearch");
	addRequestParameter("project", "");
	addRequestParameter("genusSearchType", "contains");
	addRequestParameter("genus", "sa");
	addRequestParameter("speciesSearchType", "contains");
	addRequestParameter("species", "ta");
	actionPerform();
	verifyForward("advanced");
	assertTrue("bad advanced search found nothing", ((AdvancedSearchResults) getSession().getAttribute("advancedSearchResults")).getResults().size() == 0);
  } 
 
  public void testAdvancedSearchImageOnlySuccess() {
	setRequestPathInfo("/advancedSearch");
	addRequestParameter("searchMethod", "advancedSearch");
	addRequestParameter("project", "");
	addRequestParameter("genusSearchType", "contains");
	addRequestParameter("speciesSearchType", "contains");
	addRequestParameter("images", "on");
	actionPerform();
	verifyForward("advanced");
	assertFalse("good advanced search found something", ((AdvancedSearchResults) getSession().getAttribute("advancedSearchResults")).getResults().size() == 0);
  } 
  
  public void testAdvancedSearchTypesOnlySuccess() {
	setRequestPathInfo("/advancedSearch");
	addRequestParameter("searchMethod", "advancedSearch");
	addRequestParameter("project", "");
	addRequestParameter("genusSearchType", "contains");
	addRequestParameter("speciesSearchType", "contains");
	addRequestParameter("types", "on");
	actionPerform();
	verifyForward("advanced");
	assertFalse("good advanced search found something", ((AdvancedSearchResults) getSession().getAttribute("advancedSearchResults")).getResults().size() == 0);
  } 
  
  
  
 
  public void testDescriptionSuccess() {
    setRequestPathInfo("/description");
    addRequestParameter("project", "calants");
    addRequestParameter("rank", "species");
    addRequestParameter("name", "pallipes");
    addRequestParameter("genus", "amblyopone");
    actionPerform();
    verifyForward("success");
    assertEquals("good description found something", ((Taxon) getSession().getAttribute("taxon")).getSubfamily(), "amblyoponinae");
  }

  public void testDesciptionFailure() {
    setRequestPathInfo("/description");
    addRequestParameter("project", "calants");
    addRequestParameter("rank", "species");
    addRequestParameter("name", "pallipes");
    addRequestParameter("genus", "genusoflove");
    actionPerform();
    verifyForward("success");
    assertNull("good description found something", ((Taxon) getSession().getAttribute("taxon")).getSubfamily());
  }

  public void testBrowser() {
    setRequestPathInfo("/browse");
    addRequestParameter("project", "calants");
    addRequestParameter("rank", "family");
    actionPerform();
    verifyForward("success");
    assertFalse("browser works", ((Taxon) getSession().getAttribute("taxon")).getChildren().size() == 0);
  }

  public void testSpecimen() {
    setRequestPathInfo("/specimen");
    addRequestParameter("name", "casent0005336");
    actionPerform();
    verifyForward("success");
    assertEquals("specimen found something", ((Specimen) getSession().getAttribute("specimen")).getName(), "casent0005336");
  }

  public void testSlideShow() {
    setRequestPathInfo("/slideShow");
    addRequestParameter("rank", "genus");
    actionPerform();
    verifyForward("success");
    assertNotNull("slideshow found something", ((Taxon) getSession().getAttribute("showTaxon")).getPrettyName());
  }
}

