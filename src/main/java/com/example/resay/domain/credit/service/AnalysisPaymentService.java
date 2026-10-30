package com.example.resay.domain.credit.service;

import com.example.resay.domain.credit.dto.AnalysisPaymentResponseDto;
import com.example.resay.domain.credit.entity.CreditUsage;
import com.example.resay.domain.credit.entity.UsagePurpose;
import com.example.resay.domain.credit.model.AnalysisCategory;
import com.example.resay.domain.credit.model.UsageResult;
import com.example.resay.domain.credit.repository.CreditUsageRepository;
import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.entity.RelationshipType;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.recording.service.RecordingService;
import com.example.resay.global.exception.GeneralException;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 분석 결제: 크레딧을 차감하고 녹음을 "결제 완료"로 바꾼다. 결제가 커밋되면 전사가 자동으로 시작된다(전사 쪽 담당).
 */
@Service
@RequiredArgsConstructor
public class AnalysisPaymentService {

    // 결제가 끝나 처리 중이거나 처리가 끝난 상태
    private static final Set<RecordingStatus> PAID_STATUSES = EnumSet.of(
            RecordingStatus.PAYMENT_COMPLETED,
            RecordingStatus.TRANSCRIBING,
            RecordingStatus.ANALYZING,
            RecordingStatus.COMPLETED
    );

    private final RecordingRepository recordingRepository;
    private final RecordingService recordingService;
    private final CreditService creditService;
    private final CreditQueryService creditQueryService;
    private final CreditUsageRepository creditUsageRepository;
    private final TransactionTemplate transactionTemplate;

    public AnalysisPaymentResponseDto pay(Long userId, Long recordingId) {
        // 녹음 확인은 결제 트랜잭션을 열기 전에 한다.
        // 결제 트랜잭션은 크레딧 차감(지갑 잠금)으로 시작해야 하기 때문이다 (이유는 CreditJdbcRepository 설명 참고)
        Recording recording = recordingRepository.findByIdAndUserId(recordingId, userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new GeneralException(RecordingErrorCode.RECORDING_NOT_FOUND));

        if (PAID_STATUSES.contains(recording.getStatus())) {
            return alreadyPaid(userId, recordingId);
        }
        // 유형 선택 전(UPLOADED)이거나 실패한 녹음(FAILED). 실패한 녹음은 다시 올려야 한다
        if (recording.getStatus() != RecordingStatus.TYPE_SELECTED) {
            throw new GeneralException(RecordingErrorCode.INVALID_STATUS_TRANSITION);
        }
        AnalysisCategory category = categoryOf(recording.getRelationshipType());

        // 차감과 결제 완료 전환은 함께 성공하거나 함께 취소된다.
        // 그사이 녹음 상태가 바뀌었다면 completePayment가 예외를 던지고 차감도 되돌아간다
        return transactionTemplate.execute(status -> {
            UsageResult usage = creditService.useForAnalysis(userId, recordingId, category);
            if (!usage.alreadyUsed()) {
                recordingService.completePayment(recordingId, userId);
            }
            return new AnalysisPaymentResponseDto(recordingId, usage.amount(), usage.balance(), usage.alreadyUsed());
        });
    }

    private AnalysisPaymentResponseDto alreadyPaid(Long userId, Long recordingId) {
        int usedCredits = creditUsageRepository
                .findByActiveKey(CreditUsage.activeKey(UsagePurpose.ANALYSIS, recordingId))
                .map(CreditUsage::getAmount)
                .orElse(0);
        return new AnalysisPaymentResponseDto(
                recordingId, usedCredits, creditQueryService.getSummary(userId).balance(), true);
    }

    // 가격은 관계(친구 · 연인 · 가족)와 무관하게 일상인지 갈등인지로만 갈린다
    private AnalysisCategory categoryOf(RelationshipType relationshipType) {
        if (relationshipType == null) {
            throw new GeneralException(RecordingErrorCode.INVALID_STATUS_TRANSITION);
        }
        return switch (relationshipType) {
            case FRIEND_DAILY, COUPLE_DAILY -> AnalysisCategory.DAILY;
            case COUPLE_CONFLICT, PARENT_CHILD_CONFLICT -> AnalysisCategory.CONFLICT;
        };
    }
}
