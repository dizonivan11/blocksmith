package net.adeptfrog.blocksmith.block;

import com.mojang.serialization.MapCodec;
import net.adeptfrog.blocksmith.data.VoxelMaterial;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * One voxel of a weapon as a block, used only inside the Dimensional Editor. The material (index into the voxel
 * material list) and shade are block states; the client tints a plain white cube with the matching palette colour.
 */
public class VoxelCubeBlock extends Block {
    public static final int MAX_MATERIALS = 64;
    public static final int SHADES = 5;
    public static final IntegerProperty MATERIAL = IntegerProperty.create("material", 0, MAX_MATERIALS - 1);
    public static final IntegerProperty SHADE = IntegerProperty.create("shade", 0, SHADES - 1);
    public static final MapCodec<VoxelCubeBlock> CODEC = simpleCodec(VoxelCubeBlock::new);

    public VoxelCubeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(MATERIAL, 0).setValue(SHADE, 2));
    }

    @Override
    protected @NonNull MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MATERIAL, SHADE);
    }

    public BlockState stateFor(int materialIndex, int shade) {
        return this.defaultBlockState()
                .setValue(MATERIAL, Math.clamp(materialIndex, 0, MAX_MATERIALS - 1))
                .setValue(SHADE, Math.clamp(shade, 0, SHADES - 1));
    }

    /** The materials usable in the editor, in a stable order (the block state's material index points into this). */
    public static List<VoxelMaterial> orderedMaterials() {
        List<VoxelMaterial> all = new ArrayList<>(VoxelMaterialRegistry.getAll());
        return all.size() > MAX_MATERIALS ? all.subList(0, MAX_MATERIALS) : all;
    }

    /** Index of a material id in {@link #orderedMaterials()}, or -1 when it is unknown / beyond the supported count. */
    public static int indexOf(String materialId) {
        List<VoxelMaterial> mats = orderedMaterials();
        for (int i = 0; i < mats.size(); i++) {
            if (mats.get(i).id().equalsIgnoreCase(materialId)) return i;
        }
        return -1;
    }
}
