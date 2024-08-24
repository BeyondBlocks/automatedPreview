package de.beyondblocks.automatedPreview;

import se.llbit.log.Log;
import se.llbit.nbt.NamedTag;
import se.llbit.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class WorldAnalyser {

    private final static int SECTOR_SIZE = 4096;
    private final static long MIN_INHABITED_TIME = 20 * 60; // 1 Minute

    public static int calculateChunkRadius(File worldDirectory) {
        File regionDir = new File(worldDirectory, "region");


        int minChunkX = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;

        File[] files = regionDir.listFiles((dir, name) -> name.endsWith(".mca"));

        if (files == null) {
            return -1;
        }

        for (File regionFile : files) {
            String[] parts = regionFile.getName().split("\\.");
            int regionX = Integer.parseInt(parts[1]);
            int regionZ = Integer.parseInt(parts[2]);

            try (RandomAccessFile file = new RandomAccessFile(regionFile, "r")) {
                long length = file.length();

                if (length == 0) {
                    Log.info("Skipping empty region file");
                    continue; // Skip empty region files
                }

                if (length < 2 * SECTOR_SIZE) {
                    Log.warn("Missing header in region file!");
                    continue;
                }

                // Log.info("Checking Region:" + regionFile.getName());

                for (int x = 0; x < 32; ++x) {
                    for (int z = 0; z < 32; ++z) {
                        int index = (x & 31) + (z & 31) * 32;

                        file.seek(4 * index);
                        int loc = file.readInt();
                        int numSectors = loc & 0xFF;
                        int sectorOffset = loc >> 8;

                        if (sectorOffset == 0 && numSectors == 0) {
                            continue; // Skips not generated chunks
                        }

                        file.seek((long) sectorOffset * SECTOR_SIZE);
                        int lengthInBytes = file.readInt();
                        byte compressionType = file.readByte();

                        byte[] chunkData = new byte[lengthInBytes - 1];
                        file.readFully(chunkData);


                        int chunkX = regionX * 32 + x;
                        int chunkZ = regionZ * 32 + z;

                        if (compressionType != 1 && compressionType != 2) {
                            Log.warn("Error: unknown chunk data compression method: " + compressionType + "!");
                            continue;
                        }

                        DataInputStream decompressedChunkData;
                        if (compressionType == 1) {
                            decompressedChunkData = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(chunkData)));
                        } else {
                            decompressedChunkData = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(chunkData)));
                        }

                        Set<String> request = new HashSet<>();
                        request.add(".InhabitedTime");

                        Map<String, Tag> tagMap = NamedTag.quickParse(decompressedChunkData, request);

                        long inhabitedTime = tagMap.get(".InhabitedTime").longValue();

                        if (inhabitedTime <= MIN_INHABITED_TIME) {
                            continue;
                        }

                        // Log.info("Chunk: " + chunkX + " " + chunkZ + " Length: " + lengthInBytes + " Compression: " + compressionType + " InhabitedTime: " + inhabitedTime);

                        minChunkX = Math.min(minChunkX, chunkX);
                        maxChunkX = Math.max(maxChunkX, chunkX);
                        minChunkZ = Math.min(minChunkZ, chunkZ);
                        maxChunkZ = Math.max(maxChunkZ, chunkZ);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // No Chunks found
        if (minChunkX == Integer.MAX_VALUE || minChunkZ == Integer.MAX_VALUE) {
            return -1;
        }

        int centerX = (minChunkX + maxChunkX) / 2;
        int centerZ = (minChunkZ + maxChunkZ) / 2;

        int radiusX = Math.max(centerX - minChunkX, maxChunkX - centerX);
        int radiusZ = Math.max(centerZ - minChunkZ, maxChunkZ - centerZ);

        return Math.max(radiusX, radiusZ);
    }

}
