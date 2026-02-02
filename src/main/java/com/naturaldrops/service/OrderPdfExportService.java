package com.naturaldrops.service;

import com.naturaldrops.entity.Order;
import com.naturaldrops.entity.OrderItem;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class OrderPdfExportService {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");

    public byte[] generateSingleOrderPdf(Order order, String sellerName) {
        try (PDDocument doc = new PDDocument()) {
            addOrderAsPage(doc, order, sellerName);
            return toBytes(doc);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate order PDF", e);
        }
    }

    public byte[] generateMultiOrderPdf(List<Order> orders, String sellerName) {
        try (PDDocument doc = new PDDocument()) {
            for (Order o : orders) {
                addOrderAsPage(doc, o, sellerName);
            }
            return toBytes(doc);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate orders PDF", e);
        }
    }

    private void addOrderAsPage(PDDocument doc, Order order, String sellerName) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        float margin = 48f;
        float y = page.getMediaBox().getHeight() - margin;
        float x = margin;
        float leading = 16f;

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            // Header
            y = writeText(cs, PDType1Font.HELVETICA_BOLD, 16, x, y, "Order Invoice");
            y -= 4;
            y = writeText(cs, PDType1Font.HELVETICA, 10, x, y, "Store/Seller: " + safe(sellerName));
            y = writeText(cs, PDType1Font.HELVETICA, 10, x, y, "Status: " + safe(order.getStatus() != null ? order.getStatus().toString() : ""));

            y -= 10;
            y = drawLine(cs, x, y, page.getMediaBox().getWidth() - margin);
            y -= 12;

            // Order meta
            y = writeText(cs, PDType1Font.HELVETICA_BOLD, 11, x, y, "Order #" + order.getId());
            y = writeText(cs, PDType1Font.HELVETICA, 10, x, y, "Date & Time: " + (order.getOrderDate() != null ? order.getOrderDate().format(DATE_TIME_FMT) : ""));
            y = writeText(cs, PDType1Font.HELVETICA, 10, x, y, "Buyer: " + safe(order.getBuyerName()));
            y = writeText(cs, PDType1Font.HELVETICA, 10, x, y, "Phone: " + safe(order.getBuyerPhone()));

            y -= 8;
            y = writeText(cs, PDType1Font.HELVETICA_BOLD, 11, x, y, "Delivery");
            y = writeText(cs, PDType1Font.HELVETICA, 10, x, y, "Address: " + safe(order.getDeliveryAddress()));
            String coords = "";
            if (order.getLatitude() != null && order.getLongitude() != null) {
                coords = order.getLatitude() + ", " + order.getLongitude();
            }
            y = writeText(cs, PDType1Font.HELVETICA, 10, x, y, "Map Coordinates: " + coords);

            y -= 10;
            y = drawLine(cs, x, y, page.getMediaBox().getWidth() - margin);
            y -= 12;

            // Items table header
            y = writeText(cs, PDType1Font.HELVETICA_BOLD, 11, x, y, "Items");
            y -= 6;

            float colQty = x;
            float colName = x + 40;
            float colRate = page.getMediaBox().getWidth() - margin - 160;
            float colTotal = page.getMediaBox().getWidth() - margin - 60;

            y = writeText(cs, PDType1Font.HELVETICA_BOLD, 10, colQty, y, "Qty");
            writeTextInline(cs, PDType1Font.HELVETICA_BOLD, 10, colName, y, "Item");
            writeTextInline(cs, PDType1Font.HELVETICA_BOLD, 10, colRate, y, "Rate");
            writeTextInline(cs, PDType1Font.HELVETICA_BOLD, 10, colTotal, y, "Total");
            y -= 6;
            y = drawLine(cs, x, y, page.getMediaBox().getWidth() - margin);
            y -= 12;

            BigDecimal computedTotal = BigDecimal.ZERO;
            if (order.getItems() != null) {
                for (OrderItem item : order.getItems()) {
                    int qty = item.getCartQuantity() != null ? item.getCartQuantity() : (item.getQuantity() != null ? item.getQuantity() : 0);
                    BigDecimal rate = item.getRate() != null ? item.getRate() : BigDecimal.ZERO;
                    BigDecimal lineTotal = item.getSubtotal() != null ? item.getSubtotal() : rate.multiply(new BigDecimal(qty));
                    computedTotal = computedTotal.add(lineTotal);

                    y = writeText(cs, PDType1Font.HELVETICA, 10, colQty, y, String.valueOf(qty));
                    writeTextInline(cs, PDType1Font.HELVETICA, 10, colName, y, safe(item.getItemName()));
                    writeTextInline(cs, PDType1Font.HELVETICA, 10, colRate, y, formatMoney(rate));
                    writeTextInline(cs, PDType1Font.HELVETICA, 10, colTotal, y, formatMoney(lineTotal));
                    y -= leading;
                    if (y < 120) { // simple overflow guard
                        y = 120;
                        break;
                    }
                }
            }

            y -= 4;
            y = drawLine(cs, x, y, page.getMediaBox().getWidth() - margin);
            y -= 14;

            BigDecimal total = order.getTotal() != null ? order.getTotal() : computedTotal;
            writeTextInline(cs, PDType1Font.HELVETICA_BOLD, 12, page.getMediaBox().getWidth() - margin - 160, y, "Total:");
            writeTextInline(cs, PDType1Font.HELVETICA_BOLD, 12, page.getMediaBox().getWidth() - margin - 60, y, formatMoney(total));
        }
    }

    private static byte[] toBytes(PDDocument doc) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private static float writeText(PDPageContentStream cs, org.apache.pdfbox.pdmodel.font.PDFont font, int size, float x, float y, String text) throws IOException {
        cs.beginText();
        try {
            cs.setFont(font, size);
            cs.newLineAtOffset(x, y);
            cs.showText(safe(text));
        } finally {
            // Ensure endText is called even if showText throws due to encoding issues
            cs.endText();
        }
        return y - 16f;
    }

    private static void writeTextInline(PDPageContentStream cs, org.apache.pdfbox.pdmodel.font.PDFont font, int size, float x, float y, String text) throws IOException {
        cs.beginText();
        try {
            cs.setFont(font, size);
            cs.newLineAtOffset(x, y);
            cs.showText(safe(text));
        } finally {
            cs.endText();
        }
    }

    private static float drawLine(PDPageContentStream cs, float x1, float y, float x2) throws IOException {
        cs.moveTo(x1, y);
        cs.lineTo(x2, y);
        cs.stroke();
        return y;
    }

    private static String safe(String s) {
        if (s == null) return "";
        // PDFBox Type1 fonts are WinAnsi; replace unsupported chars (ex: ₹) to avoid IllegalArgumentException.
        // Keep output readable for invoices.
        String out = s.replace('\n', ' ').replace('\r', ' ');
        out = out.replace("₹", "Rs ");
        out = out.replace("\u00A0", " "); // non-breaking space
        // If any other non-ASCII slips in, replace with '?'
        StringBuilder sb = new StringBuilder(out.length());
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            if (c <= 0x7E) {
                sb.append(c);
            } else {
                // keep common punctuation if possible, otherwise '?'
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static String formatMoney(BigDecimal v) {
        if (v == null) return "0.00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}


