package fr.sncf.osrd.conflicts

import fr.sncf.osrd.envelope.EnvelopeTimeInterpolate
import fr.sncf.osrd.envelope_sim.PhysicsRollingStock
import fr.sncf.osrd.utils.units.Offset
import fr.sncf.osrd.utils.units.meters
import kotlin.math.max
import kotlin.math.min

class IncrementalRequirementEnvelopeAdapter(
    private val rollingStock: PhysicsRollingStock,
    private val envelopeWithStops: EnvelopeTimeInterpolate?,
    override var simulationComplete: Boolean,
) : IncrementalRequirementCallbacks {
    override fun arrivalTimeInRange(
        pathBeginOff: Offset<TravelledPath>,
        pathEndOff: Offset<TravelledPath>
    ): Double {
        if (envelopeWithStops == null) return Double.POSITIVE_INFINITY
        // if the head of the train enters the zone at some point, use that
        val begin = pathBeginOff.distance.meters
        if (begin >= 0.0 && begin <= envelopeWithStops.endPos)
            return envelopeWithStops.interpolateTotalTime(begin)

        val end = pathEndOff.distance.meters

        val trainBegin = -rollingStock.length
        val trainEnd = 0.0

        if (max(trainBegin, begin) < min(trainEnd, end)) return 0.0

        return Double.POSITIVE_INFINITY
    }

    override fun departureTimeFromRange(
        pathBeginOff: Offset<TravelledPath>,
        pathEndOff: Offset<TravelledPath>
    ): Double {
        if (envelopeWithStops == null) return Double.POSITIVE_INFINITY
        val end = pathEndOff.distance.meters

        val criticalPoint = end + rollingStock.length
        if (criticalPoint >= 0.0 && criticalPoint <= envelopeWithStops.endPos)
            return envelopeWithStops.interpolateTotalTime(criticalPoint)

        return Double.POSITIVE_INFINITY
    }

    override val currentTime
        get() = envelopeWithStops?.totalTime ?: 0.0

    override val currentPathOffset
        get() = Offset<TravelledPath>(envelopeWithStops?.endPos?.meters ?: 0.meters)

    override fun clone(): IncrementalRequirementCallbacks {
        return IncrementalRequirementEnvelopeAdapter(
            rollingStock,
            envelopeWithStops,
            simulationComplete,
        )
    }
}
