package com.example.resay.domain.notification.controller;

import com.example.resay.domain.notification.dto.ServiceAnnouncementRequestDto;
import com.example.resay.domain.notification.dto.ServiceAnnouncementResponseDto;
import com.example.resay.domain.notification.entity.NotificationLog;
import com.example.resay.domain.notification.entity.NotificationLogStatus;
import com.example.resay.domain.notification.entity.NotificationSetting;
import com.example.resay.domain.notification.entity.NotificationType;
import com.example.resay.domain.notification.port.EmailMessage;
import com.example.resay.domain.notification.port.EmailSendException;
import com.example.resay.domain.notification.port.EmailSendResult;
import com.example.resay.domain.notification.port.EmailSender;
import com.example.resay.domain.notification.repository.NotificationLogRepository;
import com.example.resay.domain.notification.repository.NotificationSettingRepository;
import com.example.resay.domain.notification.service.ServiceAnnouncementService;
import com.example.resay.domain.user.entity.Role;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminNotificationControllerTest {

    private static final String URL = "/api/v1/admin/notifications/service";
    private static final String BODY = """
            {"subject": "서버 점검 안내", "body": "11월 10일 새벽 2시부터 1시간 동안 점검합니다.\\n<b>이용에 참고해 주세요.</b>"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ServiceAnnouncementService serviceAnnouncementService;

    @MockitoBean
    private EmailSender emailSender;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        // 다른 테스트가 커밋한 설정이 대상에 섞이지 않게 비우고 시작한다 (이 테스트는 끝나면 롤백된다)
        notificationLogRepository.deleteAll();
        notificationSettingRepository.deleteAll();
        User admin = userRepository.saveAndFlush(User.createLocal("admin-notice@example.com", "encoded", "관리자"));
        User user = userRepository.saveAndFlush(User.createLocal("user-notice@example.com", "encoded", "사용자"));
        adminToken = jwtTokenProvider.issueAccessToken(admin.getId(), Role.ADMIN.name()).value();
        userToken = jwtTokenProvider.issueAccessToken(user.getId(), Role.USER.name()).value();
        given(emailSender.send(any())).willReturn(EmailSendResult.sent("msg-1"));
    }

    private User subscriber(String alertEmail, boolean serviceEnabled) {
        User user = userRepository.saveAndFlush(
                User.createLocal("login-" + alertEmail, "encoded", "구독자"));
        NotificationSetting setting = NotificationSetting.createDefault(user.getId());
        setting.changeEmail(alertEmail);
        setting.changeServiceEnabled(serviceEnabled);
        notificationSettingRepository.saveAndFlush(setting);
        return user;
    }

    private ResultActions send(String token, String body) throws Exception {
        return mockMvc.perform(post(URL)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void 서비스_알림을_켠_사용자에게만_보내고_결과를_알려준다() throws Exception {
        User first = subscriber("first@example.com", true);
        subscriber("second@example.com", true);
        subscriber("off@example.com", false);

        send(adminToken, BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOTIFICATION200_4"))
                .andExpect(jsonPath("$.result.targetCount").value(2))
                .andExpect(jsonPath("$.result.sentCount").value(2))
                .andExpect(jsonPath("$.result.failedCount").value(0));

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(EmailMessage::to)
                .containsExactly("first@example.com", "second@example.com");
        EmailMessage message = captor.getAllValues().get(0);
        assertThat(message.subject()).isEqualTo("[RE:SAY] 서버 점검 안내");
        assertThat(message.html())
                .contains("11월 10일 새벽 2시부터 1시간 동안 점검합니다.<br>")
                .contains("&lt;b&gt;이용에 참고해 주세요.&lt;/b&gt;")
                .doesNotContain("<b>이용에");
        assertThat(message.subject()).doesNotContain("(광고)");

        List<NotificationLog> logs = notificationLogRepository.findAll();
        assertThat(logs).hasSize(2).allMatch(log -> log.getType() == NotificationType.SERVICE_ANNOUNCEMENT
                && log.getStatus() == NotificationLogStatus.SENT);
        assertThat(logs).extracting(NotificationLog::getUserId).contains(first.getId());
    }

    @Test
    void 알림_이메일이_없는_사용자에게는_보내지_않는다() throws Exception {
        subscriber(null, true);

        send(adminToken, BODY).andExpect(jsonPath("$.result.targetCount").value(0));

        verify(emailSender, never()).send(any());
    }

    @Test
    void 일부가_실패해도_나머지에게는_보내고_실패_수를_알려준다() throws Exception {
        subscriber("first@example.com", true);
        subscriber("broken@example.com", true);
        given(emailSender.send(argThat(message -> message != null && message.to().equals("broken@example.com"))))
                .willThrow(new EmailSendException("HTTP_422 validation_error", null));

        send(adminToken, BODY)
                .andExpect(jsonPath("$.result.targetCount").value(2))
                .andExpect(jsonPath("$.result.sentCount").value(1))
                .andExpect(jsonPath("$.result.failedCount").value(1));
    }

    @Test
    void 오늘_남은_발송_수보다_대상이_많으면_한_통도_보내지_않고_거절한다() throws Exception {
        User sender = subscriber("first@example.com", true);
        subscriber("second@example.com", true);
        // 오늘 이미 99통을 보냈다 (하루 한도 100)
        for (int i = 0; i < 99; i++) {
            notificationLogRepository.save(NotificationLog.pending(sender.getId(), NotificationType.MARKETING_REMINDER, null));
        }
        notificationLogRepository.flush();

        send(adminToken, BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTIFICATION400_2"))
                .andExpect(jsonPath("$.error.targetCount").value(2))
                .andExpect(jsonPath("$.error.remainingToday").value(1));

        verify(emailSender, never()).send(any());
    }

    @Test
    void 거절된_발송은_한도에_들어가지_않는다() throws Exception {
        User sender = subscriber("first@example.com", true);
        for (int i = 0; i < 99; i++) {
            NotificationLog log = NotificationLog.pending(sender.getId(), NotificationType.MARKETING_REMINDER, null);
            log.markFailed("HTTP_422 validation_error");
            notificationLogRepository.save(log);
        }
        notificationLogRepository.flush();

        send(adminToken, BODY).andExpect(status().isOk());
    }

    @Test
    void 제목이나_본문이_비었거나_너무_길면_400() throws Exception {
        send(adminToken, "{\"subject\": \" \", \"body\": \"본문\"}").andExpect(status().isBadRequest());
        send(adminToken, "{\"subject\": \"제목\", \"body\": \"\"}").andExpect(status().isBadRequest());
        send(adminToken, "{\"subject\": \"" + "가".repeat(101) + "\", \"body\": \"본문\"}")
                .andExpect(status().isBadRequest());
        send(adminToken, "{\"subject\": \"첫 줄\n둘째 줄\", \"body\": \"본문\"}")
                .andExpect(status().isBadRequest());

        verify(emailSender, never()).send(any());
    }

    @Test
    void 보내는_중에_서버가_꺼지면_남은_대상에게는_보내지_않고_멈춘다() {
        subscriber("first@example.com", true);
        subscriber("second@example.com", true);
        subscriber("third@example.com", true);
        // 첫 메일을 보내는 사이 서버 종료 신호(인터럽트)가 들어온 상황
        given(emailSender.send(any())).willAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return EmailSendResult.sent("msg-1");
        });

        ServiceAnnouncementResponseDto response;
        try {
            response = serviceAnnouncementService.send(new ServiceAnnouncementRequestDto("점검 안내", "본문"));
        } finally {
            Thread.interrupted();
        }

        assertThat(response).isEqualTo(new ServiceAnnouncementResponseDto(3, 1, 0));
        verify(emailSender, times(1)).send(any());
    }

    @Test
    void 관리자가_아니면_403() throws Exception {
        subscriber("first@example.com", true);

        send(userToken, BODY).andExpect(status().isForbidden());

        verify(emailSender, never()).send(any());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }
}
