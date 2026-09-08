package com.feetfit.server.service.ShoeService;

import com.feetfit.server.domain.Device;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.ShoeRecommendation;
import com.feetfit.server.domain.ShoeRecommendationReason;
import com.feetfit.server.domain.ShoeRecommendationReasonReview;
import com.feetfit.server.domain.ShoeReview;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.ReasonType;
import com.feetfit.server.domain.enums.RiskLevel;
import com.feetfit.server.domain.enums.ShoeReviewSource;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.repository.ShoeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:recommendation-visibility;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE"
})
class ShoeRecommendationVisibilityJpaIntegrationTest {

    private static final String REVIEW_UNAVAILABLE =
            "해당 영역과 관련된 실제 착용 리뷰가 부족하여 리뷰 기반 설명을 제공하기 어렵습니다.";

    @Autowired TestEntityManager entityManager;
    @Autowired ShoeRepository shoeRepository;

    @Test
    void fitScorePagesAndTop3ExcludeOnlyCurrentSessionRecommendationsWithoutEvidence() {
        Device device = entityManager.persistAndFlush(Device.builder()
                .deviceName("visibility-device")
                .build());
        User user = entityManager.persistAndFlush(User.builder()
                .nickname("visibility-user")
                .socialType(SocialType.KAKAO)
                .socialId("visibility-social")
                .build());
        MeasurementSession session = persistSession(user, device, "current");
        MeasurementSession otherSession = persistSession(user, device, "other");

        Shoe reviewlessHighScore = persistShoe("reviewless", 1);
        Shoe visibleHighScore = persistShoe("visible-high", 2);
        Shoe visibleMiddleScore = persistShoe("visible-middle", 3);
        Shoe visibleLowScore = persistShoe("visible-low", 4);

        persistRecommendation(user, session, reviewlessHighScore, 99f, null);
        persistRecommendation(user, session, visibleHighScore, 90f, ReasonType.FOREFOOT);
        persistRecommendation(user, session, visibleMiddleScore, 70f, ReasonType.HEEL);
        persistRecommendation(user, session, visibleLowScore, 50f, ReasonType.INSOLE);
        persistRecommendation(user, otherSession, reviewlessHighScore, 100f, ReasonType.FOREFOOT);
        entityManager.flush();
        entityManager.clear();

        Page<Shoe> firstPage = shoeRepository.findAllByFitScoreDesc(
                session.getId(), PageRequest.of(0, 2));
        Page<Shoe> secondPage = shoeRepository.findAllByFitScoreDesc(
                session.getId(), PageRequest.of(1, 2));
        List<Shoe> top3 = shoeRepository.findTop3ByFitScoreDesc(
                session.getId(), PageRequest.of(0, 3));
        Page<Shoe> otherSessionPage = shoeRepository.findAllByFitScoreDesc(
                otherSession.getId(), PageRequest.of(0, 10));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.getContent()).extracting(Shoe::getId)
                .containsExactly(visibleHighScore.getId(), visibleMiddleScore.getId());
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.getContent()).extracting(Shoe::getId)
                .containsExactly(visibleLowScore.getId());
        assertThat(top3).extracting(Shoe::getId)
                .containsExactly(
                        visibleHighScore.getId(),
                        visibleMiddleScore.getId(),
                        visibleLowScore.getId());
        assertThat(otherSessionPage.getContent()).extracting(Shoe::getId)
                .containsExactly(reviewlessHighScore.getId());
        assertThat(shoeRepository.findById(reviewlessHighScore.getId())).isPresent();
    }

    private MeasurementSession persistSession(User user, Device device, String suffix) {
        return entityManager.persistAndFlush(MeasurementSession.builder()
                .user(user)
                .device(device)
                .status(MeasurementStatus.COMPLETED)
                .measuredAt(LocalDateTime.now().plusSeconds("other".equals(suffix) ? 1 : 0))
                .build());
    }

    private Shoe persistShoe(String suffix, int sequence) {
        return entityManager.persistAndFlush(Shoe.builder()
                .brandName("brand-" + suffix)
                .shoeName("shoe-" + suffix)
                .modelCode("model-" + sequence)
                .musinsaGoodsNo("goods-" + sequence)
                .musinsaUrl("https://example.com/" + sequence)
                .build());
    }

    private void persistRecommendation(
            User user,
            MeasurementSession session,
            Shoe shoe,
            float fitScore,
            ReasonType evidenceReasonType) {
        ShoeRecommendation recommendation = entityManager.persistAndFlush(
                ShoeRecommendation.builder()
                        .user(user)
                        .shoe(shoe)
                        .measurementSession(session)
                        .fitScore(fitScore)
                        .pointSummary("point summary")
                        .analyzedAt(LocalDateTime.now())
                        .build());

        ShoeRecommendationReason evidenceReason = null;
        for (ReasonType reasonType : ReasonType.values()) {
            ShoeRecommendationReason reason = entityManager.persistAndFlush(
                    ShoeRecommendationReason.builder()
                            .shoeRecommendation(recommendation)
                            .reasonType(reasonType)
                            .title(reasonType.name())
                            .riskLevel(RiskLevel.MEDIUM)
                            .reviewSummary(REVIEW_UNAVAILABLE)
                            .build());
            if (reasonType == evidenceReasonType) {
                evidenceReason = reason;
            }
        }

        if (evidenceReason == null) {
            return;
        }
        ShoeReview review = entityManager.persistAndFlush(ShoeReview.builder()
                .shoe(shoe)
                .rating(5f)
                .reviewText("실제 착용 리뷰")
                .sourceReviewId("review-" + session.getId() + "-" + shoe.getId())
                .contentHash("hash-" + session.getId() + "-" + shoe.getId())
                .source(ShoeReviewSource.MUSINSA)
                .collectedAt(LocalDateTime.now())
                .build());
        entityManager.persistAndFlush(ShoeRecommendationReasonReview.builder()
                .reason(evidenceReason)
                .review(review)
                .build());
    }
}
