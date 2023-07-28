package fr.sncf.osrd.standalone_sim;

import static fr.sncf.osrd.Helpers.fullInfraFromRJS;
import static fr.sncf.osrd.envelope_sim.MaxEffortEnvelopeBuilder.makeSimpleMaxEffortEnvelope;
import static fr.sncf.osrd.sim_infra.api.PathPropertiesKt.makeTrackLocation;

import fr.sncf.osrd.Helpers;
import fr.sncf.osrd.api.FullInfra;
import fr.sncf.osrd.envelope.Envelope;
import fr.sncf.osrd.envelope_sim.SimpleContextBuilder;
import fr.sncf.osrd.sim_infra.api.PathProperties;
import fr.sncf.osrd.sim_infra.api.TrackInfraKt;
import fr.sncf.osrd.train.RollingStock;
import fr.sncf.osrd.train.StandaloneTrainSchedule;
import fr.sncf.osrd.train.TestTrains;
import fr.sncf.osrd.utils.units.Distance;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

public class ScheduleMetadataExtractorTests {

    @Test
    public void tinyInfraTests() throws Exception {
        var rjsInfra = Helpers.getExampleInfra("tiny_infra/infra.json");
        var infra = fullInfraFromRJS(rjsInfra);
        var barA = TrackInfraKt.getTrackSectionFromNameOrThrow("ne.micro.bar_a", infra.rawInfra());
        var fooA = TrackInfraKt.getTrackSectionFromNameOrThrow("ne.micro.foo_a", infra.rawInfra());
        var path = Helpers.pathFromRoutes(
                infra.rawInfra(),
                List.of(
                        "rt.buffer_stop_c->tde.track-bar",
                        "rt.tde.track-bar->tde.switch_foo-track",
                        "rt.tde.switch_foo-track->buffer_stop_a"
                ),
                makeTrackLocation(barA, 200),
                makeTrackLocation(fooA, 0)
        );
        var testContext = SimpleContextBuilder.makeSimpleContext(Distance.toMeters(path.getLength()), 0);
        var testRollingStock = TestTrains.REALISTIC_FAST_TRAIN;
        var envelope = makeSimpleMaxEffortEnvelope(
                testContext,
                testRollingStock.maxSpeed, new double[]{}
        );
        makeRouteOccupancy(infra, path, testRollingStock, envelope);
    }

    @Test
    public void tinyInfraEndsInMiddleRoutesTests() throws Exception {
        var rjsInfra = Helpers.getExampleInfra("tiny_infra/infra.json");
        var infra = fullInfraFromRJS(rjsInfra);
        var barA = TrackInfraKt.getTrackSectionFromNameOrThrow("ne.micro.bar_a", infra.rawInfra());
        var fooA = TrackInfraKt.getTrackSectionFromNameOrThrow("ne.micro.foo_a", infra.rawInfra());
        var path = Helpers.pathFromRoutes(
                infra.rawInfra(),
                List.of(
                        "rt.buffer_stop_c->tde.track-bar",
                        "rt.tde.track-bar->tde.switch_foo-track",
                        "rt.tde.switch_foo-track->buffer_stop_a"
                ),
                makeTrackLocation(barA, 100),
                makeTrackLocation(fooA, 100)
        );
        var testContext = SimpleContextBuilder.makeSimpleContext(Distance.toMeters(path.getLength()), 0);
        var testRollingStock = TestTrains.REALISTIC_FAST_TRAIN;
        var envelope = makeSimpleMaxEffortEnvelope(
                testContext,
                testRollingStock.maxSpeed, new double[]{}
        );
        // We only check that no assertion is thrown in the validation
        makeRouteOccupancy(infra, path, testRollingStock, envelope);
    }

    @Test
    public void veryLongTrainTests() throws Exception {
        var rjsInfra = Helpers.getExampleInfra("tiny_infra/infra.json");
        var infra = fullInfraFromRJS(rjsInfra);
        var barA = TrackInfraKt.getTrackSectionFromNameOrThrow("ne.micro.bar_a", infra.rawInfra());
        var fooA = TrackInfraKt.getTrackSectionFromNameOrThrow("ne.micro.foo_a", infra.rawInfra());
        var path = Helpers.pathFromRoutes(
                infra.rawInfra(),
                List.of(
                        "rt.buffer_stop_c->tde.track-bar",
                        "rt.tde.track-bar->tde.switch_foo-track",
                        "rt.tde.switch_foo-track->buffer_stop_a"
                ),
                makeTrackLocation(barA, 100),
                makeTrackLocation(fooA, 100)
        );
        var testContext = SimpleContextBuilder.makeSimpleContext(Distance.toMeters(path.getLength()), 0);
        var testRollingStock = TestTrains.VERY_LONG_FAST_TRAIN;
        var envelope = makeSimpleMaxEffortEnvelope(
                testContext,
                testRollingStock.maxSpeed, new double[]{}
        );
        // We only check that no assertion is thrown in the validation
        makeRouteOccupancy(infra, path, testRollingStock, envelope);
    }

    @Test
    public void oneLineInfraTests() throws Exception {
        final var rjsInfra = Helpers.getExampleInfra("one_line/infra.json");
        final var infra = fullInfraFromRJS(rjsInfra);
        final var routes = new ArrayList<String>();
        routes.add("rt.buffer_stop.0->detector.0");
        for (int i = 0; i < 9; i++)
            routes.add(String.format("rt.detector.%d->detector.%d", i, i + 1));
        routes.add("rt.detector.9->buffer_stop.1");
        var path = Helpers.pathFromRoutes(
                infra.rawInfra(),
                routes,
                makeTrackLocation(TrackInfraKt.getTrackSectionFromNameOrThrow("track.0", infra.rawInfra()), 0),
                makeTrackLocation(TrackInfraKt.getTrackSectionFromNameOrThrow("track.9", infra.rawInfra()), 0)
        );
        var testContext = SimpleContextBuilder.makeSimpleContext(Distance.toMeters(path.getLength()), 0);
        var testRollingStock = TestTrains.REALISTIC_FAST_TRAIN;
        var envelope = makeSimpleMaxEffortEnvelope(
                testContext,
                testRollingStock.maxSpeed, new double[]{}
        );
        // We only check that no assertion is thrown in the validation
        makeRouteOccupancy(infra, path, testRollingStock, envelope);
    }

    @Test
    public void veryShortPathTests() throws Exception {
        var rjsInfra = Helpers.getExampleInfra("one_line/infra.json");
        var infra = fullInfraFromRJS(rjsInfra);
        var routes = List.of("rt.buffer_stop.0->detector.0");
        var path = Helpers.pathFromRoutes(
                infra.rawInfra(),
                routes,
                makeTrackLocation(TrackInfraKt.getTrackSectionFromNameOrThrow("track.0", infra.rawInfra()), 0),
                makeTrackLocation(TrackInfraKt.getTrackSectionFromNameOrThrow("track.0", infra.rawInfra()), 10)
        );
        var testContext = SimpleContextBuilder.makeSimpleContext(Distance.toMeters(path.getLength()), 0);
        var testRollingStock = TestTrains.REALISTIC_FAST_TRAIN;
        var envelope = makeSimpleMaxEffortEnvelope(
                testContext,
                testRollingStock.maxSpeed, new double[]{}
        );
        // We only check that no assertion is thrown in the validation
        makeRouteOccupancy(infra, path, testRollingStock, envelope);
    }


    private static void makeRouteOccupancy(
            FullInfra fullInfra,
            PathProperties path, RollingStock testRollingStock, Envelope envelope
    ) {
        var schedule = new StandaloneTrainSchedule(
                testRollingStock, 0, new ArrayList<>(), List.of(), List.of(),
                "test", RollingStock.Comfort.STANDARD, null, null
        );
        ScheduleMetadataExtractor.run(envelope, path, schedule, fullInfra);
    }
}
