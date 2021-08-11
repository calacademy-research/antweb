package org.calacademy.antweb.util;

public class AntwebException extends Exception {

  private String message = null;

    public AntwebException() {
  }

  public AntwebException(String message) {
    this.message = message;  
  }
  
  public String getMessage() {
    return this.message;
  }
  
  public String toString() {
    return super.toString() + " " + getMessage();
  }  
}