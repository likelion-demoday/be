package com.example.resay.domain.recording.repository;

import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.entity.RelationshipType;
import com.example.resay.global.config.JpaAuditingConfig; // createdAt 자동 채우기 기능을 테스트에서도 켜기 위해 필요
import com.example.resay.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest; // Spring Boot 4 기준 새 패키지 경로 (지난번 PR #15에서 배운 그 변경사항)
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest // JPA 관련 빈들만 가볍게 띄워서 실제 DB(H2)에 저장/조회를 테스트할 수 있게 해주는 어노테이션
@Import(JpaAuditingConfig.class) // 이걸 안 넣으면 createdAt이 계속 null로 나옴 -- User 엔티티 테스트 때 배웠던 그 규칙
class RecordingRepositoryTest {

    @org.springframework.beans.factory.annotation.Autowired
    private RecordingRepository recordingRepository; // 진짜 DB에 접근하는 진짜 Repository를 스프링이 주입해줌

    @Test
    void selectType_성공시_제목과_상태가_바뀐다() {
        Recording recording = Recording.create(1L, "/storage/test.mp3", 600); // 아직 메모리에만 있는 객체
        recordingRepository.save(recording); // 실제로 저장 -- 이 순간 JPA Auditing이 createdAt을 채워줌

        recording.selectType(RelationshipType.COUPLE_DAILY); // 이제 getCreatedAt()이 null이 아니라서 안전하게 실행됨

        assertThat(recording.getStatus()).isEqualTo(RecordingStatus.TYPE_SELECTED);
        assertThat(recording.getRelationshipType()).isEqualTo(RelationshipType.COUPLE_DAILY);
        assertThat(recording.getTitle()).startsWith("연인과의 대화"); // 실제로 Swagger에서 확인했던 그 결과와 동일한지
    }

    @Test
    void selectType_이미_선택된_상태면_예외() {
        Recording recording = Recording.create(1L, "/storage/test.mp3", 600);
        recordingRepository.save(recording);
        recording.selectType(RelationshipType.COUPLE_DAILY); // 한 번은 성공시켜서 TYPE_SELECTED로 만들어둠

        assertThrows(GeneralException.class,
                () -> recording.selectType(RelationshipType.FRIEND_DAILY)); // 그 상태에서 또 시도하면 막혀야 함 -- 오늘 Swagger로 직접 확인했던 그 케이스
    }

    @Test
    void findAudioExpiredIds_완료_실패_후_기한이_지나고_음성이_남은_녹음만_조회된다() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(3);
        Long 완료_만료 = saveWith(RecordingStatus.COMPLETED, "completedAt", cutoff.minusMinutes(1));
        Long 실패_만료 = saveWith(RecordingStatus.FAILED, "failedAt", cutoff.minusMinutes(1));
        saveWith(RecordingStatus.COMPLETED, "completedAt", cutoff.plusMinutes(1)); // 아직 기한 전
        Long 이미_삭제 = saveWith(RecordingStatus.COMPLETED, "completedAt", cutoff.minusDays(1));
        recordingRepository.findById(이미_삭제).orElseThrow().markAudioDeleted(); // 음성은 이미 지움
        saveWith(RecordingStatus.ANALYZING, "completedAt", cutoff.minusDays(1)); // 아직 분석 중

        List<Long> ids = recordingRepository.findAudioExpiredIds(cutoff);

        assertThat(ids).containsExactlyInAnyOrder(완료_만료, 실패_만료);
    }

    @Test
    void findIdsByStatusInAndCreatedAtBefore_결제_전_상태이고_업로드가_오래된_녹음만_조회된다() {
        Recording 업로드만 = recordingRepository.save(Recording.create(1L, "/storage/a.mp3", 600));
        Recording 결제완료 = recordingRepository.save(Recording.create(1L, "/storage/b.mp3", 600));
        ReflectionTestUtils.setField(결제완료, "status", RecordingStatus.PAYMENT_COMPLETED);
        recordingRepository.flush();

        // 방금 저장한 녹음은 createdAt이 현재 시각이므로, 기준 시각을 미래로 잡아 "오래된 녹음"처럼 조회한다
        List<Long> 기한_지남 = recordingRepository.findIdsByStatusInAndCreatedAtBefore(
                List.of(RecordingStatus.UPLOADED, RecordingStatus.TYPE_SELECTED), LocalDateTime.now().plusMinutes(1));
        List<Long> 기한_전 = recordingRepository.findIdsByStatusInAndCreatedAtBefore(
                List.of(RecordingStatus.UPLOADED, RecordingStatus.TYPE_SELECTED), LocalDateTime.now().minusHours(3));

        assertThat(기한_지남).containsExactly(업로드만.getId());
        assertThat(기한_전).isEmpty();
    }

    private Long saveWith(RecordingStatus status, String timeField, LocalDateTime time) {
        Recording recording = Recording.create(1L, "/storage/test.mp3", 600);
        ReflectionTestUtils.setField(recording, "status", status);
        ReflectionTestUtils.setField(recording, timeField, time);
        return recordingRepository.saveAndFlush(recording).getId();
    }
}