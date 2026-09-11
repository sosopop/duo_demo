precision mediump float;

varying vec3 vWorldPos;

uniform sampler2D uTexture;
uniform vec2 uTexelSize;
uniform float uAperture;
uniform float uMaxBlurPixels;
uniform float uCameraDistance;
uniform vec2 uPhotoHalfSize;

// Sample photo texture; outside [0.0, 1.0] is infinite black border of the desktop
vec4 samplePhoto(vec2 uv, float lod) {
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    return texture2D(uTexture, uv, lod);
}

void main() {
    // 1. Camera is fixed at (0, 0, uCameraDistance) directly above desktop (Z = 0).
    // The phone screen is the frosted glass plane at vWorldPos in 3D space.
    // Ray from camera through frosted glass point vWorldPos intersects desktop Z = 0 at:
    // Guard against singularity or ray inversion when uCameraDistance is very close (0.1x factor)
    float denom = max(0.02 * uCameraDistance, uCameraDistance - vWorldPos.z);
    float t = uCameraDistance / denom;
    vec2 tablePos = vWorldPos.xy * t;

    // 2. Map table intersection coordinate to normalized photo UV:
    vec2 photoUV;
    photoUV.x = (tablePos.x + uPhotoHalfSize.x) / (2.0 * uPhotoHalfSize.x);
    photoUV.y = 1.0 - (tablePos.y + uPhotoHalfSize.y) / (2.0 * uPhotoHalfSize.y);

    // 3. Physical gap height between frosted glass (phone screen) and desktop photo:
    float vGap = abs(vWorldPos.z);

    // Non-linear Ease-In blur progression (slow start, accelerating towards the far edge):
    // Near the hinge edge: blur grows very slowly, keeping the near region sharp and clear.
    // Far lifted edge: blur accelerates quadratically (x^2) into deep, rich frosted glass.
    float normGap = clamp(vGap * 0.95, 0.0, 1.0);
    float easeIn = normGap * normGap; // Quadratic ease-in curve
    float blurCoC = clamp(easeIn * uAperture * 1.5, 0.0, 1.0);

    // If touching the table (gap is 0), render completely sharp texture without blur
    if (blurCoC <= 0.003) {
        gl_FragColor = samplePhoto(photoUV, 0.0);
        return;
    }

    // Frosted glass blur radius in UV space
    float radius = blurCoC * uMaxBlurPixels;
    vec2 stepUV = uTexelSize * radius;

    // Continuous trilinear mipmap pre-filtering (LOD bias 0.0 to 3.0)
    // Smoothly pre-filters high-frequency edges so that the disc convolution completely melts details
    // into a velvety, authentic frosted glass blur (zero mosaic, zero glow/bloom artifact)
    float lodBias = clamp(blurCoC * 3.0, 0.0, 3.0);

    // 4. Interleaved Gradient Noise (IGN) for micro-dithered Vogel spiral
    float ign = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float angle = ign * 6.283185307;

    // Incremental rotation matrix for Golden Angle (2.39996323 rad)
    // cos(2.39996323) = -0.73736888, sin(2.39996323) = 0.67549029
    const float C_STEP = -0.73736888;
    const float S_STEP =  0.67549029;

    vec2 curDir = vec2(cos(angle), sin(angle));

    vec4 sum = vec4(0.0);
    float totalWeight = 0.0;

    // 16-tap Vogel Spiral Disc Bokeh Convolution
    // Each tap has equal weighting across the circle of confusion.
    // By eliminating the sharp center tap peak, high-contrast edges and text fully dissolve
    // into substantive, genuine frosted glass optical diffusion.
    for (int i = 0; i < 16; i++) {
        float r = sqrt((float(i) + 0.5) * 0.0625); // 0.0625 = 1 / 16 (uniform area distribution)
        vec2 sampleUV = photoUV + curDir * (r * stepUV);

        sum += samplePhoto(sampleUV, lodBias);
        totalWeight += 1.0;

        // Rotate direction by Golden Angle for next tap
        curDir = vec2(
            curDir.x * C_STEP - curDir.y * S_STEP,
            curDir.y * C_STEP + curDir.x * S_STEP
        );
    }

    gl_FragColor = sum / totalWeight;
}
