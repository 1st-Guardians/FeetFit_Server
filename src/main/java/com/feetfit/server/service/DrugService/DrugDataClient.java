package com.feetfit.server.service.DrugService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.GeneralException;
import com.feetfit.server.domain.enums.DrugCategory;
import com.feetfit.server.web.dto.drug.DrugResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrugDataClient {

    public record DrugPage(List<DrugResponseDTO.DrugItemDTO> items, long totalCount) {
    }

    private static final String RECALL_SOURCE_URL = "https://www.data.go.kr/data/15059114/openapi.do";
    private static final String DEFAULT_FALLBACK_IMAGE =
            "https://project5-42-oregon-feetfit-s3.s3.us-west-2.amazonaws.com/drug-images/9917badb-5647-43b7-8d0f-531bf0dcb7f0.png";
    private static final Pattern PERCENT_ENCODING_PATTERN = Pattern.compile("%[0-9a-fA-F]{2}");

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${data-go-kr.service-key:${DATA_GO_KR_SERVICE_KEY:}}")
    private String configuredServiceKey;

    @Value("${data-go-kr.drug.easy-info.endpoint:https://apis.data.go.kr/1471000/DrbEasyDrugInfoService/getDrbEasyDrugList}")
    private String easyDrugEndpoint;

    @Value("${data-go-kr.drug.permit.endpoint:https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService08/getDrugPrdtPrmsnDtlInq08}")
    private String permitEndpoint;

    @Value("${data-go-kr.drug.recall.endpoint:https://apis.data.go.kr/1471000/MdcinRtrvlSleStpgeInfoService05/getMdcinRtrvlSleStpgelList05}")
    private String recallEndpoint;

    @Value("${drug.image.fallback-url:" + DEFAULT_FALLBACK_IMAGE + "}")
    private String fallbackImageUrl;

    @Value("${data-go-kr.drug.request-timeout-seconds:10}")
    private long requestTimeoutSeconds = 10;

    private Map<String, String> officialDrugImages;

    public DrugPage fetchDrugPage(DrugCategory category, int page, int size) {
        String serviceKey = resolveEncodedServiceKey("의약품 데이터 연결을 준비 중이에요.");
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(easyDrugEndpoint)
                .queryParam("pageNo", page + 1)
                .queryParam("numOfRows", size)
                .queryParam("type", "json")
                .queryParam("efcyQesitm", category == DrugCategory.ATHLETES_FOOT ? "무좀" : "상처");
        JsonNode root = fetchPublicData(
                appendServiceKey(uriBuilder.build().encode().toUriString(), serviceKey), "easy-drug-list");
        validatePublicDataHeader(root);
        List<DrugResponseDTO.DrugItemDTO> products = items(root).stream()
                .map(item -> toOfficialDrugItem(item, category))
                .toList();
        return new DrugPage(products, root.path("body").path("totalCount").asLong(products.size()));
    }

    public DrugResponseDTO.PermitInfoDTO fetchPermitInfo(String itemSeq) {
        String serviceKey = resolveEncodedServiceKey("허가정보 데이터 연결을 준비 중이에요.");
        return fetchPermit(serviceKey, itemSeq);
    }

    public DrugResponseDTO.RecallInfoDTO fetchRecallInfo(String itemSeq) {
        String serviceKey = resolveEncodedServiceKey("회수정보 데이터 연결을 준비 중이에요.");
        return fetchRecall(serviceKey, itemSeq);
    }

    private DrugResponseDTO.DrugItemDTO toOfficialDrugItem(JsonNode item, DrugCategory category) {
        String itemSeq = plain(item.path("itemSeq").asText());
        return DrugResponseDTO.DrugItemDTO.builder()
                .id(itemSeq)
                .name(defaultText(plain(item.path("itemName").asText()), "제품명 미상"))
                .company(defaultText(plain(item.path("entpName").asText()), "제조사 정보 없음"))
                .image(resolveDrugImageUrl(loadOfficialDrugImages().get(itemSeq)))
                .efficacy(plain(item.path("efcyQesitm").asText()))
                .usage(plain(item.path("useMethodQesitm").asText()))
                .warning(plain(firstText(item, "atpnWarnQesitm", "atpnQesitm")))
                .interaction(plain(item.path("intrcQesitm").asText()))
                .sideEffect(plain(item.path("seQesitm").asText()))
                .storage(plain(item.path("depositMethodQesitm").asText()))
                .category(category.getId())
                .online(false)
                .build();
    }

    private String resolveDrugImageUrl(String imageValue) {
        if (!StringUtils.hasText(imageValue)) {
            return fallbackImageUrl;
        }

        String trimmed = imageValue.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("/")) {
            return trimmed;
        }
        return "/drug-images/" + trimmed;
    }

    private DrugResponseDTO.PermitInfoDTO fetchPermit(String serviceKey, String itemSeq) {
        String permitUrl = appendServiceKey(UriComponentsBuilder.fromUriString(permitEndpoint)
                .queryParam("pageNo", "1")
                .queryParam("numOfRows", "1")
                .queryParam("type", "json")
                .queryParam("item_seq", itemSeq)
                .build().encode().toUriString(), serviceKey);
        JsonNode root = fetchPublicData(permitUrl, "drug-permit");
        validatePublicDataHeader(root);
        JsonNode item = firstItem(root);
        if (item.isMissingNode()) {
            return null;
        }
        if (!plain(item.path("ITEM_SEQ").asText()).equals(itemSeq)) {
            throw new IllegalStateException("Permit response did not match itemSeq");
        }
        return toPermitInfo(item, itemSeq);
    }

    private DrugResponseDTO.PermitInfoDTO toPermitInfo(JsonNode item, String itemSeq) {
        String cancelName = plain(item.path("CANCEL_NAME").asText());
        boolean cancelled = StringUtils.hasText(plain(item.path("CANCEL_DATE").asText()))
                || (StringUtils.hasText(cancelName) && !"정상".equals(cancelName));

        return DrugResponseDTO.PermitInfoDTO.builder()
                .itemSeq(plain(item.path("ITEM_SEQ").asText()))
                .englishName(plain(item.path("ITEM_ENG_NAME").asText()))
                .classification(defaultText(plain(item.path("ETC_OTC_CODE").asText()), "분류 정보 없음"))
                .status(cancelled ? defaultText(cancelName, "허가 취소") : "허가 정상")
                .cancelled(cancelled)
                .permitDate(formatDate(item.path("ITEM_PERMIT_DATE").asText()))
                .permitType(plain(item.path("PERMIT_KIND_NAME").asText()))
                .appearance(plain(item.path("CHART").asText()))
                .mainIngredient(ingredient(firstText(item, "MAIN_ITEM_INGR", "MATERIAL_NAME")))
                .packUnit(plain(item.path("PACK_UNIT").asText()))
                .storage(plain(item.path("STORAGE_METHOD").asText()))
                .validTerm(plain(item.path("VALID_TERM").asText()))
                .atcCode(plain(item.path("ATC_CODE").asText()))
                .officialUrl("https://nedrug.mfds.go.kr/pbp/CCBBB01/getItemDetail?itemSeq=" + itemSeq)
                .build();
    }

    private DrugResponseDTO.RecallInfoDTO fetchRecall(String serviceKey, String itemSeq) {
        String checkedAt = Instant.now().toString();
        try {
            String recallUrl = appendServiceKey(UriComponentsBuilder.fromUriString(recallEndpoint)
                    .queryParam("pageNo", "1")
                    .queryParam("numOfRows", "10")
                    .queryParam("type", "json")
                    .queryParam("item_seq", itemSeq)
                    .build()
                    .encode()
                    .toUriString(), serviceKey);

            JsonNode root = fetchPublicData(recallUrl, "drug-recall");

            validatePublicDataHeader(root);
            List<DrugResponseDTO.RecallRecordDTO> records = items(root).stream()
                    .filter(item -> plain(item.path("ITEM_SEQ").asText()).equals(itemSeq))
                    .map(this::toRecallRecord)
                    .toList();

            long totalCount = root == null ? 0 : root.path("body").path("totalCount").asLong(0);
            if (totalCount > 0 && records.isEmpty()) {
                throw new IllegalStateException("Recall response did not match itemSeq");
            }

            return DrugResponseDTO.RecallInfoDTO.builder()
                    .status(records.isEmpty() ? "clear" : "alert")
                    .records(records)
                    .checkedAt(checkedAt)
                    .sourceUrl(RECALL_SOURCE_URL)
                    .build();
        } catch (Exception e) {
            log.error("Drug recall API request failed. itemSeq={}", itemSeq, e);
            return DrugResponseDTO.RecallInfoDTO.builder()
                    .status("unavailable")
                    .records(List.of())
                    .checkedAt(checkedAt)
                    .sourceUrl(RECALL_SOURCE_URL)
                    .build();
        }
    }

    private DrugResponseDTO.RecallRecordDTO toRecallRecord(JsonNode item) {
        return DrugResponseDTO.RecallRecordDTO.builder()
                .product(plain(item.path("PRDUCT").asText()))
                .company(plain(item.path("ENTRPS").asText()))
                .reason(plain(item.path("RTRVL_RESN").asText()))
                .mandatory("Y".equalsIgnoreCase(plain(item.path("ENFRC_YN").asText())))
                .approvalDate(formatDate(item.path("RTRVL_CMMND_DT").asText()))
                .commandDate(formatDate(item.path("RECALL_COMMAND_DATE").asText()))
                .build();
    }

    private List<JsonNode> items(JsonNode root) {
        JsonNode raw = root == null ? null : root.path("body").path("items");
        if (raw == null || raw.isMissingNode() || raw.isNull() || raw.isTextual()) {
            return List.of();
        }

        List<JsonNode> result = new ArrayList<>();
        if (raw.isArray()) {
            raw.forEach(node -> {
                JsonNode item = node.path("item");
                if (item.isArray()) {
                    item.forEach(result::add);
                } else if (!item.isMissingNode() && !item.isNull()) {
                    result.add(item);
                } else {
                    result.add(node);
                }
            });
            return result;
        }

        JsonNode item = raw.path("item");
        if (item.isArray()) {
            item.forEach(result::add);
        } else if (!item.isMissingNode() && !item.isNull()) {
            result.add(item);
        } else if (raw.isObject()) {
            result.add(raw);
        }
        return result;
    }

    private JsonNode firstItem(JsonNode root) {
        return items(root).stream()
                .findFirst()
                .orElseGet(() -> objectMapper.missingNode());
    }

    private void validatePublicDataHeader(JsonNode root) {
        String resultCode = root == null ? "" : root.path("header").path("resultCode").asText("");
        if (StringUtils.hasText(resultCode) && !"00".equals(resultCode)) {
            String message = root.path("header").path("resultMsg").asText("공공데이터 요청이 거절됐어요.");
            throw new GeneralException(ErrorStatus.DRUG_DATA_REQUEST_FAILED, message);
        }

        JsonNode openApiError = root == null ? null : root.path("OpenAPI_ServiceResponse").path("cmmMsgHeader");
        if (openApiError != null && !openApiError.isMissingNode()) {
            String message = defaultText(
                    plain(openApiError.path("returnAuthMsg").asText()),
                    plain(openApiError.path("errMsg").asText())
            );
            throw new GeneralException(ErrorStatus.DRUG_DATA_REQUEST_FAILED, message);
        }
    }

    private JsonNode fetchPublicData(String uri, String requestName) {
        try {
            return webClient.mutate()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                    .build().get()
                    .uri(URI.create(uri))
                    .header(HttpHeaders.USER_AGENT, "FeetFit_Server/1.0")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .block();
        } catch (WebClientResponseException e) {
            log.error(
                    "Public data request failed. requestName={}, status={}, responseBody={}, url={}",
                    requestName,
                    e.getStatusCode(),
                    truncate(e.getResponseBodyAsString()),
                    maskServiceKey(uri),
                    e
            );
            throw e;
        }
    }

    private String resolveEncodedServiceKey(String message) {
        if (!StringUtils.hasText(configuredServiceKey)) {
            throw new GeneralException(ErrorStatus.DRUG_DATA_SERVICE_UNAVAILABLE, message);
        }
        String serviceKey = configuredServiceKey.trim();
        try {
            if (PERCENT_ENCODING_PATTERN.matcher(serviceKey).find()) {
                serviceKey = URLDecoder.decode(serviceKey, StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Configured data.go.kr service key could not be URL-decoded. It will be encoded as-is.");
        }
        String encodedServiceKey = URLEncoder.encode(serviceKey, StandardCharsets.UTF_8);
        log.debug(
                "Resolved data.go.kr service key. encodedPlus={}, encodedSlash={}, encodedEquals={}",
                encodedServiceKey.contains("%2B"),
                encodedServiceKey.contains("%2F"),
                encodedServiceKey.contains("%3D")
        );
        return encodedServiceKey;
    }

    private String appendServiceKey(String uri, String encodedServiceKey) {
        String separator = uri.contains("?") ? "&" : "?";
        return uri + separator + "serviceKey=" + encodedServiceKey;
    }

    private String maskServiceKey(String uri) {
        return uri.replaceAll("(?i)(serviceKey=)[^&]+", "$1***");
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500) + "...";
    }

    private String plain(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if ("null".equalsIgnoreCase(value.trim())) {
            return "";
        }
        return value
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("<[^>]+>", "")
                .replace('\u00A0', ' ')
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String ingredient(String value) {
        return plain(value)
                .replaceAll("\\[[^]]+]", "")
                .replace("|", ", ")
                .replaceAll("\\s*,\\s*", ", ")
                .trim();
    }

    private String formatDate(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.length() != 8) {
            return plain(value);
        }
        return digits.substring(0, 4) + "." + digits.substring(4, 6) + "." + digits.substring(6, 8);
    }

    private String firstText(JsonNode node, String firstField, String secondField) {
        String first = node.path(firstField).asText("");
        return StringUtils.hasText(first) ? first : node.path(secondField).asText("");
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private Map<String, String> loadOfficialDrugImages() {
        if (officialDrugImages != null) {
            return officialDrugImages;
        }
        try {
            ClassPathResource resource = new ClassPathResource("static/drug-images/manifest.json");
            try (var input = resource.getInputStream()) {
                officialDrugImages = objectMapper.readValue(input, new TypeReference<>() {});
            }
        } catch (IOException e) {
            log.warn("Drug image manifest not found. Fallback images will be used.", e);
            officialDrugImages = Map.of();
        }
        return officialDrugImages;
    }

    public List<DrugResponseDTO.DrugItemDTO> getOnlineProducts(DrugCategory category) {
        return switch (category) {
            case BUNION_BAND -> bunionBandProducts();
            case WOUND_CARE -> woundCareOnlineProducts();
            default -> List.of();
        };
    }

    private List<DrugResponseDTO.DrugItemDTO> woundCareOnlineProducts() {
        List<DrugResponseDTO.DrugItemDTO> products = new ArrayList<>();
        products.addAll(woundDressingProducts());
        products.addAll(footCareProducts());
        return products;
    }

    private List<DrugResponseDTO.DrugItemDTO> bunionBandProducts() {
        return List.of(
                DrugResponseDTO.DrugItemDTO.builder()
                        .id("bunion-day")
                        .name("데일리 실리콘 교정밴드")
                        .company("낮 시간 착용형")
                        .efficacy("엄지발가락과 둘째 발가락 사이를 부드럽게 벌려 마찰을 줄이는 형태예요.")
                        .usage("처음에는 1~2시간부터 시작해 피부 상태를 확인하며 착용 시간을 늘리세요.")
                        .warning("통증·저림·피부 변색이 생기면 즉시 사용을 멈추고 진료를 받으세요.")
                        .interaction("당뇨병이나 말초혈관질환이 있다면 사용 전 의료진과 상담하세요.")
                        .sideEffect("사이즈가 맞지 않으면 압박감, 쓸림, 피부 자극이 생길 수 있어요.")
                        .storage("세척 후 완전히 말려 통풍이 잘 되는 곳에 보관하세요.")
                        .category("무지외반증 교정밴드")
                        .online(true)
                        .priceHint("온라인 가격 비교")
                        .detailUrl("https://search.shopping.naver.com/search/all?query=%EB%AC%B4%EC%A7%80%EC%99%B8%EB%B0%98%EC%A6%9D%20%EC%8B%A4%EB%A6%AC%EC%BD%98%20%EA%B5%90%EC%A0%95%EB%B0%B4%EB%93%9C")
                        .description("얇고 부드러운 실리콘 타입")
                        .checklist(List.of("좌우 구분 확인", "세척 가능한 소재", "신발 속 착용 여부"))
                        .build(),
                DrugResponseDTO.DrugItemDTO.builder()
                        .id("bunion-night")
                        .name("나이트 엄지 고정 밴드")
                        .company("휴식·수면 전 착용형")
                        .efficacy("벨크로로 당김 강도를 조절하는 고정형 보조용품이에요.")
                        .usage("무리하게 조이지 말고 가장 약한 강도부터 짧게 적응하세요.")
                        .warning("교정용품은 변형을 치료하거나 수술을 대체하지 않아요.")
                        .interaction("붓기나 염증이 심한 날에는 사용을 피하고 전문가에게 문의하세요.")
                        .sideEffect("과도한 압박은 저림과 통증을 유발할 수 있어요.")
                        .storage("벨크로를 닫아 먼지가 붙지 않도록 보관하세요.")
                        .category("무지외반증 교정밴드")
                        .online(true)
                        .priceHint("온라인 가격 비교")
                        .detailUrl("https://search.shopping.naver.com/search/all?query=%EB%AC%B4%EC%A7%80%EC%99%B8%EB%B0%98%EC%A6%9D%20%EB%82%98%EC%9D%B4%ED%8A%B8%20%EB%B0%B4%EB%93%9C")
                        .description("강도 조절이 가능한 고정형")
                        .checklist(List.of("당김 강도 조절", "피부 접촉면", "권장 착용 시간"))
                        .build()
        );
    }

    private List<DrugResponseDTO.DrugItemDTO> woundDressingProducts() {
        return List.of(
                DrugResponseDTO.DrugItemDTO.builder()
                        .id("hydrocolloid")
                        .name("하이드로콜로이드 드레싱")
                        .company("가벼운 상처·습윤 환경")
                        .efficacy("상처를 외부 오염으로부터 보호하고 촉촉한 회복 환경을 유지하는 용품이에요.")
                        .usage("상처보다 넉넉한 크기로 붙이고 가장자리가 들뜨거나 진물이 차면 교체하세요.")
                        .warning("감염이 의심되거나 깊고 벌어진 상처에는 임의로 사용하지 마세요.")
                        .interaction("소독약을 충분히 말리지 않고 붙이면 접착력이 떨어질 수 있어요.")
                        .sideEffect("접착 성분에 의해 피부가 붉어지거나 가려울 수 있어요.")
                        .storage("고온·다습한 곳과 직사광선을 피해 보관하세요.")
                        .category("상처 보호밴드·드레싱")
                        .online(true)
                        .priceHint("사이즈별 가격 비교")
                        .detailUrl("https://search.shopping.naver.com/search/all?query=%ED%95%98%EC%9D%B4%EB%93%9C%EB%A1%9C%EC%BD%9C%EB%A1%9C%EC%9D%B4%EB%93%9C%20%EB%93%9C%EB%A0%88%EC%8B%B1")
                        .description("방수·습윤 밴드 타입")
                        .checklist(List.of("상처보다 큰 사이즈", "방수 여부", "개별 포장"))
                        .build(),
                DrugResponseDTO.DrugItemDTO.builder()
                        .id("foam-dressing")
                        .name("흡수 폼 드레싱")
                        .company("진물이 있는 상처")
                        .efficacy("부드러운 폼이 진물을 흡수하고 외부 충격을 완화하는 드레싱이에요.")
                        .usage("제품별 흡수량과 교체 주기를 확인하고 오염되면 바로 교체하세요.")
                        .warning("출혈이 계속되거나 감염 징후가 있으면 의료기관을 방문하세요.")
                        .interaction("연고와 함께 사용할 수 있는지는 제품 설명을 확인하세요.")
                        .sideEffect("접착 테두리가 민감한 피부를 자극할 수 있어요.")
                        .storage("포장이 손상된 제품은 사용하지 마세요.")
                        .category("상처 보호밴드·드레싱")
                        .online(true)
                        .priceHint("두께·크기별 비교")
                        .detailUrl("https://search.shopping.naver.com/search/all?query=%ED%8F%BC%20%EB%93%9C%EB%A0%88%EC%8B%B1%20%EB%B0%B4%EB%93%9C")
                        .description("흡수력이 높은 쿠션형")
                        .checklist(List.of("흡수량", "접착·비접착형", "교체 주기"))
                        .build()
        );
    }

    private List<DrugResponseDTO.DrugItemDTO> footCareProducts() {
        return List.of(
                DrugResponseDTO.DrugItemDTO.builder()
                        .id("toe-cap")
                        .name("실리콘 발가락 보호캡")
                        .company("마찰·압박 보호")
                        .efficacy("신발 안에서 발가락 끝이나 굳은살 부위의 마찰을 줄이는 용품이에요.")
                        .usage("발가락 둘레에 맞는 크기를 고르고 장시간 착용 시 중간에 피부를 확인하세요.")
                        .warning("상처 위에 직접 사용하지 말고 혈액순환을 방해할 만큼 조이지 마세요.")
                        .interaction("무좀 증상이 있다면 공용 사용을 피하고 사용 후 세척·건조하세요.")
                        .sideEffect("땀이 차면 피부가 짓무르거나 미끄러질 수 있어요.")
                        .storage("중성세제로 세척한 뒤 그늘에서 완전히 말리세요.")
                        .category("발가락 보호패드·발 관리용품")
                        .online(true)
                        .priceHint("사이즈별 가격 비교")
                        .detailUrl("https://search.shopping.naver.com/search/all?query=%EC%8B%A4%EB%A6%AC%EC%BD%98%20%EB%B0%9C%EA%B0%80%EB%9D%BD%20%EB%B3%B4%ED%98%B8%EC%BA%A1")
                        .description("발가락 끝을 감싸는 소프트 타입")
                        .checklist(List.of("내경 사이즈", "통기 구멍", "세척 가능 여부"))
                        .build(),
                DrugResponseDTO.DrugItemDTO.builder()
                        .id("forefoot-pad")
                        .name("앞꿈치 쿠션 보호패드")
                        .company("발바닥 압력 분산")
                        .efficacy("오래 걷거나 서 있을 때 앞꿈치의 압력과 마찰을 덜어주는 용품이에요.")
                        .usage("양말이나 신발 안에서 밀리지 않는지 확인하고 좌우 균형 있게 착용하세요.")
                        .warning("통증이 지속되면 보호패드에만 의존하지 말고 원인을 확인하세요.")
                        .interaction("평소 신발 여유 공간을 확인해 과도한 압박을 피하세요.")
                        .sideEffect("두께가 맞지 않으면 오히려 발가락이나 발등이 눌릴 수 있어요.")
                        .storage("접착형은 보호필름을 붙여 보관하세요.")
                        .category("발가락 보호패드·발 관리용품")
                        .online(true)
                        .priceHint("소재·두께별 비교")
                        .detailUrl("https://search.shopping.naver.com/search/all?query=%EC%95%9E%EA%BF%88%EC%B9%98%20%EC%BF%A0%EC%85%98%20%EB%B3%B4%ED%98%B8%ED%8C%A8%EB%93%9C")
                        .description("앞꿈치 충격을 덜어주는 쿠션형")
                        .checklist(List.of("패드 두께", "미끄럼 방지", "세척 방식"))
                        .build()
        );
    }
}
