/* Copyright (c) 2007-2022, Eric Scharff
   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.
   There is NO WARRANTY for this software.  See license.txt for
   details. */

package a2em;

public class M6502 {
  public static final int cFlag = 0x01;
  public static final int zFlag = 0x02;
  public static final int iFlag = 0x04;
  public static final int dFlag = 0x08;
  public static final int bFlag = 0x10;
  public static final int rFlag = 0x20;
  public static final int vFlag = 0x40;
  public static final int nFlag = 0x80;

  public static final int ncFlag = (~cFlag) & 0xff;
  public static final int nzFlag = (~zFlag) & 0xff;
  public static final int niFlag = (~iFlag) & 0xff;
  public static final int ndFlag = (~dFlag) & 0xff;
  public static final int nbFlag = (~bFlag) & 0xff;
  public static final int nrFlag = (~rFlag) & 0xff;
  public static final int nvFlag = (~vFlag) & 0xff;
  public static final int nnFlag = (~nFlag) & 0xff;

  private static final int mIndirectX = 0;
  private static final int mZeroPage = 1;
  private static final int mImmediate = 2;
  private static final int mAbsolute = 3;
  private static final int mIndirectY = 4;
  private static final int mZeroPageX = 5;
  private static final int mZeroPageY = 6;
  private static final int mAbsoluteX = 7;
  private static final int mAbsoluteY = 8;
  private static final int mRelative = 9;
  private static final int mIndirect = 10;
  private static final int mIndirectZP = 11;
  private static final int mAccum = 12;
  private static final int mImplied = 13;
  private static final int mAIndirectX = 14;

  public interface Memory {
    public int read(int where);

    public void write(int where, int what);
  }

  public int a;
  public int x;
  public int y;
  public int pc;
  private int sp;
  private int flags;
  private Memory mem;
  public boolean halt;
  private static final int PCBUF = 10;
  private int[] pcbuf;
  private int pcbufptr;
  public boolean dumping;

  public boolean bFlagSet() {
    return (flags & bFlag) != 0;
  }

  public boolean dFlagSet() {
    return (flags & dFlag) != 0;
  }

  public M6502(Memory m, int startPC) {
    mem = m;
    halt = false;
    pc = startPC;
    sp = 0xff;
    flags = rFlag;
    pcbuf = new int[PCBUF];
  }

  private int complement(int x) {
    return (x < 128) ? x : -1 * ((x ^ 0xff) + 1);
  }

  private int BINtoBCD(int v) {
    return (((v / 10) % 10) << 4) | (v % 10);
  }

  private int BCDtoBIN(int v) {
    return ((v >> 4) * 10) + (v & 0xf);
  }

  public void setNZ(int v) {
    if (v == 0) {
      flags |= zFlag;
    } else {
      flags &= nzFlag;
    }
    if ((v & 0x80) != 0) {
      flags |= nFlag;
    } else {
      flags &= nnFlag;
    }
  }

  public void setC(boolean v) {
    if (v) {
      flags |= cFlag;
    } else {
      flags &= ncFlag;
    }
  }

  private void setV(boolean v) {
    if (v) {
      flags |= vFlag;
    } else {
      flags &= nvFlag;
    }
  }

  private void setI(boolean v) {
    if (v) {
      flags |= iFlag;
    } else {
      flags &= niFlag;
    }
  }

  private void setD(boolean v) {
    if (v) {
      flags |= dFlag;
    } else {
      flags &= ndFlag;
    }
  }

  private void setB(boolean v) {
    if (v) {
      flags |= bFlag;
    } else {
      flags &= nbFlag;
    }
  }

  private void setN(boolean v) {
    if (v) {
      flags |= nFlag;
    } else {
      flags &= nnFlag;
    }
  }

  private void setZ(boolean v) {
    if (v) {
      flags |= zFlag;
    } else {
      flags &= nzFlag;
    }
  }

  private void pushByte(int what) {
    mem.write(sp + 256, what);
    sp--;
    sp &= 0xff;
  }

  private void pushWord(int what) {
    pushByte(what >> 8);
    pushByte(what & 0xff);
  }

  private int popByte() {
    sp++;
    sp &= 0xff;
    return mem.read(sp + 256);
  }

  private int popWord() {
    int v = popByte();
    return v + popByte() * 256;
  }

  private int fetch() {
    pc++;
    return mem.read(pc - 1);
  }

  private int fetchWord() {
    int v = fetch();
    return v + fetch() * 256;
  }

  private int wordAt(int where) {
    int v = mem.read(where);
    return v + mem.read(where + 1) * 256;
  }

  private int ea(int mode) {
    int v = -1;

    switch (mode) {
      case mIndirectX:
        v = wordAt(fetch() + x);
        break;
      case mZeroPage:
        v = fetch();
        break;
      case mImmediate:
        v = fetch();
        break;
      case mAbsolute:
        v = fetchWord();
        break;
      case mIndirectY:
        v = wordAt(fetch()) + y;
        break;
      case mZeroPageX:
        v = 0xff & (fetch() + x);
        break;
      case mZeroPageY:
        v = 0xff & (fetch() + y);
        break;
      case mAbsoluteX:
        v = fetchWord() + x;
        break;
      case mAbsoluteY:
        v = fetchWord() + y;
        break;
      case mRelative:
        v = complement(fetch()) + pc;
        break;
      case mIndirect:
        v = wordAt(fetchWord());
        break;
      case mAIndirectX:
        v = wordAt(fetchWord() + x);
        break;
      case mIndirectZP:
        v = wordAt(fetch());
        break;
      case mAccum:
        v = a;
        break;
    }
    return v;
  }

  private int cea(int mode) {
    int v = ea(mode);
    if (mode == mImmediate) {
      return v;
    } else {
      return mem.read(v);
    }
  }

  private void doADC(int mode) {
    int m = cea(mode);

    if (dFlagSet()) {
      m = BCDtoBIN(a) + BCDtoBIN(m) + (flags & cFlag);
      setC(m > 99);
      a = BINtoBCD(m);
      setNZ(a);
      return;
    }
    int r = a + m + (flags & cFlag);

    setC(r > 255);
    r &= 255;
    setNZ(r);
    setV(((a & 0x80) == (m & 0x80)) && ((r & 0x80) != (a & 0x80)));
    a = r;
  }

  private void doAND(int mode) {
    a &= cea(mode);
    setNZ(a);
  }

  private void doASL(int mode) {
    if (mode == mAccum) {
      a <<= 1;
      if (a > 255) {
        setC(true);
        a &= 255;
      } else {
        setC(false);
      }
      setNZ(a);
    } else {
      int w = ea(mode);
      int v = mem.read(w);
      v <<= 1;
      if (v > 255) {
        setC(true);
        v &= 255;
      } else {
        setC(false);
      }
      mem.write(w, v);
      setNZ(v);
    }
  }

  private void doBCC(int mode) {
    int dst = ea(mode);
    if ((cFlag & flags) == 0) {
      pc = dst;
    }
  }

  private void doBCS(int mode) {
    int dst = ea(mode);
    if ((cFlag & flags) != 0) {
      pc = dst;
    }
  }

  private void doBEQ(int mode) {
    int dst = ea(mode);
    if ((zFlag & flags) != 0) {
      pc = dst;
    }
  }

  private void doBIT(int mode) {
    int v = cea(mode);
    setV((v & 0x40) != 0);
    setN((v & 0x80) != 0);
    setZ((a & v) == 0);
  }

  private void doBMI(int mode) {
    int dst = ea(mode);
    if ((nFlag & flags) != 0) {
      pc = dst;
    }
  }

  private void doBNE(int mode) {
    int dst = ea(mode);
    if ((zFlag & flags) == 0) {
      pc = dst;
    }
  }

  private void doBPL(int mode) {
    int dst = ea(mode);
    if ((nFlag & flags) == 0) {
      pc = dst;
    }
  }

  private void doBVC(int mode) {
    int dst = ea(mode);
    if ((vFlag & flags) == 0) {
      pc = dst;
    }
  }

  private void doBVS(int mode) {
    int dst = ea(mode);
    if ((vFlag & flags) != 0) {
      pc = dst;
    }
  }

  public void doCLC(int mode) {
    setC(false);
  }

  private void doCLD(int mode) {
    setD(false);
  }

  private void doCLI(int mode) {
    setI(false);
  }

  private void doCLV(int mode) {
    setV(false);
  }

  private void doCMP(int mode) {
    int m = cea(mode);
    setC(a >= m);
    m = a - m;
    setNZ(m);
  }

  private void doCPX(int mode) {
    int m = cea(mode);
    setC(x >= m);
    m = x - m;
    setNZ(m);
  }

  private void doCPY(int mode) {
    int m = cea(mode);
    setC(y >= m);
    m = y - m;
    setNZ(m);
  }

  private void doDEC(int mode) {
    int w = ea(mode);
    int v = (mem.read(w) - 1) & 0xff;
    mem.write(w, v);
    setNZ(v);
  }

  private void doDEX(int mode) {
    x = (x - 1) & 0xff;
    setNZ(x);
  }

  private void doDEY(int mode) {
    y = (y - 1) & 0xff;
    setNZ(y);
  }

  private void doEOR(int mode) {
    int m = cea(mode);
    a = a ^ m;
    setNZ(a);
  }

  private void doINC(int mode) {
    int w = ea(mode);
    int v = (mem.read(w) + 1) & 0xff;
    mem.write(w, v);
    setNZ(v);
  }

  private void doINX(int mode) {
    x = (x + 1) & 0xff;
    setNZ(x);
  }

  private void doINY(int mode) {
    y = (y + 1) & 0xff;
    setNZ(y);
  }

  private void doJMP(int mode) {
    pc = ea(mode);
  }

  private void doJSR(int mode) {
    int m = ea(mode);
    pushWord(pc - 1);
    pc = m;
  }

  private void doLDA(int mode) {
    a = cea(mode);
    setNZ(a);
  }

  private void doLDX(int mode) {
    x = cea(mode);
    setNZ(x);
  }

  private void doLDY(int mode) {
    y = cea(mode);
    setNZ(y);
  }

  private void doLSR(int mode) {
    if (mode == mAccum) {
      setC((a & 1) != 0);
      a >>= 1;
      setNZ(a);
    } else {
      int m = ea(mode);
      int v = mem.read(m);
      setC((v & 1) != 0);
      v >>= 1;
      mem.write(m, v);
      setNZ(v);
    }
  }

  private void doNOP(int mode) {}

  private void doORA(int mode) {
    a |= cea(mode);
    setNZ(a);
  }

  private void doPHA(int mode) {
    pushByte(a);
  }

  private void doPHP(int mode) {
    flags |= rFlag;
    pushByte(flags);
  }

  private void doPLA(int mode) {
    a = popByte();
    setNZ(a);
  }

  private void doPLP(int mode) {
    //          flags = popByte() | rFlag;
    flags = popByte();
  }

  private void doROL(int mode) {
    if (mode == mAccum) {
      a <<= 1;
      a |= flags & cFlag;
      if (a > 255) {
        a &= 0xff;
        setC(true);
      } else {
        setC(false);
      }
      setNZ(a);
    } else {
      int m = ea(mode);
      int v = mem.read(m);
      v <<= 1;
      v |= flags & cFlag;
      if (v > 255) {
        v &= 0xff;
        setC(true);
      } else {
        setC(false);
      }
      mem.write(m, v);
      setNZ(v);
    }
  }

  private void doROR(int mode) {
    if (mode == mAccum) {
      if ((cFlag & flags) != 0) {
        a |= 0x100;
      }
      setC((a & 1) != 0);
      a >>= 1;
      setNZ(a);
    } else {
      int m = ea(mode);
      int v = mem.read(m);
      if ((cFlag & flags) != 0) {
        v |= 0x100;
      }
      setC((v & 1) != 0);
      v >>= 1;
      mem.write(m, v);
      setNZ(v);
    }
  }

  private void doRTI(int mode) {
    doPLP(mode);
    pc = popWord();
  }

  public void doRTS(int mode) {
    pc = popWord() + 1;
  }

  private void doSBC(int mode) {
    int m = cea(mode);
    if (dFlagSet()) {
      m = BCDtoBIN(a) - BCDtoBIN(m) - 1 + (flags & cFlag);
      if ((m & 0xff00) == 0) {
        setC(true);
      } else {
        setC(false);
        m += 100;
      }
      a = BINtoBCD(m);
      setNZ(a);
      return;
    }
    int r = a - m - 1 + (flags & cFlag);
    setC((r & 0xff00) == 0);
    r &= 0xff;
    setNZ(r);
    setV(((a & 0x80) != (m & 0x80)) && ((r & 0x80) != (a & 0x80)));
    a = r;
  }

  private void doSEC(int mode) {
    setC(true);
  }

  private void doSED(int mode) {
    setD(true);
  }

  private void doSEI(int mode) {
    setI(true);
  }

  private void doSTA(int mode) {
    mem.write(ea(mode), a);
  }

  private void doSTX(int mode) {
    mem.write(ea(mode), x);
  }

  private void doSTY(int mode) {
    mem.write(ea(mode), y);
  }

  private void doTAX(int mode) {
    x = a;
    setNZ(a);
  }

  private void doTAY(int mode) {
    y = a;
    setNZ(a);
  }

  private void doTSX(int mode) {
    x = sp;
    setNZ(x);
  }

  private void doTXA(int mode) {
    a = x;
    setNZ(a);
  }

  private void doTXS(int mode) {
    sp = x;
  }

  private void doTYA(int mode) {
    a = y;
    setNZ(a);
  }

  private void doILL(int mode) {
    halt = true;
  }

  /* ================================================================ */
  /* 65C02 OPCODES                                                    */
  /* ================================================================ */

  private void doBRA(int mode) {
    pc = ea(mode);
  }

  private void doDEA(int mode) {
    a = (a - 1) & 0xff;
    setNZ(a);
  }

  private void doINA(int mode) {
    a = (a + 1) & 0xff;
    setNZ(a);
  }

  private void doPHX(int mode) {
    pushByte(x);
  }

  private void doPHY(int mode) {
    pushByte(y);
  }

  private void doPLX(int mode) {
    x = popByte();
    setNZ(x);
  }

  private void doPLY(int mode) {
    y = popByte();
    setNZ(y);
  }

  private void doSTZ(int mode) {
    mem.write(ea(mode), 0);
  }

  private void doTRB(int mode) {
    int m = ea(mode);
    int v = mem.read(m);
    v &= 0xff ^ a;
    mem.write(m, v);
    setZ(v == 0);
  }

  private void doTSB(int mode) {
    int m = ea(mode);
    int v = mem.read(m);
    v |= a;
    mem.write(m, v);
    setZ(v == 0);
  }

  private void interrupt(boolean isBRK) {
    pushWord(pc);
    setB(isBRK);
    doPHP(mImplied);
    setI(true);
    pc = wordAt(0xfffe);
  }

  public void tryInterrupt() {
    if ((iFlag & flags) == 0) interrupt(false);
  }

  public void jumpTo(int where) {
    pc = where;
  }

  private void doBRK(int mode) {
    halt = true;
    interrupt(true);
  }

  public void step() {
    pcbuf[pcbufptr] = pc;
    pcbufptr = (pcbufptr + 1) % PCBUF;
    int i = fetch();
    switch (i) {
      case 0:
        doBRK(mImplied);
        break;
      case 1:
        doORA(mIndirectX);
        break;
      case 2:
        doILL(mImplied);
        break;
      case 3:
        doILL(mImplied);
        break;
      case 4:
        doTSB(mZeroPage);
        break;
      case 5:
        doORA(mZeroPage);
        break;
      case 6:
        doASL(mZeroPage);
        break;
      case 7:
        //                        doILL(mImplied);
        doNOP(mImplied);
        break;
      case 8:
        doPHP(mImplied);
        break;
      case 9:
        doORA(mImmediate);
        break;
      case 10:
        doASL(mAccum);
        break;
      case 11:
        doILL(mImplied);
        break;
      case 12:
        doTSB(mAbsolute);
        break;
      case 13:
        doORA(mAbsolute);
        break;
      case 14:
        doASL(mAbsolute);
        break;
      case 15:
        doILL(mImplied);
        break;
      case 16:
        doBPL(mRelative);
        break;
      case 17:
        doORA(mIndirectY);
        break;
      case 18:
        doORA(mIndirectZP);
        break;
      case 19:
        doILL(mImplied);
        break;
      case 20:
        doTRB(mZeroPage);
        break;
      case 21:
        doORA(mZeroPageX);
        break;
      case 22:
        doASL(mZeroPageX);
        break;
      case 23:
        doILL(mImplied);
        break;
      case 24:
        doCLC(mImplied);
        break;
      case 25:
        doORA(mAbsoluteY);
        break;
      case 26:
        doINA(mImplied);
        break;
      case 27:
        doILL(mImplied);
        break;
      case 28:
        doTRB(mAbsolute);
        break;
      case 29:
        doORA(mAbsoluteX);
        break;
      case 30:
        doASL(mAbsoluteX);
        break;
      case 31:
        doILL(mImplied);
        break;
      case 32:
        doJSR(mAbsolute);
        break;
      case 33:
        doAND(mIndirectX);
        break;
      case 34:
        doILL(mImplied);
        break;
      case 35:
        doILL(mImplied);
        break;
      case 36:
        doBIT(mZeroPage);
        break;
      case 37:
        doAND(mZeroPage);
        break;
      case 38:
        doROL(mZeroPage);
        break;
      case 39:
        doILL(mImplied);
        break;
      case 40:
        doPLP(mImplied);
        break;
      case 41:
        doAND(mImmediate);
        break;
      case 42:
        doROL(mAccum);
        break;
      case 43:
        doILL(mImplied);
        break;
      case 44:
        doBIT(mAbsolute);
        break;
      case 45:
        doAND(mAbsolute);
        break;
      case 46:
        doROL(mAbsolute);
        break;
      case 47:
        doILL(mImplied);
        break;
      case 48:
        doBMI(mRelative);
        break;
      case 49:
        doAND(mIndirectY);
        break;
      case 50:
        doAND(mIndirectZP);
        break;
      case 51:
        doILL(mImplied);
        break;
      case 52:
        doBIT(mZeroPageX);
        break;
      case 53:
        doAND(mZeroPageX);
        break;
      case 54:
        doROL(mZeroPageX);
        break;
      case 55:
        doILL(mImplied);
        break;
      case 56:
        doSEC(mImplied);
        break;
      case 57:
        doAND(mAbsoluteY);
        break;
      case 58:
        doDEA(mImplied);
        break;
      case 59:
        doILL(mImplied);
        break;
      case 60:
        doBIT(mAbsoluteX);
        break;
      case 61:
        doAND(mAbsoluteX);
        break;
      case 62:
        doROL(mAbsoluteX);
        break;
      case 63:
        doILL(mImplied);
        break;
      case 64:
        doRTI(mImplied);
        break;
      case 65:
        doEOR(mIndirectX);
        break;
      case 66:
        doILL(mImplied);
        break;
      case 67:
        doILL(mImplied);
        break;
      case 68:
        doILL(mImplied);
        break;
      case 69:
        doEOR(mZeroPage);
        break;
      case 70:
        doLSR(mZeroPage);
        break;
      case 71:
        doILL(mImplied);
        break;
      case 72:
        doPHA(mImplied);
        break;
      case 73:
        doEOR(mImmediate);
        break;
      case 74:
        doLSR(mAccum);
        break;
      case 75:
        doILL(mImplied);
        break;
      case 76:
        doJMP(mAbsolute);
        break;
      case 77:
        doEOR(mAbsolute);
        break;
      case 78:
        doLSR(mAbsolute);
        break;
      case 79:
        doILL(mImplied);
        break;
      case 80:
        doBVC(mRelative);
        break;
      case 81:
        doEOR(mIndirectY);
        break;
      case 82:
        doEOR(mIndirectZP);
        break;
      case 83:
        doILL(mImplied);
        break;
      case 84:
        doILL(mImplied);
        break;
      case 85:
        doEOR(mZeroPageX);
        break;
      case 86:
        doLSR(mZeroPageX);
        break;
      case 87:
        doILL(mImplied);
        break;
      case 88:
        doCLI(mImplied);
        break;
      case 89:
        doEOR(mAbsoluteY);
        break;
      case 90:
        doPHY(mImplied);
        break;
      case 91:
        doILL(mImplied);
        break;
      case 92:
        doILL(mImplied);
        break;
      case 93:
        doEOR(mAbsoluteX);
        break;
      case 94:
        doLSR(mAbsoluteX);
        break;
      case 95:
        doILL(mImplied);
        break;
      case 96:
        doRTS(mImplied);
        break;
      case 97:
        doADC(mIndirectX);
        break;
      case 98:
        doILL(mImplied);
        break;
      case 99:
        doILL(mImplied);
        break;
      case 100:
        doSTZ(mZeroPage);
        break;
      case 101:
        doADC(mZeroPage);
        break;
      case 102:
        doROR(mZeroPage);
        break;
      case 103:
        doILL(mImplied);
        break;
      case 104:
        doPLA(mImplied);
        break;
      case 105:
        doADC(mImmediate);
        break;
      case 106:
        doROR(mAccum);
        break;
      case 107:
        doILL(mImplied);
        break;
      case 108:
        doJMP(mIndirect);
        break;
      case 109:
        doADC(mAbsolute);
        break;
      case 110:
        doROR(mAbsolute);
        break;
      case 111:
        doILL(mImplied);
        break;
      case 112:
        doBVS(mRelative);
        break;
      case 113:
        doADC(mIndirectY);
        break;
      case 114:
        doADC(mIndirectZP);
        break;
      case 115:
        doILL(mImplied);
        break;
      case 116:
        doSTZ(mZeroPageX);
        break;
      case 117:
        doADC(mZeroPageX);
        break;
      case 118:
        doROR(mZeroPageX);
        break;
      case 119:
        doILL(mImplied);
        break;
      case 120:
        doSEI(mImplied);
        break;
      case 121:
        doADC(mAbsoluteY);
        break;
      case 122:
        doPLY(mImplied);
        break;
      case 123:
        doILL(mImplied);
        break;
      case 124:
        doJMP(mAIndirectX);
        break;
      case 125:
        doADC(mAbsoluteX);
        break;
      case 126:
        doROR(mAbsoluteX);
        break;
      case 127:
        doILL(mImplied);
        break;
      case 128:
        doBRA(mRelative);
        break;
      case 129:
        doSTA(mIndirectX);
        break;
      case 130:
        doILL(mImplied);
        break;
      case 131:
        doILL(mImplied);
        break;
      case 132:
        doSTY(mZeroPage);
        break;
      case 133:
        doSTA(mZeroPage);
        break;
      case 134:
        doSTX(mZeroPage);
        break;
      case 135:
        doILL(mImplied);
        break;
      case 136:
        doDEY(mImplied);
        break;
      case 137:
        doBIT(mImmediate);
        break;
      case 138:
        doTXA(mImplied);
        break;
      case 139:
        doILL(mImplied);
        break;
      case 140:
        doSTY(mAbsolute);
        break;
      case 141:
        doSTA(mAbsolute);
        break;
      case 142:
        doSTX(mAbsolute);
        break;
      case 143:
        doILL(mImplied);
        break;
      case 144:
        doBCC(mRelative);
        break;
      case 145:
        doSTA(mIndirectY);
        break;
      case 146:
        doSTA(mIndirectZP);
        break;
      case 147:
        doILL(mImplied);
        break;
      case 148:
        doSTY(mZeroPageX);
        break;
      case 149:
        doSTA(mZeroPageX);
        break;
      case 150:
        doSTX(mZeroPageY);
        break;
      case 151:
        doILL(mImplied);
        break;
      case 152:
        doTYA(mImplied);
        break;
      case 153:
        doSTA(mAbsoluteY);
        break;
      case 154:
        doTXS(mImplied);
        break;
      case 155:
        doILL(mImplied);
        break;
      case 156:
        doSTZ(mAbsolute);
        break;
      case 157:
        doSTA(mAbsoluteX);
        break;
      case 158:
        doSTZ(mAbsoluteX);
        break;
      case 159:
        doILL(mImplied);
        break;
      case 160:
        doLDY(mImmediate);
        break;
      case 161:
        doLDA(mIndirectX);
        break;
      case 162:
        doLDX(mImmediate);
        break;
      case 163:
        doILL(mImplied);
        break;
      case 164:
        doLDY(mZeroPage);
        break;
      case 165:
        doLDA(mZeroPage);
        break;
      case 166:
        doLDX(mZeroPage);
        break;
      case 167:
        doILL(mImplied);
        break;
      case 168:
        doTAY(mImplied);
        break;
      case 169:
        doLDA(mImmediate);
        break;
      case 170:
        doTAX(mImplied);
        break;
      case 171:
        doILL(mImplied);
        break;
      case 172:
        doLDY(mAbsolute);
        break;
      case 173:
        doLDA(mAbsolute);
        break;
      case 174:
        doLDX(mAbsolute);
        break;
      case 175:
        doILL(mImplied);
        break;
      case 176:
        doBCS(mRelative);
        break;
      case 177:
        doLDA(mIndirectY);
        break;
      case 178:
        doLDA(mIndirectZP);
        break;
      case 179:
        doILL(mImplied);
        break;
      case 180:
        doLDY(mZeroPageX);
        break;
      case 181:
        doLDA(mZeroPageX);
        break;
      case 182:
        doLDX(mZeroPageY);
        break;
      case 183:
        doILL(mImplied);
        break;
      case 184:
        doCLV(mImplied);
        break;
      case 185:
        doLDA(mAbsoluteY);
        break;
      case 186:
        doTSX(mImplied);
        break;
      case 187:
        doILL(mImplied);
        break;
      case 188:
        doLDY(mAbsoluteX);
        break;
      case 189:
        doLDA(mAbsoluteX);
        break;
      case 190:
        doLDX(mAbsoluteY);
        break;
      case 191:
        doILL(mImplied);
        break;
      case 192:
        doCPY(mImmediate);
        break;
      case 193:
        doCMP(mIndirectX);
        break;
      case 194:
        doILL(mImplied);
        break;
      case 195:
        doILL(mImplied);
        break;
      case 196:
        doCPY(mZeroPage);
        break;
      case 197:
        doCMP(mZeroPage);
        break;
      case 198:
        doDEC(mZeroPage);
        break;
      case 199:
        doILL(mImplied);
        break;
      case 200:
        doINY(mImplied);
        break;
      case 201:
        doCMP(mImmediate);
        break;
      case 202:
        doDEX(mImplied);
        break;
      case 203:
        doILL(mImplied);
        break;
      case 204:
        doCPY(mAbsolute);
        break;
      case 205:
        doCMP(mAbsolute);
        break;
      case 206:
        doDEC(mAbsolute);
        break;
      case 207:
        doILL(mImplied);
        break;
      case 208:
        doBNE(mRelative);
        break;
      case 209:
        doCMP(mIndirectY);
        break;
      case 210:
        doCMP(mIndirectZP);
        break;
      case 211:
        doILL(mImplied);
        break;
      case 212:
        doILL(mImplied);
        break;
      case 213:
        doCMP(mZeroPageX);
        break;
      case 214:
        doDEC(mZeroPageX);
        break;
      case 215:
        doILL(mImplied);
        break;
      case 216:
        doCLD(mImplied);
        break;
      case 217:
        doCMP(mAbsoluteY);
        break;
      case 218:
        doPHX(mImplied);
        break;
      case 219:
        doILL(mImplied);
        break;
      case 220:
        doILL(mImplied);
        break;
      case 221:
        doCMP(mAbsoluteX);
        break;
      case 222:
        doDEC(mAbsoluteX);
        break;
      case 223:
        doILL(mImplied);
        break;
      case 224:
        doCPX(mImmediate);
        break;
      case 225:
        doSBC(mIndirectX);
        break;
      case 226:
        doILL(mImplied);
        break;
      case 227:
        doILL(mImplied);
        break;
      case 228:
        doCPX(mZeroPage);
        break;
      case 229:
        doSBC(mZeroPage);
        break;
      case 230:
        doINC(mZeroPage);
        break;
      case 231:
        doILL(mImplied);
        break;
      case 232:
        doINX(mImplied);
        break;
      case 233:
        doSBC(mImmediate);
        break;
      case 234:
        doNOP(mImplied);
        break;
      case 235:
        doILL(mImplied);
        break;
      case 236:
        doCPX(mAbsolute);
        break;
      case 237:
        doSBC(mAbsolute);
        break;
      case 238:
        doINC(mAbsolute);
        break;
      case 239:
        doILL(mImplied);
        break;
      case 240:
        doBEQ(mRelative);
        break;
      case 241:
        doSBC(mIndirectY);
        break;
      case 242:
        doSBC(mIndirectZP);
        break;
      case 243:
        doILL(mImplied);
        break;
      case 244:
        doILL(mImplied);
        break;
      case 245:
        doSBC(mZeroPageX);
        break;
      case 246:
        doINC(mZeroPageX);
        break;
      case 247:
        doILL(mImplied);
        break;
      case 248:
        doSED(mImplied);
        break;
      case 249:
        doSBC(mAbsoluteY);
        break;
      case 250:
        doPLX(mImplied);
        break;
      case 251:
        doILL(mImplied);
        break;
      case 252:
        doILL(mImplied);
        break;
      case 253:
        doSBC(mAbsoluteX);
        break;
      case 254:
        doINC(mAbsoluteX);
        break;
      case 255:
        doILL(mImplied);
        break;
    }
  }

  public String dump() {
    dumping = true;
    StringBuffer b = new StringBuffer();

    for (int i = 0; i < PCBUF; i++) {
      pcbufptr = (pcbufptr + 1) % PCBUF;
      dumpHexWord(b, pcbuf[pcbufptr]);
      b.append("  ");
      b.append(disassemble(pcbuf[pcbufptr]));
      b.append('\n');
    }
    b.append("PC = ");
    dumpHexWord(b, pc);
    b.append(" A = ");
    dumpHexByte(b, a);
    b.append(" X = ");
    dumpHexByte(b, x);
    b.append(" Y = ");
    dumpHexByte(b, y);
    b.append(" SP = ");
    dumpHexByte(b, sp);
    dumpFlags(b);
    b.append(' ');
    b.append(disassemble(pc));
    dumping = false;
    return b.toString();
  }

  private void dumpFlags(StringBuffer b) {
    if ((flags & nFlag) == 0) {
      b.append(" n");
    } else {
      b.append(" N");
    }
    if ((flags & vFlag) == 0) {
      b.append('v');
    } else {
      b.append('V');
    }
    if ((flags & rFlag) == 0) {
      b.append('r');
    } else {
      b.append('R');
    }
    if ((flags & bFlag) == 0) {
      b.append('b');
    } else {
      b.append('B');
    }
    if ((flags & dFlag) == 0) {
      b.append('d');
    } else {
      b.append('D');
    }
    if ((flags & iFlag) == 0) {
      b.append('i');
    } else {
      b.append('I');
    }
    if ((flags & zFlag) == 0) {
      b.append('z');
    } else {
      b.append('Z');
    }
    if ((flags & cFlag) == 0) {
      b.append('c');
    } else {
      b.append('C');
    }
    b.append(' ');
  }

  private static char digits[] = {
    '0', '1', '2', '3', '4', '5', '6', '7',
    '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
  };

  private static void dumpHexByte(StringBuffer b, int value) {
    b.append('$');

    b.append(digits[(value >> 4) & 0xf]);
    b.append(digits[value & 0xf]);
  }

  private static void dumpHexWord(StringBuffer b, int value) {
    b.append('$');

    b.append(digits[(value >> 12) & 0xf]);
    b.append(digits[(value >> 8) & 0xf]);
    b.append(digits[(value >> 4) & 0xf]);
    b.append(digits[value & 0xf]);
  }

  public String disassemble(int where) {
    StringBuffer b = new StringBuffer();

    //          dumpHexByte(b, mem.read(where));
    b.append(codeTable[mem.read(where)]);

    switch (modeTable[mem.read(where)]) {
      case mIndirectX:
        b.append(" (");
        dumpHexByte(b, mem.read(where + 1));
        b.append(", X)");
        break;
      case mZeroPage:
        b.append(' ');
        dumpHexByte(b, mem.read(where + 1));
        break;
      case mImmediate:
        b.append(" #");
        dumpHexByte(b, mem.read(where + 1));
        break;
      case mAbsolute:
        b.append(' ');
        dumpHexWord(b, wordAt(where + 1));
        break;
      case mIndirectY:
        b.append(" (");
        dumpHexByte(b, mem.read(where + 1));
        b.append("), Y  [");
        dumpHexWord(b, wordAt(mem.read(where + 1)));
        b.append(']');
        break;
      case mZeroPageX:
        b.append(' ');
        dumpHexByte(b, mem.read(where + 1));
        b.append(", X");
        break;
      case mZeroPageY:
        b.append(' ');
        dumpHexByte(b, mem.read(where + 1));
        b.append(", Y");
        break;
      case mAbsoluteX:
        b.append(' ');
        dumpHexWord(b, wordAt(where + 1));
        b.append(", X");
        break;
      case mAbsoluteY:
        b.append(' ');
        dumpHexWord(b, wordAt(where + 1));
        b.append(", Y");
        break;
      case mRelative:
        b.append(' ');
        dumpHexWord(b, where + 2 + complement(mem.read(where + 1)));
        break;
      case mIndirect:
        b.append(" (");
        dumpHexWord(b, wordAt(where + 1));
        b.append(')');
        break;
      case mIndirectZP:
        b.append(" (");
        dumpHexByte(b, mem.read(where + 1));
        b.append(')');
        break;
      case mAccum:
        b.append(" A");
        break;
      case mImplied:
        break;
      default:
        b.append(" UNKNOWN MODE");
        break;
    }
    return b.toString();
  }

  private static String codeTable[] = {
    "BRK", "ORA", "???", "???", "TSB", "ORA", "ASL", "???",
    "PHP", "ORA", "ASL", "???", "TSB", "ORA", "ASL", "???",
    "BPL", "ORA", "ORA", "???", "TRB", "ORA", "ASL", "???",
    "CLC", "ORA", "INC A", "???", "TRB", "ORA", "ASL", "???",
    "JSR", "AND", "???", "???", "BIT", "AND", "ROL", "???",
    "PLP", "AND", "ROL", "???", "BIT", "AND", "ROL", "???",
    "BMI", "AND", "AND", "???", "BIT", "AND", "ROL", "???",
    "SEC", "AND", "DEC A", "???", "BIT", "AND", "ROL", "???",
    "RTI", "EOR", "???", "???", "???", "EOR", "LSR", "???",
    "PHA", "EOR", "LSR", "???", "JMP", "EOR", "LSR", "???",
    "BVC", "EOR", "EOR", "???", "???", "EOR", "LSR", "???",
    "CLI", "EOR", "PHY", "???", "???", "EOR", "LSR", "???",
    "RTS", "ADC", "???", "???", "STZ", "ADC", "ROR", "???",
    "PLA", "ADC", "ROR", "???", "JMP", "ADC", "ROR", "???",
    "BVS", "ADC", "ADC", "???", "STZ", "ADC", "ROR", "???",
    "SEI", "ADC", "PLY", "???", "JMP", "ADC", "ROR", "???",
    "BRA", "STA", "???", "???", "STY", "STA", "STX", "???",
    "DEY", "BIT", "TXA", "???", "STY", "STA", "STX", "???",
    "BCC", "STA", "STA", "???", "STY", "STA", "STX", "???",
    "TYA", "STA", "TXS", "???", "STZ", "STA", "STZ", "???",
    "LDY", "LDA", "LDX", "???", "LDY", "LDA", "LDX", "???",
    "TAY", "LDA", "TAX", "???", "LDY", "LDA", "LDX", "???",
    "BCS", "LDA", "LDA", "???", "LDY", "LDA", "LDX", "???",
    "CLV", "LDA", "TSX", "???", "LDY", "LDA", "LDX", "???",
    "CPY", "CMP", "???", "???", "CPY", "CMP", "DEC", "???",
    "INY", "CMP", "DEX", "???", "CPY", "CMP", "DEC", "???",
    "BNE", "CMP", "???", "???", "???", "CMP", "DEC", "???",
    "CLD", "CMP", "PHX", "???", "???", "CMP", "DEC", "???",
    "CPX", "SBC", "???", "???", "CPX", "SBC", "INC", "???",
    "INX", "SBC", "NOP", "???", "CPX", "SBC", "INC", "???",
    "BEQ", "SBC", "SBC", "???", "???", "SBC", "INC", "???",
    "SED", "SBC", "PLX", "???", "???", "SBC", "INC", "???",
  };

  private static int modeTable[] = {
    mImplied, mIndirectX, mImplied, mImplied,
    mZeroPage, mZeroPage, mZeroPage, mImplied,
    mImplied, mImmediate, mAccum, mImplied,
    mAbsolute, mAbsolute, mAbsolute, mImplied,
    mRelative, mIndirectY, mIndirectZP, mImplied,
    mZeroPage, mZeroPageX, mZeroPageX, mImplied,
    mImplied, mAbsoluteY, mImplied, mImplied,
    mAbsolute, mAbsoluteX, mAbsoluteX, mImplied,
    mAbsolute, mIndirectX, mImplied, mImplied,
    mZeroPage, mZeroPage, mZeroPage, mImplied,
    mImplied, mImmediate, mAccum, mImplied,
    mAbsolute, mAbsolute, mAbsolute, mImplied,
    mRelative, mIndirectY, mIndirectZP, mImplied,
    mZeroPageX, mZeroPageX, mZeroPageX, mImplied,
    mImplied, mAbsoluteY, mImplied, mImplied,
    mAbsoluteX, mAbsoluteX, mAbsoluteX, mImplied,
    mImplied, mIndirectX, mImplied, mImplied,
    mImplied, mZeroPage, mZeroPage, mImplied,
    mImplied, mImmediate, mAccum, mImplied,
    mAbsolute, mAbsolute, mAbsolute, mImplied,
    mRelative, mIndirectY, mIndirectZP, mImplied,
    mImplied, mZeroPageX, mZeroPageX, mImplied,
    mImplied, mAbsoluteY, mImplied, mImplied,
    mImplied, mAbsoluteX, mAbsoluteX, mImplied,
    mImplied, mIndirectX, mImplied, mImplied,
    mZeroPage, mZeroPage, mZeroPage, mImplied,
    mImplied, mImmediate, mAccum, mImplied,
    mIndirect, mAbsolute, mAbsolute, mImplied,
    mRelative, mIndirectY, mIndirectZP, mImplied,
    mZeroPageX, mZeroPageX, mZeroPageX, mImplied,
    mImplied, mAbsoluteY, mImplied, mImplied,
    mAbsoluteX, mAbsoluteX, mAbsoluteX, mImplied,
    mRelative, mIndirectX, mImplied, mImplied,
    mZeroPage, mZeroPage, mZeroPage, mImplied,
    mImplied, mImmediate, mImplied, mImplied,
    mAbsolute, mAbsolute, mAbsolute, mImplied,
    mRelative, mIndirectY, mIndirectZP, mImplied,
    mZeroPageX, mZeroPageX, mZeroPageY, mImplied,
    mImplied, mAbsoluteY, mImplied, mImplied,
    mAbsolute, mAbsoluteX, mAbsoluteX, mImplied,
    mImmediate, mIndirectX, mImmediate, mImplied,
    mZeroPage, mZeroPage, mZeroPage, mImplied,
    mImplied, mImmediate, mImplied, mImplied,
    mAbsolute, mAbsolute, mAbsolute, mImplied,
    mRelative, mIndirectY, mIndirectZP, mImplied,
    mZeroPageX, mZeroPageX, mZeroPageY, mImplied,
    mImplied, mAbsoluteY, mImplied, mImplied,
    mAbsoluteX, mAbsoluteX, mAbsoluteY, mImplied,
    mImmediate, mIndirectX, mImplied, mImplied,
    mZeroPage, mZeroPage, mZeroPage, mImplied,
    mImplied, mImmediate, mImplied, mImplied,
    mAbsolute, mAbsolute, mAbsolute, mImplied,
    mRelative, mIndirectY, mImplied, mImplied,
    mImplied, mZeroPageX, mZeroPageX, mImplied,
    mImplied, mAbsoluteY, mImplied, mImplied,
    mImplied, mAbsoluteX, mAbsoluteX, mImplied,
    mImmediate, mIndirectX, mImplied, mImplied,
    mZeroPage, mZeroPage, mZeroPage, mImplied,
    mImplied, mImmediate, mImplied, mImplied,
    mAbsolute, mAbsolute, mAbsolute, mImplied,
    mRelative, mIndirectY, mIndirectZP, mImplied,
    mImplied, mZeroPageX, mZeroPageX, mImplied,
    mImplied, mAbsoluteY, mImplied, mImplied,
    mImplied, mAbsoluteX, mAbsoluteX, mImplied,
  };
}
