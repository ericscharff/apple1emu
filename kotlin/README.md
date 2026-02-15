# Apple 1 Emulator in Kotlin Native

## Overview

This is a highly experimental, and completely unsupported, branch of the Java
Apple 1 emulator. I converted the Java code to Kotlin, and added Kotlin native
dependencies.

The end result is the ability to compile and run a native Linux executable of
the Apple 1 emulator. Build with

```
$ ./gradlew build
```

and Kotlin Native will be installed, and it will produce a native linux
executable. Run this from the main project directory with

```
$ ./build/bin/linuxX64/releaseExecutable/apple1.kexe
```

(Running from the root directory of the project is important, because that is
how it finds the necessary binaries in `assets/` which is a relative path.)

## Thoughts on Kotlin

Kotlin is, as you might expect, pretty familiar to a Java developer. It feels
like syntactic sure on top of the Java programming language. I wouldn't say that
for a project like this one, it is strongly better or worse than Java.

Kotlin native, though, is intriguing. It is very nice to be able to deploy a
binary without a user having a compatible JRE (which is fraught). However, the
environment is pretty restrictive. The standard library is quite impoverished.
Why do I need to use POSIX interop (and write a substantial amount of code) to
read files? The theoretical advantage of Kotlin as a language is its Java
interoperability, which goes away when you can't use Java libraries. This is an
understandable limitation of Kotlin native, but it's a severe limitation. The
lack of replacements for even basic features (like file I/O) makes this even
more frustrating. I'm not convinced, for a project of this nature, if it is
better than just writing in C. This story might be different for a native
Android or iOS app, but I don't have much familiarity with that.
