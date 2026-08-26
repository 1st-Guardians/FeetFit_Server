package com.feetfit.server.service.ShoeService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.GeneralException;
import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.ShoeImportAudit;
import com.feetfit.server.domain.ShoeLabMeasurement;
import com.feetfit.server.domain.ShoeLabMetric;
import com.feetfit.server.domain.ShoeReview;
import com.feetfit.server.domain.enums.ShoeImportMatchStatus;
import com.feetfit.server.domain.enums.ShoeImportOperation;
import com.feetfit.server.domain.enums.ShoeImportSource;
import com.feetfit.server.domain.enums.ShoeReviewSource;
import com.feetfit.server.repository.ShoeImportAuditRepository;
import com.feetfit.server.repository.ShoeLabMeasurementRepository;
import com.feetfit.server.repository.ShoeRepository;
import com.feetfit.server.repository.ShoeReviewRepository;
import com.feetfit.server.web.dto.shoe.ShoeIngestionRequestDTO;
import com.feetfit.server.web.dto.shoe.ShoeIngestionResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ShoeIngestionServiceImpl implements ShoeIngestionService {

    private static final String RUNREPEAT = "RUNREPEAT";

    private final ShoeRepository shoeRepository;
    private final ShoeReviewRepository shoeReviewRepository;
    private final ShoeLabMeasurementRepository shoeLabMeasurementRepository;
    private final ShoeImportAuditRepository shoeImportAuditRepository;
    private final ObjectMapper objectMapper;

    @Override
    public ShoeIngestionResponseDTO.ImportResult importMusinsa(
            ShoeIngestionRequestDTO.MusinsaImportRequest request) {
        requireSource(request.getSource(), ShoeImportSource.MUSINSA);

        List<ShoeIngestionResponseDTO.ImportItemResult> results = new ArrayList<>();
        int processed = 0;
        for (ShoeIngestionRequestDTO.MusinsaShoeItem item : request.getShoes()) {
            String rawPayload = serialize(item);
            List<Shoe> candidates = findMusinsaCandidates(item);

            boolean conflictingGoodsNo = candidates.stream()
                    .map(Shoe::getMusinsaGoodsNo)
                    .anyMatch(existingGoodsNo -> existingGoodsNo != null
                            && !item.getGoodsNo().trim().equals(existingGoodsNo));
            if (candidates.size() > 1 || conflictingGoodsNo) {
                List<Long> candidateIds = candidateIds(candidates);
                ShoeImportAudit audit = saveAudit(
                        ShoeImportSource.MUSINSA,
                        item.getGoodsNo(),
                        item.getMusinsaUrl(),
                        item.getBrandName(),
                        item.getShoeName(),
                        item.getModelCode(),
                        ShoeImportMatchStatus.AMBIGUOUS,
                        null,
                        candidateIds,
                        "goodsNo와 musinsaUrl이 서로 다른 신발을 가리킵니다.",
                        request.getCollectedAt(),
                        rawPayload);
                results.add(importResult(
                        item.getGoodsNo(), null, ShoeImportMatchStatus.AMBIGUOUS,
                        ShoeImportOperation.STAGED, candidateIds, audit.getId()));
                continue;
            }

            Shoe shoe;
            ShoeImportOperation operation;
            if (candidates.isEmpty()) {
                shoe = shoeRepository.save(Shoe.builder()
                        .brandName(item.getBrandName().trim())
                        .shoeName(item.getShoeName().trim())
                        .modelCode(item.getModelCode().trim())
                        .musinsaGoodsNo(item.getGoodsNo().trim())
                        .musinsaUrl(item.getMusinsaUrl().trim())
                        .price(item.getPrice())
                        .imageUrl(item.getImageUrl())
                        .overallRating(item.getOverallRating())
                        .reviewCount(item.getReviewCount())
                        .build());
                operation = ShoeImportOperation.CREATED;
            } else {
                shoe = candidates.get(0);
                shoe.updateCrawlerData(
                        item.getBrandName().trim(),
                        item.getShoeName().trim(),
                        item.getModelCode().trim(),
                        item.getGoodsNo().trim(),
                        item.getMusinsaUrl().trim(),
                        item.getPrice(),
                        item.getImageUrl(),
                        item.getOverallRating(),
                        item.getReviewCount());
                operation = ShoeImportOperation.UPDATED;
            }

            upsertReviews(shoe, item.getReviews());
            ShoeImportAudit audit = saveAudit(
                    ShoeImportSource.MUSINSA,
                    item.getGoodsNo(),
                    item.getMusinsaUrl(),
                    item.getBrandName(),
                    item.getShoeName(),
                    item.getModelCode(),
                    ShoeImportMatchStatus.MATCHED,
                    shoe,
                    List.of(shoe.getId()),
                    operation == ShoeImportOperation.CREATED ? "새 MUSINSA 신발을 생성했습니다." : "기존 MUSINSA 신발을 갱신했습니다.",
                    request.getCollectedAt(),
                    rawPayload);
            results.add(importResult(
                    item.getGoodsNo(), shoe.getId(), ShoeImportMatchStatus.MATCHED,
                    operation, List.of(shoe.getId()), audit.getId()));
            processed++;
        }

        return ShoeIngestionResponseDTO.ImportResult.builder()
                .requestedCount(request.getShoes().size())
                .processedCount(processed)
                .items(results)
                .build();
    }

    @Override
    public ShoeIngestionResponseDTO.ImportResult importRunRepeat(
            ShoeIngestionRequestDTO.RunRepeatImportRequest request) {
        return importRunRepeat(request, false);
    }

    @Override
    public ShoeIngestionResponseDTO.ImportResult importRunRepeatTargeted(
            ShoeIngestionRequestDTO.RunRepeatImportRequest request) {
        return importRunRepeat(request, true);
    }

    private ShoeIngestionResponseDTO.ImportResult importRunRepeat(
            ShoeIngestionRequestDTO.RunRepeatImportRequest request,
            boolean targeted) {
        requireSource(request.getSource(), ShoeImportSource.RUNREPEAT);
        requireRunRepeatShape(request, targeted);

        List<ShoeIngestionResponseDTO.ImportItemResult> results = new ArrayList<>();
        int processed = 0;
        for (ShoeIngestionRequestDTO.RunRepeatSnapshotItem item : request.getItems()) {
            String rawPayload = serialize(item);
            List<Shoe> candidates = targeted
                    ? findTargetedRunRepeatCandidates(item)
                    : findRunRepeatCandidates(item);
            if (candidates.size() != 1) {
                ShoeImportMatchStatus status = candidates.isEmpty()
                        ? ShoeImportMatchStatus.UNMATCHED
                        : ShoeImportMatchStatus.AMBIGUOUS;
                List<Long> candidateIds = candidateIds(candidates);
                ShoeImportAudit audit = saveAudit(
                        ShoeImportSource.RUNREPEAT,
                        item.getExternalKey(),
                        item.getSourceUrl(),
                        item.getBrandName(),
                        item.getShoeName(),
                        item.getModelCode(),
                        status,
                        null,
                        candidateIds,
                        status == ShoeImportMatchStatus.UNMATCHED
                                ? targeted
                                        ? "targetGoodsNo와 정확히 일치하는 MUSINSA SHOE가 없습니다."
                                        : "기존 SHOE와 정확히 일치하는 항목이 없습니다."
                                : "정확히 일치하는 SHOE가 둘 이상이어서 연결하지 않았습니다.",
                        item.getCapturedAt(),
                        rawPayload);
                results.add(importResult(
                        item.getExternalKey(), null, status, ShoeImportOperation.STAGED,
                        candidateIds, audit.getId()));
                continue;
            }

            Shoe shoe = candidates.get(0);
            String snapshotKey = hash(shoe.getId() + "|" + RUNREPEAT + "|"
                    + item.getSourceUrl().trim() + "|" + item.getCapturedAt());
            Optional<ShoeLabMeasurement> existing = shoeLabMeasurementRepository
                    .findBySnapshotKey(snapshotKey);
            ShoeLabMeasurement snapshot;
            ShoeImportOperation operation;
            if (existing.isPresent()) {
                snapshot = existing.get();
                snapshot.updateSnapshot(
                        item.getTestedSize(), item.getBrandName(), item.getShoeName(), item.getModelCode(),
                        item.getCapturedAt(), item.getParserVersion(), item.getInternalLengthMm(), item.getWidthMm(),
                        item.getToeboxWidthMm(), item.getToeboxHeightMm(), item.getInsoleThicknessMm(),
                        item.getHeelStackMm(), item.getForefootStackMm());
                operation = ShoeImportOperation.UPDATED;
            } else {
                snapshot = shoeLabMeasurementRepository.save(ShoeLabMeasurement.builder()
                        .shoe(shoe)
                        .source(RUNREPEAT)
                        .sourceUrl(item.getSourceUrl().trim())
                        .testedSize(item.getTestedSize())
                        .sourceBrandName(item.getBrandName())
                        .sourceShoeName(item.getShoeName())
                        .sourceModelCode(item.getModelCode())
                        .capturedAt(item.getCapturedAt())
                        .parserVersion(item.getParserVersion())
                        .snapshotKey(snapshotKey)
                        .internalLengthMm(item.getInternalLengthMm())
                        .widthMm(item.getWidthMm())
                        .toeboxWidthMm(item.getToeboxWidthMm())
                        .toeboxHeightMm(item.getToeboxHeightMm())
                        .insoleThicknessMm(item.getInsoleThicknessMm())
                        .heelStackMm(item.getHeelStackMm())
                        .forefootStackMm(item.getForefootStackMm())
                        .build());
                operation = ShoeImportOperation.CREATED;
            }

            snapshot.replaceRawMetrics(item.getRawMetrics().stream()
                    .map(metric -> toMetric(snapshot, metric))
                    .toList());

            ShoeImportAudit audit = saveAudit(
                    ShoeImportSource.RUNREPEAT,
                    item.getExternalKey(),
                    item.getSourceUrl(),
                    item.getBrandName(),
                    item.getShoeName(),
                    item.getModelCode(),
                    ShoeImportMatchStatus.MATCHED,
                    shoe,
                    List.of(shoe.getId()),
                    operation == ShoeImportOperation.CREATED ? "RunRepeat snapshot을 생성했습니다." : "RunRepeat snapshot을 갱신했습니다.",
                    item.getCapturedAt(),
                    rawPayload);
            results.add(importResult(
                    item.getExternalKey(), shoe.getId(), ShoeImportMatchStatus.MATCHED,
                    operation, List.of(shoe.getId()), audit.getId()));
            processed++;
        }

        return ShoeIngestionResponseDTO.ImportResult.builder()
                .requestedCount(request.getItems().size())
                .processedCount(processed)
                .items(results)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ShoeIngestionResponseDTO.AuditPageResult getImportAudits(
            ShoeImportSource source,
            ShoeImportMatchStatus matchStatus,
            int page,
            int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<ShoeImportAudit> audits;
        if (source != null && matchStatus != null) {
            audits = shoeImportAuditRepository.findBySourceAndMatchStatus(source, matchStatus, pageable);
        } else if (source != null) {
            audits = shoeImportAuditRepository.findBySource(source, pageable);
        } else if (matchStatus != null) {
            audits = shoeImportAuditRepository.findByMatchStatus(matchStatus, pageable);
        } else {
            audits = shoeImportAuditRepository.findAll(pageable);
        }

        return ShoeIngestionResponseDTO.AuditPageResult.from(audits.map(this::toAuditItem));
    }

    private List<Shoe> findMusinsaCandidates(ShoeIngestionRequestDTO.MusinsaShoeItem item) {
        Map<Long, Shoe> unique = new LinkedHashMap<>();
        String goodsNo = item.getGoodsNo().trim();
        shoeRepository.findByMusinsaGoodsNo(goodsNo)
                // Keep matching exact even with a case-insensitive database collation.
                .filter(shoe -> goodsNo.equals(shoe.getMusinsaGoodsNo()))
                .ifPresent(shoe -> unique.put(shoe.getId(), shoe));
        shoeRepository.findByMusinsaUrl(item.getMusinsaUrl().trim())
                .stream()
                .filter(shoe -> item.getMusinsaUrl().trim().equals(shoe.getMusinsaUrl()))
                .forEach(shoe -> unique.put(shoe.getId(), shoe));

        // modelCode/styleNo is not a product identity: MUSINSA legitimately has
        // multiple goodsNo values for colour SKUs that share it. Use modelCode
        // only to adopt one legacy row that predates musinsaGoodsNo, never to
        // merge or reject two fully identified MUSINSA products.
        List<Shoe> legacyModelMatches = shoeRepository
                .findByModelCodeIgnoreCase(item.getModelCode().trim()).stream()
                .filter(shoe -> shoe.getMusinsaGoodsNo() == null)
                .toList();
        if (unique.isEmpty() && legacyModelMatches.size() == 1) {
            Shoe legacy = legacyModelMatches.get(0);
            unique.put(legacy.getId(), legacy);
        } else if (unique.isEmpty() && legacyModelMatches.size() > 1) {
            legacyModelMatches.forEach(shoe -> unique.put(shoe.getId(), shoe));
        }
        return unique.values().stream().sorted(Comparator.comparing(Shoe::getId)).toList();
    }

    private List<Shoe> findRunRepeatCandidates(ShoeIngestionRequestDTO.RunRepeatSnapshotItem item) {
        if (item.getModelCode() != null && !item.getModelCode().isBlank()) {
            return shoeRepository.findByModelCodeIgnoreCase(item.getModelCode().trim()).stream()
                    .sorted(Comparator.comparing(Shoe::getId))
                    .toList();
        }
        String normalizedSourceName = normalizeProductIdentity(item.getShoeName());
        return shoeRepository.findByBrandNameIgnoreCase(item.getBrandName().trim()).stream()
                .filter(shoe -> normalizeProductIdentity(shoe.getShoeName())
                        .equals(normalizedSourceName))
                .sorted(Comparator.comparing(Shoe::getId))
                .toList();
    }

    private List<Shoe> findTargetedRunRepeatCandidates(
            ShoeIngestionRequestDTO.RunRepeatSnapshotItem item) {
        String targetGoodsNo = item.getTargetGoodsNo().trim();
        return shoeRepository.findByMusinsaGoodsNo(targetGoodsNo).stream()
                // Keep matching exact even when the backing database uses a
                // case-insensitive collation.
                .filter(shoe -> targetGoodsNo.equals(shoe.getMusinsaGoodsNo()))
                .toList();
    }

    private void requireRunRepeatShape(
            ShoeIngestionRequestDTO.RunRepeatImportRequest request,
            boolean targeted) {
        for (ShoeIngestionRequestDTO.RunRepeatSnapshotItem item : request.getItems()) {
            String targetGoodsNo = item.getTargetGoodsNo();
            if (!targeted && targetGoodsNo != null) {
                throw new GeneralException(
                        ErrorStatus._BAD_REQUEST,
                        "legacy RunRepeat import item에는 targetGoodsNo를 보낼 수 없습니다.");
            }
            if (!targeted) {
                continue;
            }

            String trimmedTargetGoodsNo = trimToNull(targetGoodsNo);
            if (trimmedTargetGoodsNo == null) {
                throw new GeneralException(
                        ErrorStatus._BAD_REQUEST,
                        "targeted RunRepeat import의 모든 item에는 targetGoodsNo가 필요합니다.");
            }
            if (trimToNull(item.getExternalKey()) == null) {
                throw new GeneralException(
                        ErrorStatus._BAD_REQUEST,
                        "targeted RunRepeat import의 모든 item에는 externalKey가 필요합니다.");
            }
            if (!trimmedTargetGoodsNo.equals(item.getExternalKey())) {
                throw new GeneralException(
                        ErrorStatus._BAD_REQUEST,
                        "targeted RunRepeat import의 externalKey는 trim(targetGoodsNo)와 정확히 같아야 합니다.");
            }
        }
    }

    /**
     * Conservative exact-name normalization for cross-source matching.
     *
     * This intentionally performs no token deletion, transliteration, edit-distance,
     * or synonym expansion: Unicode compatibility forms, case, and whitespace are
     * normalized, then equality must still be exact and unique.
     */
    private String normalizeProductIdentity(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private void upsertReviews(Shoe shoe, List<ShoeIngestionRequestDTO.MusinsaReviewItem> reviews) {
        for (ShoeIngestionRequestDTO.MusinsaReviewItem item : reviews) {
            String sourceReviewId = trimToNull(item.getSourceReviewId());
            String contentHash = sourceReviewId == null
                    ? hash(shoe.getId() + "|" + item.getRating() + "|" + normalizeText(item.getReviewText()))
                    : null;
            Optional<ShoeReview> existing = sourceReviewId != null
                    ? shoeReviewRepository.findByShoeIdAndSourceAndSourceReviewId(
                            shoe.getId(), ShoeReviewSource.MUSINSA, sourceReviewId)
                    : shoeReviewRepository.findByShoeIdAndSourceAndContentHash(
                            shoe.getId(), ShoeReviewSource.MUSINSA, contentHash);
            if (existing.isEmpty()) {
                // Legacy reviews predate source ids/content hashes. Exact text fallback is used
                // only for a single unidentified row, so two real reviews with the same text
                // can never overwrite each other's stable sourceReviewId.
                List<ShoeReview> legacyCandidates = shoeReviewRepository
                        .findByShoeIdAndSourceAndSourceReviewIdIsNullAndContentHashIsNullAndReviewTextAndRating(
                                shoe.getId(), ShoeReviewSource.MUSINSA,
                                item.getReviewText(), item.getRating());
                if (legacyCandidates.size() == 1) {
                    existing = Optional.of(legacyCandidates.get(0));
                }
            }

            if (existing.isPresent()) {
                existing.get().updateCrawledReview(
                        item.getRating(), item.getReviewText(), sourceReviewId, contentHash, item.getCollectedAt());
            } else {
                shoeReviewRepository.save(ShoeReview.builder()
                        .shoe(shoe)
                        .rating(item.getRating())
                        .reviewText(item.getReviewText())
                        .sourceReviewId(sourceReviewId)
                        .contentHash(contentHash)
                        .source(ShoeReviewSource.MUSINSA)
                        .collectedAt(item.getCollectedAt())
                        .build());
            }
        }
    }

    private ShoeLabMetric toMetric(
            ShoeLabMeasurement snapshot,
            ShoeIngestionRequestDTO.RawMetricItem metric) {
        return ShoeLabMetric.builder()
                .labMeasurement(snapshot)
                .canonicalCharacteristic(metric.getCanonicalCharacteristic())
                .sourceMetricName(metric.getSourceMetricName())
                .value(metric.getValue())
                .averageValue(metric.getAverageValue())
                .sourceMinValue(metric.getSourceMinValue())
                .sourceMaxValue(metric.getSourceMaxValue())
                .unit(metric.getUnit())
                .testedSize(metric.getTestedSize())
                .methodName(metric.getMethodName())
                .methodVersion(metric.getMethodVersion())
                .location(metric.getLocation())
                .variant(metric.getVariant())
                .comparisonSampleCount(metric.getComparisonSampleCount())
                .comparisonCohort(metric.getComparisonCohort())
                .rawValueText(metric.getRawValueText())
                .build();
    }

    private ShoeImportAudit saveAudit(
            ShoeImportSource source,
            String externalKey,
            String sourceUrl,
            String brandName,
            String shoeName,
            String modelCode,
            ShoeImportMatchStatus status,
            Shoe matchedShoe,
            List<Long> candidateIds,
            String detail,
            java.time.LocalDateTime capturedAt,
            String rawPayload) {
        return shoeImportAuditRepository.save(ShoeImportAudit.builder()
                .source(source)
                .externalKey(trimToNull(externalKey))
                .sourceUrl(trimToNull(sourceUrl))
                .sourceBrandName(brandName)
                .sourceShoeName(shoeName)
                .sourceModelCode(trimToNull(modelCode))
                .matchStatus(status)
                .matchedShoe(matchedShoe)
                .candidateShoeIds(candidateIds)
                .detail(detail)
                .payloadHash(hash(rawPayload))
                .rawPayload(rawPayload)
                .capturedAt(capturedAt)
                .build());
    }

    private ShoeIngestionResponseDTO.ImportItemResult importResult(
            String externalKey,
            Long shoeId,
            ShoeImportMatchStatus status,
            ShoeImportOperation operation,
            List<Long> candidateIds,
            Long auditId) {
        return ShoeIngestionResponseDTO.ImportItemResult.builder()
                .externalKey(externalKey)
                .shoeId(shoeId)
                .matchStatus(status)
                .operation(operation)
                .candidateShoeIds(candidateIds)
                .auditId(auditId)
                .build();
    }

    private ShoeIngestionResponseDTO.AuditItem toAuditItem(ShoeImportAudit audit) {
        return ShoeIngestionResponseDTO.AuditItem.builder()
                .auditId(audit.getId())
                .source(audit.getSource())
                .externalKey(audit.getExternalKey())
                .sourceUrl(audit.getSourceUrl())
                .sourceBrandName(audit.getSourceBrandName())
                .sourceShoeName(audit.getSourceShoeName())
                .sourceModelCode(audit.getSourceModelCode())
                .matchStatus(audit.getMatchStatus())
                .matchedShoeId(audit.getMatchedShoe() == null ? null : audit.getMatchedShoe().getId())
                .candidateShoeIds(audit.getCandidateShoeIds())
                .detail(audit.getDetail())
                .capturedAt(audit.getCapturedAt())
                .createdAt(audit.getCreatedAt())
                .build();
    }

    private List<Long> candidateIds(List<Shoe> candidates) {
        return candidates.stream().map(Shoe::getId).sorted().toList();
    }

    private void requireSource(ShoeImportSource actual, ShoeImportSource expected) {
        if (actual != expected) {
            throw new GeneralException(
                    ErrorStatus._BAD_REQUEST,
                    "source는 " + expected + " 이어야 합니다.");
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "import payload를 직렬화할 수 없습니다.");
        }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String normalizeText(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
