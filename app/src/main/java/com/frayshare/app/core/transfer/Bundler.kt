package com.frayshare.app.core.transfer

data class Segment(val first: Int, val count: Int, val solo: Boolean)

object Bundler {
    const val SMALL_FILE = 128L * 1024
    const val MAX_FILES = 256
    const val MAX_BYTES = 8L * 1024 * 1024

    fun group(
        sizes: List<Long>,
        smallLimit: Long = SMALL_FILE,
        maxFiles: Int = MAX_FILES,
        maxBytes: Long = MAX_BYTES
    ): List<Segment> {
        val out = mutableListOf<Segment>()
        var index = 0
        while (index < sizes.size) {
            if (sizes[index] > smallLimit) {
                out += Segment(index, 1, solo = true)
                index += 1
            } else {
                var count = 1
                var bytes = sizes[index]
                while (index + count < sizes.size &&
                    sizes[index + count] <= smallLimit &&
                    count < maxFiles &&
                    bytes + sizes[index + count] <= maxBytes
                ) {
                    bytes += sizes[index + count]
                    count += 1
                }
                out += Segment(index, count, solo = false)
                index += count
            }
        }
        return out
    }
}
