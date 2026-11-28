package ru.xmusya.worlds;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import java.util.Random;

/** Пустой (void) генератор: completely empty world, only void. */
public final class VoidGenerator extends ChunkGenerator {
    @Override
    public ChunkData generateChunkData(org.bukkit.World world, Random random, int chunkX, int chunkZ,
                                       BiomeGrid biome) {
        return createChunkData(world);
    }
}