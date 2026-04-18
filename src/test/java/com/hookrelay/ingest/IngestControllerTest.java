package com.hookrelay.ingest;

import com.hookrelay.config.AdminKeyFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = IngestController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AdminKeyFilter.class)
)
class IngestControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    IngestService ingestService;

    @Test
    void validSignatureReturns200() throws Exception {
        when(ingestService.ingest(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidSignatureReturns400() throws Exception {
        when(ingestService.ingest(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "invalid")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingSigHeaderReturns400() throws Exception {
        mockMvc.perform(post("/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
