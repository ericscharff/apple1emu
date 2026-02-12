package a1em

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.get
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import platform.posix.ECHO
import platform.posix.F_GETFL
import platform.posix.F_SETFL
import platform.posix.ICANON
import platform.posix.O_NONBLOCK
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.STDIN_FILENO
import platform.posix.TCSANOW
import platform.posix.fclose
import platform.posix.fcntl
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.memcpy
import platform.posix.read
import platform.posix.stdout
import platform.posix.tcgetattr
import platform.posix.tcsetattr
import platform.posix.termios

@OptIn(ExperimentalForeignApi::class)
class NativeIO : Apple1IO {
    override fun onOutput(char: Char) {
        print(char)
        fflush(stdout)
    }

    override fun warn(message: String) {}

    override fun error(message: String) {
        println("\nERROR: $message")
    }
}

@OptIn(ExperimentalForeignApi::class)
fun readBinaryFile(path: String): IntArray? {
    val file = fopen(path, "rb") ?: return null
    try {
        fseek(file, 0, SEEK_END)
        val size = ftell(file).toInt()
        fseek(file, 0, SEEK_SET)

        val buffer = nativeHeap.allocArray<ByteVar>(size)
        try {
            fread(buffer, 1.toULong(), size.toULong(), file)
            val result = IntArray(size)
            for (i in 0 until size) {
                result[i] = buffer[i].toInt() and 0xff
            }
            return result
        } finally {
            nativeHeap.free(buffer)
        }
    } finally {
        fclose(file)
    }
}

@OptIn(ExperimentalForeignApi::class)
fun main() {
    val io = NativeIO()
    val core = Apple1Core(io)

    // Updated path to assets
    val rom = readBinaryFile("assets/apple1.rom")
    if (rom == null) {
        println("Could not find apple1.rom at assets/apple1.rom")
        return
    }

    core.loadBios(rom)

    println("Apple 1 Emulator (Native)")
    println("Press Ctrl+C to exit")

    val originalTermios = nativeHeap.alloc<termios>()
    tcgetattr(STDIN_FILENO, originalTermios.ptr)

    val rawTermios = nativeHeap.alloc<termios>()
    memcpy(rawTermios.ptr, originalTermios.ptr, sizeOf<termios>().convert())

    rawTermios.c_lflag = rawTermios.c_lflag and (ICANON or ECHO).inv().convert()
    tcsetattr(STDIN_FILENO, TCSANOW, rawTermios.ptr)

    val flags = fcntl(STDIN_FILENO, F_GETFL, 0)
    fcntl(STDIN_FILENO, F_SETFL, flags or O_NONBLOCK)

    try {
        val inputBuffer = nativeHeap.alloc<ByteVar>()

        while (true) {
            core.runBatch(1000)

            if (read(STDIN_FILENO, inputBuffer.ptr, 1.convert()) > 0) {
                var charCode = inputBuffer.value.toInt() and 0xff
                if (charCode == 10) charCode = 13
                val char = charCode.toChar().uppercaseChar()
                core.setCoreLastKey(char.code)
            }
        }
    } finally {
        tcsetattr(STDIN_FILENO, TCSANOW, originalTermios.ptr)
        nativeHeap.free(originalTermios)
        nativeHeap.free(rawTermios)
    }
}
