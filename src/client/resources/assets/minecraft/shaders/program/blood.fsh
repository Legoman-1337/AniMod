#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform float Time;
uniform vec2 Frequency;
uniform vec2 WobbleAmount;

out vec4 fragColor;

void main() {
    // Плавное "тающее" искажение кадра, как у ванильного wobble
    float xOffset = sin(texCoord.y * Frequency.x + Time * 3.1415926535 * 2.0) * WobbleAmount.x;
    float yOffset = cos(texCoord.x * Frequency.y + Time * 3.1415926535 * 2.0) * WobbleAmount.y;
    vec4 rgb = texture(DiffuseSampler, texCoord + vec2(xOffset, yOffset));

    // Кровавый цветокор: яркость пикселя определяет интенсивность красного,
    // зелёный и синий каналы почти полностью выдавливаются
    float lum = dot(rgb.rgb, vec3(0.299, 0.587, 0.114));
    vec3 blood = vec3(lum * 1.55 + 0.05, lum * 0.11, lum * 0.13);

    fragColor = vec4(blood, 1.0);
}
