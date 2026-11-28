package kagaz.software.SPDF.controller.api.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

class AccessibilityReportControllerTest {

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> issues(Map<String, Object> report) {
        return (List<Map<String, Object>>) report.get("issues");
    }

    @Test
    void untaggedUntitledDocumentFailsTheStructuralChecks() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            Map<String, Object> report = AccessibilityReportController.analyse(document, "ua1");
            assertEquals("PDF/UA-1", report.get("profile"));
            assertEquals(false, report.get("tagged"));
            assertEquals(false, report.get("passesAutomatedChecks"));
            assertTrue(issues(report).size() >= 3);
        }
    }

    @Test
    void titleAndLanguageClearTheirIssues() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.getDocumentInformation().setTitle("Report");
            document.getDocumentCatalog().setLanguage("en-US");
            Map<String, Object> report = AccessibilityReportController.analyse(document, "ua2");
            assertEquals("PDF/UA-2", report.get("profile"));
            assertTrue(
                    issues(report).stream()
                            .noneMatch(i -> "34".equals(i.get("testNumber"))));
            assertTrue(
                    issues(report).stream().noneMatch(i -> "8".equals(i.get("testNumber"))));
        }
    }
}
