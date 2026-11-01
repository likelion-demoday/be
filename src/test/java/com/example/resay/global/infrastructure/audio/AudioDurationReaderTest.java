package com.example.resay.global.infrastructure.audio;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AudioDurationReaderTest {

    // 크롬(MediaRecorder)으로 16초 음성을 녹음한 파일. 헤더의 재생시간이 0인 조각(fragmented) MP4다
    private static final String CHROME_AAC = "audio/chrome-aac.m4a";
    private static final String CHROME_OPUS = "audio/chrome-opus.m4a";
    // 크롬 기본 녹음 형식. 전사 서비스(CLOVA)도 받지 않는다
    private static final String CHROME_WEBM = "audio/chrome-opus.webm";

    @TempDir
    Path tempDir;

    private final AudioDurationReader audioDurationReader = new AudioDurationReader();

    @Test
    void readSeconds_확장자만_바꾼_파일이면_지원하지_않는_포맷() throws Exception {
        Path fakeAudio = tempDir.resolve("fake.mp3");
        Files.writeString(fakeAudio, "오디오가 아닌 텍스트");

        GeneralException exception = assertThrows(GeneralException.class,
                () -> audioDurationReader.readSeconds(fakeAudio.toString()));

        assertEquals(RecordingErrorCode.UNSUPPORTED_MEDIA_TYPE, exception.getErrorCode());
    }

    @Test
    void readSeconds_브라우저로_녹음한_AAC_MP4는_조각을_더해_재생시간을_읽는다() throws Exception {
        assertEquals(16, audioDurationReader.readSeconds(resource(CHROME_AAC)));
    }

    @Test
    void readSeconds_브라우저로_녹음한_Opus_MP4도_재생시간을_읽는다() throws Exception {
        assertEquals(16, audioDurationReader.readSeconds(resource(CHROME_OPUS)));
    }

    @Test
    void readSeconds_WebM은_m4a로_이름을_바꿔도_지원하지_않는_포맷() throws Exception {
        Path renamed = Files.copy(Path.of(resource(CHROME_WEBM)), tempDir.resolve("recording.m4a"));

        GeneralException exception = assertThrows(GeneralException.class,
                () -> audioDurationReader.readSeconds(renamed.toString()));

        assertEquals(RecordingErrorCode.UNSUPPORTED_MEDIA_TYPE, exception.getErrorCode());
    }

    @Test
    void readSeconds_삼성_통화_녹음처럼_태그를_읽지_못하는_MP4는_헤더의_재생시간을_읽는다() throws Exception {
        Path callRecording = Files.write(tempDir.resolve("call.m4a"), samsungCallRecording(471_673, 10_000));

        assertEquals(47, audioDurationReader.readSeconds(callRecording.toString()));
    }

    private String resource(String name) throws Exception {
        return Path.of(getClass().getClassLoader().getResource(name).toURI()).toString();
    }

    // 갤럭시 통화 녹음 앱 파일과 같은 구조: 3gp4 브랜드, 음성 데이터(mdat)가 헤더(moov)보다 앞,
    // moov 안에 삼성 전용 udta와 버전 정보 없는 QuickTime식 meta(hdlr·keys·ilst)가 있다.
    // 오디오 트랙(AAC 48kHz)은 정상이라 meta만 없으면 jaudiotagger도 읽지만, meta 때문에 태그 읽기에서 실패한다
    private static byte[] samsungCallRecording(int duration, int timescale) {
        byte[] ftyp = box("ftyp", ascii("3gp4"), int32(0), ascii("isom"), ascii("3gp4"));
        byte[] mdat = box("mdat", new byte[64]);
        byte[] mvhd = box("mvhd", int32(0), int32(0), int32(0), int32(timescale), int32(duration), new byte[80]);
        byte[] udta = box("udta", box("SDLN", new byte[8]), box("smrd", new byte[8]), box("smta", new byte[34]));
        byte[] meta = box("meta",
                box("hdlr", int32(0), int32(0), ascii("mdta"), new byte[13]),
                box("keys", int32(0), int32(1), box("mdta", ascii("com.samsung.android.utc_offset"))),
                box("ilst", box("\0\0\0\1", box("data", int32(1), int32(0), ascii("+0900")))));
        // AAC-LC 48kHz 모노의 디코더 설정(esds)
        byte[] esds = box("esds", int32(0), HexFormat.of().parseHex("03190001000411401500000000000000000000000005021188060102"));
        byte[] mp4a = box("mp4a", new byte[6], int16(1), new byte[8], int16(1), int16(16), new byte[4], int32(48_000 << 16), esds);
        byte[] stbl = box("stbl",
                box("stsd", int32(0), int32(1), mp4a),
                box("stts", int32(0), int32(0)),
                box("stsz", int32(0), int32(0), int32(0)),
                box("stsc", int32(0), int32(0)),
                box("co64", int32(0), int32(0)));
        byte[] mdia = box("mdia",
                box("mdhd", int32(0), int32(0), int32(0), int32(timescale), int32(duration), int32(0)),
                box("hdlr", int32(0), int32(0), ascii("soun"), new byte[13]),
                box("minf", box("smhd", int32(0), int32(0)), stbl));
        byte[] trak = box("trak", box("tkhd", int32(0), new byte[80]), mdia);
        return concat(ftyp, mdat, box("moov", mvhd, udta, meta, trak));
    }

    private static byte[] box(String type, byte[]... payloads) {
        byte[] payload = concat(payloads);
        return concat(int32(8 + payload.length), ascii(type), payload);
    }

    private static byte[] int32(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    private static byte[] int16(int value) {
        return ByteBuffer.allocate(2).putShort((short) value).array();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
