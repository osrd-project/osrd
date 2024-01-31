package fr.sncf.osrd.standalone_sim;

import fr.sncf.osrd.envelope.EnvelopeTimeInterpolate;
import fr.sncf.osrd.envelope.Envelope;
import fr.sncf.osrd.envelope.EnvelopeTestUtils;
import fr.sncf.osrd.train.TrainStop;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;

public class EnvelopeStopWrapperTests {
    @Test
    public void iteratePointsWithoutStops() {
        var envelopeFloor = Envelope.make(EnvelopeTestUtils.generateTimes(
                new double[]{0, 1, 2, 3, 4, 5, 6},
                new double[]{1, 1, 1, 1, 1, 1, 1}
        ));
        var envelopeStopWrapper = new EnvelopeStopWrapper(envelopeFloor, List.of());
        Assertions.assertEquals(List.of(
                new EnvelopeTimeInterpolate.EnvelopePoint(0, 1, 0),
                new EnvelopeTimeInterpolate.EnvelopePoint(1, 1, 1),
                new EnvelopeTimeInterpolate.EnvelopePoint(2, 1, 2),
                new EnvelopeTimeInterpolate.EnvelopePoint(3, 1, 3),
                new EnvelopeTimeInterpolate.EnvelopePoint(4, 1, 4),
                new EnvelopeTimeInterpolate.EnvelopePoint(5, 1, 5),
                new EnvelopeTimeInterpolate.EnvelopePoint(6, 1, 6)
        ), envelopeStopWrapper.iteratePoints());
    }

    @Test
    public void iteratePointsWithStops() {
        var envelopeFloor = Envelope.make(EnvelopeTestUtils.generateTimes(
                new double[]{0, 1, 2, 3, 4, 5, 6},
                new double[]{1, 1, 1, 0, 1, 1, 1}
        ));
        var envelopeStopWrapper = new EnvelopeStopWrapper(envelopeFloor, List.of(
                new TrainStop(1.5, 0),
                new TrainStop(3, 10)
        ));
        Assertions.assertEquals(List.of(
                new EnvelopeTimeInterpolate.EnvelopePoint(0, 1, 0),
                new EnvelopeTimeInterpolate.EnvelopePoint(1, 1, 1),
                new EnvelopeTimeInterpolate.EnvelopePoint(2, 1, 2),
                new EnvelopeTimeInterpolate.EnvelopePoint(4, 0, 3),
                new EnvelopeTimeInterpolate.EnvelopePoint(6 + 10, 1, 4),
                new EnvelopeTimeInterpolate.EnvelopePoint(7 + 10, 1, 5),
                new EnvelopeTimeInterpolate.EnvelopePoint(8 + 10, 1, 6)
        ), envelopeStopWrapper.iteratePoints());
    }
}
