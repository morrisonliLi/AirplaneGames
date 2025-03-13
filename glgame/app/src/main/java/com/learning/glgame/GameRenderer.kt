// GameRenderer.kt
import android.content.Context
import android.graphics.*
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Build
import android.os.SystemClock
import com.learning.glgame.R
import com.learning.glgame.model.Asteroid
import com.learning.glgame.model.AsteroidProps
import com.learning.glgame.model.AsteroidsGameConstant.edgeFragmentShaderCode
import com.learning.glgame.model.AsteroidsGameConstant.edgeVertexShaderCode
import com.learning.glgame.model.AsteroidsGameConstant.fragmentShaderCode
import com.learning.glgame.model.AsteroidsGameConstant.vertexShaderCode
import com.learning.glgame.model.CoordinateData
import com.learning.glgame.model.Explosion
import com.learning.glgame.model.GameText
import com.learning.glgame.utils.GlUtil
import com.learning.glgame.utils.RenderBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CopyOnWriteArrayList
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.random.Random

/**
 * @author: morrisonli
 * @email: morrisonli@foxmail.com
 * v1:
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
 *  v2:
 *  新增功能：
 *  1. 道具系统（冻结/加速效果）
 *  2. 无敌状态机制
 *  3. 离屏渲染支持
 *  4. 动态坐标标准化计算
 *  5. 增强型碰撞检测
 *  6. 多状态边缘特效
 *  渲染流程改进：
 *  主场景渲染 → 离屏FrameBuffer → 后期处理 → 屏幕输出
 */
class GameRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var isChangePic: Boolean = false // 是否切换图片
    private var invincibility: Boolean = false // 是否无敌
    private var edgePositionHandle = 0

    // OpenGL handles
    private var program = 0
    private var edgeProgram = 0
    private var muMVPMatrixHandle = 0
    private var maPositionHandle = 0
    private var maTexCoordHandle = 0
    private var edgeRedIntensityHandle = 0
    private var edgeUseRed = 0

    // Game objects
    private val asteroids = CopyOnWriteArrayList<Asteroid>()
    private val props = CopyOnWriteArrayList<AsteroidProps>()
    private val explosions = CopyOnWriteArrayList<Explosion>()
    private var gameResult: Int = 0
    private var gameTextDrawable = GameText(0f, 0f, 0)
    var playerX = 0.5f
    var playerY = 0.1f
    private var collisionRedIntensity = 0f
    private var collisionProps = false

    // Textures
    private var backgroundTextureId = 0
    private var propsTextureId = 0
    private var playerTextureId = 0
    private var asteroidTextureId = 0
    private var explosionTextureId = 0

    // Buffers
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    // Matrix
    private val projectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private var screenHeight = 0
    private var screenWidth = 0
    private var glCoordinateData = CoordinateData()

    // 全屏顶点数据
    private val fullScreenCoords = floatArrayOf(
        -1.0f, -1.0f,  // 左下
        1.0f, -1.0f,   // 右下
        -1.0f, 1.0f,   // 左上
        1.0f, 1.0f     // 右上
    )
    private val fullScreenVertexBuffer: FloatBuffer

    private var frameBuffer: RenderBuffer? = null

    private var freezeAsteroid = false

    private var accelerateAsteroid = false

    private var lastPropsTime = 0L


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

        initShaders()
        // 加载所有纹理资源
        loadTextures()
        // 初始化文字系统
        initText()
    }


    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // 设置视口尺寸
        GLES20.glViewport(0, 0, width, height)
        glCoordinateData.apply {
            this.glTextWidth = 125f / screenWidth.toFloat()
            this.glTextHeight = 40f / screenHeight.toFloat()
            this.glAsteroidWidth = 20f / screenWidth.toFloat()
            this.glAsteroidHeight = 30f / screenHeight.toFloat()
            this.glPlayerWidth = 30f / screenWidth.toFloat()
            this.glPlayerHeight = 20f / screenHeight.toFloat()
            this.glExplosionWidth = 30f / screenWidth.toFloat()
            this.glExplosionHeight = 10f / screenHeight.toFloat()
            this.glPropsWidth = 20f / screenWidth.toFloat()
            this.glPropsHeight = 40f / screenHeight.toFloat()
            this.surfaceWidth = width
            this.surfaceHeight = height
        }
        // 初始化投影矩阵（正交投影）
        Matrix.orthoM(projectionMatrix, 0, 0f, 1f, 0f, 1f, -1f, 1f)

        // 初始化投影矩阵（反转投影）

        // Matrix.orthoM(projectionMatrix, 0, 1f, 0f, 1f, 0f, 1f, -1f)

    }

    override fun onDrawFrame(gl: GL10?) {
        if (this.screenWidth != glCoordinateData.surfaceWidth || this.screenHeight != glCoordinateData.surfaceHeight
            ) {
            frameBuffer?.glRelease()
            frameBuffer = RenderBuffer(glCoordinateData.surfaceWidth, glCoordinateData.surfaceHeight)
            frameBuffer?.glInit()
        }
        this.screenWidth = glCoordinateData.surfaceWidth
        this.screenHeight = glCoordinateData.surfaceHeight
        glCoordinateData.apply {
            this.glTextWidth = 125f / screenWidth.toFloat()
            this.glTextHeight = 40f / screenHeight.toFloat()
            this.glAsteroidWidth = 20f / screenWidth.toFloat()
            this.glAsteroidHeight = 30f / screenHeight.toFloat()
            this.glPlayerWidth = 30f / screenWidth.toFloat()
            this.glPlayerHeight = 20f / screenHeight.toFloat()
            this.glExplosionWidth = 30f / screenWidth.toFloat()
            this.glExplosionHeight = 10f / screenHeight.toFloat()
            this.glPropsWidth = 20f / screenWidth.toFloat()
            this.glPropsHeight = 40f / screenHeight.toFloat()
        }

        frameBuffer?.bind()
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
//         设置视口尺寸
        GLES20.glViewport(0, 0, frameBuffer?.width ?: 0, frameBuffer?.height ?: 0)
        // 初始化投影矩阵
//         Matrix.orthoM(projectionMatrix, 0, 0f, 1f, 0f, 1f, -1f, 1f) //正交投影，但是这里是反转的，所以要用下面这个
        Matrix.orthoM(projectionMatrix, 0, 1f, 0f, 1f, 0f, 1f, -1f)

        // 绑定着色器程序
        GLES20.glUseProgram(program)
        // 更新游戏状态
        updateGameState()
        // 绘制背景
        drawBackground()
        // 绘制玩家角色
        drawPlayer()
        // 绘制小行星
        drawAsteroids()
        // 绘制道具
        drawProps()
        // 绘制爆炸效果
        drawExplosions()
        // 绘制UI文字
        drawTexts()
        // 全屏效果
        drawEdgeEffect()

        frameBuffer?.unbind()
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    // 修改模型矩阵生成方法
    private fun getModelMatrix(x: Float, y: Float, iconX: Float, iconY: Float): FloatArray {
        val matrix = FloatArray(16).apply {
            Matrix.setIdentityM(this, 0)
            // 先缩放再平移
            Matrix.scaleM(this, 0, iconX, iconY, 1f)
            Matrix.translateM(this, 0, x / iconX - 0.5f, y / iconY - 0.5f, 0f)
            Matrix.multiplyMM(this, 0, projectionMatrix, 0, this, 0)
        }
        return matrix
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
        GlUtil.checkGlError("uRedIntensity")
        edgeUseRed = GLES20.glGetUniformLocation(edgeProgram, "uType")
        GlUtil.checkGlError("colorType")
        edgePositionHandle = GLES20.glGetAttribLocation(edgeProgram, "aPosition")
        GlUtil.checkGlError("aPosition")
        maPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        GlUtil.checkGlError("aPosition")
        maTexCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        GlUtil.checkGlError("aTexCoord")
        muMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        GlUtil.checkGlError("uMVPMatrix")
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
        asteroidTextureId = loadTexture(R.drawable.ic_star)
        explosionTextureId = loadTexture(R.drawable.icon_remove_one)
        propsTextureId = loadTexture(R.drawable.icon_props)
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
        addText("Score: $gameResult", 0.25f, 0.25f)
    }

    /**
     * 添加文字到渲染队列
     * @param text 要显示的文本内容
     * @param x 水平位置（标准化坐标，0-1）
     * @param y 垂直位置（标准化坐标，0-1）
     */
    fun addText(text: String, x: Float, y: Float) {
        val bitmap = Bitmap.createBitmap(
            125,
            40,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        Paint().apply {
            isAntiAlias = true
            textSize = 20f
            this.style = Paint.Style.STROKE
            this.strokeWidth = 2f
            this.setColor(Color.YELLOW)
            canvas.drawText(text, 20f, 30f, this)

            this.color = Color.WHITE
            style = Paint.Style.FILL
            canvas.drawText(text, 20f, 30f, this)
        }

        gameTextDrawable = GameText(x, y, loadTexture(flipVertically(bitmap)))
        bitmap.recycle()
    }

    fun flipVertically(src: Bitmap): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.preScale(-1.0f, 1.0f)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }


    private fun loadTexture(bitmap: Bitmap): Int {

        // 创建纹理ID
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val textureID = textures[0]
//        // 绑定纹理
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureID)
//        // 设置纹理参数
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        // 将Bitmap数据上传到OpenGL纹理
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return textures[0]
    }

    private fun updateGameState() {
        updateInvincibility()
        updateGameText()
        spawnAsteroids()
        updatePositions()
        spawnProps()
        updatePropsPositions()
        checkCollisions()
        updateExplosions()
    }

    fun updateInvincibility() {
        if (isChangePic) {
            if (invincibility) {
                playerTextureId = loadTexture(R.drawable.ic_plant_invincibility)
            } else {
                playerTextureId = loadTexture(R.drawable.ic_plant)
            }
            isChangePic = false
        }
    }

    /**
     * 道具生成算法，一次只能有一个
     */
    private fun spawnProps() {
        // 10%的概率生成道具
        if (props.size < 1 && Math.random() < 0.1) {
            val xPositions = Random.nextFloat()
            val speed = Random.nextFloat()
            val finalSpeed = if (speed < 0.05f) speed / 10 else speed / 50
            props.add(
                AsteroidProps(
                    x = if (xPositions < 0.2f || xPositions > 0.8f) (0.2f + xPositions / 2) else xPositions,
                    y = 1.2f,
                    speed = finalSpeed,
                    type = if (xPositions > 0.5f) 1 else 0
                )
            )
        }
    }

    /**
     * 更新陨石坐标
     */
    private fun updatePropsPositions() {
        props.forEach {
            // 道具不受加速影响
            it.y -= it.speed
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            props.removeIf { it.y < -0.2f }
        }
    }

    /**
     * 陨石生成算法
     */
    private fun spawnAsteroids() {
        if (asteroids.size < 12 && Math.random() < 0.2) {
            val xPositions = Random.nextFloat()
            val speed = Random.nextFloat()
            val finalSpeed = if (speed < 0.05f) speed / 10 else speed / 50
            asteroids.add(
                Asteroid(
                    x = if (xPositions < 0.2f || xPositions > 0.8f) (0.2f + xPositions / 2) else xPositions,
                    y = 1.2f,
                    speed = finalSpeed
                )
            )
        }
    }

    /**
     * 更新陨石坐标
     */
    private fun updatePositions() {
        if (freezeAsteroid || freezeAsteroid) {
            return
        }
        asteroids.forEach {
            if (accelerateAsteroid || accelerateAsteroid) {
                it.y -= (it.speed * 2)
            } else {
                it.y -= it.speed
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            asteroids.removeIf { it.y < -0.2f }
        }
    }

    /**
     * 遍历检查是否碰撞
     */
    private fun checkCollisions() {
        if (SystemClock.elapsedRealtime() - lastPropsTime > 3000) {
            freezeAsteroid = false
            accelerateAsteroid = false
        }
        asteroids.forEach { asteroid ->
            if (checkCollision(
                    asteroid.x, asteroid.y,
                    glCoordinateData.glAsteroidWidth,
                    glCoordinateData.glAsteroidHeight,
                    playerX, playerY
                )
            ) {
                explosions.add(Explosion(asteroid.x, asteroid.y, System.currentTimeMillis()))
                gameResult++
                collisionRedIntensity = 1f
                asteroids.remove(asteroid)
                collisionProps = false
            }
        }
        props.forEach { prop ->
            if (checkCollision(
                    prop.x, prop.y, glCoordinateData.glPropsWidth,
                    glCoordinateData.glPropsHeight, playerX, playerY
                )
            ) {
                lastPropsTime = SystemClock.elapsedRealtime()
                if (prop.type == 1) {
                    freezeAsteroid = true
                } else {
                    accelerateAsteroid = true
                }
                props.remove(prop)
                collisionProps = true
            }
        }
    }

    /**
     * 碰撞检测算法
     */
    private fun checkCollision(
        ax: Float,
        ay: Float,
        explosionsX: Float,
        explosionsY: Float,
        px: Float,
        py: Float
    ): Boolean {
        // 无敌不检测碰撞
        if (invincibility) {
            return false
        }
        return !(ax > px + (glCoordinateData.glPlayerWidth / 3) ||
                ax + (glCoordinateData.glPlayerWidth / 3) < px ||
                ay + (explosionsY / 2) < py ||
                ay - (explosionsY / 2) > py)
    }

    private fun updateExplosions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            explosions.removeIf { System.currentTimeMillis() - it.startTime > 1000 }
        }
        collisionRedIntensity = (collisionRedIntensity - 0.02f).coerceAtLeast(0f)
    }

    private fun updateGameText() {
        addText(
            "score: ${gameResult}",
            1 - glCoordinateData.glTextHeight,
            1 - glCoordinateData.glTextHeight * 2
        )
    }

    // Drawing methods
    private fun drawBackground() {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, projectionMatrix, 0)
        drawObject(identityMatrix(), backgroundTextureId)
    }

    private fun drawAsteroids() {
        asteroids.forEach {
            drawObject(
                getModelMatrix(
                    it.x,
                    it.y,
                    glCoordinateData.glAsteroidWidth,
                    glCoordinateData.glAsteroidHeight
                ), asteroidTextureId
            )
        }
    }

    private fun drawProps() {
        props.forEach {
            drawObject(
                getModelMatrix(
                    it.x,
                    it.y,
                    glCoordinateData.glPropsWidth,
                    glCoordinateData.glPropsHeight
                ), propsTextureId
            )
        }
    }

    private fun drawPlayer() {
        drawObject(
            getModelMatrix(
                playerX,
                playerY,
                glCoordinateData.glPlayerWidth,
                glCoordinateData.glPlayerHeight
            ), playerTextureId
        )
    }

    private fun drawExplosions() {
        explosions.forEach {
            drawObject(
                getModelMatrix(
                    it.x,
                    it.y,
                    glCoordinateData.glAsteroidWidth,
                    glCoordinateData.glAsteroidHeight
                ), explosionTextureId
            )
        }
    }

    private fun drawTexts() {
        drawObject(
            getModelMatrix(
                gameTextDrawable.x,
                gameTextDrawable.y,
                glCoordinateData.glTextWidth,
                glCoordinateData.glTextHeight
            ), gameTextDrawable.textureId
        )
    }


    private fun drawEdgeEffect() {
        if (collisionRedIntensity > 0) {
            // 启用混合

            GLES20.glUseProgram(edgeProgram)
            GLES20.glUniform1f(edgeRedIntensityHandle, collisionRedIntensity)
            GLES20.glUniform1i(edgeUseRed, if (collisionProps) 0 else 1)
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
}

