package net.adeptfrog.blocksmith.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static net.adeptfrog.blocksmith.Blocksmith.MAX_LAYER;
import static net.adeptfrog.blocksmith.Blocksmith.MIN_LAYER;

public record WeaponVoxel(int x, int y, int z, String materialId, int shade) {
    public static final Codec<WeaponVoxel> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("x").forGetter(WeaponVoxel::x),
                    Codec.INT.fieldOf("y").forGetter(WeaponVoxel::y),
                    Codec.INT.fieldOf("z").forGetter(WeaponVoxel::z),
                    Codec.STRING.fieldOf("material").forGetter(WeaponVoxel::materialId),
                    Codec.INT.optionalFieldOf("shade", 2).forGetter(WeaponVoxel::shade)
            ).apply(instance, WeaponVoxel::new)
    );

    public static final StreamCodec<ByteBuf, WeaponVoxel> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WeaponVoxel::x,
            ByteBufCodecs.VAR_INT, WeaponVoxel::y,
            ByteBufCodecs.VAR_INT, WeaponVoxel::z,
            ByteBufCodecs.STRING_UTF8, WeaponVoxel::materialId,
            ByteBufCodecs.VAR_INT, WeaponVoxel::shade,
            WeaponVoxel::new
    );

    public WeaponVoxel(int x, int y, int z, VoxelMaterial mat, int shade) {
        this(x, y, z, mat.id(), shade);
    }

    public static boolean isValidLayer(int z) {
        return z >= MIN_LAYER && z <= MAX_LAYER;
    }

    /**
     * Returns the voxels ordered from the back layer (-Z) to the front layer (+Z), so that flat 2D previews
     * that draw them in order end up showing the front-most voxel of each column on top.
     */
    public static List<WeaponVoxel> sortedBackToFront(Collection<WeaponVoxel> voxels) {
        List<WeaponVoxel> sorted = new ArrayList<>(voxels);
        sorted.sort(Comparator.comparingInt(WeaponVoxel::z));
        return sorted;
    }

    public VoxelMaterial material() {
        return VoxelMaterialRegistry.getOrDefault(materialId);
    }

    public int getColorRgb() {
        return material().getColorRgb(shade);
    }
}