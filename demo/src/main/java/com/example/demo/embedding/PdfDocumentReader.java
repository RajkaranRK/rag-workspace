package com.example.demo.embedding;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PdfDocumentReader {

    public record PageContent(
            int pageNumber,
            String text
    ) {}

    public List<PageContent> extractPages(String filePath)
            throws Exception {

        List<PageContent> pages = new ArrayList<>();

        File file = new File(filePath);

        try (PDDocument document = Loader.loadPDF(file)) {

            PDFTextStripper stripper = new PDFTextStripper();

            for (int page = 1;
                 page <= document.getNumberOfPages();
                 page++) {

                stripper.setStartPage(page);
                stripper.setEndPage(page);

                String text = stripper.getText(document);

                if (text != null && !text.isBlank()) {
                    pages.add(new PageContent(
                            page,
                            text.trim()
                    ));
                }
            }
        }

        return pages;
    }
}
