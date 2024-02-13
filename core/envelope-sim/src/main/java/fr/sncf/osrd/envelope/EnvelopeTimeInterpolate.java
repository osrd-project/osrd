package fr.sncf.osrd.envelope;

import java.util.List;

public interface EnvelopeTimeInterpolate extends Cloneable {

    /** Computes the time required to get to a given point of the envelope */
    double interpolateTotalTime(double position);

    /** Computes the time required to get to a given point of the envelope in ms */
    long interpolateTotalTimeMS(double position);

    /**
     * Computes the time required to get to a given point of the envelope, clamping the position to
     * [0, envelope length] first
     */
    double interpolateTotalTimeClamp(double position);

    /** Returns the start position of the envelope */
    double getBeginPos();

    /** Returns the end position of the envelope */
    double getEndPos();

    /** Returns the total time of the envelope */
    double getTotalTime();

    record EnvelopePoint(double time, double speed, double position) {}

    List<EnvelopePoint> iteratePoints();

    EnvelopeTimeInterpolate clone();
}
