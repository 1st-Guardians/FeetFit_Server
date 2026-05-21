package com.feetfit.server.service.ShoeService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.ShoeHandler;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.ShoeConverter;
import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.ShoeClickHistory;
import com.feetfit.server.domain.User;
import com.feetfit.server.repository.ShoeClickHistoryRepository;
import com.feetfit.server.repository.ShoeRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.shoe.ShoeResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ShoeCommandServiceImpl implements ShoeCommandService {

    private final ShoeRepository shoeRepository;
    private final ShoeClickHistoryRepository shoeClickHistoryRepository;
    private final UserRepository userRepository;

    @Override
    public ShoeResponseDTO.ShoeClickResultDTO clickShoe(Long userId, Long shoeId) {

        Shoe shoe = shoeRepository.findById(shoeId)
                .orElseThrow(() -> new ShoeHandler(ErrorStatus.SHOE_NOT_FOUND));

        // 이미 클릭한 유저면 clickCount 증가 안 함
        if (!shoeClickHistoryRepository.existsByUserIdAndShoeId(userId, shoeId)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

            shoe.incrementClickCount();
            shoeClickHistoryRepository.save(
                    ShoeClickHistory.builder()
                            .user(user)
                            .shoe(shoe)
                            .build()
            );
        }

        return ShoeConverter.toShoeClickResultDTO(shoe);
    }
}