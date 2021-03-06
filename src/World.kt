import java.awt.*
import javax.swing.*
import kotlin.concurrent.thread

class World(var worldWidth: Int, var worldHeight: Int) : JFrame() {
    var matrix = Array(worldWidth) { Array(worldHeight) { ImprovedCell() } } // World's matrix

    private var colors = Array(worldWidth) { Array<Color>(worldHeight) { Color.BLACK } } // World's matrix
    private var day = 0
    private var population = 0
    private var organic = 0
    private val xBoundary = 10
    private val yBoundary = 50
    private val cellSize = 5
    private var finished = Array(4) { false }
    private var working = true

    init {
        simulation = this
        title = "CyberLife 1.2.3 by Ky3He4iK"
        size = Dimension(worldWidth * cellSize + xBoundary * 2, worldHeight * cellSize + yBoundary * 2)
        setLocation(0, 0)
        defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        isVisible = true
    }

    fun run() {
        val thrCount = 4

        finished = Array(thrCount) { false }
        val widthPart = worldWidth / thrCount
        for (ind in 0 until thrCount)
            thread(isDaemon = true, name = "$ind'th calculating thread") {
                myThread(ind * widthPart, (ind + 1) * widthPart, ind)
            }

        thread(isDaemon = true, name = "drawing thread") {
            drawingThread()
        }

        while (day < Int.MAX_VALUE) {
            finished = Array(thrCount) { true }
            Thread.sleep(20)
            for (i in 0 until thrCount)
                while (finished[i])
                    Thread.sleep(1)
            day++
        }
        working = false
    }

    fun generateAdam() { // generate empty world with 1 cell
        val cell = ImprovedCell()
        cell.coordinates = Coordinates(worldWidth / 2, worldHeight / 2) //move cell into center
        cell.energy = 990 // first cell is full of energy
        cell.direction = 5
        cell.mind = IntArray(ImprovedCell.MIND_SIZE) { 22 } // fill genome by command 22 - photosynthesis
        cell.color = MyColor(0, 255, 0)
        cell.alive = ImprovedCell.Companion.CONDITION.ALIVE
        matrix[cell.coordinates.x][cell.coordinates.y] = cell
    }

    private fun drawingThread() {
        var c = 0L
        val updateRate = 100L
        val fullUpdateRate = 1000 / updateRate
        while (working) {
            Thread.sleep(updateRate)
            paint(graphics, (c++) % fullUpdateRate == 0L) //full update every 4 seconds
        }
    }

    private fun myThread(widthStart: Int, widthStop: Int, ind: Int) {
        while (true) {
            while (!finished[ind])
                Thread.sleep(2)
            distributedPart(widthStart until widthStop)
            finished[ind] = false
        }
    }

    private fun distributedPart(widthRange: IntRange) {
        for (yw in 0 until worldHeight)
            for (xw in widthRange)
                matrix[xw][yw].step() // do actions for every cell
    }

    private fun paint(g: Graphics?, fullRefresh: Boolean) {
        if (g == null)
            return
        if (fullRefresh)
            g.drawRect(xBoundary - 1, yBoundary - 1, simulation.worldWidth * cellSize + 1, simulation.worldHeight * cellSize + 1)
        population = 0
        organic = 0
        for (y in 0 until worldHeight)
            for (x in 0 until worldWidth) {
                val color = getCellColor(simulation.matrix[x][y])
                if (fullRefresh || colors[x][y] != color) {
                    g.color = color
                    g.fillRect(xBoundary + x * cellSize, yBoundary + y * cellSize, cellSize, cellSize)
                    colors[x][y] = color
                }
            }
        val fieldWidth = 120
        g.color = Color.WHITE
        g.fillRect(xBoundary, yBoundary / 4, fieldWidth, yBoundary / 2)
        g.color = Color.BLACK
        g.drawString("Day: $day", xBoundary + 2, yBoundary * 3 / 4)

        g.color = Color.WHITE
        g.fillRect(xBoundary * 2 + fieldWidth, yBoundary / 4, fieldWidth, yBoundary / 2)
        g.color = Color.BLACK
        g.drawString("Population: $population", xBoundary * 2 + fieldWidth + 2, yBoundary * 3 / 4)

        g.color = Color.WHITE
        g.fillRect(xBoundary * 3 + fieldWidth * 2, yBoundary / 4, fieldWidth, yBoundary / 2)
        g.color = Color.BLACK
        g.drawString("Organic: $organic", xBoundary * 3 + fieldWidth * 2 + 2, yBoundary * 3 / 4)
    }

    private fun getCellColor(cell: ImprovedCell): Color {
        return when (cell.alive) {
            ImprovedCell.Companion.CONDITION.FREE -> Color.WHITE
            ImprovedCell.Companion.CONDITION.ORGANIC -> {
                organic++
                Color.GRAY
            }
            ImprovedCell.Companion.CONDITION.ALIVE -> {
                population++
                cell.color.toColor()
            }
        }
    }

    companion object {
        var simulation: World = World(200, 100)

        fun getCell(xPos: Int, yPos: Int): ImprovedCell = simulation.matrix[xPos][yPos]
    }
}

fun Boolean.toInt(): Int = if (this) 1 else 0

fun main(args: Array<String>) {
    checkGens()
    World.simulation.generateAdam()
    World.simulation.run()
}
