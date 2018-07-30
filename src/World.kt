import java.awt.*
import javax.swing.*
import kotlin.concurrent.thread

class World(var worldWidth: Int, var worldHeight: Int) : JFrame() {
    var matrix = Array(worldWidth) { Array<ImprovedCell?>(worldHeight) { null } } // World's matrix

    private var day = 0
    private var population = 0
    private var organic = 0
    private val xBoundary = 10
    private val yBoundary = 50
    private val cellSize = 4
    private var finished = Array(4) {false}
    private var working = true
    private var needRedraw = false

    init {
        simulation = this

        title = "CyberLife 1.0.0 by Ky3He4iK"
        size = Dimension(worldWidth * cellSize + xBoundary * 2, worldHeight * cellSize + yBoundary * 2)
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val frameSize = Dimension(size.width % screenSize.width, size.height % screenSize.height)
        setLocation((screenSize.width - frameSize.width) shl 1, (screenSize.height - frameSize.height) shl 1) //center frame

        defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        isVisible = true

    }

    override fun paint(g: Graphics?) {
        g!!.drawRect(xBoundary - 1, yBoundary - 1, simulation.worldWidth * cellSize + 1, simulation.worldHeight * cellSize + 1)

        population = 0
        organic = 0
        for (y in 0 until worldHeight) {
            for (x in 0 until worldWidth) {
                when {
                    matrix[x][y] == null -> {
                        g.color = Color.WHITE
                        g.fillRect(xBoundary + x * cellSize, yBoundary + y * cellSize, cellSize, cellSize)
                    }
                    matrix[x][y]!!.alive == 1 || matrix[x][y]!!.alive == 2 -> {
                        g.color = Color(200, 200, 200)
                        g.fillRect(xBoundary + x * cellSize, yBoundary + y * cellSize, cellSize, cellSize)
                        organic++
                    }
                    matrix[x][y]!!.alive == 3 -> {
                        g.color = Color.BLACK
                        g.fillRect(xBoundary + x * cellSize, yBoundary + y * cellSize, cellSize, cellSize)
//                        val green = min(max(matrix[x][y]?.color?.green?.toInt() ?: 100 - (matrix[x][y]?.color?.green ?: 100.toByte()) * (matrix[x][y]?.energy ?: 200) / 2000, 0), 255)
//                        val blue = ((matrix[x][y]?.color?.blue?.toInt() ?: 100) * 0.8 - (matrix[x][y]?.color?.blue?.toInt() ?: 100) * (matrix[x][y]?.mineral ?: 200) / 2000).toInt()
//                         It's seems like a magic
                        if (matrix[x][y] != null) {
                            g.color = matrix[x][y]!!.color.toColor()
                            g.fillRect(xBoundary + x * cellSize + 1, yBoundary + y * cellSize + 1, cellSize - 1, cellSize - 1)
                            population++
                        }
                    }
                }
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

    fun run() {
        val thrCount = 4

        finished = Array(thrCount) {false}
        val widthPart = worldWidth / thrCount
        for (ind in 0 until thrCount) {
            thread(isDaemon = true,
                    name = "$ind'th calculating thread") {
                myThread(ind * widthPart, (ind + 1) * widthPart, ind)
            }
        }
        thread(isDaemon = true,
                name = "drawing thread") {
            drawingThread()
        }

        while (day < Int.MAX_VALUE) {
            finished = Array(thrCount) {true}
            Thread.sleep(5)
            for (i in 0 until thrCount)
                while (finished[i])
                    Thread.sleep(1)
            day++
            if (day % 10 == 0)
                needRedraw = true
//                paint(graphics) //update world info on screen

        }
        working = false
    }

    private fun drawingThread() {
        while (working) {
            while (!needRedraw)
                Thread.sleep(10)
            paint(graphics)
            needRedraw = false
        }
    }

    private fun distributedPart(widthRange: IntRange) {
        for (yw in 0 until worldHeight)
            for (xw in widthRange)
                try {
                    matrix[xw][yw]?.step() // do actions for every cell
                } catch (e: NullPointerException) {
                    println("Mutlithreading is cool")
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

    // делает паузу
    fun sleep() {
        Thread.sleep(20)
    }

    fun generateAdam() { // generate empty world with 1 cell
        val cell = ImprovedCell()
        cell.coordinates = Coordinates(worldWidth / 2, worldHeight / 2) //move cell into center
        cell.energy = 990 // first cell is full of energy
        cell.direction = 5
        cell.mind = IntArray(ImprovedCell.MIND_SIZE) { 25 } // fill genome by command 25 - photosynthesis
        cell.color = MutableColor(0, 255.toByte(), 0)
        matrix[cell.coordinates.x][cell.coordinates.y] = cell
    }

    companion object {
        var simulation: World = World(200, 100)

        fun getCell(xPos: Int, yPos: Int): ImprovedCell? {
            if (xPos < 0 || xPos >= simulation.worldWidth || yPos < 0 || yPos >= simulation.worldHeight)
                return null
            return simulation.matrix[xPos][yPos]
        }
    }
}

fun Boolean.toInt(): Int = if (this) 1 else 0

fun main(args: Array<String>) {
    World.simulation.generateAdam()
    World.simulation.run()
}
