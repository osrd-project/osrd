package fr.sncf.osrd.api.pathfinding;

import fr.sncf.osrd.infra.api.signaling.SignalingRoute;
import fr.sncf.osrd.railjson.schema.geom.Point;
import fr.sncf.osrd.reporting.exceptions.NotImplemented;
import fr.sncf.osrd.utils.graph.functional_interfaces.AStarHeuristic;
import fr.sncf.osrd.utils.graph.Pathfinding;
import java.util.Collection;

/** This is a function object that estimates the remaining distance to the closest target point,
 * using geo data. It is used as heuristic for A*. */
public class RemainingDistanceEstimator implements AStarHeuristic<SignalingRoute> {

    private final Collection<Point> targets;
    private final double remainingDistance;

    /** Constructor */
    public RemainingDistanceEstimator(
            Collection<Pathfinding.EdgeLocation<SignalingRoute>> edgeLocations,
            double remainingDistance
    ) {
        targets = edgeLocations.stream()
                .map(loc -> routeOffsetToPoint(loc.edge(), loc.offset()))
                .toList();
        this.remainingDistance = remainingDistance;
    }

    /** Converts a route and offset from its start into a geo point */
    private static Point routeOffsetToPoint(SignalingRoute route, double pointOffset) {
        for (var trackRange : route.getInfraRoute().getTrackRanges()) {
            if (pointOffset <= trackRange.getLength()) {
                var trackLocation = trackRange.offsetLocation(pointOffset);
                var normalizedOffset = trackLocation.offset() / trackLocation.track().getLength();
                var geo = trackLocation.track().getGeo();
                if (geo == null)
                    return new Point(0, 0);
                return geo.interpolateNormalized(normalizedOffset);
            }
            pointOffset -= trackRange.getLength();
        }
        throw new RuntimeException("Couldn't find offset on route");
    }

    /** Compute the minimum geo distance between two steps */
    public static double minDistanceBetweenSteps(
            Collection<Pathfinding.EdgeLocation<SignalingRoute>> step1,
            Collection<Pathfinding.EdgeLocation<SignalingRoute>> step2
    ) {
        var step1Points = step1.stream()
                .map(loc -> routeOffsetToPoint(loc.edge(), loc.offset()))
                .toList();
        var step2Points = step2.stream()
                .map(loc -> routeOffsetToPoint(loc.edge(), loc.offset()))
                .toList();

        var res = Double.POSITIVE_INFINITY;
        for (var point1: step1Points) {
            for (var point2: step2Points) {
                res = Double.min(res, point1.distanceAsMeters(point2));
            }
        }
        return res;
    }

    @Override
    public double apply(SignalingRoute signalingRoute, double offset) {
        var res = Double.POSITIVE_INFINITY;
        var point = routeOffsetToPoint(signalingRoute, offset);
        for (var target : targets)
            res = Double.min(res, point.distanceAsMeters(target));
        return res + remainingDistance;
    }
}
