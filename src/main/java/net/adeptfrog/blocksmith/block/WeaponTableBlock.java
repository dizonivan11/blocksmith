package net.adeptfrog.blocksmith.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class WeaponTableBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<WeaponTableBlock> CODEC = simpleCodec(WeaponTableBlock::new);

    public WeaponTableBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected @NonNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hitResult) {
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(@NonNull BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Center position on top of the block with a slight random spread
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
        double y = pos.getY() + 1.05; // Spawns just above the top surface
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;

        level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.02, 0.0);

        if (random.nextDouble() < 0.3) {
            level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.01, 0.0);
        }
    }
}