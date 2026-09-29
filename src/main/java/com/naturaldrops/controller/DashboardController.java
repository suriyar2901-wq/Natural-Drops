package com.naturaldrops.controller;

import com.naturaldrops.dto.response.ApiResponse;
import com.naturaldrops.dto.response.DashboardStatsResponse;
import com.naturaldrops.entity.User;
import com.naturaldrops.service.DashboardService;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    
    private final DashboardService dashboardService;
    
    @GetMapping
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getDashboardStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            HttpServletRequest request) {
        
        User currentUser = (User) request.getAttribute("currentUser");
        DashboardStatsResponse stats = dashboardService.getDashboardStats(fromDate, toDate, currentUser);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}

