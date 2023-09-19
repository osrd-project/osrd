package fr.sncf.osrd.envelope_sim_infra;

import static fr.sncf.osrd.envelope_utils.RangeMapUtils.mergeRanges;
import static fr.sncf.osrd.envelope_utils.RangeMapUtils.updateRangeMap;

import com.carrotsearch.hppc.DoubleArrayList;
import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import fr.sncf.osrd.envelope_sim.EnvelopeSimPath;
import fr.sncf.osrd.envelope_sim.electrification.Electrification;
import fr.sncf.osrd.envelope_sim.electrification.Electrified;
import fr.sncf.osrd.envelope_sim.electrification.Neutral;
import fr.sncf.osrd.envelope_sim.electrification.NonElectrified;
import fr.sncf.osrd.external_generated_inputs.LegacyElectricalProfileMapping;
import fr.sncf.osrd.infra.api.tracks.undirected.NeutralSection;
import fr.sncf.osrd.infra.implementation.tracks.directed.TrackRangeView;
import fr.sncf.osrd.infra_state.api.TrainPath;
import java.util.HashMap;
import java.util.List;

public class LegacyEnvelopeTrainPath {
    /** Create EnvelopePath from a list of TrackRangeView, with no electrical profile */
    public static EnvelopeSimPath from(List<TrackRangeView> trackSectionPath) {
        return from(trackSectionPath, null);
    }

    /** Create EnvelopePath from a list of TrackRangeView */
    public static EnvelopeSimPath from(List<TrackRangeView> trackSectionPath,
                                       LegacyElectricalProfileMapping electricalProfileMapping) {
        var gradePositions = new DoubleArrayList();
        gradePositions.add(0);
        var gradeValues = new DoubleArrayList();
        var catenaries = TreeRangeMap.<Double, String>create();
        var neutralSections = TreeRangeMap.<Double, NeutralSection>create();
        var neutralSectionAnnouncements = TreeRangeMap.<Double, NeutralSection>create();
        double length = 0;

        for (var range : trackSectionPath) {
            if (range.getLength() > 0) {
                var fullRange = Range.closed(0., range.getLength());
                // Add grades
                var grades = range.getGradients().subRangeMap(fullRange).asMapOfRanges();
                for (var entry : grades.entrySet()) {
                    gradePositions.add(length + entry.getKey().upperEndpoint());
                    gradeValues.add(entry.getValue());
                }
                // Add catenaries
                var catenariesInRange = range.getCatenaryVoltages().subRangeMap(fullRange);
                transferRangeMap(catenariesInRange, catenaries, length);

                // Add neutral sections
                var neutralInRange = range.getNeutralSections().subRangeMap(fullRange);
                transferRangeMap(neutralInRange, neutralSections, length);

                // Add neutral section announcements
                var announcementsInRange = range.getNeutralSectionAnnouncements().subRangeMap(fullRange);
                transferRangeMap(announcementsInRange, neutralSectionAnnouncements, length);

                // Update length
                length += range.getLength();
            }
        }
        if (gradeValues.isEmpty()) {
            gradePositions.add(length);
            gradeValues.add(0);
        }

        var electrificationMap = buildElectrificationMap(mergeRanges(catenaries), mergeRanges(neutralSections),
                mergeRanges(neutralSectionAnnouncements), length);

        var electrificationMapByPowerClass = new HashMap<String, ImmutableRangeMap<Double, Electrification>>();
        if (electricalProfileMapping != null) {
            var profileMap = electricalProfileMapping.getProfilesOnPath(trackSectionPath);
            electrificationMapByPowerClass = buildElectrificationMapByPowerClass(electrificationMap, profileMap);
        }

        return new EnvelopeSimPath(length, gradePositions.toArray(), gradeValues.toArray(), electrificationMap,
                electrificationMapByPowerClass);
    }

    /** Create EnvelopePath from a train path */
    public static EnvelopeSimPath from(TrainPath trainsPath, LegacyElectricalProfileMapping electricalProfileMapping) {
        return from(TrainPath.removeLocation(trainsPath.trackRangePath()), electricalProfileMapping);
    }

    /** Create EnvelopePath from a train path, with no electrical profile */
    public static EnvelopeSimPath from(TrainPath trainsPath) {
        return from(trainsPath, null);
    }

    private static <T> void transferRangeMap(RangeMap<Double, T> source, RangeMap<Double, T> dest,
                                             double offset) {
        for (var entry : source.asMapOfRanges().entrySet()) {
            var range = entry.getKey();
            var value = entry.getValue();
            dest.put(Range.closedOpen(range.lowerEndpoint() + offset, range.upperEndpoint() + offset), value);
        }
    }

    private static ImmutableRangeMap<Double, Electrification> buildElectrificationMap(
            RangeMap<Double, String> catenaryModes,
            RangeMap<Double, NeutralSection> neutralSections,
            RangeMap<Double, NeutralSection> neutralSectionAnnouncements,
            double length) {
        TreeRangeMap<Double, Electrification> res = TreeRangeMap.create();
        res.put(Range.closed(0.0, length), new NonElectrified());
        res = updateRangeMap(res, catenaryModes,
                (e, catenaryMode) -> catenaryMode.equals("") ? new NonElectrified() : new Electrified(catenaryMode));
        res = updateRangeMap(res, neutralSectionAnnouncements,
                (electrification, neutralSection) ->
                        new Neutral(neutralSection.lowerPantograph(), electrification, true));
        res = updateRangeMap(res, neutralSections,
                (electrification, neutralSection) ->
                        new Neutral(neutralSection.lowerPantograph(), electrification, false));
        return ImmutableRangeMap.copyOf(res);
    }

    private static HashMap<String, ImmutableRangeMap<Double, Electrification>> buildElectrificationMapByPowerClass(
            ImmutableRangeMap<Double, Electrification> electrificationMap,
            HashMap<String, RangeMap<Double, String>> profileMap) {
        var res = new HashMap<String, ImmutableRangeMap<Double, Electrification>>();
        for (var entry : profileMap.entrySet()) {
            var withElectricalProfiles = ImmutableRangeMap.copyOf(
                    updateRangeMap(electrificationMap, mergeRanges(entry.getValue()),
                            Electrification::withElectricalProfile));
            res.put(entry.getKey(), withElectricalProfiles);
        }
        return res;
    }
}