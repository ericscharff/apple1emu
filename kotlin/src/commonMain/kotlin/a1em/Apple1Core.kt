package a1em

interface Apple1IO {
    fun onOutput(char: Char)

    fun warn(message: String)

    fun error(message: String)
}

class Apple1Core(
    val io: Apple1IO,
) : M6502.Memory {
    private val mem = IntArray(65536)
    val cpu = M6502(this, 0xff00)

    var lastKey: Int = 0
    private var lastOut: Int = 0
    private var keyBuf: CharArray? = null
    private var keyBufIndex: Int = 0

    fun loadBios(bios: IntArray) {
        var a = 0xff00
        for (i in 0 until 256) {
            mem[a] = bios[i]
            a++
        }
    }

    fun fillKeyBuf(content: String) {
        keyBuf =
            CharArray(content.length) { i ->
                val it = content[i]
                if (it == '\n') '\r' else it
            }
        keyBufIndex = 0
    }

    fun loadBinary(
        data: ByteArray,
        where: Int,
    ) {
        var addr = where
        for (b in data) {
            mem[addr++] = b.toInt() and 0xff
        }
    }

    fun loadBinary(
        data: IntArray,
        where: Int,
    ) {
        var addr = where
        for (b in data) {
            mem[addr++] = b
        }
    }

    fun step() {
        cpu.step()
    }

    fun runBatch(cycles: Int) {
        var clock = 0
        while (!cpu.halt && clock < cycles) {
            cpu.step()
            clock++
        }
    }

    // Read
    private fun doIORead(where: Int): Int =
        when (where) {
            0xd010 -> {
                // Keyboard input
                var k = lastKey
                val buf = keyBuf
                if (buf != null) {
                    if (keyBufIndex == buf.size) {
                        keyBuf = null
                        k = 0
                    } else {
                        k = buf[keyBufIndex].code
                        keyBufIndex++
                        if (keyBufIndex == buf.size) keyBuf = null
                    }
                    lastKey = k
                }
                if (lastKey == 0) {
                    io.warn("Reading keyboard but no key available")
                }
                lastKey = 0
                k or 0x80
            }

            0xd011 -> {
                if (keyBuf == null && lastKey == 0) {
                    1
                } else {
                    0x80
                }
            }

            0xd0f2, 0xd012 -> {
                // Display output
                // bit 8 should always be low
                lastOut and 0x7f
            }

            0xd013 -> {
                // Display status
                io.warn("Read from display status")
                0
            }

            else -> {
                io.error("Read from Unknown I/O Address: $where")
                0
            }
        }

    // Write
    private fun doIOWrite(
        where: Int,
        what: Int,
    ) {
        val addr = (where and 0xf00f) or 0x0010
        when (addr) {
            0xd010 -> {
                io.warn("Write to keyboard I/O: $what")
            }

            0xd011 -> {
                io.warn("Write to keyboard status I/O: $what")
            }

            0xd0f2, 0xd012 -> {
                // Display output
                var ch = (what and 0x7f).toChar()
                if (ch == '\r') ch = '\n'
                io.onOutput(ch)
                lastOut = what
            }

            0xd013 -> {
                io.warn("Write to display status I/O: $what")
            }

            else -> {
                io.error("Write to Unknown I/O Address: $where")
            }
        }
    }

    override fun read(where: Int): Int {
        if (where == 0 || where == 1) {
            io.warn("Read from $where")
        }
        if (where in 0xd000..0xdfff) return doIORead(where)
        if (where < 0 || where >= 65536) {
            io.error("Read out of range: $where")
            return 0
        }
        return mem[where]
    }

    override fun write(
        where: Int,
        what: Int,
    ) {
        if (where == 0 || where == 1) {
            io.warn("Write to $where")
        }
        if (where in 0xd000..0xdfff) {
            doIOWrite(where, what)
            return
        }
        if (where < 0 || where >= 65536) {
            io.error("Write out of range: $where")
            return
        }
        if (what < 0 || what > 255) {
            io.error("Write value out of range: $what")
            return
        }
        mem[where] = what
    }

    fun getCoreCpu() = cpu

    fun setCoreLastKey(v: Int) {
        lastKey = v
    }
}
