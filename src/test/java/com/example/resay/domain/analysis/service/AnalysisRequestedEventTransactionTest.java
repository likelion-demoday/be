package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.event.AnalysisRequestedEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
class AnalysisRequestedEventTransactionTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private AnalysisProcessor analysisProcessor;

    @Test
    void startsAnalysisAfterCommit() throws Exception {
        CountDownLatch invoked = new CountDownLatch(1);
        doAnswer(invocation -> {
            invoked.countDown();
            return null;
        }).when(analysisProcessor).process(1L);

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(new AnalysisRequestedEvent(1L))
        );

        assertThat(invoked.await(3, TimeUnit.SECONDS)).isTrue();
        verify(analysisProcessor).process(1L);
    }

    @Test
    void doesNotStartAnalysisAfterRollback() throws Exception {
        CountDownLatch invoked = new CountDownLatch(1);
        doAnswer(invocation -> {
            invoked.countDown();
            return null;
        }).when(analysisProcessor).process(anyLong());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new AnalysisRequestedEvent(2L));
            status.setRollbackOnly();
        });

        assertThat(invoked.await(500, TimeUnit.MILLISECONDS)).isFalse();
        verify(analysisProcessor, never()).process(2L);
    }
}
