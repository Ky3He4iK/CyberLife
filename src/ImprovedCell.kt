import java.awt.Color
import kotlin.math.min

class ImprovedCell {
    var mineral = 0

    var adr = 0
    var coordinates = Coordinates(0, 0)
    var energy = 5
    var alive = CONDITION.FREE
    var color = MyColor(0, 0, 0)
    var direction = 2
    var chainPrev: ImprovedCell? = null
    var chainNext: ImprovedCell? = null

    var mind = IntArray(MIND_SIZE) // There will store bot's DNA

    fun step() {
        if (alive == CONDITION.ORGANIC) { // organic is always doing to depth
            if (isFreeCell(coordinates.x, coordinates.y + 1))
                moveCell(coordinates.x, coordinates.y + 1)
            return
        }
        if (alive == CONDITION.FREE)
            return // don't do anything if it's a corpse
        loop@ for (i in 0 until 15) {
            val command = mind[adr]
            var wasCom = false
            for (gen in gens)
                if (command in gen.genCodes) {
                    incCommandAddress(gen.action(this))
                    if (gen.isLong)
                        break@loop
                    wasCom = true
                }
            if (!wasCom) {
                incCommandAddress(mind[adr])
                break
            }
        }

        //check for energy, child time and distribute energy&minerals with neighbors in a chain
        if (alive == CONDITION.ALIVE) {
            val a = isMulti(this)
            if (a == 3) {
                val pb = chainPrev
                val nb = chainNext

                //distribute minerals
                var minerals = mineral + nb!!.mineral + pb!!.mineral
                minerals /= 3
                mineral = minerals
                nb.mineral = minerals
                pb.mineral = minerals

                //distribute energy. Distribute only if they isn't edge cells
                // 'cause I can do same
                val apb = isMulti(pb)
                val anb = isMulti(nb)
                if (anb == 3 && apb == 3) {
                    var energyTotal = energy + nb.energy + pb.energy
                    energyTotal /= 3
                    energy = energyTotal
                    nb.energy = energyTotal
                    pb.energy = energyTotal
                }
            }
            val neigh = when (a) {
                1 -> chainPrev
                2 -> chainNext
                else -> null
            }
            if (neigh != null) {
                val apb = isMulti(neigh)
                if (apb == 3) { //if this cell is edge and neighbor isn't give more energy to my cell to extend a chain
                    var h = energy + neigh.energy
                    h /= 4
                    energy = neigh.energy - h
                    neigh.energy = h
                }
            }

            // check energy level. It's time to give birth or die?
            if (energy > 999) { // if too many energy
                if (a == 1 || a == 2)
                    cellMulti() // extend chain if we can
                else
                    cellDouble() // create free cell otherwise
            }
            energy -= 3 // spent 3 energy just for nothing. Life is very expensive thing
            if (energy < 1) { // If cell can't afford be alive then die and turn into organic
                onDie()
                return
            }
            //if cell too deep get minerals (but no more 999)
            val moreMineralsLevels = arrayOf(World.simulation.worldHeight / 2,
                    World.simulation.worldHeight * 5 / 8,
                    World.simulation.worldHeight * 6 / 8,
                    World.simulation.worldHeight * 7 / 8,
                    World.simulation.worldHeight * 15 / 16,
                    World.simulation.worldHeight * 31 / 32,
                    World.simulation.worldHeight * 63 / 64)
            moreMineralsLevels.forEach { if (coordinates.y < it) mineral++ }
            if (mineral > 999)
                mineral = 999
        }
    }

    fun mutate() { // change random byte in DNA
        mind[(Math.random() * MIND_SIZE).toInt()] = (Math.random() * MIND_SIZE).toInt()
    }

    fun hasFreeDirection() = findEmptyDirection() != null

    /**
     * get parameter for command - byte with next position
     */
    fun botGetParam(): Int = mind[(adr + 1) % MIND_SIZE]


    /**
     * get energy from sun depending deep and cell's minerals count
     */
    fun doPhotosynthesis() {
        val t = when {
            mineral < 100 -> 0
            mineral < 400 -> 1
            else -> 2
        }
        var a = 0
        if (chainPrev != null) // synergy?
            a += 4
        if (chainNext != null)
            a += 4
        val energy = (a + 11 - 15.0 * coordinates.y / World.simulation.worldHeight + t).toInt() // formula to calc energy count
        if (energy > 0) {
            this.energy += energy // add energy to cell
            goGreen() // get more green
        }
    }

    /**
     * transform minerals to energy
     */
    fun cellMineral2Energy() {
        val mineralsCount = min(mineral, 100) // max 100 minerals in one time
        goBlue() // do cell more blue
        mineral -= mineralsCount
        energy += mineralsCount * 4 // 1 mineral == 4 energy
    }

    /**
     * move cell if free space
     * @direction - direction to neighbor
     * @isRelative - true if direction is relative
     *
     * @return 2 if there empty on target position
     * 3 if there is a wall
     * 4 if there is organic
     * 5 if there is a alien cell
     * 6 if there is a relative
     */
    fun cellMove(direction: Int, isRelative: Boolean): Int {
        val there = cellSeeCells(direction, isRelative)
        if (there == 2) {
            val dir = if (isRelative) relativeToAbsoluteDirection(direction) else direction
            moveCell(xFromDirection(dir), yFromDirection(dir))
        }
        return there
    }

    /**
     * eat another cell or organic
     * @direction - direction to neighbor
     * @isRelative - true if direction is relative
     *
     * @return 2 if there empty on target position
     * 3 if there is a wall
     * 4 if there is organic
     * 5 if eating was completed successfully (or cell died)
     */
    fun cellEat(direction: Int, isRelative: Boolean): Int {
        energy -= 4 // Lose 4 energy anyway
        val dir = if (isRelative) relativeToAbsoluteDirection(direction) else direction
        val xt = xFromDirection(dir)
        val yt = yFromDirection(dir)
        var there = cellSeeCells(direction, isRelative)
        if (there == 4) {
            deleteBot(World.getCell(xt, yt))
            energy += 100 // get 100 energy
            goRed() // do cell more red
        } else if (there == 5 || there == 6) {
            var min1 = World.getCell(xt, yt).mineral
            val en = World.getCell(xt, yt).energy
            // if enough minerals
            if (mineral >= min1) {
                mineral -= min1 // spent minerals to kill victim
                deleteBot(World.getCell(xt, yt)) // delete victim
                val cl = 100 + en / 2 // add energy
                energy += cl
            } else {
                // if victim have more minerals
                min1 -= mineral // spent minerals to defence
                mineral = 0
                World.getCell(xt, yt).mineral = min1
                if (energy >= 2 * min1) { // try
                    deleteBot(World.getCell(xt, yt)) // delete victim
                    val cl = 100 + en / 2 - 2 * min1 // every victim's mineral cost 2 energy
                    energy += cl
                } else {
                    // if not enough energy - killed by victim
                    World.getCell(xt, yt).mineral = min1 - energy / 2  // victim spent minerals
                    energy = 0 // life over
                }
            }
            goRed()
            there = 5
        }
        return there
    }

    /**
     * see in this direction
     * @direction - direction to neighbor
     * @isRelative - true if direction is relative
     *
     * @return 2 if there empty on target position
     * 3 if there is a wall
     * 4 if there is organic
     * 5 if there is a alien
     * 6 if there is a relative
     */
    fun cellSeeCells(direction: Int, isRelative: Boolean): Int {
        val dir = if (isRelative) relativeToAbsoluteDirection(direction) else direction
        val xt = xFromDirection(dir)
        val yt = yFromDirection(dir)
        return when {
            yt < 0 || yt >= World.simulation.worldHeight -> 3 //DO NOT CHANGE ORDER
            isFreeCell(xt, yt) -> 2
            World.getCell(xt, yt).alive == CONDITION.ORGANIC -> 4
            isRelative(World.getCell(xt, yt)) -> 6
            else -> 5
        }
    }

    /**
     * attacking neighbor's DNA
     */
    fun cellAttackDNA() {
        val dir = relativeToAbsoluteDirection(0)
        val xt = xFromDirection(dir)
        val yt = yFromDirection(dir)
        if (yt >= 0 && yt < World.simulation.worldHeight && World.getCell(xt, yt).alive == CONDITION.ALIVE) {
            // if there is alive cell
            energy -= 10 // Spent 10 energy
            if (energy > 0) // If have enough energy
                World.getCell(xt, yt).mutate()
        }
    }


    /**
     * Share with neighbor in given direction energy or minerals if have surplus
     * @direction - direction to neighbor
     * @isRelative - true if direction is relative
     *
     * @return 2 if there empty
     * 3 if there is a wall
     * 4 if there is organic
     * 5 if sharing was completed successfully
     */
    fun cellShare(direction: Int, isRelative: Boolean): Int {
        val dir = if (isRelative) relativeToAbsoluteDirection(direction) else direction
        val xt = xFromDirection(dir)
        val yt = yFromDirection(dir)
        var there = cellSeeCells(direction, isRelative)
        if (there == 5 || there == 6) {
            val neighborEnergy = World.getCell(xt, yt).energy
            val neighborMineral = World.getCell(xt, yt).mineral
            if (energy > neighborEnergy) {
                val halfDiff = (energy - neighborEnergy) / 2
                energy -= halfDiff
                World.getCell(xt, yt).energy += halfDiff
            }
            if (mineral > neighborMineral) {
                val halfDiff = (mineral - neighborMineral) / 2
                mineral -= halfDiff
                World.getCell(xt, yt).mineral += halfDiff
            }
            there = 5
        }
        return there
    }

    /**
     * Share with neighbor in given direction 1/4 energy or minerals
     *
     * @direction - direction to neighbor
     * @isRelative - true if direction is relative
     *
     * @return 2 if there empty
     * 3 if there is a wall
     * 4 if there is organic
     * 5 if sharing was completed successfully
     */
    fun cellGive(direction: Int, isRelative: Boolean): Int {
        val dir = if (isRelative) relativeToAbsoluteDirection(direction) else direction
        val xt = xFromDirection(dir)
        val yt = yFromDirection(dir)
        var there = cellSeeCells(direction, isRelative)
        if (there == 5 || there == 6) {
            World.getCell(xt, yt).energy += energy / 4
            energy -= energy / 4
            World.getCell(xt, yt).mineral = min(World.getCell(xt, yt).mineral + mineral / 4, 999)
            mineral -= mineral / 4
            there = 5
        }
        return there
    }


    /**
     * Cell will be doubled
     * @return new cell
     */
    fun cellDouble(): ImprovedCell? {
        energy -= 150 // Creating copy cost 150 energy
        if (energy < 150)
            return null// If hasn't enough energy - time to die

        var direction = findEmptyDirection()
        if (direction == null) { // If hasn't free direction - die
            energy = 0
            return null
        }

        direction = relativeToAbsoluteDirection(direction)

        val xt = xFromDirection(direction)
        val yt = yFromDirection(direction)
        if (yt == -1)
            println()
        val newCell = ImprovedCell()

        newCell.mind = mind.clone() // clone DNA from parent
        if (Math.random() < 0.25) { // // in 1/4 cases make mutation - change random byte in DNA
            newCell.mutate()
        }

        newCell.adr = 0 //By default start with 0 command
        newCell.coordinates.x = xt
        newCell.coordinates.y = yt

        newCell.energy = energy / 2 // Redistribution energy and minerals half-by-half
        energy /= 2
        newCell.mineral = mineral / 2
        mineral /= 2

        newCell.alive = CONDITION.ALIVE // It's alive! ALIVE!
        newCell.color = color

        newCell.direction = (Math.random() * 8).toInt() // Random direction

        World.simulation.matrix[xt][yt] = newCell // add child to world
        return newCell
    }

    /**
     * Born new cell of multi-cell life
     */
    fun cellMulti() {
        val previousCell = chainPrev
        val nextCell = chainNext // Links to previous and next cells in chain

        if (previousCell != null && nextCell != null) {
            return // if both neighbours is non-null - already into chain
        }

        val newCell = cellDouble() ?: return // D.R.Y.

//        if (newCell == null)
//            return

        if (nextCell == null) { // Insert cell to end of the chain
            chainNext = newCell
            newCell.chainPrev = this
        } else {
            chainPrev = newCell
            newCell.chainNext = this
        }
    }


    /**
     * direct increase command's address by
     * @offset
     */
    private fun incCommandAddress(offset: Int) {
        adr = (adr + offset) % MIND_SIZE
    }

    /**
     * turn dead cell into organic
     */
    private fun onDie() {
        alive = CONDITION.ORGANIC // It's now organic
        chainPrev?.chainNext = null
        chainNext?.chainPrev = null
        chainPrev = null
        chainNext = null
    }

    /**
     * move cell to (xt, yt). Without checking
     */
    private fun moveCell(xt: Int, yt: Int) {
        if (xt != -1 && yt != -1) {
            World.simulation.matrix[xt][yt] = this
            World.getCell(coordinates.x, coordinates.y).alive = CONDITION.FREE
            coordinates = Coordinates(xt, yt)
        }
    }

    private fun relativeToAbsoluteDirection(direction: Int) = (direction + this.direction) % 8

    /**
     * @return (relative?) direction to free cell (clockwise from forward) or null if no free cells
     */
    private fun findEmptyDirection(): Int? {
        for (i in 0..7) {
            val dir = relativeToAbsoluteDirection(i)
            if (isFreeCell(xFromDirection(dir), yFromDirection(dir)))
                return i
        }
        return null
    }

    /**
     * Is growing energy?
     */
    fun isEnergyGrow(): Boolean {
        val t = when {
            mineral < 100 -> 0
            mineral < 400 -> 1
            else -> 2
        }
        return 10 - 15.0 * coordinates.y / World.simulation.worldHeight + t >= 3
    }

    /**
     * @return true if none or one different in DNA with
     * @cell
     */
    private fun isRelative(cell: ImprovedCell): Boolean {
        if (cell.alive != CONDITION.ALIVE)
            return false
        var wasDifferent = false
        for (i in 0 until MIND_SIZE)
            if (mind[i] != cell.mind[i]) {
                if (wasDifferent)
                    return false
                wasDifferent = true
            }
        return true
    }

    /**
     * @return true is it's correct cell index and its condition isn't free
     */
    private fun isFreeCell(x: Int, y: Int): Boolean = y >= 0 && y < World.simulation.worldHeight && x >= 0 && x < World.simulation.worldWidth
            && World.getCell(x, y).alive == CONDITION.FREE


    /**
     * @direction - absolute direction
     * @return X coordinate near cell
     */
    private fun xFromDirection(direction: Int): Int {
        return when (direction) {
            0, 6, 7 -> (coordinates.x + World.simulation.worldWidth - 1) % World.simulation.worldWidth
            in 2..4 -> (coordinates.x + 1) % World.simulation.worldWidth
            else -> coordinates.x
        }
    }

    /**
     * @direction - absolute direction
     * @return Y coordinate near cell
     */
    private fun yFromDirection(direction: Int): Int {
        return when (direction) {
            in 0..2 -> coordinates.y - 1
            in 4..6 -> coordinates.y + 1
            else -> coordinates.y
        }
    }


    /**
     * Make cell more red on screen
     * @cell - Cell
     * @num - How much red add
     */
    private fun goRed() {
        color = MyColor(0xFF, 0, 0)
    }

    /**
     * Make cell more green on screen
     * @cell - Cell
     * @num - How much green add
     */
    private fun goGreen() {
        color = MyColor(0, 0xFF, 0)
    }

    /**
     * Make cell more blue on screen
     * @cell - Cell
     * @num - How much blue add
     */
    private fun goBlue() {
        color = MyColor(0, 0, 0xFF)
    }

    companion object {
        var MIND_SIZE = 64 //bot's DNA capacity

        enum class CONDITION {
            FREE,
            ORGANIC,
            ALIVE,
        }

        /**
         * delete
         * @cell
         */
        fun deleteBot(cell: ImprovedCell) {
            val prevCell = cell.chainPrev
            val nextCell = cell.chainNext
            if (prevCell != null)  // delete cell from chain
                prevCell.chainNext = null
            if (nextCell != null)
                nextCell.chainPrev = null
            cell.chainPrev = null
            cell.chainNext = null
//            World.simulation.matrix[cell.coordinates.x][cell.coordinates.y] = null // delete from world
            World.getCell(cell.coordinates.x, cell.coordinates.y).alive = CONDITION.FREE // delete from world
        }

        /**
         * @return 0 if cell isn't in chain
         * 1 if have previous cell
         * 2 if have next cell
         * 3 if have both cells
         */
        fun isMulti(cell: ImprovedCell): Int = ((cell.chainNext != null).toInt() shl 1) or (cell.chainPrev != null).toInt() // some binary magic
    }
}

data class Coordinates(var x: Int, var y: Int)
data class MyColor(var red: Int, var green: Int, var blue: Int) {
    fun toColor(): Color = Color(red, green, blue)
}
