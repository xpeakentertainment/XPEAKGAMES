package com.xpeak.enderite.robot;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.network.ModNetwork;
import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Logica de la armadura robot en el servidor (y reglas comunes):
 * - con brazos puestos, click izquierdo = brazo izquierdo, click derecho = brazo derecho (la hotbar no se usa);
 * - con el torso puesto, la mano secundaria no se puede usar;
 * - el agua la va estropeando.
 */
@Mod.EventBusSubscriber(modid = EnderiteMod.MODID)
public final class RobotServer {
    public static final class ArmState {
        public boolean leftDown, rightDown;
        boolean leftPrev, rightPrev;
        int leftCooldown, rightCooldown;
        boolean leftActive, rightActive;
        boolean warnedBroken;
    }

    private static final Map<UUID, ArmState> STATES = new HashMap<>();

    public static ArmState state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new ArmState());
    }

    public static void setInput(ServerPlayer player, boolean left, boolean right) {
        ArmState s = state(player);
        s.leftDown = left;
        s.rightDown = right;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        tick(player);
    }

    /** Un tick de la armadura robot de un jugador (publico para las pruebas). */
    public static void tick(ServerPlayer player) {
        ItemStack torso = RobotHelper.getTorso(player);
        ArmState s = state(player);
        if (torso.isEmpty()) {
            setActive(player, s, false, false);
            return;
        }
        ServerLevel level = player.serverLevel();

        // El agua estropea la armadura robot
        if (player.isInWaterOrBubble() && player.tickCount % 20 == 0) {
            int t = Math.max(4, torso.getMaxDamage() / 50);
            RobotHelper.wear(torso, t);
            for (HumanoidArm side : HumanoidArm.values()) {
                ItemStack arm = RobotHelper.getArm(torso, side);
                if (!arm.isEmpty()) {
                    RobotHelper.wear(arm, Math.max(3, arm.getMaxDamage() / 50));
                    RobotHelper.setArm(torso, side, arm);
                }
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.2D, player.getZ(), 8, 0.4D, 0.4D, 0.4D, 0.15D);
            level.playSound(null, player.blockPosition(), ModSounds.ROBOT_SHORT_CIRCUIT.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
        }
        boolean broken = RobotHelper.isBroken(torso);
        if (broken && !s.warnedBroken) {
            player.displayClientMessage(Component.translatable("item.enderite.robot.broken").withStyle(ChatFormatting.RED), true);
            s.warnedBroken = true;
        } else if (!broken) {
            s.warnedBroken = false;
        }

        boolean leftActive = false, rightActive = false;
        if (!broken && !player.isSpectator()) {
            leftActive = tickArm(player, torso, HumanoidArm.LEFT, s.leftDown, s.leftPrev, s, true);
            rightActive = tickArm(player, torso, HumanoidArm.RIGHT, s.rightDown, s.rightPrev, s, false);
        }
        s.leftPrev = s.leftDown;
        s.rightPrev = s.rightDown;
        if (s.leftCooldown > 0) s.leftCooldown--;
        if (s.rightCooldown > 0) s.rightCooldown--;
        setActive(player, s, leftActive, rightActive);
    }

    private static boolean tickArm(ServerPlayer player, ItemStack torso, HumanoidArm side, boolean down, boolean prev, ArmState s, boolean left) {
        ItemStack arm = RobotHelper.getArm(torso, side);
        RobotArmType type = RobotHelper.armType(arm);
        if (type == null || !down) return false;
        if (RobotHelper.isBroken(arm)) {
            if (!prev) player.displayClientMessage(Component.translatable("item.enderite.robot.arm_broken").withStyle(ChatFormatting.RED), true);
            return false;
        }
        float mult = (torso.getItem() instanceof RobotTorsoItem t && t.core == RobotCore.RED) ? 1.5F : 1.0F;
        ServerLevel level = player.serverLevel();
        if (com.xpeak.enderite.limbo.Limbo.isLimbo(level)) {
            // En el Limbo no se puede interactuar, atacar, ni tocar bloques: los brazos no funcionan
            if (!prev) player.displayClientMessage(Component.translatable("item.enderite.robot.limbo").withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }
        int cd = left ? s.leftCooldown : s.rightCooldown;
        switch (type) {
            case CHAINSAW -> {
                if (player.tickCount % 4 == 0) {
                    if (chainsawHit(player, side, 4.0F * mult)) {
                        RobotHelper.wear(arm, 1);
                        RobotHelper.setArm(torso, side, arm);
                    }
                }
                if (player.tickCount % 6 == 0) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.ROBOT_CHAINSAW.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
                }
                return true;
            }
            case ROCKET -> {
                if (prev || cd > 0) return false; // un disparo por click
                int ammo = RobotHelper.getAmmo(arm);
                if (ammo < 3 && !player.getAbilities().instabuild) {
                    level.playSound(null, player.blockPosition(), ModSounds.ROBOT_NO_AMMO.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.displayClientMessage(Component.translatable("item.enderite.robot.no_ammo").withStyle(ChatFormatting.GOLD), true);
                    return false;
                }
                fireRockets(player, side, mult);
                if (!player.getAbilities().instabuild) RobotHelper.setAmmo(arm, ammo - 3);
                RobotHelper.wear(arm, 2);
                RobotHelper.setArm(torso, side, arm);
                if (left) s.leftCooldown = 24;
                else s.rightCooldown = 24;
                return true;
            }
            case HARPOON -> {
                if (prev || cd > 0) return false;
                HarpoonEntity existing = HarpoonEntity.find(player, side);
                if (existing != null) existing.retract(); // segundo clic: recoges el arpon
                else {
                    HarpoonEntity.fire(player, side, mult);
                    RobotHelper.wear(arm, 1);
                    RobotHelper.setArm(torso, side, arm);
                }
                setCooldown(s, left, 8);
                return true;
            }
            case FLAMETHROWER -> {
                if (!hasAmmo(player, arm, 1)) {
                    if (!prev) noAmmo(player, type);
                    return false;
                }
                if (!flamethrower(player, side, mult)) return false;
                if (player.tickCount % 5 == 0) {
                    useAmmo(player, arm, 5);
                    if (player.tickCount % 20 == 0) RobotHelper.wear(arm, 1);
                    RobotHelper.setArm(torso, side, arm);
                }
                return true;
            }
            case PORTAL -> {
                if (prev || cd > 0) return false;
                if (!hasAmmo(player, arm, 1)) {
                    noAmmo(player, type);
                    return false;
                }
                PortalRegistry reg = PortalRegistry.get(player.server);
                int color = reg.nextColor(player.getUUID());
                if (RobotPortalEntity.shoot(player, armOrigin(player, side), color)) {
                    useAmmo(player, arm, 1);
                    RobotHelper.wear(arm, 1);
                }
                RobotHelper.setNextPortalColor(arm, reg.nextColor(player.getUUID()));
                RobotHelper.setArm(torso, side, arm);
                setCooldown(s, left, 10);
                return true;
            }
            case EXO -> {
                if (!hasAmmo(player, arm, 1)) {
                    if (!prev) noAmmo(player, type);
                    return false;
                }
                exoBeam(player, side, mult);
                if (player.tickCount % 5 == 0) {
                    useAmmo(player, arm, 5);
                    if (player.tickCount % 20 == 0) RobotHelper.wear(arm, 1);
                    RobotHelper.setArm(torso, side, arm);
                }
                return true;
            }
            case ILLUSION -> {
                if (prev || cd > 0) return false;
                setCooldown(s, left, 6);
                net.minecraft.world.level.block.Block chosen = RobotHelper.getIllusion(arm);
                if (chosen == null || player.isShiftKeyDown()) {
                    // Se abre el menu con los bloques de tu inventario y su boton "Generar"
                    ModNetwork.sendOpenIllusionMenu(player, side == HumanoidArm.LEFT);
                    return true;
                }
                if (!hasAmmo(player, arm, 1)) {
                    noAmmo(player, type);
                    return false;
                }
                if (placeIllusion(player, chosen)) {
                    useAmmo(player, arm, 1);
                    RobotHelper.wear(arm, 1);
                    RobotHelper.setArm(torso, side, arm);
                }
                return true;
            }
            case CATAPULT -> {
                if (prev || cd > 0) return false;
                setCooldown(s, left, 8);
                BlockState held = RobotHelper.getHeld(arm);
                if (held != null) {
                    ThrownBlockEntity.launch(player, held, mult);
                    RobotHelper.setHeld(arm, null);
                    RobotHelper.wear(arm, 2);
                    RobotHelper.setArm(torso, side, arm);
                    return true;
                }
                if (!hasAmmo(player, arm, 1)) {
                    noAmmo(player, type);
                    return false;
                }
                BlockState grabbed = grabBlock(player);
                if (grabbed == null) return false;
                RobotHelper.setHeld(arm, grabbed);
                useAmmo(player, arm, 1);
                RobotHelper.setArm(torso, side, arm);
                return true;
            }
        }
        return false;
    }

    private static void setCooldown(ArmState s, boolean left, int ticks) {
        if (left) s.leftCooldown = ticks;
        else s.rightCooldown = ticks;
    }

    private static boolean hasAmmo(ServerPlayer p, ItemStack arm, int n) {
        return p.getAbilities().instabuild || RobotHelper.getAmmo(arm) >= n;
    }

    private static void useAmmo(ServerPlayer p, ItemStack arm, int n) {
        if (!p.getAbilities().instabuild) RobotHelper.setAmmo(arm, RobotHelper.getAmmo(arm) - n);
    }

    private static void noAmmo(ServerPlayer player, RobotArmType type) {
        player.level().playSound(null, player.blockPosition(), ModSounds.ROBOT_NO_AMMO.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("item.enderite.robot.no_ammo." + type.id).withStyle(ChatFormatting.GOLD), true);
    }

    // ------------------------------------------------------------------ lanzallamas

    public static final double FLAME_RANGE = 5.0D;

    /** Llamarada de 5 bloques: quema y prende fuego a lo que pilla. */
    public static boolean flamethrower(ServerPlayer player, HumanoidArm side, float mult) {
        ServerLevel level = player.serverLevel();
        Vec3 origin = armOrigin(player, side);
        if (player.isUnderWater()) {
            level.sendParticles(ParticleTypes.BUBBLE, origin.x, origin.y, origin.z, 4, 0.1D, 0.1D, 0.1D, 0.05D);
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        var rnd = player.getRandom();
        for (int i = 0; i < 5; i++) {
            Vec3 d = look.add((rnd.nextDouble() - 0.5D) * 0.22D, (rnd.nextDouble() - 0.5D) * 0.22D, (rnd.nextDouble() - 0.5D) * 0.22D).normalize();
            level.sendParticles(i == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, origin.x, origin.y, origin.z, 0, d.x, d.y, d.z, 0.28D + rnd.nextDouble() * 0.06D);
        }
        if (player.tickCount % 3 == 0) {
            Vec3 tip = origin.add(look.scale(FLAME_RANGE * 0.8D));
            level.sendParticles(ParticleTypes.LARGE_SMOKE, tip.x, tip.y, tip.z, 1, 0.3D, 0.3D, 0.3D, 0.01D);
        }
        if (player.tickCount % 6 == 0) {
            level.playSound(null, origin.x, origin.y, origin.z, ModSounds.FLAMETHROWER.get(), SoundSource.PLAYERS, 0.9F, 0.9F + rnd.nextFloat() * 0.2F);
        }
        if (player.tickCount % 4 == 0) {
            DamageSource fire = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.IN_FIRE), player);
            AABB box = player.getBoundingBox().expandTowards(look.scale(FLAME_RANGE)).inflate(1.5D);
            for (Entity e : level.getEntities(player, box, e -> e instanceof LivingEntity && e.isAlive())) {
                Vec3 to = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D).subtract(eye);
                double dist = to.length();
                if (dist > FLAME_RANGE + e.getBbWidth() * 0.5D) continue;
                if (dist > 1.5D && to.normalize().dot(look) < 0.82D) continue;
                if (!player.hasLineOfSight(e)) continue;
                e.setSecondsOnFire(6);
                int invul = e.invulnerableTime;
                e.invulnerableTime = 0;
                if (!e.hurt(fire, 3.0F * mult)) e.invulnerableTime = invul;
            }
        }
        if (player.tickCount % 10 == 0 && player.mayBuild()) {
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(FLAME_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos at = hit.getBlockPos();
                if (!level.getFluidState(at).isEmpty()) {
                    level.sendParticles(ParticleTypes.CLOUD, hit.getLocation().x, hit.getLocation().y + 0.2D, hit.getLocation().z, 3, 0.2D, 0.1D, 0.2D, 0.02D);
                } else {
                    BlockPos firePos = at.relative(hit.getDirection());
                    if (rnd.nextFloat() < 0.6F && level.mayInteract(player, firePos) && BaseFireBlock.canBePlacedAt(level, firePos, hit.getDirection())) {
                        level.setBlock(firePos, BaseFireBlock.getState(level, firePos), 11);
                    }
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ exobrazo

    /** Rayo azul continuo: dana a todo lo que atraviesa hasta chocar con un bloque. */
    public static Vec3 exoBeam(ServerPlayer player, HumanoidArm side, float mult) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = ExoBeam.trace(level, eye, player.getLookAngle(), player);
        if (player.tickCount % 5 == 0) {
            for (LivingEntity e : ExoBeam.hits(level, eye, end, player)) {
                int invul = e.invulnerableTime;
                e.invulnerableTime = 0;
                if (e.hurt(player.damageSources().playerAttack(player), 2.5F * mult)) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5D, e.getZ(), 6, 0.2D, 0.3D, 0.2D, 0.2D);
                } else {
                    e.invulnerableTime = invul;
                }
            }
        }
        if (player.tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 3, 0.1D, 0.1D, 0.1D, 0.15D);
            level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 1, 0.05D, 0.05D, 0.05D, 0.02D);
        }
        if (player.tickCount % 8 == 0) {
            Vec3 o = armOrigin(player, side);
            level.playSound(null, o.x, o.y, o.z, ModSounds.EXO_BEAM.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        return end;
    }

    // ------------------------------------------------------------------ distorsionador de ilusiones

    /** Crea el bloque ilusorio (no-clip) del bloque elegido donde estas mirando. */
    public static boolean placeIllusion(ServerPlayer player, net.minecraft.world.level.block.Block block) {
        if (!player.mayBuild()) return false;
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(6.0D));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = hit.getBlockPos();
        if (!level.getBlockState(pos).canBeReplaced()) pos = pos.relative(hit.getDirection());
        BlockState current = level.getBlockState(pos);
        if (!current.canBeReplaced() || !level.mayInteract(player, pos) || !level.isInWorldBounds(pos)) return false;
        ItemStack fake = new ItemStack(block.asItem());
        BlockState mimic = null;
        try {
            mimic = block.getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, fake,
                    new BlockHitResult(hit.getLocation(), hit.getDirection(), pos, false)));
        } catch (Exception ignored) {
        }
        if (mimic == null || !mimic.is(block)) mimic = block.defaultBlockState();
        if (mimic.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
            mimic = mimic.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false);
        }
        level.setBlock(pos, com.xpeak.enderite.registry.ModBlocks.ILLUSION_BLOCK.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof IllusionBlockEntity be) be.setMimic(mimic);
        level.playSound(null, pos, ModSounds.ILLUSION_CREATE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.WITCH, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 12, 0.35D, 0.35D, 0.35D, 0.02D);
        return true;
    }

    /** Bloques que se pueden elegir en el menu de ilusiones. */
    public static boolean canIllusion(net.minecraft.world.level.block.Block block) {
        return block != net.minecraft.world.level.block.Blocks.AIR && block != com.xpeak.enderite.registry.ModBlocks.ILLUSION_BLOCK.get()
                && block.defaultBlockState().getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL
                && block.defaultBlockState().getFluidState().isEmpty();
    }

    /** Clic en "Generar" del menu: guarda en el brazo que bloque se va a crear (tiene que estar en tu inventario). */
    public static void selectIllusion(ServerPlayer player, boolean left, ResourceLocation id) {
        ItemStack torso = RobotHelper.getTorso(player);
        HumanoidArm side = left ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        ItemStack arm = RobotHelper.getArm(torso, side);
        if (RobotHelper.armType(arm) != RobotArmType.ILLUSION) return;
        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
        if (!(item instanceof net.minecraft.world.item.BlockItem bi) || !canIllusion(bi.getBlock())) return;
        if (!player.getAbilities().instabuild && EngineeringRecipes.count(player.getInventory(), item) <= 0) return;
        RobotHelper.setIllusion(arm, bi.getBlock());
        RobotHelper.setArm(torso, side, arm);
        player.displayClientMessage(Component.translatable("item.enderite.robot.illusion_ready", bi.getBlock().getName())
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        player.level().playSound(null, player.blockPosition(), ModSounds.ILLUSION_CREATE.get(), SoundSource.PLAYERS, 0.5F, 1.6F);
    }

    // ------------------------------------------------------------------ catapulta

    /** Agarra el bloque al que miras (lo quita del mundo). */
    @javax.annotation.Nullable
    public static BlockState grabBlock(ServerPlayer player) {
        if (!player.mayBuild()) return null;
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(5.0D)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!ThrownBlockEntity.canGrab(level, pos, state) || !level.mayInteract(player, pos)) {
            player.displayClientMessage(Component.translatable("item.enderite.robot.cant_grab").withStyle(ChatFormatting.GRAY), true);
            return null;
        }
        var event = new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, pos, state, player);
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)) return null;
        level.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), 3);
        level.playSound(null, pos, ModSounds.CATAPULT_GRAB.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return state;
    }

    // ------------------------------------------------------------------ velocidad real de los jugadores (para los portales)

    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, Vec3> VELOCITY = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Vec3 now = p.position();
            Vec3 last = LAST_POS.put(p.getUUID(), now);
            Vec3 v = last == null ? Vec3.ZERO : now.subtract(last);
            VELOCITY.put(p.getUUID(), v.lengthSqr() > 64.0D ? Vec3.ZERO : v); // un teletransporte no cuenta como velocidad
        }
    }

    /** Lo que se ha movido el jugador en el ultimo tick (el servidor no conoce su velocidad de verdad). */
    public static Vec3 playerVelocity(ServerPlayer p) {
        return VELOCITY.getOrDefault(p.getUUID(), p.getDeltaMovement());
    }

    /** Punto de salida del brazo (a la altura del hombro, desplazado hacia su lado). */
    public static Vec3 armOrigin(Player player, HumanoidArm side) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        double sign = side == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        return player.getEyePosition().add(right.scale(0.38D * sign)).add(0, -0.35D, 0).add(look.scale(0.6D));
    }

    public static void fireRockets(ServerPlayer player, HumanoidArm side, float mult) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 origin = armOrigin(player, side);
        double[][] spread = {{0.0D, 0.0D}, {-0.06D, -0.03D}, {0.06D, -0.03D}};
        for (double[] sp : spread) {
            Vec3 dir = look.add(right.scale(sp[0])).add(up.scale(sp[1]));
            RobotRocketEntity.shoot(player.level(), player, origin.add(right.scale(sp[0] * 2.0D)), dir, mult);
        }
        player.level().playSound(null, origin.x, origin.y, origin.z, ModSounds.ROBOT_ROCKET_LAUNCH.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
    }

    private static boolean chainsawHit(ServerPlayer player, HumanoidArm side, float damage) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB box = player.getBoundingBox().expandTowards(look.scale(2.8D)).inflate(0.8D);
        boolean hit = false;
        for (Entity e : player.level().getEntities(player, box, e -> e instanceof LivingEntity && e.isAlive() && e.isPickable())) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5D, 0).subtract(eye);
            double dist = to.length();
            if (dist > 3.4D || to.normalize().dot(look) < 0.45D) continue;
            int invul = e.invulnerableTime;
            e.invulnerableTime = 0;
            if (e.hurt(player.damageSources().playerAttack(player), damage)) {
                hit = true;
                ((ServerLevel) player.level()).sendParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY() + e.getBbHeight() * 0.6D, e.getZ(),
                        2, 0.2D, 0.2D, 0.2D, 0.1D);
                ((ServerLevel) player.level()).sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.5D, e.getZ(),
                        4, 0.3D, 0.3D, 0.3D, 0.2D);
            } else {
                e.invulnerableTime = invul;
            }
        }
        Vec3 tip = armOrigin(player, side).add(look.scale(1.2D));
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.SMOKE, tip.x, tip.y, tip.z, 1, 0.05D, 0.05D, 0.05D, 0.01D);
        return hit;
    }

    private static void setActive(ServerPlayer player, ArmState s, boolean left, boolean right) {
        if (s.leftActive == left && s.rightActive == right) return;
        s.leftActive = left;
        s.rightActive = right;
        ModNetwork.sendArmAnim(player, left, right);
    }

    // ------------------------------------------------------------------ Reglas (cliente y servidor)

    /** Con brazos robot no se usa la hotbar (los clicks son de los brazos), y la mano secundaria esta bloqueada. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (blocked(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (blocked(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (blocked(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (RobotHelper.hasArms(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(AttackEntityEvent event) {
        if (RobotHelper.hasArms(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (RobotHelper.hasArms(event.getEntity())) event.setNewSpeed(0.0F);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSwap(LivingSwapItemsEvent.Hands event) {
        if (RobotHelper.wearsTorso(event.getEntity())) event.setCanceled(true);
    }

    private static boolean blocked(Player player, InteractionHand hand) {
        if (!RobotHelper.wearsTorso(player)) return false;
        if (hand == InteractionHand.OFF_HAND) return true;
        return RobotHelper.hasArms(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
        LAST_POS.remove(event.getEntity().getUUID());
        VELOCITY.remove(event.getEntity().getUUID());
    }

    private RobotServer() {}
}
