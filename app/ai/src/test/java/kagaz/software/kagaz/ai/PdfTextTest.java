package kagaz.software.kagaz.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfTextTest {

    private static byte[] pdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            if (text != null) {
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(72, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void extractsText() throws Exception {
        PdfText.Extract e = new PdfText("tesseract", "eng").extract(pdf("Invoice total 42"));
        assertEquals("Invoice total 42", e.text());
        assertFalse(e.usedOcr());
    }

    @Test
    void chunksSplitLongTextWithoutLosingAnything() {
        String text = ("paragraph one. ".repeat(100) + "\n\n").repeat(50);
        java.util.List<String> parts = PdfText.chunks(text, 5000);
        assertTrue(parts.size() > 1);
        assertTrue(parts.stream().allMatch(p -> p.length() <= 5000));
        assertEquals(text.replaceAll("\\s+", ""), String.join("", parts).replaceAll("\\s+", ""));
    }

    @Test
    void missingOcrProgramGivesClearError() throws Exception {
        assertThrows(PdfText.OcrUnavailableException.class,
                () -> new PdfText("/no/such/tesseract", "eng").extract(pdf(null)));
    }

    @Test
    void garbageIsRejected() {
        assertThrows(java.io.IOException.class, () -> new PdfText("tesseract", "eng").extract(new byte[] {1, 2, 3}));
    }
}
