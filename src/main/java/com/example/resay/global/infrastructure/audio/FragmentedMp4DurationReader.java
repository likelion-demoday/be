package com.example.resay.global.infrastructure.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * 브라우저(MediaRecorder)로 녹음한 MP4의 재생시간을 읽는다.
 * 브라우저는 녹음하면서 조각(moof)을 이어 붙이기 때문에 앞쪽 헤더(mvhd)의 총 재생시간이 0으로 남는다.
 * 그래서 조각마다 적힌 시작 시각(tfdt)과 샘플 길이(trun)를 더해 실제 재생시간을 구한다.
 */
final class FragmentedMp4DurationReader {

    private static final int HEADER_SIZE = 8;
    // 헤더(moov)와 조각 헤더(moof)만 메모리에 올린다. 음성 데이터(mdat)는 건너뛴다
    private static final long MAX_METADATA_BOX_SIZE = 16L * 1024 * 1024;

    private FragmentedMp4DurationReader() {
    }

    static boolean isMp4(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
            return channel.read(header, 0) == HEADER_SIZE && "ftyp".equals(type(header, 4));
        }
    }

    // 조각이 없거나 읽을 수 없는 구조면 0을 돌려준다
    static long readMillis(Path path) throws IOException {
        Map<Long, Long> timescales = new HashMap<>();
        Map<Long, Long> defaultSampleDurations = new HashMap<>();
        Map<Long, Long> trackEnds = new HashMap<>();

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            long position = 0;
            while (position + HEADER_SIZE <= fileSize) {
                ByteBuffer header = ByteBuffer.allocate(16);
                channel.read(header, position);
                long size = Integer.toUnsignedLong(header.getInt(0));
                String type = type(header, 4);
                int headerSize = HEADER_SIZE;
                if (size == 1) {
                    size = header.getLong(8);
                    headerSize = 16;
                } else if (size == 0) {
                    size = fileSize - position;
                }
                if (size < headerSize || position + size > fileSize) {
                    break;
                }

                if (type.equals("moov") || type.equals("moof")) {
                    if (size > MAX_METADATA_BOX_SIZE) {
                        return 0;
                    }
                    ByteBuffer box = ByteBuffer.allocate((int) (size - headerSize));
                    channel.read(box, position + headerSize);
                    if (type.equals("moov")) {
                        readMovie(box, timescales, defaultSampleDurations);
                    } else {
                        readFragment(box, defaultSampleDurations, trackEnds);
                    }
                }
                position += size;
            }
        }

        long millis = 0;
        for (Map.Entry<Long, Long> trackEnd : trackEnds.entrySet()) {
            Long timescale = timescales.get(trackEnd.getKey());
            if (timescale != null && timescale > 0) {
                millis = Math.max(millis, trackEnd.getValue() * 1000 / timescale);
            }
        }
        return millis;
    }

    // moov > trak > (tkhd: 트랙 번호, mdia > mdhd: 시간 단위), moov > mvex > trex: 기본 샘플 길이
    private static void readMovie(ByteBuffer moov, Map<Long, Long> timescales, Map<Long, Long> defaultSampleDurations) {
        forEachChild(moov, (type, child) -> {
            if (type.equals("trak")) {
                long[] trackId = {-1};
                long[] timescale = {0};
                forEachChild(child, (trakChild, trakBox) -> {
                    if (trakChild.equals("tkhd")) {
                        int version = trakBox.get(0);
                        trackId[0] = Integer.toUnsignedLong(trakBox.getInt(version == 1 ? 20 : 12));
                    } else if (trakChild.equals("mdia")) {
                        forEachChild(trakBox, (mdiaChild, mdiaBox) -> {
                            if (mdiaChild.equals("mdhd")) {
                                int version = mdiaBox.get(0);
                                timescale[0] = Integer.toUnsignedLong(mdiaBox.getInt(version == 1 ? 20 : 12));
                            }
                        });
                    }
                });
                if (trackId[0] >= 0) {
                    timescales.put(trackId[0], timescale[0]);
                }
            } else if (type.equals("mvex")) {
                forEachChild(child, (mvexChild, mvexBox) -> {
                    if (mvexChild.equals("trex")) {
                        defaultSampleDurations.put(
                                Integer.toUnsignedLong(mvexBox.getInt(4)),
                                Integer.toUnsignedLong(mvexBox.getInt(12)));
                    }
                });
            }
        });
    }

    // moof > traf > (tfhd: 트랙 번호와 기본 샘플 길이, tfdt: 조각 시작 시각, trun: 샘플별 길이)
    private static void readFragment(ByteBuffer moof, Map<Long, Long> defaultSampleDurations, Map<Long, Long> trackEnds) {
        forEachChild(moof, (type, traf) -> {
            if (!type.equals("traf")) {
                return;
            }
            long[] trackId = {-1};
            long[] defaultDuration = {0};
            long[] start = {-1};
            long[] duration = {0};
            forEachChild(traf, (trafChild, box) -> {
                switch (trafChild) {
                    case "tfhd" -> {
                        int flags = box.getInt(0) & 0xFFFFFF;
                        trackId[0] = Integer.toUnsignedLong(box.getInt(4));
                        defaultDuration[0] = defaultSampleDurations.getOrDefault(trackId[0], 0L);
                        int offset = 8;
                        if ((flags & 0x01) != 0) offset += 8; // base data offset
                        if ((flags & 0x02) != 0) offset += 4; // sample description index
                        if ((flags & 0x08) != 0) defaultDuration[0] = Integer.toUnsignedLong(box.getInt(offset));
                    }
                    case "tfdt" -> start[0] = box.get(0) == 1 ? box.getLong(4) : Integer.toUnsignedLong(box.getInt(4));
                    case "trun" -> duration[0] += sampleDurations(box, defaultDuration[0]);
                    default -> {
                    }
                }
            });
            if (trackId[0] < 0) {
                return;
            }
            // 조각 시작 시각이 없으면 앞 조각에 이어 붙인다
            long fragmentStart = start[0] >= 0 ? start[0] : trackEnds.getOrDefault(trackId[0], 0L);
            trackEnds.merge(trackId[0], fragmentStart + duration[0], Math::max);
        });
    }

    private static long sampleDurations(ByteBuffer trun, long defaultDuration) {
        int flags = trun.getInt(0) & 0xFFFFFF;
        long sampleCount = Integer.toUnsignedLong(trun.getInt(4));
        int offset = 8;
        if ((flags & 0x001) != 0) offset += 4; // data offset
        if ((flags & 0x004) != 0) offset += 4; // first sample flags
        if ((flags & 0x100) == 0) {
            return sampleCount * defaultDuration;
        }
        int entrySize = 4
                + ((flags & 0x200) != 0 ? 4 : 0)
                + ((flags & 0x400) != 0 ? 4 : 0)
                + ((flags & 0x800) != 0 ? 4 : 0);
        long total = 0;
        for (long i = 0; i < sampleCount && offset + 4 <= trun.limit(); i++, offset += entrySize) {
            total += Integer.toUnsignedLong(trun.getInt(offset));
        }
        return total;
    }

    private static void forEachChild(ByteBuffer parent, ChildVisitor visitor) {
        int position = 0;
        while (position + HEADER_SIZE <= parent.limit()) {
            long size = Integer.toUnsignedLong(parent.getInt(position));
            if (size < HEADER_SIZE || position + size > parent.limit()) {
                return;
            }
            ByteBuffer child = parent.slice(position + HEADER_SIZE, (int) size - HEADER_SIZE);
            visitor.visit(type(parent, position + 4), child);
            position += (int) size;
        }
    }

    private static String type(ByteBuffer buffer, int offset) {
        byte[] bytes = new byte[4];
        buffer.get(offset, bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    @FunctionalInterface
    private interface ChildVisitor {
        void visit(String type, ByteBuffer box);
    }
}
