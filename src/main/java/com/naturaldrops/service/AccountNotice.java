package com.naturaldrops.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public final class AccountNotice {

    private AccountNotice() {
    }

    public static String digits(String phone) {
        if (phone == null) {
            return "";
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() > 10) {
            return digits.substring(digits.length() - 10);
        }
        return digits;
    }

    public static String message(String displayName, String username, String appUrl, String resetLink) {
        String name = displayName != null && displayName.trim().length() > 0 ? displayName.trim() : username;
        return "Hello " + name + ",\n\n"
                + "Your Natural Drops account details:\n"
                + "Username: " + username + "\n"
                + "Application: " + appUrl + "\n"
                + "Reset password: " + resetLink + "\n\n"
                + "The reset link expires in 15 minutes.\n";
    }

    public static String whatsappUrl(String phone, String message) {
        String digits = digits(phone);
        if (digits.length() != 10) {
            return "";
        }
        return "https://wa.me/91" + digits + "?text=" + encode(message);
    }

    public static String smsUrl(String phone, String message) {
        String digits = digits(phone);
        if (digits.length() != 10) {
            return "";
        }
        return "sms:+91" + digits + "?body=" + encode(message);
    }

    public static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return value == null ? "" : value;
        }
    }
}
