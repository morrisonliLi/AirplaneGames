package com.learning.glgame.utils

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//
object GlUtil {
    private const val TAG = "GlUtil"
    var ENABLE_LOG: Boolean = true

    fun createProgram(vertexSource: String?, fragmentSource: String?): Int {
        val vertexShader = loadShader(35633, vertexSource)
        if (vertexShader == 0) {
            return 0
        } else {
            val pixelShader = loadShader(35632, fragmentSource)
            if (pixelShader == 0) {
                return 0
            } else {
                var program = GLES20.glCreateProgram()
                checkGlError("glCreateProgram")
                if (program == 0) {
                    Log.e("GlUtil", "Could not create program")
                }

                GLES20.glAttachShader(program, vertexShader)
                checkGlError("glAttachShader")
                GLES20.glAttachShader(program, pixelShader)
                checkGlError("glAttachShader")
                GLES20.glLinkProgram(program)
                val linkStatus = IntArray(1)
                GLES20.glGetProgramiv(program, 35714, linkStatus, 0)
                if (linkStatus[0] != 1) {
                    Log.e("GlUtil", "Could not link program: ")
                    Log.e("GlUtil", GLES20.glGetProgramInfoLog(program))
                    GLES20.glDeleteProgram(program)
                    program = 0
                }

                Log.i("GlUtil", "linkStatus:" + linkStatus[0])
                return program
            }
        }
    }

    fun loadShader(shaderType: Int, source: String?): Int {
        var shader = GLES20.glCreateShader(shaderType)
        checkGlError("glCreateShader type=$shaderType")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, 35713, compiled, 0)
        if (compiled[0] == 0) {
            Log.e("GlUtil", "Could not compile shader $shaderType:")
            Log.e("GlUtil", " " + GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            shader = 0
        }

        return shader
    }

    fun checkGlError(op: String) {
        if (ENABLE_LOG) {
            val error = GLES20.glGetError()
            if (error != 0) {
                val msg = op + ": glError 0x" + Integer.toHexString(error)
                Log.e("GlUtil", "", RuntimeException(msg))
            }
        }
    }

    fun createFrameBuffer(): Int {
        val frameBuffers = IntArray(1)
        GLES20.glGenFramebuffers(frameBuffers.size, frameBuffers, 0)
        checkGlError("glGenFramebuffers")
        return frameBuffers[0]
    }

    fun releaseTexture(textures: IntArray) {
        GLES20.glDeleteTextures(textures.size, textures, 0)
        checkGlError("glDeleteTextures")
    }

    fun releaseFrameBuffer(frameBuffers: IntArray) {
        GLES20.glDeleteFramebuffers(frameBuffers.size, frameBuffers, 0)
        checkGlError("glDeleteFramebuffers")
    }

    @JvmOverloads
    fun createTexture(
        textureTarget: Int,
        bitmap: Bitmap? = null as Bitmap?,
        minFilter: Int = 9729,
        magFilter: Int = 9729,
        wrapS: Int = 33071,
        wrapT: Int = 33071
    ): Int {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)
        checkGlError("glGenTextures")
        GLES20.glBindTexture(textureTarget, textureHandle[0])
        checkGlError("glBindTexture " + textureHandle[0])
        GLES20.glTexParameterf(textureTarget, 10241, minFilter.toFloat())
        GLES20.glTexParameterf(textureTarget, 10240, magFilter.toFloat())
        GLES20.glTexParameteri(textureTarget, 10242, wrapS)
        GLES20.glTexParameteri(textureTarget, 10243, wrapT)
        if (bitmap != null) {
            GLUtils.texImage2D(3553, 0, bitmap, 0)
        }

        checkGlError("glTexParameter")
        return textureHandle[0]
    }

    fun loadTexture(tex: Int, bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            GLES20.glBindTexture(3553, tex)
            GLES20.glTexParameterf(3553, 10241, 9729.0f)
            GLES20.glTexParameterf(3553, 10240, 9729.0f)
            GLES20.glTexParameteri(3553, 10242, 33071)
            GLES20.glTexParameteri(3553, 10243, 33071)
            GLUtils.texImage2D(3553, 0, bitmap, 0)
        }
    }
}