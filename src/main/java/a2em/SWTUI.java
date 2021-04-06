package a2em;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

public class SWTUI extends Canvas implements Runnable {
  private Image charRom;
  private Image charAltRom;
  private Image charRomInverse;
  private Image charRomFlash;
  private ImageData hiresBuf;
  private Color[] loresColor;
  private Apple2e a2;

  public SWTUI(Composite parent, int style, Apple2e apple) {
    super(parent, style);
    a2 = apple;
    Display d = getDisplay();
    ResourceHelper helper = new ResourceHelper();
    charRom = helper.loadImageResource(d, "image.charset");
    charAltRom = helper.loadImageResource(d, "image.charset.alt");
    charRomInverse = helper.loadImageResource(d, "image.charset");
    charRomFlash = helper.loadImageResource(d, "image.charset");

    loresColor = new Color[16];
    loresColor[0] = new Color(d, 0, 0, 0);
    loresColor[1] = new Color(d, 0xcc, 0, 0x33);
    loresColor[2] = new Color(d, 0, 0, 0x99);
    loresColor[3] = new Color(d, 0xcc, 0, 0xff);
    loresColor[4] = new Color(d, 0, 0x66, 0);
    loresColor[5] = new Color(d, 0x66, 0x66, 0x66);
    loresColor[6] = new Color(d, 0, 0x66, 0xcc);
    loresColor[7] = new Color(d, 0, 0xcc, 0xff);
    loresColor[8] = new Color(d, 0x66, 0x33, 0);
    loresColor[9] = new Color(d, 0xff, 0x66, 0);
    loresColor[10] = new Color(d, 0x66, 0x66, 0x66);
    loresColor[11] = new Color(d, 0xff, 0x99, 0xff);
    loresColor[12] = new Color(d, 0, 0xff, 0);
    loresColor[13] = new Color(d, 0xff, 0xff, 0);
    loresColor[14] = new Color(d, 0x33, 0xff, 0x99);
    loresColor[15] = new Color(d, 0xff, 0xff, 0xff);

    Image image = new Image(d, 282, 192);
    hiresBuf = image.getImageData();
    image.dispose();

    addListener(
        SWT.KeyDown,
        new Listener() {
          public void handleEvent(Event event) {
            int lastKey = event.character;
            boolean shiftDown = (event.stateMask & SWT.SHIFT) != 0;
            if (lastKey == '\n') lastKey = 0x0d;
            if (lastKey == 8) lastKey = 0x7f;
            if ((lastKey <= 0) || (lastKey > 0xff)) {
              lastKey = 0;
            } else {
              lastKey |= 0x80;
            }
            a2.lastKey = lastKey;
            a2.shiftDown = shiftDown;
          }
        });
    addListener(
        SWT.Paint,
        new Listener() {
          public void handleEvent(Event event) {
            drawScreen(event.gc);
          }
        });
  }

  public Point computeSize(int wHint, int hHint, boolean changed) {
    return new Point(280, 192);
  }

  private void text80Mode(GC gc) {
    int b;
    int page;
    int baseAddr;
    Image src = null;

    page = 0x400;
    for (int row = 0; row < graphicsOffset.length; row++) {
      baseAddr = graphicsOffset[row] & 0xfff | page;
      for (int col = 0; col < 40; col++) {
        b = a2.mem[baseAddr + col];
        if (b < 64) {
          src = charRomInverse;
        } else if (b < 128) {
          src = charRomFlash;
        } else {
          src = charRom;
        }
        if (a2.useAltCharSet) {
          src = charAltRom;
        }
        gc.drawImage(
            src,
            b * 8, /* src x origin  */
            0, /* src y origin  */
            7, /* src width     */
            8, /* src height    */
            col * 14 + 7, /* dest x origin */
            row * 8, /* dest y origin */
            7, /* dest width    */
            8); /* dest height   */
        b = a2.auxMem[baseAddr + col];
        if (b < 64) {
          src = charRomInverse;
        } else if (b < 128) {
          src = charRomFlash;
        } else {
          src = charRom;
        }
        if (a2.useAltCharSet) {
          src = charAltRom;
        }
        gc.drawImage(src, b * 8, 0, 7, 8, col * 14, row * 8, 7, 8);
      }
    }
  }

  private void textMode(GC gc) {
    int b;
    int page;
    int baseAddr;
    Image src = null;

    if (a2.use80ColumnVideo) {
      text80Mode(gc);
      return;
    }
    page = (a2.videoPage == Apple2e.VIDEOPAGE1) ? 0x400 : 0x800;
    for (int row = 0; row < graphicsOffset.length; row++) {
      baseAddr = graphicsOffset[row] & 0xfff | page;
      for (int col = 0; col < 40; col++) {
        b = a2.mem[baseAddr + col];
        if (b < 64) {
          src = charRomInverse;
        } else if (b < 128) {
          src = charRomFlash;
        } else {
          src = charRom;
        }
        if (a2.useAltCharSet) {
          src = charAltRom;
        }
        gc.drawImage(
            src, b * 8, /* src x origin  */ 0, /* src y origin  */ 7, /* src width     */
            8, /* src height    */ col * 7, /* dest x origin */ row * 8, /* dest y origin */
            7, /* dest width    */ 8); /* dest height   */
      }
    }
  }

  private void loresMode(GC gc) {
    int b;
    int page;
    int baseAddr;

    page = (a2.videoPage == Apple2e.VIDEOPAGE1) ? 0x400 : 0x800;
    for (int row = 0; row < graphicsOffset.length; row++) {
      baseAddr = graphicsOffset[row] & 0xfff | page;
      for (int col = 0; col < 40; col++) {
        b = a2.mem[baseAddr + col];
        gc.setBackground(loresColor[b & 15]);
        gc.fillRectangle(col * 7, row * 8, 7, 4);
        gc.setBackground(loresColor[(b >> 4) & 15]);
        gc.fillRectangle(col * 7, row * 8 + 4, 7, 4);
      }
    }
  }

  private static final int graphicsOffset[] =
      new int[] {
        0x2000, 0x2080, 0x2100, 0x2180, 0x2200, 0x2280, 0x2300, 0x2380,
        0x2028, 0x20a8, 0x2128, 0x21a8, 0x2228, 0x22a8, 0x2328, 0x23a8,
        0x2050, 0x20d0, 0x2150, 0x21d0, 0x2250, 0x22d0, 0x2350, 0x23d0,
      };

  private static final boolean useColor = true;

  private void hiresMode(GC gc) {
    int x = 0;
    int y = 0;
    int baseAddr;
    int k;
    int page;
    boolean bitShift;
    boolean evenBit = true;
    boolean lastBitSet = false;
    page = (a2.videoPage == Apple2e.VIDEOPAGE1) ? 0 : 0x2000;
    for (int segment = 0; segment < graphicsOffset.length; segment++) {
      baseAddr = graphicsOffset[segment] + page;
      for (int i = 0; i < 8; i++) {
        x = 0;
        evenBit = true;
        for (int j = 0; j < 40; j++) {
          int curByte = a2.mem[baseAddr + j];
          bitShift = (curByte & 0x80) != 0;
          for (int bit = 0; bit < 7; bit++) {
            if (useColor) {
              if ((curByte & 0x1) != 0) {
                if (lastBitSet) {
                  k = 0xffffffff;
                } else {
                  if (evenBit) {
                    if (bitShift) {
                      k = 0xff0000ff;
                    } else {
                      k = 0xffff00ff;
                    }
                  } else {
                    if (bitShift) {
                      k = 0xffff0000;
                    } else {
                      k = 0xff00ff00;
                    }
                  }
                }
                hiresBuf.setPixel(x + 1, y, k);
                hiresBuf.setPixel(x, y, k);
                lastBitSet = true;
              } else {
                if (!lastBitSet) {
                  hiresBuf.setPixel(x, y, 0xff000000);
                }
                lastBitSet = false;
              }
            } else {
              if ((curByte & 0x1) != 0) {
                hiresBuf.setPixel(x, y, 0xffffffff);
              } else {
                hiresBuf.setPixel(x, y, 0xff000000);
              }
            }
            curByte >>= 1;
            x++;
            evenBit = !evenBit;
          }
        }
        lastBitSet = false;
        baseAddr += 1024;
        y++;
      }
    }
    Image image = new Image(getDisplay(), hiresBuf);
    gc.drawImage(image, 0, 0);
    image.dispose();
  }

  public void drawScreen(GC gc) {
    if ((a2.screenMode & Apple2e.TEXTMODE) != 0) {
      textMode(gc);
    } else {
      if ((a2.screenMode & Apple2e.LORESMODE) != 0) {
        loresMode(gc);
      }
      if ((a2.screenMode & Apple2e.HIRESMODE) != 0) {
        hiresMode(gc);
      }
      if ((a2.screenMode & Apple2e.MIXEDMODE) != 0) {
        gc.setClipping(0, 20 * 8, 280, 192);
        textMode(gc);
      }
    }
  }

  public void run() {
    long startTime = System.currentTimeMillis();

    a2.run();
    redraw();
    int napTime = Math.max(1, (int) (a2.emuPeriod - System.currentTimeMillis() + startTime));
    getDisplay().timerExec(napTime, this);
  }

  public static void main(String args[]) {
    Display display = new Display();
    Shell shell = new Shell(display);
    shell.setLayout(new FillLayout(SWT.VERTICAL));
    shell.setText("Apple //e");
    SWTUI me = new SWTUI(shell, SWT.NONE, new Apple2e());
    shell.pack();
    shell.open();
    display.timerExec(100, me);
    while (!shell.isDisposed()) {
      if (!display.readAndDispatch()) {
        display.sleep();
      }
    }
    display.dispose();
  }
}
