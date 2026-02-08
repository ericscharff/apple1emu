# Apple 1 Emulator in Kotlin Native

## Overview

This is a highly experimental, and completely unsupported, branch of the Java
Apple 1 emualtor. I converted the Java code to Kotlin, and added Kotlin native
dependencies.

The end result is the ability to compile and run a native Linux executable
of the Apple 1 emulator.  Build with

```
$ ./gradlew build
```

and Kotlin Native will be installed, and it will produce a native linux
executable. Run this from the main project directory with

```
$ ./build/bin/linuxX64/releaseExecutable/apple1.kexe
```

(Running from the root directory of the project is important, because that
is how it finds the necessary binaries in `assets/` which is a relative path.)
