/* Copyright (c) 2007-2026, Eric Scharff
Permission to use, copy, modify, and/or distribute this software
for any purpose with or without fee is hereby granted, provided
that the above copyright notice and this permission notice appear
in all copies.
There is NO WARRANTY for this software.  See LICENSE.txt for
details. */

package a1em

class M6502(
    private val mem: Memory,
    var pc: Int,
) {
    interface Memory {
        fun read(where: Int): Int

        fun write(
            where: Int,
            what: Int,
        )
    }

    companion object {
        const val C_FLAG = 0x01
        const val Z_FLAG = 0x02
        const val I_FLAG = 0x04
        const val D_FLAG = 0x08
        const val B_FLAG = 0x10
        const val R_FLAG = 0x20
        const val V_FLAG = 0x40
        const val N_FLAG = 0x80

        const val NC_FLAG = (C_FLAG.inv()) and 0xff
        const val NZ_FLAG = (Z_FLAG.inv()) and 0xff
        const val NI_FLAG = (I_FLAG.inv()) and 0xff
        const val ND_FLAG = (D_FLAG.inv()) and 0xff
        const val NB_FLAG = (B_FLAG.inv()) and 0xff
        const val NR_FLAG = (R_FLAG.inv()) and 0xff
        const val NV_FLAG = (V_FLAG.inv()) and 0xff
        const val NN_FLAG = (N_FLAG.inv()) and 0xff

        private const val M_INDIRECT_X = 0
        private const val M_ZERO_PAGE = 1
        private const val M_IMMEDIATE = 2
        private const val M_ABSOLUTE = 3
        private const val M_INDIRECT_Y = 4
        private const val M_ZERO_PAGE_X = 5
        private const val M_ZERO_PAGE_Y = 6
        private const val M_ABSOLUTE_X = 7
        private const val M_ABSOLUTE_Y = 8
        private const val M_RELATIVE = 9
        private const val M_INDIRECT = 10
        private const val M_INDIRECT_ZP = 11
        private const val M_ACCUM = 12
        private const val M_IMPLIED = 13
        private const val MA_INDIRECT_X = 14

        private val digits =
            charArrayOf(
                '0',
                '1',
                '2',
                '3',
                '4',
                '5',
                '6',
                '7',
                '8',
                '9',
                'A',
                'B',
                'C',
                'D',
                'E',
                'F',
            )

        private fun dumpHexByte(
            b: StringBuilder,
            value: Int,
        ) {
            b.append('$')
            b.append(digits[(value shr 4) and 0xf])
            b.append(digits[value and 0xf])
        }

        private fun dumpHexWord(
            b: StringBuilder,
            value: Int,
        ) {
            b.append('$')
            b.append(digits[(value shr 12) and 0xf])
            b.append(digits[(value shr 8) and 0xf])
            b.append(digits[(value shr 4) and 0xf])
            b.append(digits[value and 0xf])
        }

        private val mneumonics =
            arrayOf(
                "BRK",
                "ORA",
                "???",
                "???",
                "TSB",
                "ORA",
                "ASL",
                "???",
                "PHP",
                "ORA",
                "ASL",
                "???",
                "TSB",
                "ORA",
                "ASL",
                "???",
                "BPL",
                "ORA",
                "ORA",
                "???",
                "TRB",
                "ORA",
                "ASL",
                "???",
                "CLC",
                "ORA",
                "INC A",
                "???",
                "TRB",
                "ORA",
                "ASL",
                "???",
                "JSR",
                "AND",
                "???",
                "???",
                "BIT",
                "AND",
                "ROL",
                "???",
                "PLP",
                "AND",
                "ROL",
                "???",
                "BIT",
                "AND",
                "ROL",
                "???",
                "BMI",
                "AND",
                "AND",
                "???",
                "BIT",
                "AND",
                "ROL",
                "???",
                "SEC",
                "AND",
                "DEC A",
                "???",
                "BIT",
                "AND",
                "ROL",
                "???",
                "RTI",
                "EOR",
                "???",
                "???",
                "???",
                "EOR",
                "LSR",
                "???",
                "PHA",
                "EOR",
                "LSR",
                "???",
                "JMP",
                "EOR",
                "LSR",
                "???",
                "BVC",
                "EOR",
                "EOR",
                "???",
                "???",
                "EOR",
                "LSR",
                "???",
                "CLI",
                "EOR",
                "PHY",
                "???",
                "???",
                "EOR",
                "LSR",
                "???",
                "RTS",
                "ADC",
                "???",
                "???",
                "STZ",
                "ADC",
                "ROR",
                "???",
                "PLA",
                "ADC",
                "ROR",
                "???",
                "JMP",
                "ADC",
                "ROR",
                "???",
                "BVS",
                "ADC",
                "ADC",
                "???",
                "STZ",
                "ADC",
                "ROR",
                "???",
                "SEI",
                "ADC",
                "PLY",
                "???",
                "JMP",
                "ADC",
                "ROR",
                "???",
                "BRA",
                "STA",
                "???",
                "???",
                "STY",
                "STA",
                "STX",
                "???",
                "DEY",
                "BIT",
                "TXA",
                "???",
                "STY",
                "STA",
                "STX",
                "???",
                "BCC",
                "STA",
                "STA",
                "???",
                "STY",
                "STA",
                "STX",
                "???",
                "TYA",
                "STA",
                "TXS",
                "???",
                "STZ",
                "STA",
                "STZ",
                "???",
                "LDY",
                "LDA",
                "LDX",
                "???",
                "LDY",
                "LDA",
                "LDX",
                "???",
                "TAY",
                "LDA",
                "TAX",
                "???",
                "LDY",
                "LDA",
                "LDX",
                "???",
                "BCS",
                "LDA",
                "LDA",
                "???",
                "LDY",
                "LDA",
                "LDX",
                "???",
                "CLV",
                "LDA",
                "TSX",
                "???",
                "LDY",
                "LDA",
                "LDX",
                "???",
                "CPY",
                "CMP",
                "???",
                "???",
                "CPY",
                "CMP",
                "DEC",
                "???",
                "INY",
                "CMP",
                "DEX",
                "???",
                "CPY",
                "CMP",
                "DEC",
                "???",
                "BNE",
                "CMP",
                "???",
                "???",
                "???",
                "CMP",
                "DEC",
                "???",
                "CLD",
                "CMP",
                "PHX",
                "???",
                "???",
                "CMP",
                "DEC",
                "???",
                "CPX",
                "SBC",
                "???",
                "???",
                "CPX",
                "SBC",
                "INC",
                "???",
                "INX",
                "SBC",
                "NOP",
                "???",
                "CPX",
                "SBC",
                "INC",
                "???",
                "BEQ",
                "SBC",
                "SBC",
                "???",
                "???",
                "SBC",
                "INC",
                "???",
                "SED",
                "SBC",
                "PLX",
                "???",
                "???",
                "SBC",
                "INC",
                "???",
            )

        private val modeTable =
            intArrayOf(
                M_IMPLIED,
                M_INDIRECT_X,
                M_IMPLIED,
                M_IMPLIED,
                M_ZERO_PAGE,
                M_ZERO_PAGE,
                M_ZERO_PAGE,
                M_IMPLIED,
                M_IMPLIED,
                M_IMMEDIATE,
                M_ACCUM,
                M_IMPLIED,
                M_ABSOLUTE,
                M_ABSOLUTE,
                M_ABSOLUTE,
                M_IMPLIED,
                M_RELATIVE,
                M_INDIRECT_Y,
                M_INDIRECT_ZP,
                M_IMPLIED,
                M_ZERO_PAGE,
                M_ZERO_PAGE_X,
                M_ZERO_PAGE_X,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE_Y,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE,
                M_ABSOLUTE_X,
                M_ABSOLUTE_X,
                M_IMPLIED,
                M_ABSOLUTE,
                M_INDIRECT_X,
                M_IMPLIED,
                M_IMPLIED,
                M_ZERO_PAGE,
                M_ZERO_PAGE,
                M_ZERO_PAGE,
                M_IMPLIED,
                M_IMPLIED,
                M_IMMEDIATE,
                M_ACCUM,
                M_IMPLIED,
                M_ABSOLUTE,
                M_ABSOLUTE,
                M_ABSOLUTE,
                M_IMPLIED,
                M_RELATIVE,
                M_INDIRECT_Y,
                M_INDIRECT_ZP,
                M_IMPLIED,
                M_ZERO_PAGE_X,
                M_ZERO_PAGE_X,
                M_ZERO_PAGE_X,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE_Y,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE_X,
                M_ABSOLUTE_X,
                M_ABSOLUTE_X,
                M_IMPLIED,
                M_IMPLIED,
                M_INDIRECT_X,
                M_IMPLIED,
                M_IMPLIED,
                M_IMPLIED,
                M_ZERO_PAGE,
                M_ZERO_PAGE,
                M_IMPLIED,
                M_IMPLIED,
                M_IMMEDIATE,
                M_ACCUM,
                M_IMPLIED,
                M_ABSOLUTE,
                M_ABSOLUTE,
                M_ABSOLUTE,
                M_IMPLIED,
                M_RELATIVE,
                M_INDIRECT_Y,
                M_INDIRECT_ZP,
                M_IMPLIED,
                M_IMPLIED,
                M_ZERO_PAGE_X,
                M_ZERO_PAGE_X,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE_Y,
                M_IMPLIED,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE_X,
                M_ABSOLUTE_X,
                M_IMPLIED,
                M_IMPLIED,
                M_INDIRECT_X,
                M_IMPLIED,
                M_IMPLIED,
                M_ZERO_PAGE,
                M_ZERO_PAGE,
                M_ZERO_PAGE,
                M_IMPLIED,
                M_IMPLIED,
                M_IMMEDIATE,
                M_ACCUM,
                M_IMPLIED,
                M_INDIRECT,
                M_ABSOLUTE,
                M_ABSOLUTE,
                M_IMPLIED,
                M_RELATIVE,
                M_INDIRECT_Y,
                M_INDIRECT_ZP,
                M_IMPLIED,
                M_ZERO_PAGE_X,
                M_ZERO_PAGE_X,
                M_ZERO_PAGE_X,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE_Y,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE_X,
                M_ABSOLUTE_X,
                M_ABSOLUTE_X,
                M_IMPLIED,
                M_RELATIVE,
                M_INDIRECT_X,
                M_IMPLIED,
                M_IMPLIED,
                M_ZERO_PAGE,
                M_ZERO_PAGE,
                M_ZERO_PAGE,
                M_IMPLIED,
                M_IMPLIED,
                M_IMMEDIATE,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE,
                M_ABSOLUTE,
                M_ABSOLUTE,
                M_IMPLIED,
                M_RELATIVE,
                M_INDIRECT_Y,
                M_INDIRECT_ZP,
                M_IMPLIED,
                M_ZERO_PAGE_X,
                M_ZERO_PAGE_X,
                M_ZERO_PAGE_Y,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE_Y,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE,
                M_ABSOLUTE_X,
                M_ABSOLUTE_X,
                M_IMPLIED,
                M_IMMEDIATE,
                M_INDIRECT_X,
                M_IMMEDIATE,
                M_IMPLIED,
                M_ZERO_PAGE,
                M_ZERO_PAGE,
                M_ZERO_PAGE,
                M_IMPLIED,
                M_IMPLIED,
                M_IMMEDIATE,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE,
                M_ABSOLUTE,
                M_ABSOLUTE,
                M_IMPLIED,
                M_RELATIVE,
                M_INDIRECT_Y,
                M_INDIRECT_ZP,
                M_IMPLIED,
                M_ZERO_PAGE_X,
                M_ZERO_PAGE_X,
                M_ZERO_PAGE_Y,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE_Y,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE_X,
                M_ABSOLUTE_X,
                M_ABSOLUTE_Y,
                M_IMPLIED,
                M_IMMEDIATE,
                M_INDIRECT_X,
                M_IMPLIED,
                M_IMPLIED,
                M_ZERO_PAGE,
                M_ZERO_PAGE,
                M_ZERO_PAGE,
                M_IMPLIED,
                M_IMPLIED,
                M_IMMEDIATE,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE,
                M_ABSOLUTE,
                M_ABSOLUTE,
                M_IMPLIED,
                M_RELATIVE,
                M_INDIRECT_Y,
                M_IMPLIED,
                M_IMPLIED,
                M_IMPLIED,
                M_ZERO_PAGE_X,
                M_ZERO_PAGE_X,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE_Y,
                M_IMPLIED,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE_X,
                M_ABSOLUTE_X,
                M_IMPLIED,
                M_IMMEDIATE,
                M_INDIRECT_X,
                M_IMPLIED,
                M_IMPLIED,
                M_ZERO_PAGE,
                M_ZERO_PAGE,
                M_ZERO_PAGE,
                M_IMPLIED,
                M_IMPLIED,
                M_IMMEDIATE,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE,
                M_ABSOLUTE,
                M_ABSOLUTE,
                M_IMPLIED,
                M_RELATIVE,
                M_INDIRECT_Y,
                M_INDIRECT_ZP,
                M_IMPLIED,
                M_IMPLIED,
                M_ZERO_PAGE_X,
                M_ZERO_PAGE_X,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE_Y,
                M_IMPLIED,
                M_IMPLIED,
                M_IMPLIED,
                M_ABSOLUTE_X,
                M_ABSOLUTE_X,
                M_IMPLIED,
            )
    }

    var a: Int = 0
    var x: Int = 0
    var y: Int = 0
    private var sp: Int = 0xff
    private var flags: Int = R_FLAG
    var halt: Boolean = false

    fun bFlagSet(): Boolean = (flags and B_FLAG) != 0

    fun dFlagSet(): Boolean = (flags and D_FLAG) != 0

    private fun complement(x: Int): Int = if (x < 128) x else -1 * ((x xor 0xff) + 1)

    private fun binToBcd(v: Int): Int = (((v / 10) % 10) shl 4) or (v % 10)

    private fun bcdToBin(v: Int): Int = ((v shr 4) * 10) + (v and 0xf)

    fun setNZ(v: Int) {
        if (v == 0) {
            flags = flags or Z_FLAG
        } else {
            flags = flags and NZ_FLAG
        }
        if ((v and 0x80) != 0) {
            flags = flags or N_FLAG
        } else {
            flags = flags and NN_FLAG
        }
    }

    fun setC(v: Boolean) {
        if (v) {
            flags = flags or C_FLAG
        } else {
            flags = flags and NC_FLAG
        }
    }

    private fun setV(v: Boolean) {
        if (v) {
            flags = flags or V_FLAG
        } else {
            flags = flags and NV_FLAG
        }
    }

    private fun setI(v: Boolean) {
        if (v) {
            flags = flags or I_FLAG
        } else {
            flags = flags and NI_FLAG
        }
    }

    private fun setD(v: Boolean) {
        if (v) {
            flags = flags or D_FLAG
        } else {
            flags = flags and ND_FLAG
        }
    }

    private fun setB(v: Boolean) {
        if (v) {
            flags = flags or B_FLAG
        } else {
            flags = flags and NB_FLAG
        }
    }

    private fun setN(v: Boolean) {
        if (v) {
            flags = flags or N_FLAG
        } else {
            flags = flags and NN_FLAG
        }
    }

    private fun setZ(v: Boolean) {
        if (v) {
            flags = flags or Z_FLAG
        } else {
            flags = flags and NZ_FLAG
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
            M_INDIRECT_X -> v = wordAt((fetch() + x) and 0xff)
            M_ZERO_PAGE -> v = fetch()
            M_IMMEDIATE -> v = fetch()
            M_ABSOLUTE -> v = fetchWord()
            M_INDIRECT_Y -> v = wordAt(fetch()) + y
            M_ZERO_PAGE_X -> v = 0xff and (fetch() + x)
            M_ZERO_PAGE_Y -> v = 0xff and (fetch() + y)
            M_ABSOLUTE_X -> v = fetchWord() + x
            M_ABSOLUTE_Y -> v = fetchWord() + y
            M_RELATIVE -> v = complement(fetch()) + pc
            M_INDIRECT -> v = wordAt(fetchWord())
            MA_INDIRECT_X -> v = wordAt(fetchWord() + x)
            M_INDIRECT_ZP -> v = wordAt(fetch())
            M_ACCUM -> v = a
        }
        return v
    }

    private fun cea(mode: Int): Int {
        val v = ea(mode)
        return if (mode == M_IMMEDIATE) {
            v
        } else {
            mem.read(v)
        }
    }

    private fun doADC(mode: Int) {
        var m = cea(mode)
        if (dFlagSet()) {
            m = bcdToBin(a) + bcdToBin(m) + (flags and C_FLAG)
            setC(m > 99)
            a = binToBcd(m)
            setNZ(a)
            return
        }
        val r = a + m + (flags and C_FLAG)
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
        if (mode == M_ACCUM) {
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
        if ((C_FLAG and flags) == 0) pc = dst
    }

    private fun doBCS(mode: Int) {
        val dst = ea(mode)
        if ((C_FLAG and flags) != 0) pc = dst
    }

    private fun doBEQ(mode: Int) {
        val dst = ea(mode)
        if ((Z_FLAG and flags) != 0) pc = dst
    }

    private fun doBIT(mode: Int) {
        val v = cea(mode)
        setV((v and 0x40) != 0)
        setN((v and 0x80) != 0)
        setZ((a and v) == 0)
    }

    private fun doBMI(mode: Int) {
        val dst = ea(mode)
        if ((N_FLAG and flags) != 0) pc = dst
    }

    private fun doBNE(mode: Int) {
        val dst = ea(mode)
        if ((Z_FLAG and flags) == 0) pc = dst
    }

    private fun doBPL(mode: Int) {
        val dst = ea(mode)
        if ((N_FLAG and flags) == 0) pc = dst
    }

    private fun doBVC(mode: Int) {
        val dst = ea(mode)
        if ((V_FLAG and flags) == 0) pc = dst
    }

    private fun doBVS(mode: Int) {
        val dst = ea(mode)
        if ((V_FLAG and flags) != 0) pc = dst
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
        if (mode == M_ACCUM) {
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
        val pFlags = flags or R_FLAG or B_FLAG
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
        if (mode == M_ACCUM) {
            a = a shl 1
            a = a or (flags and C_FLAG)
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
            v = v or (flags and C_FLAG)
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
        if (mode == M_ACCUM) {
            if ((C_FLAG and flags) != 0) a = a or 0x100
            setC((a and 1) != 0)
            a = a shr 1
            setNZ(a)
        } else {
            val m = ea(mode)
            var v = mem.read(m)
            if ((C_FLAG and flags) != 0) v = v or 0x100
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
            var res = bcdToBin(a) - bcdToBin(m) - 1 + (flags and C_FLAG)
            if ((res and 0xff00) == 0) {
                setC(true)
            } else {
                setC(false)
                res += 100
            }
            a = binToBcd(res)
            setNZ(a)
            return
        }
        val r = a - m - 1 + (flags and C_FLAG)
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

    // ================================================================
    // 65C02 OPCODES
    // ================================================================

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
        if ((I_FLAG and flags) == 0) interrupt(false)
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
            0 -> doBRK(M_IMPLIED)
            1 -> doORA(M_INDIRECT_X)
            2 -> doILL(M_IMPLIED)
            3 -> doILL(M_IMPLIED)
            4 -> doTSB(M_ZERO_PAGE)
            5 -> doORA(M_ZERO_PAGE)
            6 -> doASL(M_ZERO_PAGE)
            7 -> doNOP(M_IMPLIED)
            8 -> doPHP(M_IMPLIED)
            9 -> doORA(M_IMMEDIATE)
            10 -> doASL(M_ACCUM)
            11 -> doILL(M_IMPLIED)
            12 -> doTSB(M_ABSOLUTE)
            13 -> doORA(M_ABSOLUTE)
            14 -> doASL(M_ABSOLUTE)
            15 -> doILL(M_IMPLIED)
            16 -> doBPL(M_RELATIVE)
            17 -> doORA(M_INDIRECT_Y)
            18 -> doORA(M_INDIRECT_ZP)
            19 -> doILL(M_IMPLIED)
            20 -> doTRB(M_ZERO_PAGE)
            21 -> doORA(M_ZERO_PAGE_X)
            22 -> doASL(M_ZERO_PAGE_X)
            23 -> doILL(M_IMPLIED)
            24 -> doCLC(M_IMPLIED)
            25 -> doORA(M_ABSOLUTE_Y)
            26 -> doINA(M_IMPLIED)
            27 -> doILL(M_IMPLIED)
            28 -> doTRB(M_ABSOLUTE)
            29 -> doORA(M_ABSOLUTE_X)
            30 -> doASL(M_ABSOLUTE_X)
            31 -> doILL(M_IMPLIED)
            32 -> doJSR(M_ABSOLUTE)
            33 -> doAND(M_INDIRECT_X)
            34 -> doILL(M_IMPLIED)
            35 -> doILL(M_IMPLIED)
            36 -> doBIT(M_ZERO_PAGE)
            37 -> doAND(M_ZERO_PAGE)
            38 -> doROL(M_ZERO_PAGE)
            39 -> doILL(M_IMPLIED)
            40 -> doPLP(M_IMPLIED)
            41 -> doAND(M_IMMEDIATE)
            42 -> doROL(M_ACCUM)
            43 -> doILL(M_IMPLIED)
            44 -> doBIT(M_ABSOLUTE)
            45 -> doAND(M_ABSOLUTE)
            46 -> doROL(M_ABSOLUTE)
            47 -> doILL(M_IMPLIED)
            48 -> doBMI(M_RELATIVE)
            49 -> doAND(M_INDIRECT_Y)
            50 -> doAND(M_INDIRECT_ZP)
            51 -> doILL(M_IMPLIED)
            52 -> doBIT(M_ZERO_PAGE_X)
            53 -> doAND(M_ZERO_PAGE_X)
            54 -> doROL(M_ZERO_PAGE_X)
            55 -> doILL(M_IMPLIED)
            56 -> doSEC(M_IMPLIED)
            57 -> doAND(M_ABSOLUTE_Y)
            58 -> doDEA(M_IMPLIED)
            59 -> doILL(M_IMPLIED)
            60 -> doBIT(M_ABSOLUTE_X)
            61 -> doAND(M_ABSOLUTE_X)
            62 -> doROL(M_ABSOLUTE_X)
            63 -> doILL(M_IMPLIED)
            64 -> doRTI(M_IMPLIED)
            65 -> doEOR(M_INDIRECT_X)
            66 -> doILL(M_IMPLIED)
            67 -> doILL(M_IMPLIED)
            68 -> doILL(M_IMPLIED)
            69 -> doEOR(M_ZERO_PAGE)
            70 -> doLSR(M_ZERO_PAGE)
            71 -> doILL(M_IMPLIED)
            72 -> doPHA(M_IMPLIED)
            73 -> doEOR(M_IMMEDIATE)
            74 -> doLSR(M_ACCUM)
            75 -> doILL(M_IMPLIED)
            76 -> doJMP(M_ABSOLUTE)
            77 -> doEOR(M_ABSOLUTE)
            78 -> doLSR(M_ABSOLUTE)
            79 -> doILL(M_IMPLIED)
            80 -> doBVC(M_RELATIVE)
            81 -> doEOR(M_INDIRECT_Y)
            82 -> doEOR(M_INDIRECT_ZP)
            83 -> doILL(M_IMPLIED)
            84 -> doILL(M_IMPLIED)
            85 -> doEOR(M_ZERO_PAGE_X)
            86 -> doLSR(M_ZERO_PAGE_X)
            87 -> doILL(M_IMPLIED)
            88 -> doCLI(M_IMPLIED)
            89 -> doEOR(M_ABSOLUTE_Y)
            90 -> doPHY(M_IMPLIED)
            91 -> doILL(M_IMPLIED)
            92 -> doILL(M_IMPLIED)
            93 -> doEOR(M_ABSOLUTE_X)
            94 -> doLSR(M_ABSOLUTE_X)
            95 -> doILL(M_IMPLIED)
            96 -> doRTS(M_IMPLIED)
            97 -> doADC(M_INDIRECT_X)
            98 -> doILL(M_IMPLIED)
            99 -> doILL(M_IMPLIED)
            100 -> doSTZ(M_ZERO_PAGE)
            101 -> doADC(M_ZERO_PAGE)
            102 -> doROR(M_ZERO_PAGE)
            103 -> doILL(M_IMPLIED)
            104 -> doPLA(M_IMPLIED)
            105 -> doADC(M_IMMEDIATE)
            106 -> doROR(M_ACCUM)
            107 -> doILL(M_IMPLIED)
            108 -> doJMP(M_INDIRECT)
            109 -> doADC(M_ABSOLUTE)
            110 -> doROR(M_ABSOLUTE)
            111 -> doILL(M_IMPLIED)
            112 -> doBVS(M_RELATIVE)
            113 -> doADC(M_INDIRECT_Y)
            114 -> doADC(M_INDIRECT_ZP)
            115 -> doILL(M_IMPLIED)
            116 -> doSTZ(M_ZERO_PAGE_X)
            117 -> doADC(M_ZERO_PAGE_X)
            118 -> doROR(M_ZERO_PAGE_X)
            119 -> doILL(M_IMPLIED)
            120 -> doSEI(M_IMPLIED)
            121 -> doADC(M_ABSOLUTE_Y)
            122 -> doPLY(M_IMPLIED)
            123 -> doILL(M_IMPLIED)
            124 -> doJMP(MA_INDIRECT_X)
            125 -> doADC(M_ABSOLUTE_X)
            126 -> doROR(M_ABSOLUTE_X)
            127 -> doILL(M_IMPLIED)
            128 -> doBRA(M_RELATIVE)
            129 -> doSTA(M_INDIRECT_X)
            130 -> doILL(M_IMPLIED)
            131 -> doILL(M_IMPLIED)
            132 -> doSTY(M_ZERO_PAGE)
            133 -> doSTA(M_ZERO_PAGE)
            134 -> doSTX(M_ZERO_PAGE)
            135 -> doILL(M_IMPLIED)
            136 -> doDEY(M_IMPLIED)
            137 -> doBIT(M_IMMEDIATE)
            138 -> doTXA(M_IMPLIED)
            139 -> doILL(M_IMPLIED)
            140 -> doSTY(M_ABSOLUTE)
            141 -> doSTA(M_ABSOLUTE)
            142 -> doSTX(M_ABSOLUTE)
            143 -> doILL(M_IMPLIED)
            144 -> doBCC(M_RELATIVE)
            145 -> doSTA(M_INDIRECT_Y)
            146 -> doSTA(M_INDIRECT_ZP)
            147 -> doILL(M_IMPLIED)
            148 -> doSTY(M_ZERO_PAGE_X)
            149 -> doSTA(M_ZERO_PAGE_X)
            150 -> doSTX(M_ZERO_PAGE_Y)
            151 -> doILL(M_IMPLIED)
            152 -> doTYA(M_IMPLIED)
            153 -> doSTA(M_ABSOLUTE_Y)
            154 -> doTXS(M_IMPLIED)
            155 -> doILL(M_IMPLIED)
            156 -> doSTZ(M_ABSOLUTE)
            157 -> doSTA(M_ABSOLUTE_X)
            158 -> doSTZ(M_ABSOLUTE_X)
            159 -> doILL(M_IMPLIED)
            160 -> doLDY(M_IMMEDIATE)
            161 -> doLDA(M_INDIRECT_X)
            162 -> doLDX(M_IMMEDIATE)
            163 -> doILL(M_IMPLIED)
            164 -> doLDY(M_ZERO_PAGE)
            165 -> doLDA(M_ZERO_PAGE)
            166 -> doLDX(M_ZERO_PAGE)
            167 -> doILL(M_IMPLIED)
            168 -> doTAY(M_IMPLIED)
            169 -> doLDA(M_IMMEDIATE)
            170 -> doTAX(M_IMPLIED)
            171 -> doILL(M_IMPLIED)
            172 -> doLDY(M_ABSOLUTE)
            173 -> doLDA(M_ABSOLUTE)
            174 -> doLDX(M_ABSOLUTE)
            175 -> doILL(M_IMPLIED)
            176 -> doBCS(M_RELATIVE)
            177 -> doLDA(M_INDIRECT_Y)
            178 -> doLDA(M_INDIRECT_ZP)
            179 -> doILL(M_IMPLIED)
            180 -> doLDY(M_ZERO_PAGE_X)
            181 -> doLDA(M_ZERO_PAGE_X)
            182 -> doLDX(M_ZERO_PAGE_Y)
            183 -> doILL(M_IMPLIED)
            184 -> doCLV(M_IMPLIED)
            185 -> doLDA(M_ABSOLUTE_Y)
            186 -> doTSX(M_IMPLIED)
            187 -> doILL(M_IMPLIED)
            188 -> doLDY(M_ABSOLUTE_X)
            189 -> doLDA(M_ABSOLUTE_X)
            190 -> doLDX(M_ABSOLUTE_Y)
            191 -> doILL(M_IMPLIED)
            192 -> doCPY(M_IMMEDIATE)
            193 -> doCMP(M_INDIRECT_X)
            194 -> doILL(M_IMPLIED)
            195 -> doILL(M_IMPLIED)
            196 -> doCPY(M_ZERO_PAGE)
            197 -> doCMP(M_ZERO_PAGE)
            198 -> doDEC(M_ZERO_PAGE)
            199 -> doILL(M_IMPLIED)
            200 -> doINY(M_IMPLIED)
            201 -> doCMP(M_IMMEDIATE)
            202 -> doDEX(M_IMPLIED)
            203 -> doILL(M_IMPLIED)
            204 -> doCPY(M_ABSOLUTE)
            205 -> doCMP(M_ABSOLUTE)
            206 -> doDEC(M_ABSOLUTE)
            207 -> doILL(M_IMPLIED)
            208 -> doBNE(M_RELATIVE)
            209 -> doCMP(M_INDIRECT_Y)
            210 -> doCMP(M_INDIRECT_ZP)
            211 -> doILL(M_IMPLIED)
            212 -> doILL(M_IMPLIED)
            213 -> doCMP(M_ZERO_PAGE_X)
            214 -> doDEC(M_ZERO_PAGE_X)
            215 -> doILL(M_IMPLIED)
            216 -> doCLD(M_IMPLIED)
            217 -> doCMP(M_ABSOLUTE_Y)
            218 -> doPHX(M_IMPLIED)
            219 -> doILL(M_IMPLIED)
            220 -> doILL(M_IMPLIED)
            221 -> doCMP(M_ABSOLUTE_X)
            222 -> doDEC(M_ABSOLUTE_X)
            223 -> doILL(M_IMPLIED)
            224 -> doCPX(M_IMMEDIATE)
            225 -> doSBC(M_INDIRECT_X)
            226 -> doILL(M_IMPLIED)
            227 -> doILL(M_IMPLIED)
            228 -> doCPX(M_ZERO_PAGE)
            229 -> doSBC(M_ZERO_PAGE)
            230 -> doINC(M_ZERO_PAGE)
            231 -> doILL(M_IMPLIED)
            232 -> doINX(M_IMPLIED)
            233 -> doSBC(M_IMMEDIATE)
            234 -> doNOP(M_IMPLIED)
            235 -> doILL(M_IMPLIED)
            236 -> doCPX(M_ABSOLUTE)
            237 -> doSBC(M_ABSOLUTE)
            238 -> doINC(M_ABSOLUTE)
            239 -> doILL(M_IMPLIED)
            240 -> doBEQ(M_RELATIVE)
            241 -> doSBC(M_INDIRECT_Y)
            242 -> doSBC(M_INDIRECT_ZP)
            243 -> doILL(M_IMPLIED)
            244 -> doILL(M_IMPLIED)
            245 -> doSBC(M_ZERO_PAGE_X)
            246 -> doINC(M_ZERO_PAGE_X)
            247 -> doILL(M_IMPLIED)
            248 -> doSED(M_IMPLIED)
            249 -> doSBC(M_ABSOLUTE_Y)
            250 -> doPLX(M_IMPLIED)
            251 -> doILL(M_IMPLIED)
            252 -> doILL(M_IMPLIED)
            253 -> doSBC(M_ABSOLUTE_X)
            254 -> doINC(M_ABSOLUTE_X)
            255 -> doILL(M_IMPLIED)
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
        b.append(if ((flags and N_FLAG) == 0) " n" else " N")
        b.append(if ((flags and V_FLAG) == 0) 'v' else 'V')
        b.append(if ((flags and R_FLAG) == 0) 'r' else 'R')
        b.append(if ((flags and B_FLAG) == 0) 'b' else 'B')
        b.append(if ((flags and D_FLAG) == 0) 'd' else 'D')
        b.append(if ((flags and I_FLAG) == 0) 'i' else 'I')
        b.append(if ((flags and Z_FLAG) == 0) 'z' else 'Z')
        b.append(if ((flags and C_FLAG) == 0) 'c' else 'C')
        b.append(' ')
    }

    fun disassemble(where: Int): String {
        val b = StringBuilder()
        val opcode = mem.read(where)
        b.append(mneumonics[opcode])

        when (modeTable[opcode]) {
            M_INDIRECT_X -> {
                b.append(" (")
                dumpHexByte(b, mem.read(where + 1))
                b.append(", X)")
            }

            M_ZERO_PAGE -> {
                b.append(' ')
                dumpHexByte(b, mem.read(where + 1))
            }

            M_IMMEDIATE -> {
                b.append(" #")
                dumpHexByte(b, mem.read(where + 1))
            }

            M_ABSOLUTE -> {
                b.append(' ')
                dumpHexWord(b, wordAt(where + 1))
            }

            M_INDIRECT_Y -> {
                b.append(" (")
                dumpHexByte(b, mem.read(where + 1))
                b.append("), Y  [")
                dumpHexWord(b, wordAt(mem.read(where + 1)))
                b.append(']')
            }

            M_ZERO_PAGE_X -> {
                b.append(' ')
                dumpHexByte(b, mem.read(where + 1))
                b.append(", X")
            }

            M_ZERO_PAGE_Y -> {
                b.append(' ')
                dumpHexByte(b, mem.read(where + 1))
                b.append(", Y")
            }

            M_ABSOLUTE_X -> {
                b.append(' ')
                dumpHexWord(b, wordAt(where + 1))
                b.append(", X")
            }

            M_ABSOLUTE_Y -> {
                b.append(' ')
                dumpHexWord(b, wordAt(where + 1))
                b.append(", Y")
            }

            M_RELATIVE -> {
                b.append(' ')
                dumpHexWord(b, where + 2 + complement(mem.read(where + 1)))
            }

            M_INDIRECT -> {
                b.append(" (")
                dumpHexWord(b, wordAt(where + 1))
                b.append(')')
            }

            M_INDIRECT_ZP -> {
                b.append(" (")
                dumpHexByte(b, mem.read(where + 1))
                b.append(')')
            }

            M_ACCUM -> {
                b.append(" A")
            }

            M_IMPLIED -> {}

            else -> {
                b.append(" UNKNOWN MODE")
            }
        }
        return b.toString()
    }
}
