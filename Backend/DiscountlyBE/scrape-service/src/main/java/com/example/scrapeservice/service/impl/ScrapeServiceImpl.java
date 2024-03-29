package com.example.scrapeservice.service.impl;

import com.example.scrapeservice.model.Promotions;
import com.example.scrapeservice.model.Stores;
import com.example.scrapeservice.repository.PromotionsRepository;
import com.example.scrapeservice.repository.StoresRepository;
import com.example.scrapeservice.service.ScrapeService;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
@Service
public class ScrapeServiceImpl implements ScrapeService {
    @Value("${billa.categories}")
    private String billaCategoryUrl;

    private static final int BILLA_ID = 1;

    private final PromotionsRepository promotionsRepository;

    private final StoresRepository storesRepository;

    @Override
    public void scrapeData() {
        scrapeBillaPromotions();
    }

    private String getProductTitle(Element product, String className) {
        try {
            Element productTitleElement = product.selectFirst(className);

            if (productTitleElement != null) {
                return productTitleElement.text().trim();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private String getProductDiscountPhrase(Element product, String className) {
        try {
            Element discountPhraseElement = product.selectFirst(className);

            if (discountPhraseElement != null) {
                return discountPhraseElement.text().trim();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private Double getBillaProductOldPrice(Element product, String className) {
        try {
            Elements productPrices = product.select(className);
            Double productOldPrice = null;

            if (productPrices.size() == 2) {
                String oldPriceText = productPrices.get(0).text().trim();
                productOldPrice = Double.parseDouble(oldPriceText);
            }

            return productOldPrice;
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Double getBillaProductNewPrice(Element product, String className) {
        try {
            Elements productPrices = product.select(className);
            Double productNewPrice = null;

            if (productPrices.size() == 2) {
                productNewPrice = Double.parseDouble(productPrices.get(1).text().trim());
            } else if (productPrices.size() == 1) {
                productNewPrice = Double.parseDouble(productPrices.get(0).text().trim());
            }

            return productNewPrice;
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Date getBillaPromotionStart(Document document) {
        Element dateDiv = document.selectFirst("div.date");

        if (dateDiv != null) {
            String[] promotionText = dateDiv.text().split(" ");
            try {
                LocalDate localDate = LocalDate.parse(promotionText[promotionText.length - 5], DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                return Date.valueOf(localDate);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    private Date getBillaPromotionEnd(Document document) {
        Element dateDiv = document.selectFirst("div.date");

        if (dateDiv != null) {
            String[] promotionText = dateDiv.text().split(" ");
            try {
                LocalDate localDate = LocalDate.parse(promotionText[promotionText.length - 2], DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                return Date.valueOf(localDate);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    private void scrapeBillaPromotions() {
        final Document document;

        try {
            document = Jsoup.connect(billaCategoryUrl).get();
            Date billaPromotionStart = getBillaPromotionStart(document);
            Date billaPromotionEnd = getBillaPromotionEnd(document);

            Stores billaStore = storesRepository.findById(BILLA_ID)
                    .orElseThrow(() -> new IllegalArgumentException("Store not found with ID: " + BILLA_ID));

            Promotions billaPromotion = Promotions.builder()
                    .startDate(billaPromotionStart)
                    .endDate(billaPromotionEnd)
                    .storesByStoreId(billaStore)
                    .build();

            promotionsRepository.save(billaPromotion);

            Elements products = document.select("div.product");

            for (int i = 5; i < products.size() - 10; i++) {
                String productTitle = getProductTitle(products.get(i), ".actualProduct");
                Double productOldPrice = getBillaProductOldPrice(products.get(i), ".price");
                Double productNewPrice = getBillaProductNewPrice(products.get(i), ".price");
                String productDiscountPhrase = getProductDiscountPhrase(products.get(i), ".discount");

                System.out.printf("%s %f %f %s", productTitle, productOldPrice, productNewPrice, productDiscountPhrase);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
