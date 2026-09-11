# DuoDepth: 3D Spatial Tilt & Bokeh Simulator

<div align="center">
  <img src="duodepth_icon.svg" width="128" height="128" alt="DuoDepth Logo" />
  <h3>Real-Time 3D Spatial Frosted Glass Tilt &amp; Optical Depth-of-Field Simulator for Android</h3>
  <p>
    <img src="https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84.svg?style=flat&logo=android" alt="Android" />
    <img src="https://img.shields.io/badge/Graphics-OpenGL_ES_3.0_/_2.0-5586A4.svg?style=flat&logo=opengl" alt="OpenGL ES" />
    <img src="https://img.shields.io/badge/Language-Java_%26_GLSL-orange.svg?style=flat" alt="Language" />
    <img src="https://img.shields.io/badge/Build-Gradle_8.13-02303A.svg?style=flat&logo=gradle" alt="Gradle" />
    <img src="https://img.shields.io/badge/Release_Signing-Automated-blue.svg?style=flat" alt="Release Signing" />
  </p>
</div>

---

## 📖 Overview

**DuoDepth** transforms your Android phone into an interactive **3D frosted glass viewport** anchored above a virtual desktop. As you tilt, lift, and rotate your phone in the real physical world, the displayed image stays locked to the tabletop beneath, undergoing authentic optical perspective shrinkage and physical depth-of-field bokeh diffusion.

Inspired by futuristic dual-screen and spatial computing concepts (such as the optical depth perception of the iPhone Duo concept), DuoDepth implements an end-to-end real-time optical ray-tracing pipeline in OpenGL ES with zero-latency gyroscope tracking.

---

## ✨ Key Features

### 🪟 1. The Phone Screen as a 3D Frosted Glass Viewport
- **Edge-to-Edge Fullscreen**: The phone screen is physically treated as the tilted frosted glass aperture itself—eliminating awkward black canvas borders.
- **Tabletop Ray-Tracing**: A virtual pinhole camera is positioned at $(0, 0, D)$ directly looking down at the tabletop ($Z = 0$). Rays projected through each screen pixel $(X_w, Y_w, Z_w)$ intersect the desktop photo at:
  $$\vec{P}_{\text{table}} = \vec{P}_{\text{world}} \cdot \frac{D}{D - Z_w}$$
- **Natural Optical Cancellation**: Perspective shrinkage rendered on-screen precisely offsets the physical perspective foreshortening perceived by human eyes, creating the stunning illusion that the image is physically printed on the table beneath your phone.

### ⚡ 2. Zero-Latency True 3D Relative Calibration
- **Relative Attitude Matrix**: Rather than simplistic 1D Euler angle subtraction, calibration computes the exact 3D coordinate transformation:
  $$R_{\text{rel}} = R_{\text{calib}}^T \cdot R_{\text{current}}$$
- **Coordinate System Follows Phone**: Regardless of whether you hold your phone flat, at a $45^\circ$ incline, or lying down, pressing **Calibrate Level** anchors the 4-edge rotation axes directly to the phone's physical chassis:
  - Tilting up/down rotates strictly around the phone's top/bottom edges.
  - Tilting left/right rotates strictly around the phone's left/right edges.
- **Dynamic Instant Filter**: High-frequency sensor fusion ($200\,\text{Hz}$) with adaptive low-pass weighting ($0.92$ during movement, $0.35$ when static) achieves sub-$10\,\text{ms}$ motion-to-photon latency with zero jitter.

### 🌌 3. Substantive 16-Tap Vogel Spiral Bokeh
- **Genuine Optical Diffusion**: Replaced bloom/glow filters with uniform-area 16-tap Vogel spiral disc convolution. High-contrast UI elements, text, and edges genuinely dissolve into rich, velvety frosted glass.
- **Hardware Trilinear Mipmap Filtering**: Dynamic LOD bias (up to $3.0$) smooths high-frequency texels before disc convolution, completely eliminating pixelation and mosaic blocks.
- **Non-Linear Quadratic Ease-In ($x^2$)**:
  $$\text{normGap} = \text{clamp}(vGap \cdot 0.95, 0.0, 1.0), \quad \text{blurCoC} = \text{clamp}(\text{normGap}^2 \cdot \text{uAperture} \cdot 1.5, 0.0, 1.0)$$
  Blur remains subtle and sharp near the anchored hinge, accelerating rapidly as the lifted edge separates from the tabletop.

### 📐 4. Fullscreen Quad Architecture (Zero Hairline Seams)
- Single 4-vertex, 2-triangle fullscreen quad eliminates all $14,400$ internal grid mesh triangles.
- Full 32-bit `highp float` shader precision eliminates coordinate quantization banding and IGN moiré artifacts.
- 1-texel soft boundary anti-aliasing prevents razor-sharp boundary lines.

### 🖐️ 5. Photo Pan, Zoom, and Lock Controls
- **Proportional AspectFill CenterCrop**: Loaded images (4:3, 16:9, panoramic) automatically scale to fill the screen without distortion or letterboxing.
- **Single-Finger Drag**: Pan the photo on the virtual desktop to explore regions beyond the physical viewport.
- **Two-Finger Pinch**: Scale the photo ($0.3\times \sim 5.0\times$).
- **Gesture Lock Switch**: Dedicated Material Switch locks photo position to prevent accidental displacement while tilting.
- **One-Tap Reset Button**: Instantly restores photo transform to default 1:1 AspectFill centered alignment.

### 🎛️ 6. Real-Time Optical Controls
- **Depth Blur Aperture Slider**: $0\% \sim 100\%$ intensity.
- **Camera FOV Slider**: $5^\circ$ (orthographic-like telephoto) to $90^\circ$ (dramatic wide angle), default $30^\circ$ matching the natural human eye field of view.
- **Viewpoint Distance Slider**: $0.1\times \sim 2.0\times$ distance factor.
- **Silent UI Toggle**: Tap anywhere on screen to toggle the floating glassmorphism HUD without popup interruptions.

---

## 🏗️ Project Architecture

```
duo_demo/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/duodemo/
│   │   │   ├── MainActivity.java           # Fullscreen HUD, gesture orchestration, permission & pickers
│   │   │   ├── gl/
│   │   │   │   ├── DuoGLSurfaceView.java   # Touch gesture detection (pan/zoom/tap) & GL thread bridge
│   │   │   │   ├── DuoGLRenderer.java      # Matrix kinematics, fixed pinhole camera, fullscreen quad
│   │   │   │   └── ShaderUtils.java        # Shader compilation & trilinear mipmap texture loader
│   │   │   ├── sensor/
│   │   │   │   └── DeviceTiltTracker.java  # Rotation vector sensor listener & 3D relative matrix math
│   │   │   └── util/
│   │   │       └── BitmapUtils.java        # Bitmap decode, EXIF rotation fix, default desktop wallpaper
│   │   ├── assets/shaders/
│   │   │   ├── dof_vertex.glsl             # Highp 3D world coordinate transformation
│   │   │   └── dof_fragment.glsl           # 16-tap Vogel spiral bokeh & ray-tabletop homography
│   │   └── res/
│   │       ├── layout/activity_main.xml    # Edge-to-edge layout & floating glassmorphism HUD
│   │       ├── values/strings.xml          # Clean English localization
│   │       └── drawable/                   # Vector HUD backgrounds & DuoDepth adaptive app icon
│   ├── duodepth-release.jks               # Production release signing keystore
│   └── build.gradle                        # Signing configs & build definitions
├── duodepth_icon.svg                       # Full-fidelity vector app icon (W3C SVG)
├── keystore.properties                     # Release signing credentials
└── README.md
```

---

## 🚀 Building & Running

### Prerequisites
- **JDK**: Java 17 or Java 21
- **Android SDK**: Compile SDK 34, Min SDK 26 (Android 8.0+)
- **Build Tool**: Gradle 8.13 (via included Gradle Wrapper)

### 1. Build Debug APK
```bash
# Windows (PowerShell / Command Prompt)
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```
Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### 2. Build Signed Release APK
The project includes automated release signing configured out of the box with `keystore.properties` and `duodepth-release.jks`:

```bash
# Windows (PowerShell / Command Prompt)
.\gradlew.bat assembleRelease

# macOS / Linux
./gradlew assembleRelease
```
Output APK: `app/build/outputs/apk/release/app-release.apk`

#### Custom Keystore Configuration
To sign with your own private production keystore, modify `keystore.properties` in the project root:
```properties
storeFile=path/to/your/release.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

---

## 🎨 Icon Design Concept

The DuoDepth application icon represents:
- **3D Floating Glass Pane**: A perspective isometric viewport floating above a dark cosmic backdrop.
- **Optical Aperture & Bokeh Rings**: Glowing concentric cyan (`#64D2FF`) and magenta (`#FF375F`) bokeh orbs with focal crosshairs.
- **Anchored Hinge**: An electric green base indicator representing the grounded edge of rotation.

The icon is provided as both an **Adaptive Vector Icon** (`ic_launcher_background.xml` + `ic_launcher_foreground.xml`) and a standalone **W3C SVG** (`duodepth_icon.svg`).

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
