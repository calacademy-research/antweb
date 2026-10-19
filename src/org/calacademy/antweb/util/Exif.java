package org.calacademy.antweb.util;

import com.zonageek.jpeg.ExifBlock;
import com.zonageek.jpeg.Jpeg;
import com.zonageek.jpeg.JpegException;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * EXIF data of an image file, read when the image page is shown (nothing is stored
 * in the database). Text is decoded by ExifText (UTF-8, else Windows-1252); binary
 * fields are summarized. getRows() is what the admin section of bigPicture shows.
 */
public class Exif {

  private boolean exifFound = false;

  private String artist = "";
  private String copyright = "";
  private String date = "";
  private String exifFields = "";
  private List<ExifText.Row> rows = new ArrayList<>();

  public Exif(String imagePath) {

    if (imagePath == null) {
      A.slog("image path null");
      return;
    }

    Jpeg jpeg = new Jpeg();
    try (InputStream in = new BufferedInputStream(new FileInputStream(imagePath))) {
      // Only the blocks before the image data: EXIF is at the start of the file.
      jpeg.readHeaders(in);
    } catch (FileNotFoundException e) {
      LogMgr.appendLog("imageNotFound.txt", imagePath);
      AntwebUtil.log("WSS. Exif() 1 exception:" + e + " on " + imagePath);
      return;
    } catch (JpegException e) {
      LogMgr.appendLog("zonageeks.txt", imagePath);
      AntwebUtil.log("WSS. Exif() 2 exception:" + e + " on " + imagePath);
      return;
    } catch (IOException | RuntimeException e) {
      // RuntimeException: a damaged EXIF block (offsets outside the block).
      AntwebUtil.log("Exif() e:" + e + " on " + imagePath);
      return;
    }

    exifFound = true;
    read(jpeg.getExifBlock());
  }

  /** For tests: the EXIF of an image given as bytes. */
  public static Exif fromBytes(byte[] jpegBytes) throws IOException, JpegException {
    Jpeg jpeg = new Jpeg();
    jpeg.readHeaders(new ByteArrayInputStream(jpegBytes));
    Exif exif = new Exif();
    exif.exifFound = true;
    exif.read(jpeg.getExifBlock());
    return exif;
  }

  private Exif() {}

  private void read(ExifBlock exif) {
    if (exif == null) return;
    HashMap theFields = exif.getExifFields();
    if (theFields == null) return;

    rows = ExifText.rows(theFields, exif.isBigEndian());
    StringBuilder sb = new StringBuilder();
    for (ExifText.Row r : rows) {
      if (sb.length() > 0) sb.append("; ");
      sb.append(r.label).append(": ").append(r.value);
    }
    exifFields = sb.toString();

    artist = text(theFields.get("Artist"));
    copyright = text(theFields.get("Copyright"));

    String dateTime = text(theFields.get("DateTime"));
    if (dateTime.length() > 0) {
      int space = dateTime.indexOf(" ");
      String d = space != -1 ? dateTime.substring(0, space) : dateTime;
      String[] parts = d.split(":");
      date = parts.length == 3 ? parts[1] + "/" + parts[2] + "/" + parts[0] : d;
    }
  }

  private static String text(Object value) {
    if (value instanceof java.util.Collection && !((java.util.Collection) value).isEmpty()) {
      value = ((java.util.Collection) value).iterator().next();
    }
    if (!(value instanceof String)) return "";
    String s = ExifText.decodeText((String) value);
    return s == null ? "" : s;
  }

  public boolean isFound() {
    return exifFound;
  }

  public String getArtist() {
    return artist;
  }

  public String getCopyright() {
    return copyright;
  }

  public String getDate() {
    return date;
  }

  /** All shown fields on one line, plain text (for logs). */
  public String getFields() {
    return exifFields;
  }

  /** Labelled, decoded values for display. Plain text: escape once (Html.esc). */
  public List<ExifText.Row> getRows() {
    return rows;
  }

  public String toString() {
    return "exifFields:" + exifFields + " artist:" + artist + " date:" + date + " copyright:" + copyright;
  }

}
