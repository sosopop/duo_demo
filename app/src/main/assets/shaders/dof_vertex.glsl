uniform mat4 uMVPMatrix;
uniform mat4 uModelMatrix;

attribute vec4 aPosition;
attribute vec2 aTexCoord;

varying vec2 vTexCoord;
varying float vGap;

void main() {
    gl_Position = uMVPMatrix * aPosition;
    vec4 worldPos = uModelMatrix * aPosition;
    // Frosted glass tilts away into Z < 0 (shrinking in perspective)
    // Physical gap between frosted glass and image plane (Z = 0) is -worldPos.z
    vGap = max(0.0, -worldPos.z);
    vTexCoord = aTexCoord;
}
