package me.jellysquid.mods.sodium.client.render.chunk.vertex.format.impl;

import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeFormat;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkMeshAttribute;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.minecraft.util.Mth;
import org.lwjgl.system.MemoryUtil;

/**
 * This version of the VanillaLikeChunkVertex aims to retain the high precision while optimizing for memory
 * layout. This keeps the visual fidelity of vanilla, but aims to reduce VRAM usage.
 */
public class VanillaLikeChunkVertex implements ChunkVertexType {
    public static final int STRIDE = 28;

    // Optimized GlVertexFormat for high-precision vertex encoding
    public static final GlVertexFormat<ChunkMeshAttribute> VERTEX_FORMAT = GlVertexFormat.builder(ChunkMeshAttribute.class, STRIDE)
            .addElement(ChunkMeshAttribute.POSITION_HI, 0, GlVertexAttributeFormat.UNSIGNED_INT, 1, false, true) // High bits of position
            .addElement(ChunkMeshAttribute.POSITION_LO, 4, GlVertexAttributeFormat.UNSIGNED_INT, 1, false, true) // Low bits of position
            .addElement(ChunkMeshAttribute.COLOR, 8, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
            .addElement(ChunkMeshAttribute.TEXTURE, 12, GlVertexAttributeFormat.UNSIGNED_SHORT, 2, false, true) // Texture coordinates
            .addElement(ChunkMeshAttribute.LIGHT_MATERIAL_INDEX, 16, GlVertexAttributeFormat.UNSIGNED_SHORT, 2, false, true) // Light data
            .build();

    private static final int POSITION_MAX_VALUE = 1 << 20; // 20-bit position precision
    private static final int TEXTURE_MAX_VALUE = 1 << 15;  // 15-bit texture precision

    private static final float MODEL_ORIGIN = 8.0f;
    private static final float MODEL_RANGE = 32.0f;

    @Override
    public GlVertexFormat<ChunkMeshAttribute> getVertexFormat() {
        return VERTEX_FORMAT;
    }

    @Override
    public ChunkVertexEncoder getEncoder() {
        return (ptr, material, vertices, sectionIndex) -> {
            // Calculate the centroid of the texture region mapped to the quad
            float texCentroidU = 0.0f;
            float texCentroidV = 0.0f;
            for (var vertex : vertices) {
                texCentroidU += vertex.u;
                texCentroidV += vertex.v;
            }
            texCentroidU *= (1.0f / 4.0f);
            texCentroidV *= (1.0f / 4.0f);

            for (int i = 0; i < 4; i++) {
                var vertex = vertices[i];
                int x = quantizePosition(vertex.x);
                int y = quantizePosition(vertex.y);
                int z = quantizePosition(vertex.z);

                int u = encodeTexture(texCentroidU, vertex.u);
                int v = encodeTexture(texCentroidV, vertex.v);
                int light = encodeLight(vertex.light);

                // High and Low bits of position are packed into two separate integers
                MemoryUtil.memPutInt(ptr + 0L, packPositionHi(x, y, z));
                MemoryUtil.memPutInt(ptr + 4L, packPositionLo(x, y, z));

                // Store color, texture and light data
                MemoryUtil.memPutInt(ptr + 8L, vertex.color);
                MemoryUtil.memPutInt(ptr + 12L, packTexture(u, v));
                MemoryUtil.memPutInt(ptr + 16L, packLightAndData(light, material.bits(), sectionIndex));

                ptr += STRIDE;
            }
            return ptr;
        };
    }

    private static int packPositionHi(int x, int y, int z) {
        // High bits of position (10 bits for each coordinate)
        return (((x >>> 10) & 0x3FF) << 0) |
               (((y >>> 10) & 0x3FF) << 10) |
               (((z >>> 10) & 0x3FF) << 20);
    }

    private static int packPositionLo(int x, int y, int z) {
        // Low bits of position (10 bits for each coordinate)
        return ((x & 0x3FF) << 0) |
               ((y & 0x3FF) << 10) |
               ((z & 0x3FF) << 20);
    }

    private static int quantizePosition(float position) {
        // Normalize and quantize the position to 20 bits
        return ((int) (normalizePosition(position) * POSITION_MAX_VALUE)) & 0xFFFFF;
    }

    private static float normalizePosition(float value) {
        return (MODEL_ORIGIN + value) / MODEL_RANGE;
    }

    private static int packTexture(int u, int v) {
        // Pack texture coordinates into a single 32-bit integer
        return ((u & 0xFFFF) << 0) | ((v & 0xFFFF) << 16);
    }

    private static int encodeTexture(float center, float value) {
        // Quantize texture coordinates with a bias to reduce stretching
        int bias = (value < center) ? 1 : -1;
        int quantized = floorInt(value * TEXTURE_MAX_VALUE) + bias;
        return (quantized & 0x7FFF) | (sign(bias) << 15);
    }

    private static int encodeLight(int light) {
        // Encode light data as 16-bit, with block and sky light values
        int sky = Mth.clamp((light >>> 16) & 0xFF, 8, 248);
        int block = Mth.clamp((light >>> 0) & 0xFF, 8, 248);
        return (block << 0) | (sky << 8);
    }

    private static int packLightAndData(int light, int material, int sectionIndex) {
        // Pack light, material, and section data
        return ((light & 0xFFFF) << 0) |
               ((material & 0xFF) << 16) |
               ((sectionIndex & 0xFF) << 24);
    }

    private static int sign(int x) {
        // Extract the sign of the texture quantization bias
        return (x >>> 31);
    }

    private static int floorInt(float value) {
        // Floor the texture value for quantization
        return (int) Math.floor(value);
    }
}