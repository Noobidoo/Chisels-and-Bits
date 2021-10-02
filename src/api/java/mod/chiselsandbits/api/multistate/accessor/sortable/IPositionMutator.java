package mod.chiselsandbits.api.multistate.accessor.sortable;

import net.minecraft.util.math.vector.Vector3i;

/**
 * Represents an object which can mutate the order of the coordinate members
 * before they are retrieved from storage.
 *
 * The default loop order is XYZ.
 * However by swapping for example the X and Y coordinate member of the
 * passed coordinates a YXZ loop order can be achieved without
 * the performance degradation of sorting.
 */
@FunctionalInterface
public interface IPositionMutator
{
    /**
     * Applies the mutation that this mutator performs on a given position.
     *
     * @param input The input position.
     *
     * @return The mutated output.
     */
    Vector3i mutate(final Vector3i input);

    /**
     * Creates a new mutator which chains the current and the next mutator into one.
     *
     * @param next The mutator that needs to be applied after the current mutator.
     *
     * @return The combined mutator.
     */
    default IPositionMutator then(final IPositionMutator next) {
        return input -> next.mutate(this.mutate(input));
    }

    /**
     * The identity operator.
     * Performs no change to the passed in position.
     *
     * @return An identity position mutator.
     */
    static IPositionMutator identity() {
        return input -> input;
    }

    /**
     * The xyz operator.
     * Generally also perceived as the identity mutator.
     *
     * @return The identity mutator.
     */
    static IPositionMutator xyz() {
        return input -> new Vector3i(input.getX(), input.getY(), input.getZ());
    }

    /**
     * The xzy operator.
     *
     * @return The mutator which switches the Y and Z coordinate members
     */
    static IPositionMutator xzy() {
        return input -> new Vector3i(input.getX(), input.getZ(), input.getY());
    }

    /**
     * The zyx operator.
     *
     * @return The mutator which switches the X and Z coordinate members
     */
    static IPositionMutator zyx() {
        return input -> new Vector3i(input.getZ(), input.getY(), input.getX());
    }

    /**
     * The yxz operator.
     *
     * @return The mutator which switches the X and Y coordinate members
     */
    static IPositionMutator yxz() {
        return input -> new Vector3i(input.getY(), input.getX(), input.getZ());
    }

    /**
     * The zxy operator.
     *
     * @return The mutator which switches the X with the Z and then the Y with the moved X coordinate members
     */
    static IPositionMutator zxy() {
        return input -> new Vector3i(input.getZ(), input.getX(), input.getY());
    }

    /**
     * The yzx operator
     * Is a combination of xzy and zyx.
     *
     * @return The mutator which first switches the Y and Z and then the X and Z coordinates.
     */
    static IPositionMutator yzx() {
        return zyx().then(yxz());
    }
}
