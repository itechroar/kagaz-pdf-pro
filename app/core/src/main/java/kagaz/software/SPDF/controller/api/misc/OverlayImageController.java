package kagaz.software.SPDF.controller.api.misc;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import kagaz.software.SPDF.model.api.misc.OverlayImageRequest;
import kagaz.software.SPDF.utils.SvgOverlayUtil;
import kagaz.software.common.annotations.AutoJobPostMapping;
import kagaz.software.common.annotations.api.MiscApi;
import kagaz.software.common.enumeration.ResourceWeight;
import kagaz.software.common.model.tool.ToolFormat;
import kagaz.software.common.model.tool.ToolIO;
import kagaz.software.common.service.CustomPDFDocumentFactory;
import kagaz.software.common.util.GeneralUtils;
import kagaz.software.common.util.SvgSanitizer;
import kagaz.software.common.util.TempFile;
import kagaz.software.common.util.TempFileManager;
import kagaz.software.common.util.WebResponseUtils;

@MiscApi
@Slf4j
@RequiredArgsConstructor
public class OverlayImageController {

    private final CustomPDFDocumentFactory pdfDocumentFactory;
    private final TempFileManager tempFileManager;
    private final SvgSanitizer svgSanitizer;

    @AutoJobPostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            value = "/add-image",
            resourceWeight = ResourceWeight.MEDIUM_WEIGHT)
    @ToolIO(produces = ToolFormat.PDF)
    @Operation(
            summary = "Overlay an image at x,y on the first or every page",
            description =
                    "Draws a raster or SVG image at native size (1 px = 1 pt), with its"
                            + " lower-left corner at PDF user-space coordinates (x, y), in points."
                            + " The page's lower-left coordinates may be nonzero. To set size,"
                            + " grid position or pages, use add-stamp with stampType=image.")
    public ResponseEntity<Resource> overlayImage(@ModelAttribute OverlayImageRequest request) {
        MultipartFile pdfFile = request.getFileInput();
        MultipartFile imageFile = request.getImageFile();
        float x = request.getX();
        float y = request.getY();
        boolean everyPage = Boolean.TRUE.equals(request.getEveryPage());

        try {
            byte[] pdfBytes = pdfFile.getBytes();
            byte[] imageBytes = imageFile.getBytes();

            boolean isSvg = SvgOverlayUtil.isSvgImage(imageBytes);
            if (isSvg) {
                imageBytes = svgSanitizer.sanitize(imageBytes);
            }

            try (PDDocument document = pdfDocumentFactory.load(pdfBytes)) {
                int pages = document.getNumberOfPages();
                for (int i = 0; i < pages; i++) {
                    PDPage page = document.getPage(i);

                    if (isSvg) {
                        SvgOverlayUtil.overlaySvgOnPage(document, page, imageBytes, x, y);
                    } else {
                        try (PDPageContentStream contentStream =
                                new PDPageContentStream(
                                        document,
                                        page,
                                        PDPageContentStream.AppendMode.APPEND,
                                        true,
                                        true)) {
                            PDImageXObject image =
                                    PDImageXObject.createFromByteArray(document, imageBytes, "");
                            contentStream.drawImage(image, x, y);
                            log.info("Image successfully overlaid onto PDF page {}", i);
                        }
                    }

                    if (!everyPage && i == 0) {
                        break;
                    }
                }

                TempFile tempOut = tempFileManager.createManagedTempFile(".pdf");
                try {
                    document.save(tempOut.getFile());
                } catch (IOException e) {
                    tempOut.close();
                    throw e;
                }
                log.info("PDF with overlaid image successfully created");

                return WebResponseUtils.pdfFileToWebResponse(
                        tempOut,
                        GeneralUtils.generateFilename(pdfFile.getOriginalFilename(), ".pdf"));
            }

        } catch (IOException e) {
            log.error("Failed to add image to PDF", e);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}
