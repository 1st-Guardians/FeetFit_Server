package com.feetfit.server.service.ShoeService;

import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.*;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:recommendation-constraints;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE"
})
class ShoeRecommendationConstraintJpaIntegrationTest {

    @Autowired
    TestEntityManager entityManager;

    @Test
    void nullMeasurementSessionIsRejected() {
        Fixture fixture = fixture();
        ShoeRecommendation recommendation = recommendation(
                fixture.user(), fixture.shoe(), null, 80f);

        assertThatThrownBy(() -> entityManager.persistAndFlush(recommendation))
                .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class);
    }

    @Test
    void sameSessionAndShoeCannotHaveTwoRows() {
        Fixture fixture = fixture();
        entityManager.persistAndFlush(recommendation(
                fixture.user(), fixture.shoe(), fixture.firstSession(), 80f));

        assertThatThrownBy(() -> entityManager.persistAndFlush(recommendation(
                fixture.user(), fixture.shoe(), fixture.firstSession(), 90f)))
                .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class);
    }

    @Test
    void differentSessionsCanKeepRecommendationsForSameShoe() {
        Fixture fixture = fixture();
        ShoeRecommendation first = entityManager.persistAndFlush(recommendation(
                fixture.user(), fixture.shoe(), fixture.firstSession(), 80f));
        ShoeRecommendation second = entityManager.persistAndFlush(recommendation(
                fixture.user(), fixture.shoe(), fixture.secondSession(), 90f));

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void duplicateReasonTypeIsRejectedByDatabase() {
        Fixture fixture = fixture();
        ShoeRecommendation recommendation = entityManager.persistAndFlush(recommendation(
                fixture.user(), fixture.shoe(), fixture.firstSession(), 80f));
        entityManager.persistAndFlush(reason(recommendation, ReasonType.FOREFOOT));

        assertThatThrownBy(() -> entityManager.persistAndFlush(
                reason(recommendation, ReasonType.FOREFOOT)))
                .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class);
    }

    @Test
    void duplicateReasonReviewLinkIsRejectedByDatabase() {
        Fixture fixture = fixture();
        ShoeRecommendation recommendation = entityManager.persistAndFlush(recommendation(
                fixture.user(), fixture.shoe(), fixture.firstSession(), 80f));
        ShoeRecommendationReason reason = entityManager.persistAndFlush(
                reason(recommendation, ReasonType.FOREFOOT));
        ShoeReview review = entityManager.persistAndFlush(ShoeReview.builder()
                .shoe(fixture.shoe())
                .rating(5f)
                .reviewText("review")
                .sourceReviewId("review-1")
                .contentHash("hash-1")
                .source(ShoeReviewSource.MUSINSA)
                .collectedAt(LocalDateTime.now())
                .build());
        entityManager.persistAndFlush(ShoeRecommendationReasonReview.builder()
                .reason(reason).review(review).build());

        assertThatThrownBy(() -> entityManager.persistAndFlush(
                ShoeRecommendationReasonReview.builder().reason(reason).review(review).build()))
                .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class);
    }

    @Test
    void recommendationRunIsUniquePerMeasurementSession() {
        Fixture fixture = fixture();
        entityManager.persistAndFlush(run(fixture.firstSession()));

        assertThatThrownBy(() -> entityManager.persistAndFlush(run(fixture.firstSession())))
                .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class);
    }

    private Fixture fixture() {
        Device device = entityManager.persistAndFlush(Device.builder()
                .deviceName("device")
                .build());
        User user = entityManager.persistAndFlush(User.builder()
                .nickname("user")
                .socialType(SocialType.KAKAO)
                .socialId("social")
                .build());
        Shoe shoe = entityManager.persistAndFlush(Shoe.builder()
                .brandName("brand")
                .shoeName("shoe")
                .modelCode("model")
                .musinsaGoodsNo("goods")
                .musinsaUrl("https://example.com/shoe")
                .build());
        MeasurementSession first = entityManager.persistAndFlush(MeasurementSession.builder()
                .user(user)
                .device(device)
                .status(MeasurementStatus.COMPLETED)
                .measuredAt(LocalDateTime.now().minusMinutes(1))
                .build());
        MeasurementSession second = entityManager.persistAndFlush(MeasurementSession.builder()
                .user(user)
                .device(device)
                .status(MeasurementStatus.COMPLETED)
                .measuredAt(LocalDateTime.now())
                .build());
        return new Fixture(user, shoe, first, second);
    }

    private static ShoeRecommendation recommendation(
            User user, Shoe shoe, MeasurementSession session, float score) {
        return ShoeRecommendation.builder()
                .user(user)
                .shoe(shoe)
                .measurementSession(session)
                .fitScore(score)
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    private static ShoeRecommendationReason reason(
            ShoeRecommendation recommendation, ReasonType type) {
        return ShoeRecommendationReason.builder()
                .shoeRecommendation(recommendation)
                .reasonType(type)
                .title(type.name())
                .riskLevel(RiskLevel.LOW)
                .build();
    }

    private static ShoeRecommendationRun run(MeasurementSession session) {
        return ShoeRecommendationRun.builder()
                .measurementSession(session)
                .status(ShoeRecommendationRunStatus.PENDING)
                .expectedCount(338)
                .processedCount(0)
                .build();
    }

    private record Fixture(
            User user,
            Shoe shoe,
            MeasurementSession firstSession,
            MeasurementSession secondSession) {
    }
}
