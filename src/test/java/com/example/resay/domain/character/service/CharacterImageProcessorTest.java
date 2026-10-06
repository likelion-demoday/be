package com.example.resay.domain.character.service;

import com.example.resay.domain.analysis.entity.AnalysisResult;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.character.event.CharacterImageRequestedEvent;
import com.example.resay.domain.character.model.CharacterImageGenerationCommand;
import com.example.resay.domain.character.model.GeneratedCharacterImage;
import com.example.resay.domain.character.port.CharacterImageGenerator;
import com.example.resay.domain.character.port.CharacterImageStorage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class CharacterImageProcessorTest {

    @Mock
    private ConversationAnalysisRepository conversationAnalysisRepository;

    @Mock
    private AnalysisResultRepository analysisResultRepository;

    @Mock
    private CharacterImageService characterImageService;

    @Mock
    private CharacterImageGenerator characterImageGenerator;

    @Mock
    private CharacterImageStorage characterImageStorage;

    private ObjectMapper objectMapper;
    private CharacterImageProcessor processor;
    private GeneratedCharacterImage generatedImage;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        processor = new CharacterImageProcessor(
                conversationAnalysisRepository,
                analysisResultRepository,
                characterImageService,
                characterImageGenerator,
                characterImageStorage,
                objectMapper
        );
        generatedImage = new GeneratedCharacterImage(
                new byte[]{1, 2, 3},
                "image/png",
                "gpt-image",
                "character-image-v1"
        );
    }

    @Test
    void generatesAndStoresImageForEachSpeaker() throws Exception {
        givenCompletedReport();
        given(characterImageService.begin(eq(10L), any())).willReturn(true);
        given(characterImageGenerator.generate(any())).willReturn(generatedImage);
        given(characterImageStorage.save(eq(10L), any(), eq(generatedImage)))
                .willAnswer(invocation -> "10/" + invocation.getArgument(1, SpeakerRole.class) + ".png");

        processor.process(new CharacterImageRequestedEvent(10L, 1L));

        ArgumentCaptor<CharacterImageGenerationCommand> commandCaptor =
                ArgumentCaptor.forClass(CharacterImageGenerationCommand.class);
        then(characterImageGenerator).should(times(2)).generate(commandCaptor.capture());
        assertThat(commandCaptor.getAllValues())
                .extracting(CharacterImageGenerationCommand::speakerRole)
                .containsExactly(SpeakerRole.SELF, SpeakerRole.FRIEND);
        then(characterImageService).should().complete(
                10L,
                SpeakerRole.SELF,
                "10/SELF.png",
                generatedImage
        );
        then(characterImageService).should().complete(
                10L,
                SpeakerRole.FRIEND,
                "10/FRIEND.png",
                generatedImage
        );
        then(characterImageService).should(never()).fail(any(), any(), anyString());
    }

    @Test
    void continuesWithOtherSpeakerWhenOneGenerationFails() throws Exception {
        givenCompletedReport();
        given(characterImageService.begin(eq(10L), any())).willReturn(true);
        given(characterImageGenerator.generate(any())).willAnswer(invocation -> {
            CharacterImageGenerationCommand command = invocation.getArgument(0);
            if (command.speakerRole() == SpeakerRole.SELF) {
                throw new IllegalStateException("생성 실패");
            }
            return generatedImage;
        });
        given(characterImageStorage.save(10L, SpeakerRole.FRIEND, generatedImage))
                .willReturn("10/friend.png");

        processor.process(new CharacterImageRequestedEvent(10L, 1L));

        then(characterImageService).should().fail(
                10L,
                SpeakerRole.SELF,
                "IllegalStateException"
        );
        then(characterImageService).should().complete(
                10L,
                SpeakerRole.FRIEND,
                "10/friend.png",
                generatedImage
        );
    }

    @Test
    void skipsAlreadyCreatedSpeakerWork() throws Exception {
        givenCompletedReport();
        given(characterImageService.begin(eq(10L), any())).willReturn(false);

        processor.process(new CharacterImageRequestedEvent(10L, 1L));

        then(characterImageGenerator).shouldHaveNoInteractions();
        then(characterImageStorage).shouldHaveNoInteractions();
    }

    @Test
    void continuesWhenStartingOneSpeakerWorkFails() throws Exception {
        givenCompletedReport();
        given(characterImageService.begin(10L, SpeakerRole.SELF))
                .willThrow(new IllegalStateException("작업 생성 실패"));
        given(characterImageService.begin(10L, SpeakerRole.FRIEND)).willReturn(true);
        given(characterImageGenerator.generate(any())).willReturn(generatedImage);
        given(characterImageStorage.save(10L, SpeakerRole.FRIEND, generatedImage))
                .willReturn("10/friend.png");

        processor.process(new CharacterImageRequestedEvent(10L, 1L));

        then(characterImageService).should().complete(
                10L,
                SpeakerRole.FRIEND,
                "10/friend.png",
                generatedImage
        );
        then(characterImageService).should(never()).fail(
                eq(10L),
                eq(SpeakerRole.SELF),
                anyString()
        );
    }

    @Test
    void deletesStoredFileWhenDatabaseCompletionFails() throws Exception {
        givenCompletedReport();
        given(characterImageService.begin(10L, SpeakerRole.SELF)).willReturn(true);
        given(characterImageService.begin(10L, SpeakerRole.FRIEND)).willReturn(false);
        given(characterImageGenerator.generate(any())).willReturn(generatedImage);
        given(characterImageStorage.save(10L, SpeakerRole.SELF, generatedImage))
                .willReturn("10/self.png");
        willThrow(new IllegalStateException("상태 저장 실패"))
                .given(characterImageService)
                .complete(10L, SpeakerRole.SELF, "10/self.png", generatedImage);

        processor.process(new CharacterImageRequestedEvent(10L, 1L));

        then(characterImageStorage).should().delete("10/self.png");
        then(characterImageService).should().fail(
                10L,
                SpeakerRole.SELF,
                "IllegalStateException"
        );
    }

    private void givenCompletedReport() throws Exception {
        ConversationAnalysis analysis = ConversationAnalysis.start(1L);
        ReflectionTestUtils.setField(analysis, "id", 10L);
        analysis.complete();
        AnalysisResult result = AnalysisResult.create(
                10L,
                objectMapper.writeValueAsString(report()),
                "liner-mark-1.1",
                "analysis-prompt-v4",
                "analysis-report-v5"
        );
        given(conversationAnalysisRepository.findById(10L)).willReturn(Optional.of(analysis));
        given(analysisResultRepository.findByAnalysisId(10L)).willReturn(Optional.of(result));
    }

    private AnalysisReport report() {
        return new AnalysisReport(
                new AnalysisReport.RecordingInfo(
                        1L,
                        AnalysisScenario.FRIEND_DAILY,
                        600_000L,
                        speakersFor(AnalysisScenario.FRIEND_DAILY)
                ),
                new AnalysisReport.QuantitativeAnalysis(List.of(), null),
                new AnalysisReport.QualitativeReport(
                        new QualitativeAnalysis.Overview("요약", "설명", List.of(1L)),
                        List.of(),
                        List.of(),
                        List.of(
                                new QualitativeAnalysis.CharacterInsight(
                                        SpeakerRole.SELF,
                                        "차분한 탐험가",
                                        "상대의 이야기를 차분히 살피는 모습이 보여요.",
                                        List.of(1L)
                                ),
                                new QualitativeAnalysis.CharacterInsight(
                                        SpeakerRole.FRIEND,
                                        "활기찬 응원가",
                                        "밝은 반응으로 대화의 흐름을 이어가요.",
                                        List.of(2L)
                                )
                        ),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );
    }
}
