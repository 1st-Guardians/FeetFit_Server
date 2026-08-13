package com.feetfit.server.service.FootCareTodoService;

import com.feetfit.server.apiPayload.exception.handler.FootCareTodoHandler;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.UserFootCareTodo;
import com.feetfit.server.domain.UserFootCareTodoAssignment;
import com.feetfit.server.domain.enums.HealthType;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.domain.enums.UserStatus;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.repository.UserFootCareTodoAssignmentRepository;
import com.feetfit.server.web.dto.footcare.FootCareTodoRequestDTO;
import com.feetfit.server.web.dto.footcare.FootCareTodoResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FootCareTodoServiceImplTest {

    @Mock
    private UserFootCareTodoAssignmentRepository userFootCareTodoAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FootCareTodoServiceImpl footCareTodoService;

    @Test
    void getMyFootCareTodos_existingUser_returnsTodos() {
        given(userRepository.existsById(1L)).willReturn(true);
        given(userFootCareTodoAssignmentRepository.findTodayAssignmentsByUserId(
                eq(1L),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        ))
                .willReturn(List.of(footCareTodoAssignment(false)));

        FootCareTodoResponseDTO.FootCareTodoListResponseDTO response =
                footCareTodoService.getMyFootCareTodos(1L);

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getHasTodayTodos()).isTrue();
        assertThat(response.getMessage()).isEqualTo("오늘 발 관리 투두입니다.");
        assertThat(response.getTodos()).hasSize(1);
        assertThat(response.getTodos().get(0).getTodoId()).isEqualTo(1L);
        assertThat(response.getTodos().get(0).getTitle()).isEqualTo("수건으로 발 당기기");
        assertThat(response.getTodos().get(0).getHealthType()).isEqualTo("POSTURE");
        assertThat(response.getTodos().get(0).getIsCompleted()).isFalse();
    }

    @Test
    void getMyFootCareTodos_noTodayMeasurement_returnsEmptyTodosWithMessage() {
        given(userRepository.existsById(1L)).willReturn(true);
        given(userFootCareTodoAssignmentRepository.findTodayAssignmentsByUserId(
                eq(1L),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).willReturn(List.of());

        FootCareTodoResponseDTO.FootCareTodoListResponseDTO response =
                footCareTodoService.getMyFootCareTodos(1L);

        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getHasTodayTodos()).isFalse();
        assertThat(response.getMessage()).isEqualTo("오늘 발 관리 투두가 없습니다.");
        assertThat(response.getTodos()).isEmpty();
    }

    @Test
    void getMyFootCareTodos_missingUser_throwsUserHandler() {
        given(userRepository.existsById(404L)).willReturn(false);

        assertThatThrownBy(() -> footCareTodoService.getMyFootCareTodos(404L))
                .isInstanceOf(UserHandler.class);
    }

    @Test
    void updateCompletion_existingTodo_updatesCompletion() {
        UserFootCareTodoAssignment assignment = footCareTodoAssignment(false);
        given(userRepository.existsById(1L)).willReturn(true);
        given(userFootCareTodoAssignmentRepository.findByUserIdAndTodoId(1L, 1L))
                .willReturn(Optional.of(assignment));

        FootCareTodoResponseDTO.FootCareTodoInfoResponseDTO response =
                footCareTodoService.updateCompletion(1L, 1L, completionRequest(true));

        assertThat(assignment.getIsCompleted()).isTrue();
        assertThat(assignment.getCompletedAt()).isNotNull();
        assertThat(response.getIsCompleted()).isTrue();
        assertThat(response.getCompletedAt()).isNotNull();
    }

    @Test
    void updateCompletion_existingTodo_canUncheckCompletion() {
        UserFootCareTodoAssignment assignment = footCareTodoAssignment(true);
        given(userRepository.existsById(1L)).willReturn(true);
        given(userFootCareTodoAssignmentRepository.findByUserIdAndTodoId(1L, 1L))
                .willReturn(Optional.of(assignment));

        FootCareTodoResponseDTO.FootCareTodoInfoResponseDTO response =
                footCareTodoService.updateCompletion(1L, 1L, completionRequest(false));

        assertThat(assignment.getIsCompleted()).isFalse();
        assertThat(assignment.getCompletedAt()).isNull();
        assertThat(response.getIsCompleted()).isFalse();
    }

    @Test
    void updateCompletion_missingTodo_throwsFootCareTodoHandler() {
        given(userRepository.existsById(1L)).willReturn(true);
        given(userFootCareTodoAssignmentRepository.findByUserIdAndTodoId(1L, 404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> footCareTodoService.updateCompletion(1L, 404L, completionRequest(true)))
                .isInstanceOf(FootCareTodoHandler.class);
    }

    private static FootCareTodoRequestDTO.UpdateCompletionRequestDTO completionRequest(Boolean isCompleted) {
        FootCareTodoRequestDTO.UpdateCompletionRequestDTO request =
                new FootCareTodoRequestDTO.UpdateCompletionRequestDTO();
        ReflectionTestUtils.setField(request, "isCompleted", isCompleted);
        return request;
    }

    private static UserFootCareTodoAssignment footCareTodoAssignment(Boolean isCompleted) {
        return UserFootCareTodoAssignment.builder()
                .id(1L)
                .user(user())
                .footCareTodo(footCareTodo())
                .isCompleted(isCompleted)
                .build();
    }

    private static UserFootCareTodo footCareTodo() {
        return UserFootCareTodo.builder()
                .id(1L)
                .title("수건으로 발 당기기")
                .healthType(HealthType.POSTURE)
                .youtubeUrl("https://www.youtube.com/watch?v=stretching")
                .todoDate(LocalDateTime.of(2026, 5, 20, 9, 0))
                .build();
    }

    private static User user() {
        return User.builder()
                .id(1L)
                .nickname("은서")
                .socialId("12345")
                .socialType(SocialType.KAKAO)
                .status(UserStatus.ACTIVE)
                .build();
    }

}
