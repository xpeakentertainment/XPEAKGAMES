#version 150

// Distorsion del Limbo (mod Enderite): ondas, aberracion cromatica, desaturado rojizo, vinyeta y grano.
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float t = Time * 6.2831853;
    vec2 uv = texCoord;
    uv.x += sin(uv.y * 22.0 + t) * 0.0032 + sin(uv.y * 6.0 - t * 2.0) * 0.0018;
    uv.y += cos(uv.x * 17.0 + t) * 0.0026;
    vec2 c = uv - 0.5;
    float r = length(c);
    uv = 0.5 + c * (1.0 + 0.018 * sin(t) * r);

    float ca = 0.0035 + 0.0025 * sin(t * 2.0);
    vec2 dir = (r > 0.0001 ? c / r : vec2(0.0)) * ca * (0.3 + r);
    float rr = texture(DiffuseSampler, uv + dir).r;
    float gg = texture(DiffuseSampler, uv).g;
    float bb = texture(DiffuseSampler, uv - dir).b;
    vec3 col = vec3(rr, gg, bb);

    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum), 0.55);
    col *= vec3(1.18, 0.80, 0.78);

    float vig = smoothstep(0.92, 0.28, r);
    col *= mix(0.30, 1.0, vig);

    float n = hash(floor(texCoord * InSize) + vec2(floor(Time * 97.0), floor(Time * 61.0)));
    col += (n - 0.5) * 0.07;

    float line = step(0.996, fract(texCoord.y * 3.0 - Time));
    col += line * 0.04;

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
