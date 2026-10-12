#version 150
in vec3 Position;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 vertexColor;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // The full 61 km ring exceeds Minecraft's chunk-distance far plane.
    // Clamp only its depth: preserve perspective and physical alignment.
    if (gl_Position.w > 0.0) gl_Position.z = min(gl_Position.z, gl_Position.w * 0.9999);
    vertexColor = Color;
}
