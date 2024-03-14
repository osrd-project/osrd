package fr.sncf.osrd.stdcm

import fr.sncf.osrd.envelope_sim.PhysicsRollingStock
import fr.sncf.osrd.graph.AStarHeuristic
import fr.sncf.osrd.sim_infra.api.Block
import fr.sncf.osrd.sim_infra.api.BlockId
import fr.sncf.osrd.sim_infra.api.BlockInfra
import fr.sncf.osrd.sim_infra.api.RawInfra
import fr.sncf.osrd.sim_infra.utils.getBlockEntry
import fr.sncf.osrd.stdcm.graph.STDCMEdge
import fr.sncf.osrd.utils.indexing.StaticIdx
import fr.sncf.osrd.utils.units.Offset
import java.util.PriorityQueue
import kotlin.math.max

private data class PendingBlock(
    val block: BlockId,
    val stepIndex: Int,
    val remainingTimeAtBlockStart: Double,
) : Comparable<PendingBlock> {
    override fun compareTo(other: PendingBlock): Int {
        return remainingTimeAtBlockStart.compareTo(other.remainingTimeAtBlockStart)
    }
}

fun makeSTDCMHeuristics(
    blockInfra: BlockInfra,
    rawInfra: RawInfra,
    steps: List<STDCMStep>,
    maxRunningTime: Double,
    rollingStock: PhysicsRollingStock,
    maxDepartureDelay: Double,
): List<AStarHeuristic<STDCMEdge, STDCMEdge>> {
    val maps = mutableListOf<MutableMap<BlockId, Double>>()
    for (i in 0 until steps.size - 1) maps.add(mutableMapOf())

    val pendingBlocks = initFirstBlocks(blockInfra, steps, rollingStock)
    while (true) {
        val block = pendingBlocks.poll() ?: break
        val index = max(0, block.stepIndex - 1)
        if (maps[index].contains(block.block)) {
            continue
        }
        maps[index][block.block] = block.remainingTimeAtBlockStart
        if (block.stepIndex > 0) {
            pendingBlocks.addAll(
                getPredecessors(blockInfra, rawInfra, steps, maxRunningTime, block, rollingStock)
            )
        }
    }
    val res = mutableListOf<AStarHeuristic<STDCMEdge, STDCMEdge>>()
    for (nPassedSteps in maps.indices) {
        res.add { edge, offset ->
            for (i in (0..nPassedSteps).reversed()) {
                val cachedRemainingDistance = maps[i][edge.block] ?: continue
                val blockOffset = edge.envelopeStartOffset + offset.distance
                val remainingTime =
                    cachedRemainingDistance -
                        getBlockTime(blockInfra, edge.block, rollingStock, blockOffset)

                // Accounts for the math in the `costToEdgeLocation`
                return@add remainingTime * maxDepartureDelay
            }
            return@add Double.POSITIVE_INFINITY
        }
    }
    return res
}

private fun getPredecessors(
    blockInfra: BlockInfra,
    rawInfra: RawInfra,
    steps: List<STDCMStep>,
    maxRunningTime: Double,
    pendingBlock: PendingBlock,
    rollingStock: PhysicsRollingStock,
): Collection<PendingBlock> {
    val detector = blockInfra.getBlockEntry(rawInfra, pendingBlock.block)
    val blocks = blockInfra.getBlocksEndingAtDetector(detector)
    val res = mutableListOf<PendingBlock>()
    for (block in blocks) {
        val newBlock =
            makePendingBlock(
                blockInfra,
                rollingStock,
                block,
                null,
                steps,
                pendingBlock.stepIndex,
                pendingBlock.remainingTimeAtBlockStart
            )
        if (newBlock.remainingTimeAtBlockStart <= maxRunningTime) {
            res.add(newBlock)
        }
    }
    return res
}

private fun initFirstBlocks(
    blockInfra: BlockInfra,
    steps: List<STDCMStep>,
    rollingStock: PhysicsRollingStock
): PriorityQueue<PendingBlock> {
    val res = PriorityQueue<PendingBlock>()
    val stepCount = steps.size
    for (wp in steps[stepCount - 1].locations) {
        res.add(
            makePendingBlock(
                blockInfra,
                rollingStock,
                wp.edge,
                wp.offset,
                steps,
                stepCount - 1,
                0.0
            )
        )
    }
    return res
}

private fun makePendingBlock(
    blockInfra: BlockInfra,
    rollingStock: PhysicsRollingStock,
    block: StaticIdx<Block>,
    offset: Offset<Block>?,
    steps: List<STDCMStep>,
    currentIndex: Int,
    remainingTime: Double
): PendingBlock {
    var newIndex = currentIndex
    val actualOffset = offset ?: blockInfra.getBlockLength(block)
    var remainingTimeWithStops = remainingTime
    while (newIndex > 0) {
        val step = steps[newIndex - 1]
        if (step.locations.none { it.edge == block && it.offset <= actualOffset }) {
            break
        }
        if (step.stop) remainingTimeWithStops += step.duration!!
        newIndex--
    }
    return PendingBlock(
        block,
        newIndex,
        remainingTimeWithStops + getBlockTime(blockInfra, block, rollingStock, offset)
    )
}

private fun getBlockTime(
    blockInfra: BlockInfra,
    block: BlockId,
    rollingStock: PhysicsRollingStock,
    endOffset: Offset<Block>?,
): Double {
    val actualLength = endOffset ?: blockInfra.getBlockLength(block)
    return actualLength.distance.meters / rollingStock.maxSpeed // TODO: use MRSP?
}
