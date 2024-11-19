// The position of the vertex around the model origin
vec3 _vert_position;

// The block texture coordinate of the vertex
vec2 _vert_tex_diffuse_coord;

// The light texture coordinate of the vertex
vec2 _vert_tex_light_coord;

// The color of the vertex
vec4 _vert_color;

// The index of the draw command which this vertex belongs to
uint _draw_id;

// The material bits for the primitive
uint _material_params;

#ifdef USE_VERTEX_COMPRESSION
const uint POSITION_BITS        = 20u;
const uint POSITION_MAX_COORD   = 1u << POSITION_BITS;
const uint POSITION_MAX_VALUE   = POSITION_MAX_COORD - 1u;
const uint TEXTURE_BITS         = 15u;
const uint TEXTURE_MAX_COORD    = 1u << TEXTURE_BITS;
const uint TEXTURE_MAX_VALUE    = TEXTURE_MAX_COORD - 1u;
const float VERTEX_SCALE = 32.0 / POSITION_MAX_COORD;
const float VERTEX_OFFSET = -8.0;
// The amount of inset the texture coordinates from the edges of the texture, to avoid texture bleeding
const float TEXTURE_FUZZ_AMOUNT = 1.0 / 64.0;
const float TEXTURE_GROW_FACTOR = (1.0 - TEXTURE_FUZZ_AMOUNT) / TEXTURE_MAX_COORD;

in uint a_PositionHi;
in uint a_PositionLo;
in vec4 a_Color;
in uvec2 a_TexCoord;
in uvec4 a_LightAndData;
uvec3 _deinterleave_u20x3(uint packed_hi, uint packed_lo) {
    uvec3 hi = (uvec3(packed_hi) >> uvec3(0u, 10u, 20u)) & 0x3FFu;
    uvec3 lo = (uvec3(packed_lo) >> uvec3(0u, 10u, 20u)) & 0x3FFu;

    return (hi << 10u) | lo;
}
vec2 _get_texcoord() {
    return vec2(a_TexCoord & TEXTURE_MAX_VALUE) / float(TEXTURE_MAX_COORD);
}
vec2 _get_texcoord_bias() {
    return mix(vec2(-TEXTURE_GROW_FACTOR), vec2(TEXTURE_GROW_FACTOR), bvec2(a_TexCoord >> TEXTURE_BITS));
}

void _vert_init() {
    _vert_position = ((_deinterleave_u20x3(a_PositionHi, a_PositionLo) * VERTEX_SCALE) + VERTEX_OFFSET);
    _vert_color = a_Color;
    _vert_tex_diffuse_coord = _get_texcoord() + _get_texcoord_bias();

    _vert_tex_light_coord = vec2(a_LightAndData.xy) / vec2(256.0);

    _material_params = a_LightAndData[2];
    _draw_id = a_LightAndData[3];
}

#else

in vec3 a_PosId;
in vec4 a_Color;
in vec2 a_TexCoord;
in uint a_LightCoord;

void _vert_init() {
    _vert_position = a_PosId;
    _vert_tex_diffuse_coord = a_TexCoord;
    _vert_color = a_Color;

    uint packed_draw_params = (a_LightCoord & 0xFFFFu);
    // Vertex Material
    _material_params = (packed_draw_params) & 0xFFu;

    // Vertex Mesh ID
    _draw_id  = (packed_draw_params >> 8) & 0xFFu;

    // Vertex Light
    _vert_tex_light_coord = ivec2((uvec2((a_LightCoord >> 16) & 0xFFFFu) >> uvec2(0, 8)) & uvec2(0xFFu));
}
#endif
