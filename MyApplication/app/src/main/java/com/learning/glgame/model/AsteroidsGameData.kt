package com.learning.glgame.model

// Data classes
data class Asteroid(var x: Float, var y: Float, val speed: Float)
data class AsteroidProps(var x: Float, var y: Float, val speed: Float, val type: Int)
data class Explosion(val x: Float, val y: Float, val startTime: Long)
data class GameText(val x: Float, val y: Float, val textureId: Int)
data class CoordinateData(
    var glTextWidth: Float = 0f,
    var glTextHeight: Float = 0f,
    var glAsteroidWidth: Float = 0f,
    var glAsteroidHeight: Float = 0f,
    var glPlayerWidth: Float = 0f,
    var glPlayerHeight: Float = 0f,
    var glExplosionWidth: Float = 0f,
    var glExplosionHeight: Float = 0f,
    var glPropsWidth: Float = 0f,
    var glPropsHeight: Float = 0f,
    var surfaceWidth: Int = 0,
    var surfaceHeight: Int = 0
)