// GameRenderer.kt
import android.content.Context
import android.graphics.*
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.SystemClock
import com.example.myapplication.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CopyOnWriteArrayList
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 启动应用
 *   │
 *   ▼
 * 创建GameRenderer实例
 *   │
 *   ▼
 * onSurfaceCreated ──→ 加载着色器/纹理/初始化资源
 *   │
 *   ▼
 * onSurfaceChanged ──→ 设置视口/初始化矩阵
 *   │
 *   ▼
 * 主循环开始 → onDrawFrame（持续循环）
 *   │   ├─→ 更新游戏逻辑
 *   │   ├─→ 绘制游戏对象
 *   │   └─→ 绘制UI元素
 *   │
 *   ▼
 * 用户触摸事件 → handleTouch
 *   │   ├─→ 坐标转换
 *   │   └─→ 更新游戏状态
 *   │
 *   ▼
 * 退出时自动释放OpenGL资源
 *
 */
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

    // 边缘效果着色器代码
    private val edgeVertexShaderCode = """
        attribute vec4 aPosition;
        varying vec2 vUV;
        void main() {
            gl_Position = aPosition;
            vUV = (aPosition.xy + 1.0) * 0.5; // 转换到0-1范围
        }
    """

    private val edgeFragmentShaderCode = """
        precision mediump float;
        varying vec2 vUV;
        uniform float uRedIntensity;

        void main() {
            // 计算到边缘的距离（四个方向）
            float edge = min(
                min(vUV.x, 1.0 - vUV.x),
                min(vUV.y, 1.0 - vUV.y)
            );
            
            // 边缘渐变范围（0.05表示5%屏幕边缘）
            float edgeWidth = 0.05;
            float alpha = smoothstep(edgeWidth, 0.0, edge) * uRedIntensity;
            
            gl_FragColor = vec4(1.0, 0.0, 0.0, alpha);
        }
    """
    private var edgePositionHandle = 0

    // OpenGL handles
    private var program = 0
    private var edgeProgram = 0
    private var muMVPMatrixHandle = 0
    private var maPositionHandle = 0
    private var maTexCoordHandle = 0
    private var edgeRedIntensityHandle = 0
    private var edgeStartTime = 0L

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
        // 设置背景色
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        // 加载着色器程序
        initShaders()
        // 加载所有纹理资源
        loadTextures()
        // 初始化文字系统
        initText()
    }


    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // 设置视口尺寸
        GLES20.glViewport(0, 0, width, height)
        // 初始化投影矩阵（正交投影）
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
        // 清空颜色缓冲区
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // 更新游戏状态
        updateGameState()

        // 绑定着色器程序
        GLES20.glUseProgram(program)

        // 绘制背景
        drawBackground()

        // 绘制玩家角色
        drawPlayer()

        // 绘制小行星
        drawAsteroids()

        // 绘制爆炸效果
        drawExplosions()

        // 绘制UI文字
        drawTexts()

        // 全屏效果
        drawEdgeEffect()
    }


    /**
     * 编译着色器代码
     * @param type 着色器类型（GLES20.GL_VERTEX_SHADER 或 GLES20.GL_FRAGMENT_SHADER）
     * @param code GLSL着色器源代码字符串
     * @return 生成的着色器对象ID（0表示失败）
     */
    private fun initShaders() {
        program = createProgram(vertexShaderCode, fragmentShaderCode)
        // 编译边缘着色器
        edgeProgram = GLES20.glCreateProgram().also {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, edgeVertexShaderCode)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, edgeFragmentShaderCode)
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        edgeRedIntensityHandle = GLES20.glGetUniformLocation(edgeProgram, "uRedIntensity")
        edgePositionHandle = GLES20.glGetAttribLocation(edgeProgram, "aPosition")

        maPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        maTexCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        muMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
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

    /**
     * 加载游戏所需的所有纹理资源
     * 初始化各游戏元素的纹理ID：
     * - 背景纹理
     * - 玩家角色纹理
     * - 小行星纹理
     * - 爆炸效果纹理
     */
    private fun loadTextures() {
        backgroundTextureId = loadTexture(R.drawable.transparent)
        playerTextureId = loadTexture(R.drawable.ic_plant)
        asteroidTextureId = loadTexture(R.drawable.ic_stat)
        explosionTextureId = loadTexture(R.drawable.ic_one)
    }

    /**
     * 加载并配置纹理资源
     * @param resId 图片资源ID
     * @return 生成的纹理对象ID
     */
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

    /**
     * 初始化文字显示系统
     * 添加初始分数显示在屏幕右上角位置
     */
    private fun initText() {
        addText("Score: 0", 0.1f, 0.9f)
    }

    /**
     * 添加文字到渲染队列
     * @param text 要显示的文本内容
     * @param x 水平位置（标准化坐标，0-1）
     * @param y 垂直位置（标准化坐标，0-1）
     */
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

    /**
     * 陨石生成算法
     */
    private fun spawnAsteroids() {
        if (asteroids.size < 4 && Math.random() < 0.02) {
            val xPositions = listOf(0.1f, 0.3f, 0.6f, 0.8f)
            asteroids.add(
                Asteroid(
                    x = xPositions[asteroids.size % 4],
                    y = 1.2f,
                    speed = 0.005f
                )
            )
        }
    }

    /**
     * 更新陨石坐标
     */
    private fun updatePositions() {
        asteroids.forEach { it.y -= it.speed }
        asteroids.removeIf { it.y < -0.2f }
    }

    /**
     * 遍历检查是否碰撞
     */
    private fun checkCollisions() {
        asteroids.forEach { asteroid ->
            if (checkCollision(asteroid.x, asteroid.y, playerX, playerY)) {
                explosions.add(Explosion(asteroid.x, asteroid.y, System.currentTimeMillis()))
                collisionRedIntensity = 1f
                asteroids.remove(asteroid)
            }
        }
    }

    /**
     * 碰撞检测算法
     */
    private fun checkCollision(ax: Float, ay: Float, px: Float, py: Float): Boolean {

        return !(ax + 0.01f < px - 0.01f ||
                ax - 0.01f > px + 0.01f ||
                ay + 0.01f < py - 0.01f ||
                ay - 0.01f > py + 0.01f)
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
            // 启用混合
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            GLES20.glUseProgram(edgeProgram)
            GLES20.glUniform1f(edgeRedIntensityHandle, collisionRedIntensity)

            // 全屏四边形顶点数据（NDC坐标）
            val vertices = floatArrayOf(
                -1f, -1f,  // 左下
                1f, -1f,   // 右下
                -1f, 1f,   // 左上
                1f, 1f     // 右上
            )
            val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(vertices)
                    position(0)
                }

            // 设置顶点属性
            GLES20.glEnableVertexAttribArray(edgePositionHandle)
            GLES20.glVertexAttribPointer(
                edgePositionHandle,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                vertexBuffer
            )

            // 绘制全屏四边形
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            // 恢复状态
            GLES20.glDisableVertexAttribArray(edgePositionHandle)
            GLES20.glDisable(GLES20.GL_BLEND)
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

