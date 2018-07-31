import java.awt.Color
import java.lang.Math.min
import kotlin.math.max

class ImprovedCell {
    private var adr = 0
    private var mineral = 0

    var coordinates = Coordinates(0, 0)
    var energy = 5
    var alive = LV_ALIVE
    var color = MyColor(0, 0, 0)
    var direction = 2
    var mprev: ImprovedCell? = null
    var mnext: ImprovedCell? = null

    var mind = IntArray(MIND_SIZE) // There will store bot's DNA

    private fun relativeToAbsoluteDirection(direction: Int) = (direction + this.direction) % 8

    fun step() {
        if (alive == LV_ORGANIC_SINK) {
            if (coordinates.y != 0 && World.getCell(coordinates.x, coordinates.y - 1) == null)
                moveCell(coordinates.x, coordinates.y - 1)
            else
                alive = LV_ORGANIC_HOLD
            return
        }
        if (alive == 0 || alive == 1)
            return // don't do anything if it's a corpse
        loop@ for (cyc in 0..14) {
            val command = mind[adr]
            when (command) {
                0 -> { // relatively turn
                    val param = botGetParam() % 8 // get turn
                    direction = (direction + param) % 8
                    botIncCommandAddress(2) // go to next command
                }
                1 -> { // absolutely turn
                    direction = botGetParam() % 8
                    botIncCommandAddress(2) // go to next command
                }
                25 -> { //Photosynthesis
                    doPhotosynthesis()
                    botIncCommandAddress(1)
                    break@loop // go out 'cause this command is one if terminating
                }
                26 -> { // relatively step
                    if (isMulti(this) == 0) // only single cells can move
                        botIndirectIncCmdAddress(cellMove(botGetParam() % 8, true)) // change address depending met object
                    else
                        botIncCommandAddress(2)
                    break@loop
                }
                27 -> { // absolutely step
                    if (isMulti(this) == 0) // only single cells can move
                        botIndirectIncCmdAddress(cellMove(botGetParam() % 8, false)) // change address depending met object
                    else
                        botIncCommandAddress(2)
                    break@loop
                }
                28 -> { // relatively eat
                    botIndirectIncCmdAddress(cellEat(botGetParam() % 8, true))
                    break@loop
                }
                29 -> { // absolutely eat
                    botIndirectIncCmdAddress(cellEat(botGetParam() % 8, false))
                    break@loop
                }
                30 -> { // relatively see
                    botIndirectIncCmdAddress(cellSeeCells(botGetParam() % 8, true))
                    break@loop
                }
                31 -> { // absolutely see
                    botIndirectIncCmdAddress(cellSeeCells(botGetParam() % 8, false))
                    break@loop
                }
                32 -> { //relatively share
                    botIndirectIncCmdAddress(cellShare(botGetParam() % 8, true))
                }
                33-> { //33 absolutely share
                    botIndirectIncCmdAddress(cellShare(botGetParam() % 8, false))
                }
                34 -> { //34 relatively give
                    botIndirectIncCmdAddress(cellGive(botGetParam() % 8, true))
                }
                35 -> { //35 absolutely give
                    botIndirectIncCmdAddress(cellGive(botGetParam() % 8, false))
                }
                36 -> { // round horizontal
                    direction = if (Math.random() < 0.5) 3 else 7 // turn into random direction
                    botIncCommandAddress(1)
                }
                37 -> { // height check
                    val param = botGetParam() * World.simulation.worldHeight / MIND_SIZE // get approximate height by DNA
                    botIndirectIncCmdAddress(2 + (coordinates.y >= param).toInt()) // if too low - step by 2 else - step by 3
                }
                38 -> { // energy check
                    val param = botGetParam() * 1000 / MIND_SIZE // get approximate energy by DNA
                    botIndirectIncCmdAddress(2 + (coordinates.y >= param).toInt()) // if too low - step by 2 else - step by 3
                }
                39 -> { // minerals check
                    val param = botGetParam() * 1000 / MIND_SIZE // get approximate minerals by DNA
                    botIndirectIncCmdAddress(2 + (coordinates.y >= param).toInt()) // if too low - step by 2 else - step by 3
                }
                40 -> { // create child in chain
                    val a = isMulti(this)
                    if (a == 3)
                        cellDouble() // create free child only if cell is already in chain
                    else
                        cellMulti()
                    botIncCommandAddress(1)
                    break@loop
                }
                41 -> { // create free-life child
                    val a = isMulti(this)
                    if (a == 0 || a == 3)
                        cellDouble()
                    else
                        cellMulti() // if cell in the edge of chain - it's "free". Cake is a lie
                    botIncCommandAddress(1)
                    break@loop
                }
                42 -> { // check for free cell near
                    botIndirectIncCmdAddress((!hasFreeDirection()).toInt() + 1) // 1 if no free cells 2 otherwise
                }
                43 -> { // check for energy input
                    botIndirectIncCmdAddress((isEnergyGrow()).toInt() + 1) // 1 if no free cells 2 otherwise
                }
                44 -> { // check for minerals input
                    botIndirectIncCmdAddress((coordinates.y <= World.simulation.worldHeight / 2).toInt() + 1)
                }
                45 -> { //check for multi-cell life
                    val mu = isMulti(this)
                    botIndirectIncCmdAddress(when (mu) {
                        0 -> 1
                        3 -> 3
                        else -> 2
                    })
                }
                46 -> { // turn minerals into energy
                    cellMineral2Energy()
                    botIncCommandAddress(1)
                    break@loop
                }
                47 -> { // mutate (change 2 randome bytes)
                    mind[(Math.random() * MIND_SIZE).toInt()] = (Math.random() * MIND_SIZE).toInt()
                    mind[(Math.random() * MIND_SIZE).toInt()] = (Math.random() * MIND_SIZE).toInt()
                    botIncCommandAddress(1)
                    break@loop
                }
                48 -> { // attack DNA
                    cellAttackDNA()
                    botIncCommandAddress(1)
                    break@loop
                }
                else -> {
                    botIncCommandAddress(mind[adr])
                    break@loop
                } //no actions -> it's goto
            }
        }

        //check for energy, child time and distribute energy&minerals with neighbors in a chain
        if (alive == LV_ALIVE) {
            val a = isMulti(this)
            if (a == 3) {
                val pb = mprev
                val nb = mnext

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
                    var enegryTotal = energy + nb.energy + pb.energy
                    enegryTotal /= 3
                    energy = enegryTotal
                    nb.energy = enegryTotal
                    pb.energy = enegryTotal
                }
            }
            val neigh = when (a) {
                1 -> mprev
                2 -> mnext
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
                bot2Organic()
                return
            }
            //if cell too deep get minerals (but no more 999)
            val moreMineralsLevels = arrayOf(World.simulation.worldHeight / 2,
                    World.simulation.worldHeight / 6 * 4,
                    World.simulation.worldHeight / 6 * 5)
            moreMineralsLevels.forEach { if (coordinates.y < it) mineral++ }
            if (mineral > 999)
                mineral = 999
        }
    }

    /**
     * @direction - absolute direction
     * @return X coordinate near cell
     */
    private fun xFromVektorA(direction: Int): Int {
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
    private fun yFromVektorA(direction: Int): Int {
        return when (direction) {
            in 0..2 -> coordinates.y - 1
            in 4..6 -> coordinates.y + 1
            else -> coordinates.y
        }
    }

    /**
     * @return (relative?) direction to free cell (clockwise from forward) or 8 if no free cells
     */
    private fun findEmptyDirection(): Int {
        for (i in 0..7) {
            val dir = relativeToAbsoluteDirection(i)
            val xt = xFromVektorA(dir)
            val yt = yFromVektorA(dir)
            if (yt >= 0 && yt < World.simulation.worldHeight && World.getCell(xt, yt) == null)
                return i
        }
        return 8
    }

    private fun hasFreeDirection() = findEmptyDirection() != 8


    /**
     * get parameter for command - byte with next position
     */
    private fun botGetParam(): Int = mind[(adr + 1) % MIND_SIZE]

    /**
     * direct increase command's address by
     * @offset
     */
    private fun botIncCommandAddress(offset: Int) {
        adr = (adr + offset) % MIND_SIZE
    }

    /**
     * indirect increase command's address
     * @offset - offset to command
     */
    private fun botIndirectIncCmdAddress(offset: Int) = botIncCommandAddress((adr + offset) % MIND_SIZE)



    /**
     * turn cell into organic
     */
    private fun bot2Organic() {
        alive = LV_ORGANIC_HOLD // It's now organic
        mprev?.mnext = null
        mnext?.mprev = null
        mprev = null
        mnext = null
    }

    /**
     * move cell to (xt, yt). Without checking
     */
    private fun moveCell(xt: Int, yt: Int) {
        if (xt != -1 && yt != -1) {
            World.simulation.matrix[xt][yt] = this
            World.simulation.matrix[coordinates.x][coordinates.y] = null
            coordinates.x = xt
            coordinates.y = yt
        }
    }


    /**
     * get energy from sun depending deep and cell's minerals count
     */
    private fun doPhotosynthesis() {
        val t = when {
            mineral < 100 -> 0
            mineral < 400 -> 1
            else -> 2
        }
        var a = 0
        if (mprev != null) // synergy?
            a += 4
        if (mnext != null)
            a += 4
        val energy = (a + 11 - 15.0 * coordinates.y / World.simulation.worldHeight + t).toInt() // formula to calc energy count
        if (energy > 0) {
            this.energy += energy // add energy to cell
            goGreen(energy) // get more green
        }
    }


    /**
     * transform minerals to energy
     */
    private fun cellMineral2Energy() {
        val mineralsCount = min(mineral, 100) // max 100 minerals in one time
        goBlue(mineralsCount) // do cell more blue
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
    private fun cellMove(direction: Int, isRelative: Boolean): Int {
        val there = cellSeeCells(direction, isRelative)
        if (there == 2) {
            val dir = if (isRelative) relativeToAbsoluteDirection(direction) else direction
            moveCell(xFromVektorA(dir), yFromVektorA(dir))
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
    private fun cellEat(direction: Int, isRelative: Boolean): Int {
        energy -= 4 // Lose 4 energy anyway
        val dir = if (isRelative) relativeToAbsoluteDirection(direction) else direction
        val xt = xFromVektorA(dir)
        val yt = yFromVektorA(dir)
        var there = cellSeeCells(direction, isRelative)
        if (there == 4) {
            deleteBot(World.getCell(xt, yt)!!)
            energy += 100 // get 100 energy
            goRed(100) // do cell more red
        } else if (there == 5 || there == 6) {
            var min1 = World.getCell(xt, yt)!!.mineral
            val en = World.getCell(xt, yt)!!.energy
            val redChange: Int

            // if enough minerals
            if (mineral >= min1) {
                mineral -= min1 // spent minerals to kill victim
                deleteBot(World.getCell(xt, yt)!!) // delete victim
                val cl = 100 + en / 2 // add energy
                energy += cl
                redChange = cl
            } else {
                // if victim have more minerals
                min1 -= mineral // spent minerals to defence
                mineral = 0
                World.getCell(xt, yt)!!.mineral = min1
                if (energy >= 2 * min1) { // try
                    deleteBot(World.getCell(xt, yt)!!) // delete victim
                    val cl = 100 + en / 2 - 2 * min1 // every victim's mineral cost 2 energy
                    energy += cl
                    redChange = max(cl, 0)
                } else {
                    // if not enough energy - killed by victim
                    World.getCell(xt, yt)!!.mineral = min1 - energy / 2  // victim spent minerals
                    energy = 0 // life over
                    redChange = 0
                }
            }
            goRed(redChange)
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
    private fun cellSeeCells(direction: Int, isRelative: Boolean): Int {
        val dir = if (isRelative) relativeToAbsoluteDirection(direction) else direction
        val xt = xFromVektorA(dir)
        val yt = yFromVektorA(dir)
        return when {
            yt < 0 || yt >= World.simulation.worldHeight -> 3 //DO NOT CHANGE ORDER
            World.getCell(xt, yt) == null -> 2
            World.getCell(xt, yt)!!.alive <= LV_ORGANIC_SINK -> 4
            isRelative(World.getCell(xt, yt)!!) -> 6
            else -> 5
        }
    }


    /**
     * attacking neighbor's DNA
     */
    private fun cellAttackDNA() {
        val dir = relativeToAbsoluteDirection(0)
        val xt = xFromVektorA(dir)
        val yt = yFromVektorA(dir)
        if (yt >= 0 && yt < World.simulation.worldHeight && World.getCell(xt, yt) != null) {
            if (World.getCell(xt, yt)!!.alive == LV_ALIVE) { // if there is alive cell
                energy -= 10 // Spent 10 energy
                if (energy > 0) { // If have enough energy
                    val ma = (Math.random() * MIND_SIZE).toInt() // changing random byte in DNA
                    val mc = (Math.random() * MIND_SIZE).toInt()
                    World.getCell(xt, yt)!!.mind[ma] = mc
                }
            }
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
    private fun cellShare(direction: Int, isRelative: Boolean): Int {
        val dir = if (isRelative) relativeToAbsoluteDirection(direction) else direction
        val xt = xFromVektorA(dir)
        val yt = yFromVektorA(dir)
        var there = cellSeeCells(direction, isRelative)
        if (there == 5 || there == 6) {
            val neighborEnergy = World.getCell(xt, yt)!!.energy
            val neighborMineral = World.getCell(xt, yt)!!.mineral
            if (energy > neighborEnergy) {
                val halfDiff = (energy - neighborEnergy) / 2
                energy -= halfDiff
                World.getCell(xt, yt)!!.energy += halfDiff
            }
            if (mineral > neighborMineral) {
                val halfDiff = (mineral - neighborMineral) / 2
                mineral -= halfDiff
                World.getCell(xt, yt)!!.mineral += halfDiff
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
    private fun cellGive(direction: Int, isRelative: Boolean): Int {
        val dir = if (isRelative) relativeToAbsoluteDirection(direction) else direction
        val xt = xFromVektorA(dir)
        val yt = yFromVektorA(dir)
        var there = cellSeeCells(direction, isRelative)
        if (there == 5 || there == 6) {
            World.getCell(xt, yt)!!.energy += energy / 4
            energy -= energy / 4
            World.getCell(xt, yt)!!.mineral = min(World.getCell(xt, yt)!!.mineral + mineral / 4, 999)
            mineral -= mineral / 4
            there = 5
        }
        return there
    }


    /**
     * Cell will be doubled
     * @return new cell
     */
    private fun cellDouble(): ImprovedCell? {
        energy -= 150 // Creating copy cost 150 energy
        if (energy < 150)
            return null// If hasn't enought energy - time to die

        var direction = findEmptyDirection()
        if (direction == 8) { // If hasn't free direction - die
            energy = 0
            return null
        }

        direction = relativeToAbsoluteDirection(direction)

        val xt = xFromVektorA(direction)
        val yt = yFromVektorA(direction)
        if (yt == -1)
            println()
        val newCell = ImprovedCell()

        newCell.mind = mind.clone() // clone DNA from parent
        if (Math.random() < 0.25) { // // in 1/4 cases make mutation - change random byte in DNA
            val ma = (Math.random() * MIND_SIZE).toInt() // byte pos
            val mc = (Math.random() * MIND_SIZE).toInt() // byte value
            newCell.mind[ma] = mc
        }

        newCell.adr = 0 //By default start with 0 command
        newCell.coordinates.x = xt
        newCell.coordinates.y = yt

        newCell.energy = energy / 2 // Redistribution energy and minerals half-by-half
        energy /= 2
        newCell.mineral = mineral / 2
        mineral /= 2

        newCell.alive = 3 // It's alive! ALIVE!
        newCell.color = color

        newCell.direction = (Math.random() * 8).toInt() // Random direction

        World.simulation.matrix[xt][yt] = newCell // add child to world
        return newCell
    }

    /**
     * Borning new cell of multi-cell life
     */
    private fun cellMulti() {
        val previousCell = mprev
        val nextCell = mnext // Links to previous and next cells in chain

        if (previousCell != null && nextCell != null) {
            return // if both neighbours is non-null - already into chain
        }

        val newCell = cellDouble() ?: return // D.R.Y.

//        if (newCell == null)
//            return

        if (nextCell == null) { // Insert cell to end of the chain
            mnext = newCell
            newCell.mprev = this
        } else {
            mprev = newCell
            newCell.mnext = this
        }
    }


    /**
     * Is growing energy?
     */
    private fun isEnergyGrow(): Boolean {
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
        if (cell.alive != LV_ALIVE)
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
     * Make cell more red on screen
     * @cell - Cell
     * @num - How much red add
     */
    private fun goRed(num: Int) {
        color = MyColor(0xFF, 0, 0)
//        color.red = min(color.green + num, 255)
//        color.blue = max(color.blue - (num shr 1), 0)
//        color.green = max(color.green - (num shr 1), 0)
    }

    /**
     * Make cell more green on screen
     * @cell - Cell
     * @num - How much green add
     */
    private fun goGreen(num: Int) {
        color = MyColor(0, 0xFF, 0)
//        color.green = min(color.green + num, 255)
//        color.red = max(color.red - (num shr 1), 0)
//        color.blue = max(color.blue - (num shr 1), 0)
    }

    /**
     * Make cell more blue on screen
     * @cell - Cell
     * @num - How much blue add
     */
    private fun goBlue(num: Int) {
        color = MyColor(0, 0, 0xFF)
//        color.blue = min(color.blue + num, 255)
//        color.red = max(color.red - (num shr 1), 0)
//        color.green = max(color.green - (num shr 1), 0)
    }

    companion object {
        //Cell's state
        var LV_FREE = 0 // Free space
        var LV_ORGANIC_HOLD = 1 // Cell is died and now there are organic
        var LV_ORGANIC_SINK = 2 // Cell is died and now there are organic which is moving down
        var LV_ALIVE = 3  // Cell is alive

        var MIND_SIZE = 64 //bot's DNA capacity

        /*enum class DIRECTIONS {
            UP,
            UP_LEFT,
            LEFT,
            DOWN_LEFT,
            DOWN,
            DOWN_RIGHT,
            RIGHT,
            UP_RIGHT
        }*/

        /**
         * delete
         * @cell
         */
        fun deleteBot(cell: ImprovedCell) {
            val prevCell = cell.mprev
            val nextCell = cell.mnext
            if (prevCell != null)  // delete cell from chain
                prevCell.mnext = null
            if (nextCell != null)
                nextCell.mprev = null
            cell.mprev = null
            cell.mnext = null
            World.simulation.matrix[cell.coordinates.x][cell.coordinates.y] = null // delete from world
        }

        /**
         * @return 0 if cell isn't in chain
         * 1 if have previous cell
         * 2 if have next cell
         * 3 if have both cells
         */
        fun isMulti(cell: ImprovedCell): Int = ((cell.mnext != null).toInt() shl 1) or (cell.mprev != null).toInt() // some binary magic
    }

    
}

data class Coordinates(var x: Int, var y: Int)
data class MyColor(var red: Int, var green: Int, var blue: Int) {
    fun toColor(): Color = Color(red, green, blue)
}

fun Byte.toUnsignedInt(): Int {
    if (this < 0)
        return this + 256
    return this.toInt()
}