package com.example.resay.domain.character.service;

import com.example.resay.domain.analysis.entity.AnalysisResult;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.character.code.CharacterImageErrorCode;
import com.example.resay.domain.character.dto.CharacterImageRequestResponseDto;
import com.example.resay.domain.character.event.CharacterImageRequestedEvent;
import com.example.resay.domain.credit.model.UsageResult;
import com.example.resay.domain.credit.service.CreditService;
import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.global.exception.GeneralException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CharacterImageRequestServiceTest {

    @Mock
    private RecordingRepository recordingRepository;

    @Mock
    private ConversationAnalysisRepository conversationAnalysisRepository;

    @Mock
    private AnalysisResultRepository analysisResultRepository;

    @Mock
    private CharacterImageService characterImageService;

    @Mock
    private CreditService creditService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ObjectMapper objectMapper;
    private CharacterImageRequestService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new CharacterImageRequestService(
                recordingRepository,
                conversationAnalysisRepository,
                analysisResultRepository,
                characterImageService,
                creditService,
                eventPublisher,
                objectMapper
        );
    }

    @Test
    void reservesWorkChargesCreditAndPublishesRequestEvent() throws Exception {
        givenCompletedAnalysis(reportWithCharacters());
        given(characterImageService.prepare(
                10L,
                List.of(SpeakerRole.SELF, SpeakerRole.FRIEND)
        )).willReturn(true);
        given(creditService.useForCharacter(1L, 1L))
                .willReturn(new UsageResult(100L, 0, true, 0, false));

        CharacterImageRequestResponseDto response = service.request(1L, 1L);

        assertThat(response.recordingId()).isEqualTo(1L);
        assertThat(response.alreadyRequested()).isFalse();
        ArgumentCaptor<CharacterImageRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(CharacterImageRequestedEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new CharacterImageRequestedEvent(10L, 1L));
    }

    @Test
    void doesNotChargeOrPublishWhenWorkAlreadyExists() throws Exception {
        givenCompletedAnalysis(reportWithCharacters());
        given(characterImageService.prepare(
                10L,
                List.of(SpeakerRole.SELF, SpeakerRole.FRIEND)
        )).willReturn(false);

        CharacterImageRequestResponseDto response = service.request(1L, 1L);

        assertThat(response.alreadyRequested()).isTrue();
        then(creditService).should(never()).useForCharacter(1L, 1L);
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void rejectsRequestBeforeAnalysisCompletion() {
        Recording recording = recording();
        ConversationAnalysis analysis = ConversationAnalysis.start(1L);
        ReflectionTestUtils.setField(analysis, "id", 10L);
        given(recordingRepository.findByIdAndUserId(1L, 1L))
                .willReturn(Optional.of(recording));
        given(conversationAnalysisRepository.findByRecordingIdForUpdate(1L))
                .willReturn(Optional.of(analysis));

        assertThatThrownBy(() -> service.request(1L, 1L))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(CharacterImageErrorCode.INVALID_ANALYSIS_STATUS);
        then(creditService).shouldHaveNoInteractions();
    }

    @Test
    void hidesAnotherUsersRecordingAsNotFound() {
        given(recordingRepository.findByIdAndUserId(1L, 2L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(2L, 1L))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(RecordingErrorCode.RECORDING_NOT_FOUND);
        then(creditService).shouldHaveNoInteractions();
    }

    private void givenCompletedAnalysis(AnalysisReport report) throws Exception {
        Recording recording = recording();
        ConversationAnalysis analysis = ConversationAnalysis.start(1L);
        ReflectionTestUtils.setField(analysis, "id", 10L);
        analysis.complete();
        AnalysisResult result = AnalysisResult.create(
                10L,
                objectMapper.writeValueAsString(report),
                "liner-mark-1.1",
                "analysis-prompt-v9",
                "analysis-report-v8"
        );
        given(recordingRepository.findByIdAndUserId(1L, 1L))
                .willReturn(Optional.of(recording));
        given(conversationAnalysisRepository.findByRecordingIdForUpdate(1L))
                .willReturn(Optional.of(analysis));
        given(analysisResultRepository.findByAnalysisId(10L)).willReturn(Optional.of(result));
    }

    private Recording recording() {
        Recording recording = Recording.create(1L, "conversation.m4a", 600);
        ReflectionTestUtils.setField(recording, "id", 1L);
        return recording;
    }

    private AnalysisReport reportWithCharacters() {
        return new AnalysisReport(
                new AnalysisReport.RecordingInfo(
                        1L,
                        AnalysisScenario.FRIEND_DAILY,
                        600_000L,
                        speakersFor(AnalysisScenario.FRIEND_DAILY)
                ),
                new AnalysisReport.QuantitativeAnalysis(List.of(), null),
                new AnalysisReport.QualitativeReport(
                        null,
                        List.of(),
                        List.of(),
                        List.of(
                                character(SpeakerRole.SELF, "차분한 탐험가"),
                                character(SpeakerRole.FRIEND, "활기찬 응원가")
                        ),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );
    }

    private QualitativeAnalysis.CharacterInsight character(SpeakerRole role, String name) {
        return new QualitativeAnalysis.CharacterInsight(
                role,
                name,
                "대화에서 관찰된 캐릭터 설명",
                List.of(1L)
        );
    }
}
