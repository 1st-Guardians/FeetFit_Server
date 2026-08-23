package com.feetfit.server.web.controller;

import com.feetfit.server.config.InternalApiKeyInterceptor;
import com.feetfit.server.config.InternalApiWebConfig;
import com.feetfit.server.apiPayload.exception.ExceptionAdvice;
import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.service.ShoeService.ShoeIngestionService;
import com.feetfit.server.web.dto.shoe.ShoeIngestionResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShoeIngestionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({InternalApiWebConfig.class, InternalApiKeyInterceptor.class, ExceptionAdvice.class})
@TestPropertySource(properties = "internal.api-key=test-service-key")
class ShoeIngestionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ShoeIngestionService shoeIngestionService;
    @MockBean TokenProvider tokenProvider;
    @MockBean(name = "jpaMappingContext") JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void rejectsMissingServiceKeyBeforeMalformedBodyIsDeserialized() throws Exception {
        mockMvc.perform(post("/internal/shoes/imports/musinsa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401"));

        verifyNoInteractions(shoeIngestionService);
    }

    @Test
    void acceptsCrawlerContractWithServiceKey() throws Exception {
        when(shoeIngestionService.importMusinsa(any())).thenReturn(
                ShoeIngestionResponseDTO.ImportResult.builder()
                        .requestedCount(1)
                        .processedCount(1)
                        .items(List.of())
                        .build());

        mockMvc.perform(post("/internal/shoes/imports/musinsa")
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, "test-service-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source":"MUSINSA",
                                  "collectedAt":"2026-08-23T12:00:00",
                                  "shoes":[{
                                    "goodsNo":"100",
                                    "brandName":"Brand",
                                    "shoeName":"Shoe",
                                    "modelCode":"MODEL-1",
                                    "musinsaUrl":"https://musinsa/100",
                                    "sourceBrandKey":"brand",
                                    "reviewCount":0,
                                    "reviews":[]
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.requestedCount").value(1))
                .andExpect(jsonPath("$.result.processedCount").value(1));
    }
}
