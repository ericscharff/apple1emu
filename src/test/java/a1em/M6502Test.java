package a1em;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class M6502Test {
  @Test
  public void runFunctionalTest() {
    ResourceHelper resources = new ResourceHelper();
    final int[] mem = resources.loadBinaryResource("rom.functional", 65536);
    final M6502.Memory memInterface = new M6502.Memory() {
      @Override
      public int read(int where) {
        return mem[where];
      }
      @Override
      public void write(int where, int what) {
        mem[where] = what;
      }
    };
    M6502 cpu = new M6502(memInterface, 0x400);
    int oldPc = 0;
    while (oldPc != cpu.pc) {
      oldPc = cpu.pc;
      cpu.step();
    }
    // When the regression test completes, without errors, it loops.
    // From
    // https://github.com/Klaus2m5/6502_65C02_functional_tests/blob/master/bin_files/6502_functional_test.lst
    //                      ; S U C C E S S ************************************************
    //                      ; -------------
    // success              ;if you get here everything went well
    // 3469 : 4c6934        jmp *           ;test passed, no errors
    //
    // If it loops at any other address, there has been some kind of failure.
    assertEquals(oldPc, 13417);
  }
}
