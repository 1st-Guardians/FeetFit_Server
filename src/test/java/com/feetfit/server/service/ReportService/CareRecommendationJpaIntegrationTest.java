package com.feetfit.server.service.ReportService;

import com.feetfit.server.domain.Device;
import com.feetfit.server.domain.HealthArticle;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.UserFootCareTodo;
import com.feetfit.server.domain.UserFootCareTodoAssignment;
import com.feetfit.server.domain.UserHealthArticle;
import com.feetfit.server.domain.enums.HealthType;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.repository.HealthArticleRepository;
import com.feetfit.server.repository.UserFootCareTodoAssignmentRepository;
import com.feetfit.server.repository.UserFootCareTodoRepository;
import com.feetfit.server.repository.UserHealthArticleRepository;
import com.feetfit.server.service.FootCareTodoService.FootCareTodoService;
import com.feetfit.server.service.FootCareTodoService.FootCareTodoServiceImpl;
import com.feetfit.server.service.HealthArticleService.HealthArticleService;
import com.feetfit.server.service.HealthArticleService.HealthArticleServiceImpl;
import com.feetfit.server.service.ImageService.ImageUploadService;
import com.feetfit.server.service.MeasurementService.MeasurementCompletionService;
import com.feetfit.server.service.MeasurementService.MeasurementSocketService;
import com.feetfit.server.web.dto.footcare.FootCareTodoRequestDTO;
import com.feetfit.server.web.dto.report.ReportRequestDTO;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import({ReportCommandServiceImpl.class, FootCareTodoServiceImpl.class, HealthArticleServiceImpl.class})
class CareRecommendationJpaIntegrationTest {

    private static final List<MetricType> METRICS = List.of(
            MetricType.PRESSURE_BALANCE, MetricType.HALLUX_VALGUS,
            MetricType.ATHLETES_FOOT, MetricType.SKIN_IRRITATION, MetricType.FOOT_ENVIRONMENT);
    private static final LocalDateTime PREVIOUS_DAY = LocalDateTime.of(2026, 1, 10, 10, 0);

    @Autowired private TestEntityManager em;
    @Autowired private ReportCommandService reportService;
    @Autowired private FootCareTodoService todoService;
    @Autowired private HealthArticleService articleService;
    @Autowired private UserFootCareTodoAssignmentRepository assignments;
    @Autowired private UserHealthArticleRepository articleLinks;
    @SpyBean private UserFootCareTodoRepository todoCatalog;
    @SpyBean private HealthArticleRepository articleCatalog;
    @MockBean private ImageUploadService imageUploadService;
    @MockBean private MeasurementCompletionService completionService;
    @MockBean private MeasurementSocketService socketService;

    private User user;
    private Device device;

    @BeforeEach
    void setUp() {
        user = user("care-user");
        device = em.persist(Device.builder().deviceName("care-device").build());
    }

    @Test
    void withoutTodaysMeasurement_retainsLatestLegacyTodosAndCompletionAndNews() {
        UserFootCareTodo obsolete = todo(HealthType.POSTURE, 0);
        UserFootCareTodo current = todo(HealthType.POSTURE, 1);
        assignment(user, obsolete, null, PREVIOUS_DAY.minusDays(1), false);
        assignment(user, current, null, PREVIOUS_DAY, true);
        HealthArticle news = article(HealthType.POSTURE, 0);
        link(user, news);
        User other = user("other-user");
        assignment(other, obsolete, null, PREVIOUS_DAY.plusDays(1), false);

        var response = todoService.getMyFootCareTodos(user.getId());

        assertThat(response.getHasTodayTodos()).isTrue();
        assertThat(response.getTodos()).singleElement().satisfies(item -> {
            assertThat(item.getTodoId()).isEqualTo(current.getId());
            assertThat(item.getTodoDate()).isEqualTo(PREVIOUS_DAY);
            assertThat(item.getIsCompleted()).isTrue();
            assertThat(item.getCompletedAt()).isNotNull();
        });
        assertThat(articleService.getMyHealthArticles(user.getId()).getArticles())
                .singleElement().satisfies(item -> assertThat(item.getArticleId()).isEqualTo(news.getId()));

        var request = new FootCareTodoRequestDTO.UpdateCompletionRequestDTO();
        ReflectionTestUtils.setField(request, "isCompleted", false);
        assertThat(todoService.updateCompletion(user.getId(), current.getId(), request).getIsCompleted()).isFalse();
        assertThat(todoService.getMyFootCareTodos(user.getId()).getTodos().get(0).getIsCompleted()).isFalse();
    }

    @Test
    void sessionRecommendationSpanningMidnight_returnsWholeBatchNotJustLastDay() {
        UserFootCareTodo first = todo(HealthType.POSTURE, 0);
        UserFootCareTodo second = todo(HealthType.POSTURE, 1);
        assignment(user, first, 100L, PREVIOUS_DAY.withHour(23), true);
        assignment(user, second, 100L, PREVIOUS_DAY.plusDays(1).withHour(0), false);

        assertThat(todoService.getMyFootCareTodos(user.getId()).getTodos())
                .extracting(item -> item.getTodoId()).containsExactly(first.getId(), second.getId());
    }

    @Test
    void noPreviousRecommendations_returnsEmptyLists() {
        assertThat(todoService.getMyFootCareTodos(user.getId()).getHasTodayTodos()).isFalse();
        assertThat(todoService.getMyFootCareTodos(user.getId()).getTodos()).isEmpty();
        assertThat(articleService.getMyHealthArticles(user.getId()).getArticles()).isEmpty();
    }

    @Test
    void newAnalysis_updatesOnlyAfterAllMetricsAndPreservesOtherUsersAndCatalogs() {
        catalog(HealthType.POSTURE);
        catalog(HealthType.SKIN_IRRITATION);
        MeasurementSession previous = session(user, PREVIOUS_DAY);
        complete(previous, MetricType.PRESSURE_BALANCE);
        List<Long> previousTodoIds = assignmentIds();
        List<Long> previousNewsIds = articleLinkIds();
        markCurrentTodoComplete();

        User other = user("other-user");
        complete(session(other, PREVIOUS_DAY), MetricType.PRESSURE_BALANCE);
        List<Long> otherTodoIds = assignments.findLatestAssignmentsByUserId(other.getId()).stream()
                .map(UserFootCareTodoAssignment::getId).toList();
        List<Long> otherNewsIds = articleLinks.findAllByUserIdWithArticle(other.getId()).stream()
                .map(UserHealthArticle::getId).toList();
        MeasurementSession current = session(user, PREVIOUS_DAY.plusDays(1));
        for (MetricType metric : METRICS.subList(0, 4)) {
            assertThat(save(current, metric, metric == MetricType.SKIN_IRRITATION ? 0f : 100f)
                    .isAllMetricsComplete()).isFalse();
        }
        assertThat(assignmentIds()).isEqualTo(previousTodoIds);
        assertThat(articleLinkIds()).isEqualTo(previousNewsIds);
        assertThat(todoService.getMyFootCareTodos(user.getId()).getTodos().get(0).getIsCompleted()).isTrue();

        var result = save(current, MetricType.FOOT_ENVIRONMENT, 100f);

        assertThat(result.isAllMetricsComplete()).isTrue();
        assertThat(result.getMatchedTodoCount()).isEqualTo(3);
        assertThat(result.getMatchedArticleCount()).isEqualTo(4);
        assertThat(assignments.findLatestAssignmentsByUserId(user.getId())).hasSize(3).allSatisfy(a -> {
            assertThat(a.getFootCareTodo().getHealthType()).isEqualTo(HealthType.SKIN_IRRITATION);
            assertThat(a.getSourceMeasurementSessionId()).isEqualTo(current.getId());
            assertThat(a.getIsCompleted()).isFalse();
        });
        assertThat(articleService.getMyHealthArticles(user.getId()).getArticles()).hasSize(4)
                .allSatisfy(a -> assertThat(a.getHealthType()).isEqualTo("SKIN_IRRITATION"));
        assertThat(assignments.findAllById(previousTodoIds)).isEmpty();
        assertThat(articleLinks.findAllById(previousNewsIds)).isEmpty();
        assertThat(assignments.findAllById(otherTodoIds)).hasSize(3);
        assertThat(articleLinks.findAllById(otherNewsIds)).hasSize(4);
        assertThat(todoCatalog.count()).isEqualTo(6);
        assertThat(articleCatalog.count()).isEqualTo(8);
    }

    @Test
    void repeatedAnalysisCallback_doesNotResetCompletionOrReplaceNews() {
        catalog(HealthType.POSTURE);
        MeasurementSession session = session(user, PREVIOUS_DAY);
        complete(session, MetricType.PRESSURE_BALANCE);
        markCurrentTodoComplete();
        List<Long> todoIds = assignmentIds();
        List<Long> newsIds = articleLinkIds();
        var completedAt = todoService.getMyFootCareTodos(user.getId()).getTodos().get(0).getCompletedAt();

        var response = save(session, MetricType.FOOT_ENVIRONMENT, 100f);

        assertThat(response.isAllMetricsComplete()).isTrue();
        assertThat(response.getMatchedTodoCount()).isEqualTo(3);
        assertThat(response.getMatchedArticleCount()).isEqualTo(4);
        assertThat(assignmentIds()).isEqualTo(todoIds);
        assertThat(articleLinkIds()).isEqualTo(newsIds);
        assertThat(todoService.getMyFootCareTodos(user.getId()).getTodos().get(0).getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void olderSessionFinishingLate_cannotOverwriteNewerRecommendations() {
        catalog(HealthType.POSTURE);
        catalog(HealthType.SKIN_IRRITATION);
        MeasurementSession old = session(user, PREVIOUS_DAY);
        MeasurementSession recent = session(user, PREVIOUS_DAY.plusDays(1));
        complete(recent, MetricType.SKIN_IRRITATION);
        markCurrentTodoComplete();
        List<Long> todoIds = assignmentIds();
        List<Long> newsIds = articleLinkIds();

        complete(old, MetricType.PRESSURE_BALANCE);

        assertThat(assignmentIds()).isEqualTo(todoIds);
        assertThat(articleLinkIds()).isEqualTo(newsIds);
        assertThat(todoService.getMyFootCareTodos(user.getId()).getTodos().get(0).getIsCompleted()).isTrue();
    }

    @Test
    void noReplacementCandidates_keepsPreviouslyLinkedTodosAndNews() {
        UserFootCareTodo oldTodo = todo(HealthType.POSTURE, 0);
        assignment(user, oldTodo, null, PREVIOUS_DAY, true);
        link(user, article(HealthType.POSTURE, 0));
        List<Long> todoIds = assignmentIds();
        List<Long> newsIds = articleLinkIds();
        doReturn(List.of()).when(todoCatalog).findByHealthTypeInOrderByIdAsc(any());
        doReturn(List.of()).when(articleCatalog).findByHealthTypeInOrderByPublishedAtDesc(any());

        complete(session(user, PREVIOUS_DAY.plusDays(1)), MetricType.PRESSURE_BALANCE);

        assertThat(assignmentIds()).isEqualTo(todoIds);
        assertThat(articleLinkIds()).isEqualTo(newsIds);
        assertThat(todoService.getMyFootCareTodos(user.getId()).getTodos().get(0).getIsCompleted()).isTrue();
    }

    private void complete(MeasurementSession session, MetricType weakMetric) {
        for (MetricType metric : METRICS) {
            save(session, metric, metric == weakMetric ? 0f : 100f);
        }
    }

    private ReportResponseDTO.SaveMetricResultResultDTO save(MeasurementSession session, MetricType metric, float score) {
        var request = new ReportRequestDTO.SaveMetricResultDTO();
        ReflectionTestUtils.setField(request, "measurementSessionId", session.getId());
        ReflectionTestUtils.setField(request, "metricType", metric);
        ReflectionTestUtils.setField(request, "score", score);
        ReflectionTestUtils.setField(request, "advice", List.of("care advice", "follow up"));
        return reportService.saveMetricResult(session.getUser().getId(), request);
    }

    private void markCurrentTodoComplete() {
        assignments.findLatestAssignmentsByUserId(user.getId()).get(0).updateCompletion(true);
        em.flush();
        em.clear();
    }

    private List<Long> assignmentIds() {
        return assignments.findLatestAssignmentsByUserId(user.getId()).stream()
                .map(UserFootCareTodoAssignment::getId).toList();
    }

    private List<Long> articleLinkIds() {
        return articleLinks.findAllByUserIdWithArticle(user.getId()).stream()
                .map(UserHealthArticle::getId).toList();
    }

    private User user(String name) {
        return em.persist(User.builder().nickname(name).socialId(name).socialType(SocialType.KAKAO).build());
    }

    private MeasurementSession session(User owner, LocalDateTime measuredAt) {
        return em.persist(MeasurementSession.builder().user(owner).device(device)
                .measuredAt(measuredAt).status(MeasurementStatus.ANALYZING).build());
    }

    private void catalog(HealthType type) {
        for (int i = 0; i < 3; i++) todo(type, i);
        for (int i = 0; i < 4; i++) article(type, i);
    }

    private UserFootCareTodo todo(HealthType type, int index) {
        return em.persist(UserFootCareTodo.builder().title("todo " + type + index)
                .healthType(type).todoDate(PREVIOUS_DAY).build());
    }

    private HealthArticle article(HealthType type, int index) {
        return em.persist(HealthArticle.builder().title("news " + type + index)
                .healthType(type).url("https://example.com/news/" + type + index)
                .publisher("test").publishedAt(PREVIOUS_DAY.plusMinutes(index)).build());
    }

    private void link(User owner, HealthArticle article) {
        em.persistAndFlush(UserHealthArticle.builder().user(owner).healthArticle(article).build());
    }

    private void assignment(User owner, UserFootCareTodo todo, Long sourceSessionId,
                            LocalDateTime assignedAt, boolean completed) {
        var assignment = em.persistAndFlush(UserFootCareTodoAssignment.builder()
                .user(owner).footCareTodo(todo).sourceMeasurementSessionId(sourceSessionId).build());
        assignment.updateCompletion(completed);
        em.flush();
        em.getEntityManager().createQuery("UPDATE UserFootCareTodoAssignment a SET a.createdAt = :at WHERE a.id = :id")
                .setParameter("at", assignedAt).setParameter("id", assignment.getId()).executeUpdate();
        em.refresh(assignment);
    }
}
