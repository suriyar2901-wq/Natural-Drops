package com.naturaldrops.service;

import com.naturaldrops.entity.Setting;
import com.naturaldrops.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingsService {
    
    private final SettingRepository settingRepository;
    
    public Map<String, String> getAllSettings() {
        List<Setting> settings = settingRepository.findAll();
        Map<String, String> settingsMap = new HashMap<>();
        settings.forEach(setting -> settingsMap.put(setting.getSettingKey(), setting.getSettingValue()));
        
        // Return default values if empty
        if (settingsMap.isEmpty()) {
            settingsMap.put("businessName", "Natural Drops");
            settingsMap.put("whatsappNumber", "");
            settingsMap.put("enableWhatsAppAuto", "false");
            settingsMap.put("qrCodeImage", "assets/upi-qr.png");
            settingsMap.put("upiId", "");
            settingsMap.put("businessPhone", "");
            settingsMap.put("businessEmail", "");
            settingsMap.put("businessAddress", "");
        }

        putDefault(settingsMap, "adminName", "Platform Admin");
        putDefault(settingsMap, "adminMobile", "");
        putDefault(settingsMap, "adminEmail", "");
        putDefault(settingsMap, "supportPhone", settingsMap.get("businessPhone") != null ? settingsMap.get("businessPhone") : "");
        putDefault(settingsMap, "supportWhatsapp", settingsMap.get("whatsappNumber") != null ? settingsMap.get("whatsappNumber") : "");
        putDefault(settingsMap, "supportEmail", settingsMap.get("customerSupportEmail") != null ? settingsMap.get("customerSupportEmail") : "");
        putDefault(settingsMap, "notifyActivation", "true");
        putDefault(settingsMap, "notifyPayments", "true");
        putDefault(settingsMap, "notifyFailedPayments", "true");
        putDefault(settingsMap, "notifyExpiry", "true");
        putDefault(settingsMap, "expiryReminderDays", "5");
        putDefault(settingsMap, "notifySevenDayReminder", "true");
        putDefault(settingsMap, "subscriptionRequired", "true");
        putDefault(settingsMap, "planMonthlyAmount", "499.00");
        putDefault(settingsMap, "planYearlyAmount", "5389.20");
        
        return settingsMap;
    }
    
    public Map<String, String> supportContacts() {
        Map<String, String> settings = getAllSettings();
        String phone = firstFilled(settings.get("supportPhone"), settings.get("businessPhone"), settings.get("adminMobile"));
        String whatsapp = firstFilled(settings.get("supportWhatsapp"), settings.get("whatsappNumber"), phone);
        String email = firstFilled(settings.get("supportEmail"), settings.get("customerSupportEmail"), settings.get("adminEmail"), settings.get("businessEmail"));
        Map<String, String> contacts = new HashMap<String, String>();
        contacts.put("phone", phone);
        contacts.put("whatsapp", whatsapp);
        contacts.put("email", email);
        return contacts;
    }

    private String firstFilled(String... values) {
        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "";
    }

    public String getSetting(String key) {
        return settingRepository.findBySettingKey(key)
                .map(Setting::getSettingValue)
                .orElse(null);
    }
    
    @Transactional
    public void updateSettings(Map<String, String> updates) {
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            Setting setting = settingRepository.findBySettingKey(entry.getKey())
                    .orElse(new Setting());
            
            setting.setSettingKey(entry.getKey());
            setting.setSettingValue(entry.getValue());
            setting.setUpdatedAt(LocalDateTime.now());
            
            settingRepository.save(setting);
        }
    }
    
    @Transactional
    public void updateSetting(String key, String value) {
        Setting setting = settingRepository.findBySettingKey(key)
                .orElse(new Setting());
        
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        setting.setUpdatedAt(LocalDateTime.now());
        
        settingRepository.save(setting);
    }

    private void putDefault(Map<String, String> settingsMap, String key, String value) {
        if (!settingsMap.containsKey(key) || settingsMap.get(key) == null) {
            settingsMap.put(key, value);
        }
    }
}

