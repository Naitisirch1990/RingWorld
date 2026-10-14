#version 150
in vec3 Position;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 RingCamera;
uniform vec4 RingLayout;
out vec4 vertexColor;
out float handoff;
const float TAU=6.28318530718;
float ringAngle(float theta, float gain) {
    float t=abs(theta), edge=1.0-t/3.14159265359;
    return theta+sign(theta)*.785398163397*tanh(4.0*(gain-1.0)*t/3.14159265359)*edge*edge;
}

void main() {
    float c=RingLayout.x;
    float dx=mod(Position.x-RingCamera.x+c*.5,c)-c*.5;
    float theta=TAU*dx/c;
    float gain=max(1.0,min(4.0,c/TAU/1024.0));
    float angle=ringAngle(theta,gain);
    float radius=c/TAU/gain;
    float vr=radius+RingLayout.y-Position.y;
    float cr=radius+RingLayout.y-RingCamera.y;

    // Preserve the requested wider far side without the old hourglass pinch.
    // The old t^4 ramp stayed almost flat through the side of the cylinder and
    // then expanded very quickly near the antipode. Perspective narrowed the
    // band first and the late expansion widened it again, producing the visible
    // waist.  sin(|angle|/2) follows the cylinder's chord distance instead, so
    // transverse width grows smoothly from 1x near the player to exactly 2x on
    // the opposite side while remaining monotonic through the whole arc.
    float chord=sin(clamp(abs(angle),0.0,3.14159265359)*0.5);
    float widthScale=1.0+chord;

    vec3 p=vec3(vr*sin(angle),cr-vr*cos(angle),(Position.z-RingCamera.z)*widthScale);
    gl_Position=ProjMat*ModelViewMat*vec4(p,1.0);
    if(gl_Position.w>0.0) gl_Position.z=min(gl_Position.z,gl_Position.w*.9999);
    handoff=smoothstep(RingCamera.w*.55,RingCamera.w*.85,length(vec2(dx,Position.z-RingCamera.z)));
    vertexColor=Color;
}
