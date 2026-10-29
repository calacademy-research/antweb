package org.calacademy.antweb.util.exception;

import org.calacademy.antweb.util.AntwebException;

/**
 * Thrown by TaxonDb when a taxon name has no row in table taxon; BrowseAction (taxon pages) and
 * FieldGuideAction catch it and show a "not found" message instead of an error page. A message only, like its
 * parent AntwebException.
 */
public class TaxonNotFoundException extends AntwebException {

  private String message;

  public TaxonNotFoundException() {
  }

  public TaxonNotFoundException(String message) {
    this.message = message;  
  }
  
  public String getMessage() {
    return this.message;
  }
  
  public String toString() {
    return getMessage();
  } // return super.toString() + " " +
}