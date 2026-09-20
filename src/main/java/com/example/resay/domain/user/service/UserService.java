package com.example.resay.domain.user.service;

import com.example.resay.domain.user.code.UserErrorCode;
import com.example.resay.domain.user.dto.UpdateUserRequestDto;
import com.example.resay.domain.user.dto.UserResponseDto;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserResponseDto getMyInfo(Long userId) {
        return UserResponseDto.from(findUser(userId));
    }

    // 이메일·가입 경로 같은 신원 정보는 바꿀 수 없고 닉네임만 수정한다
    @Transactional
    public UserResponseDto updateMyInfo(Long userId, UpdateUserRequestDto request) {
        User user = findUser(userId);
        user.updateNickname(request.nickname());
        return UserResponseDto.from(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));
    }
}
