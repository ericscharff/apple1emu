/* Copyright (c) 2007-2022, Eric Scharff
   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.
   There is NO WARRANTY for this software.  See license.txt for
   details. */

package a2em;

import java.util.Random;

public class DiskII {
  private static final int DRIVES = 2;
  private static final int TRACKS = 35;
  private static final int NIBBLES_PER_TRACK = 0x1a00;
  private static final int NIBBLES = 6656;

  private static final int DRIVE_1 = 0;
  private static final int DRIVE_2 = 1;
  private static final byte sectorNumber[][] = {
    {
      0x00, 0x08, 0x01, 0x09, 0x02, 0x0A, 0x03, 0x0B,
      0x04, 0x0C, 0x05, 0x0D, 0x06, 0x0E, 0x07, 0x0F
    },
    {
      0x00, 0x07, 0x0E, 0x06, 0x0D, 0x05, 0x0C, 0x04,
      0x0B, 0x03, 0x0A, 0x02, 0x09, 0x01, 0x08, 0x0F
    },
    {
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    }
  };
  private static final short diskByte[] = {
    0x96, 0x97, 0x9A, 0x9B, 0x9D, 0x9E, 0x9F, 0xA6,
    0xA7, 0xAB, 0xAC, 0xAD, 0xAE, 0xAF, 0xB2, 0xB3,
    0xB4, 0xB5, 0xB6, 0xB7, 0xB9, 0xBA, 0xBB, 0xBC,
    0xBD, 0xBE, 0xBF, 0xCB, 0xCD, 0xCE, 0xCF, 0xD3,
    0xD6, 0xD7, 0xD9, 0xDA, 0xDB, 0xDC, 0xDD, 0xDE,
    0xDF, 0xE5, 0xE6, 0xE7, 0xE9, 0xEA, 0xEB, 0xEC,
    0xED, 0xEE, 0xEF, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6,
    0xF7, 0xF9, 0xFA, 0xFB, 0xFC, 0xFD, 0xFE, 0xFF
  };

  private abstract class DiskImage {
    protected boolean[] validTrack;
    protected byte[] workBuffer;

    public DiskImage() {
      workBuffer = new byte[8192];
    }

    private byte code44a(int a) {
      return (byte) (0xff & (((a >> 1) & 0x55) | 0xaa));
    }

    private byte code44b(int a) {
      return (byte) (0xff & ((a & 0x55) | 0xaa));
    }

    private int addValue(int value, int a) {
      return (value << 2) | ((a & 1) << 1) | ((a & 2) >> 1);
    }

    private void code62(int sector) {
      {
        // Convert 256 8-bit bytes into 342 6-bit bytes, storing it into
        // the work buffer starting at index 4096. (low 4096 is the data itself)
        int sectorBase = sector << 8;
        int resultOff = 0x1000;
        int offset = 0xac;

        while (offset != 0x02) {
          int value = 0;
          value = addValue(value, workBuffer[sectorBase + offset]);
          offset -= 0x56;
          offset &= 0xff;
          value = addValue(value, workBuffer[sectorBase + offset]);
          offset -= 0x56;
          offset &= 0xff;
          value = addValue(value, workBuffer[sectorBase + offset]);
          offset -= 0x53;
          offset &= 0xff;
          workBuffer[resultOff++] = (byte) (value << 2);
        }
        workBuffer[resultOff - 2] &= 0x3f;
        workBuffer[resultOff - 1] &= 0x3f;
        int loop = 0;
        while (loop < 0x100) {
          workBuffer[resultOff++] = workBuffer[sectorBase + loop++];
        }
      }
      {
        // XOR the entire data block with itself offset by one byte
        // creating a 343rd byte which is used as a checksum.  store the
        // new block of 343 bytes starting at 5k into the work buffer
        int savedVal = 0;
        int sourcePtr = 0x1000;
        int resultPtr = 0x1400;
        int loop = 342;

        while (loop-- != 0) {
          workBuffer[resultPtr++] = (byte) (0xff & (savedVal ^ workBuffer[sourcePtr]));
          savedVal = workBuffer[sourcePtr++] & 0xff;
        }
        workBuffer[resultPtr] = (byte) (savedVal & 0xff);
      }
      {
        // using a lookup table, convert the 6-bit bytes into disk bytes.  A
        // valid disk byte is a byte that has the high bit set, at least two
        // adjacent bits set (excluding the high bit), and at most one pair of
        // consecutive zero bits.  The converted block of 343 bytes is stored
        // starting at 4k into the work buffer
        int sourcePtr = 0x1400;
        int resultPtr = 0x1000;
        int loop = 343;
        while (loop-- != 0)
          workBuffer[resultPtr++] = (byte) diskByte[(workBuffer[sourcePtr++] & 0xff) >> 2];
      }
    }

    protected int nibblizeTrack(byte[] trackImageBuffer, int dosOrder, int track) {
      /* zero 4K of the work buffer, starting 4K into it */
      for (int i = 0x1000; i < 0x2000; i++) {
        workBuffer[i] = 0;
      }
      int where = 0;
      byte sector = 0;
      int loop;

      // Write 48 sync bytes
      for (loop = 0; loop < 48; loop++) {
        trackImageBuffer[where++] = (byte) 0xff;
      }

      while (sector < 16) {
        // Write address field
        // - prologue (D5AA96)
        // - Volume number ("4 AND 4" encoded)
        // - Track Number ("4 AND 4" encoded)
        // - Sector number ("4 AND 4" encoded)
        // - Checksum ("4 AND 4" encoded)
        // - Epilog (DEAAEB)
        trackImageBuffer[where++] = (byte) 0xd5;
        trackImageBuffer[where++] = (byte) 0xaa;
        trackImageBuffer[where++] = (byte) 0x96;
        trackImageBuffer[where++] = (byte) 0xff;
        trackImageBuffer[where++] = (byte) 0xfe;
        trackImageBuffer[where++] = code44a(track);
        trackImageBuffer[where++] = code44b(track);
        trackImageBuffer[where++] = code44a(sector);
        trackImageBuffer[where++] = code44b(sector);
        trackImageBuffer[where++] = code44a(0xfe ^ track ^ sector);
        trackImageBuffer[where++] = code44b(0xfe ^ track ^ sector);
        trackImageBuffer[where++] = (byte) 0xde;
        trackImageBuffer[where++] = (byte) 0xaa;
        trackImageBuffer[where++] = (byte) 0xeb;
        // Write the second gap, 6 sync bytes
        for (loop = 0; loop < 6; loop++) {
          trackImageBuffer[where++] = (byte) 0xff;
        }
        // Write the data filed
        // - prologus (D5AAAD)
        // - 343 6-bit bytes of nibblized data, including a 6 bit checksum
        // - epilogue (DEAAEB)
        trackImageBuffer[where++] = (byte) 0xd5;
        trackImageBuffer[where++] = (byte) 0xaa;
        trackImageBuffer[where++] = (byte) 0xad;
        code62(sectorNumber[dosOrder][sector]);
        for (loop = 0; loop < 343; loop++) {
          trackImageBuffer[where++] = workBuffer[4096 + loop];
        }
        trackImageBuffer[where++] = (byte) 0xde;
        trackImageBuffer[where++] = (byte) 0xaa;
        trackImageBuffer[where++] = (byte) 0xeb;

        // Write the third gap, 27 sync bytes
        for (loop = 0; loop < 27; loop++) {
          trackImageBuffer[where++] = (byte) 0xff;
        }
        sector++;
      }
      return where;
    }

    private byte[] sixBitByte;

    private void decode62(int imagePtr) {
      // make a decode table
      if (sixBitByte == null) {
        sixBitByte = new byte[0x80];
        int loop = 0;
        while (loop < 0x40) {
          sixBitByte[0xff & (diskByte[loop] - 0x80)] = (byte) (loop << 2);
          loop++;
        }
      }
      // Use the table to turn disk bytes back into 6-bit bytes
      {
        int sourcePtr = 0x1000;
        int resultPtr = 0x1400;
        int loop = 343;

        while (loop-- != 0) {
          workBuffer[resultPtr++] = sixBitByte[workBuffer[sourcePtr++] & 0x7f];
        }
      }
      // XOR the entire data block with itself offset by one byte
      // to undo the effects of checksumming
      {
        byte savedVal = 0;
        int sourcePtr = 0x1400;
        int resultPtr = 0x1000;
        int loop = 342;

        while (loop-- != 0) {
          workBuffer[resultPtr] = (byte) (savedVal ^ workBuffer[sourcePtr++]);
          savedVal = workBuffer[resultPtr++];
        }
      }
      // Convert the 342 6-bit bytes into 256 8-bit bytes
      {
        int lowBitsPtr = 0x1000;
        int sectorBase = 0x1056;
        int offset = 0xac;

        while (offset != 0x02) {
          if (offset >= 0xac) {
            workBuffer[imagePtr + offset] =
                (byte)
                    ((workBuffer[sectorBase + offset] & 0xfc)
                        | ((workBuffer[lowBitsPtr] & 0x80) >> 7)
                        | ((workBuffer[lowBitsPtr] & 0x40) >> 5));
          }
          offset -= 0x56;
          offset &= 0xff;
          workBuffer[imagePtr + offset] =
              (byte)
                  ((workBuffer[sectorBase + offset] & 0xfc)
                      | ((workBuffer[lowBitsPtr] & 0x20) >> 5)
                      | ((workBuffer[lowBitsPtr] & 0x10) >> 3));
          offset -= 0x56;
          offset &= 0xff;
          workBuffer[imagePtr + offset] =
              (byte)
                  ((workBuffer[sectorBase + offset] & 0xfc)
                      | ((workBuffer[lowBitsPtr] & 0x08) >> 3)
                      | ((workBuffer[lowBitsPtr] & 0x04) >> 1));
          offset -= 0x53;
          offset &= 0xff;
          lowBitsPtr++;
        }
      }
    }

    protected void denibblizeTrack(byte[] trackImage, int dosOrder, int nibbles) {
      // search through the track image for each sector.  For every sector
      // we find, copy the nibblized data for that sector into the work
      // buffer at offset 4k.  Then call decode62() to denibblize the data
      // in the buffer and write it into the first part of the work buffer
      // offset by the sector number
      for (int i = 0; i < 4096; i++) {
        workBuffer[i] = 0;
      }
      int offset = 0;
      int partsLeft = 33;
      int sector = 0;
      while (partsLeft-- != 0) {
        byte[] byteVal = new byte[] {0, 0, 0};
        int byteNum = 0;
        int loop = nibbles;
        while ((loop-- != 0) && (byteNum < 3)) {
          if (byteNum != 0) {
            byteVal[byteNum++] = trackImage[offset++];
          } else if ((trackImage[offset++] & 0xff) == 0xd5) {
            byteNum = 1;
          }
          if (offset >= nibbles) {
            offset = 0;
          }
        }
        if ((byteNum == 3) && ((byteVal[1] & 0xff) == 0xaa)) {
          // found a sector
          int loop2 = 0;
          int tempOffset = offset;
          while (loop2 < 384) {
            workBuffer[0x1000 + loop2++] = trackImage[tempOffset++];
            if (tempOffset >= nibbles) tempOffset = 0;
          }
          if ((byteVal[2] & 0xff) == 0x96) {
            sector = ((workBuffer[0x1004] & 0x55) << 1) | (workBuffer[0x1005] & 0x55);
          } else if ((byteVal[2] & 0xff) == 0xad) {
            decode62(sectorNumber[dosOrder][sector] << 8);
            sector = 0;
          }
        }
      }
    }

    public abstract boolean canRead();

    public abstract void read(int track, int quarterTrack, byte[] trackImageBuffer, int[] nibbles);

    public abstract void write(int track, int quarterTrack, byte[] trackImageBuffer, int[] nibbles);

    public void close() {}
  }

  private class Floppy {
    private String fullName;
    private DiskImage diskData;
    private int track;
    private byte[] trackImage;
    private int phase;
    private int theByte;
    private boolean writeProtected;
    private boolean trackImageData;
    private boolean trackImageDirty;
    private int spinning;
    private int writeLight;
    private int[] nibbles;
  }

  private int curDrive = 0;
  private boolean diskAccessed = false;
  private boolean enhancedDisk = true;
  private Floppy[] floppy;
  private byte floppyLatch = 0;
  private boolean floppyMotorOn = false;
  private boolean floppyWriteMode = false;
  private static Random rand = new Random();

  public DiskII() {
    floppy = new Floppy[DRIVES];
    for (int i = 0; i < DRIVES; i++) {
      floppy[i] = new Floppy();
    }
  }

  public boolean isMotorOff() {
    return !floppyMotorOn;
  }

  private void checkSpinning() {
    boolean modeChange = floppyMotorOn && (floppy[curDrive].spinning == 0);
    if (floppyMotorOn) {
      floppy[curDrive].spinning = 20000;
    }
    if (modeChange) {
      // Update LEDs
    }
  }

  private void allocTrack(int drive) {
    Floppy f = floppy[drive];
    f.trackImage = new byte[NIBBLES_PER_TRACK];
  }

  private void readTrack(int drive) {
    Floppy f = floppy[drive];
    if (f.track >= TRACKS) {
      f.trackImageData = false;
      return;
    }
    if (f.trackImage == null) {
      allocTrack(drive);
    }
    if ((f.trackImage != null) && (f.diskData != null)) {
      imageReadTrack(f.diskData, f.track, f.phase, f.trackImage, f.nibbles);
      f.theByte = 0;
      f.trackImageData = (f.nibbles[0] != 0);
    }
  }

  public void removeDisk(int drive) {
    Floppy f = floppy[drive];
    if (f.diskData != null) {
      if ((f.trackImage != null) && f.trackImageDirty) {
        writeTrack(drive);
      }
      f.diskData.close();
      f.diskData = null;
    }
    f.trackImage = null;
    f.trackImageData = false;
  }

  private void writeTrack(int drive) {
    Floppy f = floppy[drive];
    if (f.track >= TRACKS) return;
    if ((f.trackImage != null) && (f.diskData != null) && (!f.writeProtected))
      imageWriteTrack(f.diskData, f.track, f.phase, f.trackImage, f.nibbles);
    f.trackImageDirty = false;
  }

  /* =========== */
  private void imageReadTrack(
      DiskImage image, int track, int quarterTrack, byte[] trackImageBuffer, int[] nibbles) {
    if (image.canRead() && image.validTrack[track]) {
      image.read(track, quarterTrack, trackImageBuffer, nibbles);
    } else {
      for (nibbles[0] = 0; nibbles[0] < NIBBLES; nibbles[0]++) {
        trackImageBuffer[nibbles[0]] = (byte) rand.nextInt(8);
      }
    }
  }

  private void imageWriteTrack(
      DiskImage image, int track, int quarterTrack, byte[] trackImageBuffer, int[] nibbles) {
    image.write(track, quarterTrack, trackImageBuffer, nibbles);
  }

  private class DosOrderImage extends DiskImage {
    private byte[] myData;
    private static final int DISK_SIZE = 144 * 1024;
    private boolean needsSave;

    public DosOrderImage(byte[] data) {
      needsSave = false;
      myData = data;
      validTrack = new boolean[TRACKS];
      for (int offset = 0; offset < TRACKS; offset++) {
        validTrack[offset] = true;
      }
    }

    public boolean canRead() {
      return true;
    }

    public void read(int track, int quarterTrack, byte[] trackImageBuffer, int[] nibbles) {
      int seekLoc = track << 12; /* 4K per DOS track (16 sectors of 256 bytes) */
      /* read 4K from file starting at offset into workBuffer */
      for (int i = 0; i < 4096; i++) {
        workBuffer[i] = myData[seekLoc++];
      }
      nibbles[0] = nibblizeTrack(trackImageBuffer, 1, track);
    }

    public void write(int track, int quarterTrack, byte[] trackImageBuffer, int[] nibbles) {
      for (int i = 0; i < 4096; i++) {
        workBuffer[i] = 0;
      }
      denibblizeTrack(trackImageBuffer, 1, nibbles[0]);
      int seekLoc = track << 12;
      for (int i = 0; i < 4096; i++) {
        myData[seekLoc++] = workBuffer[i];
      }
      needsSave = true;
    }

    public void close() {
      if (needsSave) {
        String fileName = System.currentTimeMillis() + ".dsk";
        /*
        System.out.println("Saving disk:" + fileName);
        try {
          FileOutputStream os = new FileOutputStream(fileName);
          os.write(myData);
          os.close();
        } catch (Exception e) { e.printStackTrace(); }
          */
      }
    }
  }

  /* =========== */
  public int diskControlMotor(int address) {
    floppyMotorOn = (address & 1) != 0;
    checkSpinning();
    return Apple2e.randomData(true);
  }

  public int diskControlStepper(int address) {
    address &= 0xff;
    Floppy f = floppy[curDrive];

    if ((address & 1) != 0) {
      int phase = (address >> 1) & 3;
      int direction = 0;
      if (phase == ((f.phase + 1) & 3)) {
        direction = 1;
      }
      if (phase == ((f.phase + 3) & 3)) {
        direction = -1;
      }
      if (direction != 0) {
        f.phase = Math.max(0, Math.min(79, f.phase + direction));
        if ((f.phase & 1) == 0) {
          int newTrack = Math.min(TRACKS - 1, f.phase >> 1);
          if (newTrack != f.track) {
            if ((f.trackImage != null) && f.trackImageDirty) {
              writeTrack(curDrive);
            }
            f.track = newTrack;
            f.trackImageData = false;
          }
        }
      }
    }
    return (address == 0xe0) ? 0xff : Apple2e.randomData(true);
  }

  public int diskEnable(int address) {
    curDrive = address & 1;
    int otherDrive = (curDrive == 0) ? 1 : 0;
    floppy[otherDrive].spinning = 0;
    floppy[otherDrive].writeLight = 0;
    checkSpinning();
    return 0;
  }

  public void diskInsert(
      int drive, String name, byte[] data, boolean writeProtected, boolean createIfNecessary) {
    DosOrderImage di = new DosOrderImage(data);
    Floppy f = floppy[drive];
    if (f.diskData != null) {
      removeDisk(drive);
    }
    f.fullName = name;
    f.diskData = di;
    f.track = 0;
    f.trackImage = null;
    f.phase = 0;
    f.theByte = 0;
    f.writeProtected = writeProtected;
    f.trackImageData = false;
    f.trackImageDirty = false;
    f.spinning = 0;
    f.writeLight = 0;
    f.nibbles = new int[1];
    f.nibbles[0] = 0;
  }

  public int diskReadWrite() {
    Floppy f = floppy[curDrive];
    diskAccessed = true;
    if ((!f.trackImageData) && (f.diskData != null)) {
      readTrack(curDrive);
    }
    if (!f.trackImageData) {
      return 0xff;
    }
    int result = 0;
    if ((!floppyWriteMode) || (!f.writeProtected)) {
      if (floppyWriteMode) {
        if ((floppyLatch & 0x80) != 0) {
          f.trackImage[f.theByte] = floppyLatch;
          f.trackImageDirty = true;
        } else {
          return 0;
        }
      } else {
        result = f.trackImage[f.theByte];
      }
    }
    if (++f.theByte >= f.nibbles[0]) f.theByte = 0;
    return result & 0xff;
  }

  public int diskSetLatchValue(int what, boolean write) {
    if (write) floppyLatch = (byte) what;
    return floppyLatch;
  }

  public int diskSetReadMode() {
    floppyWriteMode = false;
    return Apple2e.randomData(floppy[curDrive].writeProtected);
  }

  public int diskSetWriteMode() {
    floppyWriteMode = true;
    floppy[curDrive].writeLight = 20000;
    return Apple2e.randomData(true);
  }
}
