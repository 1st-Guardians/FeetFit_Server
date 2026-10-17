package com.feetfit.server.web.controller;

import com.feetfit.server.config.SecurityConfig;
import com.feetfit.server.jwt.*;
import com.feetfit.server.service.DrugService.DrugQueryService;
import com.feetfit.server.service.DrugService.DrugRecommendationQueryService;
import com.feetfit.server.web.dto.drug.DrugResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
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
    void publicListDoesNotExposePersonalSummaryFromSuppliedUserId() throws Exception {
        when(catalog.getDrugList("athletes-foot", null, 0, 20)).thenReturn(catalogPage());

        mvc.perform(get("/api/drugs").param("userId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.category").value("athletes-foot"))
                .andExpect(jsonPath("$.result.items[0].id").value("199304286"))
                .andExpect(jsonPath("$.result.hasMeasurement").value(false))
                .andExpect(jsonPath("$.result.summary").value(nullValue()))
                .andExpect(jsonPath("$.result.summaryStatus").value("NOT_READY"))
                .andExpect(jsonPath("$.result.summarySource").value("NONE"));

        verifyNoInteractions(recommendations);
    }

    @Test
    @WithMockUser(username = "7")
    void existingListIncludesAuthenticatedUsersSummaryAndPreservesProductFields() throws Exception {
        var page = catalogPage();
        when(catalog.getDrugList("athletes-foot", "cream", 1, 2)).thenReturn(page);
        when(recommendations.withRecommendationSummary(7L, page)).thenReturn(page.toBuilder()
                .hasMeasurement(true).reportId(11L).measurementSessionId(21L)
                .summary("Stored personal summary").summaryStatus("COMPLETED").summarySource("GPT").build());

        mvc.perform(get("/api/drugs").param("category", "athletes-foot").param("q", "cream")
                        .param("page", "1").param("size", "2").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.category").value("athletes-foot"))
                .andExpect(jsonPath("$.result.source").value("Official source"))
                .andExpect(jsonPath("$.result.items[0].id").value("199304286"))
                .andExpect(jsonPath("$.result.hasMeasurement").value(true))
                .andExpect(jsonPath("$.result.reportId").value(11))
                .andExpect(jsonPath("$.result.measurementSessionId").value(21))
                .andExpect(jsonPath("$.result.summary").value("Stored personal summary"))
                .andExpect(jsonPath("$.result.summaryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summarySource").value("GPT"));

        verify(recommendations).withRecommendationSummary(7L, page);
        verifyNoMoreInteractions(recommendations);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT_READY", "PENDING", "GENERATING", "FAILED"})
    @WithMockUser(username = "7")
    void listExposesRecommendationReadinessWithoutBlocking(String summaryStatus) throws Exception {
        var page = catalogPage();
        when(catalog.getDrugList("athletes-foot", null, 0, 20)).thenReturn(page);
        when(recommendations.withRecommendationSummary(7L, page)).thenReturn(page.toBuilder()
                .hasMeasurement(true).summaryStatus(summaryStatus).build());

        mvc.perform(get("/api/drugs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summaryStatus").value(summaryStatus))
                .andExpect(jsonPath("$.result.items[0].id").value("199304286"));
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

    private DrugResponseDTO.DrugListResultDTO catalogPage() {
        return DrugResponseDTO.DrugListResultDTO.builder()
                .category("athletes-foot").categoryLabel("Fungal care").source("Official source")
                .items(List.of(DrugResponseDTO.DrugItemDTO.builder().id("199304286").name("Stored cream").build()))
                .currentPage(1).totalPages(2).totalElements(3).hasNext(false).build();
    }
}
