package dev.z8emu.machine.cpc.disk;

import java.util.Optional;

public final class CpcDskImage {
    private final int trackCount;
    private final int sideCount;
    private final CpcDiskTrack[][] tracks;

    public CpcDskImage(int trackCount, int sideCount, CpcDiskTrack[][] tracks) {
        if (trackCount <= 0 || trackCount > 255) {
            throw new IllegalArgumentException("trackCount must be between 1 and 255");
        }
        if (sideCount <= 0 || sideCount > 2) {
            throw new IllegalArgumentException("sideCount must be 1 or 2");
        }
        if (tracks.length < trackCount) {
            throw new IllegalArgumentException("track table does not contain all tracks");
        }
        this.trackCount = trackCount;
        this.sideCount = sideCount;
        this.tracks = new CpcDiskTrack[trackCount][sideCount];
        for (int track = 0; track < trackCount; track++) {
            if (tracks[track].length < sideCount) {
                throw new IllegalArgumentException("track table does not contain all sides");
            }
            for (int side = 0; side < sideCount; side++) {
                this.tracks[track][side] = tracks[track][side];
            }
        }
    }

    public int trackCount() {
        return trackCount;
    }

    public int sideCount() {
        return sideCount;
    }

    public Optional<CpcDiskTrack> track(int track, int side) {
        if (track < 0 || track >= trackCount || side < 0 || side >= sideCount) {
            return Optional.empty();
        }
        return Optional.ofNullable(tracks[track][side]);
    }

    public Optional<CpcDiskSector> findSector(int physicalTrack, int side, int cylinder, int head, int record, int sizeCode) {
        return track(physicalTrack, side)
                .flatMap(track -> track.findSector(cylinder, head, record, sizeCode));
    }
}
