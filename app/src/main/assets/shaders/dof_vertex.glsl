precision highp float;

attribute vec2 aPosition;

uniform vec2 uScreenHalfSize;
uniform mat4 uModelMatrix;

varying vec3 vWorldPos;

void main() {
    // 1. Render edge-to-edge fullscreen on the physical phone screen:
    // aPosition.x in [-halfW, halfW] maps directly to NDC [-1.0, 1.0]
    // aPosition.y in [-halfH, halfH] maps directly to NDC [-1.0, 1.0]
    gl_Position = vec4(aPosition.x / uScreenHalfSize.x, aPosition.y / uScreenHalfSize.y, 0.0, 1.0);

    // 2. Compute the 3D position of this frosted glass point in world space when tilted
    vec4 worldPos = uModelMatrix * vec4(aPosition, 0.0, 1.0);
    vWorldPos = worldPos.xyz;
}
