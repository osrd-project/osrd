package fr.sncf.osrd.sim_infra_adapter

import fr.sncf.osrd.railjson.schema.common.RJSWaypointRef
import fr.sncf.osrd.railjson.schema.common.graph.EdgeDirection
import fr.sncf.osrd.railjson.schema.infra.RJSRoute
import fr.sncf.osrd.railjson.schema.infra.trackobjects.RJSTrainDetector
import fr.sncf.osrd.sim_infra.api.DirTrackSectionId
import fr.sncf.osrd.sim_infra.api.RawInfra
import fr.sncf.osrd.sim_infra.api.TrackNode
import fr.sncf.osrd.sim_infra.api.TrackNodeConfigId
import fr.sncf.osrd.sim_infra.api.decreasing
import fr.sncf.osrd.sim_infra.api.increasing
import fr.sncf.osrd.utils.Direction
import fr.sncf.osrd.utils.DistanceRangeMapImpl
import fr.sncf.osrd.utils.Helpers
import fr.sncf.osrd.utils.indexing.StaticIdx
import fr.sncf.osrd.utils.units.meters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RawInfraAdapterTest {
    @Test
    fun smokeAdaptTinyInfra() {
        val rjsInfra = Helpers.getExampleInfra("tiny_infra/infra.json")
        val oldInfra = Helpers.infraFromRJS(rjsInfra)
        adaptRawInfra(oldInfra)
    }

    @Test
    fun smokeAdaptSmallInfra() {
        val rjsInfra = Helpers.getExampleInfra("small_infra/infra.json")
        val oldInfra = Helpers.infraFromRJS(rjsInfra)
        adaptRawInfra(oldInfra)
    }

    @ParameterizedTest
    @ValueSource(strings = ["small_infra/infra.json", "tiny_infra/infra.json"])
    fun testTrackChunksOnRoutes(infraPath: String) {
        val epsilon =
            1e-2 // fairly high value, because we compare integer millimeters with float meters
        val rjsInfra = Helpers.getExampleInfra(infraPath)
        val oldInfra = Helpers.infraFromRJS(rjsInfra)
        val infra = adaptRawInfra(oldInfra)
        for (route in infra.routes.iterator()) {
            val oldRoute = oldInfra.reservationRouteMap[infra.getRouteName(route)]!!
            val chunks = infra.getChunksOnRoute(route)
            var offset = 0.meters
            for (chunk in chunks) {
                val end = offset + infra.getTrackChunkLength(chunk.value).distance
                val trackRangeViews = oldRoute.getTrackRanges(offset.meters, end.meters)!!
                assertTrue { trackRangeViews.size == 1 } // This may fail because of float rounding,
                // but as long as it's true it makes testing much easier
                val trackRangeView = trackRangeViews[0]
                assertEquals(
                    trackRangeView.track.edge.id,
                    infra.getTrackSectionName(infra.getTrackFromChunk(chunk.value))
                )
                assertEquals(
                    trackRangeView.length,
                    infra.getTrackChunkLength(chunk.value).distance.meters,
                    epsilon
                )
                assertEquals(trackRangeView.track.direction.toKtDirection(), chunk.direction)

                offset = end
            }
            assertEquals(offset.meters, oldRoute.length, epsilon)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["small_infra/infra.json", "tiny_infra/infra.json"])
    fun testChunkSlopes(infraPath: String) {
        val rjsInfra = Helpers.getExampleInfra(infraPath)
        val oldInfra = Helpers.infraFromRJS(rjsInfra)
        val infra = adaptRawInfra(oldInfra)
        for (route in infra.routes.iterator()) {
            val oldRoute = oldInfra.reservationRouteMap[infra.getRouteName(route)]!!
            val chunks = infra.getChunksOnRoute(route)
            var offset = 0.meters
            for (chunk in chunks) {
                val end = offset + infra.getTrackChunkLength(chunk.value).distance
                val trackRangeViews = oldRoute.getTrackRanges(offset.meters, end.meters)!!
                assertTrue { trackRangeViews.size == 1 } // This may fail because of float rounding,
                // but as long as it's true it makes testing much easier
                val trackRangeView = trackRangeViews[0]

                val slopes = infra.getTrackChunkSlope(chunk)
                val refSlopes = trackRangeView.slopes

                assertEquals(DistanceRangeMapImpl.from(refSlopes), slopes)
                offset = end
            }
        }
    }

    /**
     * Checks that we can load a route that starts at the edge of a track section, an edge case that
     * happens when loading a real infra but not on our generated infras
     */
    @Test
    fun adaptSmallInfraRouteWithEmptyTrackRange() {
        // The route goes from the end of TA1 to TA3, two tracks that are linked through switch PA0
        // in
        // its LEFT config
        val rjsInfra = Helpers.getExampleInfra("small_infra/infra.json")
        rjsInfra.detectors.add(RJSTrainDetector("det_at_transition", 1950.0, "TA1"))
        rjsInfra.detectors.add(RJSTrainDetector("det_end_new_route", 20.0, "TA3"))
        val newRoute =
            RJSRoute(
                "new_route",
                RJSWaypointRef("det_at_transition", RJSWaypointRef.RJSWaypointType.DETECTOR),
                EdgeDirection.START_TO_STOP,
                RJSWaypointRef("det_end_new_route", RJSWaypointRef.RJSWaypointType.DETECTOR),
            )
        newRoute.switchesDirections["PA0"] = "A_B1"
        rjsInfra.routes = listOf(newRoute)
        val oldInfra = Helpers.infraFromRJS(rjsInfra)
        adaptRawInfra(oldInfra)
    }

    private fun assertCrossing(rawInfra: RawInfra, nodeIdx: StaticIdx<TrackNode>) {
        val portIdxs = rawInfra.getTrackNodePorts(nodeIdx)
        assert(portIdxs.size == 4u)
        val configIdxs = rawInfra.getTrackNodeConfigs(nodeIdx)
        assert(configIdxs.size == 1u)
    }

    private fun assertDoubleSlipSwitch(rawInfra: RawInfra, nodeIdx: StaticIdx<TrackNode>) {
        val portIdxs = rawInfra.getTrackNodePorts(nodeIdx)
        assert(portIdxs.size == 4u)
        val configIdxs = rawInfra.getTrackNodeConfigs(nodeIdx)
        assert(configIdxs.size == 4u)
    }

    private fun assertPointSwitch(rawInfra: RawInfra, nodeIdx: StaticIdx<TrackNode>) {
        val portIdxs = rawInfra.getTrackNodePorts(nodeIdx)
        assert(portIdxs.size == 3u)
        val configIdxs = rawInfra.getTrackNodeConfigs(nodeIdx)
        assert(configIdxs.size == 2u)
    }

    @Test
    fun loadSmallInfraNodes() {
        val rjsInfra = Helpers.getExampleInfra("small_infra/infra.json")
        val rawInfra = adaptRawInfra(Helpers.infraFromRJS(rjsInfra)).simInfra
        val nodeNameToIdxMap =
            rawInfra.trackNodes
                .map { nodeIdx -> Pair(rawInfra.getTrackNodeName(nodeIdx), nodeIdx) }
                .toMap()
        assertPointSwitch(rawInfra, nodeNameToIdxMap["PA0"]!!)
        assertPointSwitch(rawInfra, nodeNameToIdxMap["PE1"]!!)
        assertDoubleSlipSwitch(rawInfra, nodeNameToIdxMap["PH0"]!!)
        assertCrossing(rawInfra, nodeNameToIdxMap["PD1"]!!)

        // check that PD0 crossing connects correctly track-sections with their directions
        assertCrossing(rawInfra, nodeNameToIdxMap["PD0"]!!)
        val configIdx = TrackNodeConfigId(0u) // only configuration of "PD0" crossing node
        val te0 = rawInfra.getTrackSectionFromName("TE0")!!
        val tf0 = rawInfra.getTrackSectionFromName("TF0")!!
        val td0 = rawInfra.getTrackSectionFromName("TD0")!!
        val td2 = rawInfra.getTrackSectionFromName("TD2")!!
        val te0Increasing = DirTrackSectionId(te0, Direction.INCREASING)
        val te0Decreasing = DirTrackSectionId(te0, Direction.DECREASING)
        val tf0Increasing = DirTrackSectionId(tf0, Direction.INCREASING)
        val tf0Decreasing = DirTrackSectionId(tf0, Direction.DECREASING)
        val td0Increasing = DirTrackSectionId(td0, Direction.INCREASING)
        val td0Decreasing = DirTrackSectionId(td0, Direction.DECREASING)
        val td2Increasing = DirTrackSectionId(td2, Direction.INCREASING)
        val td2Decreasing = DirTrackSectionId(td2, Direction.DECREASING)
        assert(rawInfra.getNextTrackNode(te0Increasing).asIndex() == nodeNameToIdxMap["PD0"])
        assert(rawInfra.getNextTrackNode(tf0Decreasing).asIndex() == nodeNameToIdxMap["PD0"])
        assert(rawInfra.getNextTrackNode(td0Increasing).asIndex() == nodeNameToIdxMap["PD0"])
        assert(rawInfra.getNextTrackNode(td2Decreasing).asIndex() == nodeNameToIdxMap["PD0"])
        assert(rawInfra.getNextTrackSection(te0Increasing, configIdx).asIndex() == tf0Increasing)
        assert(rawInfra.getNextTrackSection(tf0Decreasing, configIdx).asIndex() == te0Decreasing)
        assert(rawInfra.getNextTrackSection(td0Increasing, configIdx).asIndex() == td2Increasing)
        assert(rawInfra.getNextTrackSection(td2Decreasing, configIdx).asIndex() == td0Decreasing)
    }

    @Test
    fun loadSmallInfraCrossingZone() {
        val rjsInfra = Helpers.getExampleInfra("small_infra/infra.json")
        val rawInfra = adaptRawInfra(Helpers.infraFromRJS(rjsInfra)).simInfra

        // Check that for crossing nodes, only one zone is generated.
        // In small_infra, PD0 and PD1 are crossings, linked by track-section TF0 (no detector on
        // TF0) so PD0 and PD1 are in the same detection zone (bounded by the 6 detectors below)
        val detectorNameToIdx = rawInfra.detectors.associateBy { rawInfra.getDetectorName(it) }
        val de0inc = detectorNameToIdx["DE0"]!!.increasing
        val dd2inc = detectorNameToIdx["DD2"]!!.increasing
        val dd3inc = detectorNameToIdx["DD3"]!!.increasing
        val dd4dec = detectorNameToIdx["DD4"]!!.decreasing
        val dd5dec = detectorNameToIdx["DD5"]!!.decreasing
        val df0dec = detectorNameToIdx["DF0"]!!.decreasing
        val expectedBounds = setOf(de0inc, dd2inc, dd3inc, dd4dec, dd5dec, df0dec)

        val zoneCrossings = rawInfra.getNextZone(de0inc)!!
        assert(rawInfra.getZoneBounds(zoneCrossings).toSet() == expectedBounds)
        val zoneCrossingsName = rawInfra.getZoneName(zoneCrossings)
        for (dirDet in expectedBounds) {
            assert(rawInfra.getNextZone(dirDet) == zoneCrossings)
            assert(
                "${rawInfra.getDetectorName(dirDet.value)}:${dirDet.direction}" in zoneCrossingsName
            )
        }
    }
}
