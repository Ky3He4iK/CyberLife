import ImprovedCell.Companion.MIND_SIZE
import ImprovedCell.Companion.isMulti
import kotlin.math.roundToInt

fun indirectInc(cell: ImprovedCell, shift: Int): Int {
    return (cell.adr + shift) % MIND_SIZE
}

class BaseGen(val genCodes: HashSet<Int>, val isLong: Boolean, val name: String, val action: (cell: ImprovedCell) -> Int)

val gens = arrayOf(
        BaseGen(hashSetOf(0, 32), true, "RMove") { cell ->
            // relatively move
            if (isMulti(cell) == 0) // only single cells can move
                indirectInc(cell, cell.cellMove(cell.botGetParam() % 8, true))
            else 2
        },
        BaseGen(hashSetOf(1, 33), true, "AMove") { cell ->
            // absolutely move
            if (isMulti(cell) == 0) // only single cells can move
                indirectInc(cell, cell.cellMove(cell.botGetParam() % 8, false))
            else 2
        },
        BaseGen(hashSetOf(2, 34), false, "RTurn") { cell ->
            // relatively turn
            cell.direction = (cell.botGetParam() + cell.direction) % 8
            2
        },
        BaseGen(hashSetOf(3, 35), false, "ATurn") { cell ->
            // absolutely turn
            cell.direction = cell.botGetParam() % 8
            2
        },
        BaseGen(hashSetOf(4, 36), true, "RSee") { cell ->
            // relatively see
            indirectInc(cell, cell.cellSeeCells(cell.botGetParam() % 8, true))
        },
        BaseGen(hashSetOf(5, 37), true, "ASee") { cell ->
            // absolutely see
            indirectInc(cell, cell.cellSeeCells(cell.botGetParam() % 8, false))
        },
        BaseGen(hashSetOf(6, 38), true, "RShare") { cell ->
            // relatively share
            indirectInc(cell, cell.cellShare(cell.botGetParam() % 8, true))
        },
        BaseGen(hashSetOf(7, 39), true, "AShare") { cell ->
            // absolutely share
            indirectInc(cell, cell.cellShare(cell.botGetParam() % 8, false))
        },
        BaseGen(hashSetOf(8, 40), true, "RGive") { cell ->
            // relatively give
            indirectInc(cell, cell.cellGive(cell.botGetParam() % 8, true))
        },
        BaseGen(hashSetOf(9, 41), true, "AGive") { cell ->
            // absolutely give
            indirectInc(cell, cell.cellGive(cell.botGetParam() % 8, false))
        },
        BaseGen(hashSetOf(10, 42), true, "REat") { cell ->
            // relatively eat
            indirectInc(cell, cell.cellEat(cell.botGetParam() % 8, true))
        },
        BaseGen(hashSetOf(11, 43), true, "AEat") { cell ->
            // absolutely eat
            indirectInc(cell, cell.cellEat(cell.botGetParam() % 8, false))
        },

        BaseGen(hashSetOf(12, 44), false, "HRound") { cell ->
            // round horizontal
            cell.direction = if (Math.random() < 0.5) 3 else 7 // turn into random direction
            1
        },
        BaseGen(hashSetOf(13, 45), false, "VRound") { cell ->
            // round vertical
            cell.direction = if (Math.random() < 0.5) 1 else 5 // turn into random direction
            1
        },
        BaseGen(hashSetOf(14, 46), false, "HCheck") { cell ->
            // height check
            // get approximate height by DNA
            indirectInc(cell, 2 + (cell.coordinates.y >= cell.botGetParam() * World.simulation.worldHeight / MIND_SIZE).toInt()) // if too low - step by 2 else - step by 3
        },
        BaseGen(hashSetOf(15, 47), false, "ECheck") { cell ->
            // energy check
            // get approximate energy by DNA
            indirectInc(cell, 2 + (cell.energy >= cell.botGetParam() * 1000 / MIND_SIZE).toInt()) // if too low - step by 2 else - step by 3
        },
        BaseGen(hashSetOf(16, 48), false, "MCheck") { cell ->
            // minerals check
            // get approximate energy by DNA
            indirectInc(cell, 2 + (cell.mineral >= cell.botGetParam() * 1000 / MIND_SIZE).toInt()) // if too low - step by 2 else - step by 3
        },
        BaseGen(hashSetOf(17, 49), false, "FCNCheck") { cell ->
            // check for free cell near
            indirectInc(cell, (!cell.hasFreeDirection()).toInt() + 1) // 1 if no free cells 2 otherwise
        },
        BaseGen(hashSetOf(18, 50), false, "EGCheck") { cell ->
            // check for energy grow
            indirectInc(cell, (cell.isEnergyGrow()).toInt() + 1)
        },
        BaseGen(hashSetOf(19, 51), false, "MGCheck") { cell ->
            // check for minerals grow
            indirectInc(cell, (cell.coordinates.y <= World.simulation.worldHeight / 2).toInt() + 1)
        },
        BaseGen(hashSetOf(20, 52), false, "MCLCheck") { cell ->
            // check for multi-cell life
            indirectInc(cell, when (isMulti(cell)) {
                0 -> 1
                3 -> 3
                else -> 2
            })
        },

        BaseGen(hashSetOf(21, 53), true, "M2E") { cell ->
            // turn minerals into energy
            cell.cellMineral2Energy()
            1
        },
        BaseGen(hashSetOf(22, 54), true, "S2E") { cell ->
            // do photosynthesis)
            cell.doPhotosynthesis()
            1
        },
        BaseGen(hashSetOf(23, 55), true, "DNAAttack") { cell ->
            // attack DNA
            cell.cellAttackDNA()
            1
        },
        BaseGen(hashSetOf(24, 56), true, "Mutate") { cell ->
            // mutate
            cell.mutate()
            cell.mutate()
            1
        },

        BaseGen(hashSetOf(25, 57), true, "CCC") { cell ->
            // create child in chain
            if (isMulti(cell) == 3)
                cell.cellDouble() // create free child only if cell is already in chain
            else
                cell.cellMulti()
            1
        },
        BaseGen(hashSetOf(26, 58), true, "CFC") { cell ->
            // create child in chain
            val a = isMulti(cell)
            if (a == 0 || a == 3)
                cell.cellDouble()
            else
                cell.cellMulti() // if cell in the edge of chain - it's "free". Cake is a lie
            1
        },
        BaseGen(hashSetOf(27, 59), true, "RandomShift") { _ ->
            (Math.random() * MIND_SIZE).roundToInt()
        }
)

fun checkGens() {
    val genes = HashMap<Int, String>()
    gens.forEach { gen ->
        gen.genCodes.forEach {
            if (it in genes.keys)
                println("WARNING: Doubling gene $it in ${gen.name} and ${genes[it]!!}")
            else if (it >= MIND_SIZE || it < 0)
                println("WARNING: Invalid gene $it in ${gen.name}")
            else
                genes[it] = gen.name
        }
    }
}