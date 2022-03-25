package org.calacademy.antweb.util;

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