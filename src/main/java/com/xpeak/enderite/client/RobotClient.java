package com.xpeak.enderite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.network.ModNetwork;
import com.xpeak.enderite.robot.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/** Todo lo de la armadura robot en el cliente. */
@Mod.EventBusSubscriber(modid = EnderiteMod.MODID, value = Dist.CLIENT)
public final class RobotClient {
    private static final Map<Integer, long[]> ANIM = new HashMap<>(); // id -> [ultimo tick activo izq, der]
    private static boolean lastLeft, lastRight;
    public static RobotArmModel rightModel, leftModel;

    // ------------------------------------------------------------------ animacion

    public static void setAnim(int entityId, boolean left, boolean right) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        long[] a = ANIM.computeIfAbsent(entityId, k -> new long[]{-100, -100});
        if (left) a[0] = now;
        if (right) a[1] = now;
        ANIM.put(entityId, a);
    }

    /** Brazo "activo" (motosierra girando o apuntando tras disparar) durante un momento. */
    public static boolean isActive(Player p, HumanoidArm side) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        if (p == mc.player) {
            ItemStack arm = RobotHelper.getArm(RobotHelper.getTorso(p), side);
            if (RobotHelper.armType(arm) == RobotArmType.CHAINSAW && (side == HumanoidArm.LEFT ? lastLeft : lastRight)
                    && !RobotHelper.isBroken(arm) && !RobotHelper.isBroken(RobotHelper.getTorso(p))) return true;
        }
        long[] a = ANIM.get(p.getId());
        if (a == null) return false;
        long t = side == HumanoidArm.LEFT ? a[0] : a[1];
        return mc.level.getGameTime() - t <= 12;
    }

    // ------------------------------------------------------------------ entrada (clicks = brazos)

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            lastLeft = lastRight = false;
            return;
        }
        boolean arms = RobotHelper.hasArms(p);
        boolean left = arms && mc.screen == null && mc.options.keyAttack.isDown();
        boolean right = arms && mc.screen == null && mc.options.keyUse.isDown();
        if (left != lastLeft || right != lastRight) {
            ModNetwork.sendArmInput(left, right);
            lastLeft = left;
            lastRight = right;
        }
        if (RobotHelper.wearsTorso(p)) {
            while (mc.options.keySwapOffhand.consumeClick()) {
                // la mano secundaria esta bloqueada con la armadura robot
            }
        }
    }

    /** Con brazos robot los clicks no usan la hotbar; con el torso, la mano secundaria no se usa. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !RobotHelper.wearsTorso(p)) return;
        if (RobotHelper.hasArms(p) && (event.isAttack() || event.isUseItem())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        } else if (event.isUseItem() && event.getHand() == InteractionHand.OFF_HAND) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    // ------------------------------------------------------------------ HUD

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id()) && RobotHelper.hasArms(p)) {
            event.setCanceled(true);
        }
    }

    /** Sustituye la hotbar cuando llevas brazos robot. */
    public static void renderHud(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !RobotHelper.hasArms(p) || mc.options.hideGui) return;
        ItemStack torso = RobotHelper.getTorso(p);
        int core = torso.getItem() instanceof RobotTorsoItem t ? t.core.color : 0xAAAAAA;
        int y = height - 23;
        drawArmPanel(g, mc.font, width / 2 - 91, y, RobotHelper.getArm(torso, HumanoidArm.LEFT), "enderite.robot.hud.left", core,
                isActive(p, HumanoidArm.LEFT));
        drawArmPanel(g, mc.font, width / 2 + 3, y, RobotHelper.getArm(torso, HumanoidArm.RIGHT), "enderite.robot.hud.right", core,
                isActive(p, HumanoidArm.RIGHT));
        // integridad del torso
        float integ = 1.0F - torso.getDamageValue() / (float) Math.max(1, torso.getMaxDamage() - 1);
        int bw = 182;
        g.fill(width / 2 - 91, y - 4, width / 2 - 91 + bw, y - 2, 0xAA000000);
        g.fill(width / 2 - 91, y - 4, width / 2 - 91 + (int) (bw * integ), y - 2, 0xFF000000 | core);
    }

    private static void drawArmPanel(GuiGraphics g, Font font, int x, int y, ItemStack arm, String key, int core, boolean active) {
        g.fill(x, y, x + 88, y + 22, 0xB0101418);
        int border = active ? 0xFFFFFFFF : (0xFF000000 | core);
        g.fill(x, y, x + 88, y + 1, border);
        g.fill(x, y + 21, x + 88, y + 22, border);
        g.fill(x, y, x + 1, y + 22, border);
        g.fill(x + 87, y, x + 88, y + 22, border);
        if (arm.isEmpty()) {
            g.drawString(font, Component.translatable("enderite.robot.hud.empty"), x + 6, y + 7, 0x777777, false);
            return;
        }
        g.renderItem(arm, x + 3, y + 3);
        RobotArmType type = RobotHelper.armType(arm);
        Component line1 = Component.translatable(key);
        Component line2;
        int color2;
        if (RobotHelper.isBroken(arm)) {
            line2 = Component.translatable("enderite.robot.hud.broken");
            color2 = 0xFF5555;
        } else if (type == RobotArmType.ROCKET) {
            line2 = Component.translatable("enderite.robot.hud.ammo", RobotHelper.getAmmo(arm));
            color2 = RobotHelper.getAmmo(arm) >= 3 ? 0xFFCC55 : 0xFF5555;
        } else {
            int pct = 100 - (int) (100.0F * arm.getDamageValue() / Math.max(1, arm.getMaxDamage() - 1));
            line2 = Component.literal(pct + "%");
            color2 = 0xBBBBBB;
        }
        g.drawString(font, line1, x + 22, y + 3, 0xFFFFFF, false);
        g.drawString(font, line2, x + 22, y + 12, color2, false);
    }

    // ------------------------------------------------------------------ huecos de brazo en el inventario

    private record SlotPos(int x, int y) {}

    private static SlotPos[] armSlots(AbstractContainerScreen<?> screen) {
        if (screen instanceof InventoryScreen) {
            return new SlotPos[]{new SlotPos(screen.getGuiLeft() + 27, screen.getGuiTop() + 30),
                    new SlotPos(screen.getGuiLeft() + 57, screen.getGuiTop() + 30)};
        }
        if (screen instanceof CreativeModeInventoryScreen cs && cs.isInventoryOpen()) {
            return new SlotPos[]{new SlotPos(screen.getGuiLeft() + 127, screen.getGuiTop() + 19),
                    new SlotPos(screen.getGuiLeft() + 149, screen.getGuiTop() + 19)};
        }
        return null;
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !RobotHelper.wearsTorso(p)) return;
        SlotPos[] slots = armSlots(screen);
        if (slots == null) return;
        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        ItemStack torso = RobotHelper.getTorso(p);
        int core = torso.getItem() instanceof RobotTorsoItem t ? t.core.color : 0xAAAAAA;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        for (int i = 0; i < 2; i++) {
            SlotPos s = slots[i];
            HumanoidArm side = i == 0 ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
            g.fill(s.x - 1, s.y - 1, s.x + 17, s.y + 17, 0xFF000000 | core);
            g.fill(s.x, s.y, s.x + 16, s.y + 16, 0xFF2A2F36);
            ItemStack arm = RobotHelper.getArm(torso, side);
            if (arm.isEmpty()) {
                g.drawString(font, i == 0 ? "I" : "D", s.x + 5, s.y + 4, 0x667788, false);
            } else {
                g.renderItem(arm, s.x, s.y);
                g.renderItemDecorations(font, arm, s.x, s.y);
            }
            boolean hover = event.getMouseX() >= s.x && event.getMouseX() < s.x + 16 && event.getMouseY() >= s.y && event.getMouseY() < s.y + 16;
            if (hover) {
                g.fill(s.x, s.y, s.x + 16, s.y + 16, 0x80FFFFFF);
                if (screen.getMenu().getCarried().isEmpty()) {
                    if (!arm.isEmpty()) g.renderTooltip(font, arm, event.getMouseX(), event.getMouseY());
                    else g.renderTooltip(font, Component.translatable(i == 0 ? "enderite.robot.slot.left" : "enderite.robot.slot.right")
                            .withStyle(ChatFormatting.AQUA), event.getMouseX(), event.getMouseY());
                }
            }
        }
        // mano secundaria bloqueada
        if (screen instanceof InventoryScreen) {
            int ox = screen.getGuiLeft() + 77, oy = screen.getGuiTop() + 62;
            g.fill(ox, oy, ox + 16, oy + 16, 0xA0300000);
            g.drawString(font, "X", ox + 5, oy + 4, 0xFF4444, false);
        }
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onScreenClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !RobotHelper.wearsTorso(p)) return;
        double mx = event.getMouseX(), my = event.getMouseY();
        SlotPos[] slots = armSlots(screen);
        if (slots != null) {
            for (int i = 0; i < 2; i++) {
                SlotPos s = slots[i];
                if (mx >= s.x && mx < s.x + 16 && my >= s.y && my < s.y + 16) {
                    clickArmSlot(screen, p, i == 0);
                    event.setCanceled(true);
                    return;
                }
            }
        }
        // la mano secundaria no se puede tocar con la armadura robot puesta
        Slot hovered = screen.getSlotUnderMouse();
        if (hovered != null && hovered.container == p.getInventory() && hovered.getContainerSlot() == 40) {
            event.setCanceled(true);
        }
    }

    private static void clickArmSlot(AbstractContainerScreen<?> screen, LocalPlayer p, boolean left) {
        ItemStack carried = screen.getMenu().getCarried();
        if (!carried.isEmpty() && !(carried.getItem() instanceof RobotArmItem)) return;
        if (screen instanceof CreativeModeInventoryScreen) {
            ItemStack torso = RobotHelper.getTorso(p);
            ItemStack current = RobotHelper.getArm(torso, left ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
            ModNetwork.sendArmSlot(left, true, carried.copy());
            RobotHelper.setArm(torso, left ? HumanoidArm.LEFT : HumanoidArm.RIGHT, carried.copy());
            screen.getMenu().setCarried(current);
        } else {
            ModNetwork.sendArmSlot(left, false, ItemStack.EMPTY);
        }
    }

    // ------------------------------------------------------------------ primera persona

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !RobotHelper.wearsTorso(p)) return;
        HumanoidArm side = event.getHand() == InteractionHand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
        if (!RobotHelper.hasArms(p)) {
            if (event.getHand() == InteractionHand.OFF_HAND) event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        ItemStack arm = RobotHelper.getArm(RobotHelper.getTorso(p), side);
        RobotArmType type = RobotHelper.armType(arm);
        if (type == null || rightModel == null) return;
        boolean active = isActive(p, side);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        boolean right = side == HumanoidArm.RIGHT;
        float f = right ? 1.0F : -1.0F;
        float equip = event.getEquipProgress();
        pose.translate(f * 0.64F, -0.6F + equip * -0.6F, -0.72F);
        pose.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        pose.translate(f * -1.0F, 3.6F, 3.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        pose.mulPose(Axis.XP.rotationDegrees(200.0F));
        pose.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        pose.translate(f * 5.6F, 0.0F, 0.0F);
        if (active && type == RobotArmType.CHAINSAW) {
            float t = p.tickCount + event.getPartialTick();
            pose.translate(Mth.sin(t * 2.3F) * 0.012F, -0.08F, 0.0F);
        }
        pose.translate(f * -5.0F / 16.0F, 2.0F / 16.0F, 0.0F);
        VertexConsumer vc = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(RobotArmModel.TEXTURE));
        (right ? rightModel : leftModel).render(pose, vc, event.getPackedLight(), type, active, p.tickCount + event.getPartialTick());
        pose.popPose();
    }

    // ------------------------------------------------------------------ tercera persona

    private static final Map<Integer, ItemStack[]> HIDDEN = new HashMap<>();

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (!RobotHelper.wearsTorso(p)) return;
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        ItemStack torso = RobotHelper.getTorso(p);
        boolean l = !RobotHelper.getArm(torso, HumanoidArm.LEFT).isEmpty();
        boolean r = !RobotHelper.getArm(torso, HumanoidArm.RIGHT).isEmpty();
        model.leftArm.visible = !l;
        model.leftSleeve.visible = !l;
        model.rightArm.visible = !r;
        model.rightSleeve.visible = !r;
        // sin objetos en las manos: la hotbar y la mano secundaria no se usan con el robot
        ItemStack main = p.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack off = p.getItemBySlot(EquipmentSlot.OFFHAND);
        boolean hideMain = (l || r) && !main.isEmpty();
        if (hideMain || !off.isEmpty()) {
            HIDDEN.put(p.getId(), new ItemStack[]{hideMain ? main : null, off.isEmpty() ? null : off});
            if (hideMain) p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            if (!off.isEmpty()) p.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        Player p = event.getEntity();
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        model.leftArm.visible = true;
        model.leftSleeve.visible = true;
        model.rightArm.visible = true;
        model.rightSleeve.visible = true;
        ItemStack[] hidden = HIDDEN.remove(p.getId());
        if (hidden != null) {
            if (hidden[0] != null) p.setItemSlot(EquipmentSlot.MAINHAND, hidden[0]);
            if (hidden[1] != null) p.setItemSlot(EquipmentSlot.OFFHAND, hidden[1]);
        }
    }

    /** Capa que dibuja los brazos robot sobre el jugador. */
    public static class ArmsLayer extends net.minecraft.client.renderer.entity.layers.RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public ArmsLayer(PlayerRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ItemStack torso = RobotHelper.getTorso(p);
            if (torso.isEmpty() || rightModel == null) return;
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(RobotArmModel.TEXTURE));
            PlayerModel<AbstractClientPlayer> model = this.getParentModel();
            for (HumanoidArm side : HumanoidArm.values()) {
                ItemStack arm = RobotHelper.getArm(torso, side);
                RobotArmType type = RobotHelper.armType(arm);
                if (type == null) continue;
                var part = side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                boolean active = isActive(p, side);
                pose.pushPose();
                pose.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
                float xRot = part.xRot, yRot = part.yRot, zRot = part.zRot;
                if (active) {
                    xRot = model.head.xRot - (float) Math.PI / 2.0F;
                    yRot = model.head.yRot;
                    zRot = 0.0F;
                }
                pose.mulPose(new org.joml.Quaternionf().rotationZYX(zRot, yRot, xRot));
                (side == HumanoidArm.RIGHT ? rightModel : leftModel).render(pose, vc, light, type, active, ageInTicks);
                pose.popPose();
            }
        }
    }

    private RobotClient() {}
}
