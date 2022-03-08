package fr.sncf.osrd.new_infra;

import static fr.sncf.osrd.new_infra.api.Direction.BACKWARD;
import static fr.sncf.osrd.new_infra.api.Direction.FORWARD;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.Network;
import com.google.common.graph.NetworkBuilder;
import com.google.common.graph.Traverser;
import fr.sncf.osrd.new_infra.api.Direction;
import fr.sncf.osrd.new_infra.api.reservation.DiDetector;
import fr.sncf.osrd.new_infra.api.tracks.undirected.TrackEdge;
import fr.sncf.osrd.new_infra.api.tracks.undirected.TrackInfra;
import fr.sncf.osrd.new_infra.api.tracks.undirected.TrackNode;
import fr.sncf.osrd.new_infra.api.tracks.undirected.TrackSection;
import fr.sncf.osrd.new_infra.implementation.tracks.undirected.*;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class InfraHelpers {

    /** Returns the InfraTrackSection with the given ID, throws if it can't be found */
    public static TrackSection getTrack(TrackInfra infra, String id) {
        return infra.getTrackSection(id);
    }

    /** Returns the same graph as g, but undirected */
    public static Network<TrackNode, TrackEdge> toUndirected(Network<TrackNode, TrackEdge> g) {
        ImmutableNetwork.Builder<TrackNode, TrackEdge> builder = NetworkBuilder
                .undirected()
                .immutable();
        for (var n : g.nodes())
            builder.addNode(n);
        for (var e : g.edges())
            builder.addEdge(g.incidentNodes(e), e);
        return builder.build();
    }

    /** Asserts that `values` contains every element from `included` and none from `excluded`.
     * Values that don't appear in either of those are ignored. */
    public static <T> void assertSetMatch(Iterable<T> values, Set<T> included, Set<T> excluded) {
        var valuesSet = StreamSupport.stream(values.spliterator(), false).collect(Collectors.toSet());
        for (var x : included)
            assertTrue(valuesSet.contains(x));
        for (var x : excluded)
            assertFalse(valuesSet.contains(x));
    }

    /** Make a switch infra with the following configuration:
    *      Out1
    *       ^
    *       |
    *      In1
    *      /  ^
    *     /    \
    *    v      \
    *   In2     In3
    *    |       |
    *    v       v
    *    3       3
     */
    public static TrackInfra makeSwitchInfra() {
        var builder = NetworkBuilder
                .directed()
                .<TrackNode, TrackEdge>immutable();

        final var nodeIn1 = new SwitchPortImpl("1");
        final var nodeIn2 = new SwitchPortImpl("2");
        final var nodeIn3 = new SwitchPortImpl("3");
        final var nodeOut1 = new TrackNodeImpl.Joint();
        final var nodeOut2 = new TrackNodeImpl.Joint();
        final var nodeOut3 = new TrackNodeImpl.Joint();
        builder.addNode(nodeIn1);
        builder.addNode(nodeIn2);
        builder.addNode(nodeIn3);
        builder.addNode(nodeOut1);
        builder.addNode(nodeOut2);
        builder.addNode(nodeOut3);
        builder.addEdge(nodeIn1, nodeOut1, new TrackSectionImpl(0, "1"));
        builder.addEdge(nodeIn2, nodeOut2, new TrackSectionImpl(0, "2"));
        builder.addEdge(nodeIn3, nodeOut3, new TrackSectionImpl(0, "3"));
        builder.addEdge(nodeIn1, nodeIn2, new SwitchBranchImpl());
        builder.addEdge(nodeIn3, nodeIn1, new SwitchBranchImpl());

        return TrackInfraImpl.from(null, builder.build());
    }

    /** Get the value in the map, throw if absent */
    public static <T, U> T safeGet(ImmutableMap<U, T> m, U x) {
        var res = m.get(x);
        if (res == null)
            throw new RuntimeException("Unexpected null value");
        return res;
    }

    /** Tests that the right DiDetectors can be reached in an oriented graph based on tiny infra.
     * Can be used for different route types */
    public static <T> void testTinyInfraDiDetectorGraph(
            ImmutableNetwork<DiDetector, T> graph,
            ImmutableMap<Direction, ImmutableMap<String, DiDetector>> diDetectors
    ) {
        var bufferStopA = safeGet(safeGet(diDetectors, FORWARD), "buffer_stop_a");
        var bufferStopB = safeGet(safeGet(diDetectors, FORWARD), "buffer_stop_b");
        var bufferStopC = safeGet(safeGet(diDetectors, FORWARD), "buffer_stop_c");
        var bufferStopABackward = safeGet(safeGet(diDetectors, BACKWARD), "buffer_stop_a");
        var bufferStopBBackward = safeGet(safeGet(diDetectors, BACKWARD), "buffer_stop_b");
        var bufferStopCBackward = safeGet(safeGet(diDetectors, BACKWARD), "buffer_stop_c");
        var allDirectedBufferStops = Set.of(
                bufferStopA,
                bufferStopB,
                bufferStopC,
                bufferStopABackward,
                bufferStopBBackward,
                bufferStopCBackward
        );
        var reachableFromA = Set.of(
                bufferStopA,
                bufferStopC
        );
        var reachableFromB = Set.of(
                bufferStopB,
                bufferStopC
        );
        var reachableFromC = Set.of(
                bufferStopABackward,
                bufferStopBBackward,
                bufferStopCBackward
        );
        final var traverser = Traverser.forGraph(graph);
        assertSetMatch(
                traverser.breadthFirst(bufferStopA),
                reachableFromA,
                Sets.difference(allDirectedBufferStops, reachableFromA)
        );
        assertSetMatch(
                traverser.breadthFirst(bufferStopB),
                reachableFromB,
                Sets.difference(allDirectedBufferStops, reachableFromB)
        );
        assertSetMatch(
                traverser.breadthFirst(bufferStopCBackward),
                reachableFromC,
                Sets.difference(allDirectedBufferStops, reachableFromC)
        );
    }
}
