package dev.z8emu.machine.cpc.disk;

import java.util.List;
import java.util.Optional;

public record CpcDiskTrack(
        int track,
        int side,
        int sectorSizeCode,
        int gap3Length,
        int fillerByte,
        List<CpcDiskSector> sectors
) {
    public CpcDiskTrack {
        track &= 0xFF;
        side &= 0xFF;
        sectorSizeCode &= 0xFF;
        gap3Length &= 0xFF;
        fillerByte &= 0xFF;
        sectors = List.copyOf(sectors);
    }

    public Optional<CpcDiskSector> findSector(int cylinder, int head, int record, int sizeCode) {
        for (CpcDiskSector sector : sectors) {
            if (sector.matches(cylinder, head, record, sizeCode)) {
                return Optional.of(sector);
            }
        }
        return Optional.empty();
    }

    public Optional<CpcDiskSector> firstSector() {
        return sectors.isEmpty() ? Optional.empty() : Optional.of(sectors.get(0));
    }
}
