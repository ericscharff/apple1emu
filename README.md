This is an Apple2e emulator written entirely in Java. It is a subset of a
generic 6502 emulator suite which supported front ends for Apple1, Apple 2,
Apple ][ plus, and Apple //e. This particular repository only includes the Apple
//e emulator, which was ported from Swing to SWT. Included are all the resources
needed to run the emulator, as well as sample disks.

To build, and run, use gradle:

```bash
./gradlew run
```

You may need to update the SWT dependency for the architecture you are using.
