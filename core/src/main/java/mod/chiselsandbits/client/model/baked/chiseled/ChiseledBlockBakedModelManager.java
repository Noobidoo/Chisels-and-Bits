package mod.chiselsandbits.client.model.baked.chiseled;

import mod.chiselsandbits.api.blockinformation.IBlockInformation;
import mod.chiselsandbits.blockinformation.BlockInformation;
import mod.chiselsandbits.api.config.IClientConfiguration;
import mod.chiselsandbits.api.item.multistate.IMultiStateItemStack;
import mod.chiselsandbits.api.multistate.accessor.IAreaAccessor;
import mod.chiselsandbits.api.multistate.accessor.IStateEntryInfo;
import mod.chiselsandbits.api.multistate.accessor.identifier.IAreaShapeIdentifier;
import mod.chiselsandbits.api.neighborhood.IBlockNeighborhood;
import mod.chiselsandbits.api.neighborhood.IBlockNeighborhoodBuilder;
import mod.chiselsandbits.api.profiling.IProfilerSection;
import mod.chiselsandbits.api.util.VectorUtils;
import mod.chiselsandbits.profiling.ProfilingManager;
import mod.chiselsandbits.utils.SimpleMaxSizedCache;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class ChiseledBlockBakedModelManager {
    private static final ChiseledBlockBakedModelManager INSTANCE = new ChiseledBlockBakedModelManager();

    private final SimpleMaxSizedCache<Key, ChiseledBlockBakedModel> cache = new SimpleMaxSizedCache<>(
            () -> IClientConfiguration.getInstance().getModelCacheSize().get() * RenderType.chunkBufferLayers().size()
    );

    private ChiseledBlockBakedModelManager() {
    }

    public static ChiseledBlockBakedModelManager getInstance() {
        return INSTANCE;
    }

    public void clearCache() {
        cache.clear();
    }

    public ChiseledBlockBakedModel get(
            @NotNull final IMultiStateItemStack multiStateItemStack,
            @NotNull final ChiselRenderType chiselRenderType,
            @NotNull final RenderType renderType
    ) {
        try (IProfilerSection ignored = ProfilingManager.getInstance().withSection("Item based chiseled block model")) {
            return get(
                multiStateItemStack,
                multiStateItemStack.getStatistics().getPrimaryState(),
                chiselRenderType,
                null,
                null,
                BlockPos.ZERO,
                renderType
            );
        }
    }

    public ChiseledBlockBakedModel get(
            @NotNull final IAreaAccessor accessor,
            @NotNull final BlockInformation primaryState,
            @NotNull final ChiselRenderType chiselRenderType,
            @NotNull final RenderType renderType
    ) {
        return this.get(accessor, primaryState, chiselRenderType, null, null, BlockPos.ZERO, renderType);
    }

    public ChiseledBlockBakedModel get(
            final IAreaAccessor accessor,
            final IBlockInformation primaryState,
            final ChiselRenderType chiselRenderType,
            @Nullable final Function<Direction, IBlockInformation> neighborhoodBlockInformationProvider,
            @Nullable final Function<Direction, IAreaAccessor> neighborhoodAreaAccessorProvider,
            @NotNull final BlockPos position,
            @NotNull final RenderType renderType
    ) {
        try (IProfilerSection ignored1 = ProfilingManager.getInstance().withSection("Block based chiseled block model")) {
            return get(accessor, primaryState, chiselRenderType, IBlockNeighborhoodBuilder.getInstance().build(
                    neighborhoodBlockInformationProvider,
                    neighborhoodAreaAccessorProvider
            ), position, renderType);
        }
    }

    public ChiseledBlockBakedModel get(
            final IAreaAccessor accessor,
            final IBlockInformation primaryState,
            final ChiselRenderType chiselRenderType,
            @NotNull IBlockNeighborhood blockNeighborhood,
            @NotNull BlockPos position,
            @NotNull final RenderType renderType
    ) {
        try (IProfilerSection ignored1 = ProfilingManager.getInstance().withSection("Block based chiseled block model")) {
            final long primaryStateRenderSeed = primaryState.getBlockState().getSeed(position);
            final Key key = new Key(
                    accessor.createNewShapeIdentifier(),
                    primaryState,
                    chiselRenderType,
                    blockNeighborhood,
                    primaryStateRenderSeed,
                    renderType);
            return cache.get(key,
                    () -> {
                        try (IProfilerSection ignored3 = ProfilingManager.getInstance().withSection("Cache mis")) {
                            return new ChiseledBlockBakedModel(
                                    primaryState,
                                    chiselRenderType,
                                    accessor,
                                    targetOffset -> {
                                        final Vec3 nominalTargetOffset = Vec3.ZERO.add(targetOffset);
                                        final BlockPos nominalTargetBlockOffset = new BlockPos(nominalTargetOffset);
                                        final Vec3 inBlockOffset = nominalTargetOffset.subtract(Vec3.atLowerCornerOf(nominalTargetBlockOffset));                                        final Vec3 inBlockOffsetTarget = VectorUtils.makePositive(inBlockOffset);

                                        final Direction offsetDirection = Direction.getNearest(
                                          nominalTargetBlockOffset.getX(),
                                          nominalTargetBlockOffset.getY(),
                                          nominalTargetBlockOffset.getZ()
                                        );

                                        IAreaAccessor neighborAccessor;
                                        if (targetOffset.x() >= 0 && targetOffset.x() < 1 &&
                                                targetOffset.y() >= 0 && targetOffset.y() < 1 &&
                                                targetOffset.z() >= 0 && targetOffset.z() < 1
                                        ) {
                                            neighborAccessor = accessor;
                                        } else {
                                            neighborAccessor = blockNeighborhood.getAreaAccessor(offsetDirection);
                                        }

                                        if (neighborAccessor != null) {
                                            return neighborAccessor.getInAreaTarget(inBlockOffsetTarget)
                                                    .map(IStateEntryInfo::getBlockInformation)
                                                    .orElse(BlockInformation.AIR);
                                        }

                                        return blockNeighborhood.getBlockInformation(offsetDirection);
                                    },
                                    primaryStateRenderSeed
                            );
                        }
                    });
        }
    }

    private record Key(IAreaShapeIdentifier identifier, IBlockInformation primaryState,
                       ChiselRenderType chiselRenderType, IBlockNeighborhood neighborhood, long renderSeed,
                       RenderType renderType) {
        }
}
