package kagaz.software.SPDF.controller.api.form;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import kagaz.software.common.util.FormUtils;
import kagaz.software.common.util.FormUtils.NewFormFieldDefinition;

class FormFieldDetectorTest {

    private static PDDocument pageWithLines(String... lines) throws Exception {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(50, 700);
            for (String line : lines) {
                content.showText(line);
                content.newLineAtOffset(0, -30);
            }
            content.endText();
        }
        return document;
    }

    @Test
    void underscoreRunBecomesATextField() throws Exception {
        try (PDDocument document = pageWithLines("Name: ______________________")) {
            List<NewFormFieldDefinition> found = FormFieldDetector.detect(document, 0.5f);
            assertEquals(1, found.size());
            assertEquals(FormUtils.FIELD_TYPE_TEXT, found.get(0).type());
            assertTrue(found.get(0).width() > 60);
            assertEquals(0, found.get(0).pageIndex());
        }
    }

    @Test
    void bracketPairBecomesACheckbox() throws Exception {
        try (PDDocument document = pageWithLines("[ ] I agree")) {
            List<NewFormFieldDefinition> found = FormFieldDetector.detect(document, 0.5f);
            assertEquals(1, found.size());
            assertEquals(FormUtils.FIELD_TYPE_CHECKBOX, found.get(0).type());
        }
    }

    @Test
    void plainTextYieldsNothingAndDetectedFieldsAreWritable() throws Exception {
        try (PDDocument document = pageWithLines("Just a paragraph.", "Phone: __________________")) {
            List<NewFormFieldDefinition> found = FormFieldDetector.detect(document, 0.5f);
            assertEquals(1, found.size());
            FormUtils.addNewFields(document, found, new java.util.ArrayList<>());
            assertNotNull(document.getDocumentCatalog().getAcroForm());
            assertEquals(1, document.getDocumentCatalog().getAcroForm().getFields().size());
        }
    }
}
