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

vec3 ringPosition(vec3 localFlat) {
    float circumference = float(RingWorldLayout.y);
    float radius = circumference / TAU;
    float angle = TAU * localFlat.x / circumference;
    float vertexRadius = radius + RingWorldVertical.x - (RingWorldCamera.y + localFlat.y);
    float cameraRadius = radius + RingWorldVertical.x - RingWorldCamera.y;
    return vec3(
        vertexRadius * sin(angle),
        cameraRadius - vertexRadius * cos(angle),
        localFlat.z
    );
}

vec3 ringNormal(vec3 sourceNormal, float localX) {
    float angle = TAU * localX / float(RingWorldLayout.y);
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
