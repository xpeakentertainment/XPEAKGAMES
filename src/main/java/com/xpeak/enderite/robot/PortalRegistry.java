package com.xpeak.enderite.robot;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Portales de cada jugador. Solo existen los dos ultimos: el primero se empareja con el segundo; al lanzar el tercero,
 * el primero desaparece y el segundo se empareja con el tercero, y asi sucesivamente.
 */
public class PortalRegistry extends SavedData {
    private static final String NAME = "enderite_portals";

    public record Entry(UUID id, ResourceKey<Level> dim, Vec3 center, Direction facing, Direction up, int color) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            t.putString("Dim", dim.location().toString());
            t.putDouble("X", center.x);
            t.putDouble("Y", center.y);
            t.putDouble("Z", center.z);
            t.putInt("Facing", facing.get3DDataValue());
            t.putInt("Up", up.get3DDataValue());
            t.putInt("Color", color);
            return t;
        }

        static Entry load(CompoundTag t) {
            return new Entry(t.getUUID("Id"), ResourceKey.create(Registries.DIMENSION, new ResourceLocation(t.getString("Dim"))),
                    new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z")), Direction.from3DDataValue(t.getInt("Facing")),
                    Direction.from3DDataValue(t.getInt("Up")), t.getInt("Color"));
        }
    }

    private final Map<UUID, List<Entry>> portals = new HashMap<>();

    public static PortalRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PortalRegistry::load, PortalRegistry::new, NAME);
    }

    private static PortalRegistry load(CompoundTag tag) {
        PortalRegistry r = new PortalRegistry();
        CompoundTag owners = tag.getCompound("Owners");
        for (String key : owners.getAllKeys()) {
            List<Entry> list = new ArrayList<>();
            ListTag lt = owners.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < lt.size(); i++) list.add(Entry.load(lt.getCompound(i)));
            try {
                r.portals.put(UUID.fromString(key), list);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag owners = new CompoundTag();
        portals.forEach((owner, list) -> {
            ListTag lt = new ListTag();
            for (Entry e : list) lt.add(e.save());
            owners.put(owner.toString(), lt);
        });
        tag.put("Owners", owners);
        return tag;
    }

    /** Anyade un portal; devuelve los que sobran (el mas antiguo cuando ya habia dos). */
    public List<Entry> add(UUID owner, Entry e) {
        List<Entry> list = portals.computeIfAbsent(owner, k -> new ArrayList<>());
        list.add(e);
        List<Entry> removed = new ArrayList<>();
        while (list.size() > 2) removed.add(list.remove(0));
        setDirty();
        return removed;
    }

    public void remove(UUID owner, UUID id) {
        List<Entry> list = portals.get(owner);
        if (list != null && list.removeIf(e -> e.id().equals(id))) setDirty();
    }

    public boolean isActive(UUID owner, UUID id) {
        List<Entry> list = portals.get(owner);
        return list != null && list.stream().anyMatch(e -> e.id().equals(id));
    }

    @Nullable
    public Entry partner(UUID owner, UUID id) {
        List<Entry> list = portals.get(owner);
        if (list == null || list.size() < 2) return null;
        for (Entry e : list) if (!e.id().equals(id)) return e;
        return null;
    }

    public List<Entry> of(UUID owner) {
        return Collections.unmodifiableList(portals.getOrDefault(owner, List.of()));
    }

    /** Color del siguiente portal: alterna con el del ultimo que se lanzo. */
    public int nextColor(UUID owner) {
        List<Entry> list = portals.get(owner);
        return list == null || list.isEmpty() ? 0 : (list.get(list.size() - 1).color() ^ 1);
    }
}
