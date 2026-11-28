package kagaz.software.SPDF.controller.api.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences;
import org.apache.pdfbox.util.Matrix;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.v3.oas.annotations.Operation;

import lombok.RequiredArgsConstructor;

import kagaz.software.SPDF.config.swagger.JsonDataResponse;
import kagaz.software.common.annotations.AutoJobPostMapping;
import kagaz.software.common.annotations.api.SecurityApi;
import kagaz.software.common.enumeration.ResourceWeight;
import kagaz.software.common.model.api.PDFFile;
import kagaz.software.common.model.tool.ToolFormat;
import kagaz.software.common.model.tool.ToolIO;
import kagaz.software.common.service.CustomPDFDocumentFactory;

/**
 * Structural accessibility check. It inspects what PDFBox can see (tagging, title, language,
 * fonts, images, permissions); it is not a full PDF/UA validator and says so in {@code
 * humanChecks}.
 */
@SecurityApi
@RequiredArgsConstructor
public class AccessibilityReportController {

    private static final int MAX_FIGURES = 200;
    private static final int OUTLINE_PAGE_THRESHOLD = 20;

    private final CustomPDFDocumentFactory pdfDocumentFactory;

    @AutoJobPostMapping(
            value = "/accessibility-report",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            resourceWeight = ResourceWeight.MEDIUM_WEIGHT)
    @JsonDataResponse
    @ToolIO(produces = ToolFormat.JSON)
    @Operation(
            summary = "Check PDF accessibility",
            description =
                    "Reports structural accessibility problems: missing tags, title, language,"
                            + " unembedded fonts and images without a description.")
    public ResponseEntity<Map<String, Object>> report(
            @ModelAttribute PDFFile file,
            @RequestParam(value = "profile", defaultValue = "ua1") String profile)
            throws IOException {
        try (PDDocument document = pdfDocumentFactory.load(file.getFileInput())) {
            return ResponseEntity.ok(analyse(document, profile));
        }
    }

    static Map<String, Object> analyse(PDDocument document, String profile) throws IOException {
        PDDocumentCatalog catalog = document.getDocumentCatalog();
        boolean tagged =
                catalog.getMarkInfo() != null
                        && catalog.getMarkInfo().isMarked()
                        && catalog.getStructureTreeRoot() != null;
        String title = document.getDocumentInformation().getTitle();
        boolean hasTitle = title != null && !title.isBlank();
        PDViewerPreferences prefs = catalog.getViewerPreferences();
        boolean displaysTitle = prefs != null && prefs.displayDocTitle();
        String language = catalog.getLanguage();
        boolean hasLanguage = language != null && !language.isBlank();
        boolean encrypted = document.isEncrypted();
        boolean blocksAccessTools =
                encrypted
                        && !document.getCurrentAccessPermission().canExtractForAccessibility();
        int pages = document.getNumberOfPages();

        Set<String> unembedded = unembeddedFonts(document);
        List<Map<String, Object>> figures = findFigures(document);

        List<Map<String, Object>> issues = new ArrayList<>();
        if (!tagged) {
            issues.add(
                    issue(
                            "7.1",
                            "1",
                            "The document is not tagged, so screen readers have no reading"
                                    + " structure.",
                            "error",
                            1,
                            false));
        }
        if (!hasTitle) {
            issues.add(issue("7.1", "8", "The document has no title.", "error", 1, true));
        } else if (!displaysTitle) {
            issues.add(
                    issue(
                            "7.1",
                            "9",
                            "The title is set but the viewer shows the file name instead.",
                            "warning",
                            1,
                            true));
        }
        if (!hasLanguage) {
            issues.add(
                    issue(
                            "7.2",
                            "34",
                            "The document language is not declared.",
                            "error",
                            1,
                            true));
        }
        if (!unembedded.isEmpty()) {
            issues.add(
                    issue(
                            "7.21",
                            "4.1",
                            unembedded.size() + " font(s) are not embedded: " + unembedded,
                            "error",
                            unembedded.size(),
                            false));
        }
        if (blocksAccessTools) {
            issues.add(
                    issue(
                            "7.16",
                            "1",
                            "Encryption blocks text extraction for assistive technology.",
                            "error",
                            1,
                            false));
        }
        if (tagged && !figures.isEmpty()) {
            issues.add(
                    issue(
                            "7.3",
                            "1",
                            figures.size() + " image(s) may need an alternative description.",
                            "warning",
                            figures.size(),
                            false));
        }
        if (pages >= OUTLINE_PAGE_THRESHOLD && catalog.getDocumentOutline() == null) {
            issues.add(
                    issue(
                            "7.18",
                            "3",
                            "A document of " + pages + " pages has no bookmarks.",
                            "warning",
                            1,
                            false));
        }

        int fixable = (int) issues.stream().filter(i -> (boolean) i.get("autoFixable")).count();
        long errors = issues.stream().filter(i -> "error".equals(i.get("severity"))).count();
        int rules = 8;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pages", pages);
        summary.put("hasTitle", hasTitle);
        summary.put("displaysDocTitle", displaysTitle);
        summary.put("hasLanguage", hasLanguage);
        summary.put("allFontsEmbedded", unembedded.isEmpty());
        summary.put("unembeddedFonts", unembedded.size());
        summary.put("figures", figures.size());
        summary.put("encrypted", encrypted);

        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("total", rules);
        checks.put("failed", issues.size());
        checks.put("passed", Math.max(0, rules - issues.size()));
        checks.put("rulesInProfile", rules);
        checks.put("rulesFailed", issues.size());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("profile", "ua2".equalsIgnoreCase(profile) ? "PDF/UA-2" : "PDF/UA-1");
        report.put("tagged", tagged);
        report.put("declaresConformance", false);
        report.put("passesAutomatedChecks", errors == 0);
        report.put("validationError", null);
        report.put("issues", issues);
        report.put("humanChecks", humanChecks());
        report.put("automaticallyFixable", fixable);
        report.put("needsInput", issues.size() - fixable);
        report.put("figuresNeedingDescription", tagged ? List.of() : figures);
        report.put("summary", summary);
        report.put("checks", checks);
        return report;
    }

    private static List<String> humanChecks() {
        return List.of(
                "Reading order matches the visual order.",
                "Headings and lists are marked up as headings and lists.",
                "Every image that conveys information has a meaningful description.",
                "Colour is not the only way information is conveyed, and contrast is sufficient.",
                "This is a structural check; it does not replace a full PDF/UA validation.");
    }

    private static Map<String, Object> issue(
            String clause,
            String test,
            String message,
            String severity,
            int occurrences,
            boolean autoFixable) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("clause", clause);
        issue.put("testNumber", test);
        issue.put("message", message);
        issue.put("severity", severity);
        issue.put("occurrences", occurrences);
        issue.put("autoFixable", autoFixable);
        return issue;
    }

    private static Set<String> unembeddedFonts(PDDocument document) {
        Set<String> names = new HashSet<>();
        for (PDPage page : document.getPages()) {
            PDResources resources = page.getResources();
            if (resources == null) {
                continue;
            }
            for (COSName key : resources.getFontNames()) {
                try {
                    PDFont font = resources.getFont(key);
                    if (font != null && !font.isEmbedded() && !font.isStandard14()) {
                        names.add(font.getName());
                    }
                } catch (IOException | RuntimeException e) {
                    // an unreadable font dictionary is not evidence either way
                }
            }
        }
        return names;
    }

    private static List<Map<String, Object>> findFigures(PDDocument document) {
        List<Map<String, Object>> figures = new ArrayList<>();
        for (int i = 0; i < document.getNumberOfPages() && figures.size() < MAX_FIGURES; i++) {
            ImageCollector collector = new ImageCollector(i, figures);
            try {
                collector.processPage(document.getPage(i));
            } catch (IOException | RuntimeException e) {
                // a page whose content cannot be parsed contributes no figures
            }
        }
        return figures;
    }

    /** Records each drawn image with its placement, in points from the page's top-left. */
    private static final class ImageCollector extends PDFStreamEngine {
        private final int pageIndex;
        private final List<Map<String, Object>> figures;

        ImageCollector(int pageIndex, List<Map<String, Object>> figures) {
            this.pageIndex = pageIndex;
            this.figures = figures;
            addOperator(new Concatenate(this));
            addOperator(new Save(this));
            addOperator(new Restore(this));
            addOperator(new SetMatrix(this));
            addOperator(new SetGraphicsStateParameters(this));
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands)
                throws IOException {
            if ("Do".equals(operator.getName())
                    && !operands.isEmpty()
                    && operands.get(0) instanceof COSName name) {
                PDXObject xobject = getResources().getXObject(name);
                if (xobject instanceof PDImageXObject) {
                    record();
                } else if (xobject instanceof PDFormXObject form) {
                    showForm(form);
                }
                return;
            }
            super.processOperator(operator, operands);
        }

        private void record() {
            if (figures.size() >= MAX_FIGURES) {
                return;
            }
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            float pageHeight = getCurrentPage().getCropBox().getHeight();
            float width = Math.abs(ctm.getScalingFactorX());
            float height = Math.abs(ctm.getScalingFactorY());
            Map<String, Object> figure = new LinkedHashMap<>();
            figure.put("key", "p" + pageIndex + "-i" + figures.size());
            figure.put("page", pageIndex + 1);
            figure.put("kind", "image");
            figure.put("x", ctm.getTranslateX());
            figure.put("y", pageHeight - ctm.getTranslateY() - height);
            figure.put("width", width);
            figure.put("height", height);
            figure.put("existingAlt", null);
            figures.add(figure);
        }
    }
}
