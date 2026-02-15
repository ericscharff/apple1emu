package a1em

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual object TestResourceLoader {
    actual fun loadBinaryResource(
        name: String,
        size: Int,
    ): IntArray {
        // Map logical names to local asset paths
        val path =
            when (name) {
                "rom.functional" -> "assets/6502_functional_test.bin"
                else -> "assets/$name"
            }
        return readBinaryFile(path) ?: IntArray(size)
    }
}
