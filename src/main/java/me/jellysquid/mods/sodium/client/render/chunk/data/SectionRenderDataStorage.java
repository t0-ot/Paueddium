package me.jellysquid.mods.sodium.client.render.chunk.data;

import me.jellysquid.mods.sodium.client.gl.arena.GlBufferSegment;
import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class SectionRenderDataStorage {
    private final GlBufferSegment[] allocations = new GlBufferSegment[RenderRegion.REGION_SIZE];
    private final GlBufferSegment[] indexAllocations = new GlBufferSegment[RenderRegion.REGION_SIZE];

    private final long pMeshDataArray;

    public SectionRenderDataStorage() {
        this.pMeshDataArray = SectionRenderDataUnsafe.allocateHeap(RenderRegion.REGION_SIZE);
    }

    public void setMeshes(int localSectionIndex,
                          GlBufferSegment allocation, @Nullable GlBufferSegment indexAllocation, VertexRange[] ranges) {
        if (this.allocations[localSectionIndex] != null) {
            this.allocations[localSectionIndex].delete();
            this.allocations[localSectionIndex] = null;
        }

        if (this.indexAllocations[localSectionIndex] != null) {
            this.indexAllocations[localSectionIndex].delete();
            this.indexAllocations[localSectionIndex] = null;
        }

        this.allocations[localSectionIndex] = allocation;
        this.indexAllocations[localSectionIndex] = indexAllocation;

        var pMeshData = this.getDataPointer(localSectionIndex);

        int sliceMask = 0;
        int vertexOffset = allocation.getOffset();
        int indexOffset = indexAllocation != null ? indexAllocation.getOffset() * 4 : 0;

        for (int i = 0; i < ModelQuadFacing.COUNT; i++) {
            VertexRange vertexRange = ranges[i];
            int vertexCount;
            int facing = -1;

            if (vertexRange != null) {
                vertexCount = vertexRange.vertexCount();
                facing = vertexRange.facing();
            } else {
                vertexCount = 0;
            }

            int indexCount = (vertexCount >> 2) * 6;

            SectionRenderDataUnsafe.setVertexOffset(pMeshData, i, vertexOffset);
            SectionRenderDataUnsafe.setElementCountAndFacing(pMeshData, i, indexCount, facing);
            SectionRenderDataUnsafe.setIndexOffset(pMeshData, i, indexOffset);

            if (vertexCount > 0) {
                sliceMask |= 1 << facing;
            }

            vertexOffset += vertexCount;
            indexOffset += indexCount * 4;
        }

        SectionRenderDataUnsafe.setSliceMask(pMeshData, sliceMask);
    }

    public void removeMeshes(int localSectionIndex) {
        if (this.allocations[localSectionIndex] != null) {
            this.allocations[localSectionIndex].delete();
            this.allocations[localSectionIndex] = null;

            SectionRenderDataUnsafe.clear(this.getDataPointer(localSectionIndex));
        }

        removeIndexBuffer(localSectionIndex);
    }

    public void removeIndexBuffer(int localSectionIndex) {
        if (this.indexAllocations[localSectionIndex] != null) {
            this.indexAllocations[localSectionIndex].delete();
            this.indexAllocations[localSectionIndex] = null;
        }
    }

    public void replaceIndexBuffer(int localSectionIndex, GlBufferSegment indexAllocation) {
        removeIndexBuffer(localSectionIndex);

        this.indexAllocations[localSectionIndex] = indexAllocation;

        var pMeshData = this.getDataPointer(localSectionIndex);

        int indexOffset = indexAllocation != null ? indexAllocation.getOffset() * 4 : 0;

        for (int facingIndex = 0; facingIndex < ModelQuadFacing.COUNT; facingIndex++) {
            SectionRenderDataUnsafe.setIndexOffset(pMeshData, facingIndex, indexOffset);
            int indexCount = SectionRenderDataUnsafe.getElementCount(pMeshData, facingIndex);
            indexOffset += indexCount * 4;
        }
    }

    public void onBufferResized() {
        for (int sectionIndex = 0; sectionIndex < RenderRegion.REGION_SIZE; sectionIndex++) {
            this.updateMeshes(sectionIndex);
        }
    }

    private void updateMeshes(int sectionIndex) {
        var allocation = this.allocations[sectionIndex];

        if (allocation == null) {
            return;
        }

        var indexAllocation = this.indexAllocations[sectionIndex];

        var vertexOffset = allocation.getOffset();
        var indexOffset = indexAllocation != null ? indexAllocation.getOffset() * 4 : 0;

        var data = this.getDataPointer(sectionIndex);

        for (int i = 0; i < ModelQuadFacing.COUNT; i++) {
            SectionRenderDataUnsafe.setVertexOffset(data, i, vertexOffset);
            SectionRenderDataUnsafe.setIndexOffset(data, i, indexOffset);

            var indexCount = SectionRenderDataUnsafe.getElementCount(data, i);
            vertexOffset += (indexCount / 6) * 4; // convert elements back into vertices
            indexOffset += indexCount * 4;
        }
    }

    public long getDataPointer(int sectionIndex) {
        return SectionRenderDataUnsafe.heapPointer(this.pMeshDataArray, sectionIndex);
    }

    public void delete() {
        for (var allocation : this.allocations) {
            if (allocation != null) {
                allocation.delete();
            }
        }

        for (var allocation : this.indexAllocations) {
            if (allocation != null) {
                allocation.delete();
            }
        }

        Arrays.fill(this.allocations, null);
        Arrays.fill(this.indexAllocations, null);

        SectionRenderDataUnsafe.freeHeap(this.pMeshDataArray);
    }
}