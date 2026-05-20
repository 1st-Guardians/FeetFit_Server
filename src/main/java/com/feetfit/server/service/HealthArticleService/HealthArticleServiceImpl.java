package com.feetfit.server.service.HealthArticleService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.HealthArticleConverter;
import com.feetfit.server.domain.UserHealthArticle;
import com.feetfit.server.repository.UserHealthArticleRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.health.HealthArticleResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthArticleServiceImpl implements HealthArticleService {

    private final UserHealthArticleRepository userHealthArticleRepository;
    private final UserRepository userRepository;

    @Override
    public HealthArticleResponseDTO.HealthArticleListResponseDTO getMyHealthArticles(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserHandler(ErrorStatus.USER_NOT_FOUND);
        }

        List<UserHealthArticle> userHealthArticles = userHealthArticleRepository.findAllByUserIdWithArticle(userId);
        return HealthArticleConverter.toHealthArticleListResponseDTO(userHealthArticles);
    }
}
