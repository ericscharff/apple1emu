package a1em;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.FileInputStream;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.Timer;

public class Apple1 extends JComponent implements M6502.Memory {
  private int[] mem;
  private static M6502 cpu;
  private int lastKey;
  private int lastOutput;
  private char keyBuf[];
  private int keyBufIndex;

  private static void err(String s) {
    System.err.println(s);
    System.out.println(cpu.dump());
    System.exit(1);
  }
  private static void warn(String s) {
    System.err.println(s);
  }

  public Dimension getPreferredSize() {
    return new Dimension(150, 150);
  }

  private void fillKeyBuf(File srcFile) {
    try {
      FileInputStream is = new FileInputStream(srcFile);
      long len = srcFile.length();
      keyBuf = new char[(int) len];
      for (int i = 0; i < len; i++) {
        keyBuf[i] = (char) is.read();
        if (keyBuf[i] == '\n') {
          keyBuf[i] = '\r';
        }
      }
      is.close();
      keyBufIndex = 0;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void loadBinary(File srcFile, int where) {
    try {
      FileInputStream is = new FileInputStream(srcFile);
      int c;
      while ((c = is.read()) >= 0) {
        mem[where++] = c;
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public Apple1() {
    super();
    mem = new int[65536];
    cpu = new M6502(this, 0xff00);

    ResourceHelper resources = new ResourceHelper();
    int[] bios = resources.loadBinaryResource("rom.apple1", 256);
    int a = 0xff00;
    for (int i = 0; i < 256; i++) {
      mem[a] = bios[i];
      a++;
    }
    addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
          case KeyEvent.VK_F1:
            // Load file into keyboard buffer
            {
              JFileChooser fc = new JFileChooser();
              int returnVal = fc.showOpenDialog(Apple1.this);
              if (returnVal == JFileChooser.APPROVE_OPTION) {
                fillKeyBuf(fc.getSelectedFile());
              }
            }
            return;
          case KeyEvent.VK_F2:
            // Load Binary at 300H
            {
              JFileChooser fc = new JFileChooser();
              int returnVal = fc.showOpenDialog(Apple1.this);
              if (returnVal == JFileChooser.APPROVE_OPTION) {
                loadBinary(fc.getSelectedFile(), 0x300);
              }
            }
            return;
        }
        lastKey = (int) Character.toUpperCase(e.getKeyChar());
        if (lastKey == '\n')
          lastKey = 0x0d;
        if ((lastKey < 0) || (lastKey > 255)) {
          lastKey = 0;
        }
      }
    });
    new Timer(1000 / 6, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        run();
      }
    }).start();
  }

  private int doIO(int where) {
    //		System.out.println("IO read" + Integer.toHexString(where));
    if (where == 0xd010) {
      /* Keyboard input */
      int k = lastKey;
      if (keyBuf != null) {
        if (keyBufIndex == keyBuf.length) {
          keyBuf = null;
          k = 0;
        } else {
          k = keyBuf[keyBufIndex];
          keyBufIndex++;
          if (keyBufIndex == keyBuf.length)
            keyBuf = null;
        }
        lastKey = k;
      }
      if (lastKey == 0) {
        warn("Reading keyboard but no key available");
      }
      lastKey = 0;
      return k | 0x80;
    } else if (where == 0xd011) {
      if ((keyBuf == null) && (lastKey == 0)) {
        return 1;
      } else {
        return 0x80;
      }
    } else if ((where == 0xd0f2) || (where == 0xd012)) {
      /* Display output */
      /* bit 8 should always be low */
      return lastOutput & 0x7f;
    } else if (where == 0xd013) {
      /* Display status */
      warn("Read from display status");
      return 0;
    }
    err("Read from Unknown I/O Address: " + where);
    return 0;
  }

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
      char ch = (char) (what & 0x7f);
      if (ch == '\r')
        ch = '\n';
      System.out.print(ch);
      System.out.flush();
      lastOutput = what;
      return;
    } else if (where == 0xd013) {
      /* Display status */
      warn("Write to display status I/O: " + what);
      return;
    }
    err("Write to Unknown I/O Address: " + where);
  }

  public int read(int where) {
    if (cpu.dumping)
      return mem[where];
    if ((where == 0) || (where == 1)) {
      warn("Read from " + where);
    }
    if ((where >= 0xd000) && (where < 0xe000))
      return doIO(where);
    if ((where < 0) || (where > 65536))
      err("Read out of range: " + where);
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
    if ((where < 0) || (where > 65536))
      err("Write out of range: " + where);
    if ((what < 0) || (what > 256))
      err("Write value out of range: " + what);
    mem[where] = what;
  }

  public void run() {
    long clock = 0;

    while (!cpu.halt && (clock < 10000)) {
      cpu.step();
      clock++;
    }
  }

  public static void main(String args[]) {
    Apple1 me = new Apple1();
    JFrame f = new JFrame("Apple 1");
    f.getContentPane().setLayout(new BorderLayout());
    f.getContentPane().add(me);
    f.pack();
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    f.setVisible(true);
    me.requestFocus();
  }
}
