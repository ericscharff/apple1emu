This repository formerly hosted an Apple2e emulator written in Java, with its
GUI utilizing the SWT library. The Apple //e emulator still exists in the
history of this repository, under the `last-a2-emu` tag. The current version has
no external dependencies other that what is in the J2SE distribution.

What remains is a generic 6502 emulator, and a rudimentary Apple 1 emulator. The
Apple 1 emulator relies on Swing (because handling raw console I/O is
problematic in pure Java) but is intended to be a simple 6502 test bed.

To build, and run, use gradle:

```bash
./gradlew run
```
