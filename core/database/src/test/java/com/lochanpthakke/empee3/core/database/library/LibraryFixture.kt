package com.lochanpthakke.empee3.core.database.library

/** The real-library fixture: 104 MediaStore rows from public albums (see library_fixture.tsv header). */
object LibraryFixture {
    val tracks: List<RawTrack> by lazy {
        val lines =
            javaClass.classLoader!!
                .getResourceAsStream("library_fixture.tsv")!!
                .bufferedReader()
                .readLines()
        lines.drop(1).map { line ->
            val c = line.split('\t')
            val track = c[5].toIntOrNull() ?: 0
            RawTrack(c[0].toLong(), c[1], c[2], c[3].ifBlank { null }, c[4], track % 1000, track / 1000, c[6], c[7] == "1")
        }
    }

    fun songId(title: String): Long = tracks.first { it.title == title }.songId
}
