package a1em

expect object TestResourceLoader {
    fun loadBinaryResource(name: String, size: Int): IntArray
}
