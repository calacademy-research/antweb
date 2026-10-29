package org.calacademy.antweb.util;

/**
 * AntWeb's own checked exception: a message only (getMessage, toString), no cause. Thrown by the uploads, the
 * Scheduler, HttpUtil's fetches, TaxonProxy and OverviewMgr lookups, and caught by the actions to show the
 * message. Subclass: util.exception.TaxonNotFoundException. Docs: docs/flows/specimen-upload.md,
 * docs/flows/taxon-pages.md.
 */
public class AntwebException extends Exception {

  private String message;

    public AntwebException() {
  }

  public AntwebException(String message) {
    this.message = message;  
  }
  
  public String getMessage() {
    return this.message;
  }
  
  public String toString() {
    return getMessage();
  } // return super.toString() + " " +
}