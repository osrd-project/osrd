package fr.sncf.osrd.stdcm.preprocessing.interfaces

import com.google.common.base.Objects
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import fr.sncf.osrd.envelope.EnvelopeTimeInterpolate
import fr.sncf.osrd.sim_infra.api.BlockId
import fr.sncf.osrd.utils.units.Distance

/** Abstract interface used to request the availability of path sections  */
@SuppressFBWarnings(
    value = ["URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"],
    justification = "The classes aren't used yet. This should be removed by the end of the migration."
)
interface BlockAvailabilityInterface {
    /** Request availability information about a section of the path.
     * <br></br>
     * Can return either an instance of either type:
     *
     *  * Available: the section is available
     *  * Unavailable: the section isn't available
     *  * NotEnoughLookahead: the train path needs to extend further, it depends on a directional choice
     *
     * More details are given in the instances.
     * <br></br>
     * Note: every position refers to the position of the head of the train.
     * The implementation of BlockAvailabilityInterface must account for train length, sight distance,
     * and similar factors.
     *
     * @param blocks List of block IDs taken by the train
     * @param startOffset Start of the section to check for availability, as an offset from the start of the path
     * @param endOffset End of the section to check for availability, as an offset from the start of the path
     * @param envelope Envelope describing the position of the train at any moment.
     * Must be exactly `(endOffset - startOffset) long.
     * @param startTime Time at which the train is at `startOffset`.
     * @return An Availability instance.
     */
    fun getAvailability(
        blocks: List<BlockId>,
        startOffset: Distance,
        endOffset: Distance,
        envelope: EnvelopeTimeInterpolate,
        startTime: Double
    ): Availability

    /** Represents the availability of the requested section  */
    abstract class Availability

    /** The requested section is available.
     * <br></br>
     * For example: a train enter the section at t=10, sees a signal 42s after entering the section,
     * and the signal is green until t=100. The result would be:
     *
     *  * maximumDelay = 100 - 42 - 10 = 48
     *  * timeOfNextConflict = 100
     *
     */
    class Available(
        /** The train can use the section now and up to `maximumDelay` later without conflict  */
        val maximumDelay: Double,
        /** Earliest time any resource used by the train is reused by another one  */
        val timeOfNextConflict: Double
    ) : Availability() {
        override fun equals(other: Any?): Boolean {
            if (this === other)
                return true
            if (other == null || javaClass != other.javaClass)
                return false
            val available = other as Available
            return (available.maximumDelay.compareTo(maximumDelay) == 0
                    && available.timeOfNextConflict.compareTo(timeOfNextConflict) == 0)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(maximumDelay, timeOfNextConflict)
        }
    }

    /** The requested section isn't available yet  */
    class Unavailable(
        /** Minimum delay to add to the departure time to avoid any conflict  */
        val duration: Double,
        /** Exact offset of the first conflict encountered. It's always the offset that marks either the border of an
         * unavailable section.
         * <br></br>
         * It's either the offset where we start using a ressource before it's available,
         * or the offset where the train would have released a ressource it has kept for too long.  */
        val firstConflictOffset: Distance
    ) : Availability() {
        override fun equals(other: Any?): Boolean {
            if (this === other)
                return true
            if (other == null || javaClass != other.javaClass)
                return false
            val that = other as Unavailable
            return (that.duration.compareTo(duration) == 0
                    && that.firstConflictOffset.millimeters.compareTo(firstConflictOffset.millimeters) == 0)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(duration, firstConflictOffset)
        }
    }

    /** The availability of the requested section can't be determined,
     * the path needs to extend further. The availability depends
     * on the block taken by the train after the end of the given path.  */
    class NotEnoughLookahead : Availability()
}