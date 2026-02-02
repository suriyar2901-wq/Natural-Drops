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
        
        return settingsMap;
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
}

