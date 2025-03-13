package com.learning.glgame.model

object AsteroidsGameConstant {
    const val vertexShaderCode = """
    attribute vec4 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    uniform mat4 uMVPMatrix;
    void main() {
        gl_Position = uMVPMatrix * aPosition;
        vTexCoord = aTexCoord;
    }"""

    const val fragmentShaderCode = """
    precision mediump float;
    varying vec2 vTexCoord;
    uniform sampler2D uTexture;
    void main() {
        gl_FragColor = texture2D(uTexture, vTexCoord);
    }"""


    // 边缘效果着色器代码
    const val edgeVertexShaderCode = """
        attribute vec4 aPosition;
        varying vec2 vUV;
        void main() {
            gl_Position = aPosition;
            vUV = (aPosition.xy + 1.0) * 0.5; // 转换到0-1范围
        }
    """

    const val edgeFragmentShaderCode = """
        precision mediump float;
        varying vec2 vUV;
        uniform float uRedIntensity;
        uniform int uType; // 增加边缘颜色
        
        void main() {
            // 计算到边缘的距离（四个方向）
            float edge = min(
                min(vUV.x, 1.0 - vUV.x),
                min(vUV.y, 1.0 - vUV.y)
            );
            
            // 边缘渐变范围（0.05表示5%屏幕边缘）
            float edgeWidth = 0.05;
            float alpha = smoothstep(edgeWidth, 0.0, edge) * uRedIntensity;
            if(uType > 0) {
                gl_FragColor = vec4(1.0, 0.0, 0.0, alpha);
            } else {
                gl_FragColor = vec4(0.0, 0.0, 0.7, alpha);
            } 
        }
    """
}