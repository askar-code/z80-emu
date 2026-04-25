package dev.z8emu.machine.cpc.disk;

import java.util.Arrays;

public record CpcDiskSector(
        int track,
        int side,
        int sectorId,
        int sizeCode,
        int status1,
        int status2,
        byte[] data
) {
    public CpcDiskSector {
        track &= 0xFF;
        side &= 0xFF;
        sectorId &= 0xFF;
        sizeCode &= 0xFF;
        status1 &= 0xFF;
        status2 &= 0xFF;
        data = Arrays.copyOf(data, data.length);
    }

    public int declaredSize() {
        return 128 << (sizeCode & 0x07);
    }

    public boolean matches(int cylinder, int head, int record, int requestedSizeCode) {
        return track == (cylinder & 0xFF)
                && side == (head & 0xFF)
                && sectorId == (record & 0xFF)
                && sizeCode == (requestedSizeCode & 0xFF);
    }

    @Override
    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }
}
