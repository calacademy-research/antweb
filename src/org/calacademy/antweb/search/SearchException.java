package org.calacademy.antweb.search;

/**
 * Checked exception with a message only. Thrown by AdvancedSearch ("Not enough search criteria ..."); caught by
 * SearchAction (message shown to the visitor, escaped), SearchListAction and the Species List Tool.
 */
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