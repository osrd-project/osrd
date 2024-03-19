package fr.sncf.osrd.stdcm.graph

import fr.sncf.osrd.conflicts.TravelledPath
import fr.sncf.osrd.envelope.Envelope
import fr.sncf.osrd.envelope_sim.EnvelopeSimContext
import fr.sncf.osrd.envelope_sim.EnvelopeSimPath
import fr.sncf.osrd.envelope_sim.TrainPhysicsIntegrator
import fr.sncf.osrd.envelope_sim.allowances.LinearAllowance
import fr.sncf.osrd.envelope_sim.allowances.MarecoAllowance
import fr.sncf.osrd.envelope_sim.allowances.utils.AllowanceRange
import fr.sncf.osrd.envelope_sim.allowances.utils.AllowanceValue
import fr.sncf.osrd.graph.Pathfinding.EdgeRange
import fr.sncf.osrd.reporting.exceptions.ErrorType
import fr.sncf.osrd.reporting.exceptions.OSRDError
import fr.sncf.osrd.standalone_sim.EnvelopeStopWrapper
import fr.sncf.osrd.stdcm.infra_exploration.withEnvelope
import fr.sncf.osrd.stdcm.preprocessing.interfaces.BlockAvailabilityInterface
import fr.sncf.osrd.train.RollingStock
import fr.sncf.osrd.train.RollingStock.Comfort
import fr.sncf.osrd.train.TrainStop
import fr.sncf.osrd.utils.units.Distance
import fr.sncf.osrd.utils.units.Offset
import fr.sncf.osrd.utils.units.meters
import java.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * We try to apply the standard allowance as one mareco computation over the whole path. If it
 * causes conflicts, we split the mareco ranges so that the passage time at the points of conflict
 * stays the same as the one we expected when exploring the graph.
 */
object STDCMStandardAllowance

val logger: Logger = LoggerFactory.getLogger(STDCMStandardAllowance::class.java)

private data class FixedTimePoint(
    val time: Double,
    val offset: Offset<TravelledPath>,
    val stopTime: Double?
) : Comparable<FixedTimePoint> {
    override fun compareTo(other: FixedTimePoint): Int {
        return offset.compareTo(other.offset)
    }
}

/**
 * Build the final envelope, this time without any approximation. Apply the allowances properly. The
 * simulations can be approximations up to this point (when exploring the graph), this is where we
 * transition to a precise simulation.
 */
fun buildFinalEnvelope(
    graph: STDCMGraph,
    maxSpeedEnvelope: Envelope,
    ranges: List<EdgeRange<STDCMEdge, STDCMEdge>>,
    standardAllowance: AllowanceValue?,
    envelopeSimPath: EnvelopeSimPath,
    rollingStock: RollingStock,
    timeStep: Double,
    comfort: Comfort?,
    blockAvailability: BlockAvailabilityInterface,
    departureTime: Double,
    stops: List<TrainStop>,
    isMareco: Boolean = true,
): Envelope {
    val context = build(rollingStock, envelopeSimPath, timeStep, comfort)
    val fullInfraExplorer = ranges.last().edge.infraExplorerWithNewEnvelope

    val incrementalPath = fullInfraExplorer.getIncrementalPath()
    assert(incrementalPath.pathComplete)
    val fixedPoints = initFixedPoints(ranges, stops)

    val maxIterations = ranges.size * 2 // just to avoid infinite loops on bugs or edge cases
    for (i in 0 until maxIterations) {
        try {
            val newEnvelope =
                runSimulationWithFixedPoints(maxSpeedEnvelope, fixedPoints, context, isMareco)
            val conflictOffset =
                findConflictOffsets(
                    graph,
                    newEnvelope,
                    blockAvailability,
                    ranges,
                    departureTime,
                    stops
                ) ?: return newEnvelope
            if (fixedPoints.any { it.offset == conflictOffset })
                break // Error case, we exit and fallback to the linear envelope
            logger.info(
                "Conflict in new envelope at offset {}, splitting mareco ranges",
                conflictOffset
            )
            fixedPoints.add(makeFixedPoint(ranges, conflictOffset))
        } catch (e: OSRDError) {
            if (e.osrdErrorType == ErrorType.AllowanceConvergenceTooMuchTime) {
                // Mareco allowances must have a non-zero capacity speed limit,
                // which may cause "too much time" errors.
                // We can ignore this exception and move on to the linear allowance as fallback
                logger.info("Can't slow down enough to match the given standard allowance")
                break
            } else throw e
        }
    }
    if (!isMareco) {
        throw RuntimeException(
            "Failed to compute a standard allowance that wouldn't cause conflicts"
        )
    } else {
        logger.info("Failed to compute a mareco standard allowance, fallback to linear allowance")
        return buildFinalEnvelope(
            graph,
            maxSpeedEnvelope,
            ranges,
            standardAllowance,
            envelopeSimPath,
            rollingStock,
            timeStep,
            comfort,
            blockAvailability,
            departureTime,
            stops,
            false,
        )
    }
}

/**
 * Initialize all fixed points at stop locations. This is based off the max speed envelope and
 * allowance value instead of getting times on the envelope approximating the train time with
 * allowance and stops, because getting time at stop locations on that envelope is ambiguous
 */
private fun initFixedPoints(
    ranges: List<EdgeRange<STDCMEdge, STDCMEdge>>,
    stops: List<TrainStop>,
): TreeSet<FixedTimePoint> {
    val res = TreeSet<FixedTimePoint>()
    var prevStopTime = 0.0
    for (stop in stops) {
        res.add(makeFixedPoint(ranges, Offset(Distance.fromMeters(stop.position)), stop.duration))
        prevStopTime += stop.duration
    }
    return res
}

/** Create a new fixed point at a given offset. The reference time is fetched on the envelope. */
private fun makeFixedPoint(
    ranges: List<EdgeRange<STDCMEdge, STDCMEdge>>,
    conflictOffset: Offset<TravelledPath>,
    stopDuration: Double = 0.0
): FixedTimePoint {
    return FixedTimePoint(
        getTimeOnRanges(ranges, conflictOffset),
        conflictOffset,
        if (stopDuration > 0) stopDuration else null
    )
}

/** Returns the time expected during the exploration at the given offset */
private fun getTimeOnRanges(
    ranges: List<EdgeRange<STDCMEdge, STDCMEdge>>,
    offset: Offset<TravelledPath>,
): Double {
    var remainingDistance = offset.distance
    for (range in ranges) {
        assert(range.start.distance == 0.meters)
        if (remainingDistance <= range.end.distance) {
            val time = range.edge.getApproximateTimeAtLocation(Offset(remainingDistance))
            // We still have to account for departure time shift
            val totalDepartureTimeShift = ranges.last().edge.totalDepartureTimeShift
            val timeWithoutShift = time - totalDepartureTimeShift
            return timeWithoutShift
        }
        remainingDistance -= range.end.distance
    }
    throw java.lang.RuntimeException("unreachable")
}

/**
 * Looks for the first detected conflict that would happen on the given envelope. If a conflict is
 * found, returns its offset. Otherwise, returns null.
 */
private fun findConflictOffsets(
    graph: STDCMGraph,
    envelope: Envelope,
    blockAvailability: BlockAvailabilityInterface,
    ranges: List<EdgeRange<STDCMEdge, STDCMEdge>>,
    departureTime: Double,
    stops: List<TrainStop>
): Offset<TravelledPath>? {
    val envelopeWithStops = EnvelopeStopWrapper(envelope, stops)
    val startOffset = ranges[0].edge.envelopeStartOffset
    val endOffset =
        startOffset +
            Distance(
                millimeters =
                    ranges
                        .stream()
                        .mapToLong { range -> (range.end - range.start).millimeters }
                        .sum()
            )
    val explorer =
        ranges
            .last()
            .edge
            .infraExplorer
            .withEnvelope(
                envelopeWithStops,
                graph.fullInfra,
                graph.rollingStock,
                isSimulationComplete = true
            )
    assert(
        TrainPhysicsIntegrator.arePositionsEqual(
            envelopeWithStops.endPos,
            (endOffset - startOffset).meters
        )
    )
    val availability =
        blockAvailability.getAvailability(
            explorer,
            startOffset.cast(),
            endOffset.cast(),
            departureTime
        )
    val offsetDistance =
        (availability as? BlockAvailabilityInterface.Unavailable)?.firstConflictOffset
            ?: return null
    return offsetDistance
}

/**
 * Run a full simulation, with allowances configured to match the given fixed points. If isMareco is
 * set to true, the allowances follow the mareco distribution (more accurate but less reliable).
 */
private fun runSimulationWithFixedPoints(
    envelope: Envelope,
    fixedPoints: TreeSet<FixedTimePoint>,
    context: EnvelopeSimContext,
    isMareco: Boolean
): Envelope {
    val ranges = makeAllowanceRanges(envelope, fixedPoints)
    if (ranges.isEmpty()) return envelope
    val allowance =
        if (isMareco)
            MarecoAllowance(
                0.0,
                envelope.endPos,
                1.0, // Needs to be >0 to avoid problems when simulating low speeds
                ranges
            )
        else LinearAllowance(0.0, envelope.endPos, 0.0, ranges)
    return allowance.apply(envelope, context)
}

/** Create the list of `AllowanceRange`, with the given fixed points */
private fun makeAllowanceRanges(
    envelope: Envelope,
    fixedPoints: TreeSet<FixedTimePoint>
): List<AllowanceRange> {
    var transition = 0.0
    var transitionTime = 0.0
    val res = ArrayList<AllowanceRange>()
    for (point in fixedPoints) {
        val baseTime =
            envelope.interpolateTotalTimeClamp(point.offset.distance.meters) -
                envelope.interpolateTotalTimeClamp(transition)
        val pointArrivalTime = transitionTime + baseTime
        val neededDelay = point.time - pointArrivalTime

        res.add(
            AllowanceRange(
                transition,
                point.offset.distance.meters,
                AllowanceValue.FixedTime(neededDelay)
            )
        )

        transitionTime += baseTime + (point.stopTime ?: 0.0)
        transition = point.offset.distance.meters
    }
    if (transition < envelope.endPos)
        res.add(AllowanceRange(transition, envelope.endPos, AllowanceValue.FixedTime(0.0)))
    return res
}
