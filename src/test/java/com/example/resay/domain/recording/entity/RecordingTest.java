package com.example.resay.domain.recording.entity;

import com.example.resay.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat; // 값 비교를 읽기 좋게 해주는 AssertJ 라이브러리
import static org.junit.jupiter.api.Assertions.assertThrows; // 예외가 던져지는지 확인하는 JUnit 메서드

class RecordingTest {

    @Test // 이 메서드가 테스트 케이스 하나라는 표시
    void create_성공() { // 메서드 이름 자체를 "무엇을 검증하는지" 설명으로 씀 -- 흔한 테스트 네이밍 관례
        Recording recording = Recording.create(1L, "/storage/test.mp3", 600); // 정상적인 값으로 생성 시도

        assertThat(recording.getUserId()).isEqualTo(1L); // userId가 제대로 들어갔는지
        assertThat(recording.getAudioFilePath()).isEqualTo("/storage/test.mp3"); // 파일경로도 제대로 들어갔는지
        assertThat(recording.getStatus()).isEqualTo(RecordingStatus.UPLOADED); // 생성 직후 상태가 UPLOADED로 고정되는지
        assertThat(recording.getTitle()).endsWith("녹음"); // 임시 제목이 "...녹음" 형태로 끝나는지
        assertThat(recording.getDurationSeconds()).isEqualTo(600); // 재생시간(초)이 저장되는지
    }

    @Test
    void create_재생시간이_없거나_0이하면_예외() {
        assertThrows(GeneralException.class,
                () -> Recording.create(1L, "/storage/test.mp3", null));
        assertThrows(GeneralException.class,
                () -> Recording.create(1L, "/storage/test.mp3", 0));
    }

    @Test
    void create_userId가_null이면_예외() {
        assertThrows(GeneralException.class, // "이 코드를 실행하면 GeneralException이 던져져야 한다"는 뜻
                () -> Recording.create(null, "/storage/test.mp3", 600)); // userId 자리에 일부러 null을 넣어봄
    }

    @Test
    void create_audioFilePath가_null이면_예외() {
        assertThrows(GeneralException.class,
                () -> Recording.create(1L, null, 600)); // audioFilePath 자리에 null
    }

    @Test
    void create_audioFilePath가_공백이면_예외() {
        assertThrows(GeneralException.class,
                () -> Recording.create(1L, "   ", 600)); // 공백만 있는 문자열도 막히는지 확인
    }

    @Test
    void selectType_relationshipType이_null이면_예외() {
        Recording recording = Recording.create(1L, "/storage/test.mp3", 600); // 정상 생성 (아직 UPLOADED 상태)

        assertThrows(GeneralException.class,
                () -> recording.selectType(null)); // null 체크가 상태 체크 다음, getCreatedAt() 쓰기 전에 걸려서 순수 단위 테스트로도 검증 가능
    }

    @Test
    void startTranscribing_결제완료_상태면_TRANSCRIBING으로_전이() {
        Recording recording = recordingWithStatus(RecordingStatus.PAYMENT_COMPLETED); // 결제 기능이 아직 없어 상태를 직접 지정

        recording.startTranscribing();

        assertThat(recording.getStatus()).isEqualTo(RecordingStatus.TRANSCRIBING);
    }

    @Test
    void startTranscribing_결제완료가_아니면_예외() {
        Recording recording = recordingWithStatus(RecordingStatus.TYPE_SELECTED);

        assertThrows(GeneralException.class, recording::startTranscribing);
        assertThat(recording.getStatus()).isEqualTo(RecordingStatus.TYPE_SELECTED); // 실패해도 상태는 그대로
    }

    @Test
    void fail_진행중이면_FAILED로_전이() {
        Recording recording = recordingWithStatus(RecordingStatus.TRANSCRIBING);

        recording.fail(RecordingFailureReason.SPEAKER_NOT_SEPARATED);

        assertThat(recording.getStatus()).isEqualTo(RecordingStatus.FAILED);
        assertThat(recording.getFailureReason()).isEqualTo(RecordingFailureReason.SPEAKER_NOT_SEPARATED); // 프론트 안내 문구용
    }

    @Test
    void fail_이미_완료된_녹음이면_예외() {
        Recording recording = recordingWithStatus(RecordingStatus.COMPLETED);

        assertThrows(GeneralException.class, () -> recording.fail(RecordingFailureReason.TRANSCRIPTION_FAILED));
    }

    @Test
    void fail_실패_사유가_없으면_예외() {
        Recording recording = recordingWithStatus(RecordingStatus.TRANSCRIBING);

        assertThrows(GeneralException.class, () -> recording.fail(null));
        assertThat(recording.getStatus()).isEqualTo(RecordingStatus.TRANSCRIBING);
    }

    @Test
    void complete_분석_중이면_완료로_전이하고_완료_시각을_기록() {
        Recording recording = recordingWithStatus(RecordingStatus.ANALYZING);

        recording.complete();

        assertThat(recording.getStatus()).isEqualTo(RecordingStatus.COMPLETED);
        assertThat(recording.getCompletedAt()).isNotNull(); // 3일 보관 기간의 기준 시각
    }

    @Test
    void complete_분석_중이_아니면_예외() {
        Recording recording = recordingWithStatus(RecordingStatus.TRANSCRIBING);

        assertThrows(GeneralException.class, recording::complete);
        assertThat(recording.getCompletedAt()).isNull();
    }

    @Test
    void fail_실패_시각을_기록() {
        Recording recording = recordingWithStatus(RecordingStatus.TRANSCRIBING);

        recording.fail(RecordingFailureReason.TRANSCRIPTION_TIMEOUT);

        assertThat(recording.getFailedAt()).isNotNull(); // 실패 후 3일 보관 기간의 기준 시각
    }

    @Test
    void markDeleted_분석이_끝난_녹음만_숨긴다() {
        Recording completed = recordingWithStatus(RecordingStatus.COMPLETED);
        Recording failed = recordingWithStatus(RecordingStatus.FAILED);

        completed.markDeleted();
        failed.markDeleted();

        assertThat(completed.isDeleted()).isTrue();
        assertThat(failed.isDeleted()).isTrue();
        for (RecordingStatus inProgress : List.of(RecordingStatus.UPLOADED, RecordingStatus.TYPE_SELECTED,
                RecordingStatus.PAYMENT_COMPLETED, RecordingStatus.TRANSCRIBING, RecordingStatus.ANALYZING)) {
            Recording recording = recordingWithStatus(inProgress);
            assertThrows(GeneralException.class, recording::markDeleted);
            assertThat(recording.isDeleted()).isFalse();
        }
    }

    @Test
    void markAudioDeleted_음성_삭제_시각을_한번만_기록() {
        Recording recording = Recording.create(1L, "/storage/test.mp3", 600);
        assertThat(recording.hasAudio()).isTrue();

        recording.markAudioDeleted();
        var firstDeletedAt = recording.getAudioDeletedAt();
        recording.markAudioDeleted(); // 다시 호출해도 처음 시각을 유지

        assertThat(recording.hasAudio()).isFalse();
        assertThat(recording.getAudioDeletedAt()).isEqualTo(firstDeletedAt);
    }

    @Test
    void mapSpeakers_전사_중이면_화자를_저장하고_분석_중으로_전이() {
        Recording recording = recordingWithStatus(RecordingStatus.TRANSCRIBING);
        ReflectionTestUtils.setField(recording, "relationshipType", RelationshipType.FRIEND_DAILY);

        recording.mapSpeakers("2", " 호석 ", null);

        assertThat(recording.getStatus()).isEqualTo(RecordingStatus.ANALYZING);
        assertThat(recording.getSelfSpeakerLabel()).isEqualTo("2");
        assertThat(recording.getPartnerNickname()).isEqualTo("호석"); // 앞뒤 공백 제거
    }

    @Test
    void mapSpeakers_부모_자녀_대화는_본인_역할이_필요하고_다른_대화에서는_받지_않는다() {
        Recording parentChild = recordingWithStatus(RecordingStatus.TRANSCRIBING);
        ReflectionTestUtils.setField(parentChild, "relationshipType", RelationshipType.PARENT_CHILD_CONFLICT);
        Recording friend = recordingWithStatus(RecordingStatus.TRANSCRIBING);
        ReflectionTestUtils.setField(friend, "relationshipType", RelationshipType.FRIEND_DAILY);

        assertThrows(GeneralException.class, () -> parentChild.mapSpeakers("1", "엄마", null));
        assertThrows(GeneralException.class, () -> friend.mapSpeakers("1", "호석", ParentChildRole.PARENT));

        parentChild.mapSpeakers("1", "엄마", ParentChildRole.CHILD);
        assertThat(parentChild.getParentChildRole()).isEqualTo(ParentChildRole.CHILD);
    }

    @Test
    void mapSpeakers_전사_중이_아니면_예외() {
        Recording recording = recordingWithStatus(RecordingStatus.ANALYZING);
        ReflectionTestUtils.setField(recording, "relationshipType", RelationshipType.FRIEND_DAILY);

        assertThrows(GeneralException.class, () -> recording.mapSpeakers("1", "호석", null));
    }

    private Recording recordingWithStatus(RecordingStatus status) {
        Recording recording = Recording.create(1L, "/storage/test.mp3", 600);
        ReflectionTestUtils.setField(recording, "status", status);
        return recording;
    }
}