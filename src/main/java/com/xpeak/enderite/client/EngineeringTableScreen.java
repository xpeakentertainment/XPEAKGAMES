package com.xpeak.enderite.client;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.robot.EngineeringRecipes;
import com.xpeak.enderite.robot.EngineeringTableMenu;
import com.xpeak.enderite.robot.RobotArmItem;
import com.xpeak.enderite.robot.RobotHelper;
import com.xpeak.enderite.robot.RobotTorsoItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Pantalla de la Mesa de ingenieria: montaje (izquierda) y fabricacion de piezas (derecha). */
public class EngineeringTableScreen extends AbstractContainerScreen<EngineeringTableMenu> {
    private static final ResourceLocation BG = new ResourceLocation(EnderiteMod.MODID, "textures/gui/engineering_table.png");
    private static final int BTN_X = 96, BTN_Y = 16, BTN_W = 74, BTN_H = 19;

    public EngineeringTableScreen(EngineeringTableMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 200;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        // botones de fabricacion
        for (int i = 0; i < EngineeringRecipes.PARTS.size(); i++) {
            var part = EngineeringRecipes.PARTS.get(i);
            int x = leftPos + BTN_X, y = topPos + BTN_Y + i * BTN_H;
            boolean hover = mouseX >= x && mouseX < x + BTN_W && mouseY >= y && mouseY < y + BTN_H - 1;
            boolean can = this.minecraft != null && this.minecraft.player != null && EngineeringRecipes.canAfford(this.minecraft.player, part);
            int bg = can ? (hover ? 0xFF3C6E8F : 0xFF2B4A60) : (hover ? 0xFF5A3434 : 0xFF3A2A2A);
            g.fill(x, y, x + BTN_W, y + BTN_H - 1, 0xFF0D1418);
            g.fill(x + 1, y + 1, x + BTN_W - 1, y + BTN_H - 2, bg);
            ItemStack icon = new ItemStack(part.result().get());
            g.renderItem(icon, x + 1, y + 1);
            String name = Component.translatable("enderite.engineering.part." + i).getString();
            if (this.font.width(name) > BTN_W - 20) name = this.font.plainSubstrByWidth(name, BTN_W - 24) + "..";
            g.drawString(this.font, name, x + 19, y + 5, can ? 0xFFFFFF : 0xAA8888, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, 8, 5, 0x1B1E22, false);
        g.drawString(this.font, Component.translatable("enderite.engineering.left"), 17, 27, 0x9FD3FF, false);
        g.drawString(this.font, Component.translatable("enderite.engineering.torso"), 38, 27, 0x9FD3FF, false);
        g.drawString(this.font, Component.translatable("enderite.engineering.right"), 71, 27, 0x9FD3FF, false);
        g.drawString(this.font, Component.translatable("enderite.engineering.ammo"), 8, 67, 0xFFCC55, false);
        g.drawString(this.font, Component.translatable("enderite.engineering.repair"), 60, 67, 0xC0C0C0, false);
        // estado del robot montado
        ItemStack torso = this.menu.getTable().getItem(0);
        if (torso.getItem() instanceof RobotTorsoItem) {
            int pct = 100 - (int) (100.0F * torso.getDamageValue() / Math.max(1, torso.getMaxDamage() - 1));
            g.drawString(this.font, Component.translatable("enderite.engineering.integrity", pct), 8, 98,
                    RobotHelper.isBroken(torso) ? 0xFF5555 : 0x77DD77, false);
        }
        g.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        // tooltip de coste
        for (int i = 0; i < EngineeringRecipes.PARTS.size(); i++) {
            int x = leftPos + BTN_X, y = topPos + BTN_Y + i * BTN_H;
            if (mouseX >= x && mouseX < x + BTN_W && mouseY >= y && mouseY < y + BTN_H - 1) {
                var part = EngineeringRecipes.PARTS.get(i);
                List<Component> lines = new ArrayList<>();
                ItemStack result = new ItemStack(part.result().get());
                lines.add(result.getHoverName().copy().withStyle(ChatFormatting.AQUA));
                if (result.getItem() instanceof RobotTorsoItem t) {
                    lines.add(Component.translatable("item.enderite.robot_torso.effect." + t.core.id).withStyle(t.core.format));
                } else if (result.getItem() instanceof RobotArmItem a) {
                    lines.add(Component.translatable("item.enderite.robot_arm." + a.type.id + ".desc").withStyle(ChatFormatting.GRAY));
                }
                lines.add(Component.translatable("enderite.engineering.cost").withStyle(ChatFormatting.YELLOW));
                for (var c : part.costs()) {
                    int have = EngineeringRecipes.count(this.minecraft.player.getInventory(), c.item());
                    boolean ok = have >= c.count() || this.minecraft.player.getAbilities().instabuild;
                    lines.add(Component.literal(" " + c.count() + "x ").append(c.item().getDescription())
                            .append(Component.literal("  (" + have + ")")).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
                }
                g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
                return;
            }
        }
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < EngineeringRecipes.PARTS.size(); i++) {
            int x = leftPos + BTN_X, y = topPos + BTN_Y + i * BTN_H;
            if (mouseX >= x && mouseX < x + BTN_W && mouseY >= y && mouseY < y + BTN_H - 1 && button == 0) {
                if (this.minecraft != null && this.minecraft.gameMode != null) {
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, i);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
