package com.example.resay.domain.credit.service;

import com.example.resay.domain.credit.dto.AnalysisPaymentResponseDto;
import com.example.resay.domain.credit.entity.CreditLedgerEntry;
import com.example.resay.domain.credit.entity.CreditLedgerType;
import com.example.resay.domain.credit.entity.CreditUsage;
import com.example.resay.domain.credit.entity.UsageStatus;
import com.example.resay.domain.credit.repository.CreditLedgerRepository;
import com.example.resay.domain.credit.repository.CreditUsageRepository;
import com.example.resay.domain.credit.repository.CreditWalletRepository;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingFailureReason;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.entity.RelationshipType;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.recording.service.RecordingService;
import com.example.resay.domain.transcription.service.TranscriptionStartListener;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 결제 → 실패 → 환급이 실제 커밋을 거쳐 이어지는지 확인한다.
// 결제가 커밋되면 전사가 시작되는데, 여기서는 외부 호출이 나가지 않게 그 시작점만 막아 둔다
@SpringBootTest
class AnalysisPaymentFlowTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final Long ADMIN_ID = 999_999L;

    @MockitoBean
    private TranscriptionStartListener transcriptionStartListener;

    @Autowired
    private AnalysisPaymentService analysisPaymentService;

    @Autowired
    private CreditRefundReconciler creditRefundReconciler;

    @Autowired
    private CreditService creditService;

    @Autowired
    private CreditQueryService creditQueryService;

    @Autowired
    private RecordingService recordingService;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreditWalletRepository walletRepository;

    @Autowired
    private CreditUsageRepository usageRepository;

    @Autowired
    private CreditLedgerRepository ledgerRepository;

    @AfterEach
    void cleanUp() {
        ledgerRepository.deleteAllInBatch();
        usageRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        recordingRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void chargesOnceWhenPaymentIsRequestedConcurrently() throws Exception {
        Long userId = userWithCredits(5000);
        Long recordingId = recordingWithType(userId, RelationshipType.COUPLE_CONFLICT);

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AnalysisPaymentResponseDto>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return analysisPaymentService.pay(userId, recordingId);
                    } catch (GeneralException exception) {
                        // 동시에 처리 중이던 요청은 409로 거절될 수 있다. 그 밖의 실패는 없어야 한다
                        assertThat(exception.getErrorCode().getCode()).isEqualTo("CREDIT409_1");
                        return null;
                    }
                }));
            }
            start.countDown();

            int charged = 0;
            for (Future<AnalysisPaymentResponseDto> future : futures) {
                AnalysisPaymentResponseDto response = future.get(30, TimeUnit.SECONDS);
                if (response != null && !response.alreadyPaid()) {
                    charged++;
                }
            }
            assertThat(charged).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }

        assertThat(balanceOf(userId)).isEqualTo(3000);
        assertThat(statusOf(recordingId)).isEqualTo(RecordingStatus.PAYMENT_COMPLETED);
        assertThat(usageRepository.count()).isEqualTo(1);
        assertLedgerMatchesBalance(userId);
    }

    @Test
    void refundsWhenTranscriptionFails() {
        Long userId = userWithCredits(5000);
        Long recordingId = recordingWithType(userId, RelationshipType.FRIEND_DAILY);
        analysisPaymentService.pay(userId, recordingId);
        recordingService.startTranscribing(recordingId);
        assertThat(balanceOf(userId)).isEqualTo(3500);

        recordingService.failTranscription(recordingId, RecordingFailureReason.TRANSCRIPTION_FAILED);

        assertThat(balanceOf(userId)).isEqualTo(5000);
        CreditUsage usage = usageRepository.findAll().get(0);
        assertThat(usage.getStatus()).isEqualTo(UsageStatus.REFUNDED);
        assertThat(usage.getRefundReason()).isEqualTo("TRANSCRIPTION_FAILED");
        assertThat(ledgerRepository.findByUserIdOrderByIdAsc(userId))
                .extracting(CreditLedgerEntry::getType)
                .containsExactly(CreditLedgerType.ADJUST, CreditLedgerType.USE, CreditLedgerType.USE_REFUND);
        assertLedgerMatchesBalance(userId);
    }

    // 사용자가 화자를 고르지 않아 기한이 지난 경우도 사유와 무관하게 환급한다
    @Test
    void refundsWhenSpeakerSelectionExpires() {
        Long userId = userWithCredits(2000);
        Long recordingId = recordingWithType(userId, RelationshipType.PARENT_CHILD_CONFLICT);
        analysisPaymentService.pay(userId, recordingId);
        recordingService.startTranscribing(recordingId);

        recordingService.failTranscription(recordingId, RecordingFailureReason.SPEAKER_SELECTION_EXPIRED);

        assertThat(balanceOf(userId)).isEqualTo(2000);
        assertThat(usageRepository.findAll().get(0).getRefundReason()).isEqualTo("SPEAKER_SELECTION_EXPIRED");
    }

    @Test
    void refundsWhenAnalysisFails() {
        Long userId = userWithCredits(2000);
        Long recordingId = recordingWithType(userId, RelationshipType.COUPLE_CONFLICT);
        analysisPaymentService.pay(userId, recordingId);
        setStatus(recordingId, RecordingStatus.ANALYZING);
        assertThat(balanceOf(userId)).isZero();

        recordingService.failAnalysis(recordingId, RecordingFailureReason.ANALYSIS_FAILED);

        assertThat(balanceOf(userId)).isEqualTo(2000);
        assertLedgerMatchesBalance(userId);
    }

    @Test
    void doesNotRefundWhenAnalysisCompletes() {
        Long userId = userWithCredits(2000);
        Long recordingId = recordingWithType(userId, RelationshipType.COUPLE_CONFLICT);
        analysisPaymentService.pay(userId, recordingId);
        setStatus(recordingId, RecordingStatus.ANALYZING);

        recordingService.completeAnalysis(recordingId);

        assertThat(balanceOf(userId)).isZero();
        assertThat(usageRepository.findAll().get(0).getStatus()).isEqualTo(UsageStatus.USED);
    }

    // 환급된 실패 녹음은 다시 결제할 수 없다 (새로 올려야 한다). 환급받은 크레딧으로 다른 녹음은 결제할 수 있다
    @Test
    void failedRecordingCannotBePaidAgainButRefundedCreditsCanBeReused() {
        Long userId = userWithCredits(1500);
        Long failedRecordingId = recordingWithType(userId, RelationshipType.FRIEND_DAILY);
        analysisPaymentService.pay(userId, failedRecordingId);
        recordingService.startTranscribing(failedRecordingId);
        recordingService.failTranscription(failedRecordingId, RecordingFailureReason.TRANSCRIPTION_TIMEOUT);

        assertThatThrownBy(() -> analysisPaymentService.pay(userId, failedRecordingId))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode().getCode()).isEqualTo("RECORDING400_2"));

        Long newRecordingId = recordingWithType(userId, RelationshipType.FRIEND_DAILY);
        assertThat(analysisPaymentService.pay(userId, newRecordingId).alreadyPaid()).isFalse();
        assertThat(balanceOf(userId)).isZero();
        assertLedgerMatchesBalance(userId);
    }

    // 실패 이벤트로 환급되지 못한 건(그 순간 서버가 꺼진 경우 등)은 정리 작업이 찾아서 환급한다
    @Test
    void reconcilerRefundsFailedRecordingThatMissedRefund() {
        Long userId = userWithCredits(5000);
        Long recordingId = recordingWithType(userId, RelationshipType.FRIEND_DAILY);
        analysisPaymentService.pay(userId, recordingId);
        // 이벤트 없이 실패 상태만 남은 상황을 만든다
        setStatus(recordingId, RecordingStatus.FAILED);
        assertThat(balanceOf(userId)).isEqualTo(3500);

        int refunded = creditRefundReconciler.refundFailedAnalyses(LocalDateTime.now().plusMinutes(10));

        assertThat(refunded).isEqualTo(1);
        assertThat(balanceOf(userId)).isEqualTo(5000);
        assertThat(usageRepository.findAll().get(0).getRefundReason()).isEqualTo("RECONCILED");
        assertThat(creditRefundReconciler.refundFailedAnalyses(LocalDateTime.now().plusMinutes(10))).isZero();
        assertLedgerMatchesBalance(userId);
    }

    @Test
    void reconcilerLeavesInProgressCompletedAndRecentPaymentsAlone() {
        Long userId = userWithCredits(10_000);
        Long inProgress = recordingWithType(userId, RelationshipType.FRIEND_DAILY);
        Long completed = recordingWithType(userId, RelationshipType.FRIEND_DAILY);
        Long justFailed = recordingWithType(userId, RelationshipType.FRIEND_DAILY);
        analysisPaymentService.pay(userId, inProgress);
        analysisPaymentService.pay(userId, completed);
        analysisPaymentService.pay(userId, justFailed);
        setStatus(completed, RecordingStatus.COMPLETED);
        setStatus(justFailed, RecordingStatus.FAILED);

        // 방금 실패한 건은 이벤트 환급이 진행 중일 수 있어 아직 건드리지 않는다
        assertThat(creditRefundReconciler.refundFailedAnalyses(LocalDateTime.now())).isZero();
        assertThat(balanceOf(userId)).isEqualTo(5500);

        assertThat(creditRefundReconciler.refundFailedAnalyses(LocalDateTime.now().plusMinutes(10))).isEqualTo(1);
        assertThat(balanceOf(userId)).isEqualTo(7000);
    }

    private Long userWithCredits(int credits) {
        long sequence = SEQUENCE.incrementAndGet();
        Long userId = userRepository.saveAndFlush(
                User.createLocal("pay-flow-" + sequence + "@example.com", "encoded-password", "결제" + sequence)
        ).getId();
        creditService.adjust(userId, credits, "테스트 지급", ADMIN_ID);
        return userId;
    }

    private Long recordingWithType(Long userId, RelationshipType type) {
        Long recordingId = recordingRepository.saveAndFlush(
                Recording.create(userId, "pay-flow-" + SEQUENCE.incrementAndGet() + ".m4a", 600)).getId();
        recordingService.selectType(recordingId, userId, type);
        return recordingId;
    }

    private void setStatus(Long recordingId, RecordingStatus status) {
        Recording recording = recordingRepository.findById(recordingId).orElseThrow();
        ReflectionTestUtils.setField(recording, "status", status);
        recordingRepository.saveAndFlush(recording);
    }

    private RecordingStatus statusOf(Long recordingId) {
        return recordingRepository.findById(recordingId).orElseThrow().getStatus();
    }

    private int balanceOf(Long userId) {
        return creditQueryService.getSummary(userId).balance();
    }

    private void assertLedgerMatchesBalance(Long userId) {
        assertThat(ledgerRepository.sumAmountByUserId(userId)).isEqualTo(balanceOf(userId));
    }
}
