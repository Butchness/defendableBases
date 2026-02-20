package com.defendablebases;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class FortressCenterBlock extends BaseEntityBlock {

    // Same axis property vanilla logs use
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    // Upside-down flag
    public static final BooleanProperty FLIPPED = BooleanProperty.create("flipped");

    public FortressCenterBlock(Properties props) {
        super(props);

        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(AXIS, Direction.Axis.Y)
                        .setValue(FLIPPED, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(AXIS, FLIPPED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();

        // Log-like axis: axis aligns with clicked face
        Direction.Axis axis = face.getAxis();

        // Upside-down if placing against the underside of a block
        boolean flipped = (face == Direction.DOWN);

        return this.defaultBlockState()
                .setValue(AXIS, axis)
                .setValue(FLIPPED, flipped);
    }

    /**
     * IMPORTANT: prevent face culling between adjacent blocks.
     * Without this, Minecraft may cull the touching face (especially against itself),
     * which can look like transparency with non-full-cube/custom models.
     */
    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        return false; // render all faces always
        // If you only want to prevent culling against itself, use:
        // return adjacentState.is(this) ? false : super.skipRendering(state, adjacentState, side);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FortressCenterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;

        return createTickerHelper(
                type,
                ModBlockEntities.FORTRESS_CENTER.get(),
                FortressCenterBlockEntity::serverTick
        );
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);

        if (random.nextFloat() >= 0.35f) return;

        double x = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.6D;
        double y = pos.getY() + 0.5D + (random.nextDouble() - 0.5D) * 0.8D;
        double z = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.6D;

        double vx = (random.nextDouble() - 0.5D) * 0.02D;
        double vy = (random.nextDouble() - 0.5D) * 0.02D;
        double vz = (random.nextDouble() - 0.5D) * 0.02D;

        level.addParticle(ParticleTypes.PORTAL, x, y, z, vx, vy, vz);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            // sneak-right-click => net highlight (and DO NOT open menu)
            if (player.isShiftKeyDown()) {
                FortressNetHighlight.trySendHighlight(player, level, pos);
                return InteractionResult.SUCCESS;
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FortressCenterBlockEntity fc) {
                fc.openMenu(player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
