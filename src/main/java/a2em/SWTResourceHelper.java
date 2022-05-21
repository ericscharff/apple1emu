/* Copyright (c) 2007-2022, Eric Scharff
   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.
   There is NO WARRANTY for this software.  See LICENSE.txt for
   details. */

package a2em;

import java.io.InputStream;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

public class SWTResourceHelper extends ResourceHelper {
  public Image loadImageResource(Display display, String resourceName) {
    InputStream is = findResourceAsStream(resourceName);
    if (is == null) {
      warn("Could not find resource: " + resourceName);
      return null;
    } else {
      try {
        return new Image(display, is);
      } finally {
        try {
          is.close();
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
    }
  }
}
