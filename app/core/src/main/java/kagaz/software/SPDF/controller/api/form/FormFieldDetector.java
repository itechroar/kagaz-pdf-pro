package kagaz.software.SPDF.controller.api.form;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import kagaz.software.common.util.FormUtils;
import kagaz.software.common.util.FormUtils.NewFormFieldDefinition;

/**
 * Finds places a person is meant to write, from the text layer alone: runs of underscores or dots
 * become text fields and empty check-box glyphs or {@code [ ]} become check boxes. Scanned pages
 * have no text layer and yield nothing.
 */
final class FormFieldDetector {

    private static final Pattern BLANK_RUN = Pattern.compile("(_{3,}|\\.{6,}|-{6,})");
    private static final Pattern CHECKBOX = Pattern.compile("[\\u2610\\u25A1\\u25FB\\u274F]|\\[\\s{1,2}\\]");
    private static final float MIN_FIELD_WIDTH = 24f;
    private static final float CHECKBOX_SIZE = 12f;

    private FormFieldDetector() {}

    /**
     * @param sensitivity 0..1; lower values keep shorter blanks, higher values keep only long ones
     */
    static List<NewFormFieldDefinition> detect(PDDocument document, float sensitivity)
            throws IOException {
        float minWidth = MIN_FIELD_WIDTH + Math.max(0f, Math.min(1f, sensitivity)) * 40f;
        List<NewFormFieldDefinition> found = new ArrayList<>();
        for (int page = 0; page < document.getNumberOfPages(); page++) {
            PageScanner scanner = new PageScanner(page, minWidth, found);
            scanner.setStartPage(page + 1);
            scanner.setEndPage(page + 1);
            scanner.getText(document);
        }
        return found;
    }

    private static final class PageScanner extends PDFTextStripper {
        private final int pageIndex;
        private final float minWidth;
        private final List<NewFormFieldDefinition> out;

        PageScanner(int pageIndex, float minWidth, List<NewFormFieldDefinition> out)
                throws IOException {
            this.pageIndex = pageIndex;
            this.minWidth = minWidth;
            this.out = out;
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions)
                throws IOException {
            if (positions.isEmpty() || text.length() != positions.size()) {
                return;
            }
            Matcher blanks = BLANK_RUN.matcher(text);
            while (blanks.find()) {
                TextPosition first = positions.get(blanks.start());
                TextPosition last = positions.get(blanks.end() - 1);
                float width = last.getXDirAdj() + last.getWidthDirAdj() - first.getXDirAdj();
                if (width >= minWidth) {
                    addText(first, width);
                }
            }
            Matcher boxes = CHECKBOX.matcher(text);
            while (boxes.find()) {
                addCheckbox(positions.get(boxes.start()));
            }
        }

        private float pdfY(TextPosition p) {
            PDPage page = getCurrentPage();
            return page.getCropBox().getHeight() - p.getYDirAdj();
        }

        private void addText(TextPosition at, float width) {
            float height = Math.max(14f, at.getFontSizeInPt() + 4f);
            out.add(
                    new NewFormFieldDefinition(
                            "field_" + (out.size() + 1),
                            null,
                            FormUtils.FIELD_TYPE_TEXT,
                            pageIndex,
                            at.getXDirAdj(),
                            pdfY(at) - 2f,
                            width,
                            height,
                            false,
                            false,
                            null,
                            null,
                            null,
                            null,
                            false,
                            false,
                            null,
                            null));
        }

        private void addCheckbox(TextPosition at) {
            out.add(
                    new NewFormFieldDefinition(
                            "field_" + (out.size() + 1),
                            null,
                            FormUtils.FIELD_TYPE_CHECKBOX,
                            pageIndex,
                            at.getXDirAdj(),
                            pdfY(at) - 2f,
                            CHECKBOX_SIZE,
                            CHECKBOX_SIZE,
                            false,
                            false,
                            null,
                            null,
                            null,
                            null,
                            false,
                            false,
                            null,
                            null));
        }
    }
}
