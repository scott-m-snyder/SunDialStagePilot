package com.sundial.stagepilot

import org.junit.Test
import java.io.File
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

class PdfTest {
    @Test
    fun testPdfParsing() {
        val file = File("../I Drink Wine - Adele - Key of C.pdf")
        if (!file.exists()) {
            println("File not found")
            return
        }
        val document = PDDocument.load(file)
        val textStripper = PDFTextStripper()
        textStripper.sortByPosition = true
        val parsedText = textStripper.getText(document)
        document.close()
        
        println(parsedText)
    }
}