package fr.sncf.osrd.api.stdcm;

import com.google.common.collect.Multimap;
import fr.sncf.osrd.api.pathfinding.RemainingDistanceEstimator;
import fr.sncf.osrd.envelope.Envelope;
import fr.sncf.osrd.envelope.part.EnvelopePart;
import fr.sncf.osrd.envelope_sim.EnvelopeSimContext;
import fr.sncf.osrd.envelope_sim.PhysicsPath;
import fr.sncf.osrd.envelope_sim.pipelines.MaxSpeedEnvelope;
import fr.sncf.osrd.envelope_sim_infra.EnvelopeTrainPath;
import fr.sncf.osrd.infra.api.signaling.SignalingInfra;
import fr.sncf.osrd.infra.api.signaling.SignalingRoute;
import fr.sncf.osrd.infra.implementation.tracks.directed.TrackRangeView;
import fr.sncf.osrd.infra_state.api.TrainPath;
import fr.sncf.osrd.infra_state.implementation.TrainPathBuilder;
import fr.sncf.osrd.train.RollingStock;
import fr.sncf.osrd.utils.graph.Pathfinding;
import fr.sncf.osrd.utils.graph.functional_interfaces.TargetsOnEdge;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** This class combines all the (static) methods used to find a path in the STDCM graph. */
public class STDCMPathfinding {

    /** Given an infra, a rolling stock and a collection of unavailable time for each route,
     * find a path made of a sequence of route ranges with a matching envelope.
     * Returns null if no path is found.
     * */
    public static STDCMResult findPath(
            SignalingInfra infra,
            RollingStock rollingStock,
            double startTime,
            double endTime,
            Set<Pathfinding.EdgeLocation<SignalingRoute>> startLocations,
            Set<Pathfinding.EdgeLocation<SignalingRoute>> endLocations,
            Multimap<SignalingRoute, OccupancyBlock> unavailableTimes,
            double timeStep
    ) {
        var graph = new STDCMGraph(infra, rollingStock, timeStep, unavailableTimes);
        var remainingDistance = new RemainingDistanceEstimator(endLocations);
        var path = new Pathfinding<>(graph)
                .setEdgeToLength(edge -> edge.route().getInfraRoute().getLength())
                .setRemainingDistanceEstimator((edge, offset) -> remainingDistance.apply(edge.route(), offset))
                .runPathfinding(
                        convertLocations(graph, startLocations, startTime),
                        makeObjectiveFunction(endLocations)
                );
        if (path == null)
            return null;
        return makeResult(path, rollingStock, timeStep, startTime);
    }

    /** Make the objective function from the edge locations */
    private static List<TargetsOnEdge<STDCMGraph.Edge>> makeObjectiveFunction(
            Set<Pathfinding.EdgeLocation<SignalingRoute>> endLocations
    ) {
        return List.of(edge -> {
            var res = new HashSet<Double>();
            for (var loc : endLocations)
                if (loc.edge().equals(edge.route()))
                    res.add(loc.offset());
            return res;
        });
    }

    /** Builds the STDCM result object from the raw pathfinding result */
    private static STDCMResult makeResult(
            Pathfinding.Result<STDCMGraph.Edge> path,
            RollingStock rollingStock,
            double timeStep,
            double startTime
    ) {
        var routeRanges = path.ranges().stream()
                .map(x -> new Pathfinding.EdgeRange<>(x.edge().route(), x.start(), x.end()))
                .toList();
        var routeWaypoints = path.waypoints().stream()
                .map(x -> new Pathfinding.EdgeLocation<>(x.edge().route(), x.offset()))
                .toList();
        var physicsPath = makePhysicsPath(path.ranges());
        return new STDCMResult(
                new Pathfinding.Result<>(routeRanges, routeWaypoints),
                makeFinalEnvelope(path.ranges(), rollingStock, physicsPath, timeStep),
                makeTrainPath(path.ranges()),
                physicsPath,
                computeDepartureTime(path, startTime)
        );
    }

    /** Builds the final envelope, assembling the parts together and adding any missing braking curves */
    private static Envelope makeFinalEnvelope(
            List<Pathfinding.EdgeRange<STDCMGraph.Edge>> edges,
            RollingStock rollingStock,
            PhysicsPath physicsPath,
            double timeStep
    ) {
        var parts = new ArrayList<EnvelopePart>();
        double offset = 0;
        for (var edge : edges) {
            var envelope = Envelope.make(edge.edge().envelope().slice(0, Math.abs(edge.end() - edge.start())));
            for (var part : envelope)
                parts.add(part.copyAndShift(offset));
            offset += edge.edge().envelope().getEndPos();
        }
        var newEnvelope = Envelope.make(parts.toArray(new EnvelopePart[0]));
        var finalEnvelope = addBrakingCurves(newEnvelope, rollingStock, physicsPath, timeStep);
        assert finalEnvelope.continuous;
        return finalEnvelope;
    }

    /** Adds any missing braking curves to the envelope (including the last stop).
     * Until this step we may have discontinuities if the MRSP decreases right after a route transition. */
    private static Envelope addBrakingCurves(
            Envelope envelope,
            RollingStock rollingStock,
            PhysicsPath physicsPath,
            double timeStep
    ) {
        var context = new EnvelopeSimContext(rollingStock, physicsPath, timeStep);
        return MaxSpeedEnvelope.from(context, new double[]{envelope.getEndPos()}, envelope);
    }

    /** Converts the list of pathfinding edges into a list of TrackRangeView that covers the path exactly */
    private static List<TrackRangeView> makeTrackRanges(
            List<Pathfinding.EdgeRange<STDCMGraph.Edge>> edges
    ) {
        var trackRanges = new ArrayList<TrackRangeView>();
        for (var routeRange : edges) {
            var infraRoute = routeRange.edge().route().getInfraRoute();
            trackRanges.addAll(infraRoute.getTrackRanges(routeRange.start(), routeRange.end()));
        }
        return trackRanges;
    }

    /** Builds a PhysicsPath from the pathfinding edges */
    private static PhysicsPath makePhysicsPath(
            List<Pathfinding.EdgeRange<STDCMGraph.Edge>> edges
    ) {
        return EnvelopeTrainPath.from(makeTrackRanges(edges));
    }

    /** Creates a TrainPath instance from the list of pathfinding edges */
    private static TrainPath makeTrainPath(
            List<Pathfinding.EdgeRange<STDCMGraph.Edge>> ranges
    ) {
        var routeList = ranges.stream()
                .map(edge -> edge.edge().route())
                .toList();
        var trackRanges = makeTrackRanges(ranges);
        var lastRange = trackRanges.get(trackRanges.size() - 1);
        return TrainPathBuilder.from(
                routeList,
                trackRanges.get(0).offsetLocation(0),
                lastRange.offsetLocation(lastRange.getLength())
        );
    }


    /** Converts locations on a SignalingRoute into a location on a STDCMGraph.Edge. */
    private static Set<Pathfinding.EdgeLocation<STDCMGraph.Edge>> convertLocations(
            STDCMGraph graph,
            Set<Pathfinding.EdgeLocation<SignalingRoute>> locations,
            double startTime
    ) {
        var res = new HashSet<Pathfinding.EdgeLocation<STDCMGraph.Edge>>();
        for (var location : locations) {
            var start = location.offset();
            var maximumDelay = 3600 * 24; // Placeholder, there will probably be a parameter eventually
            for (var edge : graph.makeEdges(location.edge(), startTime, 0, start, maximumDelay))
                res.add(new Pathfinding.EdgeLocation<>(edge, location.offset()));
        }
        return res;
    }

    /** Computes the departure time, made of the sum of all delays added over the path */
    private static double computeDepartureTime(Pathfinding.Result<STDCMGraph.Edge> path, double startTime) {
        for (var ranges : path.ranges()) {
            startTime += ranges.edge().addedDelay();
        }
        return startTime;
    }
}
