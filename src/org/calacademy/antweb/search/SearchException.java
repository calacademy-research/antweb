package org.calacademy.antweb.search;

public class SearchException extends Exception {

  String message;

  public SearchException(String message) {
    setMessage(message);
  }

  public void setMessage(String message) {
    this.message = message;
  }
  public String getMessage() {
    return message;
  }
  
  public String toString() {
    return message;
  }
}