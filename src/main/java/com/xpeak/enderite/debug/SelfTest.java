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
            if (tick == 32) {
                server.overworld().setChunkForced(500 >> 4, 500 >> 4, true);
                server.overworld().setChunkForced(500 >> 4, (500 >> 4) - 1, true);
                server.overworld().setChunkForced(500 >> 4, (500 >> 4) + 1, true);
            }
            if (tick == 40) copyCheck();
            if (tick == 60) nightmareCheck();
            if (tick == 70) clingCheck();
            if (tick == 80) startDigCheck();
            if (tick == 84) quicksandCheck();
            if (tick == 86) robotCheck();
            if (tick == 88) startMagnetCheck();
            if (tick == 88 + 140) endMagnetCheck();
            if (tick == 90) startRocketCheck();
            if (tick == 90 + 60) endRocketCheck();
            if (portableTicks > 0) tickPortable();
            if (tick == 80 + 700) endDigCheck();
            if (tick == 80 + 720 && portableTicks <= 0) finish();
            if (tick > 80 + 720 && portableTicks <= 0 && tick < 80 + 900) finish();
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


    // ------------------------------------------------------------------ arenas movedizas
    private static void quicksandCheck() {
        ServerLevel ow = server.overworld();
        ow.setWeatherParameters(0, 6000, true, false);
        ow.setRainLevel(1.0F);
        BlockPos spot = null;
        for (int i = 0; i < 64 && spot == null; i++) {
            int x = 600 + (i % 8) * 48, z = 600 + (i / 8) * 48;
            ow.getChunk(x >> 4, z >> 4);
            BlockPos top = ow.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
            if (ow.getBiome(top).value().getPrecipitationAt(top) == net.minecraft.world.level.biome.Biome.Precipitation.RAIN) spot = top;
        }
        check("arenas: zona con lluvia", spot != null, String.valueOf(spot));
        if (spot == null) return;
        ow.setChunkForced(spot.getX() >> 4, spot.getZ() >> 4, true);
        net.minecraft.util.RandomSource rnd = net.minecraft.util.RandomSource.create(42L);
        int ok = 0, trials = 500;
        for (int i = 0; i < trials; i++) {
            ow.setBlock(spot, Blocks.SAND.defaultBlockState(), 3);
            if (com.xpeak.enderite.block.QuicksandWeather.tryConvert(ow, spot, rnd)) ok++;
        }
        check("arenas: la lluvia convierte la arena un ~10%", ok > trials * 0.06 && ok < trials * 0.14, ok + "/" + trials);
        ow.setBlock(spot, Blocks.SAND.defaultBlockState(), 3);
        ow.setBlock(spot.above(2), Blocks.STONE.defaultBlockState(), 3);
        boolean covered = !com.xpeak.enderite.block.QuicksandWeather.tryConvert(ow, spot, net.minecraft.util.RandomSource.create(1L))
                && ow.getBlockState(spot).is(Blocks.SAND);
        check("arenas: sin lluvia encima no cambia", covered, "");
        ow.setBlock(spot.above(2), Blocks.AIR.defaultBlockState(), 3);
        BlockState qs = com.xpeak.enderite.registry.ModBlocks.QUICKSAND.get().defaultBlockState();
        ItemStack silkShovel = new ItemStack(Items.IRON_SHOVEL);
        silkShovel.enchant(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, 1);
        ItemStack silkPick = new ItemStack(Items.IRON_PICKAXE);
        silkPick.enchant(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, 1);
        var d1 = net.minecraft.world.level.block.Block.getDrops(qs, ow, spot, null, null, silkShovel);
        var d2 = net.minecraft.world.level.block.Block.getDrops(qs, ow, spot, null, null, silkPick);
        var d3 = net.minecraft.world.level.block.Block.getDrops(qs, ow, spot, null, null, new ItemStack(Items.IRON_SHOVEL));
        check("arenas: pala con toque de seda la recoge", !d1.isEmpty() && d1.get(0).is(com.xpeak.enderite.registry.ModItems.QUICKSAND.get()), d1.toString());
        check("arenas: pico con toque de seda la recoge", !d2.isEmpty() && d2.get(0).is(com.xpeak.enderite.registry.ModItems.QUICKSAND.get()), d2.toString());
        check("arenas: sin toque de seda da arena normal", !d3.isEmpty() && d3.get(0).is(Items.SAND), d3.toString());
        ow.setWeatherParameters(6000, 0, false, false);
        ow.setRainLevel(0.0F);
    }

    // ------------------------------------------------------------------ robot
    private static FakePlayer robotPlayer;

    private static void robotCheck() {
        ServerLevel ow = server.overworld();
        ItemStack blue = new ItemStack(com.xpeak.enderite.registry.ModItems.ROBOT_TORSO_BLUE.get());
        ItemStack red = new ItemStack(com.xpeak.enderite.registry.ModItems.ROBOT_TORSO_RED.get());
        ItemStack green = new ItemStack(com.xpeak.enderite.registry.ModItems.ROBOT_TORSO_GREEN.get());
        var chest = net.minecraft.world.entity.EquipmentSlot.CHEST;
        check("robot azul: +50% velocidad", blue.getAttributeModifiers(chest).get(Attributes.MOVEMENT_SPEED).stream().anyMatch(m -> m.getAmount() == 0.5D), "");
        check("robot rojo: +50% dano", red.getAttributeModifiers(chest).get(Attributes.ATTACK_DAMAGE).stream().anyMatch(m -> m.getAmount() == 0.5D), "");
        check("robot verde: x2 vida", green.getAttributeModifiers(chest).get(Attributes.MAX_HEALTH).stream().anyMatch(m -> m.getAmount() == 1.0D), "");

        robotPlayer = FakePlayerFactory.get(ow, new GameProfile(UUID.fromString("00000000-0000-0000-0000-0000000000b0"), "[EnderiteRobot]"));
        ItemStack t = blue.copy();
        t.hurtAndBreak(100000, robotPlayer, p -> {});
        check("robot: nunca se rompe del todo (se averia)", !t.isEmpty() && com.xpeak.enderite.robot.RobotHelper.isBroken(t), "dano=" + t.getDamageValue());
        check("robot averiado: sin bonus", t.getAttributeModifiers(chest).get(Attributes.MOVEMENT_SPEED).isEmpty(), "");

        // Fabricar en la mesa
        robotPlayer.getInventory().clearContent();
        robotPlayer.getInventory().add(new ItemStack(Items.IRON_INGOT, 16));
        robotPlayer.getInventory().add(new ItemStack(Items.REDSTONE, 9));
        robotPlayer.getInventory().add(new ItemStack(Items.DIAMOND, 1));
        robotPlayer.getInventory().add(new ItemStack(Items.LAPIS_LAZULI, 8));
        robotPlayer.getInventory().add(new ItemStack(Items.IRON_AXE, 1));
        var container = new net.minecraft.world.SimpleContainer(3);
        var menu = new com.xpeak.enderite.robot.EngineeringTableMenu(1, robotPlayer.getInventory(), container,
                net.minecraft.world.inventory.ContainerLevelAccess.NULL);
        boolean built = menu.clickMenuButton(robotPlayer, 0);
        boolean has = com.xpeak.enderite.robot.EngineeringRecipes.count(robotPlayer.getInventory(), com.xpeak.enderite.registry.ModItems.ROBOT_TORSO_BLUE.get()) == 1;
        check("mesa: fabrica el torso azul", built && has, "iron left=" + com.xpeak.enderite.robot.EngineeringRecipes.count(robotPlayer.getInventory(), Items.IRON_INGOT));
        boolean noMore = !menu.clickMenuButton(robotPlayer, 1);
        check("mesa: sin materiales no fabrica", noMore, "");
        boolean saw = menu.clickMenuButton(robotPlayer, 3);
        check("mesa: fabrica el brazo motosierra", saw, "");
        // montar brazos en la mesa
        container.setItem(0, new ItemStack(com.xpeak.enderite.registry.ModItems.ROBOT_TORSO_BLUE.get()));
        menu.getSlot(1).set(new ItemStack(com.xpeak.enderite.registry.ModItems.ROBOT_ARM_CHAINSAW.get()));
        menu.getSlot(2).set(new ItemStack(com.xpeak.enderite.registry.ModItems.ROBOT_ARM_ROCKET.get()));
        ItemStack assembled = container.getItem(0);
        check("mesa: los brazos se montan en el torso",
                com.xpeak.enderite.robot.RobotHelper.getArm(assembled, net.minecraft.world.entity.HumanoidArm.LEFT).is(com.xpeak.enderite.registry.ModItems.ROBOT_ARM_CHAINSAW.get())
                        && com.xpeak.enderite.robot.RobotHelper.getArm(assembled, net.minecraft.world.entity.HumanoidArm.RIGHT).is(com.xpeak.enderite.registry.ModItems.ROBOT_ARM_ROCKET.get()), "");

        // municion y reparacion en el bloque real
        BlockPos tablePos = new BlockPos(500, ow.getHeight(Heightmap.Types.MOTION_BLOCKING, 500, 500), 500);
        ow.setChunkForced(500 >> 4, 500 >> 4, true);
        ow.setBlock(tablePos, com.xpeak.enderite.registry.ModBlocks.ENGINEERING_TABLE.get().defaultBlockState(), 3);
        var be = (com.xpeak.enderite.robot.EngineeringTableBlockEntity) ow.getBlockEntity(tablePos);
        ItemStack robot = assembled.copy();
        robot.setDamageValue(300);
        be.getContainer().setItem(0, robot);
        be.getContainer().setItem(1, new ItemStack(Items.FIREWORK_ROCKET, 10));
        be.getContainer().setItem(2, new ItemStack(Items.IRON_INGOT, 1));
        be.process();
        ItemStack after = be.getContainer().getItem(0);
        int ammo = com.xpeak.enderite.robot.RobotHelper.getAmmo(com.xpeak.enderite.robot.RobotHelper.getArm(after, net.minecraft.world.entity.HumanoidArm.RIGHT));
        check("mesa: carga cohetes como municion", ammo == 10 && be.getContainer().getItem(1).isEmpty(), "municion=" + ammo);
        check("mesa: repara con hierro", after.getDamageValue() == 300 - after.getMaxDamage() / 4 && be.getContainer().getItem(2).isEmpty(), "dano=" + after.getDamageValue());

        // agua
        ItemStack worn = after.copy();
        worn.setDamageValue(0);
        robotPlayer.setItemSlot(chest, worn);
        BlockPos wpos = tablePos.offset(3, 0, 0);
        ow.setBlock(wpos, Blocks.WATER.defaultBlockState(), 3);
        ow.setBlock(wpos.above(), Blocks.WATER.defaultBlockState(), 3);
        robotPlayer.moveTo(wpos.getX() + 0.5D, wpos.getY(), wpos.getZ() + 0.5D, 0, 0);
        robotPlayer.baseTick();
        robotPlayer.tickCount = 20;
        com.xpeak.enderite.robot.RobotServer.tick(robotPlayer);
        check("robot: el agua lo estropea", robotPlayer.getItemBySlot(chest).getDamageValue() > 0,
                "en agua=" + robotPlayer.isInWaterOrBubble() + " dano=" + robotPlayer.getItemBySlot(chest).getDamageValue());
        ow.setBlock(wpos, Blocks.AIR.defaultBlockState(), 3);
        ow.setBlock(wpos.above(), Blocks.AIR.defaultBlockState(), 3);
        robotPlayer.baseTick();

        // reglas: con brazos no se usa la hotbar, con torso no se usa la mano secundaria
        check("robot: con brazos la hotbar no se usa", com.xpeak.enderite.robot.RobotHelper.hasArms(robotPlayer), "");

        // motosierra
        robotPlayer.getItemBySlot(chest).setDamageValue(0);
        robotPlayer.moveTo(tablePos.getX() + 0.5D, tablePos.getY() + 1.0D, tablePos.getZ() - 3.5D, 0.0F, 0.0F); // mirando al sur
        Zombie dummy = EntityType.ZOMBIE.create(ow);
        dummy.moveTo(robotPlayer.getX(), robotPlayer.getY(), robotPlayer.getZ() + 1.8D, 180.0F, 0);
        dummy.setNoAi(true);
        ow.addFreshEntity(dummy);
        float before = dummy.getHealth();
        com.xpeak.enderite.robot.RobotServer.setInput(robotPlayer, true, false);
        for (int i = 0; i < 12; i++) {
            robotPlayer.tickCount = 100 + i;
            com.xpeak.enderite.robot.RobotServer.tick(robotPlayer);
        }
        com.xpeak.enderite.robot.RobotServer.setInput(robotPlayer, false, false);
        com.xpeak.enderite.robot.RobotServer.tick(robotPlayer);
        check("motosierra: hace dano constante", dummy.getHealth() < before - 6.0F || !dummy.isAlive(),
                String.format("vida %.1f -> %.1f", before, dummy.getHealth()));
        dummy.discard();
    }

    // ------------------------------------------------------------------ cohetes
    private static Zombie rocketTarget;
    private static float rocketTargetHealth;

    private static void startRocketCheck() {
        ServerLevel ow = server.overworld();
        ItemStack torso = robotPlayer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        ItemStack arm = com.xpeak.enderite.robot.RobotHelper.getArm(torso, net.minecraft.world.entity.HumanoidArm.RIGHT);
        com.xpeak.enderite.robot.RobotHelper.setAmmo(arm, 9);
        com.xpeak.enderite.robot.RobotHelper.setArm(torso, net.minecraft.world.entity.HumanoidArm.RIGHT, arm);
        BlockPos base = robotPlayer.blockPosition();
        for (int dz = 1; dz < 14; dz++) for (int dy = 0; dy < 4; dy++) for (int dx = -2; dx <= 2; dx++)
            ow.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
        rocketTarget = EntityType.ZOMBIE.create(ow);
        rocketTarget.moveTo(robotPlayer.getX() - 0.38D, robotPlayer.getY(), robotPlayer.getZ() + 9.0D, 180.0F, 0);
        rocketTarget.setNoAi(true);
        rocketTarget.setPersistenceRequired();
        ow.addFreshEntity(rocketTarget);
        rocketTargetHealth = rocketTarget.getHealth();
        robotPlayer.setXRot(2.0F);
        com.xpeak.enderite.robot.RobotServer.setInput(robotPlayer, false, true);
        robotPlayer.tickCount = 200;
        com.xpeak.enderite.robot.RobotServer.tick(robotPlayer);
        com.xpeak.enderite.robot.RobotServer.setInput(robotPlayer, false, false);
        robotPlayer.tickCount = 201;
        com.xpeak.enderite.robot.RobotServer.tick(robotPlayer);
        int rockets = 0;
        for (var e : ow.getAllEntities()) if (e instanceof com.xpeak.enderite.robot.RobotRocketEntity) rockets++;
        ItemStack armAfter = com.xpeak.enderite.robot.RobotHelper.getArm(torso, net.minecraft.world.entity.HumanoidArm.RIGHT);
        check("lanzacohetes: dispara 3 cohetes", rockets == 3, rockets + " cohetes");
        check("lanzacohetes: gasta 3 de municion", com.xpeak.enderite.robot.RobotHelper.getAmmo(armAfter) == 6,
                "municion=" + com.xpeak.enderite.robot.RobotHelper.getAmmo(armAfter));
    }

    private static void endRocketCheck() {
        float hp = rocketTarget.isAlive() ? rocketTarget.getHealth() : 0.0F;
        check("cohetes: mucho mas dano que un cohete normal", rocketTargetHealth - hp >= 12.0F,
                String.format("vida %.1f -> %.1f", rocketTargetHealth, hp));
        rocketTarget.discard();
    }

    // ------------------------------------------------------------------ imanes
    private static net.minecraft.world.entity.item.ItemEntity itemFree, itemWalled, itemPortable;
    private static BlockPos magnetPos;
    private static FakePlayer magnetHolder;
    private static ItemStack magnetStack;

    private static void startMagnetCheck() {
        ServerLevel ow = server.overworld();
        int bx = 700, bz = 700;
        for (int cx = (bx - 32) >> 4; cx <= (bx + 32) >> 4; cx++) for (int cz = (bz - 32) >> 4; cz <= (bz + 32) >> 4; cz++) ow.setChunkForced(cx, cz, true);
        int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) + 1;
        for (int dx = -26; dx <= 26; dx++) for (int dz = -3; dz <= 3; dz++) {
            ow.setBlock(new BlockPos(bx + dx, y - 1, bz + dz), Blocks.STONE.defaultBlockState(), 3);
            for (int dy = 0; dy < 5; dy++) ow.setBlock(new BlockPos(bx + dx, y + dy, bz + dz), Blocks.AIR.defaultBlockState(), 3);
        }
        magnetPos = new BlockPos(bx, y, bz);
        ow.setBlock(magnetPos, com.xpeak.enderite.registry.ModBlocks.MAGNET.get().defaultBlockState(), 3);
        ow.setBlock(magnetPos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        // pared que tapa el lado oeste
        for (int dz = -3; dz <= 3; dz++) for (int dy = 0; dy < 5; dy++) ow.setBlock(new BlockPos(bx - 8, y + dy, bz + dz), Blocks.STONE.defaultBlockState(), 3);
        itemFree = new net.minecraft.world.entity.item.ItemEntity(ow, bx + 20.5D, y + 0.1D, bz + 0.5D, new ItemStack(Items.DIAMOND));
        itemWalled = new net.minecraft.world.entity.item.ItemEntity(ow, bx - 15.5D, y + 0.1D, bz + 0.5D, new ItemStack(Items.EMERALD));
        itemFree.setDeltaMovement(0, 0, 0);
        itemWalled.setDeltaMovement(0, 0, 0);
        ow.addFreshEntity(itemFree);
        ow.addFreshEntity(itemWalled);
        check("iman: se enciende con redstone", ow.getBlockState(magnetPos).getValue(com.xpeak.enderite.magnet.MagnetBlock.POWERED), "");
    }

    private static void endMagnetCheck() {
        Vec3 c = Vec3.atCenterOf(magnetPos);
        double dFree = itemFree.position().distanceTo(c), dWall = itemWalled.position().distanceTo(c);
        check("iman: atrae objetos a 20 bloques", dFree < 3.0D, String.format("distancia=%.1f", dFree));
        check("iman: no atrae a traves de paredes", dWall > 14.0D, String.format("distancia=%.1f", dWall));
        // iman portatil
        ServerLevel ow = server.overworld();
        magnetHolder = FakePlayerFactory.get(ow, new GameProfile(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "[EnderiteIman]"));
        magnetHolder.getInventory().clearContent();
        magnetStack = new ItemStack(com.xpeak.enderite.registry.ModItems.PORTABLE_MAGNET.get());
        com.xpeak.enderite.magnet.PortableMagnetItem.setActive(magnetStack, true);
        magnetHolder.getInventory().add(magnetStack);
        magnetHolder.moveTo(magnetPos.getX() + 14.5D, magnetPos.getY(), magnetPos.getZ() + 0.5D, 0, 0);
        ow.setBlock(magnetPos.below(), Blocks.STONE.defaultBlockState(), 3); // apagar el bloque
        itemPortable = new net.minecraft.world.entity.item.ItemEntity(ow, magnetHolder.getX() + 12.0D, magnetHolder.getY() + 0.1D, magnetHolder.getZ(), new ItemStack(Items.GOLD_INGOT));
        ow.addFreshEntity(itemPortable);
        portableTicks = 80;
    }

    private static int portableTicks;

    private static void tickPortable() {
        ServerLevel ow = server.overworld();
        magnetHolder.getInventory().getItem(0).inventoryTick(ow, magnetHolder, 0, true);
        if (--portableTicks == 0) {
            double dp = itemPortable.position().distanceTo(magnetHolder.position());
            check("iman portatil: atrae objetos hacia ti", dp < 6.0D || !itemPortable.isAlive(), String.format("distancia=%.1f", dp));
            itemFree.discard();
            itemWalled.discard();
            itemPortable.discard();
        }
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
