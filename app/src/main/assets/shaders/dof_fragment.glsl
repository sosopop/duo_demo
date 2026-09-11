precision mediump float;

varying vec2 vTexCoord;
varying float vGap;

uniform sampler2D uTexture;
uniform vec2 uTexelSize;
uniform float uAperture;
uniform float uMaxBlurPixels;

// Sample photo texture; outside [0.0, 1.0] is infinite black border
vec4 samplePhoto(vec2 uv, float lod) {
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    return texture2D(uTexture, uv, lod);
}

void main() {
    // Physical frosted glass blur:
    // When gap = 0 (contact edge): blurCoC = 0.0 (completely sharp, zero blur)
    // When gap > 0 (lifted frosted glass): blur increases directly with height above the picture
    float blurCoC = clamp(pow(vGap * uAperture * 2.8, 1.15), 0.0, 1.0);

    // If touching the picture (gap is 0), render sharpest texture directly without blur
    if (blurCoC <= 0.003) {
        gl_FragColor = samplePhoto(vTexCoord, 0.0);
        return;
    }

    // Frosted glass blur radius in pixels
    float radius = blurCoC * uMaxBlurPixels;
    vec2 stepUV = uTexelSize * radius;
    // Mipmap LOD bias for velvety, creamy frosted glass optical diffusion
    float lodBias = blurCoC * 4.5;

    // 25-tap Multi-Ring Concentric Bokeh Kernel
    vec4 sum = samplePhoto(vTexCoord, lodBias) * 2.0;
    float totalWeight = 2.0;

    // Ring 1 (Inner core, radius 0.25)
    sum += samplePhoto(vTexCoord + vec2( 0.0,   0.25) * stepUV, lodBias) * 1.5;
    sum += samplePhoto(vTexCoord + vec2( 0.0,  -0.25) * stepUV, lodBias) * 1.5;
    sum += samplePhoto(vTexCoord + vec2( 0.25,  0.0 ) * stepUV, lodBias) * 1.5;
    sum += samplePhoto(vTexCoord + vec2(-0.25,  0.0 ) * stepUV, lodBias) * 1.5;
    totalWeight += 4.0 * 1.5;

    // Ring 2 (Middle ring, radius 0.55)
    sum += samplePhoto(vTexCoord + vec2( 0.389,  0.389) * stepUV, lodBias) * 1.2;
    sum += samplePhoto(vTexCoord + vec2(-0.389,  0.389) * stepUV, lodBias) * 1.2;
    sum += samplePhoto(vTexCoord + vec2( 0.389, -0.389) * stepUV, lodBias) * 1.2;
    sum += samplePhoto(vTexCoord + vec2(-0.389, -0.389) * stepUV, lodBias) * 1.2;
    sum += samplePhoto(vTexCoord + vec2( 0.0,    0.55 ) * stepUV, lodBias) * 1.2;
    sum += samplePhoto(vTexCoord + vec2( 0.0,   -0.55 ) * stepUV, lodBias) * 1.2;
    totalWeight += 6.0 * 1.2;

    // Ring 3 (Outer ring, radius 0.85)
    sum += samplePhoto(vTexCoord + vec2( 0.736,  0.425) * stepUV, lodBias) * 0.9;
    sum += samplePhoto(vTexCoord + vec2(-0.736,  0.425) * stepUV, lodBias) * 0.9;
    sum += samplePhoto(vTexCoord + vec2( 0.736, -0.425) * stepUV, lodBias) * 0.9;
    sum += samplePhoto(vTexCoord + vec2(-0.736, -0.425) * stepUV, lodBias) * 0.9;
    sum += samplePhoto(vTexCoord + vec2( 0.425,  0.736) * stepUV, lodBias) * 0.9;
    sum += samplePhoto(vTexCoord + vec2(-0.425,  0.736) * stepUV, lodBias) * 0.9;
    sum += samplePhoto(vTexCoord + vec2( 0.425, -0.736) * stepUV, lodBias) * 0.9;
    sum += samplePhoto(vTexCoord + vec2(-0.425, -0.736) * stepUV, lodBias) * 0.9;
    totalWeight += 8.0 * 0.9;

    // Ring 4 (Perimeter spread, radius 1.15 for smooth boundary diffusion)
    sum += samplePhoto(vTexCoord + vec2( 0.0,    1.15) * stepUV, lodBias) * 0.6;
    sum += samplePhoto(vTexCoord + vec2( 0.0,   -1.15) * stepUV, lodBias) * 0.6;
    sum += samplePhoto(vTexCoord + vec2( 1.15,   0.0 ) * stepUV, lodBias) * 0.6;
    sum += samplePhoto(vTexCoord + vec2(-1.15,   0.0 ) * stepUV, lodBias) * 0.6;
    sum += samplePhoto(vTexCoord + vec2( 0.813,  0.813) * stepUV, lodBias) * 0.6;
    sum += samplePhoto(vTexCoord + vec2(-0.813,  0.813) * stepUV, lodBias) * 0.6;
    totalWeight += 6.0 * 0.6;

    gl_FragColor = sum / totalWeight;
}
