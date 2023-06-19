package fr.sncf.osrd.api.pathfinding;

import fr.sncf.osrd.api.pathfinding.response.PathfindingResult;
import fr.sncf.osrd.geom.LineString;
import fr.sncf.osrd.infra.api.signaling.SignalingInfra;
import fr.sncf.osrd.railjson.schema.geom.RJSLineString;
import fr.sncf.osrd.railjson.schema.infra.trackranges.RJSDirectionalTrackRange;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GeomUtils {
    /** Generates the path geometry */
    static void addGeometry(PathfindingResult res, SignalingInfra infra) {
        var geoList = new ArrayList<LineString>();
        var schList = new ArrayList<LineString>();

        RJSDirectionalTrackRange previousTrack = null;
        double previousBegin = 0;
        double previousEnd = 0;

        for (var routePath : res.routePaths) {
            for (var trackSection : routePath.trackSections) {

                if (trackSection.getBegin() == trackSection.getEnd())
                    continue;

                if (previousTrack == null) {
                    previousTrack = trackSection;
                    previousBegin = trackSection.getBegin();
                    previousEnd = trackSection.getEnd();
                    continue;
                }

                if (previousTrack.trackSectionID.compareTo(trackSection.trackSectionID) != 0) {
                    if (Double.compare(previousBegin, previousEnd) != 0) {
                        var track = infra.getTrackSection(previousTrack.trackSectionID);
                        sliceAndAdd(geoList, track.getGeo(), previousBegin, previousEnd, track.getLength());
                        sliceAndAdd(schList, track.getSch(), previousBegin, previousEnd, track.getLength());
                    }
                    previousTrack = trackSection;
                    previousBegin = trackSection.getBegin();
                }
                previousEnd = trackSection.getEnd();
            }
        }

        assert previousTrack != null;
        var track = infra.getTrackSection(previousTrack.trackSectionID);
        sliceAndAdd(geoList, track.getGeo(), previousBegin, previousEnd, track.getLength());
        sliceAndAdd(schList, track.getSch(), previousBegin, previousEnd, track.getLength());

        res.geographic = toRJSLineString(concatenate(geoList));
        res.schematic = toRJSLineString(concatenate(schList));
    }

    private static RJSLineString toRJSLineString(LineString lineString) {
        var coordinates = new ArrayList<List<Double>>();
        for (var p : lineString.getPoints())
            coordinates.add(List.of(p.x(), p.y()));
        return new RJSLineString("LineString", coordinates);
    }

    /** Concatenates a list of LineString into a single LineString.
     * If not enough values are present, we return the default [0, 1] line. */
    private static LineString concatenate(List<LineString> list) {
        if (list.size() >= 2)
            return LineString.concatenate(list);
        else if (list.size() == 1)
            return list.get(0);
        return LineString.make(
                new double[] {0., 1.},
                new double[] {0., 1.}
        );
    }

    /** If the lineString isn't null, slice it from previousBegin to previousEnd and add it to res */
    private static void sliceAndAdd(
            List<LineString> res,
            LineString lineString,
            double previousBegin,
            double previousEnd,
            double trackLength
    ) {
        if (lineString == null)
            return;
        if (trackLength == 0) {
            assert previousBegin == 0;
            assert previousEnd == 0;
            res.add(lineString);
        } else
            res.add(lineString.slice(previousBegin / trackLength, previousEnd / trackLength));
    }
}
