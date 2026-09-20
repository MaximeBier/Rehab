package rehab.rules

data class VersionRange(val minInclusive: String, val maxExclusive: String) {
    fun contains(version: String): Boolean =
        compare(version, minInclusive) >= 0 && compare(version, maxExclusive) < 0

    companion object {
        fun compare(a: String, b: String): Int {
            val pa = parts(a)
            val pb = parts(b)
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val x = pa.getOrElse(i) { 0 }
                val y = pb.getOrElse(i) { 0 }
                if (x != y) return x.compareTo(y)
            }
            return 0
        }

        private fun parts(v: String): List<Int> =
            v.split('.', '-').map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    }
}
