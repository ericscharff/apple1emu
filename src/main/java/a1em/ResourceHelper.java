/* Copyright (c) 2007-2022, Eric Scharff
   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.
   There is NO WARRANTY for this software.  See LICENSE.txt for
   details. */

package a1em;

import java.io.InputStream;
import java.net.URL;
import java.util.ResourceBundle;

public class ResourceHelper {
  private ResourceBundle resourceBundle;

  protected void warn(String s) {
    System.err.println(s);
  }

  public ResourceHelper() {
    resourceBundle = ResourceBundle.getBundle("resources.apple1");
  }

  public String getProperty(String propName) {
    return resourceBundle.getString(propName);
  }

  private String makeResourceName(String resourceName) {
    String prefix = "/resources/";
    if (resourceName.startsWith("image.")) {
      prefix += "images/";
    } else if (resourceName.startsWith("disk.")) {
      prefix += "disks/";
    }
    return prefix + resourceBundle.getString(resourceName);
  }

  private URL findResource(String resourceName) {
    return getClass().getResource(makeResourceName(resourceName));
  }

  protected InputStream findResourceAsStream(String resourceName) {
    return getClass().getResourceAsStream(makeResourceName(resourceName));
  }

  public int[] loadBinaryResource(String name, int size) {
    int r[] = new int[size];
    InputStream is = findResourceAsStream(name);
    try {
      int b;
      int idx = 0;
      while ((b = is.read()) >= 0) {
        r[idx++] = b;
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    } finally {
      try {
        is.close();
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
    return r;
  }

  public byte[] loadBytes(String name, int size) {
    int[] ints = loadBinaryResource(name, size);
    byte[] bytes = new byte[size];
    for (int i = 0; i < size; i++) {
      bytes[i] = (byte) ints[i];
    }
    return bytes;
  }
}
