package net.adeptfrog.blocksmith.data;

import java.util.List;
import java.util.function.Function;

/**
 * Remembers a value computed from a voxel list (unlocked abilities, draw speed, a render mesh, ...) so it is not
 * recomputed every tick or frame. Looked up by list instance first (an item keeps the same list until the design
 * changes), then by content, because a stack synced from the server (after a durability change, for example)
 * arrives with a new but equal list. Entries are dropped when the material settings change
 * (see {@link VoxelMaterialRegistry#version()}).
 */
public final class DesignCache<V> {
    private static final class Entry<V> {
        List<WeaponVoxel> key;
        final int hash;
        final int version;
        final V value;

        Entry(List<WeaponVoxel> key, int hash, int version, V value) {
            this.key = key;
            this.hash = hash;
            this.version = version;
            this.value = value;
        }
    }

    private final Entry<V>[] entries;

    @SuppressWarnings("unchecked")
    public DesignCache(int size) {
        this.entries = new Entry[size];
    }

    public synchronized V get(List<WeaponVoxel> voxels, Function<List<WeaponVoxel>, V> compute) {
        int version = VoxelMaterialRegistry.version();
        for (int i = 0; i < entries.length; i++) {
            Entry<V> e = entries[i];
            if (e != null && e.key == voxels && e.version == version) return hit(i);
        }
        int hash = voxels.hashCode();
        for (int i = 0; i < entries.length; i++) {
            Entry<V> e = entries[i];
            if (e != null && e.version == version && e.hash == hash && e.key.size() == voxels.size() && e.key.equals(voxels)) {
                e.key = voxels; // the new instance is the one asked for from now on
                return hit(i);
            }
        }
        V value = compute.apply(voxels);
        System.arraycopy(entries, 0, entries, 1, entries.length - 1);
        entries[0] = new Entry<>(voxels, hash, version, value);
        return value;
    }

    private V hit(int i) {
        Entry<V> e = entries[i];
        System.arraycopy(entries, 0, entries, 1, i);
        entries[0] = e;
        return e.value;
    }
}
