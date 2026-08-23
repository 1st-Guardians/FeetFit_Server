package com.feetfit.server.service.ShoeService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.apiPayload.exception.handler.ShoeHandler;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.ShoeClickHistoryRepository;
import com.feetfit.server.repository.ShoeRecommendationReasonRepository;
import com.feetfit.server.repository.ShoeRecommendationReasonReviewRepository;
import com.feetfit.server.repository.ShoeRecommendationRepository;
import com.feetfit.server.repository.ShoeRepository;
import com.feetfit.server.repository.ShoeReviewRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.shoe.ShoeRequestDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoeCommandServiceImplValidationTest {

    @Mock ShoeRepository shoeRepository;
    @Mock ShoeClickHistoryRepository shoeClickHistoryRepository;
    @Mock UserRepository userRepository;
    @Mock ShoeRecommendationRepository shoeRecommendationRepository;
    @Mock ShoeRecommendationReasonRepository shoeRecommendationReasonRepository;
    @Mock ShoeRecommendationReasonReviewRepository shoeRecommendationReasonReviewRepository;
    @Mock ShoeReviewRepository shoeReviewRepository;
    @Mock MeasurementSessionRepository measurementSessionRepository;

    @InjectMocks ShoeCommandServiceImpl service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsDuplicateReasonTypeBeforeAnyDatabaseWrite() throws Exception {
        ShoeRequestDTO.SaveShoeRecommendationDTO request = readRequest("""
                {"measurementSessionId":1,"recommendations":[{
                  "shoeId":10,"fitScore":80,
                  "reasons":[
                    {"reasonType":"FOREFOOT","title":"a","riskLevel":"LOW","reviewIds":[]},
                    {"reasonType":"FOREFOOT","title":"b","riskLevel":"LOW","reviewIds":[]},
                    {"reasonType":"INSOLE","title":"c","riskLevel":"LOW","reviewIds":[]}
                  ]
                }]}
                """);

        assertThatThrownBy(() -> service.saveShoeRecommendations(7L, request))
                .isInstanceOf(ShoeHandler.class);
        verifyNoInteractions(measurementSessionRepository, shoeRepository);
    }

    @Test
    void rejectsMeasurementSessionOwnedByAnotherUser() throws Exception {
        ShoeRequestDTO.SaveShoeRecommendationDTO request = readRequest("""
                {"measurementSessionId":1,"recommendations":[{
                  "shoeId":10,"fitScore":80,
                  "reasons":[
                    {"reasonType":"FOREFOOT","title":"a","riskLevel":"LOW","reviewIds":[]},
                    {"reasonType":"HEEL","title":"b","riskLevel":"LOW","reviewIds":[]},
                    {"reasonType":"INSOLE","title":"c","riskLevel":"LOW","reviewIds":[]}
                  ]
                }]}
                """);
        MeasurementSession otherUsersSession = MeasurementSession.builder()
                .id(1L)
                .user(User.builder().id(99L).build())
                .status(MeasurementStatus.COMPLETED)
                .build();
        when(measurementSessionRepository.findById(1L)).thenReturn(Optional.of(otherUsersSession));

        assertThatThrownBy(() -> service.saveShoeRecommendations(7L, request))
                .isInstanceOf(MeasurementHandler.class);
        verifyNoInteractions(shoeRepository);
    }

    @Test
    void rejectsFitScoreOutsideZeroToOneHundred() throws Exception {
        ShoeRequestDTO.SaveShoeRecommendationDTO request = readRequest("""
                {"measurementSessionId":1,"recommendations":[{
                  "shoeId":10,"fitScore":101,
                  "reasons":[
                    {"reasonType":"FOREFOOT","title":"a","riskLevel":"LOW","reviewIds":[]},
                    {"reasonType":"HEEL","title":"b","riskLevel":"LOW","reviewIds":[]},
                    {"reasonType":"INSOLE","title":"c","riskLevel":"LOW","reviewIds":[]}
                  ]
                }]}
                """);

        assertThatThrownBy(() -> service.saveShoeRecommendations(7L, request))
                .isInstanceOf(ShoeHandler.class);
        verifyNoInteractions(measurementSessionRepository);
    }

    private ShoeRequestDTO.SaveShoeRecommendationDTO readRequest(String json) throws Exception {
        return objectMapper.readValue(json, ShoeRequestDTO.SaveShoeRecommendationDTO.class);
    }
}
