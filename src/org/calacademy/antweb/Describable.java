package org.calacademy.antweb;

import java.util.Hashtable;

public interface Describable {

  String getName();
  void setName(String name);
  Hashtable<String, String> getDescription();

}
