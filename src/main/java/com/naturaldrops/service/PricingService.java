package com.naturaldrops.service;

import com.naturaldrops.entity.ShopProfile;
import com.naturaldrops.repository.ShopProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PricingService {

    private static final double EARTH_KM = 6371.0;

    private final SettingsService settingsService;
    private final ShopProfileRepository shopProfileRepository;
    private final SellerNetworkService sellerNetworkService;

    public Map<String, Object> quote(BigDecimal subtotal, Long sellerUserId, Double buyerLat, Double buyerLng) {
        BigDecimal safeSubtotal = subtotal != null ? subtotal.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        Map<String, String> fees = settingsService.feeSettings();

        boolean taxEnabled = !"false".equalsIgnoreCase(String.valueOf(fees.get("taxEnabled")));
        BigDecimal taxRate = parseDecimal(fees.get("taxRatePercent"), new BigDecimal("5"));
        BigDecimal tax = taxEnabled
                ? safeSubtotal.multiply(taxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        boolean platformEnabled = "true".equalsIgnoreCase(String.valueOf(fees.get("platformFeeEnabled")));
        BigDecimal platformFee = platformEnabled
                ? parseDecimal(fees.get("platformFeeAmount"), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        Long resolvedSellerUserId = sellerUserId;
        ShopProfile profile = resolvedSellerUserId != null
                ? shopProfileRepository.findBySellerUserId(resolvedSellerUserId).orElse(null)
                : null;

        BigDecimal perKm = profile != null && profile.getDeliveryChargePerKm() != null
                ? profile.getDeliveryChargePerKm()
                : BigDecimal.ZERO;
        if (perKm.compareTo(BigDecimal.ZERO) < 0) {
            perKm = BigDecimal.ZERO;
        }

        Double distanceKm = null;
        BigDecimal delivery = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (perKm.compareTo(BigDecimal.ZERO) > 0) {
            if (buyerLat != null && buyerLng != null
                    && profile != null && profile.getLatitude() != null && profile.getLongitude() != null) {
                distanceKm = haversineKm(profile.getLatitude(), profile.getLongitude(), buyerLat, buyerLng);
                // Bill at least 1 km when rate is set and distance is known
                double billableKm = Math.max(1.0, Math.ceil(distanceKm * 10.0) / 10.0);
                delivery = perKm.multiply(BigDecimal.valueOf(billableKm)).setScale(2, RoundingMode.HALF_UP);
            } else {
                // No GPS pair yet — charge 1 km as the minimum when seller set a per-km rate
                distanceKm = 1.0;
                delivery = perKm.setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal total = safeSubtotal.add(tax).add(delivery).add(platformFee).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> quote = new HashMap<String, Object>();
        quote.put("subtotal", safeSubtotal.setScale(2, RoundingMode.HALF_UP));
        quote.put("taxEnabled", Boolean.valueOf(taxEnabled));
        quote.put("taxRatePercent", taxRate);
        quote.put("tax", tax);
        quote.put("platformFeeEnabled", Boolean.valueOf(platformEnabled));
        quote.put("platformFee", platformFee);
        quote.put("deliveryChargePerKm", perKm.setScale(2, RoundingMode.HALF_UP));
        quote.put("distanceKm", distanceKm);
        quote.put("deliveryCharge", delivery);
        quote.put("total", total);
        quote.put("sellerUserId", resolvedSellerUserId);
        return quote;
    }

    public Map<String, Object> quoteForBuyer(Long buyerUserId, BigDecimal subtotal, Double buyerLat, Double buyerLng) {
        Long sellerUserId = sellerNetworkService.sellerUserIdForBuyer(buyerUserId);
        return quote(subtotal, sellerUserId, buyerLat, buyerLng);
    }

    public BigDecimal totalForBuyer(Long buyerUserId, BigDecimal subtotal, Double buyerLat, Double buyerLng) {
        Object total = quoteForBuyer(buyerUserId, subtotal, buyerLat, buyerLng).get("total");
        return total instanceof BigDecimal ? (BigDecimal) total : new BigDecimal(String.valueOf(total));
    }

    private static BigDecimal parseDecimal(String raw, BigDecimal fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_KM * c;
    }
}
