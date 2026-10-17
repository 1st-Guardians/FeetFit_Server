package com.feetfit.server.service.DrugService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.domain.enums.DrugCategory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DrugDataClientTest {
    private final List<ClientRequest> requests = new ArrayList<>();

    @Test
    void pageFetchMakesOneRequestWithoutPerProductPermitCallsAndKeepsDrugFields() {
        DrugDataClient client = client("""
                {"header":{"resultCode":"00"},"body":{"totalCount":35,"items":[{
                  "itemSeq":"199400709","itemName":"Stored cream","entpName":"Maker",
                  "efcyQesitm":"<p>Efficacy</p>","useMethodQesitm":"Usage",
                  "atpnQesitm":"Warning","intrcQesitm":"Interaction","seQesitm":"Side effect",
                  "depositMethodQesitm":"Storage"
                }]}}
                """);

        var page = client.fetchDrugPage(DrugCategory.ATHLETES_FOOT, 2, 100);

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).url().toString())
                .contains("pageNo=3", "numOfRows=100", "serviceKey=test%2Bkey%3D")
                .doesNotContain("%252B");
        assertThat(page.totalCount()).isEqualTo(35);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo("199400709");
            assertThat(item.getEfficacy()).isEqualTo("Efficacy");
            assertThat(item.getUsage()).isEqualTo("Usage");
            assertThat(item.getWarning()).isEqualTo("Warning");
            assertThat(item.getImage()).startsWith("https://").contains("s3.us-west-2.amazonaws.com");
            assertThat(item.getPermit()).isNull();
        });
    }

    @Test
    void permitFetchUsesItemCodeAndMapsStoredDetailFields() {
        var client = client("""
                {"header":{"resultCode":"00"},"body":{"items":[{
                  "ITEM_SEQ":"199400709","CANCEL_NAME":"","ITEM_PERMIT_DATE":"19940117",
                  "MAIN_ITEM_INGR":"Ingredient","ETC_OTC_CODE":"OTC","STORAGE_METHOD":"Keep dry"
                }]}}
                """);
        var permit = client.fetchPermitInfo("199400709");
        assertThat(permit.getMainIngredient()).isEqualTo("Ingredient");
        assertThat(permit.getPermitDate()).isEqualTo("1994.01.17");
        assertThat(permit.isCancelled()).isFalse();
        assertThat(requests.get(0).url().toString()).contains("item_seq=199400709");
    }

    @Test
    void mismatchedPermitIsAnImportFailureRatherThanClearingStoredPermit() {
        var client = client("""
                {"header":{"resultCode":"00"},"body":{"items":[{"ITEM_SEQ":"199400001"}]}}
                """);
        assertThatThrownBy(() -> client.fetchPermitInfo("199400709"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("did not match");
    }

    @Test
    void failedRecallIsUnavailableAndNeverReportedClear() {
        var client = client("""
                {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"errMsg":"SERVICE_KEY_ERROR"}}}
                """);
        var recall = client.fetchRecallInfo("199400709");
        assertThat(recall.getStatus()).isEqualTo("unavailable");
        assertThat(recall.getRecords()).isEmpty();
    }

    private DrugDataClient client(String body) {
        WebClient webClient = WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json").body(body).build());
        }).build();
        var client = new DrugDataClient(webClient, new ObjectMapper());
        ReflectionTestUtils.setField(client, "configuredServiceKey", "test%2Bkey%3D");
        ReflectionTestUtils.setField(client, "easyDrugEndpoint", "https://data.example/drugs");
        ReflectionTestUtils.setField(client, "permitEndpoint", "https://data.example/permits");
        ReflectionTestUtils.setField(client, "recallEndpoint", "https://data.example/recalls");
        ReflectionTestUtils.setField(client, "fallbackImageUrl", "https://bucket.s3.amazonaws.com/fallback.jpg");
        return client;
    }
}
