package com.example.resay.domain.transcription.service;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.recording.service.RecordingService;
import com.example.resay.global.exception.GeneralException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 결제 쪽이 결제 트랜잭션 안에서 completePayment를 호출하면, 커밋된 뒤에만 전사가 시작되는지 확인한다
@SpringBootTest
class PaymentTranscriptionStartIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private RecordingService recordingService;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // 실제 CLOVA 요청은 이 테스트의 범위가 아니다
    @MockitoBean
    private TranscriptionService transcriptionService;

    private Recording recording;

    @BeforeEach
    void setUp() {
        recording = Recording.create(USER_ID, "payment-test.m4a", 600);
        ReflectionTestUtils.setField(recording, "status", RecordingStatus.TYPE_SELECTED);
        recordingRepository.save(recording);
    }

    @AfterEach
    void tearDown() {
        recordingRepository.deleteById(recording.getId());
    }

    @Test
    void 결제가_커밋되면_전사를_시작한다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                recordingService.completePayment(recording.getId(), USER_ID));

        verify(transcriptionService).start(recording.getId());
        assertThat(recordingRepository.findById(recording.getId()).orElseThrow().getStatus())
                .isEqualTo(RecordingStatus.PAYMENT_COMPLETED);
    }

    @Test
    void 결제_요청이_동시에_두_번_들어와도_한_번만_결제된다() throws Exception {
        CountDownLatch firstLocked = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // 첫 번째 결제: 녹음을 잠근 채 크레딧 차감 등 결제 처리가 진행 중인 상황
            Future<?> first = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        recordingService.completePayment(recording.getId(), USER_ID);
                        firstLocked.countDown();
                        sleep(300);
                    }));
            firstLocked.await(5, TimeUnit.SECONDS);

            // 두 번째 결제: 첫 번째가 커밋될 때까지 기다린 뒤 이미 결제된 상태를 보고 거부된다
            GeneralException second = assertThrows(GeneralException.class, () ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                            recordingService.completePayment(recording.getId(), USER_ID)));
            first.get(5, TimeUnit.SECONDS);

            assertThat(second.getErrorCode()).isEqualTo(RecordingErrorCode.INVALID_STATUS_TRANSITION);
            verify(transcriptionService, times(1)).start(recording.getId());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 같은_트랜잭션에서_녹음을_먼저_읽어_뒀어도_이미_결제된_녹음은_다시_결제되지_않는다() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            GeneralException exception = assertThrows(GeneralException.class, () ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        // 결제 쪽이 가격을 정하려고 녹음을 먼저 읽어 둔 상황 (메모리에는 결제 전 상태가 남아 있음)
                        recordingRepository.findById(recording.getId()).orElseThrow();
                        // 그사이 다른 요청이 같은 녹음을 결제하고 커밋
                        runInOtherTransaction(executor);
                        recordingService.completePayment(recording.getId(), USER_ID);
                    }));

            assertThat(exception.getErrorCode()).isEqualTo(RecordingErrorCode.INVALID_STATUS_TRANSITION);
            verify(transcriptionService, times(1)).start(recording.getId());
        } finally {
            executor.shutdownNow();
        }
    }

    private void runInOtherTransaction(ExecutorService executor) {
        try {
            executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    recordingService.completePayment(recording.getId(), USER_ID))).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 결제가_롤백되면_전사를_시작하지_않는다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            recordingService.completePayment(recording.getId(), USER_ID);
            status.setRollbackOnly(); // 결제 쪽 처리 실패
        });

        verify(transcriptionService, never()).start(anyLong());
        assertThat(recordingRepository.findById(recording.getId()).orElseThrow().getStatus())
                .isEqualTo(RecordingStatus.TYPE_SELECTED);
    }

    @Test
    void 결제가_먼저_끝나면_결제_전_녹음_자동_삭제는_건너뛴다() throws Exception {
        CountDownLatch paymentLocked = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> payment = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        recordingService.completePayment(recording.getId(), USER_ID);
                        paymentLocked.countDown();
                        sleep(300);
                    }));
            paymentLocked.await(5, TimeUnit.SECONDS);

            // 결제가 커밋될 때까지 기다렸다가 결제된 상태를 보고 지우지 않는다
            boolean deleted = recordingService.deleteAbandoned(recording.getId());
            payment.get(5, TimeUnit.SECONDS);

            assertThat(deleted).isFalse();
            assertThat(recordingRepository.findById(recording.getId())).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
