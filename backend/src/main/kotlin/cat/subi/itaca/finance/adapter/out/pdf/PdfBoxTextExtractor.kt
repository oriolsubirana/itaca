package cat.subi.itaca.finance.adapter.out.pdf

import cat.subi.itaca.finance.application.PdfTextExtractor
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component

/** Apache PDFBox implementation of the PDF text port. */
@Component
class PdfBoxTextExtractor : PdfTextExtractor {
    override fun extract(bytes: ByteArray): String {
        Loader.loadPDF(bytes).use { document ->
            return PDFTextStripper().getText(document)
        }
    }
}
