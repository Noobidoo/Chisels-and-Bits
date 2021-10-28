package mod.chiselsandbits.chiseling.modes.replace;

import com.google.common.collect.Maps;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import mod.chiselsandbits.api.change.IChangeTrackerManager;
import mod.chiselsandbits.api.chiseling.ChiselingOperation;
import mod.chiselsandbits.api.chiseling.IChiselingContext;
import mod.chiselsandbits.api.chiseling.mode.IChiselMode;
import mod.chiselsandbits.api.inventory.bit.IBitInventory;
import mod.chiselsandbits.api.inventory.management.IBitInventoryManager;
import mod.chiselsandbits.api.item.click.ClickProcessingState;
import mod.chiselsandbits.api.item.withmode.group.IToolModeGroup;
import mod.chiselsandbits.api.multistate.StateEntrySize;
import mod.chiselsandbits.api.multistate.accessor.IAreaAccessor;
import mod.chiselsandbits.api.multistate.accessor.IStateEntryInfo;
import mod.chiselsandbits.api.multistate.mutator.IMutatorFactory;
import mod.chiselsandbits.api.multistate.mutator.batched.IBatchMutation;
import mod.chiselsandbits.api.util.RayTracingUtils;
import mod.chiselsandbits.registrars.ModMetadataKeys;
import mod.chiselsandbits.utils.BitInventoryUtils;
import mod.chiselsandbits.utils.ItemStackUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistryEntry;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class ReplaceChiselingMode extends ForgeRegistryEntry<IChiselMode> implements IChiselMode
{
    private final MutableComponent displayName;
    private final MutableComponent multiLineDisplayName;
    private final ResourceLocation iconName;

    public ReplaceChiselingMode(
      final MutableComponent displayName,
      final MutableComponent multiLineDisplayName,
      final ResourceLocation iconName) {
        this.displayName = displayName;
        this.multiLineDisplayName = multiLineDisplayName;
        this.iconName = iconName;
    }

    @Override
    public ClickProcessingState onLeftClickBy(
      final Player Player, final IChiselingContext context)
    {
        final Optional<ClickProcessingState> rayTraceHandle = this.processRayTraceIntoContext(
          Player,
          context
        );

        if (context.isSimulation())
        {
            return ClickProcessingState.DEFAULT;
        }

        return rayTraceHandle.orElseGet(() -> context.getMutator().map(mutator -> {
              try (IBatchMutation ignored =
                     mutator.batch(IChangeTrackerManager.getInstance().getChangeTracker(Player)))
              {
                  final BlockState heldBlockState = ItemStackUtils.getHeldBitBlockStateFromPlayer(Player);
                  if (heldBlockState.isAir())
                  {
                      return ClickProcessingState.DEFAULT;
                  }

                  context.setComplete();

                  final Map<BlockState, Integer> resultingBitCount = Maps.newHashMap();

                  final Predicate<IStateEntryInfo> filter = context.getStateFilter()
                    .map(builder -> builder.apply(mutator))
                    .orElse((state) -> true);

                  final int missingBitCount = (int) mutator.stream()
                    .filter(filter)
                    .count();

                  final IBitInventory playerBitInventory = IBitInventoryManager.getInstance().create(Player);

                  if (!Player.isCreative() && !playerBitInventory.canExtract(heldBlockState, missingBitCount))
                      return ClickProcessingState.DEFAULT;

                  mutator.inWorldMutableStream()
                    .filter(filter)
                    .forEach(LamdbaExceptionUtils.rethrowConsumer(state -> {
                        final BlockState currentState = state.getState();

                        if (context.tryDamageItem()) {
                            resultingBitCount.putIfAbsent(currentState, 0);
                            resultingBitCount.computeIfPresent(currentState, (s, currentCount) -> currentCount + 1);
                        }

                        state.clear();
                        state.setState(heldBlockState);
                    }));

                  if (!Player.isCreative())
                        playerBitInventory.extract(heldBlockState, missingBitCount);

                  resultingBitCount.forEach((blockState, count) -> BitInventoryUtils.insertIntoOrSpawn(
                    Player,
                    blockState,
                    count
                  ));
              }

              return new ClickProcessingState(true, Event.Result.ALLOW);
          }).orElse(ClickProcessingState.DEFAULT)
        );
    }

    @Override
    public void onStoppedLeftClicking(final Player Player, final IChiselingContext context)
    {
        //NOOP
    }

    @Override
    public ClickProcessingState onRightClickBy(final Player Player, final IChiselingContext context)
    {
        return onLeftClickBy(Player, context);
    }

    @Override
    public void onStoppedRightClicking(final Player Player, final IChiselingContext context)
    {
        //NOOP
    }

    @Override
    public boolean isStillValid(final Player Player, final IChiselingContext context, final ChiselingOperation modeOfOperation)
    {
        final Optional<Set<Vec3i>> validPositions = context.getMetadata(ModMetadataKeys.VALID_POSITIONS.get());
        final Optional<Direction> targetedSide = context.getMetadata(ModMetadataKeys.TARGETED_SIDE.get());
        final Optional<BlockPos> targetedBlockPos = context.getMetadata(ModMetadataKeys.TARGETED_BLOCK.get());

        if (!validPositions.isPresent() || !targetedSide.isPresent() || !targetedBlockPos.isPresent())
            return false;

        final HitResult hitResult = RayTracingUtils.rayTracePlayer(Player);
        if (hitResult.getType() != HitResult.Type.BLOCK || !(hitResult instanceof BlockHitResult))
        {
            return false;
        }

        final BlockHitResult blockHitResult = (BlockHitResult) hitResult;
        if (blockHitResult.getDirection() != targetedSide.get())
            return false;

        final Function<Direction, Vec3> placementFacingAdapter = modeOfOperation == ChiselingOperation.CHISELING ?
                                                                       face -> Vec3.atLowerCornerOf(face.getOpposite().getNormal()) :
                                                                       face -> Vec3.atLowerCornerOf(face.getNormal());

        final Vec3 hitVector = blockHitResult.getLocation().add(
          placementFacingAdapter.apply(blockHitResult.getDirection())
            .multiply(StateEntrySize.current().getSizePerHalfBit(), StateEntrySize.current().getSizePerHalfBit(), StateEntrySize.current().getSizePerHalfBit())
        );

        final BlockPos hitPos = new BlockPos(hitVector);
        final Vec3 hitBlockPosVector = Vec3.atLowerCornerOf(hitPos);
        final Vec3 inBlockHitVector = hitVector.subtract(hitBlockPosVector);
        final Vec3i selectedPosition = new Vec3i(
          inBlockHitVector.x() * StateEntrySize.current().getBitsPerBlockSide(),
          inBlockHitVector.y() * StateEntrySize.current().getBitsPerBlockSide(),
          inBlockHitVector.z() * StateEntrySize.current().getBitsPerBlockSide()
        );

        return validPositions.get().contains(selectedPosition) && hitPos.equals(targetedBlockPos.get());
    }

    @Override
    public Optional<IAreaAccessor> getCurrentAccessor(final IChiselingContext context)
    {
        return context.getMutator().map(mutator -> mutator);
    }

    private Optional<ClickProcessingState> processRayTraceIntoContext(
      final Player Player,
      final IChiselingContext context
    )
    {
        final HitResult hitResult = RayTracingUtils.rayTracePlayer(Player);
        if (hitResult.getType() != HitResult.Type.BLOCK || !(hitResult instanceof BlockHitResult))
        {
            return Optional.of(ClickProcessingState.DEFAULT);
        }

        final Function<Direction, Vec3> placementFacingAdapter = face -> Vec3.atLowerCornerOf(face.getOpposite().getNormal());
        final BlockHitResult blockHitResult = (BlockHitResult) hitResult;
        final Vec3 hitVector = blockHitResult.getLocation().add(
          placementFacingAdapter.apply(blockHitResult.getDirection())
            .multiply(StateEntrySize.current().getSizePerHalfBit(), StateEntrySize.current().getSizePerHalfBit(), StateEntrySize.current().getSizePerHalfBit())
        );

        final BlockPos hitPos = new BlockPos(hitVector);
        final Vec3 hitBlockPosVector = Vec3.atLowerCornerOf(hitPos);
        final Vec3 inBlockHitVector = hitVector.subtract(hitBlockPosVector);

        final Deque<Vec3i> toProcess = new LinkedList<>();
        final IAreaAccessor worldAccessor = IMutatorFactory.getInstance().in(context.getWorld(), hitPos);

        final Set<Vec3i> validPositions = new HashSet<>();

        final Vec3i selectedPosition = new Vec3i(
          inBlockHitVector.x() * StateEntrySize.current().getBitsPerBlockSide(),
          inBlockHitVector.y() * StateEntrySize.current().getBitsPerBlockSide(),
          inBlockHitVector.z() * StateEntrySize.current().getBitsPerBlockSide()
        );
        toProcess.addLast(selectedPosition);

        final Vec3 selectedInBlockPosition = Vec3.atLowerCornerOf(
          selectedPosition
        ).multiply(
          StateEntrySize.current().getSizePerBitScalingVector()
        );

        final Optional<IStateEntryInfo> targetedInfo = worldAccessor.getInAreaTarget(
          selectedInBlockPosition
        );

        if (!targetedInfo.isPresent())
            return Optional.of(ClickProcessingState.DEFAULT);

        worldAccessor.stream()
          .filter(state -> state.getState().equals(targetedInfo.get().getState()))
          .map(state -> state.getStartPoint().multiply(StateEntrySize.current().getBitsPerBlockSideScalingVector()))
          .map(position -> new Vec3i(
            position.x(),
            position.y(),
            position.z()
          ))
          .forEach(validPositions::add);

        context.include(hitPos, Vec3.ZERO);
        context.include(hitPos, new Vec3(0.9999, 0.9999, 0.9999));
        context.setStateFilter(accessor -> new SelectedBitStateFilter(validPositions));

        context.setMetadata(ModMetadataKeys.VALID_POSITIONS.get(), validPositions);
        context.setMetadata(ModMetadataKeys.TARGETED_SIDE.get(), blockHitResult.getDirection());
        context.setMetadata(ModMetadataKeys.TARGETED_BLOCK.get(), hitPos);

        return Optional.empty();
    }

    @Override
    public boolean requiresPlaceableEditStack()
    {
        return true;
    }

    @Override
    public @NotNull ResourceLocation getIcon()
    {
        return iconName;
    }

    @Override
    public @NotNull Optional<IToolModeGroup> getGroup()
    {
        return Optional.empty();
    }

    @Override
    public Component getDisplayName()
    {
        return displayName;
    }

    @Override
    public Component getMultiLineDisplayName()
    {
        return this.multiLineDisplayName;
    }

    private static final class SelectedBitStateFilter implements Predicate<IStateEntryInfo> {

        private final Set<Vec3i> validPositions;

        public SelectedBitStateFilter( final Set<Vec3i> validPositions)
        {
            this.validPositions = validPositions;
        }

        @Override
        public boolean test(final IStateEntryInfo iStateEntryInfo)
        {
            final Vec3i position = new Vec3i(
              iStateEntryInfo.getStartPoint().x() * StateEntrySize.current().getBitsPerBlockSide(),
              iStateEntryInfo.getStartPoint().y() * StateEntrySize.current().getBitsPerBlockSide(),
              iStateEntryInfo.getStartPoint().z() * StateEntrySize.current().getBitsPerBlockSide()
            );

            return validPositions.contains(
                position
            );
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (!(o instanceof SelectedBitStateFilter))
            {
                return false;
            }

            final SelectedBitStateFilter that = (SelectedBitStateFilter) o;

            return validPositions.equals(that.validPositions);
        }

        @Override
        public int hashCode()
        {
            return validPositions.hashCode();
        }

        @Override
        public String toString()
        {
            return "SelectedBitStateFilter{" +
                     "validPositions=" + validPositions +
                     '}';
        }
    }
}