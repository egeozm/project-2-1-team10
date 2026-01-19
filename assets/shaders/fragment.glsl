#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_texCoords;
varying vec4 v_color;

uniform sampler2D u_texture;
uniform vec2 u_resolution;

const float PI = 3.14159265;

void main() {

    vec2 uv = v_texCoords;

    vec2 pixel_size = 1.0 / u_resolution * 8.0;

    // ----------------------------------
    // Scanlines
    // ----------------------------------

    float scanline = 0.5 * (cos(uv.y * 2.0 * PI / pixel_size.y) + 1.0);

    // ----------------------------------
    // Horizontal blur
    // ----------------------------------
    vec4 colour = vec4(0.0);

    for (int x = 0; x < 5; x++) {
        float offset = float(x) * pixel_size.x / 8.0;
        colour += (1.0 / 10.0) * texture2D(u_texture, uv + vec2( offset, 0.0));
        colour += (1.0 / 10.0) * texture2D(u_texture, uv + vec2(-offset, 0.0));
    }

    // ----------------------------------
    // Luminance-based scanline darkening
    // ----------------------------------
    float luminance =
    colour.r * 0.3
    + colour.g * 0.59
    + colour.b * 0.11;

    colour.rgb *= 1.0 - (0.5 * scanline - 0.5 * luminance);

    gl_FragColor = colour * v_color;
}
