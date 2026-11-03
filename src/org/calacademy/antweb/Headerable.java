package org.calacademy.antweb;

/**
 * The header fields of a place page: species imaged, link parameters, short display name and name. Implemented by
 * Overview (so every place); no code uses the type Headerable itself (as of 2026-11-02).
 */
public interface Headerable {
  int getNumSpeciesImaged();
  String getParams();
  String getShortDisplayName();
  String getName();
}