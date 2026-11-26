package kagaz.software.kagaz.ai;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Reads a PDF's text. Pages without a text layer (scans) are recognized with Tesseract, which
 * works offline. No length limit is applied here; the caller splits long text into chunks.
 */
final class PdfText {

    /** Raised when a scan needs OCR but Tesseract cannot be run. */
    static class OcrUnavailableException extends IOException {
        OcrUnavailableException(String message) {
            super(message);
        }
    }

    record Extract(String text, boolean usedOcr) {}

    private final String tesseract;
    private final String languages;

    PdfText(String tesseractPath, String languages) {
        this.tesseract = tesseractPath.isBlank() ? "tesseract" : tesseractPath;
        this.languages = languages.isBlank() ? "eng" : languages;
    }

    Extract extract(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc).strip();
            if (!text.isEmpty() || doc.getNumberOfPages() == 0) {
                return new Extract(text, false);
            }
            return new Extract(ocr(doc), true);
        }
    }

    private static final float OCR_DPI = 200f;
    private static final double MAX_OCR_PIXELS = 40_000_000d;

    /** Lowers the render DPI for oversized pages so one hostile page cannot exhaust memory. */
    static float dpiFor(org.apache.pdfbox.pdmodel.PDPage page) {
        var box = page.getMediaBox();
        double inches = Math.max(1d, (box.getWidth() / 72d) * (box.getHeight() / 72d));
        double fit = Math.sqrt(MAX_OCR_PIXELS / inches);
        return (float) Math.max(10d, Math.min(OCR_DPI, fit));
    }

    private String ocr(PDDocument doc) throws IOException {
        Path dir = Files.createTempDirectory("kagaz-ocr");
        try {
            PDFRenderer renderer = new PDFRenderer(doc);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, dpiFor(doc.getPage(i)), ImageType.GRAY);
                Path png = dir.resolve("p" + i + ".png");
                ImageIO.write(img, "png", png.toFile());
                out.append(runTesseract(png)).append("\n\n");
            }
            return out.toString().strip();
        } finally {
            try (var files = Files.list(dir)) {
                files.forEach(f -> f.toFile().delete());
            } catch (IOException ignored) {
                // temp files are small; the OS cleans the directory eventually
            }
            dir.toFile().delete();
        }
    }

    private String runTesseract(Path png) throws IOException {
        Process p;
        try {
            p =
                    new ProcessBuilder(tesseract, png.toString(), "stdout", "-l", languages)
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start();
        } catch (IOException e) {
            throw new OcrUnavailableException(
                    "This PDF is a scan and the OCR program (Tesseract) is not installed on the server");
        }
        try {
            byte[] bytes = p.getInputStream().readAllBytes();
            if (!p.waitFor(120, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new OcrUnavailableException("OCR took too long on one page");
            }
            if (p.exitValue() != 0) {
                throw new OcrUnavailableException(
                        "OCR failed; check that the language data for '" + languages + "' is installed");
            }
            return new String(bytes, StandardCharsets.UTF_8).strip();
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new OcrUnavailableException("Interrupted");
        }
    }

    /** Splits on paragraph boundaries where possible so each piece is at most {@code size} chars. */
    static java.util.List<String> chunks(String text, int size) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int max = Math.max(1000, size);
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(text.length(), pos + max);
            if (end < text.length()) {
                int cut = text.lastIndexOf("\n\n", end);
                if (cut <= pos + max / 2) {
                    cut = text.lastIndexOf('\n', end);
                }
                if (cut <= pos + max / 2) {
                    cut = text.lastIndexOf(' ', end);
                }
                if (cut > pos + max / 2) {
                    end = cut;
                }
            }
            String piece = text.substring(pos, end).strip();
            if (!piece.isEmpty()) {
                out.add(piece);
            }
            pos = end;
        }
        return out;
    }
}
