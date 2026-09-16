package net.adeptfrog.blocksmith.client.render;

import net.adeptfrog.blocksmith.data.WeaponVoxel;
import java.util.List;

public record WeaponRenderState(List<WeaponVoxel> voxels, int offsetX, int offsetY) {}