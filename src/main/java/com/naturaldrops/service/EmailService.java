package com.naturaldrops.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    
    private final JavaMailSender mailSender;
    
    @Value("${spring.mail.username:no-reply@naturaldrops.com}")
    private String fromEmail;
    
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;
    
    public boolean sendPasswordResetToken(String toEmail, String displayName, String username, String token, String otp) {
        try {
            // Validate email configuration
            if (!mailReady()) {
                return false;
            }
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Your Natural Drops account");
            
            String resetLink = baseUrl + "/ResetPassword?token=" + token;
            String safeName = (displayName != null && !displayName.trim().isEmpty()) ? displayName : username;
            String emailBody = AccountNotice.message(safeName, username, baseUrl, resetLink);
            if (otp != null && otp.trim().length() > 0) {
                emailBody = emailBody + "\nYou can also reset with this OTP: " + otp + "\n";
            }
            
            message.setText(emailBody);
            
            log.debug("Attempting to send password reset email to: {} from: {}", toEmail, fromEmail);
            mailSender.send(message);
            log.info("Password reset email sent successfully to: {}", toEmail);
            return true;
        } catch (MailAuthenticationException e) {
            // Gmail authentication failure - likely wrong password or not using app password
            // This wraps javax.mail.AuthenticationFailedException
            log.error("❌ Gmail SMTP authentication failed for email: {}. " +
                    "\nTroubleshooting steps:" +
                    "\n1. Ensure 2-Step Verification is enabled in Gmail" +
                    "\n2. Generate App Password at: https://myaccount.google.com/apppasswords" +
                    "\n3. Use the 16-character App Password (not regular password) in application.properties" +
                    "\n4. Set spring.mail.username=your-email@gmail.com" +
                    "\n5. Set spring.mail.password=your-16-char-app-password" +
                    "\nError details: {}", toEmail, e.getMessage());
            if (e.getCause() != null) {
                log.error("Root cause: {}", e.getCause().getMessage());
            }
            return false;
        } catch (MailException e) {
            // Other mail-related exceptions
            log.error("❌ Failed to send password reset email to: {}. Error: {}", toEmail, e.getMessage());
            if (e.getCause() != null) {
                log.error("Root cause: {}", e.getCause().getMessage());
            }
            return false;
        } catch (Exception e) {
            // Unexpected exceptions
            log.error("❌ Unexpected error sending password reset email to: {}. Error: {} - {}", 
                    toEmail, e.getClass().getSimpleName(), e.getMessage());
            log.error("Stack trace:", e);
            return false;
        }
    }
    
    public boolean sendBuyerInvite(String toEmail, String buyerName, String username, String companyName, String resetLink) {
        if (toEmail == null || toEmail.trim().isEmpty()) {
            return false;
        }
        try {
            if (!mailReady()) {
                return false;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail.trim());
            message.setSubject("Your " + (companyName != null ? companyName : "Natural Drops") + " buyer account");
            String displayName = buyerName != null && buyerName.trim().length() > 0 ? buyerName : username;
            message.setText(AccountNotice.message(displayName, username, baseUrl, resetLink)
                    + "\nCompany: " + (companyName != null ? companyName : "Natural Drops") + "\n");
            mailSender.send(message);
            log.info("Buyer invite email sent to: {}", toEmail);
            return true;
        } catch (Exception e) {
            log.error("Failed to send buyer invite email to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }

    public boolean sendBuyerInviteCopy(String toEmail, String sellerName, String username, String buyerName, String companyName, String resetLink) {
        if (toEmail == null || toEmail.trim().isEmpty()) {
            return false;
        }
        try {
            if (!mailReady()) {
                return false;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail.trim());
            message.setSubject("Buyer created: " + username);
            message.setText(
                    "Dear " + (sellerName != null ? sellerName : "Seller") + ",\n\n" +
                    "You created a buyer account for " + buyerName + ".\n\n" +
                    "Company: " + (companyName != null ? companyName : "Natural Drops") + "\n" +
                    "Username: " + username + "\n\n" +
                    "Share this password-setup link with the buyer:\n" +
                    resetLink + "\n\n" +
                    "The buyer must create a new password using this link before they can log in.\n\n" +
                    "Best regards,\n" +
                    "Natural Drops"
            );
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("Failed to send seller buyer-invite copy to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }

    public boolean sendPasswordResetOtp(String toEmail, String otp) {
        try {
            // Validate email configuration
            if (!mailReady()) {
                return false;
            }
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Password Reset OTP - Natural Drops");
            
            String emailBody = String.format(
                "Hello,\n\n" +
                "You have requested to reset your password for your Natural Drops account.\n\n" +
                "Your OTP code is: %s\n\n" +
                "This OTP will expire in 15 minutes.\n\n" +
                "If you did not request this password reset, please ignore this email.\n\n" +
                "Best regards,\n" +
                "Natural Drops Team",
                otp
            );
            
            message.setText(emailBody);
            
            log.debug("Attempting to send password reset OTP email to: {} from: {}", toEmail, fromEmail);
            mailSender.send(message);
            log.info("Password reset OTP email sent successfully to: {}", toEmail);
            return true;
        } catch (MailAuthenticationException e) {
            // Gmail authentication failure
            // This wraps javax.mail.AuthenticationFailedException
            log.error("❌ Gmail SMTP authentication failed for email: {}. " +
                    "\nTroubleshooting steps:" +
                    "\n1. Ensure 2-Step Verification is enabled in Gmail" +
                    "\n2. Generate App Password at: https://myaccount.google.com/apppasswords" +
                    "\n3. Use the 16-character App Password (not regular password) in application.properties" +
                    "\n4. Set spring.mail.username=your-email@gmail.com" +
                    "\n5. Set spring.mail.password=your-16-char-app-password" +
                    "\nError details: {}", toEmail, e.getMessage());
            if (e.getCause() != null) {
                log.error("Root cause: {}", e.getCause().getMessage());
            }
            return false;
        } catch (MailException e) {
            // Other mail-related exceptions
            log.error("❌ Failed to send password reset OTP email to: {}. Error: {}", toEmail, e.getMessage());
            if (e.getCause() != null) {
                log.error("Root cause: {}", e.getCause().getMessage());
            }
            return false;
        } catch (Exception e) {
            // Unexpected exceptions
            log.error("❌ Unexpected error sending password reset OTP email to: {}. Error: {} - {}", 
                    toEmail, e.getClass().getSimpleName(), e.getMessage());
            log.error("Stack trace:", e);
            return false;
        }
    }

    public boolean sendAccountDetails(String toEmail, String displayName, String username, String resetLink) {
        if (toEmail == null || toEmail.trim().isEmpty() || !toEmail.contains("@")) {
            return false;
        }
        if (!mailReady()) {
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail.trim());
            message.setSubject("Your Natural Drops account");
            message.setText(AccountNotice.message(displayName, username, baseUrl, resetLink));
            mailSender.send(message);
            log.info("Account details email sent to {}", toEmail);
            return true;
        } catch (Exception e) {
            log.error("Failed to send account details email to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }

    private boolean mailReady() {
        if (fromEmail == null || fromEmail.trim().isEmpty() || fromEmail.contains("your-email")) {
            log.error("Email not configured. Set MAIL_USERNAME and MAIL_PASSWORD.");
            return false;
        }
        return true;
    }
}

