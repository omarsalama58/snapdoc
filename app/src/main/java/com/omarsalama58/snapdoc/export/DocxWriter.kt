package com.omarsalama58.snapdoc.export

import com.omarsalama58.snapdoc.imaging.Imaging
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal .docx writer: every page becomes the scanned picture followed by its recognised text. */
object DocxWriter {

    class PageContent(val image: File, val text: String)

    private fun esc(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch.code < 32 && ch != '\t' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun hasArabic(s: String) = s.any { it.code in 0x0600..0x06FF }

    fun write(out: File, pages: List<PageContent>) {
        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            fun put(name: String, bytes: ByteArray) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }

            put(
                "[Content_Types].xml",
                ("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                    """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
                    """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
                    """<Default Extension="xml" ContentType="application/xml"/>""" +
                    """<Default Extension="jpg" ContentType="image/jpeg"/>""" +
                    """<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""" +
                    """</Types>""").toByteArray()
            )
            put(
                "_rels/.rels",
                ("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                    """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>""" +
                    """</Relationships>""").toByteArray()
            )

            val rels = StringBuilder()
            val body = StringBuilder()
            pages.forEachIndexed { i, p ->
                val n = i + 1
                val bmp = Imaging.decodeFile(p.image.absolutePath, 1600)
                if (bmp != null) {
                    // picture file
                    val bos = java.io.ByteArrayOutputStream()
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, bos)
                    put("word/media/image$n.jpg", bos.toByteArray())
                    rels.append("""<Relationship Id="rIdImg$n" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image$n.jpg"/>""")

                    val maxW = 5486400L            // 6 inch in EMU
                    val maxH = 7000000L
                    var cx = maxW
                    var cy = maxW * bmp.height / bmp.width
                    if (cy > maxH) {
                        cy = maxH
                        cx = maxH * bmp.width / bmp.height
                    }
                    body.append(
                        """<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:drawing>""" +
                            """<wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$cx" cy="$cy"/>""" +
                            """<wp:docPr id="$n" name="Picture $n"/>""" +
                            """<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr>""" +
                            """<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">""" +
                            """<pic:pic><pic:nvPicPr><pic:cNvPr id="$n" name="image$n.jpg"/><pic:cNvPicPr/></pic:nvPicPr>""" +
                            """<pic:blipFill><a:blip r:embed="rIdImg$n"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>""" +
                            """<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>""" +
                            """<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>""" +
                            """</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"""
                    )
                }
                for (line in p.text.split("\n")) {
                    if (line.isBlank()) {
                        body.append("<w:p/>")
                        continue
                    }
                    val rtl = hasArabic(line)
                    body.append("<w:p><w:pPr>")
                    if (rtl) body.append("<w:bidi/>")
                    body.append("</w:pPr><w:r><w:rPr>")
                    body.append("""<w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:cs="Arial"/>""")
                    if (rtl) body.append("<w:rtl/>")
                    body.append("""<w:sz w:val="26"/><w:szCs w:val="26"/></w:rPr>""")
                    body.append("""<w:t xml:space="preserve">${esc(line)}</w:t></w:r></w:p>""")
                }
                if (i < pages.lastIndex) body.append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
            }

            put(
                "word/_rels/document.xml.rels",
                ("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                    """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels</Relationships>""")
                    .toByteArray()
            )
            put(
                "word/document.xml",
                ("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                    """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" """ +
                    """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" """ +
                    """xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" """ +
                    """xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" """ +
                    """xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">""" +
                    """<w:body>$body""" +
                    """<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>""" +
                    """<w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="708" w:footer="708" w:gutter="0"/>""" +
                    """</w:sectPr></w:body></w:document>""").toByteArray()
            )
        }
    }
}
