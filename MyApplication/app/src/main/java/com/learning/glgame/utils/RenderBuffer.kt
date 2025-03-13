package com.learning.glgame.utils

import android.opengl.GLES20
import java.nio.Buffer

class RenderBuffer @JvmOverloads constructor(
    val width: Int,
    val height: Int,
    activeTexUnit: Int = 33984
) {
    var textureId: Int = 0
        private set
    var activeTexUnit: Int = 0
    private var frameBufferId = 0

    init {
        this.activeTexUnit = activeTexUnit
    }

    fun bind() {
        GLES20.glViewport(0, 0, this.width, this.height)
        GLES20.glBindFramebuffer(36160, this.frameBufferId)
        GlUtil.checkGlError("glBindFramebuffer")
    }

    fun unbind() {
        GLES20.glBindFramebuffer(36160, 0)
        GlUtil.checkGlError("glBindFramebuffer")
    }

    fun glInit() {
        GLES20.glActiveTexture(this.activeTexUnit)
        this.textureId = GlUtil.createTexture(3553)
        GLES20.glTexImage2D(3553, 0, 6408, this.width, this.height, 0, 6408, 5121, null as Buffer?)
        GlUtil.checkGlError("glTexImage2D")
        GLES20.glTexParameterf(3553, 10241, 9729.0f)
        GLES20.glTexParameterf(3553, 10240, 9729.0f)
        GLES20.glTexParameteri(3553, 10242, 33071)
        GLES20.glTexParameteri(3553, 10243, 33071)
        GlUtil.checkGlError("glTexParameteri")
        GlUtil.checkGlError("glGenFramebuffers")
        this.frameBufferId = GlUtil.createFrameBuffer()
        GLES20.glBindFramebuffer(36160, this.frameBufferId)
        GlUtil.checkGlError("glBindFramebuffer")
        GLES20.glFramebufferTexture2D(36160, 36064, 3553, this.textureId, 0)
        GlUtil.checkGlError("glFramebufferTexture2D")
        this.unbind()
    }

    fun glRelease() {
        this.unbind()
        GlUtil.releaseFrameBuffer(intArrayOf(this.frameBufferId))
        GlUtil.releaseTexture(intArrayOf(this.textureId))
        this.textureId = 0
        this.frameBufferId = 0
    }

    companion object {
        private const val TAG = "RenderBuffer"
    }
}