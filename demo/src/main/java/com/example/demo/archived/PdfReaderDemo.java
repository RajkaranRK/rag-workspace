package com.example.demo.archived;

import com.example.demo.constants.Constants;
import com.example.demo.embedding.PdfDocumentReader;

import java.util.List;

public class PdfReaderDemo {

    public static void main(String[] args) throws Exception {

        String filePath = Constants.filePath;

        PdfDocumentReader reader = new PdfDocumentReader();

        List<PdfDocumentReader.PageContent> pages =
                reader.extractPages(filePath);

        System.out.println("Total pages extracted: " + pages.size());

        for (PdfDocumentReader.PageContent page : pages) {

            System.out.println("\nPage: " + page.pageNumber());

            System.out.println(
                    page.text().substring(
                            0,
                            Math.min(200, page.text().length())
                    )
            );

            System.out.println("--------------------------------");
        }
    }
}
