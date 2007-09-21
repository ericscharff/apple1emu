package eric;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ResourceBundle;

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

public class Apple2e extends Canvas implements Runnable, M6502.Memory {
  private static final int TEXTMODE = 1;
  private static final int MIXEDMODE = 2;
  /* Only LORESMODE or HIRESMODE is active at once.  LORESMODE    */
  /* being set does not mean that the display is showing graphics */
  /* But only that LORES is the active mode.                      */
  private static final int LORESMODE = 4;
  private static final int HIRESMODE = 8;
  private static final int VIDEOPAGE1 = 1;
  private static final int VIDEOPAGE2 = 2;
  /* Language card inputs */
  private static final int READ_FROM_ROM = 0;
  private static final int READ_FROM_BANK1 = 1;
  private static final int READ_FROM_BANK2 = 2;
  private static final int WRITE_TO_ROM = 0; /* ignore writes */
  private static final int WRITE_TO_BANK1 = 1;
  private static final int WRITE_TO_BANK2 = 2;
  private int[] mem;
  private int[] auxMem;
  private int videoPage;
  private int lcReadMode;
  private int lcWriteMode;
  private int[] lcRom; /* d000 - ffff */
  private int[] lcBank1; /* d000 - dfff */
  private int[] lcBank2; /* d000 - dfff */
  private int[] lcMainBank; /* e000 - ffff */
  private int[] auxLcBank1;
  private int[] auxLcBank2;
  private int[] auxLcMainBank;
  private boolean shouldSwitch;
  private boolean use80ColumnMapping;
  private boolean readAuxRam;
  private boolean writeAuxRam;
  private boolean readAuxZeroPage;
  private boolean readInternalSlotRom;
  private boolean readExternalSlot3;
  private boolean use80ColumnVideo;
  private boolean useAltCharSet;
  private int[] diskRom;
  private DiskII disk;
  private boolean hitError;
  private M6502 cpu;
  private int lastKey;
  private boolean shiftDown;
  private Image charRom;
  private Image charAltRom;
  private Image charRomInverse;
  private Image charRomFlash;
  private ImageData hiresBuf;
  private Color[] loresColor;
  private int screenMode;
  private ResourceBundle resourceBundle;

  //    private static final int sectorMap[] = {
  //            0x0, 0x7, 0xE, 0x6, 0xD, 0x5, 0xC, 0x4,
  //            0xB, 0x3, 0xA, 0x2, 0x9, 0x1, 0x8, 0xF };

  private void err(String s) {
    hitError = true;
    System.err.println(s);
    System.out.println(cpu.dump());
    System.exit(1);
  }

  private void warn(String s) {
    System.err.println(s);
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

  
  private InputStream findResourceAsStream(String resourceName) {
    return getClass().getResourceAsStream(makeResourceName(resourceName));
  }

  private Image loadImageResource(String resourceName) throws IOException {
    InputStream is = findResourceAsStream(resourceName);
    if (is == null) {
      warn("Could not find resource: " + resourceName);
      return null;
    } else {
      try {
        return new Image(getDisplay(), is);
      } finally {
        is.close();
      }
    }
  }
  
  public Point computeSize(int wHint, int hHint, boolean changed) {
    return new Point(280, 192);
  }

  private void loadRom() {
    try {
      charRom = loadImageResource("image.charset");
      charAltRom = loadImageResource("image.charset.alt");
      charRomInverse = loadImageResource("image.charset");
      charRomFlash = loadImageResource("image.charset");
      InputStream is = findResourceAsStream("rom.bios");
      lcRom = new int[16384];
      int a = 0;
      int b;
      while ((b = is.read()) >= 0) {
        lcRom[a] = b;
        a++;
      }
      is.close();
      is = findResourceAsStream("rom.disk");
      diskRom = new int[256];
      a=0;
      while ((b = is.read()) >= 0) {
        diskRom[a] = b;
        a++;
      }
      is.close();
      // Remove delays from disk rom
      diskRom[0x4c] = 0xa9;
      diskRom[0x4d] = 0x00;
      diskRom[0x4e] = 0xea;
    } catch (Exception e) { e.printStackTrace(); }
  }

  //    private void diskEntryPoint() {
  //            try {
  // //         System.out.println("Booting Disk");
  //            /* Read 256 bytes from disk to $0800, JMP $0801 */
  //            FileInputStream is = new FileInputStream(diskFile);
  //            for (int i=0x800; i < 0x900; i++) {
  //                    write(i, is.read());
  //            }
  //            is.close();
  //            } catch (Exception e) { e.printStackTrace(); }
  //            write(0x26, 0);
  //            write(0x27, 9);
  //            write(0x2b,  6*16);
  //            cpu.jumpTo(0x801);
  //    }
  //
  //    private void loadTrackAndSector() {
  //            int track = read(0x41);
  //            int sector = sectorMap[read(0x3d)];
  //            int startByte = (track * 16 * 256) + (sector * 256);
  //            int where = read(0x27)*256 + read(0x26);
  //            try {
  //            FileInputStream is = new FileInputStream(diskFile);
  //            while (startByte > 0) {
  //                    is.read();
  //                    startByte--;
  //            }
  //            for (int i=where; i < where+256; i++) {
  //                    write(i, is.read());
  //            }
  //            is.close();
  // //         System.out.println("Loaded track " + track + " sector " + sector + " to " + where);
  //            } catch (Exception e) { e.printStackTrace(); }
  //            write(0x26, 0);
  //            write(0x27, read(0x27)+1);
  //            cpu.jumpTo(0x801);
  //    }
  //
  //    private void rwts() {
  //            write(0x48,  cpu.y);
  //            write(0x49, cpu.a);
  //            write(0x6f8, 2);
  //            write(0x4f8, 4);
  //            int iobuf = read(0x49) * 256 + read(0x48);
  //            int command = read(iobuf + 12);
  //            int track = read(iobuf + 4);
  //            int sector = read(iobuf + 5);
  //            int membuf = read(iobuf+9) * 256 + read(iobuf+8);
  //            int memend = membuf + 256;
  //            switch (command) {
  //            case 0:
  //                    System.out.println("rwts: ignoring seek");
  //                    break;
  //            case 1:
  // //                 System.out.println("rtws: track " + track + " sector " + sector);
  //                    try {
  //                            FileInputStream is = new FileInputStream(diskFile);
  //                            int startByte = track * 16 * 256 + sector * 256;
  //                            while (startByte > 0) {
  //                                    is.read();
  //                                    startByte--;
  //                            }
  //                            for (int i=membuf; i < memend; i++) {
  //                                    write(i, is.read());
  //                            }
  //                            is.close();
  //                    } catch (Exception e) { e.printStackTrace(); }
  //                    break;
  //            case 2:
  //                    System.out.println("rwts: ignoring write");
  //                    break;
  //            case 4:
  //                    System.out.println("rwts: ignoring format");
  //                    break;
  //            default:
  //                    err("rwts: unknown command " + command);
  //                    break;
  //            }
  //            cpu.a = 0;
  //            cpu.doCLC(0);
  //            cpu.doRTS(0);
  //    }

  public Apple2e(Composite parent, int style) throws IOException {
    super(parent, style);
    hitError = false;
    disk = new DiskII();
    lcReadMode = READ_FROM_ROM;
    lcWriteMode = WRITE_TO_BANK1;
    videoPage = VIDEOPAGE1;
    mem = new int[49152];
    lcMainBank = new int[8192];
    lcBank1 = new int[4096];
    lcBank2 = new int[4096];
    auxMem = new int[49152];
    auxLcMainBank = new int[8192];
    auxLcBank1 = new int[4096];
    auxLcBank2 = new int[4096];
    shouldSwitch = true;
    use80ColumnMapping = false;
    readAuxRam = false;
    writeAuxRam = false;
    readAuxZeroPage = false;
    readInternalSlotRom = false;
    readExternalSlot3 = false;
    use80ColumnVideo = false;
    useAltCharSet = false;
    screenMode = TEXTMODE;
    resourceBundle = ResourceBundle.getBundle("resources.apple2");
    loresColor = new Color[16];
    Display d = getDisplay();
    Image image = new Image(d, 282, 192);
    hiresBuf = image.getImageData();
    image.dispose();
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
    loadRom();
    cpu = new M6502(this, (lcRom[16380]) + (lcRom[16381]) * 256);

    InputStream is = findResource("disk.drive0").openConnection().getInputStream();
    if (is != null) {
      try {
        disk.diskInsert(0, "Untitled", is, true, false);
      } catch (IOException ex) {
        ex.printStackTrace();
      } finally {
        is.close();
      }
    }
    addListener(SWT.KeyDown, new Listener() {
      public void handleEvent(Event event) {
        lastKey = event.character;
        shiftDown = (event.stateMask & SWT.SHIFT) != 0;
        if (lastKey == '\n') lastKey = 0x0d;
        if (lastKey == 8) lastKey = 0x7f;
        if ((lastKey <= 0) || (lastKey > 0xff)) {
          lastKey = 0;
        } else {
          lastKey |= 0x80;
        }
      }
    });
    addListener(SWT.Paint, new Listener() {
      public void handleEvent(Event event) {
        drawScreen(event.gc);
      }
    });
  }

  private int doIO(int where) {
    if ((readInternalSlotRom && (where >= 0xc100)) ||
        (!readExternalSlot3 && (((where & 0xff00) == 0xc300) || (where >= 0xc800)))) {
      return lcRom[where & 0xfff];
    }
    if ((where >= 0xc600) && (where < 0xc700)) {
      return diskRom[where & 0xff];
    }
    if ((where >= 0xc100) && (where < 0xc800)) {
      return randomData(true);
    }
    if (hitError || cpu.dumping) return 0;
    /* Soft switches */
    switch (where) {
    case 0xc000:
    case 0xc001:
    case 0xc002:
    case 0xc003:
    case 0xc004:
    case 0xc005:
    case 0xc006:
    case 0xc007:
    case 0xc008:
    case 0xc009:
    case 0xc00a:
    case 0xc00b:
    case 0xc00c:
    case 0xc00d:
    case 0xc00e:
    case 0xc00f:
      /* Keyboard input - bit 7 = key is available */
      return lastKey;
    case 0xc010:
      /* Keyboard latch */
      int k = lastKey;
      lastKey &= 0x7f;
      return k;
    case 0xc011:
      return (lcReadMode == READ_FROM_BANK2) ? 0x80 : 0;
    case 0xc012:
      return (lcReadMode != READ_FROM_ROM) ? 0x80 : 0;
    case 0xc013:
      return (readAuxRam) ? 0x80 : 0;
    case 0xc014:
      return (writeAuxRam) ? 0x80 : 0;
    case 0xc015:
      return (readInternalSlotRom) ? 0x80 : 0;
    case 0xc016:
      return (readAuxZeroPage) ? 0x80 : 0;
    case 0xc017:
      return (readExternalSlot3) ? 0x80 : 0;
    case 0xc018:
      return (use80ColumnMapping) ? 0x80 : 0;
    case 0xc019:
      return 0x80;
    case 0xc01a:
      return ((screenMode & TEXTMODE) != 0) ? 0x80 : 0;
    case 0xc01b:
      return ((screenMode & MIXEDMODE) != 0) ? 0x80 : 0;
    case 0xc01c:
      return (videoPage == VIDEOPAGE2) ? 0x80 : 0;
    case 0xc01d:
      return ((screenMode & HIRESMODE) != 0) ? 0x80 : 0;
    case 0xc01e:
      return (useAltCharSet) ? 0x80 : 0;
    case 0xc01f:
      return (use80ColumnVideo) ? 0x80 : 0;
    case 0xc030:
      /* Tweak the speaker */
      return randomData(true);
    case 0xc050:
      /* Turn off text mode */
      screenMode &= ~TEXTMODE;
      // Return VBL?
      return randomData(false);
    case 0xc051:
      /* Turn on text mode */
      screenMode |= TEXTMODE;
      return randomData(true);
    case 0xc052:
      /* Turn off mixed graphics mode */
      screenMode &= ~MIXEDMODE;
      return randomData(true);
    case 0xc053:
      /* Turn on mixed graphics mode (graphics + 4 text lines) */
      screenMode |= MIXEDMODE;
      return randomData(true);
    case 0xc054:
      /* Set page 1 video ram */
      videoPage = VIDEOPAGE1;
      return randomData(true);
    case 0xc055:
      /* Set page 2 video ram */
      videoPage = VIDEOPAGE2;
      return randomData(true);
    case 0xc056:
      /* Set lo-res graphics */
      screenMode &= ~HIRESMODE;
      screenMode |= LORESMODE;
      return randomData(true);
    case 0xc057:
      /* Set hi-res graphics */
      screenMode &= ~LORESMODE;
      screenMode |= HIRESMODE;
      return randomData(true);
    case 0xc058:
      /* Set annunciator 0 (speaker?) to 0 */
      return 0;
    case 0xc05a:
      /* Set annunciator 1 (speaker?) to 0 */
      return 0;
    case 0xc05c:
      /* Set annunciator 2 (speaker?) to 1 */
      return 0;
    case 0xc05d:
      /* Set annunciator 2 (speaker?) to 0 */
      return 0;
    case 0xc05e:
      /* Set annunciator 3 (speaker?) to 0 */
      return 0;
    case 0xc05f:
      /* Set annunciator 3 (speaker?) to 0 */
      return 0;
    case 0xc060:
      /* cassette input and game button 3 */
      return 0;
    case 0xc061:
      /* open apple key */
      return 0;
    case 0xc062:
      /* closed apple key */
      return 0;
    case 0xc063:
      /* "shift key mod" */
      return randomData(shiftDown);
    case 0xc080:
      shouldSwitch = false;
      lcReadMode = READ_FROM_BANK2;
      lcWriteMode = WRITE_TO_ROM;
      return randomData(true);
    case 0xc081:
      lcReadMode = READ_FROM_ROM;
      if (shouldSwitch) {
        lcWriteMode = WRITE_TO_BANK2;
      } else {
        shouldSwitch = true;
      }
      return randomData(true);
    case 0xc082:
      lcReadMode = READ_FROM_ROM;
      lcWriteMode = WRITE_TO_ROM;
      shouldSwitch = false;
      return randomData(true);
    case 0xc083:
      lcReadMode = READ_FROM_BANK2;
      if (shouldSwitch) {
        lcWriteMode = WRITE_TO_BANK2;
      } else {
        shouldSwitch = true;
      }
      return randomData(true);
    case 0xc088:
      shouldSwitch = false;
      lcReadMode = READ_FROM_BANK1;
      lcWriteMode = WRITE_TO_ROM;
      return randomData(true);
    case 0xc089:
      lcReadMode = READ_FROM_ROM;
      if (shouldSwitch) {
        lcWriteMode = WRITE_TO_BANK1;
      } else {
        shouldSwitch = true;
      }
      return randomData(true);
    case 0xc08a:
      lcReadMode = READ_FROM_ROM;
      lcWriteMode = WRITE_TO_ROM;
      shouldSwitch = false;
      return randomData(true);
    case 0xc08b:
      lcReadMode = READ_FROM_BANK1;
      if (shouldSwitch) {
        lcWriteMode = WRITE_TO_BANK1;
      } else {
        shouldSwitch = true;
      }
      return randomData(true);
    case 0xc0e0:
      return disk.diskControlStepper(0xc0e0);
    case 0xc0e1:
      return disk.diskControlStepper(0xc0e1);
    case 0xc0e2:
      return disk.diskControlStepper(0xc0e2);
    case 0xc0e3:
      return disk.diskControlStepper(0xc0e3);
    case 0xc0e4:
      return disk.diskControlStepper(0xc0e4);
    case 0xc0e5:
      return disk.diskControlStepper(0xc0e5);
    case 0xc0e6:
      return disk.diskControlStepper(0xc0e6);
    case 0xc0e7:
      return disk.diskControlStepper(0xc0e7);
    case 0xc0e8:
      return disk.diskControlMotor(0xc0e8);
    case 0xc0e9:
      return disk.diskControlMotor(0xc0e9);
    case 0xc0ea:
      return disk.diskEnable(0xc0ea);
    case 0xc0eb:
      return disk.diskEnable(0xc0eb);
    case 0xc0ec:
      return disk.diskReadWrite();
    case 0xc0ed:
      return disk.diskSetLatchValue(0, false);
    case 0xc0ee:
      return disk.diskSetReadMode();
    case 0xc0ef:
      return disk.diskSetWriteMode();
      //                case 0xc100:
      //                case 0xc200:
      //                case 0xc300:
      //                case 0xc400:
      //                case 0xc500:
      //                case 0xc600:
      //                case 0xc700:
      //                        /* Slot ROM entry points */
      //                        System.out.println("Slot entry point");
      //                        return 96; /* RTS */
    case 0xcfff:
      /* Clear all the slot ROM bank switching */
      return 0;
    case 49194:
    case 0xc036:
    case 0xc070:
    case 0xc0b0:
    case 0xc064:
    case 0xc065:
    case 0xc066:
    case 0xc067:
      return randomData(false);
      // Prodos specific
    case 0xc068:
      return randomData(true);
    }
    err("Read from Unknown I/O Address: " + where);
    return 96;
  }

  private void doIO(int where, int what) {
    switch (where) {
    case 0xc000:
      use80ColumnMapping = false;
      return;
    case 0xc001:
      use80ColumnMapping = true;
      return;
    case 0xc002:
      readAuxRam = false;
      return;
    case 0xc003:
      readAuxRam = true;
      return;
    case 0xc004:
      writeAuxRam = false;
      return;
    case 0xc005:
      writeAuxRam = true;
      return;
    case 0xc006:
      readInternalSlotRom = false;
      return;
    case 0xc007:
      readInternalSlotRom = true;
      return;
    case 0xc008:
      readAuxZeroPage = false;
      return;
    case 0xc009:
      readAuxZeroPage = true;
      return;
    case 0xc00a:
      readExternalSlot3 = false;
      return;
    case 0xc00b:
      readExternalSlot3 = true;
      return;
    case 0xc00c:
      use80ColumnVideo = false;
      return;
    case 0xc00d:
      use80ColumnVideo = true;
      return;
    case 0xc00e:
      useAltCharSet = false;
      return;
    case 0xc00f:
      useAltCharSet = true;
      return;
      // Used by Merlin
    case 0xc073:
      return;
      // Used by Ultima V
    case 0xc074:
      return;
    case 0xc0ed:
      disk.diskSetLatchValue(what, true);
      return;
    }
    /* Most soft switches are accessed via reads, but some are accessed */
    /* through writes.  Though strictly not compatible with a real      */
    /* Apple, we attempt to route write requests we don't understand to */
    /* the read routine */
    doIO(where);
  }

  public int read(int where) {
    if (where >= 0xe000) {
      if (lcReadMode != READ_FROM_ROM) {
        if (readAuxZeroPage) {
          return auxLcMainBank[where - 0xe000];
        } else {
          return lcMainBank[where - 0xe000];
        }
      } else {
        return lcRom[where - 0xc000];
      }
    }
    if (where >= 0xd000) {
      switch (lcReadMode) {
      case READ_FROM_ROM:
        return lcRom[where - 0xc000];
      case READ_FROM_BANK1:
        if (readAuxZeroPage) {
          return auxLcBank1[where - 0xd000];
        } else {
          return lcBank1[where - 0xd000];
        }
      case READ_FROM_BANK2:
        if (readAuxZeroPage) {
          return auxLcBank2[where - 0xd000];
        } else {
          return lcBank2[where - 0xd000];
        }
      }
    }
    if (use80ColumnMapping) {
      if ((where >= 0x400) && (where <= 0x7ff)) {
        if (videoPage == VIDEOPAGE1) {
          return mem[where];
        } else {
          return auxMem[where];
        }
      }
      if (((screenMode & HIRESMODE) != 0) && (where >= 0x2000) && (where <= 0x3fff)) {
        if (videoPage == VIDEOPAGE1) {
          return mem[where];
        } else {
          return auxMem[where];
        }
      }
    }
    if ((where >= 0xc000) && (where < 0xd000)) return doIO(where);
    if ((where < 0) || (where > 65536)) err("Read out of range: " + where);
    if (where < 0x200) {
      if (readAuxZeroPage) {
        return auxMem[where];
      } else {
        return mem[where];
      }
    }
    if (readAuxRam) {
      return auxMem[where];
    } else {
      return mem[where];
    }
  }

  public void write(int where, int what) {
    if (where >= 0xe000) {
      if (lcWriteMode != WRITE_TO_ROM) {
        if (readAuxZeroPage) {
          auxLcMainBank[where - 0xe000] = what;
        } else {
          lcMainBank[where - 0xe000] = what;
        }
      }
      return;
    }
    if (where >= 0xd000) {
      switch (lcWriteMode) {
      case WRITE_TO_ROM:
        break;
      case WRITE_TO_BANK1:
        if (readAuxZeroPage) {
          auxLcBank1[where - 0xd000] = what;
        } else {
          lcBank1[where - 0xd000] = what;
        }
        break;
      case WRITE_TO_BANK2:
        if (readAuxZeroPage) {
          auxLcBank2[where - 0xd000] = what;
        } else {
          lcBank2[where - 0xd000] = what;
        }
        break;
      }
      return;
    }
    if (where >= 0xc000) { doIO(where, what); return; }
    if (use80ColumnMapping) {
      if ((where >= 0x400) && (where <= 0x7ff)) {
        if (videoPage == VIDEOPAGE1) {
          mem[where] = what;
        } else {
          auxMem[where] = what;
        }
        return;
      }
      if (((screenMode & HIRESMODE) != 0) && (where >= 0x2000) && (where <= 0x3fff)) {
        if (videoPage == VIDEOPAGE1) {
          mem[where] = what;
        } else {
          auxMem[where] = what;
        }
        return;
      }
    }
    if ((where < 0) || (where > 65536)) err("Write out of range: " + where);
    if ((what < 0) || (what > 256)) err("Write value out of range: " + what);
    if (where < 0x200) {
      if (readAuxZeroPage) {
        auxMem[where] = what;
      } else {
        mem[where] = what;
      }
      return;
    }
    if (writeAuxRam) {
      auxMem[where] = what;
    } else {
      mem[where] = what;
    }
  }

  public void run() {
    int clock = 0;
    long startTime = System.currentTimeMillis();

    if (isDisposed()) return;
    while (!cpu.halt && (clock < 10000)) {
      cpu.step();
      clock++;
      //                      if (cpu.bFlagSet()) { err("Ack!  BRK mode!"); }
    }
    if (cpu.halt) {
      System.out.println("halted");
      System.out.println(cpu.dump());
      System.exit(0);
    } else {
      redraw();
      int napTime = Math.max(1, (int)(100 - System.currentTimeMillis() + startTime));
      getDisplay().timerExec(napTime, this);
    }
  }

  public void text80Mode(GC gc) {
    int b;
    int page;
    int baseAddr;
    Image src = null;

    /* It appears you cannot use video page 2 in 80 column mode */
    //          page = (videoPage == VIDEOPAGE1) ? 0x400 : 0x800;
    page = 0x400;
    for (int row=0; row < graphicsOffset.length; row++) {
      baseAddr = graphicsOffset[row] & 0xfff | page;
      for (int col=0; col < 40; col++) {
        b = mem[baseAddr + col];
        if (b < 64) {
          src = charRomInverse;
        } else if (b < 128) {
          src = charRomFlash;
        } else {
          src = charRom;
        }
        if (useAltCharSet) {
          src = charAltRom;
        }
        gc.drawImage(src,
            b*8,          /* src x origin  */
            0,            /* src y origin  */
            7,            /* src width     */
            8,            /* src height    */
            col * 14 + 7, /* dest x origin */
            row * 8,      /* dest y origin */
            7,            /* dest width    */
            8);           /* dest height   */
        b = auxMem[baseAddr + col];
        if (b < 64) {
          src = charRomInverse;
        } else if (b < 128) {
          src = charRomFlash;
        } else {
          src = charRom;
        }
        if (useAltCharSet) {
          src = charAltRom;
        }
        gc.drawImage(src, b*8, 0, 7, 8, col * 14, row * 8, 7, 8);
      }
    }
  }

  public void textMode(GC gc) {
    int b;
    int page;
    int baseAddr;
    Image src = null;

    if (use80ColumnVideo) {
      text80Mode(gc);
      return;
    }
    page = (videoPage == VIDEOPAGE1) ? 0x400 : 0x800;
    for (int row=0; row < graphicsOffset.length; row++) {
      baseAddr = graphicsOffset[row] & 0xfff | page;
      for (int col=0; col < 40; col++) {
        b = mem[baseAddr + col];
        if (b < 64) {
          src = charRomInverse;
        } else if (b < 128) {
          src = charRomFlash;
        } else {
          src = charRom;
        }
        if (useAltCharSet) {
          src = charAltRom;
        }
        gc.drawImage(src,
            b*8,   /* src x origin  */
            0,     /* src y origin  */
            7,     /* src width     */
            8,     /* src height    */
            col*7, /* dest x origin */
            row*8, /* dest y origin */
            7,     /* dest width    */
            8);    /* dest height   */
      }
    }
  }

  public void loresMode(GC gc) {
    int b;
    int page;
    int baseAddr;

    page = (videoPage == VIDEOPAGE1) ? 0x400 : 0x800;
    for (int row=0; row < graphicsOffset.length; row++) {
      baseAddr = graphicsOffset[row] & 0xfff | page;
      for (int col=0; col < 40; col++) {
        b = mem[baseAddr + col];
        gc.setBackground(loresColor[b&15]);
        gc.fillRectangle(col*7, row*8, 7, 4);
        gc.setBackground(loresColor[(b >> 4) & 15]);
        gc.fillRectangle(col*7, row*8+4, 7, 4);
      }
    }
  }

  private static final int graphicsOffset[] = new int[] {
    0x2000, 0x2080, 0x2100, 0x2180, 0x2200, 0x2280, 0x2300, 0x2380,
    0x2028, 0x20a8, 0x2128, 0x21a8, 0x2228, 0x22a8, 0x2328, 0x23a8,
    0x2050, 0x20d0, 0x2150, 0x21d0, 0x2250, 0x22d0, 0x2350, 0x23d0,
  };

  private static final boolean useColor = true;
  public void hiresMode(GC gc) {
    int x=0;
    int y=0;
    int baseAddr;
    int k;
    int page;
    boolean bitShift;
    boolean evenBit = true;
    boolean lastBitSet = false;
    page = (videoPage == VIDEOPAGE1) ? 0 : 0x2000;
    for (int segment=0; segment < graphicsOffset.length; segment++) {
      baseAddr = graphicsOffset[segment] + page;
      for (int i=0; i < 8; i++) {
        x=0;
        evenBit = true;
        for (int j=0; j < 40; j++) {
          int curByte = mem[baseAddr+j];
          bitShift = (curByte & 0x80) != 0;
          for (int bit=0; bit < 7; bit++) {
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
                hiresBuf.setPixel(x+1, y, k);
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
    if ((screenMode & TEXTMODE) != 0) {
      textMode(gc);
    } else {
      if ((screenMode & LORESMODE) != 0) {
        loresMode(gc);
      }
      if ((screenMode & HIRESMODE) != 0) {
        hiresMode(gc);
      }
      if ((screenMode & MIXEDMODE) != 0) {
        gc.setClipping(0, 20*8, 280, 192);
        textMode(gc);
      }
    }
  }

  public static void main(String args[]) {
    try {
      Display display = new Display();
      Shell shell = new Shell(display);
      shell.setLayout(new FillLayout(SWT.VERTICAL));
      shell.setText("Apple //e");
      Apple2e me = new Apple2e(shell, SWT.NONE);
      shell.pack();
      shell.open();
      display.timerExec(100, me);
      while (!shell.isDisposed()) {
        if (!display.readAndDispatch()) {
          display.sleep();
        }
      }
      display.dispose();
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  private static final java.util.Random rand = new java.util.Random();
  private static final byte randomBytes[] =
  {0x00,0x2D,0x2D,0x30,0x30,0x32,0x32,0x34,
   0x35,0x39,0x43,0x43,0x43,0x60,0x7F,0x7F};
  public static int randomData(boolean highBit) {
    int r = rand.nextInt() & 0xff;
    if (r <= 170)
      return 0x20 | (highBit ? 0x80 : 0);
    else
      return randomBytes[r & 15] | (highBit ? 0x80 : 0);
  }
}
