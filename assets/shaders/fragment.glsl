#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2 u_resolution;
uniform float u_time;

void main() {
    vec2 uv = v_texCoords;
    vec2 texel = 1.0 / u_resolution;

    // -------------------------------------------------
    // Horizontal wobble (CRT-style distortion)
    // -------------------------------------------------
    float wobble =
    sin(uv.y * u_resolution.y * 3.14159 * 0.1 + u_time * 4.0)
    * 0.6 * texel.x;

    vec2 wobbleUV = vec2(uv.x + wobble, uv.y);

    // -------------------------------------------------
    // Chromatic aberration
    // -------------------------------------------------
    float aberr = 4.0;

    vec4 center = texture2D(u_texture, wobbleUV);

    float r = texture2D(
    u_texture,
    wobbleUV + vec2(texel.x * aberr, 0.0)
    ).r;

    float g = center.g;

    float b = texture2D(
    u_texture,
    wobbleUV - vec2(texel.x * aberr, 0.0)
    ).b;

    vec4 color = vec4(r, g, b, center.a);

    // -------------------------------------------------
    // Scanlines
    // -------------------------------------------------
    float scanline =
    sin(uv.y * u_resolution.y * 3.14159 * 0.3 + u_time);

    color.rgb *= 0.9 + 0.1 * scanline;

    gl_FragColor = color;
}
