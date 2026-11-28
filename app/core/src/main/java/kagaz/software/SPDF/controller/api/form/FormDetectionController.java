package kagaz.software.SPDF.controller.api.form;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import tools.jackson.databind.ObjectMapper;

import kagaz.software.common.annotations.AutoJobPostMapping;
import kagaz.software.common.enumeration.ResourceWeight;
import kagaz.software.common.model.ApplicationProperties;
import kagaz.software.common.model.tool.ToolFormat;
import kagaz.software.common.model.tool.ToolIO;
import kagaz.software.common.service.CustomPDFDocumentFactory;
import kagaz.software.common.util.FormUtils;
import kagaz.software.common.util.TempFileManager;
import kagaz.software.common.util.WebResponseUtils;

/** Adds fillable fields where the page text shows a blank to write in. */
@RestController
@RequestMapping("/api/v1/form/form-detection")
@Tag(name = "Form", description = "Detect and add form fields")
@RequiredArgsConstructor
public class FormDetectionController {

    static final String SUMMARY_HEADER = "X-Kagaz-Detected-Fields";

    private final CustomPDFDocumentFactory pdfDocumentFactory;
    private final TempFileManager tempFileManager;
    private final ObjectMapper objectMapper;
    private final ApplicationProperties properties;

    @AutoJobPostMapping(
            value = "/detect",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            resourceWeight = ResourceWeight.MEDIUM_WEIGHT)
    @ToolIO(produces = ToolFormat.PDF)
    @Operation(
            summary = "Detect form fields",
            description =
                    "Finds blanks and check boxes in the text layer and adds fillable fields."
                            + " The counts are returned in the X-Kagaz-Detected-Fields header.")
    public ResponseEntity<Resource> detect(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "applyToPdf", defaultValue = "true") boolean applyToPdf,
            @RequestParam(value = "confThreshold", required = false) Float confThreshold)
            throws IOException {
        if (!properties.getFormDetection().isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Auto form detection is switched off");
        }
        float sensitivity = confThreshold == null ? 0.5f : confThreshold;
        try (PDDocument document = pdfDocumentFactory.load(file)) {
            List<FormUtils.NewFormFieldDefinition> fields =
                    FormFieldDetector.detect(document, sensitivity);
            if (applyToPdf && !fields.isEmpty()) {
                FormUtils.addNewFields(document, fields, new ArrayList<>());
            }
            String base = baseName(file.getOriginalFilename());
            ResponseEntity<Resource> response =
                    WebResponseUtils.pdfDocToWebResponse(
                            document, base + "_fields.pdf", tempFileManager);
            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .header(SUMMARY_HEADER, summaryJson(fields))
                    .header("Access-Control-Expose-Headers", SUMMARY_HEADER)
                    .body(response.getBody());
        }
    }

    private String summaryJson(List<FormUtils.NewFormFieldDefinition> fields) {
        Map<String, Integer> byType = new LinkedHashMap<>();
        TreeSet<Integer> pages = new TreeSet<>();
        for (FormUtils.NewFormFieldDefinition f : fields) {
            byType.merge(f.type(), 1, Integer::sum);
            pages.add(f.pageIndex());
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", fields.size());
        summary.put("byType", byType);
        summary.put("pagesWithFields", pages.size());
        return objectMapper.writeValueAsString(summary);
    }

    private static String baseName(String name) {
        String n = name == null || name.isBlank() ? "document" : name;
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        n = n.substring(slash + 1);
        return n.replaceFirst("(?i)\\.pdf$", "");
    }
}
