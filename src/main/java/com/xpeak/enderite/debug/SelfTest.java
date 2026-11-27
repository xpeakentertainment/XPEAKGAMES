package com.xpeak.enderite.debug;

import com.mojang.authlib.GameProfile;
import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.cling.WallCling;
import com.xpeak.enderite.entity.CrawlerEntity;
import com.xpeak.enderite.entity.StuckToolEntity;
import com.xpeak.enderite.limbo.Limbo;
import com.xpeak.enderite.limbo.WorldCopier;
import com.xpeak.enderite.nightmare.NightmareData;
import com.xpeak.enderite.registry.ModBlocks;
import com.xpeak.enderite.registry.ModEntities;
import com.xpeak.enderite.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pruebas automaticas en un servidor real. Solo se activan con -Denderite.selftest=true (no afectan al juego normal).
 */
public final class SelfTest {
    public static final boolean ENABLED = Boolean.getBoolean("enderite.selftest");

    private static final List<String> LOG = new ArrayList<>();
    private static int passed, failed;
    private static MinecraftServer server;
    private static int tick = -1;

    // estado de la prueba del Crawler
    private static CrawlerEntity digCrawler;
    private static BlockPos digRoom;
    private static int wallBlocksBefore;
    private static FakePlayer digTarget;

    private static void check(String name, boolean ok, String detail) {
        if (ok) passed++;
        else failed++;
        String line = (ok ? "PASS " : "FAIL ") + name + (detail.isEmpty() ? "" : " -> " + detail);
        LOG.add(line);
        EnderiteMod.LOGGER.info("[SELFTEST] {}", line);
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        server = event.getServer();
        tick = 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null || tick < 0) return;
        tick++;
        try {
            if (tick == 20) staticChecks();
            if (tick == 30) worldgenCheck();
            if (tick == 40) copyCheck();
            if (tick == 60) nightmareCheck();
            if (tick == 70) clingCheck();
            if (tick == 80) startDigCheck();
            if (tick == 80 + 700) endDigCheck();
            if (tick == 80 + 720) finish();
        } catch (Throwable t) {
            check("excepcion en tick " + tick, false, t.toString());
            EnderiteMod.LOGGER.error("[SELFTEST] error", t);
            finish();
        }
    }

    private static void staticChecks() {
        ServerLevel limbo = server.getLevel(Limbo.LIMBO);
        check("dimension limbo existe", limbo != null, "");
        if (limbo != null) {
            check("limbo: no natural (no se duerme)", !limbo.dimensionType().natural(), "");
            check("limbo: altura igual al mundo", limbo.getMinBuildHeight() == server.overworld().getMinBuildHeight()
                    && limbo.getHeight() == server.overworld().getHeight(), limbo.getMinBuildHeight() + "/" + limbo.getHeight());
        }
        BlockState ore = ModBlocks.ENDERITE_ORE.get().defaultBlockState();
        check("pico netherite pica la enderita", new ItemStack(Items.NETHERITE_PICKAXE).isCorrectToolForDrops(ore), "");
        check("pico diamante NO pica la enderita", !new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(ore), "");
        check("pico enderite pica la enderita", new ItemStack(ModItems.ENDERITE_PICKAXE.get()).isCorrectToolForDrops(ore), "");
        check("pico enderite pica obsidiana", new ItemStack(ModItems.ENDERITE_PICKAXE.get()).isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "");
        check("mena en mineable/pickaxe", ore.is(BlockTags.MINEABLE_WITH_PICKAXE), "");
        ItemStack sword = new ItemStack(ModItems.ENDERITE_SWORD.get());
        check("espada enderite durabilidad 4062", sword.getMaxDamage() == 4062, "" + sword.getMaxDamage());
        double dmg = sword.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE).stream().mapToDouble(m -> m.getAmount()).sum() + 1.0D;
        check("espada enderite dano 16 (x2 netherite)", Math.abs(dmg - 16.0D) < 0.01D, "" + dmg);
        int armor = ((net.minecraft.world.item.ArmorItem) ModItems.ENDERITE_CHESTPLATE.get()).getDefense();
        check("peto enderite armadura 16 (x2)", armor == 16, "" + armor);

        var recipes = server.getRecipeManager();
        String[] ids = {"enderite_ingot", "enderite_scrap_from_smelting", "enderite_scrap_from_blasting", "enderite_block",
                "enderite_ingot_from_block", "enderite_sword_smithing", "enderite_pickaxe_smithing", "enderite_axe_smithing",
                "enderite_shovel_smithing", "enderite_hoe_smithing", "enderite_helmet_smithing", "enderite_chestplate_smithing",
                "enderite_leggings_smithing", "enderite_boots_smithing", "enderite_upgrade_smithing_template", "enderite_bricks",
                "chiseled_enderite_bricks", "enderite_brick_stairs", "enderite_brick_slab"};
        List<String> missing = new ArrayList<>();
        for (String id : ids) if (recipes.byKey(new ResourceLocation(EnderiteMod.MODID, id)).isEmpty()) missing.add(id);
        check("recetas cargadas", missing.isEmpty(), missing.toString());

        String[] tables = {"blocks/enderite_ore", "blocks/enderite_block", "blocks/enderite_bricks", "blocks/chiseled_enderite_bricks",
                "blocks/enderite_brick_stairs", "blocks/enderite_brick_slab"};
        List<String> missingLoot = new ArrayList<>();
        for (String t : tables) {
            if (server.getLootData().getLootTable(new ResourceLocation(EnderiteMod.MODID, t)) == net.minecraft.world.level.storage.loot.LootTable.EMPTY)
                missingLoot.add(t);
        }
        check("tablas de botin", missingLoot.isEmpty(), missingLoot.toString());
        var damageTypes = server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        check("tipo de dano crawler", damageTypes.containsKey(CrawlerEntity.CRAWLER_DAMAGE.location()), "");
        var placed = server.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        check("feature de mena registrada", placed.containsKey(new ResourceLocation(EnderiteMod.MODID, "enderite_ore")), "");
    }

    private static void worldgenCheck() {
        ServerLevel end = server.getLevel(Level.END);
        int count = 0;
        int endStone = 0;
        for (int cx = -4; cx <= 4; cx++) {
            for (int cz = -4; cz <= 4; cz++) {
                LevelChunk c = end.getChunk(cx, cz);
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 100; y++) {
                    BlockState s = c.getBlockState(new BlockPos(c.getPos().getMinBlockX() + x, y, c.getPos().getMinBlockZ() + z));
                    if (s.is(ModBlocks.ENDERITE_ORE.get())) count++;
                    else if (s.is(Blocks.END_STONE)) endStone++;
                }
            }
        }
        check("mena de enderita se genera en el End", count > 0, count + " menas entre " + endStone + " de piedra del End (isla central)");
    }

    private static void copyCheck() {
        ServerLevel ow = server.overworld();
        ServerLevel limbo = server.getLevel(Limbo.LIMBO);
        if (limbo == null) return;
        int bx = 320, bz = 320;
        int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        BlockPos base = new BlockPos(bx, y, bz);
        // Casita de prueba
        for (int dx = 0; dx < 7; dx++) for (int dz = 0; dz < 7; dz++) {
            ow.setBlock(base.offset(dx, -1, dz), Blocks.COBBLESTONE.defaultBlockState(), 3);
            for (int dy = 0; dy < 4; dy++) {
                boolean wall = dx == 0 || dz == 0 || dx == 6 || dz == 6;
                ow.setBlock(base.offset(dx, dy, dz), wall ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            }
            ow.setBlock(base.offset(dx, 4, dz), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
        }
        ow.setBlock(base.offset(3, 1, 0), Blocks.GLASS.defaultBlockState(), 3);
        BlockPos chestPos = base.offset(2, 0, 2);
        ow.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        if (ow.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
        BlockPos signPos = base.offset(4, 0, 2);
        ow.setBlock(signPos, Blocks.OAK_SIGN.defaultBlockState(), 3);
        if (ow.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            sign.setText(sign.getFrontText().setMessage(0, Component.literal("HOLA LIMBO")), true);
        }
        ow.setBlock(base.offset(3, 2, 3), Blocks.TORCH.defaultBlockState(), 3);
        // Agujero en el suelo
        for (int dy = 2; dy < 6; dy++) ow.setBlock(base.offset(10, -dy, 10), Blocks.AIR.defaultBlockState(), 3);

        long t0 = System.nanoTime();
        WorldCopier copier = new WorldCopier(ow, limbo, new ChunkPos(base), 1);
        copier.copyAllNow();
        copier.release();
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        int mismatches = 0, total = 0;
        ChunkPos center = new ChunkPos(base);
        for (int cx = center.x - 1; cx <= center.x + 1; cx++) for (int cz = center.z - 1; cz <= center.z + 1; cz++) {
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int yy = ow.getMinBuildHeight(); yy < ow.getMaxBuildHeight(); yy++) {
                BlockPos p = new BlockPos(cx * 16 + x, yy, cz * 16 + z);
                total++;
                if (ow.getBlockState(p) != limbo.getBlockState(p)) mismatches++;
            }
        }
        check("copia del mundo identica", mismatches == 0, mismatches + "/" + total + " distintos, " + copier.changedBlocks
                + " bloques copiados en " + ms + " ms");
        boolean chestOk = limbo.getBlockEntity(chestPos) instanceof ChestBlockEntity c && c.getItem(0).is(Items.DIAMOND);
        check("cofre copiado con su contenido", chestOk, "");
        boolean signOk = limbo.getBlockEntity(signPos) instanceof SignBlockEntity s
                && s.getFrontText().getMessage(0, false).getString().equals("HOLA LIMBO");
        check("cartel copiado con su texto", signOk, "");

        // El terreno natural lejos de la casa debe ser identico (misma semilla y generador)
        int natDiff = 0, natTotal = 0;
        LevelChunk a = ow.getChunk(center.x + 6, center.z + 6);
        LevelChunk b = limbo.getChunk(center.x + 6, center.z + 6);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int yy = -64; yy < 200; yy++) {
            BlockPos p = new BlockPos(a.getPos().getMinBlockX() + x, yy, a.getPos().getMinBlockZ() + z);
            natTotal++;
            if (a.getBlockState(p) != b.getBlockState(p)) natDiff++;
        }
        check("terreno natural del Limbo igual al mundo real", natDiff * 100 < natTotal, natDiff + "/" + natTotal + " distintos");
    }

    private static void nightmareCheck() {
        ServerLevel ow = server.overworld();
        NightmareData data = NightmareData.get(server);
        data.setLevel(3);
        BlockPos p = new BlockPos(0, ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0), 0);
        Zombie z = EntityType.ZOMBIE.create(ow);
        z.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
        z.finalizeSpawn(ow, ow.getCurrentDifficultyAt(p), MobSpawnType.COMMAND, null, null);
        ow.addFreshEntity(z);
        check("pesadilla 3: zombi con +60% vida", Math.abs(z.getMaxHealth() - 32.0F) < 0.01F, "" + z.getMaxHealth());
        check("pesadilla 3: zombi con vida llena", Math.abs(z.getHealth() - z.getMaxHealth()) < 0.01F, "" + z.getHealth());
        z.discard();
        data.setLevel(0);
        Zombie z2 = EntityType.ZOMBIE.create(ow);
        z2.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
        ow.addFreshEntity(z2);
        check("pesadilla 0: zombi normal", Math.abs(z2.getMaxHealth() - 20.0F) < 0.01F, "" + z2.getMaxHealth());
        z2.discard();
    }

    private static void clingCheck() {
        ServerLevel ow = server.overworld();
        int bx = 360, bz = 360;
        ow.setChunkForced(bx >> 4, bz >> 4, true);
        int gy = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        // pared de piedra alta en x=bx (su cara este mira al jugador)
        for (int dy = -1; dy < 8; dy++) for (int dz = -2; dz <= 2; dz++)
            ow.setBlock(new BlockPos(bx, gy + dy, bz + dz), Blocks.STONE.defaultBlockState(), 3);
        for (int dy = 0; dy < 8; dy++) for (int dx = 1; dx <= 3; dx++) for (int dz = -2; dz <= 2; dz++)
            ow.setBlock(new BlockPos(bx + dx, gy + dy, bz + dz), Blocks.AIR.defaultBlockState(), 3);

        FakePlayer fake = FakePlayerFactory.get(ow, new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000c11a"), "[EnderiteTest]"));
        fake.moveTo(bx + 1.31D, gy + 2.0D, bz + 0.5D, 90.0F, 0.0F); // mirando al oeste (a la pared)
        fake.setOnGround(false);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        fake.setItemInHand(InteractionHand.MAIN_HAND, sword);

        WallCling.Spot spot = WallCling.findSpot(fake, null, null);
        check("agarre: encuentra la pared", spot != null && spot.face() == Direction.EAST, String.valueOf(spot));
        if (spot == null) return;
        boolean ok = WallCling.doCling(fake, InteractionHand.MAIN_HAND, spot);
        check("agarre: se clava la espada y cuelgas", ok && fake.getVehicle() instanceof StuckToolEntity, "vehiculo=" + fake.getVehicle());
        if (!(fake.getVehicle() instanceof StuckToolEntity tool)) return;
        int dmg = tool.getItem().getDamageValue();
        check("agarre: gasta durabilidad", dmg >= 20, "dano=" + dmg + "/250");
        check("agarre: la mano queda vacia", fake.getMainHandItem().isEmpty(), "");
        Vec3 before = fake.position();
        WallCling.wallJump(fake);
        check("agarre: salto extra desde la pared", fake.getVehicle() == null && fake.getDeltaMovement().y > 0.5D,
                "vel=" + fake.getDeltaMovement());
        tool.interact(fake, InteractionHand.MAIN_HAND);
        check("agarre: click derecho recupera la herramienta", fake.getMainHandItem().is(Items.IRON_SWORD) && tool.isRemoved(),
                "mano=" + fake.getMainHandItem() + " pos=" + before);
        // herramienta no valida
        fake.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        check("agarre: un palo no sirve", !WallCling.canTryCling(fake, fake.getMainHandItem()), "");
        fake.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TRIDENT));
        check("agarre: el tridente (lanza) sirve", WallCling.canTryCling(fake, fake.getMainHandItem()), "");
        fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void startDigCheck() {
        ServerLevel ow = server.overworld();
        int bx = 420, bz = 420;
        for (int cx = (bx - 16) >> 4; cx <= (bx + 32) >> 4; cx++) for (int cz = (bz - 16) >> 4; cz <= (bz + 16) >> 4; cz++)
            ow.setChunkForced(cx, cz, true);
        int gy = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) + 1;
        // suelo plano
        for (int dx = -4; dx <= 20; dx++) for (int dz = -6; dz <= 6; dz++) {
            ow.setBlock(new BlockPos(bx + dx, gy - 1, bz + dz), Blocks.STONE.defaultBlockState(), 3);
            for (int dy = 0; dy < 6; dy++) ow.setBlock(new BlockPos(bx + dx, gy + dy, bz + dz), Blocks.AIR.defaultBlockState(), 3);
        }
        // habitacion cerrada de cobblestone (con una puerta) donde esta la "presa"
        digRoom = new BlockPos(bx + 12, gy, bz);
        wallBlocksBefore = 0;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 4; dy++) {
            boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == -1 || dy == 4;
            BlockPos p = digRoom.offset(dx, dy, dz);
            ow.setBlock(p, shell ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            if (shell) wallBlocksBefore++;
        }
        BlockPos door = digRoom.offset(-2, 0, 0);
        ow.setBlock(door, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST), 3);
        ow.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);

        digTarget = FakePlayerFactory.get(ow, new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000d16a"), "[EnderitePresa]"));
        digTarget.moveTo(digRoom.getX() + 0.5D, digRoom.getY(), digRoom.getZ() + 0.5D, 0, 0);

        digCrawler = ModEntities.CRAWLER.get().create(ow);
        digCrawler.moveTo(bx + 0.5D, gy, bz + 0.5D, -90.0F, 0);
        digCrawler.setPersistenceRequired();
        ow.addFreshEntity(digCrawler);
        digCrawler.setTarget(digTarget);
        check("crawler aparece", digCrawler.isAlive(), "");
    }

    private static void endDigCheck() {
        ServerLevel ow = server.overworld();
        int wallNow = 0;
        boolean doorGone = !(ow.getBlockState(digRoom.offset(-2, 0, 0)).getBlock() instanceof DoorBlock);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 4; dy++) {
            boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == -1 || dy == 4;
            if (shell && ow.getBlockState(digRoom.offset(dx, dy, dz)).is(Blocks.COBBLESTONE)) wallNow++;
        }
        double dist = digCrawler.distanceTo(digTarget);
        check("crawler llega hasta la presa encerrada", dist < 3.5D, String.format("distancia=%.1f", dist));
        check("crawler rompe la puerta o los muros", doorGone || wallNow < wallBlocksBefore - 1,
                "puerta rota=" + doorGone + ", cobble " + wallBlocksBefore + " -> " + wallNow);
        digCrawler.discard();
    }

    private static void finish() {
        if (tick < 0) return;
        tick = -10_000;
        LOG.add(0, "RESUMEN: " + passed + " OK, " + failed + " FALLOS");
        try {
            Files.write(new File(server.getServerDirectory(), "enderite-selftest.txt").toPath(), LOG, StandardCharsets.UTF_8);
        } catch (IOException e) {
            EnderiteMod.LOGGER.error("No se pudo escribir el resultado", e);
        }
        EnderiteMod.LOGGER.info("[SELFTEST] TERMINADO: {} OK, {} FALLOS", passed, failed);
        server.halt(false);
    }

    private SelfTest() {}
}
