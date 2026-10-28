#version 150
// MPL-2.0. Resource-only trial for the hash-pinned DETALLE-2B JAR.
in vec4 vertexColor;
in vec3 atlasPosition;
uniform vec4 ColorModulator;
uniform vec4 RingCamera;
uniform vec4 RingLayout;
uniform vec4 DetailInfo;
uniform sampler2D DetailCoverage;
out vec4 fragColor;

void main() {
    // Evaluate coverage per fragment: interpolated vertex opacities on large
    // Atlas quads can otherwise leak into the protected near region.
    float circumference = max(RingLayout.x, 1.0);
    float dx = mod(atlasPosition.x - RingCamera.x + circumference * .5,
                   circumference) - circumference * .5;
    float flatDistance = length(vec2(dx, atlasPosition.z - RingCamera.z));
    // A high camera also runs out of real vertical sections. Do not leave an
    // empty column below it merely because the horizontal distance is small.
    float coverageDistance = max(flatDistance, abs(atlasPosition.y - RingCamera.y));
    float radius = max(RingCamera.w, 16.0);
    float handoff = smoothstep(max(0.0, radius - 16.0), radius, coverageDistance);
    if (handoff <= 0.0) discard;

    // Preserve the existing detail/coarse complement and its existing dither.
    float blend = 1.0 - smoothstep(256.0, 320.0, flatDistance);
    float pattern = fract(dot(floor(gl_FragCoord.xy), vec2(.754877666, .569840296)));
    if (DetailInfo.w > .5) {
        if (blend <= pattern) discard;
    } else if (DetailInfo.x > 0.0 && DetailInfo.y > 0.0) {
        int x = int(mod(floor(atlasPosition.x / 16.0), DetailInfo.x));
        int z = int(floor(atlasPosition.z / 16.0) - DetailInfo.z);
        if (z >= 0 && z < int(DetailInfo.y) &&
            texelFetch(DetailCoverage, ivec2(x, z), 0).r > .5 && pattern < blend) discard;
    }
    fragColor = vertexColor * ColorModulator;
    fragColor.a *= handoff;
    if (fragColor.a <= 0.0) discard;
    // Forward-depth OpenGL 1.20.1: a nearly invisible proxy must not write
    // fully opaque surface depth and cut holes in the real trees drawn later.
    // Keep depth writes and exact far-proxy depth once fully visible.
    gl_FragDepth = mix(gl_DepthRange.far, gl_FragCoord.z, clamp(fragColor.a, 0.0, 1.0));
}
