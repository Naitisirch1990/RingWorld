#version 150
in vec4 vertexColor;
in float handoff;
uniform vec4 ColorModulator;
out vec4 fragColor;
void main() {
    if(handoff<.02) discard;
    fragColor=vertexColor*ColorModulator;
    fragColor.a*=handoff;
}
