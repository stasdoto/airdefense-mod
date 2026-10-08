#version 330
#extension GL_ARB_separate_shader_objects : require

// 1.26: the thermal channel of a gun sight - white hot. Living things and running engines (drawn white-hot by the
// renderers while it is on) and fire glow; the ground is a dull grey by how bright it is; the sky and water are cold.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

float lum(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 px = 1.0 / InSize;
    // A touch of blur: a thermal picture is never quite sharp.
    vec3 c = texture(InSampler, texCoord).rgb * 0.5
           + texture(InSampler, texCoord + vec2(px.x, 0.0)).rgb * 0.125
           + texture(InSampler, texCoord - vec2(px.x, 0.0)).rgb * 0.125
           + texture(InSampler, texCoord + vec2(0.0, px.y)).rgb * 0.125
           + texture(InSampler, texCoord - vec2(0.0, px.y)).rgb * 0.125;
    float l = lum(c);
    float hi = max(c.r, max(c.g, c.b));
    float lo = min(c.r, min(c.g, c.b));
    float sat = hi - lo;
    // Cold: the blue of the sky and of water.
    float cold = clamp((c.b - max(c.r, c.g)) * 4.0, 0.0, 1.0);
    // Hot: near-white with little colour (what the renderers made white-hot), and fire.
    float hot = smoothstep(0.70, 0.90, l) * (1.0 - smoothstep(0.15, 0.32, sat));
    float fire = smoothstep(0.55, 0.9, c.r) * smoothstep(0.25, 0.55, c.r - c.b);
    float t = 0.10 + l * 0.42;
    t = mix(t, 0.04, cold);
    t = max(t, hot);
    t = max(t, fire * 0.95);
    // The sensor's grain, fixed in the picture.
    float grain = fract(sin(dot(floor(texCoord * InSize), vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    t = clamp(t + grain * 0.035, 0.0, 1.0);
    fragColor = vec4(vec3(t * 0.93, t, t * 0.9), 1.0);
}
