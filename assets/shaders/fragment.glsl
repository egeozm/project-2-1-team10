#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_texCoords;
varying vec4 v_color;

uniform sampler2D u_texture;
uniform vec2 u_resolution;

void main() {

    vec2 uv = v_texCoords;

    vec2 pixel_size = 1.0 / u_resolution;

    // ----------------------------------
    // Scanlines
    // ----------------------------------
    float y_pixel = uv.y / pixel_size.y;

    float scan_line =
    0.5 * step(4.0, mod(y_pixel - 1.0, 6.0))
    + 0.5 * step(2.0, mod(y_pixel - 2.0, 6.0));

    // ----------------------------------
    // Horizontal blur
    // ----------------------------------
    vec4 colour = vec4(0.0);

    for (int x = 0; x < 3; x++) {
        float offset = float(x) * pixel_size.x;
        colour += (1.0 / 6.0) * texture2D(u_texture, uv + vec2( offset, 0.0));
        colour += (1.0 / 6.0) * texture2D(u_texture, uv + vec2(-offset, 0.0));
    }

    // Global darkening
    colour.rgb *= 0.8;

    // ----------------------------------
    // Luminance-based scanline darkening
    // ----------------------------------
    float luminance =
    colour.r * 0.3
    + colour.g * 0.59
    + colour.b * 0.11;

    colour.rgb *= 1.0 - (0.5 * scan_line - 0.5 * luminance);

    // 🔑 THIS LINE FIXES FONT COLOR
    gl_FragColor = colour * v_color;
}
