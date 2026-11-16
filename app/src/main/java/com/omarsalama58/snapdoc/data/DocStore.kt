package com.omarsalama58.snapdoc.data

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class PageEntry(val file: String, val orig: String)

data class Doc(
    val id: String,
    var name: String,
    val created: Long,
    var updated: Long,
    val pages: MutableList<PageEntry>
)

/** Stores every document as a folder: meta.json + one edited and one original JPEG per page. */
class DocStore(private val ctx: Context) {
    private val root = File(ctx.filesDir, "docs").also { it.mkdirs() }

    fun dir(id: String) = File(root, id)
    fun pageFile(doc: Doc, i: Int) = File(dir(doc.id), doc.pages[i].file)
    fun origFile(doc: Doc, i: Int) = File(dir(doc.id), doc.pages[i].orig)

    fun list(): List<Doc> =
        (root.listFiles() ?: emptyArray()).mapNotNull { load(it.name) }.sortedByDescending { it.updated }

    fun load(id: String): Doc? {
        val f = File(dir(id), "meta.json")
        if (!f.exists()) return null
        return try {
            val j = JSONObject(f.readText())
            val arr = j.getJSONArray("pages")
            val pages = mutableListOf<PageEntry>()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                pages.add(PageEntry(p.getString("f"), p.getString("o")))
            }
            Doc(j.getString("id"), j.getString("name"), j.getLong("created"), j.getLong("updated"), pages)
        } catch (e: Exception) {
            null
        }
    }

    fun save(doc: Doc) {
        val arr = JSONArray()
        doc.pages.forEach { arr.put(JSONObject().put("f", it.file).put("o", it.orig)) }
        val j = JSONObject()
            .put("id", doc.id)
            .put("name", doc.name)
            .put("created", doc.created)
            .put("updated", doc.updated)
            .put("pages", arr)
        dir(doc.id).mkdirs()
        File(dir(doc.id), "meta.json").writeText(j.toString())
    }

    fun create(name: String): Doc {
        val id = UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        val doc = Doc(id, name, now, now, mutableListOf())
        save(doc)
        return doc
    }

    fun delete(id: String) {
        dir(id).deleteRecursively()
    }

    fun rename(doc: Doc, name: String) {
        doc.name = name
        doc.updated = System.currentTimeMillis()
        save(doc)
    }

    private fun writeJpeg(bmp: Bitmap, f: File) {
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }

    private fun newName(prefix: String) = "${prefix}_${UUID.randomUUID().toString().take(8)}.jpg"

    fun addPage(doc: Doc, bmp: Bitmap) {
        val pf = newName("p")
        val of = newName("o")
        val d = dir(doc.id)
        d.mkdirs()
        writeJpeg(bmp, File(d, pf))
        File(d, pf).copyTo(File(d, of), overwrite = true)
        doc.pages.add(PageEntry(pf, of))
        doc.updated = System.currentTimeMillis()
        save(doc)
    }

    fun replacePage(doc: Doc, index: Int, bmp: Bitmap, alsoOrig: Boolean) {
        val d = dir(doc.id)
        val old = doc.pages[index]
        val pf = newName("p")
        writeJpeg(bmp, File(d, pf))
        File(d, old.file).delete()
        var of = old.orig
        if (alsoOrig) {
            of = newName("o")
            File(d, pf).copyTo(File(d, of), overwrite = true)
            File(d, old.orig).delete()
        }
        doc.pages[index] = PageEntry(pf, of)
        doc.updated = System.currentTimeMillis()
        save(doc)
    }

    fun deletePage(doc: Doc, index: Int) {
        val d = dir(doc.id)
        val p = doc.pages.removeAt(index)
        File(d, p.file).delete()
        File(d, p.orig).delete()
        doc.updated = System.currentTimeMillis()
        save(doc)
    }

    fun movePage(doc: Doc, from: Int, to: Int) {
        if (from !in doc.pages.indices || to !in doc.pages.indices) return
        val p = doc.pages.removeAt(from)
        doc.pages.add(to, p)
        doc.updated = System.currentTimeMillis()
        save(doc)
    }

    fun appendPagesFrom(target: Doc, source: Doc) {
        val sd = dir(source.id)
        val td = dir(target.id)
        td.mkdirs()
        for (p in source.pages) {
            val pf = newName("p")
            val of = newName("o")
            File(sd, p.file).copyTo(File(td, pf), overwrite = true)
            File(sd, p.orig).copyTo(File(td, of), overwrite = true)
            target.pages.add(PageEntry(pf, of))
        }
        target.updated = System.currentTimeMillis()
        save(target)
    }
}
