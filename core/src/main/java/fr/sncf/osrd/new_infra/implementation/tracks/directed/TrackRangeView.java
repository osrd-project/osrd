package fr.sncf.osrd.new_infra.implementation.tracks.directed;


import fr.sncf.osrd.new_infra.api.Direction;
import fr.sncf.osrd.new_infra.api.tracks.directed.DiTrackEdge;
import fr.sncf.osrd.new_infra.api.tracks.undirected.TrackLocation;
import fr.sncf.osrd.new_infra.api.tracks.undirected.TrackObject;
import fr.sncf.osrd.utils.DoubleRangeMap;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** An oriented view on a track range. Can be used to iterate over its content */
public class TrackRangeView {
    /** Start of the range on the original track (begin < end) */
    public final double begin;
    /** End of the range on the original track (begin < end) */
    public final double end;
    /** Referenced oriented track. This sets the direction of the range */
    public final DiTrackEdge track;

    /** A pair containing an element and an offset. The offset is oriented with 0 = start of the range.
     * The offset contained in the element is based on the track itself, it may be different */
    public record ElementView<T>(double offset, T element){}

    private static final Comparator<? super ElementView<TrackObject>> comparator
            = Comparator.comparingDouble(x -> x.offset);

    /** Constructor */
    public TrackRangeView(double begin, double end, DiTrackEdge track) {
        if (begin < end) {
            this.begin = begin;
            this.end = end;
        } else {
            this.begin = end;
            this.end = begin;
        }
        assert end <= track.getEdge().getLength();
        assert begin >= 0;
        this.track = track;
    }

    /** Returns the length of the range */
    public double getLength() {
        return end - begin;
    }

    /** Returns a list of objects on the range (sorted) */
    public List<ElementView<TrackObject>> getObjects() {
        return track.getEdge().getTrackObjects().stream()
                .map(o -> new ElementView<>(convertPosition(o.getOffset()), o))
                .filter(this::isInRange)
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    /** Returns the speed sections with positions referring to the track range (0 = directed start of the range) */
    public DoubleRangeMap getSpeedSections() {
        var originalSpeedSections = track.getEdge().getSpeedSections().get(track.getDirection());
        return convertMap(originalSpeedSections);
    }

    /** Returns the gradients with positions referring to the track range (0 = directed start of the range) */
    public DoubleRangeMap getGradients() {
        var originalGradients = track.getEdge().getGradients().get(track.getDirection());
        return convertMap(originalGradients);
    }

    /** Returns true if the location is included in the range */
    public boolean contains(TrackLocation location) {
        return location.track().equals(track.getEdge())
                && location.offset() >= begin
                && location.offset() <= end;
    }

    /** Returns the distance between the start of the range and the given location */
    public double offsetOf(TrackLocation location) {
        assert contains(location) : "can't determine the offset of an element not in the range";
        if (track.getDirection().equals(Direction.FORWARD))
            return location.offset() - begin;
        else
            return end - location.offset();
    }

    /** Returns a new view where the beginning is truncated until the given offset */
    public TrackRangeView truncateBegin(double offset) {
        assert offset >= begin && offset <= end : "truncate location isn't located in the range";
        if (track.getDirection().equals(Direction.FORWARD))
            return new TrackRangeView(offset, end, track);
        else
            return new TrackRangeView(begin, offset, track);
    }

    /** Returns a new view where the end is truncated from the given offset */
    public TrackRangeView truncateEnd(double offset) {
        assert offset >= begin && offset <= end : "truncate location isn't located in the range";
        if (track.getDirection().equals(Direction.FORWARD))
            return new TrackRangeView(begin, offset, track);
        else
            return new TrackRangeView(offset, end, track);
    }

    /** Converts a DoubleRangeMap based on the original track so that the positions refer to the range */
    private DoubleRangeMap convertMap(DoubleRangeMap map) {
        var res = new DoubleRangeMap();
        for (var entry : map.getValuesInRange(begin, end).entrySet()) {
            var rangeStart = convertPosition(entry.getKey().getBeginPosition());
            var rangeEnd = convertPosition(entry.getKey().getEndPosition());
            if (rangeStart > rangeEnd) {
                var tmp = rangeStart;
                rangeStart = rangeEnd;
                rangeEnd = tmp;
            }
            res.addRange(rangeStart, rangeEnd, entry.getValue());
        }
        return res.simplify();
    }

    /** Converts a position on the original track to one referring to the range itself.*/
    private double convertPosition(double position) {
        if (track.getDirection() == Direction.FORWARD)
            return position - begin;
        return end - position;
    }

    /** Returns true if the element is inside the range */
    private boolean isInRange(ElementView<?> element) {
        return element.offset >= 0 && element.offset <= getLength();
    }
}
