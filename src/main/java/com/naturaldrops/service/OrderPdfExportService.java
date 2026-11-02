package com.naturaldrops.service;

import com.naturaldrops.entity.Order;
import com.naturaldrops.entity.OrderItem;
import com.naturaldrops.entity.Seller;
import com.naturaldrops.entity.User;
import com.naturaldrops.repository.SellerRepository;
import com.naturaldrops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class OrderPdfExportService {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    private final SellerRepository sellerRepository;
    private final UserRepository userRepository;
    private final SellerNetworkService sellerNetworkService;

    public byte[] generateSingleOrderPdf(Order order, String sellerNameFallback) {
        try (PDDocument doc = new PDDocument()) {
            addOrderAsPage(doc, order, sellerNameFallback);
            return toBytes(doc);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate order PDF", e);
        }
    }

    public byte[] generateMultiOrderPdf(List<Order> orders, String sellerNameFallback) {
        try (PDDocument doc = new PDDocument()) {
            for (Order o : orders) {
                addOrderAsPage(doc, o, sellerNameFallback);
            }
            return toBytes(doc);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate orders PDF", e);
        }
    }

    private void addOrderAsPage(PDDocument doc, Order order, String sellerNameFallback) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        float pageWidth = page.getMediaBox().getWidth();
        float pageHeight = page.getMediaBox().getHeight();
        float margin = 42f;
        float contentWidth = pageWidth - (margin * 2);
        float y = pageHeight - margin;

        SellerProfile seller = resolveSeller(order, sellerNameFallback);

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            // Brand header band
            cs.setNonStrokingColor(2 / 255f, 50 / 255f, 170 / 255f);
            cs.addRect(0, pageHeight - 78, pageWidth, 78);
            cs.fill();
            cs.setNonStrokingColor(1f, 1f, 1f);

            y = pageHeight - 34;
            y = writeText(cs, PDType1Font.HELVETICA_BOLD, 18, margin, y, safe(seller.companyName));
            writeTextInline(cs, PDType1Font.HELVETICA, 10, pageWidth - margin - 90, pageHeight - 30, "TAX INVOICE");
            y = writeText(cs, PDType1Font.HELVETICA, 9, margin, y - 2, "Order Invoice");
            if (!seller.companyCode.isEmpty()) {
                y = writeText(cs, PDType1Font.HELVETICA, 9, margin, y, "Company code: " + seller.companyCode);
            }

            cs.setNonStrokingColor(0.12f, 0.14f, 0.18f);
            y = pageHeight - 98;

            // Seller details card
            y = writeText(cs, PDType1Font.HELVETICA_BOLD, 11, margin, y, "Seller details");
            y -= 2;
            for (String line : wrap(seller.addressLine, 88)) {
                y = writeText(cs, PDType1Font.HELVETICA, 9, margin, y, line);
            }
            if (!seller.phone.isEmpty()) {
                y = writeText(cs, PDType1Font.HELVETICA, 9, margin, y, "Phone: " + seller.phone);
            }
            if (!seller.email.isEmpty()) {
                y = writeText(cs, PDType1Font.HELVETICA, 9, margin, y, "Email: " + seller.email);
            }
            if (!seller.ownerName.isEmpty()) {
                y = writeText(cs, PDType1Font.HELVETICA, 9, margin, y, "Owner: " + seller.ownerName);
            }

            y -= 8;
            y = drawAccentLine(cs, margin, y, pageWidth - margin);
            y -= 14;

            // Order + status row
            String status = order.getStatus() != null ? order.getStatus().toString() : "";
            y = writeText(cs, PDType1Font.HELVETICA_BOLD, 12, margin, y, "Order #" + order.getId());
            writeTextInline(cs, PDType1Font.HELVETICA_BOLD, 10, pageWidth - margin - 110, y + 16,
                    "Status: " + titleCase(status));
            y = writeText(cs, PDType1Font.HELVETICA, 9, margin, y,
                    "Date & Time: " + (order.getOrderDate() != null ? order.getOrderDate().format(DATE_TIME_FMT) : "-"));
            if (order.getPaymentStatus() != null) {
                y = writeText(cs, PDType1Font.HELVETICA, 9, margin, y,
                        "Payment: " + titleCase(order.getPaymentStatus().toString()));
            }

            y -= 8;
            // Two-column buyer / delivery
            float midX = margin + (contentWidth / 2f) + 8;
            float leftY = y;
            float rightY = y;

            leftY = writeText(cs, PDType1Font.HELVETICA_BOLD, 10, margin, leftY, "Bill to");
            leftY = writeText(cs, PDType1Font.HELVETICA, 9, margin, leftY, "Buyer: " + safe(order.getBuyerName()));
            leftY = writeText(cs, PDType1Font.HELVETICA, 9, margin, leftY, "Phone: " + safe(order.getBuyerPhone()));

            rightY = writeText(cs, PDType1Font.HELVETICA_BOLD, 10, midX, rightY, "Delivery");
            for (String line : wrap("Address: " + safe(order.getDeliveryAddress()), 48)) {
                rightY = writeText(cs, PDType1Font.HELVETICA, 9, midX, rightY, line);
            }
            if (order.getScheduledDeliveryDate() != null) {
                String when = order.getScheduledDeliveryDate().format(DATE_FMT);
                if (order.getEstimatedDelivery() != null) {
                    when += " at " + order.getEstimatedDelivery().format(TIME_FMT);
                }
                rightY = writeText(cs, PDType1Font.HELVETICA, 9, midX, rightY, "Scheduled: " + when);
            }

            y = Math.min(leftY, rightY) - 10;
            y = drawAccentLine(cs, margin, y, pageWidth - margin);
            y -= 14;

            // Items table
            y = writeText(cs, PDType1Font.HELVETICA_BOLD, 11, margin, y, "Items");
            y -= 4;

            float colQty = margin;
            float colName = margin + 36;
            float colRate = pageWidth - margin - 150;
            float colTotal = pageWidth - margin - 70;

            // Table header background
            cs.setNonStrokingColor(0.93f, 0.94f, 0.97f);
            cs.addRect(margin, y - 4, contentWidth, 18);
            cs.fill();
            cs.setNonStrokingColor(0.12f, 0.14f, 0.18f);

            writeTextInline(cs, PDType1Font.HELVETICA_BOLD, 9, colQty, y, "Qty");
            writeTextInline(cs, PDType1Font.HELVETICA_BOLD, 9, colName, y, "Item");
            writeTextInline(cs, PDType1Font.HELVETICA_BOLD, 9, colRate, y, "Rate");
            writeTextInline(cs, PDType1Font.HELVETICA_BOLD, 9, colTotal, y, "Total");
            y -= 18;
            y = drawAccentLine(cs, margin, y, pageWidth - margin);
            y -= 12;

            BigDecimal computedTotal = BigDecimal.ZERO;
            if (order.getItems() != null) {
                boolean shade = false;
                for (OrderItem item : order.getItems()) {
                    int qty = item.getCartQuantity() != null
                            ? item.getCartQuantity()
                            : (item.getQuantity() != null ? item.getQuantity() : 0);
                    BigDecimal rate = item.getRate() != null ? item.getRate() : BigDecimal.ZERO;
                    BigDecimal lineTotal = item.getSubtotal() != null
                            ? item.getSubtotal()
                            : rate.multiply(new BigDecimal(qty));
                    computedTotal = computedTotal.add(lineTotal);

                    if (shade) {
                        cs.setNonStrokingColor(0.97f, 0.98f, 0.99f);
                        cs.addRect(margin, y - 3, contentWidth, 16);
                        cs.fill();
                        cs.setNonStrokingColor(0.12f, 0.14f, 0.18f);
                    }
                    shade = !shade;

                    writeTextInline(cs, PDType1Font.HELVETICA, 9, colQty, y, String.valueOf(qty));
                    writeTextInline(cs, PDType1Font.HELVETICA, 9, colName, y, truncate(safe(item.getItemName()), 42));
                    writeTextInline(cs, PDType1Font.HELVETICA, 9, colRate, y, "Rs " + formatMoney(rate));
                    writeTextInline(cs, PDType1Font.HELVETICA, 9, colTotal, y, "Rs " + formatMoney(lineTotal));
                    y -= 16;
                    if (y < 110) {
                        break;
                    }
                }
            }

            y -= 6;
            y = drawAccentLine(cs, margin, y, pageWidth - margin);
            y -= 18;

            BigDecimal total = order.getTotal() != null ? order.getTotal() : computedTotal;
            if (order.getFinalBillAmount() != null) {
                total = order.getFinalBillAmount();
            }

            // Totals box
            float boxX = pageWidth - margin - 180;
            float boxW = 180;
            cs.setNonStrokingColor(0.93f, 0.94f, 0.97f);
            cs.addRect(boxX, y - 28, boxW, 40);
            cs.fill();
            cs.setNonStrokingColor(0.12f, 0.14f, 0.18f);
            writeTextInline(cs, PDType1Font.HELVETICA_BOLD, 11, boxX + 12, y, "Grand Total");
            writeTextInline(cs, PDType1Font.HELVETICA_BOLD, 12, boxX + 100, y, "Rs " + formatMoney(total));
            y -= 48;

            cs.setNonStrokingColor(0.45f, 0.48f, 0.55f);
            y = writeText(cs, PDType1Font.HELVETICA, 8, margin, y,
                    "Thank you for your order with " + safe(seller.companyName) + ".");
            y = writeText(cs, PDType1Font.HELVETICA, 8, margin, y,
                    "This is a computer-generated invoice.");
            cs.setNonStrokingColor(0.12f, 0.14f, 0.18f);
        }
    }

    private SellerProfile resolveSeller(Order order, String fallbackName) {
        SellerProfile profile = new SellerProfile();
        profile.companyName = fallbackName != null && !fallbackName.trim().isEmpty() ? fallbackName.trim() : "Seller";
        profile.companyCode = "";
        profile.addressLine = "";
        profile.phone = "";
        profile.email = "";
        profile.ownerName = "";

        Long sellerUserId = order != null ? order.getSellerUserId() : null;
        if (sellerUserId == null && order != null && order.getBuyerId() != null) {
            sellerUserId = sellerNetworkService.sellerUserIdForBuyer(order.getBuyerId());
        }

        Seller seller = null;
        if (sellerUserId != null) {
            seller = sellerRepository.findByUserId(sellerUserId).orElse(null);
        }
        if (seller == null && order != null && order.getSellerBusinessName() != null
                && !order.getSellerBusinessName().trim().isEmpty()) {
            profile.companyName = order.getSellerBusinessName().trim();
        }

        if (seller != null) {
            if (seller.getBusinessName() != null && !seller.getBusinessName().trim().isEmpty()) {
                profile.companyName = seller.getBusinessName().trim();
            }
            if (seller.getCompanyCode() != null) {
                profile.companyCode = seller.getCompanyCode().trim();
            } else if (seller.getSellerCode() != null) {
                profile.companyCode = seller.getSellerCode().trim();
            }
            profile.addressLine = formatSellerAddress(seller);
            if (seller.getMobile() != null) {
                profile.phone = seller.getMobile().trim();
            }
            if ((profile.phone.isEmpty()) && seller.getAlternateMobile() != null) {
                profile.phone = seller.getAlternateMobile().trim();
            }
            if (seller.getEmail() != null) {
                profile.email = seller.getEmail().trim();
            }
            if (seller.getOwnerName() != null) {
                profile.ownerName = seller.getOwnerName().trim();
            }
            return profile;
        }

        // Fallback: seller user profile if no Seller row
        if (sellerUserId != null) {
            User sellerUser = userRepository.findById(sellerUserId).orElse(null);
            if (sellerUser != null) {
                if (sellerUser.getFullName() != null && !sellerUser.getFullName().trim().isEmpty()) {
                    profile.companyName = sellerUser.getFullName().trim();
                } else if (sellerUser.getUsername() != null) {
                    profile.companyName = sellerUser.getUsername();
                }
                profile.addressLine = formatUserAddress(sellerUser);
                if (sellerUser.getPhone() != null && !sellerUser.getPhone().trim().isEmpty()) {
                    profile.phone = sellerUser.getPhone().trim();
                }
                if (sellerUser.getEmail() != null) {
                    profile.email = sellerUser.getEmail().trim();
                }
            }
        }
        return profile;
    }

    private static String formatSellerAddress(Seller seller) {
        List<String> parts = new ArrayList<String>();
        if (notBlank(seller.getBusinessAddress())) {
            parts.add(seller.getBusinessAddress().trim());
        }
        if (notBlank(seller.getArea())) {
            parts.add(seller.getArea().trim());
        }
        if (notBlank(seller.getCity())) {
            parts.add(seller.getCity().trim());
        }
        if (notBlank(seller.getPincode())) {
            parts.add(seller.getPincode().trim());
        }
        return String.join(", ", parts);
    }

    private static String formatUserAddress(User user) {
        List<String> parts = new ArrayList<String>();
        if (notBlank(user.getHouseDoorNo())) parts.add(user.getHouseDoorNo().trim());
        if (notBlank(user.getStreetArea())) parts.add(user.getStreetArea().trim());
        if (notBlank(user.getCity())) parts.add(user.getCity().trim());
        if (notBlank(user.getDistrict())) parts.add(user.getDistrict().trim());
        if (notBlank(user.getState())) parts.add(user.getState().trim());
        if (notBlank(user.getPincode())) parts.add(user.getPincode().trim());
        if (!parts.isEmpty()) {
            return String.join(", ", parts);
        }
        return user.getAddress() != null ? user.getAddress().trim() : "";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
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
            cs.endText();
        }
        return y - 14f;
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

    private static float drawAccentLine(PDPageContentStream cs, float x1, float y, float x2) throws IOException {
        cs.setStrokingColor(2 / 255f, 50 / 255f, 170 / 255f);
        cs.setLineWidth(1.2f);
        cs.moveTo(x1, y);
        cs.lineTo(x2, y);
        cs.stroke();
        cs.setStrokingColor(0f, 0f, 0f);
        cs.setLineWidth(1f);
        return y;
    }

    private static List<String> wrap(String text, int maxChars) {
        List<String> lines = new ArrayList<String>();
        String value = safe(text);
        if (value.isEmpty()) {
            lines.add("-");
            return lines;
        }
        while (value.length() > maxChars) {
            int breakAt = value.lastIndexOf(' ', maxChars);
            if (breakAt < 20) {
                breakAt = maxChars;
            }
            lines.add(value.substring(0, breakAt).trim());
            value = value.substring(breakAt).trim();
        }
        if (!value.isEmpty()) {
            lines.add(value);
        }
        return lines;
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String titleCase(String value) {
        if (value == null || value.isEmpty()) return "";
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String safe(String s) {
        if (s == null) return "";
        String out = s.replace('\n', ' ').replace('\r', ' ');
        out = out.replace("₹", "Rs ");
        out = out.replace("\u00A0", " ");
        StringBuilder sb = new StringBuilder(out.length());
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            if (c <= 0x7E) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static String formatMoney(BigDecimal v) {
        if (v == null) return "0.00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static class SellerProfile {
        private String companyName;
        private String companyCode;
        private String addressLine;
        private String phone;
        private String email;
        private String ownerName;
    }
}
