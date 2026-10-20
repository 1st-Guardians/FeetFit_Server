package com.feetfit.server.service.DrugService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.domain.enums.DrugCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import javax.net.ssl.SSLHandshakeException;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DrugDataClientTest {
    private final List<ClientRequest> requests = new CopyOnWriteArrayList<>();
    private static final String EMPTY_PAGE = "{\"header\":{\"resultCode\":\"00\"},\"body\":{\"totalCount\":0,\"items\":[]}}";

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

    @Test
    void defaultTimeoutIsThirtySeconds() {
        assertThat(ReflectionTestUtils.getField(client(EMPTY_PAGE), "requestTimeoutSeconds")).isEqualTo(30L);
    }

    @Test
    void requestTimeoutRetriesTheSamePageAndCanRecover() {
        AtomicInteger attempts = new AtomicInteger();
        var client = client(request -> attempts.incrementAndGet() == 1 ? Mono.never()
                : Mono.just(response(HttpStatus.OK, EMPTY_PAGE)));
        ReflectionTestUtils.setField(client, "requestTimeoutSeconds", 1L);

        assertThat(client.fetchDrugPage(DrugCategory.WOUND_CARE, 2, 100).totalCount()).isZero();

        assertThat(requests).hasSize(2);
        assertThat(requests).extracting(request -> request.url().toString()).containsOnly(requests.get(0).url().toString());
        assertThat(requests.get(0).url().toString()).contains("pageNo=3", "numOfRows=100");
    }

    @Test
    void persistentTimeoutStopsAfterThreeAttemptsWithOriginalCause() {
        var client = client(request -> Mono.never());
        ReflectionTestUtils.setField(client, "requestTimeoutSeconds", 1L);

        assertThatThrownBy(() -> client.fetchDrugPage(DrugCategory.WOUND_CARE, 0, 100))
                .hasRootCauseInstanceOf(TimeoutException.class);

        assertThat(requests).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 500, 502, 503, 504})
    void transientHttpFailuresCanRecover(int status) {
        AtomicInteger attempts = new AtomicInteger();
        var client = client(request -> Mono.just(attempts.incrementAndGet() == 1
                ? response(HttpStatus.valueOf(status), "upstream unavailable") : response(HttpStatus.OK, EMPTY_PAGE)));

        assertThat(client.fetchDrugPage(DrugCategory.ATHLETES_FOOT, 0, 100).items()).isEmpty();
        assertThat(requests).hasSize(2);
    }

    @Test
    void persistentServerErrorStopsAfterThreeAttemptsWithoutRetryExhaustedWrapper() {
        var client = client(request -> Mono.just(response(HttpStatus.SERVICE_UNAVAILABLE, "upstream unavailable")));

        assertThatThrownBy(() -> client.fetchDrugPage(DrugCategory.WOUND_CARE, 0, 100))
                .isInstanceOf(WebClientResponseException.ServiceUnavailable.class);

        assertThat(requests).hasSize(3);
    }

    @Test
    void connectionResetCanRecover() {
        AtomicInteger attempts = new AtomicInteger();
        var client = client(request -> attempts.incrementAndGet() == 1
                ? Mono.error(new WebClientRequestException(new java.net.SocketException("Connection reset"),
                        request.method(), request.url(), request.headers()))
                : Mono.just(response(HttpStatus.OK, EMPTY_PAGE)));

        assertThat(client.fetchDrugPage(DrugCategory.WOUND_CARE, 0, 100).totalCount()).isZero();
        assertThat(requests).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 422, 429, 501})
    void nonTransientHttpErrorsAreNotRetried(int status) {
        var client = client(request -> Mono.just(response(HttpStatus.valueOf(status), "request rejected")));

        assertThatThrownBy(() -> client.fetchDrugPage(DrugCategory.ATHLETES_FOOT, 0, 100))
                .isInstanceOf(WebClientResponseException.class);

        assertThat(requests).hasSize(1);
    }

    @Test
    void successfulHttpResponseWithInvalidServiceKeyIsNotRetried() {
        var client = client("{\"OpenAPI_ServiceResponse\":{\"cmmMsgHeader\":{\"errMsg\":\"SERVICE_KEY_IS_NOT_REGISTERED_ERROR\"}}}");

        assertThatThrownBy(() -> client.fetchDrugPage(DrugCategory.WOUND_CARE, 0, 100))
                .isInstanceOf(com.feetfit.server.apiPayload.exception.GeneralException.class);

        assertThat(requests).hasSize(1);
    }

    @Test
    void invalidJsonIsNotRetriedAsANetworkError() {
        var client = client("invalid json");

        assertThatThrownBy(() -> client.fetchDrugPage(DrugCategory.WOUND_CARE, 0, 100)).isInstanceOf(RuntimeException.class);

        assertThat(requests).hasSize(1);
    }

    @Test
    void invalidTlsCertificateIsNotRetried() {
        var client = client(request -> Mono.error(new WebClientRequestException(new SSLHandshakeException("invalid certificate"),
                request.method(), request.url(), request.headers())));

        assertThatThrownBy(() -> client.fetchDrugPage(DrugCategory.WOUND_CARE, 0, 100))
                .isInstanceOf(WebClientRequestException.class);

        assertThat(requests).hasSize(1);
    }

    @Test
    void exhaustedRecallRetriesRemainUnavailableRatherThanClear() {
        var client = client(request -> Mono.just(response(HttpStatus.SERVICE_UNAVAILABLE, "recall unavailable")));

        var recall = client.fetchRecallInfo("199400709");

        assertThat(recall.getStatus()).isEqualTo("unavailable");
        assertThat(recall.getRecords()).isEmpty();
        assertThat(requests).hasSize(3);
    }

    @Test
    void retriesCanBeDisabled() {
        var client = client(request -> Mono.just(response(HttpStatus.SERVICE_UNAVAILABLE, "upstream unavailable")));
        ReflectionTestUtils.setField(client, "maxRetries", 0);

        assertThatThrownBy(() -> client.fetchDrugPage(DrugCategory.WOUND_CARE, 0, 100))
                .isInstanceOf(WebClientResponseException.ServiceUnavailable.class);

        assertThat(requests).hasSize(1);
    }

    private DrugDataClient client(String body) {
        return client(request -> Mono.just(response(HttpStatus.OK, body)));
    }

    private DrugDataClient client(Function<ClientRequest, Mono<ClientResponse>> exchange) {
        WebClient webClient = WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            return exchange.apply(request);
        }).build();
        var client = new DrugDataClient(webClient, new ObjectMapper());
        ReflectionTestUtils.setField(client, "configuredServiceKey", "test%2Bkey%3D");
        ReflectionTestUtils.setField(client, "easyDrugEndpoint", "https://data.example/drugs");
        ReflectionTestUtils.setField(client, "permitEndpoint", "https://data.example/permits");
        ReflectionTestUtils.setField(client, "recallEndpoint", "https://data.example/recalls");
        ReflectionTestUtils.setField(client, "fallbackImageUrl", "https://bucket.s3.amazonaws.com/fallback.jpg");
        ReflectionTestUtils.setField(client, "retryBackoffMillis", 0L);
        return client;
    }

    private static ClientResponse response(HttpStatus status, String body) {
        return ClientResponse.create(status).header(HttpHeaders.CONTENT_TYPE, "application/json").body(body).build();
    }
}
