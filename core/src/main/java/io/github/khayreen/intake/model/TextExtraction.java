package io.github.khayreen.intake.model;

/**
 * Text read from a document by OCR (Textract on AWS, PDFBox locally).
 *
 * @param text       lines joined by newlines, in reading order
 * @param confidence mean OCR confidence from 0 to 100
 * @param pages      pages processed, used for cost
 */
public record TextExtraction(String text, double confidence, int pages) {

    public static TextExtraction ofText(String text) {
        return new TextExtraction(text, 100.0, 1);
    }
}
