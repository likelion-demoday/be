package com.example.resay.domain.credit.service;

import com.example.resay.domain.analysis.dto.AnalysisResultCommand;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.analysis.service.AnalysisDeletionService;
import com.example.resay.domain.analysis.service.AnalysisService;
import com.example.resay.domain.character.dto.CharacterImageRequestResponseDto;
import com.example.resay.domain.character.entity.CharacterImage;
import com.example.resay.domain.character.entity.CharacterImageStatus;
import com.example.resay.domain.character.model.GeneratedCharacterImage;
import com.example.resay.domain.character.repository.CharacterImageRepository;
import com.example.resay.domain.character.service.CharacterImageRequestService;
import com.example.resay.domain.character.service.CharacterImageService;
import com.example.resay.domain.credit.entity.CreditLedgerEntry;
import com.example.resay.domain.credit.entity.CreditLedgerType;
import com.example.resay.domain.credit.entity.CreditUsage;
import com.example.resay.domain.credit.entity.UsagePurpose;
import com.example.resay.domain.credit.entity.UsageStatus;
import com.example.resay.domain.credit.repository.CreditLedgerRepository;
import com.example.resay.domain.credit.repository.CreditUsageRepository;
import com.example.resay.domain.credit.repository.CreditWalletRepository;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.exception.GeneralException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;

import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

// 캐릭터 생성 요청 → 차감 → 실패 · 멈춤 → 환급이 실제 커밋을 거쳐 이어지는지 확인한다.
// 테스트 설정은 이미지 생성 작업을 끄므로(character.image.generation-enabled: false) 요청한 이미지는 PENDING에 머문다.
// 생성 작업의 진행은 CharacterImageService를 직접 불러 흉내 낸다
@SpringBootTest
class CharacterCreditFlowTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final Long ADMIN_ID = 999_999L;
    private static final String GENERATION_FAILED = "캐릭터 이미지 생성 실패";

    @Autowired
    private CharacterImageRequestService characterImageRequestService;

    @Autowired
    private CharacterImageService characterImageService;

    @Autowired
    private CreditRefundReconciler creditRefundReconciler;

    @Autowired
    private CreditService creditService;

    @Autowired
    private CreditQueryService creditQueryService;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private AnalysisDeletionService analysisDeletionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private ConversationAnalysisRepository conversationAnalysisRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private CharacterImageRepository characterImageRepository;

    @Autowired
    private CreditWalletRepository walletRepository;

    // 조회 결과를 바꿔 끼우는 테스트가 하나 있다. 그 밖에는 실제 저장소 그대로 동작한다
    @MockitoSpyBean
    private CreditUsageRepository usageRepository;

    @Autowired
    private CreditLedgerRepository ledgerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        characterImageRepository.deleteAllInBatch();
        analysisResultRepository.deleteAllInBatch();
        conversationAnalysisRepository.deleteAllInBatch();
        ledgerRepository.deleteAllInBatch();
        usageRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        recordingRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void usesFreeChanceFirstAndThenChargesPrice() {
        Long userId = userWithCredits(1000);
        Long first = completedAnalysis(userId);
        Long second = completedAnalysis(userId);

        characterImageRequestService.request(userId, first);
        assertThat(balanceOf(userId)).isEqualTo(1000);
        assertThat(freeChanceAvailable(userId)).isFalse();

        CharacterImageRequestResponseDto response = characterImageRequestService.request(userId, second);

        assertThat(response.alreadyRequested()).isFalse();
        assertThat(balanceOf(userId)).isEqualTo(500);
        assertThat(activeUsage(second).getAmount()).isEqualTo(500);
        assertThat(activeUsage(second).isFreeUse()).isFalse();
        assertThat(imageStatuses(second)).containsOnly(CharacterImageStatus.PENDING).hasSize(2);
        assertLedgerMatchesBalance(userId);
    }

    @Test
    void rejectsRequestWithoutEnoughCreditsAndPreparesNothing() {
        Long userId = userWithCredits(300);
        characterImageRequestService.request(userId, completedAnalysis(userId));
        Long recordingId = completedAnalysis(userId);

        assertThatThrownBy(() -> characterImageRequestService.request(userId, recordingId))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode().getCode()).isEqualTo("CREDIT402_1"));

        // 차감이 거절되면 같은 트랜잭션에서 준비한 이미지 작업도 함께 되돌아간다
        assertThat(imageStatuses(recordingId)).isEmpty();
        assertThat(usageRepository.findByActiveKey(activeKey(recordingId))).isEmpty();
        assertThat(balanceOf(userId)).isEqualTo(300);
        assertLedgerMatchesBalance(userId);
    }

    // 한 장이라도 실패하면 전액 환급하고, 다시 요청하면 다시 차감한 뒤 실패한 이미지만 다시 만든다
    @Test
    void refundsFailedSetAndRetriesOnlyFailedImagesWithNewCharge() {
        Long userId = userWithCredits(1000);
        useFreeChance(userId);
        Long recordingId = completedAnalysis(userId);
        characterImageRequestService.request(userId, recordingId);
        Long analysisId = analysisIdOf(recordingId);
        complete(analysisId, SpeakerRole.SELF);
        fail(analysisId, SpeakerRole.FRIEND);
        assertThat(balanceOf(userId)).isEqualTo(500);

        // 생성 작업(CharacterImageGenerationListener)이 실패 때 하는 환급
        assertThat(creditService.refund(UsagePurpose.CHARACTER, recordingId, GENERATION_FAILED)).isTrue();
        assertThat(balanceOf(userId)).isEqualTo(1000);

        CharacterImageRequestResponseDto retry = characterImageRequestService.request(userId, recordingId);

        assertThat(retry.alreadyRequested()).isFalse();
        assertThat(balanceOf(userId)).isEqualTo(500);
        assertThat(imageStatus(analysisId, SpeakerRole.SELF)).isEqualTo(CharacterImageStatus.COMPLETED);
        assertThat(imageStatus(analysisId, SpeakerRole.FRIEND)).isEqualTo(CharacterImageStatus.PENDING);
        assertThat(usagesOf(recordingId)).extracting(CreditUsage::getStatus)
                .containsExactly(UsageStatus.REFUNDED, UsageStatus.USED);
        assertLedgerMatchesBalance(userId);
    }

    // 서버가 재시작되면 생성 작업이 사라져 이미지가 PENDING · GENERATING에 남고 실패 처리도 일어나지 않는다
    @Test
    void reconcilerRefundsSetStalledByRestartAndRestoresFreeChance() {
        Long userId = userWithCredits(0);
        Long recordingId = completedAnalysis(userId);
        characterImageRequestService.request(userId, recordingId);
        characterImageService.begin(analysisIdOf(recordingId), SpeakerRole.SELF);
        assertThat(freeChanceAvailable(userId)).isFalse();

        assertThat(creditRefundReconciler.refundStalledCharacters(LocalDateTime.now())).isZero();
        int refunded = creditRefundReconciler.refundStalledCharacters(LocalDateTime.now().plusMinutes(20));

        assertThat(refunded).isEqualTo(1);
        assertThat(freeChanceAvailable(userId)).isTrue();
        CreditUsage usage = usagesOf(recordingId).get(0);
        assertThat(usage.getStatus()).isEqualTo(UsageStatus.REFUNDED);
        assertThat(usage.getRefundReason()).isEqualTo("RECONCILED");
        assertThat(creditRefundReconciler.refundStalledCharacters(LocalDateTime.now().plusMinutes(20))).isZero();

        // 멈춘 이미지가 실패로 바뀌지 않은 채라 다시 요청해도 생성이 시작되지 않는다. 그 경우에도 차감하지 않는다
        assertThat(characterImageRequestService.request(userId, recordingId).alreadyRequested()).isTrue();
        assertThat(usagesOf(recordingId)).hasSize(1);
        assertThat(freeChanceAvailable(userId)).isTrue();
        assertLedgerMatchesBalance(userId);
    }

    // 스케줄러가 부르는 진입점도 캐릭터 정리를 함께 돈다
    @Test
    void scheduledReconcileAlsoRefundsStalledCharacters() {
        Long userId = userWithCredits(500);
        useFreeChance(userId);
        Long recordingId = completedAnalysis(userId);
        characterImageRequestService.request(userId, recordingId);
        backdate(recordingId, 30);

        creditRefundReconciler.reconcile();

        assertThat(balanceOf(userId)).isEqualTo(500);
        assertThat(usagesOf(recordingId)).extracting(CreditUsage::getStatus).containsExactly(UsageStatus.REFUNDED);
    }

    @Test
    void reconcilerRefundsFailedSetWhoseRefundWasMissed() {
        Long userId = userWithCredits(500);
        useFreeChance(userId);
        Long recordingId = completedAnalysis(userId);
        characterImageRequestService.request(userId, recordingId);
        Long analysisId = analysisIdOf(recordingId);
        complete(analysisId, SpeakerRole.SELF);
        // 실패는 기록됐지만 환급 호출이 실패한 상황 (환급하지 않는다)
        fail(analysisId, SpeakerRole.FRIEND);
        assertThat(balanceOf(userId)).isZero();

        int refunded = creditRefundReconciler.refundStalledCharacters(LocalDateTime.now().plusMinutes(20));

        assertThat(refunded).isEqualTo(1);
        assertThat(balanceOf(userId)).isEqualTo(500);
        assertThat(ledgerRepository.findByUserIdOrderByIdAsc(userId))
                .extracting(CreditLedgerEntry::getType)
                .containsExactly(CreditLedgerType.ADJUST, CreditLedgerType.USE, CreditLedgerType.USE,
                        CreditLedgerType.USE_REFUND);
        assertLedgerMatchesBalance(userId);
    }

    @Test
    void reconcilerLeavesCompletedSetsAndRecentRequestsAlone() {
        Long userId = userWithCredits(500);
        Long completed = completedAnalysis(userId);
        characterImageRequestService.request(userId, completed);
        complete(analysisIdOf(completed), SpeakerRole.SELF);
        complete(analysisIdOf(completed), SpeakerRole.FRIEND);
        Long recent = completedAnalysis(userId);
        characterImageRequestService.request(userId, recent);

        assertThat(creditRefundReconciler.refundStalledCharacters(LocalDateTime.now())).isZero();
        // 완료된 건은 시간이 지나도 환급하지 않는다
        assertThat(creditRefundReconciler.refundStalledCharacters(LocalDateTime.now().plusMinutes(20)))
                .isEqualTo(1);
        assertThat(activeUsage(completed).getStatus()).isEqualTo(UsageStatus.USED);
        assertThat(usagesOf(recent)).extracting(CreditUsage::getStatus).containsExactly(UsageStatus.REFUNDED);
        assertThat(balanceOf(userId)).isEqualTo(500);
    }

    // 요청한 지 오래됐어도 이미지가 하나씩 만들어지고 있으면(대기열에서 늦게 시작한 경우 등) 기다린다
    @Test
    void reconcilerWaitsWhileImagesAreStillProgressing() {
        Long userId = userWithCredits(500);
        useFreeChance(userId);
        Long recordingId = completedAnalysis(userId);
        characterImageRequestService.request(userId, recordingId);
        Long analysisId = analysisIdOf(recordingId);
        complete(analysisId, SpeakerRole.SELF);
        backdate(recordingId, 30);
        characterImageService.begin(analysisId, SpeakerRole.FRIEND);

        assertThat(creditRefundReconciler.refundStalledCharacters(LocalDateTime.now())).isZero();
        assertThat(balanceOf(userId)).isZero();

        // 그 뒤로도 진행이 없으면 멈춘 것으로 본다
        assertThat(creditRefundReconciler.refundStalledCharacters(LocalDateTime.now().plusMinutes(20)))
                .isEqualTo(1);
        assertThat(balanceOf(userId)).isEqualTo(500);
    }

    // 환급이 누락된 실패 건을 다시 요청하면 기존 사용 건으로(추가 차감 없이) 다시 생성한다.
    // 그 사용 건은 오래전에 만들어졌지만 생성이 다시 진행 중이므로 기다린다
    @Test
    void reconcilerWaitsForRetryThatReusesUnrefundedUsage() {
        Long userId = userWithCredits(500);
        useFreeChance(userId);
        Long recordingId = completedAnalysis(userId);
        characterImageRequestService.request(userId, recordingId);
        Long analysisId = analysisIdOf(recordingId);
        fail(analysisId, SpeakerRole.SELF);
        fail(analysisId, SpeakerRole.FRIEND);
        backdate(recordingId, 30);

        CharacterImageRequestResponseDto retry = characterImageRequestService.request(userId, recordingId);

        assertThat(retry.alreadyRequested()).isFalse();
        assertThat(usagesOf(recordingId)).hasSize(1);
        assertThat(balanceOf(userId)).isZero();
        assertThat(creditRefundReconciler.refundStalledCharacters(LocalDateTime.now())).isZero();
        assertThat(creditRefundReconciler.refundStalledCharacters(LocalDateTime.now().plusMinutes(20)))
                .isEqualTo(1);
        assertThat(balanceOf(userId)).isEqualTo(500);
    }

    // 분석을 지우면 이미지도 지워져 결과를 받았는지 알 수 없다. 완료된 캐릭터를 받은 뒤 지운 건을 환급하지 않도록 대상에서 뺀다
    @Test
    void reconcilerDoesNotRefundAfterAnalysisIsDeleted() {
        Long userId = userWithCredits(500);
        useFreeChance(userId);
        Long recordingId = completedAnalysis(userId);
        characterImageRequestService.request(userId, recordingId);
        complete(analysisIdOf(recordingId), SpeakerRole.SELF);
        complete(analysisIdOf(recordingId), SpeakerRole.FRIEND);

        analysisDeletionService.delete(recordingId, userId);

        assertThat(creditRefundReconciler.refundStalledCharacters(LocalDateTime.now().plusMinutes(20))).isZero();
        assertThat(activeUsage(recordingId).getStatus()).isEqualTo(UsageStatus.USED);
        assertThat(balanceOf(userId)).isZero();
    }

    // 실패한 이미지의 시각은 진행으로 보지 않는다. 멈춘 이미지를 나중에 실패로 바꾸는 처리가 생겨도 환급이 그만큼 늦어지지 않게
    @Test
    void reconcilerDoesNotTreatRecentlyFailedImagesAsProgress() {
        Long userId = userWithCredits(500);
        useFreeChance(userId);
        Long recordingId = completedAnalysis(userId);
        characterImageRequestService.request(userId, recordingId);
        backdate(recordingId, 30);
        Long analysisId = analysisIdOf(recordingId);
        fail(analysisId, SpeakerRole.SELF);
        fail(analysisId, SpeakerRole.FRIEND);

        assertThat(creditRefundReconciler.refundStalledCharacters(LocalDateTime.now())).isEqualTo(1);
        assertThat(balanceOf(userId)).isEqualTo(500);
    }

    // 요청한 지 기준 시간이 지나지 않은 사용 건은 이미지 시각과 무관하게 기다린다
    @Test
    void reconcilerWaitsUntilUsageItselfIsOldEnough() {
        Long userId = userWithCredits(500);
        useFreeChance(userId);
        Long recordingId = completedAnalysis(userId);
        characterImageRequestService.request(userId, recordingId);
        jdbcTemplate.update("update character_images set updated_at = ? where analysis_id = ?",
                LocalDateTime.now().minusMinutes(30), analysisIdOf(recordingId));

        assertThat(creditRefundReconciler.refundStalledCharacters(LocalDateTime.now())).isZero();
        assertThat(balanceOf(userId)).isZero();
    }

    // 정리 작업이 "멈췄다"고 조회한 건이 그사이 환급되고 사용자가 다시 요청했다면, 새 사용 건은 정상 생성 중이다.
    // 조회 결과가 그 사이에 낡은 상황을 만들기 위해 조회만 미리 받아 둔 결과로 바꾼다
    @Test
    void reconcilerDoesNotRefundNewUsageCreatedAfterItsQuery() {
        Long userId = userWithCredits(1000);
        useFreeChance(userId);
        Long recordingId = completedAnalysis(userId);
        characterImageRequestService.request(userId, recordingId);
        Long analysisId = analysisIdOf(recordingId);
        fail(analysisId, SpeakerRole.SELF);
        fail(analysisId, SpeakerRole.FRIEND);
        CreditUsage staleUsage = activeUsage(recordingId);
        creditService.refund(UsagePurpose.CHARACTER, recordingId, GENERATION_FAILED);
        characterImageRequestService.request(userId, recordingId);
        assertThat(balanceOf(userId)).isEqualTo(500);
        doReturn(List.of(staleUsage)).when(usageRepository).findStalledCharacterUsages(any());

        assertThat(creditRefundReconciler.refundStalledCharacters(LocalDateTime.now())).isZero();

        assertThat(activeUsage(recordingId).getId()).isNotEqualTo(staleUsage.getId());
        assertThat(balanceOf(userId)).isEqualTo(500);
        assertLedgerMatchesBalance(userId);
    }

    @Test
    void chargesOnceWhenSameCharacterRequestArrivesConcurrently() throws Exception {
        Long userId = userWithCredits(5000);
        useFreeChance(userId);
        Long recordingId = completedAnalysis(userId);

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CharacterImageRequestResponseDto>> futures = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        int accepted = 0;
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return characterImageRequestService.request(userId, recordingId);
                }));
            }
            start.countDown();
            for (Future<CharacterImageRequestResponseDto> future : futures) {
                try {
                    if (!future.get(30, TimeUnit.SECONDS).alreadyRequested()) {
                        accepted++;
                    }
                } catch (java.util.concurrent.ExecutionException exception) {
                    failures.add(exception.getCause());
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }

        // MySQL에서는 늦게 처리된 요청이 이미지 작업 유니크 제약에 걸려 실패한다 (먼저 한 조회가 옛 시점을 보기 때문.
        // 요청 서비스의 조회 순서 문제로 C 파트에 전달함). 그 경우에도 트랜잭션 전체가 되돌아가 차감은 한 번뿐이어야 한다
        assertThat(failures).allSatisfy(failure ->
                assertThat(failure).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class));
        assertThat(accepted).isEqualTo(1);
        assertThat(usagesOf(recordingId)).hasSize(1);
        assertThat(balanceOf(userId)).isEqualTo(4500);
        assertLedgerMatchesBalance(userId);
    }

    private Long userWithCredits(int credits) {
        long sequence = SEQUENCE.incrementAndGet();
        Long userId = userRepository.saveAndFlush(
                User.createLocal("character-credit-" + sequence + "@example.com", "encoded-password", "캐릭터" + sequence)
        ).getId();
        if (credits > 0) {
            creditService.adjust(userId, credits, "테스트 지급", ADMIN_ID);
        }
        return userId;
    }

    // 무료 기회를 쓴 요청은 생성까지 끝낸다 (정리 작업의 대상이 되지 않게)
    private void useFreeChance(Long userId) {
        Long recordingId = completedAnalysis(userId);
        characterImageRequestService.request(userId, recordingId);
        complete(analysisIdOf(recordingId), SpeakerRole.SELF);
        complete(analysisIdOf(recordingId), SpeakerRole.FRIEND);
        assertThat(freeChanceAvailable(userId)).isFalse();
    }

    private Long completedAnalysis(Long userId) {
        Long recordingId = recordingRepository.saveAndFlush(
                Recording.create(userId, "character-credit-" + SEQUENCE.incrementAndGet() + ".m4a", 600)).getId();
        analysisService.start(recordingId);
        analysisService.complete(recordingId, new AnalysisResultCommand(
                objectMapper.writeValueAsString(report(recordingId)),
                "liner-mark-1.1",
                "analysis-prompt-v9",
                "analysis-report-v8"
        ));
        return recordingId;
    }

    private AnalysisReport report(Long recordingId) {
        return new AnalysisReport(
                new AnalysisReport.RecordingInfo(
                        recordingId,
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
        return new QualitativeAnalysis.CharacterInsight(role, name, "대화에서 관찰된 캐릭터 설명", List.of(1L));
    }

    private void complete(Long analysisId, SpeakerRole role) {
        characterImageService.begin(analysisId, role);
        characterImageService.complete(analysisId, role, analysisId + "/" + role.name().toLowerCase() + ".png",
                new GeneratedCharacterImage(new byte[]{1, 2, 3}, "image/png", "gpt-image", "v1"));
    }

    private void fail(Long analysisId, SpeakerRole role) {
        characterImageService.begin(analysisId, role);
        characterImageService.fail(analysisId, role, "OpenAiImageException");
    }

    // 요청 · 이미지 진행 시각을 과거로 옮긴다 (정리 작업이 보는 시각)
    private void backdate(Long recordingId, int minutes) {
        LocalDateTime past = LocalDateTime.now().minusMinutes(minutes);
        jdbcTemplate.update("update credit_usages set created_at = ? where purpose = 'CHARACTER' and recording_id = ?",
                past, recordingId);
        jdbcTemplate.update("update character_images set updated_at = ? where analysis_id = ?",
                past, analysisIdOf(recordingId));
    }

    private Long analysisIdOf(Long recordingId) {
        return conversationAnalysisRepository.findByRecordingId(recordingId).orElseThrow().getId();
    }

    private List<CharacterImageStatus> imageStatuses(Long recordingId) {
        return conversationAnalysisRepository.findByRecordingId(recordingId)
                .map(analysis -> characterImageRepository.findAllByAnalysisId(analysis.getId()).stream()
                        .map(CharacterImage::getStatus)
                        .toList())
                .orElse(List.of());
    }

    private CharacterImageStatus imageStatus(Long analysisId, SpeakerRole role) {
        return characterImageRepository.findByAnalysisIdAndSpeakerRole(analysisId, role).orElseThrow().getStatus();
    }

    private CreditUsage activeUsage(Long recordingId) {
        return usageRepository.findByActiveKey(activeKey(recordingId)).orElseThrow();
    }

    private List<CreditUsage> usagesOf(Long recordingId) {
        return usageRepository.findAll().stream()
                .filter(usage -> usage.getPurpose() == UsagePurpose.CHARACTER)
                .filter(usage -> usage.getRecordingId().equals(recordingId))
                .sorted(java.util.Comparator.comparing(CreditUsage::getId))
                .toList();
    }

    private static String activeKey(Long recordingId) {
        return CreditUsage.activeKey(UsagePurpose.CHARACTER, recordingId);
    }

    private int balanceOf(Long userId) {
        return creditQueryService.getSummary(userId).balance();
    }

    private boolean freeChanceAvailable(Long userId) {
        return creditQueryService.getSummary(userId).characterFreeAvailable();
    }

    private void assertLedgerMatchesBalance(Long userId) {
        assertThat(ledgerRepository.sumAmountByUserId(userId)).isEqualTo(balanceOf(userId));
    }
}
