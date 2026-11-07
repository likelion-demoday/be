package com.example.resay.domain.notification.service;

import com.example.resay.domain.notification.code.NotificationErrorCode;
import com.example.resay.domain.notification.dto.NotificationSettingsResponseDto;
import com.example.resay.domain.notification.dto.UpdateNotificationEmailRequestDto;
import com.example.resay.domain.notification.dto.UpdateNotificationSettingsRequestDto;
import com.example.resay.domain.notification.entity.NotificationSetting;
import com.example.resay.domain.notification.repository.NotificationSettingRepository;
import com.example.resay.domain.user.code.UserErrorCode;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSettingService {

    private final NotificationSettingRepository notificationSettingRepository;
    private final UserRepository userRepository;

    public NotificationSettingsResponseDto getSettings(Long userId) {
        User user = findUser(userId);
        NotificationSetting setting = notificationSettingRepository.findByUserId(userId)
                .orElseGet(() -> NotificationSetting.createDefault(userId));
        return toResponse(setting, user);
    }

    @Transactional
    public NotificationSettingsResponseDto updateEmail(Long userId, UpdateNotificationEmailRequestDto request) {
        User user = findUser(userId);
        NotificationSetting setting = findOrCreate(userId);
        setting.changeEmail(request.email());
        // 받을 주소가 없으면 어떤 알림도 보낼 수 없으므로 모두 끈다. 이메일을 다시 넣으면 사용자가 직접 켠다
        if (!setting.hasEmail()) {
            setting.changeAnalysisEnabled(false);
            setting.changeServiceEnabled(false);
            user.updateMarketingAgreement(false, LocalDateTime.now());
        }
        return toResponse(setting, user);
    }

    @Transactional
    public NotificationSettingsResponseDto updateSettings(Long userId, UpdateNotificationSettingsRequestDto request) {
        User user = findUser(userId);
        NotificationSetting setting = findOrCreate(userId);
        // 받을 주소가 없으면 켜도 보낼 수 없으므로 막는다. 끄는 것은 이메일이 없어도 된다
        if (!setting.hasEmail() && turnsOnAny(request)) {
            throw new GeneralException(NotificationErrorCode.EMAIL_REQUIRED);
        }
        if (request.analysis() != null) {
            setting.changeAnalysisEnabled(request.analysis());
        }
        if (request.service() != null) {
            setting.changeServiceEnabled(request.service());
        }
        if (request.marketing() != null) {
            user.updateMarketingAgreement(request.marketing(), LocalDateTime.now());
        }
        return toResponse(setting, user);
    }

    private static boolean turnsOnAny(UpdateNotificationSettingsRequestDto request) {
        return Boolean.TRUE.equals(request.analysis())
                || Boolean.TRUE.equals(request.service())
                || Boolean.TRUE.equals(request.marketing());
    }

    private NotificationSetting findOrCreate(Long userId) {
        return notificationSettingRepository.findByUserId(userId)
                .orElseGet(() -> notificationSettingRepository.save(NotificationSetting.createDefault(userId)));
    }

    private static NotificationSettingsResponseDto toResponse(NotificationSetting setting, User user) {
        return new NotificationSettingsResponseDto(
                setting.getEmail(),
                setting.isAnalysisEnabled(),
                setting.isServiceEnabled(),
                user.getMarketingAgreedAt() != null
        );
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));
    }
}
