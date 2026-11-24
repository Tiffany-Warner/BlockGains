package com.tiffles.blockgains.data.importer

/** A fixture's file name and contents. */
data class NamedText(val fileName: String, val text: String)

/** Reads a made-up fixture from `src/test/resources/fixtures/`. */
fun fixture(name: String): NamedText {
    val stream = checkNotNull(NamedText::class.java.getResourceAsStream("/fixtures/$name")) { "Missing fixture $name" }
    return NamedText(name, stream.use { it.readBytes().toString(Charsets.UTF_8) })
}
