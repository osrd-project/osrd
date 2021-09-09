package fr.sncf.osrd.api;

import com.squareup.moshi.Json;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.sncf.osrd.infra.StopActionPoint.RestartTrainEvent.RestartTrainPlanned;
import fr.sncf.osrd.train.TrainSchedule;
import fr.sncf.osrd.api.InfraManager.InfraLoadException;
import fr.sncf.osrd.infra.Infra;
import fr.sncf.osrd.infra.SuccessionTable;
import fr.sncf.osrd.infra.routegraph.Route;
import fr.sncf.osrd.infra_state.RouteState;
import fr.sncf.osrd.infra_state.RouteStatus;
import fr.sncf.osrd.infra_state.SignalState;
import fr.sncf.osrd.railjson.parser.RJSSimulationParser;
import fr.sncf.osrd.railjson.parser.RJSSuccessionsParser;
import fr.sncf.osrd.railjson.parser.exceptions.InvalidRollingStock;
import fr.sncf.osrd.railjson.parser.exceptions.InvalidSchedule;
import fr.sncf.osrd.railjson.parser.exceptions.InvalidSuccession;
import fr.sncf.osrd.railjson.schema.RJSSimulation;
import fr.sncf.osrd.railjson.schema.RJSSuccessions;
import fr.sncf.osrd.railjson.schema.common.ID;
import fr.sncf.osrd.railjson.schema.rollingstock.RJSRollingResistance;
import fr.sncf.osrd.railjson.schema.rollingstock.RJSRollingStock;
import fr.sncf.osrd.railjson.schema.schedule.RJSAllowance;
import fr.sncf.osrd.railjson.schema.schedule.RJSTrainPhase;
import fr.sncf.osrd.railjson.schema.schedule.RJSTrainSchedule;
import fr.sncf.osrd.railjson.schema.successiontable.RJSSuccessionTable;
import fr.sncf.osrd.simulation.Change;
import fr.sncf.osrd.simulation.Simulation;
import fr.sncf.osrd.simulation.SimulationError;
import fr.sncf.osrd.simulation.changelog.ChangeConsumer;
import fr.sncf.osrd.simulation.changelog.ChangeConsumerMultiplexer;
import fr.sncf.osrd.train.Train;
import fr.sncf.osrd.train.events.TrainCreatedEvent;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqPrint;
import org.takes.rs.RsJson;
import org.takes.rs.RsText;
import org.takes.rs.RsWithBody;
import org.takes.rs.RsWithStatus;

import java.io.IOException;
import java.util.*;

public class SimulationEndpoint implements Take {
    private final InfraManager infraManager;

    public static final JsonAdapter<SimulationRequest> adapterRequest = new Moshi
            .Builder()
            .add(ID.Adapter.FACTORY)
            .add(RJSRollingResistance.adapter)
            .add(RJSTrainPhase.adapter)
            .add(RJSAllowance.adapter)
            .build()
            .adapter(SimulationRequest.class);

    public static final JsonAdapter<SimulationResult> adapterResult = new Moshi
            .Builder()
            .build()
            .adapter(SimulationResult.class);

    public SimulationEndpoint(InfraManager infraManager) {
        this.infraManager = infraManager;
    }

    @Override
    public Response act(Request req) throws
            IOException,
            InvalidRollingStock,
            InvalidSchedule,
            InvalidSuccession,
            SimulationError {
        try {
            // Parse request input
            var body = new RqPrint(req).printBody();
            var request = adapterRequest.fromJson(body);
            if (request == null)
                return new RsWithStatus(new RsText("missing request body"), 400);

            // load infra
            Infra infra;
            try {
                infra = infraManager.load(request.infra);
            } catch (InfraLoadException | InterruptedException e) {
                return new RsWithStatus(new RsText(
                        String.format("Error loading infrastructure '%s'%n%s", request.infra, e.getMessage())), 400);
            }

            // load train schedules
            var rjsSimulation = new RJSSimulation(request.rollingStocks, request.trainSchedules);
            var trainSchedules = RJSSimulationParser.parse(infra, rjsSimulation);

            // load trains successions tables
            var successions = new ArrayList<SuccessionTable>();
            if (request.successions != null) {
                var rjsSuccessions = new RJSSuccessions(request.successions);
                successions = RJSSuccessionsParser.parse(rjsSuccessions);
            }

            // create the simulation and his changelog
            var changeConsumers = new ArrayList<ChangeConsumer>();
            var multiplexer = new ChangeConsumerMultiplexer(changeConsumers);
            var sim = Simulation.createFromInfraAndSuccessions(infra, successions, 0, multiplexer);
            var resultLog = new ArrayResultLog(infra, sim);
            multiplexer.add(resultLog);

            // insert the train start events into the simulation
            for (var trainSchedule : trainSchedules)
                TrainCreatedEvent.plan(sim, trainSchedule);

            // run the simulation loop
            while (!sim.isSimulationOver())
                sim.step();

            // Check number of reached stops is what we expect
            resultLog.validate();

            // TODO Simplify data
            // resultLog.simplify();

            return new RsJson(new RsWithBody(adapterResult.toJson(resultLog.getResult())));
        } catch (Throwable ex) {
            ex.printStackTrace(System.err);
            throw ex;
        }
    }


    public static final class SimulationRequest {
        /** Infra id */
        public final String infra;

        /** A list of rolling stocks involved in this simulation */
        @Json(name = "rolling_stocks")
        public Collection<RJSRollingStock> rollingStocks;

        /** A list of trains plannings */
        @Json(name = "train_schedules")
        public Collection<RJSTrainSchedule> trainSchedules;

        /** A list of trains successions tables */
        @Json(name = "successions")
        public Collection<RJSSuccessionTable> successions;

        /** Create SimulationRequest with empty successions tables */
        public SimulationRequest(
                String infra,
                Collection<RJSRollingStock> rollingStocks,
                Collection<RJSTrainSchedule> trainSchedules
        ) {
            this.infra = infra;
            this.rollingStocks = rollingStocks;
            this.trainSchedules = trainSchedules;
            this.successions = null;
        }
    }


    private static final class ArrayResultLog extends ChangeConsumer {
        private final SimulationResult result = new SimulationResult();
        private final Infra infra;
        private final HashMap<String, TrainSchedule> trainSchedules = new HashMap<>();
        private final Simulation sim;

        private ArrayResultLog(Infra infra, Simulation sim) {
            this.infra = infra;
            this.sim = sim;
        }

        private SimulationResultTrain getTrainResult(String trainId) {
            var trainResult = result.trains.get(trainId);
            if (trainResult == null) {
                trainResult = new SimulationResultTrain();
                result.trains.put(trainId, trainResult);
            }
            return trainResult;
        }

        public void validate() throws SimulationError {
            for (var trainName : result.trains.keySet()) {
                var trainResult = result.trains.get(trainName);
                var nStopReached = trainResult.stopReaches.size();
                var trainSchedule = trainSchedules.get(trainName);
                var expectedStopReached = trainSchedule.stops.size();
                if (nStopReached != expectedStopReached) {
                    var err = String.format("Train '%s', unexpected stop number: expected %d, got %d",
                            trainName, expectedStopReached, nStopReached);
                    throw new SimulationError(err);
                }
            }
        }

        @Override
        public void changeCreationCallback(Change change) { }

        @Override
        @SuppressFBWarnings({"BC_UNCONFIRMED_CAST"})
        public void changePublishedCallback(Change change) {
            if (change.getClass() == RouteState.RouteStatusChange.class) {
                var routeStatusChange = (RouteState.RouteStatusChange) change;
                var route = infra.routeGraph.getEdge(routeStatusChange.routeIndex);
                var newStatus = routeStatusChange.newStatus;
                result.routesStatus.add(new SimulationResultRouteStatus(sim.getTime(), route, newStatus));
            } else if (change.getClass() == Train.TrainStateChange.class) {
                var trainStateChange = (Train.TrainStateChange) change;
                var trainResult = getTrainResult(trainStateChange.trainID);
                var train = trainSchedules.get(trainStateChange.trainID);
                for (var pos : trainStateChange.positionUpdates) {
                    trainResult.positions.add(new SimulationResultPosition(pos.time, pos.pathPosition, train));
                    trainResult.speeds.add(new SimulationResultSpeed(pos.time, pos.speed));
                }
            } else if (change.getClass() == TrainCreatedEvent.TrainCreationPlanned.class) {
                var trainCreationPlanned = (TrainCreatedEvent.TrainCreationPlanned) change;
                trainSchedules.put(trainCreationPlanned.schedule.trainID, trainCreationPlanned.schedule);
            } else if (change.getClass() == SignalState.SignalAspectChange.class) {
                var aspectChange = (SignalState.SignalAspectChange) change;
                var signal = infra.signals.get(aspectChange.signalIndex).id;
                var aspects = new ArrayList<String>();
                for (var aspect : aspectChange.aspects)
                    aspects.add(aspect.id);
                result.signalChanges.add(new SimulationResultSignalChange(sim.getTime(), signal, aspects));
            } else if (change.getClass() == RestartTrainPlanned.class) {
                var stopReached = (RestartTrainPlanned) change;
                var trainResult = getTrainResult(stopReached.train.getName());
                trainResult.stopReaches.add(new SimulationResultStopReach(sim.getTime(), stopReached.stopIndex));
            }
        }

        public SimulationResult getResult() {
            return result;
        }
    }

    public static class SimulationResult {
        public Map<String, SimulationResultTrain> trains = new HashMap<>();
        @Json(name = "routes_status")
        public Collection<SimulationResultRouteStatus> routesStatus = new ArrayList<>();
        @Json(name = "signal_changes")
        public Collection<SimulationResultSignalChange> signalChanges = new ArrayList<>();
    }

    public static class SimulationResultTrain {
        public Collection<SimulationResultSpeed> speeds = new ArrayList<>();
        public Collection<SimulationResultPosition> positions = new ArrayList<>();
        @Json(name = "stop_reaches")
        public Collection<SimulationResultStopReach> stopReaches = new ArrayList<>();
    }

    public static class SimulationResultSpeed {
        public final double time;
        public final double speed;

        public SimulationResultSpeed(double time, double speed) {
            this.time = time;
            this.speed = speed;
        }
    }

    public static class SimulationResultPosition {
        public final double time;
        @Json(name = "head_track_section")
        public final String headTrackSection;
        @Json(name = "head_offset")
        public final double headOffset;
        @Json(name = "tail_track_section")
        public final String tailTrackSection;
        @Json(name = "tail_offset")
        public final double tailOffset;
        transient public final double pathOffset;

        SimulationResultPosition(double time, double pathOffset, TrainSchedule trainSchedule) {
            this.time = time;
            this.pathOffset = pathOffset;
            var headLocation = trainSchedule.plannedPath.findLocation(pathOffset);
            this.headTrackSection = headLocation.edge.id;
            this.headOffset = headLocation.offset;
            var tailLocation = trainSchedule.plannedPath.findLocation(
                    Math.max(0, pathOffset - trainSchedule.rollingStock.length));
            this.tailTrackSection = tailLocation.edge.id;
            this.tailOffset = tailLocation.offset;
        }
    }

    public static class SimulationResultRouteStatus {
        public final double time;
        @Json(name = "route_id")
        private final String routeId;
        private final RouteStatus status;
        @Json(name = "start_track_section")
        private final String startTrackSection;
        @Json(name = "start_offset")
        private final double startOffset;
        @Json(name = "end_track_section")
        private final String endTrackSection;
        @Json(name = "end_offset")
        private final double endOffset;

        SimulationResultRouteStatus(double time, Route route, RouteStatus status) {
            this.time = time;
            this.routeId = route.id;
            this.status = status;
            var start = route.tvdSectionsPaths.get(0).trackSections[0];
            this.startTrackSection = start.edge.id;
            this.startOffset = start.getBeginPosition();
            var lastIndex = route.tvdSectionsPaths.size() - 1;
            var lastTracks = route.tvdSectionsPaths.get(lastIndex).trackSections;
            var end = lastTracks[lastTracks.length - 1];
            this.endTrackSection = end.edge.id;
            this.endOffset = end.getEndPosition();
        }
    }

    public static class SimulationResultSignalChange {
        public final double time;
        @Json(name = "signal_id")
        public final String signalId;
        public final List<String> aspects;

        SimulationResultSignalChange(double time, String signalId, List<String> aspects) {
            this.time = time;
            this.signalId = signalId;
            this.aspects = aspects;
        }
    }

    public static class SimulationResultStopReach {
        public final double time;
        @Json(name = "stop_index")
        private final int stopIndex;

        public SimulationResultStopReach(double time, int stopIndex) {
            this.time = time;
            this.stopIndex = stopIndex;
        }
    }
}

