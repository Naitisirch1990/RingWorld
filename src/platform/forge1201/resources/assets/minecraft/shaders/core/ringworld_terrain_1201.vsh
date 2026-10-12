#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;
uniform ivec4 RingWorldLayout;
uniform vec4 RingWorldVertical;
uniform vec4 RingWorldCamera;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec4 normal;

const float TAU = 6.28318530717958647692;

float ringAngle(float theta, float gain) {
    float t=abs(theta), edge=1.0-t/3.14159265359;
    return theta+sign(theta)*.785398163397*tanh(4.0*(gain-1.0)*t/3.14159265359)*edge*edge;
}

vec3 ringPosition(vec3 localFlat) {
    float circumference = float(RingWorldLayout.y);
    float gain = max(1.0, min(16.0, circumference / TAU / 512.0));
    float radius = circumference / TAU / gain;
    float intrinsicAngle = TAU * localFlat.x / circumference;
    // Match Forge1201CurvedProjection and the atlas shader, including local block scale.
    float angle = ringAngle(intrinsicAngle, gain);
    float vertexRadius = radius + RingWorldVertical.x - (RingWorldCamera.y + localFlat.y);
    float cameraRadius = radius + RingWorldVertical.x - RingWorldCamera.y;
    return vec3(
        vertexRadius * sin(angle),
        cameraRadius - vertexRadius * cos(angle),
        localFlat.z * mix(1.0, min(1.0, 2.0/gain), pow(sin(angle*.5), 2.0))
    );
}

vec3 ringNormal(vec3 sourceNormal, float localX) {
    float intrinsicAngle = TAU * localX / float(RingWorldLayout.y);
    float gain = max(1.0, min(16.0, float(RingWorldLayout.y) / TAU / 512.0));
    float angle = ringAngle(intrinsicAngle, gain);
    float c = cos(angle);
    float s = sin(angle);
    return vec3(
        sourceNormal.x * c - sourceNormal.y * s,
        sourceNormal.x * s + sourceNormal.y * c,
        sourceNormal.z
    );
}

void main() {
    vec3 flatPos = Position + ChunkOffset;
    vec3 pos = flatPos;
    vec3 curvedNormal = Normal;
    if (RingWorldLayout.x != 0 && RingWorldLayout.y > 0) {
        pos = ringPosition(flatPos);
        curvedNormal = ringNormal(Normal, flatPos.x);
    }

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    vertexDistance = fog_distance(ModelViewMat, pos, FogShape);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    normal = ProjMat * ModelViewMat * vec4(curvedNormal, 0.0);
}
