package com.feetfit.server.web.controller;

import com.feetfit.server.config.SecurityConfig;
import com.feetfit.server.jwt.*;
import com.feetfit.server.service.DrugService.DrugQueryService;
import com.feetfit.server.service.DrugService.DrugRecommendationQueryService;
import com.feetfit.server.web.dto.drug.DrugResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DrugController.class)
@Import({SecurityConfig.class, JwtFilter.class, JwtAccessDeniedHandler.class,
        JwtAuthenticationEntryPoint.class, FindLoginUser.class})
class DrugRecommendationSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean DrugQueryService catalog;
    @MockBean DrugRecommendationQueryService recommendations;
    @MockBean TokenProvider tokenProvider;
    @MockBean(name = "jpaMappingContext") JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void personalRecommendationsAndRefreshRequireAuthentication() throws Exception {
        mvc.perform(get("/api/drugs/recommendations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/drugs/recommendations/refresh")).andExpect(status().isUnauthorized());
        verifyNoInteractions(recommendations);
    }

    @Test
    void publicCatalogRemainsAvailableWithoutAuthentication() throws Exception {
        when(catalog.getCategories()).thenReturn(DrugResponseDTO.DrugCategoryResultDTO.builder()
                .categories(List.of()).build());
        mvc.perform(get("/api/drugs/categories")).andExpect(status().isOk());
        verify(catalog).getCategories();
    }

    @Test
    @WithMockUser(username = "7")
    void usesAuthenticatedUserAndIgnoresSuppliedUserId() throws Exception {
        when(recommendations.get(7L, null, null, 0, 20)).thenReturn(
                DrugResponseDTO.DrugRecommendationResultDTO.builder().reportId(11L).summary("Stored summary")
                        .summaryStatus("COMPLETED").summarySource("GPT")
                        .items(List.of()).priorities(List.of()).build());
        mvc.perform(get("/api/drugs/recommendations").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.reportId").value(11))
                .andExpect(jsonPath("$.result.summary").value("Stored summary"))
                .andExpect(jsonPath("$.result.summaryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summarySource").value("GPT"))
                .andExpect(jsonPath("$.result.items").isArray());
        verify(recommendations).get(7L, null, null, 0, 20);
    }

    @Test
    @WithMockUser(username = "7")
    void refreshIsAcceptedAsBackgroundWork() throws Exception {
        mvc.perform(post("/api/drugs/recommendations/refresh")).andExpect(status().isAccepted());
        verify(recommendations).refresh(7L);
    }
}
