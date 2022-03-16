package fr.sncf.osrd.new_infra.implementation.tracks.undirected;

import com.google.common.base.MoreObjects;
import fr.sncf.osrd.new_infra.api.reservation.Detector;
import fr.sncf.osrd.new_infra.api.tracks.undirected.TrackObject;
import fr.sncf.osrd.new_infra.api.tracks.undirected.TrackSection;
import fr.sncf.osrd.utils.jacoco.ExcludeFromGeneratedCodeCoverage;

public class TrackObjectImpl implements TrackObject {

    /** Track section the object is placed on */
    public final TrackSection trackSection;
    /** Offset on the track section */
    public final double offset;
    /** Type of the object (detector or buffer stop) */
    public final TrackObjectType type;
    /** ID of the object */
    public final String id;
    /** Detector object corresponding to this track object (if any) */
    private Detector detector;

    @Override
    @ExcludeFromGeneratedCodeCoverage
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("trackSection", trackSection.getID())
                .add("offset", offset)
                .add("type", type)
                .add("id", id)
                .toString();
    }

    /** Constructor */
    public TrackObjectImpl(TrackSection trackSection, double offset, TrackObjectType type, String id) {
        this.trackSection = trackSection;
        this.offset = offset;
        this.type = type;
        this.id = id;
    }

    @Override
    public TrackSection getTrackSection() {
        return trackSection;
    }

    @Override
    public double getOffset() {
        return offset;
    }

    @Override
    public String getID() {
        return id;
    }

    @Override
    public TrackObjectType getType() {
        return type;
    }

    @Override
    public Detector getDetector() {
        return detector;
    }

    @Override
    public void setDetector(Detector detector) {
        this.detector = detector;
    }
}
