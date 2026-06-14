package cat.subi.itaca.finance.application

/** Port: extract the plain text of a PDF (finpension reports are text PDFs, not scans). */
fun interface PdfTextExtractor {
    fun extract(bytes: ByteArray): String
}
