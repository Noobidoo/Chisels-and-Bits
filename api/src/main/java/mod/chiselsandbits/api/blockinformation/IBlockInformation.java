package mod.chiselsandbits.api.blockinformation;

import mod.chiselsandbits.api.util.INBTSerializable;
import mod.chiselsandbits.api.util.IPacketBufferSerializable;
import mod.chiselsandbits.api.util.ISnapshotable;
import mod.chiselsandbits.api.variant.state.IStateVariant;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public interface IBlockInformation extends INBTSerializable<CompoundTag>, IPacketBufferSerializable, ISnapshotable<IBlockInformation>, Comparable<IBlockInformation> {
    IBlockInformation AIR = IBlockInformationFactory.getInstance().create(
            Blocks.AIR.defaultBlockState(),
            Optional.empty()
    );

    BlockState getBlockState();

    Optional<IStateVariant> getVariant();

    boolean isFluid();

    boolean isAir();
}
