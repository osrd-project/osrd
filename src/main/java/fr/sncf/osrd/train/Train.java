package fr.sncf.osrd.train;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.sncf.osrd.simulation.*;
import fr.sncf.osrd.simulation.Simulation.TimelineEventCreated;
import fr.sncf.osrd.speedcontroller.SpeedController;
import fr.sncf.osrd.speedcontroller.SpeedDirective;
import fr.sncf.osrd.timetable.TrainSchedule;
import fr.sncf.osrd.utils.CryoList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Train extends AbstractEntity<Train, Train.TrainEntityID> {
    static final Logger logger = LoggerFactory.getLogger(Train.class);

    public static class TrainEntityID implements EntityID<Train> {
        public final String trainName;

        public TrainEntityID(String trainName) {
            this.trainName = trainName;
        }

        @Override
        public Train getEntity(Simulation sim) {
            return sim.trains.get(trainName);
        }
    }

    public String getName() {
        return id.trainName;
    }

    // how far the driver of the train can see
    public final double driverSightDistance;

    public final RollingStock rollingStock;
    public final TrainPath path;

    private TrainState lastState;

    // the candidate next state, which corresponds to the state of the train when
    // it goes through the next point of interest, such as a signal getting in sight range
    private TrainState nextState = null;

    // the timeline event for the train's arrival at the next point of interest
    private TimelineEvent<TrainLocationChange> nextMoveEvent = null;

    Train(
            @SuppressWarnings("SameParameterValue") double driverSightDistance,
            String name,
            Simulation sim,
            RollingStock rollingStock,
            TrainPath trainPath,
            double initialSpeed,
            List<SpeedController> controllers
    ) {
        super(new TrainEntityID(name));
        this.driverSightDistance = driverSightDistance;
        this.rollingStock = rollingStock;
        this.path = trainPath;
        var location = new TrainPositionTracker(sim.infra, trainPath, rollingStock.length);
        this.lastState = new TrainState(
                sim.getTime(),
                location,
                initialSpeed,
                TrainStatus.STARTING_UP,
                controllers,
                this
        );
        // the train must react to its own move events
        this.subscribers.add(this);
    }

    /**
     * Creates a train entity
     * @param sim the simulation
     * @param trainCreatedChange the change modeling the train's creation
     * @return A new train entity
     */
    public static Train createTrain(
            Simulation sim,
            TrainCreatedChange trainCreatedChange
    ) throws SimulationError {
        var train = trainCreatedChange.apply(sim);
        sim.publishChange(trainCreatedChange);
        train.planNextMove(sim);
        return train;
    }

    // region ENTITY_REACTOR

    void planNextMove(Simulation sim) throws SimulationError {
        if (lastState.status == TrainStatus.REACHED_DESTINATION) {
            logger.info("train {} reached destination, aborting planning", id);
            return;
        }

        logger.info("planning the next move for train {}", id);
        var moveEventChange = lastState.simulateUntilEvent(sim);
        var change = new TrainPlannedMoveChange(sim, this, moveEventChange);
        change.apply(sim, this);
        sim.publishChange(change);
    }

    @Override
    public void onEventOccurred(Simulation sim, TimelineEvent<?> event) throws SimulationError {
        if (event.value.getClass() == TrainLocationChange.class) {
            var locationChange = (TrainLocationChange) event.value;
            locationChange.apply(sim, this);
            sim.publishChange(locationChange);
            planNextMove(sim);
        }
    }

    @Override
    public void onEventCancelled(Simulation sim, TimelineEvent<?> event) throws SimulationError {
        if (event.value.getClass() == TrainLocationChange.class)
            planNextMove(sim);
    }

    // endregion

    // region CHANGES

    public static final class TrainCreatedChange extends SimChange<Train> {
        public final TrainSchedule schedule;
        public final TrainPath trainPath;
        public final CryoList<SpeedController> initialControllers;

        /**
         * A change corresponding to a train's creation.
         * @param sim the simulation
         * @param schedule the train's timetable
         * @param trainPath the path the train shall follow
         */
        public TrainCreatedChange(
                Simulation sim,
                TrainSchedule schedule,
                TrainPath trainPath,
                CryoList<SpeedController> initialControllers
        ) {
            super(sim);
            this.schedule = schedule;
            this.trainPath = trainPath;
            this.initialControllers = initialControllers;
        }

        @Override
        public Train apply(Simulation sim) {
            var trainName = schedule.name;

            var controllers = new ArrayList<>(initialControllers);

            var train = new Train(
                    400,
                    trainName,
                    sim,
                    schedule.rollingStock,
                    trainPath,
                    schedule.initialSpeed,
                    controllers
            );
            sim.trains.put(trainName, train);
            return train;
        }

        @Override
        public String toString() {
            return String.format("TrainCreatedChange { name=%s }", schedule.name);
        }
    }

    public static class TrainLocationChange extends EntityChange<Train, TrainEntityID, Void> {
        public final TrainState newState;

        @SuppressFBWarnings({"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
        public static final class PathValue<T> {
            public final double pathPosition;
            public final T value;

            public PathValue(double pathPosition, T value) {
                this.pathPosition = pathPosition;
                this.value = value;
            }
        }

        public static final class SpeedUpdate {
            public double pathPosition;
            public double time;
            public double speed;

            /**
             * A speed update
             * @param pathPosition the current position
             * @param time the current time
             * @param speed the current speed
             */
            public SpeedUpdate(double pathPosition, double time, double speed) {
                this.pathPosition = pathPosition;
                this.time = time;
                this.speed = speed;
            }

            public double interpolatePosition(double nextTime) {
                double delta = time - nextTime;
                return pathPosition + delta * speed;
            }
        }

        public static final class PathUpdates<T> extends CryoList<PathValue<T>> {
            private static final long serialVersionUID = -398512329955860429L;

            /**
             * Add an update, avoiding duplicates
             * @param pathPosition the position on the path
             * @param value the value at the given position
             */
            public void dedupAdd(double pathPosition, T value) {
                if (isEmpty()) {
                    add(new PathValue<>(pathPosition, value));
                    return;
                }

                var last = get(size() - 1);

                // only add the new value if it differs from the last one
                if (!last.value.equals(value))
                    add(new PathValue<>(pathPosition, value));
            }
        }

        public static final class SpeedUpdates extends CryoList<SpeedUpdate> {
            private static final long serialVersionUID = 1186037080779235871L;

            /**
             * Adds a speed update, avoiding duplicates
             * @param pathPosition the new position on the path
             * @param time the current time
             * @param speed the current speed
             */
            @SuppressFBWarnings({"FE_FLOATING_POINT_EQUALITY"})
            public void addSpeedUpdate(double pathPosition, double time, double speed) {
                if (isEmpty()) {
                    add(new SpeedUpdate(pathPosition, time, speed));
                    return;
                }

                var last = get(size() - 1);
                if (last.speed == speed)
                    return;

                add(new SpeedUpdate(pathPosition, time, speed));
            }
        }

        public final SpeedUpdates positionUpdates = new SpeedUpdates();
        public final PathUpdates<SpeedController[]> speedControllersUpdates = new PathUpdates<>();
        public final PathUpdates<SpeedDirective> speedDirectivesUpdates = new PathUpdates<>();

        /**
         * Finds the last speed update at a given time
         * @param time the reference time
         * @return the last speed update at a given time
         */
        public SpeedUpdate findLastSpeedUpdate(double time) {
            SpeedUpdate lastUpdate = null;

            for (var update : positionUpdates) {
                if (update.time > time)
                    break;
                lastUpdate = update;
            }
            assert lastUpdate != null;
            return lastUpdate;
        }

        /**
         * Creates a change corresponding to the movement of a train
         *
         * @param sim the simulation
         * @param train the train this movement is about
         * @param newState the state of the train after the change
         */
        public TrainLocationChange(
                Simulation sim,
                Train train,
                TrainState newState
        ) {
            super(sim, train.getID());
            this.newState = newState;
        }

        @Override
        public final Void apply(Simulation sim, Train train) {
            train.lastState = this.newState;
            train.nextState = null;
            train.nextMoveEvent = null;
            return null;
        }

        @Override
        public String toString() {
            return String.format(
                    "TrainLocationChange { speed=%.2f, newState.headPathPosition=%.2f }",
                    newState.speed,
                    newState.location.getHeadPathPosition()
            );
        }
    }

    public static final class TrainPlannedMoveChange extends EntityChange<
            Train, TrainEntityID, Void> {
        public final TimelineEventCreated<Train, TrainLocationChange> moveEventCreated;

        public TrainPlannedMoveChange(
                Simulation sim,
                Train train,
                TimelineEventCreated<Train, TrainLocationChange> moveEventCreated
        ) {
            super(sim, train.getID());
            this.moveEventCreated = moveEventCreated;
        }

        @Override
        public Void apply(Simulation sim, Train train) {
            train.nextMoveEvent = moveEventCreated.apply(sim, train);
            return null;
        }

        @Override
        public String toString() {
            return String.format("TrainPlannedMoveChange { moveEventCreation=%s }", moveEventCreated);
        }
    }

    // endregion
}
