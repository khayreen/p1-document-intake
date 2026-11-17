package io.github.khayreen.intake.app.ocr;

import io.github.khayreen.intake.model.TextExtraction;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Local stand-in for Textract: reads the text layer of a PDF. Synthetic forms have a perfect text
 * layer, so confidence is 100; OCR-style errors are printed into the forms by the generator instead.
 */
@Component
public class PdfTextReader {

    public TextExtraction read(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return new TextExtraction(stripper.getText(doc).strip(), 100.0, doc.getNumberOfPages());
        }
    }
}
