package a1em

import kotlin.test.Test
import kotlin.test.assertEquals

class M6502Test {
    @Test
    fun runFunctionalTest() {
        val mem = TestResourceLoader.loadBinaryResource("rom.functional", 65536)
        val memInterface =
            object : M6502.Memory {
                override fun read(where: Int): Int = mem[where]

                override fun write(
                    where: Int,
                    what: Int,
                ) {
                    mem[where] = what
                }
            }
        val cpu = M6502(memInterface, 0x400)
        var oldPc = 0
        while (oldPc != cpu.pc) {
            oldPc = cpu.pc
            cpu.step()
        }
        // When the regression test completes, without errors, it loops at 0x3469 (13417)
        assertEquals(13417, oldPc)
    }
}
