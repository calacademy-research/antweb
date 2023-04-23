package org.calacademy.antweb.util.exception;

import org.calacademy.antweb.util.AntwebException;

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