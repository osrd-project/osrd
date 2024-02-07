package fr.sncf.osrd.standalone_sim;

import fr.sncf.osrd.envelope.EnvelopeTimeInterpolate;
import fr.sncf.osrd.train.TrainStop;
import java.util.ArrayList;
import java.util.List;

public class EnvelopeStopWrapper implements EnvelopeTimeInterpolate {
    public final EnvelopeTimeInterpolate envelope;
    public final List<TrainStop> stops;

    public EnvelopeStopWrapper(EnvelopeTimeInterpolate envelope, List<TrainStop> stops) {
        this.envelope = envelope;
        this.stops = stops;
    }

    @Override
    public double interpolateTotalTime(double position) {
        double stopTime = 0;
        for (var stop : stops) {
            if (stop.position > position) break;
            stopTime += stop.duration;
        }
        return stopTime + envelope.interpolateTotalTime(position);
    }

    public long interpolateTotalTimeMS(double position) {
        return (long) (this.interpolateTotalTime(position) * 1000);
    }

    @Override
    public double interpolateTotalTimeClamp(double position) {
        position = Math.max(0, Math.min(envelope.getEndPos(), position));
        return interpolateTotalTime(position);
    }

    @Override
    public double getBeginPos() {
        return envelope.getBeginPos();
    }

    @Override
    public double getEndPos() {
        return envelope.getEndPos();
    }

    @Override
    public double getTotalTime() {
        return envelope.getTotalTime()
                + stops.stream().mapToDouble(stop -> stop.duration).sum();
    }

    /** Returns all the points as (time, speed, position), with time adjusted for stop duration */
    @Override
    public List<EnvelopePoint> iteratePoints() {
        var res = new ArrayList<EnvelopePoint>();
        double sumPreviousStopDuration = 0;
        int stopIndex = 0;
        for (var point : envelope.iteratePoints()) {
            var shiftedPoint =
                    new EnvelopePoint(point.time() + sumPreviousStopDuration, point.speed(), point.position());
            res.add(shiftedPoint);
            if (stopIndex < stops.size() && point.position() >= stops.get(stopIndex).position) {
                var stopDuration = stops.get(stopIndex).duration;
                stopIndex++;
                sumPreviousStopDuration += stopDuration;
            }
        }
        return res;
    }
}
