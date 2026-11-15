package com.example.resay.domain.recording.service;

import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.transcription.repository.TranscriptSegmentRepository;
import com.example.resay.global.infrastructure.storage.LocalFileStorage;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingSourceCleanupServiceTest {

    private static final long RECORDING_ID = 1L;
    private static final String AUDIO_PATH = "/storage/source.m4a";

    @Mock
    private RecordingRepository recordingRepository;

    @Mock
    private TranscriptSegmentRepository transcriptSegmentRepository;

    @Mock
    private LocalFileStorage localFileStorage;

    @InjectMocks
    private RecordingSourceCleanupService cleanupService;

    @Test
    void 음성과_전사_원문을_함께_삭제한다() {
        Recording recording = Recording.create(1L, AUDIO_PATH, 600);
        when(recordingRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(recording));
        when(localFileStorage.delete(AUDIO_PATH)).thenReturn(true);
        when(transcriptSegmentRepository.deleteByRecordingId(RECORDING_ID)).thenReturn(12);

        boolean completed = cleanupService.deleteSourceData(RECORDING_ID);

        assertThat(completed).isTrue();
        assertThat(recording.hasAudio()).isFalse();
        verify(transcriptSegmentRepository).deleteByRecordingId(RECORDING_ID);
    }

    @Test
    void 음성_삭제에_실패해도_전사_원문은_삭제하고_재시도_대상으로_남긴다() {
        Recording recording = Recording.create(1L, AUDIO_PATH, 600);
        when(recordingRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(recording));
        when(localFileStorage.delete(AUDIO_PATH)).thenReturn(false);

        boolean completed = cleanupService.deleteSourceData(RECORDING_ID);

        assertThat(completed).isFalse();
        assertThat(recording.hasAudio()).isTrue();
        verify(transcriptSegmentRepository).deleteByRecordingId(RECORDING_ID);
    }

    @Test
    void 이미_음성이_삭제됐어도_남은_전사_원문을_삭제한다() {
        Recording recording = Recording.create(1L, AUDIO_PATH, 600);
        recording.markAudioDeleted();
        when(recordingRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(recording));

        boolean completed = cleanupService.deleteSourceData(RECORDING_ID);

        assertThat(completed).isTrue();
        verify(localFileStorage, never()).delete(AUDIO_PATH);
        verify(transcriptSegmentRepository).deleteByRecordingId(RECORDING_ID);
    }

    @Test
    void 이미_녹음_행이_없으면_정리가_끝난_것으로_본다() {
        when(recordingRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.empty());

        assertThat(cleanupService.deleteSourceData(RECORDING_ID)).isTrue();

        verify(localFileStorage, never()).delete(AUDIO_PATH);
        verify(transcriptSegmentRepository, never()).deleteByRecordingId(RECORDING_ID);
    }
}
