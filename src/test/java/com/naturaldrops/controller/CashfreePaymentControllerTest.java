package com.naturaldrops.controller;

import com.naturaldrops.exception.GlobalExceptionHandler;
import com.naturaldrops.exception.UnauthorizedException;
import com.naturaldrops.service.CashfreePaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CashfreePaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CashfreePaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CashfreePaymentService cashfreePaymentService;

    @Test
    void createOrderWithoutAuthenticatedBuyerReturnsUnauthorized() throws Exception {
        // currentUser attribute missing → controller throws UnauthorizedException
        mockMvc.perform(post("/api/payments/cashfree/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":50}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookInvalidSignaturePropagatesUnauthorized() throws Exception {
        when(cashfreePaymentService.handleWebhook(any(), any(), any()))
                .thenThrow(new UnauthorizedException("Invalid Cashfree webhook signature"));

        mockMvc.perform(post("/api/payments/cashfree/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-webhook-signature", "bad")
                        .header("x-webhook-timestamp", "1")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
