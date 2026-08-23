package com.feetfit.server.service.ShoeService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.ShoeImportAudit;
import com.feetfit.server.domain.enums.ShoeImportMatchStatus;
import com.feetfit.server.domain.enums.ShoeImportSource;
import com.feetfit.server.repository.ShoeImportAuditRepository;
import com.feetfit.server.repository.ShoeLabMeasurementRepository;
import com.feetfit.server.repository.ShoeRepository;
import com.feetfit.server.repository.ShoeReviewRepository;
import com.feetfit.server.web.dto.shoe.ShoeIngestionRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoeIngestionServiceImplTest {

    @Mock ShoeRepository shoeRepository;
    @Mock ShoeReviewRepository shoeReviewRepository;
    @Mock ShoeLabMeasurementRepository shoeLabMeasurementRepository;
    @Mock ShoeImportAuditRepository shoeImportAuditRepository;

    private ShoeIngestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShoeIngestionServiceImpl(
                shoeRepository,
                shoeReviewRepository,
                shoeLabMeasurementRepository,
                shoeImportAuditRepository,
                new ObjectMapper().findAndRegisterModules());
        when(shoeImportAuditRepository.save(any(ShoeImportAudit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void preservesTwoDistinctSourceReviewIdsEvenWhenTextIsIdentical() {
        Shoe shoe = shoe(10L);
        when(shoeRepository.findByMusinsaGoodsNo("100")) .thenReturn(Optional.of(shoe));
        when(shoeRepository.findByMusinsaUrl("https://musinsa.example/100"))
                .thenReturn(List.of(shoe));
        when(shoeRepository.findByModelCodeIgnoreCase("MODEL-1")).thenReturn(List.of(shoe));
        when(shoeReviewRepository.findByShoeIdAndSourceAndSourceReviewId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(shoeReviewRepository
                .findByShoeIdAndSourceAndSourceReviewIdIsNullAndContentHashIsNullAndReviewTextAndRating(
                        any(), any(), any(), any()))
                .thenReturn(List.of());

        service.importMusinsa(musinsaRequest(List.of(
                review("review-1", "same text"),
                review("review-2", "same text"))));

        verify(shoeReviewRepository, times(2)).save(any());
    }

    @Test
    void stagesUnmatchedRunRepeatWithoutCreatingMeasurement() {
        when(shoeRepository.findByModelCodeIgnoreCase("UNKNOWN")).thenReturn(List.of());
        ShoeIngestionRequestDTO.RunRepeatSnapshotItem item =
                ShoeIngestionRequestDTO.RunRepeatSnapshotItem.builder()
                        .externalKey("rr-1")
                        .brandName("Brand")
                        .shoeName("Unknown")
                        .modelCode("UNKNOWN")
                        .sourceUrl("https://runrepeat.example/unknown")
                        .capturedAt(LocalDateTime.of(2026, 8, 23, 12, 0))
                        .parserVersion("1.0")
                        .rawMetrics(List.of())
                        .build();

        var result = service.importRunRepeat(
                ShoeIngestionRequestDTO.RunRepeatImportRequest.builder()
                        .source(ShoeImportSource.RUNREPEAT)
                        .items(List.of(item))
                        .build());

        assertThat(result.getItems()).singleElement()
                .extracting("matchStatus")
                .isEqualTo(ShoeImportMatchStatus.UNMATCHED);
        verify(shoeLabMeasurementRepository, never()).save(any());
        verify(shoeImportAuditRepository).save(any(ShoeImportAudit.class));
    }

    private ShoeIngestionRequestDTO.MusinsaImportRequest musinsaRequest(
            List<ShoeIngestionRequestDTO.MusinsaReviewItem> reviews) {
        return ShoeIngestionRequestDTO.MusinsaImportRequest.builder()
                .source(ShoeImportSource.MUSINSA)
                .collectedAt(LocalDateTime.of(2026, 8, 23, 12, 0))
                .shoes(List.of(ShoeIngestionRequestDTO.MusinsaShoeItem.builder()
                        .goodsNo("100")
                        .brandName("Brand")
                        .shoeName("Shoe")
                        .modelCode("MODEL-1")
                        .musinsaUrl("https://musinsa.example/100")
                        .reviewCount(reviews.size())
                        .reviews(reviews)
                        .build()))
                .build();
    }

    private ShoeIngestionRequestDTO.MusinsaReviewItem review(String sourceReviewId, String text) {
        return ShoeIngestionRequestDTO.MusinsaReviewItem.builder()
                .sourceReviewId(sourceReviewId)
                .rating(4.5f)
                .reviewText(text)
                .collectedAt(LocalDateTime.of(2026, 8, 23, 12, 0))
                .build();
    }

    private Shoe shoe(Long id) {
        return Shoe.builder()
                .id(id)
                .brandName("Brand")
                .shoeName("Shoe")
                .modelCode("MODEL-1")
                .musinsaGoodsNo("100")
                .musinsaUrl("https://musinsa.example/100")
                .clickCount(0)
                .reviewCount(0)
                .build();
    }
}
