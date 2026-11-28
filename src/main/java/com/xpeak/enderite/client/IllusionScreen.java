package com.xpeak.enderite.client;

import com.xpeak.enderite.network.ModNetwork;
import com.xpeak.enderite.robot.RobotHelper;
import com.xpeak.enderite.robot.RobotServer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Menu del Distorsionador de ilusiones: todos los bloques que llevas ahora mismo en el inventario, cada uno con su
 * boton "Generar" debajo. Al pulsarlo, el brazo queda preparado y el siguiente clic crea ese bloque en version no-clip.
 */
public class IllusionScreen extends Screen {
    private static final int COLS = 6, ROWS = 3, CELL_W = 46, CELL_H = 42;
    private final boolean left;
    private final List<Item> blocks = new ArrayList<>();
    private int page;
    private int x0, y0, panelW, panelH;
    public final List<Button> generateButtons = new ArrayList<>();

    public IllusionScreen(boolean left) {
        super(Component.translatable("enderite.illusion.title"));
        this.left = left;
    }

    public static void open(boolean left) {
        Minecraft.getInstance().setScreen(new IllusionScreen(left));
    }

    public List<Item> blocks() {
        return blocks;
    }

    @Override
    protected void init() {
        blocks.clear();
        generateButtons.clear();
        if (minecraft != null && minecraft.player != null) {
            Inventory inv = minecraft.player.getInventory();
            LinkedHashSet<Item> seen = new LinkedHashSet<>();
            for (int i = 0; i < inv.items.size(); i++) {
                ItemStack s = inv.items.get(i);
                if (s.getItem() instanceof BlockItem bi && RobotServer.canIllusion(bi.getBlock())) seen.add(s.getItem());
            }
            blocks.addAll(seen);
        }
        int perPage = COLS * ROWS;
        int pages = Math.max(1, (blocks.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        panelW = COLS * CELL_W + 16;
        panelH = ROWS * CELL_H + 54;
        x0 = (this.width - panelW) / 2;
        y0 = (this.height - panelH) / 2;
        int start = page * perPage;
        for (int i = start; i < Math.min(blocks.size(), start + perPage); i++) {
            int slot = i - start;
            int cx = x0 + 8 + (slot % COLS) * CELL_W, cy = y0 + 24 + (slot / COLS) * CELL_H;
            Item item = blocks.get(i);
            Button b = Button.builder(Component.translatable("enderite.illusion.generate"), btn -> generate(item))
                    .bounds(cx + 2, cy + 22, CELL_W - 4, 16).build();
            generateButtons.add(b);
            this.addRenderableWidget(b);
        }
        if (pages > 1) {
            this.addRenderableWidget(Button.builder(Component.literal("<"), btn -> {
                page = (page - 1 + pages) % pages;
                this.rebuildWidgets();
            }).bounds(x0 + panelW / 2 - 50, y0 + panelH - 22, 20, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal(">"), btn -> {
                page = (page + 1) % pages;
                this.rebuildWidgets();
            }).bounds(x0 + panelW / 2 + 30, y0 + panelH - 22, 20, 16).build());
        }
    }

    public void generate(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id != null) ModNetwork.sendIllusionSelect(left, id);
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        g.fill(x0, y0, x0 + panelW, y0 + panelH, 0xE0140E1E);
        g.fill(x0, y0, x0 + panelW, y0 + 1, 0xFFB57BFF);
        g.fill(x0, y0 + panelH - 1, x0 + panelW, y0 + panelH, 0xFFB57BFF);
        g.fill(x0, y0, x0 + 1, y0 + panelH, 0xFFB57BFF);
        g.fill(x0 + panelW - 1, y0, x0 + panelW, y0 + panelH, 0xFFB57BFF);
        g.drawCenteredString(this.font, this.title, x0 + panelW / 2, y0 + 7, 0xE6CCFF);
        if (blocks.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("enderite.illusion.empty"), x0 + panelW / 2, y0 + panelH / 2 - 4, 0xAA88CC);
        }
        int perPage = COLS * ROWS;
        int start = page * perPage;
        Block current = null;
        if (minecraft != null && minecraft.player != null) {
            current = RobotHelper.getIllusion(RobotHelper.getArm(RobotHelper.getTorso(minecraft.player), left ? HumanoidArm.LEFT : HumanoidArm.RIGHT));
        }
        Item hovered = null;
        for (int i = start; i < Math.min(blocks.size(), start + perPage); i++) {
            int slot = i - start;
            int cx = x0 + 8 + (slot % COLS) * CELL_W, cy = y0 + 24 + (slot / COLS) * CELL_H;
            Item item = blocks.get(i);
            boolean sel = item instanceof BlockItem bi && bi.getBlock() == current;
            g.fill(cx + 1, cy, cx + CELL_W - 1, cy + CELL_H - 2, sel ? 0x80B57BFF : 0x40FFFFFF);
            g.renderItem(new ItemStack(item), cx + CELL_W / 2 - 8, cy + 3);
            if (mouseX >= cx + CELL_W / 2 - 8 && mouseX < cx + CELL_W / 2 + 8 && mouseY >= cy + 3 && mouseY < cy + 19) hovered = item;
        }
        int pages = Math.max(1, (blocks.size() + perPage - 1) / perPage);
        if (pages > 1) g.drawCenteredString(this.font, (page + 1) + "/" + pages, x0 + panelW / 2, y0 + panelH - 18, 0xCCCCCC);
        else g.drawCenteredString(this.font, Component.translatable("enderite.illusion.hint").withStyle(ChatFormatting.GRAY), x0 + panelW / 2,
                y0 + panelH - 18, 0x999999);
        super.render(g, mouseX, mouseY, partial);
        if (hovered != null) g.renderTooltip(this.font, new ItemStack(hovered), mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
