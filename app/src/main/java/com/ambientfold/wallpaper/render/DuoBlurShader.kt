package com.ambientfold.wallpaper.render

import org.intellij.lang.annotations.Language

/**
 * Android Shading Language (AGSL) DuoBlur Shader.
 * Implements the iPhone Duo unfolding optical transition:
 * 1. 3D perspective warp: vertical coordinates compress further from hinge axis.
 * 2. Directional 9-tap progressive Gaussian blur: spatially weighted by distance from hinge.
 * 3. Luminance attenuation: darkening as the panel angles away from viewer.
 * 4. Dual-mode execution: supports both cover panel (hinge at x=0.0) and inner panel (crease at x=0.5).
 */
object DuoBlurShader {

    @Language("AGSL")
    const val DUO_FOLD_SHADER_SRC = """
        uniform shader content;
        uniform float2 resolution;
        uniform float foldProgress; // Normalized: 0.0 (flat/closed) to 1.0 (mid-fold peak)
        uniform float isInnerScreen; // 0.0 for cover panel, 1.0 for inner dual panel

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            
            // Distance from hinge axis: cover screen hinge is at x=0.0, inner screen crease is at x=0.5
            float distFromHinge = (isInnerScreen > 0.5) ? abs(uv.x - 0.5) * 2.0 : uv.x;
            
            // Perspective warp: compress vertical coordinates slightly further from the hinge
            float bendFactor = foldProgress * 0.08 * distFromHinge;
            float2 warpedUv = uv;
            warpedUv.y = (uv.y - 0.5) / (1.0 - bendFactor) + 0.5;
            
            // Guard against out-of-bounds UV coordinates during warp
            if (warpedUv.y < 0.0 || warpedUv.y > 1.0) {
                return half4(0.0, 0.0, 0.0, 1.0);
            }

            // Progressive frost radius: stronger near hinge/crease, tapering outward
            float spatialWeight = mix(1.0, 0.35, distFromHinge);
            float blurRadius = foldProgress * 28.0 * spatialWeight;

            // 9-tap directional Gaussian approximation for high-FPS GPU execution
            half4 accumulatedColor = half4(0.0);
            float totalWeight = 0.0;
            
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    float2 offset = float2(float(x), float(y)) * (blurRadius * 0.5);
                    float weight = exp(-0.5 * float(x*x + y*y) / 2.0);
                    accumulatedColor += content.eval(warpedUv * resolution + offset) * weight;
                    totalWeight += weight;
                }
            }
            
            half4 finalColor = accumulatedColor / totalWeight;

            // Luminance attenuation (darkening as panel angles away from viewer)
            float darkening = 1.0 - (foldProgress * 0.65 * (0.5 + 0.5 * distFromHinge));
            finalColor.rgb *= half(darkening);

            return finalColor;
        }
    """
}
