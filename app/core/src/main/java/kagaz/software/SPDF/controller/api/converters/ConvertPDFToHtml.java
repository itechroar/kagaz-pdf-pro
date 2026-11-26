package kagaz.software.SPDF.controller.api.converters;

import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;

import lombok.RequiredArgsConstructor;

import kagaz.software.SPDF.service.OfficeConversionService;
import kagaz.software.common.annotations.AutoJobPostMapping;
import kagaz.software.common.annotations.api.ConvertApi;
import kagaz.software.common.enumeration.ResourceWeight;
import kagaz.software.common.model.api.PDFFile;
import kagaz.software.common.model.tool.ToolFormat;
import kagaz.software.common.model.tool.ToolIO;
import kagaz.software.common.util.GeneralUtils;
import kagaz.software.common.util.TempFile;
import kagaz.software.common.util.TempFileManager;
import kagaz.software.common.util.WebResponseUtils;

@ConvertApi
@RequiredArgsConstructor
public class ConvertPDFToHtml {

    private final TempFileManager tempFileManager;
    private final OfficeConversionService officeConversionService;

    @AutoJobPostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            value = "/pdf/html",
            resourceWeight = ResourceWeight.MEDIUM_WEIGHT)
    @ToolIO(produces = ToolFormat.ZIP)
    @Operation(
            summary = "Convert PDF to HTML",
            description = "This endpoint converts a PDF file to HTML format.")
    public ResponseEntity<Resource> processPdfToHTML(@ModelAttribute PDFFile file)
            throws Exception {
        MultipartFile inputFile = file.getFileInput();
        ResponseEntity<Resource> html =
                officeConversionService.convert(
                        inputFile, "html", MediaType.TEXT_HTML, officeConversionService.settings());
        TempFile zip = tempFileManager.createManagedTempFile(".zip");
        try (var in = html.getBody().getInputStream();
                var out = new ZipOutputStream(Files.newOutputStream(zip.getPath()))) {
            out.putNextEntry(new ZipEntry("index.html"));
            in.transferTo(out);
            out.closeEntry();
        } catch (Exception e) {
            zip.close();
            throw e;
        }
        return WebResponseUtils.fileToWebResponse(
                zip,
                GeneralUtils.generateFilename(inputFile.getOriginalFilename(), ".zip"),
                MediaType.valueOf("application/zip"));
    }
}
