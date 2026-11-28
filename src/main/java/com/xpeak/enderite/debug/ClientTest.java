package com.xpeak.enderite.debug;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.cling.WallCling;
import com.xpeak.enderite.client.ClientLimboState;
import com.xpeak.enderite.entity.CrawlerEntity;
import com.xpeak.enderite.entity.StuckToolEntity;
import com.xpeak.enderite.limbo.Limbo;
import com.xpeak.enderite.limbo.LimboManager;
import com.xpeak.enderite.network.ModNetwork;
import com.xpeak.enderite.nightmare.NightmareData;
import com.xpeak.enderite.registry.ModBlocks;
import com.xpeak.enderite.registry.ModEntities;
import com.xpeak.enderite.registry.ModItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Prueba automatica en un cliente real (solo con -Denderite.clienttest=true): monta escenas, hace capturas
 * y comprueba el Limbo, el Crawler, la armadura y el agarre a paredes. Al terminar cierra el juego.
 */
public final class ClientTest {
    public static final boolean ENABLED = Boolean.getBoolean("enderite.clienttest");

    private static final List<String> LOG = new ArrayList<>();
    private static int passed, failed;
    private static int t = -1;
    private static int idleTicks;
    private static int waitStart;
    private static int step;
    private static BlockPos base;
    private static BlockPos bed;
    private static double quickY, hangY;
    private static volatile int serverPhase = -99;

    private static void check(String name, boolean ok, String detail) {
        if (ok) passed++;
        else failed++;
        String line = (ok ? "PASS " : "FAIL ") + name + (detail.isEmpty() ? "" : " -> " + detail);
        LOG.add(line);
        EnderiteMod.LOGGER.info("[CLIENTTEST] {}", line);
    }

    private static void note(String s) {
        LOG.add("INFO " + s);
        EnderiteMod.LOGGER.info("[CLIENTTEST] {}", s);
    }

    private static void server(Consumer<ServerPlayer> action) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer srv = mc.getSingleplayerServer();
        if (srv == null || mc.player == null) return;
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) {
                try {
                    action.accept(sp);
                } catch (Throwable e) {
                    check("excepcion en servidor (paso " + step + ")", false, e.toString());
                    EnderiteMod.LOGGER.error("[CLIENTTEST]", e);
                }
            }
        });
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {});
        note("captura " + name);
    }

    private static void look(Vec3 target) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        Vec3 d = target.subtract(p.getEyePosition());
        float yaw = (float) (Math.atan2(d.z, d.x) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180.0D / Math.PI));
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        idleTicks++;
        if (idleTicks % 400 == 0) {
            EnderiteMod.LOGGER.info("[CLIENTTEST] esperando... pantalla={} jugador={} paso={} t={}",
                    mc.screen == null ? "ninguna" : mc.screen.getClass().getName(), mc.player != null, step, t);
        }
        if (t < 0 && mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen confirm && idleTicks % 40 == 0) {
            // Aviso de "ajustes experimentales" (sale una vez en mundos con dimensiones nuevas): pulsar "Proceder"
            for (var child : confirm.children()) {
                if (child instanceof net.minecraft.client.gui.components.Button b) {
                    note("pulsando '" + b.getMessage().getString() + "' en el aviso de ajustes experimentales");
                    b.onPress();
                    break;
                }
            }
        }
        if (t < 0 && idleTicks > 20 * 60 * 8) {
            check("el mundo de prueba carga", false, "pantalla=" + (mc.screen == null ? "ninguna" : mc.screen.getClass().getName()));
            finish(mc);
            return;
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (t < 0) {
            t = 0;
            note("mundo cargado");
        }
        t++;
        try {
            run(mc);
        } catch (Throwable e) {
            check("excepcion en cliente (paso " + step + ")", false, e.toString());
            EnderiteMod.LOGGER.error("[CLIENTTEST]", e);
            finish(mc);
        }
        if (t > 20 * 60 * 12) {
            check("tiempo maximo de la prueba", false, "paso " + step);
            finish(mc);
        }
    }

    private static boolean after(int ticks) {
        if (t - waitStart >= ticks) {
            waitStart = t;
            return true;
        }
        return false;
    }

    private static void next() {
        step++;
        waitStart = t;
    }

    private static void run(Minecraft mc) {
        switch (step) {
            case 0 -> {
                if (!after(60)) return;
                server(sp -> {
                    ServerLevel ow = sp.serverLevel();
                    ow.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, sp.server);
                    ow.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, sp.server);
                    ow.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, sp.server);
                    ow.setWeatherParameters(6000, 0, false, false);
                    ow.setDayTime(6000);
                    sp.setGameMode(GameType.SURVIVAL);
                    int x = sp.getBlockX(), z = sp.getBlockZ();
                    int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
                    base = new BlockPos(x, y, z);
                    // Escenario plano
                    for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 12; dz++) {
                        ow.setBlock(base.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                        for (int dy = 0; dy < 8; dy++) ow.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                    }
                    BlockState[] row = {
                            ModBlocks.ENDERITE_ORE.get().defaultBlockState(), ModBlocks.ENDERITE_BLOCK.get().defaultBlockState(),
                            ModBlocks.ENDERITE_BRICKS.get().defaultBlockState(), ModBlocks.CHISELED_ENDERITE_BRICKS.get().defaultBlockState(),
                            ModBlocks.ENDERITE_BRICK_STAIRS.get().defaultBlockState(),
                            ModBlocks.ENDERITE_BRICK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)};
                    for (int i = 0; i < row.length; i++) {
                        ow.setBlock(base.offset(-5 + i * 2, 0, 5), row[i], 3);
                    }
                    ow.setBlock(base.offset(-5, 1, 5), Blocks.END_STONE.defaultBlockState(), 3);
                    sp.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.ENDERITE_HELMET.get()));
                    sp.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.ENDERITE_CHESTPLATE.get()));
                    sp.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.ENDERITE_LEGGINGS.get()));
                    sp.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.ENDERITE_BOOTS.get()));
                    sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ENDERITE_SWORD.get()));
                    sp.connection.teleport(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0.0F, 20.0F);
                });
                next();
            }
            case 1 -> {
                if (!after(80)) return;
                server(sp -> check("armadura completa = 30 (tope del juego)", sp.getArmorValue() >= 30, "" + sp.getArmorValue()));
                look(Vec3.atCenterOf(base.offset(0, 0, 5)));
                next();
            }
            case 2 -> {
                if (!after(30)) return;
                shot("01_bloques_y_espada");
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                next();
            }
            case 3 -> {
                if (!after(40)) return;
                shot("02_armadura");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(sp -> {
                    Item[] items = {ModItems.ENDERITE_SCRAP.get(), ModItems.ENDERITE_INGOT.get(), ModItems.ENDERITE_UPGRADE_SMITHING_TEMPLATE.get(),
                            ModItems.ENDERITE_PICKAXE.get(), ModItems.ENDERITE_AXE.get(), ModItems.ENDERITE_SHOVEL.get(), ModItems.ENDERITE_HOE.get(),
                            ModItems.ENDERITE_ORE.get(), ModItems.ENDERITE_BLOCK.get(), ModItems.ENDERITE_BRICKS.get(),
                            ModItems.CHISELED_ENDERITE_BRICKS.get(), ModItems.ENDERITE_BRICK_STAIRS.get(), ModItems.ENDERITE_BRICK_SLAB.get(),
                            ModItems.CRAWLER_SPAWN_EGG.get(), ModItems.ENDERITE_HELMET.get(), ModItems.ENDERITE_CHESTPLATE.get(),
                            ModItems.ENDERITE_LEGGINGS.get(), ModItems.ENDERITE_BOOTS.get()};
                    for (Item it : items) sp.getInventory().add(new ItemStack(it));
                });
                next();
            }
            case 4 -> {
                if (!after(20)) return;
                mc.setScreen(new InventoryScreen(mc.player));
                next();
            }
            case 5 -> {
                if (!after(30)) return;
                shot("03_inventario");
                mc.setScreen(null);
                server(sp -> {
                    ServerLevel ow = sp.serverLevel();
                    CrawlerEntity c = ModEntities.CRAWLER.get().create(ow);
                    c.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 4.5, 180.0F, 0.0F);
                    c.setYHeadRot(180.0F);
                    c.setYBodyRot(180.0F);
                    c.setNoAi(true);
                    ow.addFreshEntity(c);
                    CrawlerEntity c2 = ModEntities.CRAWLER.get().create(ow);
                    c2.moveTo(base.getX() + 3.0, base.getY(), base.getZ() + 6.0, 210.0F, 0.0F);
                    c2.setYHeadRot(210.0F);
                    c2.setYBodyRot(210.0F);
                    c2.setNoAi(true);
                    c2.setAggressive(true);
                    ow.addFreshEntity(c2);
                    sp.connection.teleport(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0.0F, -15.0F);
                });
                next();
            }
            case 6 -> {
                if (!after(40)) return;
                look(new Vec3(base.getX() + 1.2, base.getY() + 2.2, base.getZ() + 5.0));
                next();
            }
            case 7 -> {
                if (!after(20)) return;
                shot("04_crawler_dia");
                server(sp -> sp.serverLevel().setDayTime(18000));
                next();
            }
            case 8 -> {
                if (!after(60)) return;
                shot("05_crawler_noche");
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                next();
            }
            case 9 -> {
                if (!after(30)) return;
                shot("06_crawler_noche_cerca");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(sp -> {
                    ServerLevel ow = sp.serverLevel();
                    ow.setDayTime(6000);
                    for (Entity e : ow.getAllEntities()) if (e instanceof CrawlerEntity) e.discard();
                    // Pared para el agarre en x = base-6, cara este hacia el jugador
                    for (int dy = 0; dy < 8; dy++) for (int dz = -2; dz <= 4; dz++)
                        ow.setBlock(base.offset(-6, dy, dz), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                    sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ENDERITE_PICKAXE.get()));
                });
                next();
            }
            case 10 -> {
                if (!after(20)) return;
                server(sp -> {
                    // Simula: estas en el aire pegado a la pared, mirando a lo largo de ella
                    sp.teleportTo(base.getX() - 4.69, base.getY() + 1.6, base.getZ() + 0.5);
                    sp.setYRot(0.0F);
                    sp.setOnGround(false);
                    WallCling.Spot spot = WallCling.findSpot(sp, Direction.EAST, base.offset(-6, 3, 0));
                    check("agarre (cliente): encuentra la pared", spot != null, String.valueOf(spot));
                    if (spot != null) {
                        boolean ok = WallCling.doCling(sp, InteractionHand.MAIN_HAND, spot);
                        check("agarre (cliente): cuelga del pico", ok && sp.getVehicle() instanceof StuckToolEntity, "" + sp.getVehicle());
                    }
                });
                next();
            }
            case 11 -> {
                if (!after(30)) return;
                mc.player.setYRot(0.0F);
                mc.player.setXRot(10.0F);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                next();
            }
            case 12 -> {
                if (!after(25)) return;
                check("agarre (cliente): el cliente ve que cuelgas", mc.player.getVehicle() instanceof StuckToolEntity, "" + mc.player.getVehicle());
                shot("07_agarre_pared");
                mc.player.setYRot(-60.0F);
                next();
            }
            case 13 -> {
                if (!after(15)) return;
                shot("08_agarre_pared_2");
                ModNetwork.sendClingJump();
                next();
            }
            case 14 -> {
                if (!after(4)) return;
                check("agarre (cliente): salto extra", mc.player.getVehicle() == null && mc.player.getY() > base.getY() + 1.6,
                        String.format("y=%.2f base=%d", mc.player.getY(), base.getY()));
                shot("09_salto_extra");
                next();
            }
            case 15 -> {
                if (!after(30)) return;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(sp -> {
                    StuckToolEntity tool = null;
                    for (Entity e : sp.serverLevel().getAllEntities()) if (e instanceof StuckToolEntity s) tool = s;
                    check("agarre (cliente): el pico sigue clavado", tool != null, "");
                    if (tool != null) {
                        tool.interact(sp, InteractionHand.MAIN_HAND);
                        check("agarre (cliente): recuperas el pico", sp.getMainHandItem().is(ModItems.ENDERITE_PICKAXE.get()),
                                "" + sp.getMainHandItem() + " dano=" + sp.getMainHandItem().getDamageValue());
                    }
                    // Casita con cama para el Limbo
                    ServerLevel ow = sp.serverLevel();
                    BlockPos h = base.offset(3, 0, -6);
                    for (int dx = 0; dx < 7; dx++) for (int dz = 0; dz < 6; dz++) for (int dy = 0; dy < 4; dy++) {
                        boolean wall = dx == 0 || dz == 0 || dx == 6 || dz == 5;
                        ow.setBlock(h.offset(dx, dy, dz), wall ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                    }
                    for (int dx = 0; dx < 7; dx++) for (int dz = 0; dz < 6; dz++) ow.setBlock(h.offset(dx, 4, dz), Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3);
                    ow.setBlock(h.offset(3, 1, 0), Blocks.GLASS.defaultBlockState(), 3);
                    ow.setBlock(h.offset(6, 1, 3), Blocks.GLASS.defaultBlockState(), 3);
                    ow.setBlock(h.offset(1, 2, 1), Blocks.WALL_TORCH.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.SOUTH), 3);
                    ow.setBlock(h.offset(5, 0, 4), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
                    ow.setBlock(h.offset(1, 0, 4), Blocks.CHEST.defaultBlockState(), 3);
                    bed = h.offset(3, 0, 3);
                    BlockState foot = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT);
                    ow.setBlock(bed.south(), foot, 3);
                    ow.setBlock(bed, foot.setValue(BedBlock.PART, BedPart.HEAD), 3);
                    ow.setDayTime(14000);
                    ow.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, sp.server);
                    sp.setRespawnPosition(ow.dimension(), bed, 0.0F, true, false);
                    sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 16));
                    sp.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    sp.connection.teleport(bed.getX() + 0.5, bed.getY() + 0.6, bed.getZ() + 1.5, 180.0F, 0.0F);
                });
                next();
            }
            case 16 -> {
                if (!after(40)) return;
                server(sp -> {
                    sp.startSleepInBed(bed);
                });
                next();
            }
            case 17 -> {
                if (!after(10)) return;
                check("el jugador esta durmiendo", mc.player.isSleeping(), "");
                server(sp -> {
                    LimboManager.forceNextSleep(sp);
                });
                next();
            }
            case 18 -> {
                // espera a que el 1% (forzado) lo mande al Limbo al terminar de dormirse
                if (Limbo.isLimbo(mc.level)) {
                    check("dormir te lleva al Limbo", true, "tras " + (t - waitStart) + " ticks");
                    next();
                } else if (t - waitStart > 400) {
                    check("dormir te lleva al Limbo", false, "no entro");
                    finish(mc);
                }
            }
            case 19 -> {
                if (!after(10)) return;
                shot("10_limbo_cargando");
                next();
            }
            case 20 -> {
                // Se espera a que el SERVIDOR pase de "cargando" a "activo" (el cliente puede ir por delante si el servidor va lento)
                if (t % 5 == 0) server(sp -> {
                    LimboManager.Session ses = LimboManager.getSession(sp);
                    serverPhase = ses == null ? -1 : ses.phase();
                });
                if (serverPhase == Limbo.PHASE_ACTIVE && ClientLimboState.phase() == Limbo.PHASE_ACTIVE) {
                    check("el Limbo termina de copiar el mundo", true, "tras " + (t - waitStart) + " ticks");
                    serverPhase = -99;
                    next();
                } else if (t - waitStart > 900) {
                    check("el Limbo termina de copiar el mundo", false, "sigue cargando");
                    next();
                }
            }
            case 21 -> {
                if (!after(70)) return;
                server(sp -> {
                    ServerLevel limbo = sp.serverLevel();
                    check("en el Limbo", Limbo.isLimbo(limbo), "");
                    check("la cama existe en la copia", limbo.getBlockState(bed).getBlock() instanceof BedBlock, "" + limbo.getBlockState(bed));
                    check("la casa existe en la copia", limbo.getBlockState(bed.offset(0, 4, 0)).is(Blocks.DARK_OAK_PLANKS),
                            "" + limbo.getBlockState(bed.offset(0, 4, 0)));
                    check("apareces de pie sobre la cama", sp.position().distanceTo(Vec3.atBottomCenterOf(bed)) < 1.2D && !sp.isSleeping(),
                            String.format("%.2f", sp.position().distanceTo(Vec3.atBottomCenterOf(bed))));
                    int others = 0;
                    for (Entity e : limbo.getAllEntities()) if (!(e instanceof Player) && !(e instanceof CrawlerEntity)) others++;
                    check("no hay otras entidades en el Limbo", others == 0, others + " entidades");
                });
                look(Vec3.atCenterOf(bed.north(2)));
                next();
            }
            case 22 -> {
                if (!after(20)) return;
                shot("11_limbo_casa");
                // Intentar colocar un bloque y abrir un cofre (no debe poder)
                BlockPos floor = bed.offset(-1, -1, -1);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
                next();
            }
            case 23 -> {
                if (!after(10)) return;
                server(sp -> {
                    BlockState s = sp.serverLevel().getBlockState(bed.offset(-1, 0, -1));
                    check("no puedes colocar bloques en el Limbo", s.isAir(), "" + s);
                    check("no puedes correr", !sp.isSprinting(), "");
                    // A la puerta de casa: abrir hueco para que el crawler entre
                    LimboManager.spawnCrawlerNow(sp, 9.0D, 13.0D);
                });
                next();
            }
            case 24 -> {
                if (!after(60)) return;
                Entity crawler = null;
                for (Entity e : mc.level.entitiesForRendering()) if (e instanceof CrawlerEntity) crawler = e;
                server(sp -> {
                    int n = 0;
                    for (Entity e : sp.serverLevel().getAllEntities()) if (e instanceof CrawlerEntity) n++;
                    check("el Crawler aparece en el Limbo", n == 1, n + " crawler(s)");
                });
                if (crawler != null) {
                    note(String.format("crawler a %.1f bloques", crawler.distanceTo(mc.player)));
                    look(crawler.position().add(0, 2.0, 0));
                }
                next();
            }
            case 25 -> {
                if (!after(5)) return;
                shot("12_limbo_crawler");
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                next();
            }
            case 26 -> {
                if (!after(200)) return;
                shot("13_limbo_crawler_tercera");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(sp -> {
                    CrawlerEntity c = null;
                    for (Entity e : sp.serverLevel().getAllEntities()) if (e instanceof CrawlerEntity ce) c = ce;
                    note(String.format("tras 10 s: vida jugador=%.1f, crawler a %.1f bloques",
                            sp.getHealth(), c == null ? -1.0F : c.distanceTo(sp)));
                    check("el Crawler persigue al jugador", c != null && (c.distanceTo(sp) < 6.0F || sp.getHealth() < 20.0F),
                            c == null ? "sin crawler" : String.format("%.1f", c.distanceTo(sp)));
                    sp.setHealth(20.0F);
                });
                next();
            }
            case 27 -> {
                if (!after(20)) return;
                server(sp -> {
                    int before = NightmareData.get(sp.server).getLevel();
                    LimboManager.leaveLimbo(sp, true);
                    check("al amanecer vuelves a tu cama real", !Limbo.isLimbo(sp.level())
                            && sp.position().distanceTo(Vec3.atBottomCenterOf(bed)) < 2.0D, sp.position().toString());
                    check("sube la Pesadilla", NightmareData.get(sp.server).getLevel() == before + 1,
                            before + " -> " + NightmareData.get(sp.server).getLevel());
                    int crawlers = 0;
                    ServerLevel limbo = sp.server.getLevel(Limbo.LIMBO);
                    for (Entity e : limbo.getAllEntities()) if (e instanceof CrawlerEntity) crawlers++;
                    check("el Crawler desaparece al salir", crawlers == 0, crawlers + "");
                });
                next();
            }
            case 28 -> {
                if (!after(80)) return;
                shot("14_de_vuelta");
                check("el cliente ya no esta en el Limbo", !ClientLimboState.inLimbo(), "");
                // Prueba de muerte en el Limbo
                server(sp -> {
                    sp.getInventory().add(new ItemStack(Items.DIAMOND, 32));
                    sp.giveExperienceLevels(10);
                    LimboManager.enterLimbo(sp, sp.blockPosition(), false);
                });
                next();
            }
            case 29 -> {
                if (!after(140)) return;
                server(sp -> {
                    check("segunda entrada al Limbo", Limbo.isLimbo(sp.level()), "");
                    sp.hurt(sp.damageSources().fellOutOfWorld(), 1000.0F);
                });
                next();
            }
            case 30 -> {
                if (!after(40)) return;
                shot("15_muerte_limbo");
                if (mc.player.isDeadOrDying() || mc.player.getHealth() <= 0) mc.player.respawn();
                next();
            }
            case 31 -> {
                if (!after(80)) return;
                server(sp -> {
                    boolean empty = sp.getInventory().isEmpty();
                    check("al morir en el Limbo pierdes tus cosas", empty && sp.experienceLevel == 0,
                            "vacio=" + empty + " nivel=" + sp.experienceLevel);
                    check("y apareces en el mundo real", !Limbo.isLimbo(sp.level()), sp.level().dimension().location().toString());
                });
                next();
            }
            case 32 -> {
                if (!after(40)) return;
                shot("16_tras_morir");
                // ---------------- Armadura robot
                server(sp -> {
                    ServerLevel ow = sp.serverLevel();
                    ow.setDayTime(6000);
                    ow.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, sp.server);
                    ItemStack torso = new ItemStack(ModItems.ROBOT_TORSO_BLUE.get());
                    com.xpeak.enderite.robot.RobotHelper.setArm(torso, net.minecraft.world.entity.HumanoidArm.LEFT, new ItemStack(ModItems.ROBOT_ARM_CHAINSAW.get()));
                    ItemStack rocket = new ItemStack(ModItems.ROBOT_ARM_ROCKET.get());
                    com.xpeak.enderite.robot.RobotHelper.setAmmo(rocket, 30);
                    com.xpeak.enderite.robot.RobotHelper.setArm(torso, net.minecraft.world.entity.HumanoidArm.RIGHT, rocket);
                    sp.setItemSlot(EquipmentSlot.CHEST, torso);
                    sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
                    sp.connection.teleport(base.getX() + 0.5, base.getY(), base.getZ() - 4.5, 0.0F, 5.0F);
                    for (Entity e : ow.getAllEntities()) if (e instanceof net.minecraft.world.entity.monster.Zombie) e.discard();
                    var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(ow);
                    z.moveTo(base.getX() + 0.5, base.getY(), base.getZ() - 2.6, 180.0F, 0.0F);
                    z.setNoAi(true);
                    ow.addFreshEntity(z);
                });
                next();
            }
            case 33 -> {
                if (!after(40)) return;
                check("robot: la hotbar se sustituye por los brazos", com.xpeak.enderite.robot.RobotHelper.hasArms(mc.player), "");
                check("robot azul: mas velocidad", mc.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) > 0.14D,
                        String.format("%.3f", mc.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)));
                shot("17_robot_primera_persona");
                mc.player.setYRot(90.0F);
                mc.player.setXRot(10.0F);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                next();
            }
            case 34 -> {
                if (!after(30)) return;
                shot("18_robot_tercera_persona");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.player.setYRot(0.0F);
                mc.player.setXRot(5.0F);
                mc.options.keyAttack.setDown(true);
                next();
            }
            case 35 -> {
                if (!after(25)) return;
                shot("19_motosierra");
                next();
            }
            case 36 -> {
                if (!after(15)) return;
                mc.options.keyAttack.setDown(false);
                server(sp -> {
                    float hp = 20.0F;
                    boolean found = false;
                    for (Entity e : sp.serverLevel().getAllEntities())
                        if (e instanceof net.minecraft.world.entity.monster.Zombie zz) { hp = zz.isAlive() ? zz.getHealth() : 0; found = true; }
                    check("motosierra (clic izquierdo de verdad): hace dano", !found || hp < 14.0F, String.format("vida=%.1f", hp));
                    // un objeto en la mano no se debe haber usado (la espada no ataca)
                    check("con brazos no usas la espada de la hotbar", sp.getMainHandItem().getDamageValue() == 0, "");
                });
                mc.player.setXRot(-2.0F);
                mc.options.keyUse.setDown(true);
                next();
            }
            case 37 -> {
                if (!after(3)) return;
                mc.options.keyUse.setDown(false);
                next();
            }
            case 38 -> {
                if (!after(4)) return;
                shot("20_cohetes");
                server(sp -> {
                    ItemStack arm = com.xpeak.enderite.robot.RobotHelper.getArm(com.xpeak.enderite.robot.RobotHelper.getTorso(sp), net.minecraft.world.entity.HumanoidArm.RIGHT);
                    check("lanzacohetes (clic derecho de verdad): dispara 3", com.xpeak.enderite.robot.RobotHelper.getAmmo(arm) == 27,
                            "municion=" + com.xpeak.enderite.robot.RobotHelper.getAmmo(arm));
                });
                mc.setScreen(new InventoryScreen(mc.player));
                next();
            }
            case 39 -> {
                if (!after(25)) return;
                shot("21_inventario_robot");
                mc.setScreen(null);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(sp -> {
                    sp.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                    ServerLevel ow = sp.serverLevel();
                    ow.setBlock(base.offset(2, 0, -6), ModBlocks.ENGINEERING_TABLE.get().defaultBlockState(), 3);
                    if (ow.getBlockEntity(base.offset(2, 0, -6)) instanceof com.xpeak.enderite.robot.EngineeringTableBlockEntity be) {
                        ItemStack t = new ItemStack(ModItems.ROBOT_TORSO_RED.get());
                        com.xpeak.enderite.robot.RobotHelper.setArm(t, net.minecraft.world.entity.HumanoidArm.LEFT, new ItemStack(ModItems.ROBOT_ARM_ROCKET.get()));
                        t.setDamageValue(200);
                        be.getContainer().setItem(0, t);
                        be.getContainer().setItem(1, new ItemStack(Items.FIREWORK_ROCKET, 5));
                    }
                    sp.getInventory().add(new ItemStack(Items.IRON_INGOT, 20));
                    sp.getInventory().add(new ItemStack(Items.REDSTONE, 10));
                    sp.connection.teleport(base.getX() + 2.5, base.getY(), base.getZ() - 3.5, 180.0F, 30.0F);
                });
                next();
            }
            case 40 -> {
                if (!after(20)) return;
                BlockPos table = base.offset(2, 0, -6);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(table).add(0, 0.5, 0), Direction.UP, table, false));
                next();
            }
            case 41 -> {
                if (!after(30)) return;
                check("la Mesa de ingenieria se abre", mc.screen instanceof com.xpeak.enderite.client.EngineeringTableScreen,
                        mc.screen == null ? "ninguna" : mc.screen.getClass().getSimpleName());
                shot("22_mesa_ingenieria");
                mc.setScreen(null);
                // ---------------- Arenas movedizas
                server(sp -> {
                    ServerLevel ow = sp.serverLevel();
                    BlockPos q = base.offset(-3, 0, 6);
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                        ow.setBlock(q.offset(dx, -1, dz), ModBlocks.QUICKSAND.get().defaultBlockState(), 3);
                        ow.setBlock(q.offset(dx, -2, dz), ModBlocks.QUICKSAND.get().defaultBlockState(), 3);
                        ow.setBlock(q.offset(dx, -3, dz), Blocks.STONE.defaultBlockState(), 3);
                    }
                    for (int dx = -2; dx <= 2; dx++) ow.setBlock(q.offset(dx, -1, 2), Blocks.SAND.defaultBlockState(), 3);
                    sp.connection.teleport(q.getX() + 0.5, q.getY() + 0.2, q.getZ() + 0.5, 180.0F, 20.0F);
                });
                next();
            }
            case 42 -> {
                if (!after(50)) return;
                double sink = base.getY() - mc.player.getY();
                check("arenas movedizas: te hundes", sink > 0.4D, String.format("hundido=%.2f", sink));
                shot("23_arenas_movedizas");
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                next();
            }
            case 43 -> {
                if (!after(15)) return;
                shot("24_arenas_movedizas_fuera");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                quickY = mc.player.getY();
                mc.options.keyJump.setDown(true);
                next();
            }
            case 44 -> {
                if (!after(40)) return;
                mc.options.keyJump.setDown(false);
                check("arenas movedizas: manteniendo saltar sales", mc.player.getY() > quickY + 0.4D,
                        String.format("y %.2f -> %.2f", quickY, mc.player.getY()));
                // ---------------- Iman
                server(sp -> {
                    ServerLevel ow = sp.serverLevel();
                    BlockPos m = base.offset(6, 0, 0);
                    ow.setBlock(m, ModBlocks.MAGNET.get().defaultBlockState(), 3);
                    ow.setBlock(m.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                    Item[] drops = {Items.DIAMOND, Items.GOLD_INGOT, Items.EMERALD, Items.IRON_INGOT, Items.APPLE};
                    for (int i = 0; i < drops.length; i++) {
                        var it = new net.minecraft.world.entity.item.ItemEntity(ow, m.getX() + 0.5 - 5 + i * 1.8, m.getY() + 0.2, m.getZ() + 7.5,
                                new ItemStack(drops[i]));
                        it.setPickUpDelay(400);
                        ow.addFreshEntity(it);
                    }
                    sp.connection.teleport(m.getX() + 0.5, m.getY(), m.getZ() + 12.5, 180.0F, 20.0F);
                });
                next();
            }
            case 45 -> {
                if (!after(18)) return;
                shot("25_iman_atrayendo");
                next();
            }
            case 46 -> {
                if (!after(50)) return;
                shot("26_iman");
                server(sp -> {
                    BlockPos m = base.offset(6, 0, 0);
                    int near = 0;
                    for (Entity e : sp.serverLevel().getAllEntities())
                        if (e instanceof net.minecraft.world.entity.item.ItemEntity ie && ie.position().distanceTo(Vec3.atCenterOf(m)) < 2.5D) near++;
                    check("iman: los objetos llegan al iman", near >= 4, near + "/5");
                    sp.serverLevel().setBlock(m.below(), Blocks.STONE.defaultBlockState(), 3);
                    // ---------------- Salto desde la pared con la TECLA de saltar
                    sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                    sp.teleportTo(base.getX() - 4.69, base.getY() + 1.6, base.getZ() + 0.5);
                    sp.setOnGround(false);
                    WallCling.Spot spot = WallCling.findSpot(sp, Direction.EAST, base.offset(-6, 3, 0));
                    if (spot != null) WallCling.doCling(sp, InteractionHand.MAIN_HAND, spot);
                });
                next();
            }
            case 47 -> {
                if (!after(20)) return;
                check("agarre: colgado otra vez", mc.player.getVehicle() instanceof StuckToolEntity, "" + mc.player.getVehicle());
                hangY = mc.player.getY();
                mc.options.keyJump.setDown(true);
                next();
            }
            case 48 -> {
                note(String.format("tecla saltar t=%d y=%.2f vel=%s vehiculo=%s", t - waitStart, mc.player.getY(), mc.player.getDeltaMovement(), mc.player.getVehicle()));
                if (!after(3)) return;
                mc.options.keyJump.setDown(false);
                next();
            }
            case 49 -> {
                note(String.format("tras soltar t=%d y=%.2f vel=%s vehiculo=%s", t - waitStart, mc.player.getY(), mc.player.getDeltaMovement(), mc.player.getVehicle()));
                if (!after(5)) return;
                check("agarre: SALTAS pulsando la tecla de saltar", mc.player.getVehicle() == null && mc.player.getY() > hangY + 0.5D,
                        String.format("vehiculo=%s y %.2f -> %.2f", mc.player.getVehicle(), hangY, mc.player.getY()));
                next();
            }
            case 50 -> {
                if (!after(30)) return;
                finish(mc);
            }
            default -> {
            }
        }
    }

    private static void finish(Minecraft mc) {
        if (step >= 1000) return;
        step = 1000;
        LOG.add(0, "RESUMEN: " + passed + " OK, " + failed + " FALLOS");
        try {
            Files.write(new File(mc.gameDirectory, "enderite-clienttest.txt").toPath(), LOG, StandardCharsets.UTF_8);
        } catch (IOException e) {
            EnderiteMod.LOGGER.error("No se pudo escribir el resultado", e);
        }
        EnderiteMod.LOGGER.info("[CLIENTTEST] TERMINADO: {} OK, {} FALLOS", passed, failed);
        mc.stop();
    }

    private ClientTest() {}
}
