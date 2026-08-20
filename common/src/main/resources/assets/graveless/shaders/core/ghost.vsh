#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out float fresnel;
out vec3 localPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vec3 viewPos = (ModelViewMat * vec4(Position, 1.0)).xyz;
    vertexDistance = fog_distance(viewPos, FogShape);
    vertexColor = Color;
    texCoord0 = UV0;
    localPos = Position;

    vec3 viewNormal = normalize((ModelViewMat * vec4(Normal, 0.0)).xyz);
    vec3 viewDir = normalize(-viewPos);
    fresnel = 1.0 - clamp(abs(dot(viewNormal, viewDir)), 0.0, 1.0);
}
