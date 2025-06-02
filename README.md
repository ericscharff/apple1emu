# Apple 1 Emulator in Java

## Overview

This repository formerly hosted an Apple2e emulator written in Java, with its
GUI utilizing the SWT library. The Apple //e emulator still exists in the
history of this repository, under the `last-a2-emu` tag. The current version has
no external dependencies other that what is in the J2SE distribution, although
it uses Swing to simplify keyboard input.

What remains is a generic 6502 emulator, and a rudimentary Apple 1 emulator. The
Apple 1 emulator relies on Swing (because handling raw console I/O is
problematic in pure Java) but is intended to be a simple 6502 test bed.

## Building

To build, and run, use gradle:

```bash
./gradlew run
```

## Using the Apple 1

The Apple 1 is a 6502 based computer. When the program starts, it creates 64K of
RAM, and then loads the machine's monitor (the BIOS program that runs when the
machine starts) from file file `rom.bin`. This contains 256 bytes(!) of data
loaded into $FF00 through $FFFF. The monitor is an interactive program that
allows you to display memory, enter bytes into memory, and jump to a program.

Unlike a real Apple 1, there are 64K of true RAM. The ROM code technically
should be read-only, but it is editable just like any other RAM. The original
Apple 1 also has limits to available RAM, but essentially any memory address
other than an I/O port can be used as RAM.

When the emulator starts, a Java Swing UI window opens up. The output from the
Apple 1 appears in the terminal that stared, not in the window that opens. To
use the emulator, _by sure to focus the window._ The reason for this is that
Java does not accept raw terminal input, so instead, the Swing window is what
handles input (although all output appears in the terminal.)

If all goes well, you will be greeted with a backslash ("\"), a prompt that the
Apple 1 is ready to accept input.

### Accessing Memory

Memory can be examined by typing an address, or a range of addresses. Typing a
single address shows its contents, and typing two addressses separated by a dot
(".") shows a range of memory, e.g.:

<pre>
\

<i># Print a single location</i>
<b>FFF0</b>

FFF0: 12
<b>FFE0</b>

FFE0: 4A
<b>FFE1</b>

FFE1: 20

<i># Print a range from start.end, inclusive</i>
<b>FFE0.FFF0</b>

FFE0: 4A 20 E5 FF 68 29 0F 09
FFE8: B0 C9 BA 90 02 69 06 2C
FFF0: 12
</pre>

Memory can be set in the same form that the monitor display it. For example, you
can set a single location in memory with a colon:

```
# Typing 0000: 01 puts $01 in memory location 0
0000: 01

# More than one number can appear on a line, so this puts $10 in $0010, $11 in
# $0011. and $12 in $0012
0010: 10 11 12
```

You can jump to a program by typing an address, followed by R, e.g., `300R`.

### Loading and Saving Programs

The terminal is the best way to get software into and out of the emulator. For
example, the file [fig6502.mon](software/fig6502.mon) is a machine monitor dump
of a program, the Fig-Forth programming language. This is a text file with a
binary dump of the program, followed by a command to run the program. This can
be simply copied and pasted into the machine language monitor, which will load
(and run) the program.

Similarly, programs aren't saved, but you can paste the contents of the terminal
into a text file. Since the monitor can emit the contents of RAM, you can
display a block of RAM, save it to a text file, and then load it later.

### Quitting the Emulator

To exit the emulator, close the Swing window.


## Alternatives

Before the Java implementation was written, I'd written an Apple I and 6502
emulator in Oberon. I ported this to Oberon-07 and have also written an Oberon
compiler, so my [oberon-compiler](https://github.com/ericscharff/oberon-compiler)
repository has an Apple I emulator that uses the console without any Java GUI
hacks, and compiles to a native platform binary.
Sun Jun  1 08:00:45 PM MDT 2025
