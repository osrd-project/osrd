package fr.sncf.osrd.envelope;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to concatenate envelopes without a deep copy of all the underlying data. All
 * envelopes are expected to start at position 0.
 */
public class EnvelopeConcat implements EnvelopeTimeInterpolate {

    private final List<LocatedEnvelope> envelopes;
    private final double endPos;

    private EnvelopeConcat(List<LocatedEnvelope> envelopes, double endPos) {
        this.envelopes = envelopes;
        this.endPos = endPos;
    }

    /** Creates an instance from a list of envelopes */
    public static EnvelopeConcat from(List<? extends EnvelopeTimeInterpolate> envelopes) {
        var locatedEnvelopes = initLocatedEnvelopes(envelopes);
        var lastEnvelope = locatedEnvelopes.get(locatedEnvelopes.size() - 1);
        var endPos = lastEnvelope.startOffset + lastEnvelope.envelope.getEndPos();
        return new EnvelopeConcat(locatedEnvelopes, endPos);
    }

    /** Creates an instance from a list of located envelopes.
     * Avoids redundant initialization when elements are appended to one envelope list. */
    public static EnvelopeConcat fromLocated(List<LocatedEnvelope> envelopes) {
        var lastEnvelope = envelopes.get(envelopes.size() - 1);
        var endPos = lastEnvelope.startOffset + lastEnvelope.envelope.getEndPos();
        return new EnvelopeConcat(envelopes, endPos);
    }

    /** Place all envelopes in a record containing the offset on which they start */
    private static List<LocatedEnvelope> initLocatedEnvelopes(List<? extends EnvelopeTimeInterpolate> envelopes) {
        double currentOffset = 0.0;
        double currentTime = 0.0;
        var res = new ArrayList<LocatedEnvelope>();
        for (var envelope : envelopes) {
            res.add(new LocatedEnvelope(envelope, currentOffset, currentTime));
            currentOffset += envelope.getEndPos();
            currentTime += envelope.getTotalTime();
        }
        return res;
    }

    @Override
    public double interpolateTotalTime(double position) {
        var envelope = findEnvelopeAt(position);
        assert envelope != null : "Trying to interpolate time outside of the envelope";
        return envelope.startTime + envelope.envelope.interpolateTotalTimeClamp(position - envelope.startOffset);
    }

    @Override
    public long interpolateTotalTimeMS(double position) {
        return (long) (interpolateTotalTime(position) * 1000);
    }

    @Override
    public double interpolateTotalTimeClamp(double position) {
        var clamped = Math.max(0, Math.min(position, endPos));
        return interpolateTotalTime(clamped);
    }

    @Override
    public double getBeginPos() {
        return 0;
    }

    @Override
    public double getEndPos() {
        return endPos;
    }

    @Override
    public double getTotalTime() {
        var lastEnvelope = envelopes.get(envelopes.size() - 1);
        return lastEnvelope.startTime + lastEnvelope.envelope.getTotalTime();
    }

    @Override
    public List<EnvelopePoint> iteratePoints() {
        return envelopes.stream()
                .flatMap(locatedEnvelope -> locatedEnvelope.envelope.iteratePoints().stream()
                        .map(p -> new EnvelopePoint(
                                p.time() + locatedEnvelope.startTime,
                                p.speed(),
                                p.position() + locatedEnvelope.startOffset)))
                .toList();
    }

    /**
     * Returns the envelope at the given position.
     */
    private LocatedEnvelope findEnvelopeAt(double position) {
        if (position < 0) return null;
        var lowerBound = 0; // included
        var upperBound = envelopes.size(); // excluded
        while (lowerBound < upperBound) {
            var i = (lowerBound + upperBound) / 2;
            var envelope = envelopes.get(i);
            if (position < envelope.startOffset) {
                upperBound = i;
            } else if (position > envelope.startOffset + envelope.envelope.getEndPos()) {
                lowerBound = i + 1;
            } else {
                return envelope;
            }
        }
        return null;
    }

    public record LocatedEnvelope(EnvelopeTimeInterpolate envelope, double startOffset, double startTime) {}
}
