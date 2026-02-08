/* Copyright (c) 2007-2026, Eric Scharff
Permission to use, copy, modify, and/or distribute this software
for any purpose with or without fee is hereby granted, provided
that the above copyright notice and this permission notice appear
in all copies.
There is NO WARRANTY for this software.  See LICENSE.txt for
details. */

package a1em

class M6502(private val mem: Memory, var pc: Int) {

    interface Memory {
        fun read(where: Int): Int
        fun write(where: Int, what: Int)
    }

    companion object {
        const val cFlag = 0x01
        const val zFlag = 0x02
        const val iFlag = 0x04
        const val dFlag = 0x08
        const val bFlag = 0x10
        const val rFlag = 0x20
        const val vFlag = 0x40
        const val nFlag = 0x80

        const val ncFlag = (cFlag.inv()) and 0xff
        const val nzFlag = (zFlag.inv()) and 0xff
        const val niFlag = (iFlag.inv()) and 0xff
        const val ndFlag = (dFlag.inv()) and 0xff
        const val nbFlag = (bFlag.inv()) and 0xff
        const val nrFlag = (rFlag.inv()) and 0xff
        const val nvFlag = (vFlag.inv()) and 0xff
        const val nnFlag = (nFlag.inv()) and 0xff

        private const val mIndirectX = 0
        private const val mZeroPage = 1
        private const val mImmediate = 2
        private const val mAbsolute = 3
        private const val mIndirectY = 4
        private const val mZeroPageX = 5
        private const val mZeroPageY = 6
        private const val mAbsoluteX = 7
        private const val mAbsoluteY = 8
        private const val mRelative = 9
        private const val mIndirect = 10
        private const val mIndirectZP = 11
        private const val mAccum = 12
        private const val mImplied = 13
        private const val mAIndirectX = 14

        private val digits = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
        )

        private fun dumpHexByte(b: StringBuilder, value: Int) {
            b.append('$')
            b.append(digits[(value shr 4) and 0xf])
            b.append(digits[value and 0xf])
        }

        private fun dumpHexWord(b: StringBuilder, value: Int) {
            b.append('$')
            b.append(digits[(value shr 12) and 0xf])
            b.append(digits[(value shr 8) and 0xf])
            b.append(digits[(value shr 4) and 0xf])
            b.append(digits[value and 0xf])
        }

        private val mneumonics = arrayOf(
            "BRK", "ORA", "???", "???", "TSB", "ORA", "ASL", "???", "PHP", "ORA", "ASL", "???", "TSB", "ORA", "ASL", "???",
            "BPL", "ORA", "ORA", "???", "TRB", "ORA", "ASL", "???", "CLC", "ORA", "INC A", "???", "TRB", "ORA", "ASL", "???",
            "JSR", "AND", "???", "???", "BIT", "AND", "ROL", "???", "PLP", "AND", "ROL", "???", "BIT", "AND", "ROL", "???",
            "BMI", "AND", "AND", "???", "BIT", "AND", "ROL", "???", "SEC", "AND", "DEC A", "???", "BIT", "AND", "ROL", "???",
            "RTI", "EOR", "???", "???", "???", "EOR", "LSR", "???", "PHA", "EOR", "LSR", "???", "JMP", "EOR", "LSR", "???",
            "BVC", "EOR", "EOR", "???", "???", "EOR", "LSR", "???", "CLI", "EOR", "PHY", "???", "???", "EOR", "LSR", "???",
            "RTS", "ADC", "???", "???", "STZ", "ADC", "ROR", "???", "PLA", "ADC", "ROR", "???", "JMP", "ADC", "ROR", "???",
            "BVS", "ADC", "ADC", "???", "STZ", "ADC", "ROR", "???", "SEI", "ADC", "PLY", "???", "JMP", "ADC", "ROR", "???",
            "BRA", "STA", "???", "???", "STY", "STA", "STX", "???", "DEY", "BIT", "TXA", "???", "STY", "STA", "STX", "???",
            "BCC", "STA", "STA", "???", "STY", "STA", "STX", "???", "TYA", "STA", "TXS", "???", "STZ", "STA", "STZ", "???",
            "LDY", "LDA", "LDX", "???", "LDY", "LDA", "LDX", "???", "TAY", "LDA", "TAX", "???", "LDY", "LDA", "LDX", "???",
            "BCS", "LDA", "LDA", "???", "LDY", "LDA", "LDX", "???", "CLV", "LDA", "TSX", "???", "LDY", "LDA", "LDX", "???",
            "CPY", "CMP", "???", "???", "CPY", "CMP", "DEC", "???", "INY", "CMP", "DEX", "???", "CPY", "CMP", "DEC", "???",
            "BNE", "CMP", "???", "???", "???", "CMP", "DEC", "???", "CLD", "CMP", "PHX", "???", "???", "CMP", "DEC", "???",
            "CPX", "SBC", "???", "???", "CPX", "SBC", "INC", "???", "INX", "SBC", "NOP", "???", "CPX", "SBC", "INC", "???",
            "BEQ", "SBC", "SBC", "???", "???", "SBC", "INC", "???", "SED", "SBC", "PLX", "???", "???", "SBC", "INC", "???",
        )

        private val modeTable = intArrayOf(
            mImplied, mIndirectX, mImplied, mImplied, mZeroPage, mZeroPage, mZeroPage, mImplied, mImplied, mImmediate, mAccum, mImplied, mAbsolute, mAbsolute, mAbsolute, mImplied,
            mRelative, mIndirectY, mIndirectZP, mImplied, mZeroPage, mZeroPageX, mZeroPageX, mImplied, mImplied, mAbsoluteY, mImplied, mImplied, mAbsolute, mAbsoluteX, mAbsoluteX, mImplied,
            mAbsolute, mIndirectX, mImplied, mImplied, mZeroPage, mZeroPage, mZeroPage, mImplied, mImplied, mImmediate, mAccum, mImplied, mAbsolute, mAbsolute, mAbsolute, mImplied,
            mRelative, mIndirectY, mIndirectZP, mImplied, mZeroPageX, mZeroPageX, mZeroPageX, mImplied, mImplied, mAbsoluteY, mImplied, mImplied, mAbsoluteX, mAbsoluteX, mAbsoluteX, mImplied,
            mImplied, mIndirectX, mImplied, mImplied, mImplied, mZeroPage, mZeroPage, mImplied, mImplied, mImmediate, mAccum, mImplied, mAbsolute, mAbsolute, mAbsolute, mImplied,
            mRelative, mIndirectY, mIndirectZP, mImplied, mImplied, mZeroPageX, mZeroPageX, mImplied, mImplied, mAbsoluteY, mImplied, mImplied, mImplied, mAbsoluteX, mAbsoluteX, mImplied,
            mImplied, mIndirectX, mImplied, mImplied, mZeroPage, mZeroPage, mZeroPage, mImplied, mImplied, mImmediate, mAccum, mImplied, mIndirect, mAbsolute, mAbsolute, mImplied,
            mRelative, mIndirectY, mIndirectZP, mImplied, mZeroPageX, mZeroPageX, mZeroPageX, mImplied, mImplied, mAbsoluteY, mImplied, mImplied, mAbsoluteX, mAbsoluteX, mAbsoluteX, mImplied,
            mRelative, mIndirectX, mImplied, mImplied, mZeroPage, mZeroPage, mZeroPage, mImplied, mImplied, mImmediate, mImplied, mImplied, mAbsolute, mAbsolute, mAbsolute, mImplied,
            mRelative, mIndirectY, mIndirectZP, mImplied, mZeroPageX, mZeroPageX, mZeroPageY, mImplied, mImplied, mAbsoluteY, mImplied, mImplied, mAbsolute, mAbsoluteX, mAbsoluteX, mImplied,
            mImmediate, mIndirectX, mImmediate, mImplied, mZeroPage, mZeroPage, mZeroPage, mImplied, mImplied, mImmediate, mImplied, mImplied, mAbsolute, mAbsolute, mAbsolute, mImplied,
            mRelative, mIndirectY, mIndirectZP, mImplied, mZeroPageX, mZeroPageX, mZeroPageY, mImplied, mImplied, mAbsoluteY, mImplied, mImplied, mAbsoluteX, mAbsoluteX, mAbsoluteY, mImplied,
            mImmediate, mIndirectX, mImplied, mImplied, mZeroPage, mZeroPage, mZeroPage, mImplied, mImplied, mImmediate, mImplied, mImplied, mAbsolute, mAbsolute, mAbsolute, mImplied,
            mRelative, mIndirectY, mImplied, mImplied, mImplied, mZeroPageX, mZeroPageX, mImplied, mImplied, mAbsoluteY, mImplied, mImplied, mImplied, mAbsoluteX, mAbsoluteX, mImplied,
            mImmediate, mIndirectX, mImplied, mImplied, mZeroPage, mZeroPage, mZeroPage, mImplied, mImplied, mImmediate, mImplied, mImplied, mAbsolute, mAbsolute, mAbsolute, mImplied,
            mRelative, mIndirectY, mIndirectZP, mImplied, mImplied, mZeroPageX, mZeroPageX, mImplied, mImplied, mAbsoluteY, mImplied, mImplied, mImplied, mAbsoluteX, mAbsoluteX, mImplied,
        )
    }

    var a: Int = 0
    var x: Int = 0
    var y: Int = 0
    private var sp: Int = 0xff
    private var flags: Int = rFlag
    var halt: Boolean = false

    fun bFlagSet(): Boolean = (flags and bFlag) != 0
    fun dFlagSet(): Boolean = (flags and dFlag) != 0

    private fun complement(x: Int): Int = if (x < 128) x else -1 * ((x xor 0xff) + 1)
    private fun BINtoBCD(v: Int): Int = (((v / 10) % 10) shl 4) or (v % 10)
    private fun BCDtoBIN(v: Int): Int = ((v shr 4) * 10) + (v and 0xf)

    fun setNZ(v: Int) {
        if (v == 0) {
            flags = flags or zFlag
        } else {
            flags = flags and nzFlag
        }
        if ((v and 0x80) != 0) {
            flags = flags or nFlag
        } else {
            flags = flags and nnFlag
        }
    }

    fun setC(v: Boolean) {
        if (v) {
            flags = flags or cFlag
        } else {
            flags = flags and ncFlag
        }
    }

    private fun setV(v: Boolean) {
        if (v) {
            flags = flags or vFlag
        } else {
            flags = flags and nvFlag
        }
    }

    private fun setI(v: Boolean) {
        if (v) {
            flags = flags or iFlag
        } else {
            flags = flags and niFlag
        }
    }

    private fun setD(v: Boolean) {
        if (v) {
            flags = flags or dFlag
        } else {
            flags = flags and ndFlag
        }
    }

    private fun setB(v: Boolean) {
        if (v) {
            flags = flags or bFlag
        } else {
            flags = flags and nbFlag
        }
    }

    private fun setN(v: Boolean) {
        if (v) {
            flags = flags or nFlag
        } else {
            flags = flags and nnFlag
        }
    }

    private fun setZ(v: Boolean) {
        if (v) {
            flags = flags or zFlag
        } else {
            flags = flags and nzFlag
        }
    }

    private fun pushByte(what: Int) {
        mem.write(sp + 256, what)
        sp--
        sp = sp and 0xff
    }

    private fun pushWord(what: Int) {
        pushByte(what shr 8)
        pushByte(what and 0xff)
    }

    private fun popByte(): Int {
        sp++
        sp = sp and 0xff
        return mem.read(sp + 256)
    }

    private fun popWord(): Int {
        val v = popByte()
        return v + popByte() * 256
    }

    private fun fetch(): Int {
        pc++
        return mem.read(pc - 1)
    }

    private fun fetchWord(): Int {
        val v = fetch()
        return v + fetch() * 256
    }

    private fun wordAt(where: Int): Int {
        val v = mem.read(where)
        return v + mem.read(where + 1) * 256
    }

    private fun ea(mode: Int): Int {
        var v = -1
        when (mode) {
            mIndirectX -> v = wordAt((fetch() + x) and 0xff)
            mZeroPage -> v = fetch()
            mImmediate -> v = fetch()
            mAbsolute -> v = fetchWord()
            mIndirectY -> v = wordAt(fetch()) + y
            mZeroPageX -> v = 0xff and (fetch() + x)
            mZeroPageY -> v = 0xff and (fetch() + y)
            mAbsoluteX -> v = fetchWord() + x
            mAbsoluteY -> v = fetchWord() + y
            mRelative -> v = complement(fetch()) + pc
            mIndirect -> v = wordAt(fetchWord())
            mAIndirectX -> v = wordAt(fetchWord() + x)
            mIndirectZP -> v = wordAt(fetch())
            mAccum -> v = a
        }
        return v
    }

    private fun cea(mode: Int): Int {
        val v = ea(mode)
        return if (mode == mImmediate) {
            v
        } else {
            mem.read(v)
        }
    }

    private fun doADC(mode: Int) {
        var m = cea(mode)
        if (dFlagSet()) {
            m = BCDtoBIN(a) + BCDtoBIN(m) + (flags and cFlag)
            setC(m > 99)
            a = BINtoBCD(m)
            setNZ(a)
            return
        }
        val r = a + m + (flags and cFlag)
        setC(r > 255)
        val res = r and 255
        setNZ(res)
        setV(((a and 0x80) == (m and 0x80)) && ((res and 0x80) != (a and 0x80)))
        a = res
    }

    private fun doAND(mode: Int) {
        a = a and cea(mode)
        setNZ(a)
    }

    private fun doASL(mode: Int) {
        if (mode == mAccum) {
            a = a shl 1
            if (a > 255) {
                setC(true)
                a = a and 255
            } else {
                setC(false)
            }
            setNZ(a)
        } else {
            val w = ea(mode)
            var v = mem.read(w)
            v = v shl 1
            if (v > 255) {
                setC(true)
                v = v and 255
            } else {
                setC(false)
            }
            mem.write(w, v)
            setNZ(v)
        }
    }

    private fun doBCC(mode: Int) {
        val dst = ea(mode)
        if ((cFlag and flags) == 0) pc = dst
    }

    private fun doBCS(mode: Int) {
        val dst = ea(mode)
        if ((cFlag and flags) != 0) pc = dst
    }

    private fun doBEQ(mode: Int) {
        val dst = ea(mode)
        if ((zFlag and flags) != 0) pc = dst
    }

    private fun doBIT(mode: Int) {
        val v = cea(mode)
        setV((v and 0x40) != 0)
        setN((v and 0x80) != 0)
        setZ((a and v) == 0)
    }

    private fun doBMI(mode: Int) {
        val dst = ea(mode)
        if ((nFlag and flags) != 0) pc = dst
    }

    private fun doBNE(mode: Int) {
        val dst = ea(mode)
        if ((zFlag and flags) == 0) pc = dst
    }

    private fun doBPL(mode: Int) {
        val dst = ea(mode)
        if ((nFlag and flags) == 0) pc = dst
    }

    private fun doBVC(mode: Int) {
        val dst = ea(mode)
        if ((vFlag and flags) == 0) pc = dst
    }

    private fun doBVS(mode: Int) {
        val dst = ea(mode)
        if ((vFlag and flags) != 0) pc = dst
    }

    fun doCLC(mode: Int) = setC(false)
    private fun doCLD(mode: Int) = setD(false)
    private fun doCLI(mode: Int) = setI(false)
    private fun doCLV(mode: Int) = setV(false)

    private fun doCMP(mode: Int) {
        val m = cea(mode)
        setC(a >= m)
        val res = a - m
        setNZ(res and 0xff) // Use 0xff to handle potential negative from subtraction
    }

    private fun doCPX(mode: Int) {
        val m = cea(mode)
        setC(x >= m)
        val res = x - m
        setNZ(res and 0xff)
    }

    private fun doCPY(mode: Int) {
        val m = cea(mode)
        setC(y >= m)
        val res = y - m
        setNZ(res and 0xff)
    }

    private fun doDEC(mode: Int) {
        val w = ea(mode)
        val v = (mem.read(w) - 1) and 0xff
        mem.write(w, v)
        setNZ(v)
    }

    private fun doDEX(mode: Int) {
        x = (x - 1) and 0xff
        setNZ(x)
    }

    private fun doDEY(mode: Int) {
        y = (y - 1) and 0xff
        setNZ(y)
    }

    private fun doEOR(mode: Int) {
        a = a xor cea(mode)
        setNZ(a)
    }

    private fun doINC(mode: Int) {
        val w = ea(mode)
        val v = (mem.read(w) + 1) and 0xff
        mem.write(w, v)
        setNZ(v)
    }

    private fun doINX(mode: Int) {
        x = (x + 1) and 0xff
        setNZ(x)
    }

    private fun doINY(mode: Int) {
        y = (y + 1) and 0xff
        setNZ(y)
    }

    private fun doJMP(mode: Int) {
        pc = ea(mode)
    }

    private fun doJSR(mode: Int) {
        val m = ea(mode)
        pushWord(pc - 1)
        pc = m
    }

    private fun doLDA(mode: Int) {
        a = cea(mode)
        setNZ(a)
    }

    private fun doLDX(mode: Int) {
        x = cea(mode)
        setNZ(x)
    }

    private fun doLDY(mode: Int) {
        y = cea(mode)
        setNZ(y)
    }

    private fun doLSR(mode: Int) {
        if (mode == mAccum) {
            setC((a and 1) != 0)
            a = a shr 1
            setNZ(a)
        } else {
            val m = ea(mode)
            var v = mem.read(m)
            setC((v and 1) != 0)
            v = v shr 1
            mem.write(m, v)
            setNZ(v)
        }
    }

    private fun doNOP(mode: Int) {}

    private fun doORA(mode: Int) {
        a = a or cea(mode)
        setNZ(a)
    }

    private fun doPHA(mode: Int) = pushByte(a)

    private fun doPHP(mode: Int) {
        val pFlags = flags or rFlag or bFlag
        pushByte(pFlags)
    }

    private fun doPLA(mode: Int) {
        a = popByte()
        setNZ(a)
    }

    private fun doPLP(mode: Int) {
        flags = popByte()
    }

    private fun doROL(mode: Int) {
        if (mode == mAccum) {
            a = a shl 1
            a = a or (flags and cFlag)
            if (a > 255) {
                a = a and 0xff
                setC(true)
            } else {
                setC(false)
            }
            setNZ(a)
        } else {
            val m = ea(mode)
            var v = mem.read(m)
            v = v shl 1
            v = v or (flags and cFlag)
            if (v > 255) {
                v = v and 0xff
                setC(true)
            } else {
                setC(false)
            }
            mem.write(m, v)
            setNZ(v)
        }
    }

    private fun doROR(mode: Int) {
        if (mode == mAccum) {
            if ((cFlag and flags) != 0) a = a or 0x100
            setC((a and 1) != 0)
            a = a shr 1
            setNZ(a)
        } else {
            val m = ea(mode)
            var v = mem.read(m)
            if ((cFlag and flags) != 0) v = v or 0x100
            setC((v and 1) != 0)
            v = v shr 1
            mem.write(m, v)
            setNZ(v)
        }
    }

    private fun doRTI(mode: Int) {
        doPLP(mode)
        pc = popWord()
    }

    fun doRTS(mode: Int) {
        pc = popWord() + 1
    }

    private fun doSBC(mode: Int) {
        val m = cea(mode)
        if (dFlagSet()) {
            var res = BCDtoBIN(a) - BCDtoBIN(m) - 1 + (flags and cFlag)
            if ((res and 0xff00) == 0) {
                setC(true)
            } else {
                setC(false)
                res += 100
            }
            a = BINtoBCD(res)
            setNZ(a)
            return
        }
        val r = a - m - 1 + (flags and cFlag)
        setC((r and 0xff00) == 0)
        val res = r and 0xff
        setNZ(res)
        setV(((a and 0x80) != (m and 0x80)) && ((res and 0x80) != (a and 0x80)))
        a = res
    }

    fun doSEC(mode: Int) = setC(true)
    private fun doSED(mode: Int) = setD(true)
    private fun doSEI(mode: Int) = setI(true)

    private fun doSTA(mode: Int) = mem.write(ea(mode), a)
    private fun doSTX(mode: Int) = mem.write(ea(mode), x)
    private fun doSTY(mode: Int) = mem.write(ea(mode), y)

    private fun doTAX(mode: Int) {
        x = a
        setNZ(a)
    }

    private fun doTAY(mode: Int) {
        y = a
        setNZ(a)
    }

    private fun doTSX(mode: Int) {
        x = sp
        setNZ(x)
    }

    private fun doTXA(mode: Int) {
        a = x
        setNZ(a)
    }

    private fun doTXS(mode: Int) {
        sp = x
    }

    private fun doTYA(mode: Int) {
        a = y
        setNZ(a)
    }

    private fun doILL(mode: Int) {
        halt = true
    }

    /* ================================================================ */
    /* 65C02 OPCODES                                                    */
    /* ================================================================ */

    private fun doBRA(mode: Int) {
        pc = ea(mode)
    }

    private fun doDEA(mode: Int) {
        a = (a - 1) and 0xff
        setNZ(a)
    }

    private fun doINA(mode: Int) {
        a = (a + 1) and 0xff
        setNZ(a)
    }

    private fun doPHX(mode: Int) = pushByte(x)
    private fun doPHY(mode: Int) = pushByte(y)

    private fun doPLX(mode: Int) {
        x = popByte()
        setNZ(x)
    }

    private fun doPLY(mode: Int) {
        y = popByte()
        setNZ(y)
    }

    private fun doSTZ(mode: Int) = mem.write(ea(mode), 0)

    private fun doTRB(mode: Int) {
        val m = ea(mode)
        var v = mem.read(m)
        v = v and (0xff xor a)
        mem.write(m, v)
        setZ(v == 0)
    }

    private fun doTSB(mode: Int) {
        val m = ea(mode)
        var v = mem.read(m)
        v = v or a
        mem.write(m, v)
        setZ(v == 0)
    }

    private fun interrupt(isBRK: Boolean) {
        pushWord(pc)
        setB(isBRK)
        setI(true)
        pushByte(flags)
        pc = wordAt(0xfffe)
    }

    fun tryInterrupt() {
        if ((iFlag and flags) == 0) interrupt(false)
    }

    fun jumpTo(where: Int) {
        pc = where
    }

    private fun doBRK(mode: Int) {
        pushWord(pc + 1)
        setB(true)
        doPHP(0)
        setI(true)
        pc = wordAt(0xfffe)
    }

    fun step() {
        val i = fetch()
        when (i) {
            0 -> doBRK(mImplied)
            1 -> doORA(mIndirectX)
            2 -> doILL(mImplied)
            3 -> doILL(mImplied)
            4 -> doTSB(mZeroPage)
            5 -> doORA(mZeroPage)
            6 -> doASL(mZeroPage)
            7 -> doNOP(mImplied)
            8 -> doPHP(mImplied)
            9 -> doORA(mImmediate)
            10 -> doASL(mAccum)
            11 -> doILL(mImplied)
            12 -> doTSB(mAbsolute)
            13 -> doORA(mAbsolute)
            14 -> doASL(mAbsolute)
            15 -> doILL(mImplied)
            16 -> doBPL(mRelative)
            17 -> doORA(mIndirectY)
            18 -> doORA(mIndirectZP)
            19 -> doILL(mImplied)
            20 -> doTRB(mZeroPage)
            21 -> doORA(mZeroPageX)
            22 -> doASL(mZeroPageX)
            23 -> doILL(mImplied)
            24 -> doCLC(mImplied)
            25 -> doORA(mAbsoluteY)
            26 -> doINA(mImplied)
            27 -> doILL(mImplied)
            28 -> doTRB(mAbsolute)
            29 -> doORA(mAbsoluteX)
            30 -> doASL(mAbsoluteX)
            31 -> doILL(mImplied)
            32 -> doJSR(mAbsolute)
            33 -> doAND(mIndirectX)
            34 -> doILL(mImplied)
            35 -> doILL(mImplied)
            36 -> doBIT(mZeroPage)
            37 -> doAND(mZeroPage)
            38 -> doROL(mZeroPage)
            39 -> doILL(mImplied)
            40 -> doPLP(mImplied)
            41 -> doAND(mImmediate)
            42 -> doROL(mAccum)
            43 -> doILL(mImplied)
            44 -> doBIT(mAbsolute)
            45 -> doAND(mAbsolute)
            46 -> doROL(mAbsolute)
            47 -> doILL(mImplied)
            48 -> doBMI(mRelative)
            49 -> doAND(mIndirectY)
            50 -> doAND(mIndirectZP)
            51 -> doILL(mImplied)
            52 -> doBIT(mZeroPageX)
            53 -> doAND(mZeroPageX)
            54 -> doROL(mZeroPageX)
            55 -> doILL(mImplied)
            56 -> doSEC(mImplied)
            57 -> doAND(mAbsoluteY)
            58 -> doDEA(mImplied)
            59 -> doILL(mImplied)
            60 -> doBIT(mAbsoluteX)
            61 -> doAND(mAbsoluteX)
            62 -> doROL(mAbsoluteX)
            63 -> doILL(mImplied)
            64 -> doRTI(mImplied)
            65 -> doEOR(mIndirectX)
            66 -> doILL(mImplied)
            67 -> doILL(mImplied)
            68 -> doILL(mImplied)
            69 -> doEOR(mZeroPage)
            70 -> doLSR(mZeroPage)
            71 -> doILL(mImplied)
            72 -> doPHA(mImplied)
            73 -> doEOR(mImmediate)
            74 -> doLSR(mAccum)
            75 -> doILL(mImplied)
            76 -> doJMP(mAbsolute)
            77 -> doEOR(mAbsolute)
            78 -> doLSR(mAbsolute)
            79 -> doILL(mImplied)
            80 -> doBVC(mRelative)
            81 -> doEOR(mIndirectY)
            82 -> doEOR(mIndirectZP)
            83 -> doILL(mImplied)
            84 -> doILL(mImplied)
            85 -> doEOR(mZeroPageX)
            86 -> doLSR(mZeroPageX)
            87 -> doILL(mImplied)
            88 -> doCLI(mImplied)
            89 -> doEOR(mAbsoluteY)
            90 -> doPHY(mImplied)
            91 -> doILL(mImplied)
            92 -> doILL(mImplied)
            93 -> doEOR(mAbsoluteX)
            94 -> doLSR(mAbsoluteX)
            95 -> doILL(mImplied)
            96 -> doRTS(mImplied)
            97 -> doADC(mIndirectX)
            98 -> doILL(mImplied)
            99 -> doILL(mImplied)
            100 -> doSTZ(mZeroPage)
            101 -> doADC(mZeroPage)
            102 -> doROR(mZeroPage)
            103 -> doILL(mImplied)
            104 -> doPLA(mImplied)
            105 -> doADC(mImmediate)
            106 -> doROR(mAccum)
            107 -> doILL(mImplied)
            108 -> doJMP(mIndirect)
            109 -> doADC(mAbsolute)
            110 -> doROR(mAbsolute)
            111 -> doILL(mImplied)
            112 -> doBVS(mRelative)
            113 -> doADC(mIndirectY)
            114 -> doADC(mIndirectZP)
            115 -> doILL(mImplied)
            116 -> doSTZ(mZeroPageX)
            117 -> doADC(mZeroPageX)
            118 -> doROR(mZeroPageX)
            119 -> doILL(mImplied)
            120 -> doSEI(mImplied)
            121 -> doADC(mAbsoluteY)
            122 -> doPLY(mImplied)
            123 -> doILL(mImplied)
            124 -> doJMP(mAIndirectX)
            125 -> doADC(mAbsoluteX)
            126 -> doROR(mAbsoluteX)
            127 -> doILL(mImplied)
            128 -> doBRA(mRelative)
            129 -> doSTA(mIndirectX)
            130 -> doILL(mImplied)
            131 -> doILL(mImplied)
            132 -> doSTY(mZeroPage)
            133 -> doSTA(mZeroPage)
            134 -> doSTX(mZeroPage)
            135 -> doILL(mImplied)
            136 -> doDEY(mImplied)
            137 -> doBIT(mImmediate)
            138 -> doTXA(mImplied)
            139 -> doILL(mImplied)
            140 -> doSTY(mAbsolute)
            141 -> doSTA(mAbsolute)
            142 -> doSTX(mAbsolute)
            143 -> doILL(mImplied)
            144 -> doBCC(mRelative)
            145 -> doSTA(mIndirectY)
            146 -> doSTA(mIndirectZP)
            147 -> doILL(mImplied)
            148 -> doSTY(mZeroPageX)
            149 -> doSTA(mZeroPageX)
            150 -> doSTX(mZeroPageY)
            151 -> doILL(mImplied)
            152 -> doTYA(mImplied)
            153 -> doSTA(mAbsoluteY)
            154 -> doTXS(mImplied)
            155 -> doILL(mImplied)
            156 -> doSTZ(mAbsolute)
            157 -> doSTA(mAbsoluteX)
            158 -> doSTZ(mAbsoluteX)
            159 -> doILL(mImplied)
            160 -> doLDY(mImmediate)
            161 -> doLDA(mIndirectX)
            162 -> doLDX(mImmediate)
            163 -> doILL(mImplied)
            164 -> doLDY(mZeroPage)
            165 -> doLDA(mZeroPage)
            166 -> doLDX(mZeroPage)
            167 -> doILL(mImplied)
            168 -> doTAY(mImplied)
            169 -> doLDA(mImmediate)
            170 -> doTAX(mImplied)
            171 -> doILL(mImplied)
            172 -> doLDY(mAbsolute)
            173 -> doLDA(mAbsolute)
            174 -> doLDX(mAbsolute)
            175 -> doILL(mImplied)
            176 -> doBCS(mRelative)
            177 -> doLDA(mIndirectY)
            178 -> doLDA(mIndirectZP)
            179 -> doILL(mImplied)
            180 -> doLDY(mZeroPageX)
            181 -> doLDA(mZeroPageX)
            182 -> doLDX(mZeroPageY)
            183 -> doILL(mImplied)
            184 -> doCLV(mImplied)
            185 -> doLDA(mAbsoluteY)
            186 -> doTSX(mImplied)
            187 -> doILL(mImplied)
            188 -> doLDY(mAbsoluteX)
            189 -> doLDA(mAbsoluteX)
            190 -> doLDX(mAbsoluteY)
            191 -> doILL(mImplied)
            192 -> doCPY(mImmediate)
            193 -> doCMP(mIndirectX)
            194 -> doILL(mImplied)
            195 -> doILL(mImplied)
            196 -> doCPY(mZeroPage)
            197 -> doCMP(mZeroPage)
            198 -> doDEC(mZeroPage)
            199 -> doILL(mImplied)
            200 -> doINY(mImplied)
            201 -> doCMP(mImmediate)
            202 -> doDEX(mImplied)
            203 -> doILL(mImplied)
            204 -> doCPY(mAbsolute)
            205 -> doCMP(mAbsolute)
            206 -> doDEC(mAbsolute)
            207 -> doILL(mImplied)
            208 -> doBNE(mRelative)
            209 -> doCMP(mIndirectY)
            210 -> doCMP(mIndirectZP)
            211 -> doILL(mImplied)
            212 -> doILL(mImplied)
            213 -> doCMP(mZeroPageX)
            214 -> doDEC(mZeroPageX)
            215 -> doILL(mImplied)
            216 -> doCLD(mImplied)
            217 -> doCMP(mAbsoluteY)
            218 -> doPHX(mImplied)
            219 -> doILL(mImplied)
            220 -> doILL(mImplied)
            221 -> doCMP(mAbsoluteX)
            222 -> doDEC(mAbsoluteX)
            223 -> doILL(mImplied)
            224 -> doCPX(mImmediate)
            225 -> doSBC(mIndirectX)
            226 -> doILL(mImplied)
            227 -> doILL(mImplied)
            228 -> doCPX(mZeroPage)
            229 -> doSBC(mZeroPage)
            230 -> doINC(mZeroPage)
            231 -> doILL(mImplied)
            232 -> doINX(mImplied)
            233 -> doSBC(mImmediate)
            234 -> doNOP(mImplied)
            235 -> doILL(mImplied)
            236 -> doCPX(mAbsolute)
            237 -> doSBC(mAbsolute)
            238 -> doINC(mAbsolute)
            239 -> doILL(mImplied)
            240 -> doBEQ(mRelative)
            241 -> doSBC(mIndirectY)
            242 -> doSBC(mIndirectZP)
            243 -> doILL(mImplied)
            244 -> doILL(mImplied)
            245 -> doSBC(mZeroPageX)
            246 -> doINC(mZeroPageX)
            247 -> doILL(mImplied)
            248 -> doSED(mImplied)
            249 -> doSBC(mAbsoluteY)
            250 -> doPLX(mImplied)
            251 -> doILL(mImplied)
            252 -> doILL(mImplied)
            253 -> doSBC(mAbsoluteX)
            254 -> doINC(mAbsoluteX)
            255 -> doILL(mImplied)
        }
    }

    fun dump(): String {
        val b = StringBuilder()
        b.append("PC = ")
        dumpHexWord(b, pc)
        b.append(" A = ")
        dumpHexByte(b, a)
        b.append(" X = ")
        dumpHexByte(b, x)
        b.append(" Y = ")
        dumpHexByte(b, y)
        b.append(" SP = ")
        dumpHexByte(b, sp)
        dumpFlags(b)
        b.append(' ')
        b.append(disassemble(pc))
        return b.toString()
    }

    private fun dumpFlags(b: StringBuilder) {
        b.append(if ((flags and nFlag) == 0) " n" else " N")
        b.append(if ((flags and vFlag) == 0) 'v' else 'V')
        b.append(if ((flags and rFlag) == 0) 'r' else 'R')
        b.append(if ((flags and bFlag) == 0) 'b' else 'B')
        b.append(if ((flags and dFlag) == 0) 'd' else 'D')
        b.append(if ((flags and iFlag) == 0) 'i' else 'I')
        b.append(if ((flags and zFlag) == 0) 'z' else 'Z')
        b.append(if ((flags and cFlag) == 0) 'c' else 'C')
        b.append(' ')
    }

    fun disassemble(where: Int): String {
        val b = StringBuilder()
        val opcode = mem.read(where)
        b.append(mneumonics[opcode])

        when (modeTable[opcode]) {
            mIndirectX -> {
                b.append(" (")
                dumpHexByte(b, mem.read(where + 1))
                b.append(", X)")
            }
            mZeroPage -> {
                b.append(' ')
                dumpHexByte(b, mem.read(where + 1))
            }
            mImmediate -> {
                b.append(" #")
                dumpHexByte(b, mem.read(where + 1))
            }
            mAbsolute -> {
                b.append(' ')
                dumpHexWord(b, wordAt(where + 1))
            }
            mIndirectY -> {
                b.append(" (")
                dumpHexByte(b, mem.read(where + 1))
                b.append("), Y  [")
                dumpHexWord(b, wordAt(mem.read(where + 1)))
                b.append(']')
            }
            mZeroPageX -> {
                b.append(' ')
                dumpHexByte(b, mem.read(where + 1))
                b.append(", X")
            }
            mZeroPageY -> {
                b.append(' ')
                dumpHexByte(b, mem.read(where + 1))
                b.append(", Y")
            }
            mAbsoluteX -> {
                b.append(' ')
                dumpHexWord(b, wordAt(where + 1))
                b.append(", X")
            }
            mAbsoluteY -> {
                b.append(' ')
                dumpHexWord(b, wordAt(where + 1))
                b.append(", Y")
            }
            mRelative -> {
                b.append(' ')
                dumpHexWord(b, where + 2 + complement(mem.read(where + 1)))
            }
            mIndirect -> {
                b.append(" (")
                dumpHexWord(b, wordAt(where + 1))
                b.append(')')
            }
            mIndirectZP -> {
                b.append(" (")
                dumpHexByte(b, mem.read(where + 1))
                b.append(')')
            }
            mAccum -> b.append(" A")
            mImplied -> {}
            else -> b.append(" UNKNOWN MODE")
        }
        return b.toString()
    }
}
