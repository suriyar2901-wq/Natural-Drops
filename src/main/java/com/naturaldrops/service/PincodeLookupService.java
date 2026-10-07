package com.naturaldrops.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naturaldrops.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PincodeLookupService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public Map<String, Object> lookup(String code) {
        if (code == null || !code.matches("^[0-9]{6}$")) {
            throw new BadRequestException("Enter a 6-digit pincode");
        }
        String body;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.postalpincode.in/pincode/" + code,
                    HttpMethod.GET,
                    new HttpEntity<String>(headers),
                    String.class);
            body = response.getBody();
        } catch (RestClientException ex) {
            throw new BadRequestException("Could not check this pincode. Try again.");
        }
        List<Map<String, Object>> suggestions = new ArrayList<Map<String, Object>>();
        try {
            JsonNode root = objectMapper.readTree(body == null ? "[]" : body);
            JsonNode first = root.isArray() && root.size() > 0 ? root.get(0) : null;
            JsonNode offices = first == null ? null : first.get("PostOffice");
            if (first != null && "Success".equals(first.path("Status").asText()) && offices != null && offices.isArray()) {
                for (JsonNode office : offices) {
                    String area = office.path("Name").asText("");
                    String division = office.path("Division").asText("");
                    String block = office.path("Block").asText("");
                    String district = office.path("District").asText("");
                    String state = office.path("State").asText("");
                    String city = !division.isEmpty() ? division : (!block.isEmpty() ? block : area);
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("pincode", code);
                    row.put("name", area.isEmpty() ? code : area);
                    row.put("displayName", (area.isEmpty() ? code : area)
                            + (district.isEmpty() ? "" : ", " + district)
                            + (state.isEmpty() ? "" : ", " + state)
                            + " — " + code);
                    row.put("city", city);
                    row.put("district", district);
                    row.put("state", state);
                    suggestions.add(row);
                }
            }
        } catch (Exception ex) {
            throw new BadRequestException("Could not check this pincode. Try again.");
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("valid", !suggestions.isEmpty());
        result.put("suggestions", suggestions);
        return result;
    }
}
