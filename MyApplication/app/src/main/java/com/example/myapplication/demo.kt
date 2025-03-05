// GameRenderer.kt
import android.content.Context
import android.graphics.*
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.example.myapplication.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.LinkedList
import java.util.concurrent.CopyOnWriteArrayList
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GameRenderer(private val context: Context) : GLSurfaceView.Renderer {
    // Shader codes and matrix tools
    private val vertexShaderCode = """
    attribute vec4 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    uniform mat4 uMVPMatrix;
    void main() {
        gl_Position = uMVPMatrix * aPosition;
        vTexCoord = aTexCoord;
    }"""

    private val fragmentShaderCode = """
    precision mediump float;
    varying vec2 vTexCoord;
    uniform sampler2D uTexture;
    void main() {
        gl_FragColor = texture2D(uTexture, vTexCoord);
    }"""

    private val edgeRedShaderCode = """
    precision mediump float;
    varying vec2 vTexCoord;
    uniform float uRedIntensity;
    void main() {
        vec2 pos = abs(vTexCoord - 0.5) * 2.0;
        float edge = max(pos.x, pos.y);
        float red = smoothstep(0.9, 1.0, edge) * uRedIntensity;
        gl_FragColor = vec4(red, 0.0, 0.0, red);
    }"""

    // OpenGL handles
    private var program = 0
    private var edgeProgram = 0
    private var muMVPMatrixHandle = 0
    private var maPositionHandle = 0
    private var maTexCoordHandle = 0
    private var edgeRedIntensityHandle = 0

    // Game objects
    private val asteroids = CopyOnWriteArrayList<Asteroid>()
    private val explosions = CopyOnWriteArrayList<Explosion>()
    private val texts = CopyOnWriteArrayList<GameText>()
    var playerX = 0.5f
    var playerY = 0.1f
    private var collisionRedIntensity = 0f

    // Textures
    private var backgroundTextureId = 0
    private var playerTextureId = 0
    private var asteroidTextureId = 0
    private var explosionTextureId = 0

    // Buffers
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    // Matrix
    private val projectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
    private val screenWidth = context.resources.displayMetrics.widthPixels.toFloat()
    private val unitSizeX get() = 80f / screenWidth
    private val unitSizeY get() = 80f / screenHeight
    // 全屏顶点数据
    private val fullScreenCoords = floatArrayOf(
        -1.0f, -1.0f,  // 左下
        1.0f, -1.0f,   // 右下
        -1.0f, 1.0f,   // 左上
        1.0f, 1.0f     // 右上
    )
    private val fullScreenVertexBuffer: FloatBuffer

    init {
        // 初始化全屏顶点缓冲
        fullScreenVertexBuffer = ByteBuffer.allocateDirect(fullScreenCoords.size * 4)
            .run {
                order(ByteOrder.nativeOrder())
                asFloatBuffer().apply {
                    put(fullScreenCoords)
                    position(0)
                }
            }
        // Initialize vertex data
        val squareCoords = floatArrayOf(
            -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f
        )
        vertexBuffer = ByteBuffer.allocateDirect(squareCoords.size * 4)
            .run {
                order(ByteOrder.nativeOrder())
                asFloatBuffer().apply {
                    put(squareCoords)
                    position(0)
                }
            }

        val texCoords = floatArrayOf(
            0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f
        )
        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .run {
                order(ByteOrder.nativeOrder())
                asFloatBuffer().apply {
                    put(texCoords)
                    position(0)
                }
            }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        initShaders()
        loadTextures()
        initText()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.orthoM(projectionMatrix, 0, 0f, 1f, 0f, 1f, -1f, 1f)
    }

    // 修改模型矩阵生成方法
    private fun getModelMatrix(x: Float, y: Float): FloatArray {
        val matrix = FloatArray(16).apply {
            Matrix.setIdentityM(this, 0)
            // 先缩放再平移
            Matrix.scaleM(this, 0, unitSizeX, unitSizeY, 1f)
            Matrix.translateM(this, 0, x / unitSizeX - 0.5f, y / unitSizeY - 0.5f, 0f)
            Matrix.multiplyMM(this, 0, projectionMatrix, 0, this, 0)
        }
        return matrix
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        updateGameState()
        drawBackground()
        drawAsteroids()
        drawPlayer()
        drawExplosions()
        drawTexts()
        drawEdgeEffect()
    }

    private fun initShaders() {
        program = createProgram(vertexShaderCode, fragmentShaderCode)
        edgeProgram = createProgram(vertexShaderCode, edgeRedShaderCode)

        maPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        maTexCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        muMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        edgeRedIntensityHandle = GLES20.glGetUniformLocation(edgeProgram, "uRedIntensity")
    }

    private fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)
        return GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vertexShader)
            GLES20.glAttachShader(this, fragmentShader)
            GLES20.glLinkProgram(this)
        }
    }

    private fun loadShader(type: Int, code: String): Int {
        return GLES20.glCreateShader(type).apply {
            GLES20.glShaderSource(this, code)
            GLES20.glCompileShader(this)
        }
    }

    private fun loadTextures() {
        backgroundTextureId = loadTexture(R.drawable.transparent)
        playerTextureId = loadTexture(R.drawable.ic_plant)
        asteroidTextureId = loadTexture(R.drawable.ic_stat)
        explosionTextureId = loadTexture(R.drawable.ic_one)
    }

    private fun loadTexture(resId: Int): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        BitmapFactory.decodeResource(context.resources, resId).apply {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, this, 0)
            recycle()
        }
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        return textures[0]
    }

    private fun initText() {
        addText("Score: 0", 0.1f, 0.9f)
    }

    fun addText(text: String, x: Float, y: Float) {
        val bitmap = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            Paint().apply {
                color = Color.WHITE
                textSize = 20f
                setShadowLayer(10f, 0f, 0f, Color.YELLOW)
                canvas.drawText(text, 30f, 70f, this)
            }
        }
        texts.add(GameText(x, y, loadTexture(bitmap)))
        bitmap.recycle()
    }

    private fun loadTexture(bitmap: Bitmap): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return textures[0]
    }

    private fun updateGameState() {
        spawnAsteroids()
        updatePositions()
        checkCollisions()
        updateExplosions()
    }

    private fun spawnAsteroids() {
        if (asteroids.size < 4 && Math.random() < 0.02) {
            val xPositions = listOf(0.1f, 0.3f, 0.6f, 0.8f)
            asteroids.add(Asteroid(
                x = xPositions[asteroids.size % 4],
                y = 1.2f,
                speed = 0.005f
            ))
        }
    }

    private fun updatePositions() {
        asteroids.forEach { it.y -= it.speed }
        asteroids.removeIf { it.y < -0.2f }
    }

    private fun checkCollisions() {
        asteroids.forEach { asteroid ->
            if (checkCollision(asteroid.x, asteroid.y, playerX, playerY)) {
                explosions.add(Explosion(asteroid.x, asteroid.y, System.currentTimeMillis()))
                collisionRedIntensity = 1f
                asteroids.remove(asteroid)
            }
        }
    }

    private fun checkCollision(ax: Float, ay: Float, px: Float, py: Float): Boolean {
        return !(ax + 0.05f < px - 0.01f ||
                ax - 0.05f > px + 0.01f ||
                ay - 0.05f > py + 0.01f)
    }

    private fun updateExplosions() {
        explosions.removeIf { System.currentTimeMillis() - it.startTime > 1000 }
        collisionRedIntensity = (collisionRedIntensity - 0.02f).coerceAtLeast(0f)
    }

    // Drawing methods
    private fun drawBackground() {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, projectionMatrix, 0)
        drawObject(identityMatrix(), backgroundTextureId)
    }

    private fun drawAsteroids() {
        asteroids.forEach {
            drawObject(getModelMatrix(it.x, it.y), asteroidTextureId)
        }
    }

    private fun drawPlayer() {
        drawObject(getModelMatrix(playerX, playerY), playerTextureId)
    }

    private fun drawExplosions() {
        explosions.forEach {
            drawObject(getModelMatrix(it.x, it.y), explosionTextureId)
        }
    }

    private fun drawTexts() {
        texts.forEach {
            drawObject(getModelMatrix(it.x, it.y), it.textureId)
        }
    }

    private fun drawEdgeEffect() {
        if (collisionRedIntensity > 0) {
            GLES20.glUseProgram(edgeProgram)
            GLES20.glUniform1f(edgeRedIntensityHandle, collisionRedIntensity)

            // 使用全屏顶点数据
            GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, identityMatrix(), 0)
            GLES20.glEnableVertexAttribArray(maPositionHandle)
            GLES20.glVertexAttribPointer(
                maPositionHandle,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                fullScreenVertexBuffer
            )

            // 禁用纹理坐标属性
            GLES20.glDisableVertexAttribArray(maTexCoordHandle)

            // 绘制全屏三角形
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            // 恢复默认程序
            GLES20.glUseProgram(program)
        }
    }

    private fun drawObject(matrix: FloatArray, textureId: Int) {
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, matrix, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glEnableVertexAttribArray(maPositionHandle)
        GLES20.glVertexAttribPointer(maPositionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(maTexCoordHandle)
        GLES20.glVertexAttribPointer(maTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun identityMatrix() = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private fun translateMatrix(x: Float, y: Float): FloatArray {
        return FloatArray(16).apply {
            Matrix.setIdentityM(this, 0)
            Matrix.translateM(this, 0, x - 0.5f, y - 0.5f, 0f)
            Matrix.multiplyMM(this, 0, projectionMatrix, 0, this, 0)
        }
    }

    // Data classes
    data class Asteroid(var x: Float, var y: Float, val speed: Float)
    data class Explosion(val x: Float, val y: Float, val startTime: Long)
    data class GameText(val x: Float, val y: Float, val textureId: Int)
}

