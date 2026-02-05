package a1em;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Apple1 implements M6502.Memory {

  private int[] mem;
  private static M6502 cpu;
  private static ConcurrentLinkedQueue<Integer> keyBuffer;

  private static void err(String s) {
    System.err.println(s);
    System.out.println(cpu.dump());
    System.exit(1);
  }

  private static void warn(String s) {
    System.err.println(s);
  }

  public Apple1() {
    mem = new int[65536];
    cpu = new M6502(this, 0xff00);
    keyBuffer = new ConcurrentLinkedQueue<>();

    ResourceHelper resources = new ResourceHelper();
    int[] bios = resources.loadBinaryResource("rom.apple1", 256);
    int a = 0xff00;
    for (int i = 0; i < 256; i++) {
      mem[a] = bios[i];
      a++;
    }
  }

  // Read
  private int doIO(int where) {
    if (where == 0xd010) {
      /* Keyboard input */
      if (keyBuffer.isEmpty()) {
        return 0;
      } else {
        // This assumed that 0x80 was already or'd in, and that
        // '\n' was translated to '\r' beforehand. This is done
        // when the key is read;
        return keyBuffer.remove();
      }
    } else if (where == 0xd011) {
      /* Keyboard status */
      return (keyBuffer.isEmpty()) ? 1 : 0x80;
    } else if ((where == 0xd0f2) || (where == 0xd012)) {
      /* Display output */
      // Bit 8 of the last output was cleared when it was written
      return mem[0xd012];
    } else if (where == 0xd013) {
      /* Display status */
      warn("Read from display status");
      return 0;
    }
    err("Read from Unknown I/O Address: " + where);
    return 0;
  }

  // Write
  private void doIO(int where, int what) {
    where &= 0xf00f;
    where |= 0x0010;
    if (where == 0xd010) {
      /* Keyboard input */
      warn("Write to keyboard I/O: " + what);
      return;
    } else if (where == 0xd011) {
      /* Keyboard status */
      warn("Write to keyboard status I/O: " + what);
      return;
    } else if ((where == 0xd0f2) || (where == 0xd012)) {
      /* Display output */
      mem[0xd012] = what & 0x7f;
      char ch = (char) mem[0xd012];
      if (ch == '\r') {
        System.out.print("\r\n");
      } else {
        System.out.print(ch);
      }
      System.out.flush();
      return;
    } else if (where == 0xd013) {
      /* Display status */
      warn("Write to display status I/O: " + what);
      return;
    }
    err("Write to Unknown I/O Address: " + where);
  }

  public int read(int where) {
    if ((where == 0) || (where == 1)) {
      warn("Read from " + where);
    }
    if ((where >= 0xd000) && (where < 0xe000)) return doIO(where);
    if ((where < 0) || (where > 65536)) err("Read out of range: " + where);
    return mem[where];
  }

  public void write(int where, int what) {
    if ((where == 0) || (where == 1)) {
      warn("Write to " + where);
    }
    if ((where >= 0xd000) && (where < 0xe000)) {
      doIO(where, what);
      return;
    }
    if ((where < 0) || (where > 65536)) err("Write out of range: " + where);
    if ((what < 0) || (what > 256)) err("Write value out of range: " + what);
    mem[where] = what;
  }

  public void mainLoop() {
    Runnable readKeyboard = () -> {
      try {
        while (true) {
          int ch = System.in.read();
          if (ch < 0) {
            System.exit(0);
          }
          // If we run with "stty raw -echo", we need to intercept ^D
          // to gracefully quit
          if (ch == 4) {
            System.exit(0);
          }
          // Translate newline to carriage return
          if (ch == 10) {
            ch = 13;
          }
          ch = Character.toUpperCase(ch) | 0x80;
          keyBuffer.add(ch);
        }
      } catch (IOException ignored) {}
    };
    Thread keyThread = new Thread(readKeyboard);
    keyThread.start();
    while (true) {
      cpu.step();
    }
  }

  public static void main(String args[]) {
    Apple1 me = new Apple1();
    me.mainLoop();
  }
}
