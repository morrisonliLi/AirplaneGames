# GameRenderer - OpenGL 2D游戏渲染引擎

## 概述
`GameRenderer`是一个基于OpenGL ES 2.0的2D游戏渲染引擎，专为移动端游戏开发设计。它实现了完整的游戏渲染管线，包含离屏渲染、后期处理特效、物理碰撞检测和游戏对象管理系统，适用于太空射击类游戏的开发。

## 功能特性
### 核心功能
- **OpenGL ES 2.0渲染管线**
- 多对象渲染（玩家/陨石/道具/爆炸效果）
- 离屏渲染与后期处理
- 多状态边缘特效（碰撞红屏/道具特效）
- 动态坐标标准化系统

### 游戏机制
- 增强型碰撞检测（AABB+阈值优化）
- 道具系统（冻结/加速效果）
- 玩家无敌状态
- 得分统计系统
- 自动对象回收机制

### 高级特性
- 多线程安全对象管理（CopyOnWriteArrayList）
- 动态纹理加载系统
- 自适应屏幕分辨率
- GLSL着色器热加载
- 混合模式透明渲染

## 类结构
### 主要成员变量
| 类型 | 名称 | 说明 |
|------|------|------|
| `CopyOnWriteArrayList` | asteroids/props/explosions | 游戏对象池 |
| `FloatArray` | projectionMatrix/modelMatrix | 矩阵系统 |
| `RenderBuffer` | frameBuffer | 离屏渲染缓冲 |
| `CoordinateData` | glCoordinateData | 动态坐标计算器 |

### 核心方法
| 方法 | 说明 |
|------|------|
| `onSurfaceCreated()` | 初始化GL环境 |
| `onSurfaceChanged()` | 处理屏幕变化 |
| `onDrawFrame()` | 主渲染循环 |
| `updateGameState()` | 游戏逻辑更新 |
| `drawEdgeEffect()` | 后期特效处理 |

## 使用说明
### 初始化流程
```kotlin
// 创建GLSurfaceView
val glSurfaceView = GLSurfaceView(context).apply {
    setEGLContextClientVersion(2)
    setRenderer(GameRenderer(context))
    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
}
``` 
### 渲染流程
graph TD
    A[主场景渲染] --> B[绑定离屏缓冲]
    B --> C[绘制背景]
    C --> D[绘制玩家]
    D --> E[绘制陨石]
    E --> F[绘制道具]
    F --> G[绘制爆炸]
    G --> H[绘制UI]
    H --> I[边缘特效处理]
    I --> J[解除离屏绑定]
    J --> K[最终输出]

## 关键算法
```kotlin
// 动态坐标标准化
// 将像素坐标转换为OpenGL标准坐标
fun pixelToGl(x: Float, y: Float): Pair<Float, Float> {
    return (x / screenWidth) to (1 - y / screenHeight)
}

//增强型碰撞检测
fun checkCollision() {
    // 使用对象实际尺寸的1/3作为碰撞盒
    val collisionBox = actualSize * 0.33f
    // 加入Y轴偏移补偿
    val yThreshold = explosionHeight / 2
    // 四叉树空间分割优化
}
```

## 注意事项
- 注意事项
- 资源管理
- 纹理资源在onSurfaceCreated加载
- 离屏缓冲随屏幕尺寸变化重建
- 使用try-with-resource管理Bitmap
- 性能优化
- 对象池最大数量限制（陨石≤12）
- 使用nativeOrder缓冲分配
- 避免在渲染循环中创建对象
- 线程安全
- 游戏对象使用写时复制集合
- OpenGL上下文绑定检查
- 纹理操作在GL线程执行

## 示例
```kotlin
// 游戏参数配置
object AsteroidsGameConstant {
    // 着色器代码
    const val vertexShaderCode = "..."
    const val fragmentShaderCode = "..."
    
    // 对象默认尺寸
    const val PLAYER_WIDTH_DP = 30f
    const val ASTEROID_MIN_SPEED = 0.05f
}
```



