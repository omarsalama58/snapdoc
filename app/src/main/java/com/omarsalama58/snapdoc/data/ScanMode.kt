package com.omarsalama58.snapdoc.data

/** The ways the camera can be used. */
enum class ScanMode(val label: String) {
    DOC("Document"),
    ID("ID Card"),
    BOOK("Book"),
    BOARD("Whiteboard"),
    TEXT("Extract Text"),
    STAMP("Timestamp"),
    CODE("Scan Code")
}

/** What to do with a freshly cropped picture. */
enum class CropAction { ADD, BOOK, BOARD, OCR }
