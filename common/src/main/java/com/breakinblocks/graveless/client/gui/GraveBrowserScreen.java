package com.breakinblocks.graveless.client.gui;

import com.breakinblocks.graveless.client.GhostClientManager;
import com.breakinblocks.graveless.client.render.GhostSkins;
import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.event.GraveMenuHandlers;
import com.breakinblocks.graveless.net.GravelessNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.geom.ModelLayers;
import com.breakinblocks.graveless.client.render.GhostModel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.breakinblocks.graveless.platform.Services;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class GraveBrowserScreen extends Screen {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final int EDGE = 0xFF3BE8E8;
    private static final int EDGE_DIM = 0xFF14555C;
    private static final int PANEL_FILL = 0xF6070C0E;
    private static final int SUB_FILL = 0xFF0C1215;
    private static final int SLOT_FILL = 0xFF101619;
    private static final int SLOT_EDGE = 0xFF1C4348;
    private static final int TEXT_BRIGHT = 0xFFD8FBFB;
    private static final int TEXT_CYAN = 0xFF41E9E9;
    private static final int TEXT_MUTED = 0xFF6E9B9E;
    private static final int RED = 0xFFFF6459;
    private static final int RED_DIM = 0xFF9E3B33;

    private static final int ROW_HEIGHT = 44;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLS = 6;
    private static final int TERRAIN_PX = 112;
    private static final int BUTTON_H = 16;
    private static final int BUTTON_STEP = 20;

    private static final int ACTION_TELEPORT = 1;
    private static final int ACTION_TRACK = 2;
    private static final int ACTION_RESTORE = 3;
    private static final int ACTION_CLAIM_XP = 4;
    private static final int ACTION_BACKUPS = 5;

    private static final int OVERLAY_NONE = 0;
    private static final int OVERLAY_PICKER = 1;
    private static final int OVERLAY_ACCESS = 2;
    private static final int OVERLAY_ACCESS_ADD = 3;
    private static final int OVERLAY_BACKUPS = 4;

    private UUID ownerId;
    private String ownerName;
    private List<GravelessNetworking.GraveSummary> graves;
    private boolean admin;
    private boolean ownerEnabled;
    private UUID trackedId;
    private List<GravelessNetworking.PlayerEntry> players;
    private List<GravelessNetworking.PlayerEntry> allowed;
    private List<GravelessNetworking.BackupEntry> backups = List.of();
    private final Map<UUID, GravelessNetworking.GraveDetailPayload> details = new HashMap<>();
    private final Map<UUID, BlockState[]> dioramaCache = new HashMap<>();
    private UUID selectedId;
    private UUID focusId;
    private int listScroll;
    private int gridScroll;
    private boolean confirmDelete;
    private int overlay = OVERLAY_NONE;
    private int overlayScroll;
    private int armedBackup = -1;
    private int overlayDragX;
    private int overlayDragY;
    private boolean draggingOverlay;
    private boolean draggingGridBar;
    private boolean draggingDiorama;
    private float dioramaZoom = 1.0F;
    private float dioramaYaw = 225.0F;
    private GhostModel wideModel;
    private GhostModel slimModel;

    private record Layout(int left, int top, int panelW, int panelH, int bottom,
                          int listX, int listY, int listW, int listH,
                          int midX, int midW, int gridX, int gridY, int gridBottom,
                          int topButtonY, int rightX, int rightW, int terrainX, int terrainY,
                          int infoY, int deleteY) {
    }

    private record ButtonSpec(int action, int y, Component label, boolean enabled) {
    }

    public GraveBrowserScreen(GravelessNetworking.GraveListPayload payload) {
        super(Component.translatable("graveless.menu.title"));
        applyPayload(payload);
    }

    private void applyPayload(GravelessNetworking.GraveListPayload payload) {
        this.ownerId = payload.ownerId();
        this.ownerName = payload.ownerName();
        this.graves = new ArrayList<>(payload.graves());
        this.admin = payload.admin();
        this.ownerEnabled = payload.ownerEnabled();
        this.trackedId = payload.trackedId().orElse(null);
        this.players = new ArrayList<>(payload.players());
        this.allowed = new ArrayList<>(payload.allowed());
        payload.focusId().ifPresent(id -> this.focusId = id);
    }

    @Override
    protected void init() {
        super.init();
        if (wideModel == null) {
            wideModel = new GhostModel(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            slimModel = new GhostModel(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }
        if (focusId != null && findSummary(focusId) != null) {
            select(focusId);
        } else if (selectedId == null && !graves.isEmpty()) {
            select(graves.getFirst().recordId());
        }
        focusId = null;
        revealSelection();
    }

    public void updateFrom(GravelessNetworking.GraveListPayload payload) {
        boolean ownerChanged = !payload.ownerId().equals(ownerId);
        applyPayload(payload);
        confirmDelete = false;
        details.clear();
        dioramaCache.clear();
        if (ownerChanged) {
            selectedId = null;
            listScroll = 0;
            backups = List.of();
            if (overlay == OVERLAY_BACKUPS) {
                overlay = OVERLAY_NONE;
            }
        } else if (selectedId != null && findSummary(selectedId) == null) {
            selectedId = null;
        }
        if (focusId != null && findSummary(focusId) != null) {
            selectedId = focusId;
            gridScroll = 0;
        }
        focusId = null;
        if (selectedId == null && !graves.isEmpty()) {
            select(graves.getFirst().recordId());
        } else if (selectedId != null) {
            requestDetail(selectedId);
        }
        clampScroll();
        revealSelection();
    }

    public void receiveDetail(GravelessNetworking.GraveDetailPayload payload) {
        details.put(payload.recordId(), payload);
    }

    public void receiveBackups(GravelessNetworking.BackupListPayload payload) {
        if (payload.ownerId().equals(ownerId)) {
            backups = new ArrayList<>(payload.backups());
            armedBackup = -1;
        }
    }

    private GravelessNetworking.GraveSummary findSummary(UUID id) {
        for (GravelessNetworking.GraveSummary summary : graves) {
            if (summary.recordId().equals(id)) {
                return summary;
            }
        }
        return null;
    }

    private boolean viewingSelf() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getUUID().equals(ownerId);
    }

    private boolean canDelete() {
        return admin || viewingSelf();
    }

    private UUID effectiveTrackedId() {
        if (trackedId != null && findSummary(trackedId) != null) {
            return trackedId;
        }
        return graves.isEmpty() ? null : graves.getFirst().recordId();
    }

    private void select(UUID id) {
        selectedId = id;
        gridScroll = 0;
        confirmDelete = false;
        requestDetail(id);
    }

    private void requestDetail(UUID id) {
        if (!details.containsKey(id)) {
            Services.NETWORK.sendToServer(new GravelessNetworking.GraveDetailRequestPayload(ownerId, id));
        }
    }

    private List<ButtonSpec> buttonSpecs(Layout l) {
        List<ButtonSpec> specs = new ArrayList<>();
        boolean hasSelection = selectedId != null && findSummary(selectedId) != null;
        int y = l.bottom() - BUTTON_H;
        if (admin) {
            specs.add(new ButtonSpec(ACTION_TELEPORT, y,
                    Component.translatable("graveless.menu.button.teleport"), hasSelection));
            y -= BUTTON_STEP;
            specs.add(new ButtonSpec(ACTION_RESTORE, y,
                    Component.translatable("graveless.menu.button.restore"), hasSelection));
            y -= BUTTON_STEP;
        }
        if (viewingSelf()) {
            boolean tracking = hasSelection && selectedId.equals(effectiveTrackedId());
            specs.add(new ButtonSpec(ACTION_TRACK, y,
                    Component.translatable(tracking ? "graveless.menu.button.tracking" : "graveless.menu.button.track"),
                    hasSelection && !tracking));
            y -= BUTTON_STEP;
        }
        GravelessNetworking.GraveSummary selected = hasSelection ? findSummary(selectedId) : null;
        specs.add(new ButtonSpec(ACTION_CLAIM_XP, y,
                Component.translatable("graveless.menu.button.claim_xp"),
                selected != null && selected.xp() > 0 && canTake(selected)));
        y -= BUTTON_STEP;
        if (admin) {
            specs.add(new ButtonSpec(ACTION_BACKUPS, y,
                    Component.translatable("graveless.menu.button.backups"), true));
        }
        return specs;
    }

    private Layout layout() {
        int panelW = Math.min(width - 12, 424);
        int panelH = Math.min(height - 12, 228);
        int left = (width - panelW) / 2;
        int top = (height - panelH) / 2;
        int bottom = top + panelH - 8;
        int contentY = top + 24;
        int listX = left + 8;
        int listW = 138;
        int listY = contentY + 12;
        int listH = bottom - listY - (viewingSelf() ? BUTTON_H + 4 : 0);
        int midX = listX + listW + 7;
        int midW = 128;
        int gridX = midX + (midW - GRID_COLS * SLOT_SIZE) / 2;
        int gridY = contentY + 24;
        int buttonCount = (admin ? 3 : 0) + (viewingSelf() ? 1 : 0) + 1;
        int topButtonY = bottom - BUTTON_H - (buttonCount - 1) * BUTTON_STEP;
        int gridBottom = topButtonY - 14;
        int rightX = midX + midW + 7;
        int rightW = left + panelW - 8 - rightX;
        int terrainX = rightX + (rightW - TERRAIN_PX - 4) / 2;
        int terrainY = contentY + 24;
        int infoY = terrainY + TERRAIN_PX + 10;
        int deleteY = bottom - BUTTON_H;
        return new Layout(left, top, panelW, panelH, bottom, listX, listY, listW, listH,
                midX, midW, gridX, gridY, gridBottom, topButtonY,
                rightX, rightW, terrainX, terrainY, infoY, deleteY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        Layout l = layout();
        graphics.fill(0, 0, width, height, 0xB8020404);

        graphics.fill(l.left(), l.top(), l.left() + l.panelW(), l.top() + l.panelH(), PANEL_FILL);
        graphics.renderOutline(l.left(), l.top(), l.panelW(), l.panelH(), EDGE);
        graphics.renderOutline(l.left() + 2, l.top() + 2, l.panelW() - 4, l.panelH() - 4, EDGE_DIM);
        drawCorners(graphics, l);
        drawTitle(graphics, l);
        drawListHeader(graphics, l, mouseX, mouseY);

        drawList(graphics, l, mouseX, mouseY);
        drawSelfButtons(graphics, l, mouseX, mouseY);
        drawInventory(graphics, l, mouseX, mouseY);
        drawTerrainPanel(graphics, l);
        drawButtons(graphics, l, mouseX, mouseY);
        drawOverlay(graphics, l, mouseX, mouseY);

        flushTooltip(graphics);
    }

    private List<Component> deferredTooltip;
    private int tooltipX;
    private int tooltipY;

    private void setDeferredTooltip(List<Component> lines, int mouseX, int mouseY) {
        deferredTooltip = lines;
        tooltipX = mouseX;
        tooltipY = mouseY;
    }

    private void flushTooltip(GuiGraphics graphics) {
        if (deferredTooltip != null) {
            graphics.renderComponentTooltip(font, deferredTooltip, tooltipX, tooltipY);
            deferredTooltip = null;
        }
    }

    private void drawCorners(GuiGraphics graphics, Layout l) {
        int x1 = l.left() + l.panelW();
        int y1 = l.top() + l.panelH();
        for (int[] corner : new int[][]{{l.left(), l.top(), 1, 1}, {x1, l.top(), -1, 1},
                {l.left(), y1, 1, -1}, {x1, y1, -1, -1}}) {
            fillFromCorner(graphics, corner[0], corner[1], 6 * corner[2], 2 * corner[3]);
            fillFromCorner(graphics, corner[0], corner[1], 2 * corner[2], 6 * corner[3]);
        }
    }

    private void fillFromCorner(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(Math.min(x, x + w), Math.min(y, y + h), Math.max(x, x + w), Math.max(y, y + h), EDGE);
    }

    private void drawTitle(GuiGraphics graphics, Layout l) {
        int centerX = l.left() + l.panelW() / 2;
        int titleW = font.width(title) + 28;
        int tx = centerX - titleW / 2;
        int ty = l.top() + 4;
        graphics.fill(tx, ty, tx + titleW, ty + 14, 0xFF03080A);
        graphics.renderOutline(tx, ty, titleW, 14, EDGE_DIM);
        graphics.drawCenteredString(font, title, centerX, ty + 3, TEXT_CYAN);
        drawDiamond(graphics, tx + 7, ty + 7);
        drawDiamond(graphics, tx + titleW - 7, ty + 7);
    }

    private void drawDiamond(GuiGraphics graphics, int cx, int cy) {
        graphics.fill(cx - 1, cy - 3, cx + 1, cy + 3, EDGE);
        graphics.fill(cx - 3, cy - 1, cx + 3, cy + 1, EDGE);
    }

    private void drawListHeader(GuiGraphics graphics, Layout l, int mouseX, int mouseY) {
        int centerX = l.listX() + l.listW() / 2;
        if (!admin) {
            graphics.drawCenteredString(font, viewingSelf()
                            ? Component.translatable("graveless.menu.outstanding")
                            : Component.translatable("graveless.menu.viewing", ownerName.toUpperCase()),
                    centerX, l.top() + 26, TEXT_BRIGHT);
            return;
        }
        Component label = Component.translatable("graveless.menu.viewing", ownerName.toUpperCase());
        int w = font.width(label) + 12;
        int x = centerX - w / 2;
        int y = l.top() + 23;
        boolean hoveredNow = overlay == OVERLAY_NONE && hovered(mouseX, mouseY, x, y, w, 12);
        graphics.fill(x, y, x + w, y + 12, hoveredNow ? 0xFF12272C : 0xFF0B1417);
        graphics.renderOutline(x, y, w, 12, hoveredNow || overlay == OVERLAY_PICKER ? EDGE : EDGE_DIM);
        graphics.drawCenteredString(font, label, centerX, y + 2, TEXT_CYAN);
    }

    private void drawSelfButtons(GuiGraphics graphics, Layout l, int mouseX, int mouseY) {
        if (!viewingSelf()) {
            return;
        }
        int y = l.bottom() - BUTTON_H;
        int half = (l.listW() - 4) / 2;
        drawButton(graphics, l.listX(), y, half, BUTTON_H,
                Component.translatable(ownerEnabled ? "graveless.menu.button.graves_on" : "graveless.menu.button.graves_off"),
                ownerEnabled ? TEXT_CYAN : RED, ownerEnabled ? EDGE : RED_DIM, true,
                overlay == OVERLAY_NONE && hovered(mouseX, mouseY, l.listX(), y, half, BUTTON_H));
        drawButton(graphics, l.listX() + half + 4, y, half, BUTTON_H,
                Component.translatable("graveless.menu.button.access"), TEXT_CYAN, EDGE, true,
                overlay == OVERLAY_NONE && hovered(mouseX, mouseY, l.listX() + half + 4, y, half, BUTTON_H));
    }

    private void drawList(GuiGraphics graphics, Layout l, int mouseX, int mouseY) {
        graphics.fill(l.listX(), l.listY(), l.listX() + l.listW(), l.listY() + l.listH(), SUB_FILL);
        graphics.renderOutline(l.listX(), l.listY(), l.listW(), l.listH(), EDGE_DIM);

        if (graves.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("graveless.menu.empty"),
                    l.listX() + l.listW() / 2, l.listY() + l.listH() / 2 - 4, TEXT_MUTED);
            return;
        }

        UUID tracked = effectiveTrackedId();
        int innerW = l.listW() - 6;
        graphics.enableScissor(l.listX() + 1, l.listY() + 1, l.listX() + l.listW() - 1, l.listY() + l.listH() - 1);
        for (int i = 0; i < graves.size(); i++) {
            GravelessNetworking.GraveSummary summary = graves.get(i);
            int rowY = l.listY() + 2 + i * ROW_HEIGHT - listScroll;
            if (rowY + ROW_HEIGHT < l.listY() || rowY > l.listY() + l.listH()) {
                continue;
            }
            boolean selected = summary.recordId().equals(selectedId);
            boolean hoveredNow = overlay == OVERLAY_NONE
                    && mouseX >= l.listX() + 2 && mouseX < l.listX() + innerW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2
                    && mouseY >= l.listY() && mouseY < l.listY() + l.listH();
            if (selected) {
                graphics.fill(l.listX() + 2, rowY, l.listX() + innerW, rowY + ROW_HEIGHT - 2, 0x3325DFDF);
                graphics.renderOutline(l.listX() + 2, rowY, innerW - 2, ROW_HEIGHT - 2, EDGE);
            } else {
                graphics.fill(l.listX() + 2, rowY, l.listX() + innerW, rowY + ROW_HEIGHT - 2, 0xFF0A1013);
                graphics.renderOutline(l.listX() + 2, rowY, innerW - 2, ROW_HEIGHT - 2, hoveredNow ? 0xFF2A8A90 : 0xFF12272B);
            }
            BlockPos pos = summary.pos().pos();
            graphics.drawString(font, Component.translatable("graveless.menu.entry", i + 1),
                    l.listX() + 6, rowY + 3, selected ? TEXT_CYAN : TEXT_BRIGHT);
            graphics.drawString(font, timestamp(summary.epochMillis(), summary.gameTime()),
                    l.listX() + 6, rowY + 13, TEXT_MUTED);
            graphics.drawString(font, "X " + pos.getX() + "  Y " + pos.getY() + "  Z " + pos.getZ(),
                    l.listX() + 6, rowY + 23, TEXT_MUTED);
            graphics.drawString(font, dimensionName(summary.pos().dimension().location()),
                    l.listX() + 6, rowY + 33, selected ? TEXT_CYAN : 0xFF3FA9AD);
            if (summary.recordId().equals(tracked)) {
                drawDiamond(graphics, l.listX() + innerW - 8, rowY + 7);
            }
            if (hoveredNow) {
                setDeferredTooltip(List.of(
                        Component.literal(summary.cause()),
                        Component.translatable("graveless.menu.tooltip_items", summary.itemCount(), summary.xp())
                                .withStyle(style -> style.withColor(0x7FD4D6)),
                        Component.literal(summary.pos().dimension().location().toString())
                                .withStyle(style -> style.withColor(0x5A7F82))),
                        mouseX, mouseY);
            }
        }
        graphics.disableScissor();

        int contentH = graves.size() * ROW_HEIGHT + 4;
        if (contentH > l.listH()) {
            int trackX = l.listX() + l.listW() - 5;
            graphics.fill(trackX, l.listY() + 2, trackX + 3, l.listY() + l.listH() - 2, 0xFF0A1013);
            int thumbH = Math.max(12, l.listH() * l.listH() / contentH);
            int maxScroll = contentH - l.listH();
            int thumbY = l.listY() + 2 + (l.listH() - 4 - thumbH) * Math.min(listScroll, maxScroll) / maxScroll;
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, EDGE_DIM);
        }
    }

    private void drawInventory(GuiGraphics graphics, Layout l, int mouseX, int mouseY) {
        GravelessNetworking.GraveSummary summary = selectedId == null ? null : findSummary(selectedId);
        int centerX = l.midX() + l.midW() / 2;
        graphics.drawCenteredString(font, Component.translatable("graveless.menu.inventory"), centerX, l.top() + 26, TEXT_BRIGHT);
        if (summary != null) {
            graphics.drawCenteredString(font, Component.translatable("graveless.menu.item_count", summary.itemCount()),
                    centerX, l.top() + 36, TEXT_MUTED);
        }

        boolean takeable = summary != null && canTake(summary);
        GravelessNetworking.GraveDetailPayload detail = selectedId == null ? null : details.get(selectedId);
        List<ItemStack> items = detail == null ? List.of() : detail.items();
        int[] metrics = gridMetrics(l);
        int viewRows = metrics[0];
        int totalRows = metrics[1];
        int maxScroll = metrics[2];
        gridScroll = Mth.clamp(gridScroll, 0, maxScroll);

        for (int row = 0; row < viewRows; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int slotX = l.gridX() + col * SLOT_SIZE;
                int slotY = l.gridY() + row * SLOT_SIZE;
                graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, SLOT_FILL);
                graphics.renderOutline(slotX, slotY, SLOT_SIZE, SLOT_SIZE, SLOT_EDGE);
                int index = (gridScroll + row) * GRID_COLS + col;
                boolean hoveredNow = overlay == OVERLAY_NONE
                        && mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                        && mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
                if (hoveredNow) {
                    graphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, 0x3330E5E5);
                }
                if (index < items.size()) {
                    ItemStack stack = items.get(index);
                    graphics.renderItem(stack, slotX + 1, slotY + 1);
                    graphics.renderItemDecorations(font, stack, slotX + 1, slotY + 1);
                    if (hoveredNow) {
                        List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, stack));
                        lines.add(Component.translatable(takeable
                                        ? "graveless.menu.extract_hint" : "graveless.menu.extract_far")
                                .withStyle(style -> style.withColor(takeable ? 0x41E9E9 : 0x6E9B9E)));
                        setDeferredTooltip(lines, mouseX, mouseY);
                    }
                }
            }
        }
        if (maxScroll > 0) {
            int trackX = l.gridX() + GRID_COLS * SLOT_SIZE + 2;
            int trackH = viewRows * SLOT_SIZE;
            graphics.fill(trackX, l.gridY(), trackX + 4, l.gridY() + trackH, SLOT_FILL);
            graphics.renderOutline(trackX, l.gridY(), 4, trackH, SLOT_EDGE);
            int thumbH = Math.max(8, trackH * viewRows / totalRows);
            int thumbY = l.gridY() + (trackH - thumbH) * gridScroll / maxScroll;
            graphics.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, EDGE);
            int shownFrom = gridScroll * GRID_COLS + 1;
            int shownTo = Math.min(items.size(), (gridScroll + viewRows) * GRID_COLS);
            graphics.drawCenteredString(font, Component.translatable("graveless.menu.grid_window",
                    shownFrom, shownTo, items.size()), centerX, l.gridBottom() + 2, TEXT_MUTED);
        }
        if (detail == null && selectedId != null) {
            graphics.drawCenteredString(font, Component.translatable("graveless.menu.loading"),
                    centerX, l.gridY() + viewRows * SLOT_SIZE / 2 - 4, TEXT_MUTED);
        }
    }

    private void drawTerrainPanel(GuiGraphics graphics, Layout l) {
        int centerX = l.rightX() + l.rightW() / 2;
        graphics.drawCenteredString(font, Component.translatable("graveless.menu.terrain"), centerX, l.top() + 26, TEXT_BRIGHT);
        graphics.drawCenteredString(font, Component.translatable("graveless.menu.terrain_size",
                GraveMenuHandlers.TERRAIN_SIZE), centerX, l.top() + 36, TEXT_MUTED);

        int tx = l.terrainX() + 2;
        int ty = l.terrainY() + 2;
        graphics.fill(l.terrainX(), l.terrainY(), l.terrainX() + TERRAIN_PX + 4, l.terrainY() + TERRAIN_PX + 4, 0xFF04080A);
        graphics.renderOutline(l.terrainX(), l.terrainY(), TERRAIN_PX + 4, TERRAIN_PX + 4, EDGE);

        BlockState[] blocks = selectedId == null ? null : dioramaBlocks(selectedId);
        if (blocks != null) {
            PlayerSkin skin = GhostSkins.get(ownerId, ownerName);
            GhostModel model = skin.model() == PlayerSkin.Model.SLIM ? slimModel : wideModel;
            GraveDioramaRenderer.render(graphics, blocks, model, skin, dioramaYaw,
                    tx, ty, tx + TERRAIN_PX, ty + TERRAIN_PX, TERRAIN_PX / 26.0F * dioramaZoom);
        } else {
            graphics.drawCenteredString(font, Component.translatable("graveless.menu.loading"),
                    centerX, l.terrainY() + TERRAIN_PX / 2, TEXT_MUTED);
        }
    }

    private BlockState[] dioramaBlocks(UUID recordId) {
        BlockState[] cached = dioramaCache.get(recordId);
        if (cached != null) {
            return cached;
        }
        GravelessNetworking.GraveDetailPayload detail = details.get(recordId);
        int total = GraveMenuHandlers.TERRAIN_SIZE * GraveMenuHandlers.TERRAIN_SIZE * GraveMenuHandlers.TERRAIN_HEIGHT;
        if (detail == null || detail.terrain().size() < total) {
            return null;
        }
        BlockState[] blocks = new BlockState[total];
        for (int i = 0; i < total; i++) {
            blocks[i] = Block.stateById(detail.terrain().get(i));
        }
        dioramaCache.put(recordId, blocks);
        return blocks;
    }

    private void drawButtons(GuiGraphics graphics, Layout l, int mouseX, int mouseY) {
        boolean hasSelection = selectedId != null && findSummary(selectedId) != null;
        for (ButtonSpec spec : buttonSpecs(l)) {
            drawButton(graphics, l.midX(), spec.y(), l.midW(), BUTTON_H, spec.label(), TEXT_CYAN, EDGE,
                    spec.enabled(), overlay == OVERLAY_NONE && hovered(mouseX, mouseY, l.midX(), spec.y(), l.midW(), BUTTON_H));
        }
        if (canDelete()) {
            int deleteX = l.rightX() + (l.rightW() - 100) / 2;
            drawButton(graphics, deleteX, l.deleteY(), 100, BUTTON_H,
                    Component.translatable(confirmDelete ? "graveless.menu.button.confirm_delete" : "graveless.menu.button.delete"),
                    RED, confirmDelete ? RED : RED_DIM,
                    hasSelection, overlay == OVERLAY_NONE && hovered(mouseX, mouseY, deleteX, l.deleteY(), 100, BUTTON_H));
        }

        GravelessNetworking.GraveSummary summary = hasSelection ? findSummary(selectedId) : null;
        if (summary != null) {
            int infoX = l.rightX() + 2;
            graphics.drawString(font, Component.translatable("graveless.menu.distance", distanceText(summary)),
                    infoX, l.infoY(), TEXT_BRIGHT);
            graphics.drawString(font, Component.translatable("graveless.menu.recoverable", summary.itemCount()),
                    infoX, l.infoY() + 10, TEXT_BRIGHT);
            graphics.drawString(font, Component.translatable("graveless.menu.xp_stored", summary.xp()),
                    infoX, l.infoY() + 20, TEXT_BRIGHT);
        }
    }

    private record OverlayBox(int x, int y, int w, int h, int rowH, int visible) {
    }

    private Component overlayTitle() {
        return Component.translatable(switch (overlay) {
            case OVERLAY_PICKER -> "graveless.menu.overlay.players";
            case OVERLAY_ACCESS -> "graveless.menu.overlay.access";
            case OVERLAY_ACCESS_ADD -> "graveless.menu.overlay.access_add";
            default -> "graveless.menu.overlay.backups";
        });
    }

    private OverlayBox overlayBox(Layout l) {
        int rowH = overlay == OVERLAY_BACKUPS ? 24 : 12;
        int count = switch (overlay) {
            case OVERLAY_PICKER -> players.size();
            case OVERLAY_ACCESS -> Math.max(1, allowed.size());
            case OVERLAY_ACCESS_ADD -> Math.max(1, accessCandidates().size());
            case OVERLAY_BACKUPS -> Math.max(1, backups.size());
            default -> 0;
        };
        int visible = Math.min(count, overlay == OVERLAY_BACKUPS ? 6 : 9);
        int contentW = font.width(overlayTitle());
        switch (overlay) {
            case OVERLAY_PICKER -> {
                for (GravelessNetworking.PlayerEntry entry : players) {
                    contentW = Math.max(contentW, font.width(entry.name()));
                }
            }
            case OVERLAY_ACCESS -> {
                contentW = Math.max(contentW, 130);
                if (allowed.isEmpty()) {
                    contentW = Math.max(contentW, font.width(
                            Component.translatable("graveless.menu.overlay.access_empty")));
                }
                for (GravelessNetworking.PlayerEntry entry : allowed) {
                    contentW = Math.max(contentW, font.width(entry.name()));
                }
            }
            case OVERLAY_ACCESS_ADD -> {
                contentW = Math.max(contentW, font.width(
                        Component.translatable("graveless.menu.overlay.no_players")));
                for (GravelessNetworking.PlayerEntry entry : accessCandidates()) {
                    contentW = Math.max(contentW, font.width(entry.name()));
                }
            }
            case OVERLAY_BACKUPS -> {
                contentW = Math.max(contentW, font.width(
                        Component.translatable("graveless.menu.overlay.backups_empty")));
                contentW = Math.max(contentW, font.width(
                        Component.translatable("graveless.menu.overlay.revive_confirm")));
                for (GravelessNetworking.BackupEntry entry : backups) {
                    BlockPos pos = entry.pos().pos();
                    contentW = Math.max(contentW, font.width(Component.translatable(
                            "graveless.menu.overlay.backup_line",
                            entry.itemCount(), pos.getX(), pos.getY(), pos.getZ())));
                }
            }
            default -> {
            }
        }
        int w = Mth.clamp(contentW + 20, 120, l.panelW() - 16);
        int footer = overlay == OVERLAY_ACCESS ? BUTTON_H + 6 : 0;
        int h = visible * rowH + 18 + footer;
        int x = l.left() + (l.panelW() - w) / 2 + overlayDragX;
        int y = l.top() + Math.max(24, (l.panelH() - h) / 2) + overlayDragY;
        x = Mth.clamp(x, l.left() + 2, l.left() + l.panelW() - w - 2);
        y = Mth.clamp(y, l.top() + 2, Math.max(l.top() + 2, l.top() + l.panelH() - h - 2));
        return new OverlayBox(x, y, w, h, rowH, visible);
    }

    private List<GravelessNetworking.PlayerEntry> accessCandidates() {
        Minecraft mc = Minecraft.getInstance();
        UUID self = mc.player == null ? null : mc.player.getUUID();
        List<GravelessNetworking.PlayerEntry> out = new ArrayList<>();
        for (GravelessNetworking.PlayerEntry entry : players) {
            if (entry.id().equals(self)) {
                continue;
            }
            boolean already = allowed.stream().anyMatch(a -> a.id().equals(entry.id()));
            if (!already) {
                out.add(entry);
            }
        }
        return out;
    }

    private void drawOverlay(GuiGraphics graphics, Layout l, int mouseX, int mouseY) {
        if (overlay == OVERLAY_NONE) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        graphics.fill(l.left(), l.top(), l.left() + l.panelW(), l.top() + l.panelH(), 0x99020608);
        OverlayBox box = overlayBox(l);
        graphics.fill(box.x(), box.y(), box.x() + box.w(), box.y() + box.h(), 0xFF060B0D);
        graphics.fill(box.x(), box.y(), box.x() + box.w(), box.y() + 14, 0xFF0B1417);
        graphics.renderOutline(box.x(), box.y(), box.w(), box.h(), EDGE);
        graphics.drawCenteredString(font, overlayTitle(), box.x() + box.w() / 2, box.y() + 4, TEXT_CYAN);

        int listTop = box.y() + 16;
        switch (overlay) {
            case OVERLAY_PICKER -> drawOverlayPlayers(graphics, box, listTop, mouseX, mouseY, players, null);
            case OVERLAY_ACCESS -> {
                if (allowed.isEmpty()) {
                    graphics.drawCenteredString(font, Component.translatable("graveless.menu.overlay.access_empty"),
                            box.x() + box.w() / 2, listTop + 2, TEXT_MUTED);
                } else {
                    drawOverlayPlayers(graphics, box, listTop, mouseX, mouseY, allowed,
                            Component.translatable("graveless.menu.overlay.revoke_hint"));
                }
                int fy = box.y() + box.h() - BUTTON_H - 4;
                int half = (box.w() - 12) / 2;
                drawButton(graphics, box.x() + 4, fy, half, BUTTON_H,
                        Component.translatable("graveless.menu.button.access_add"), TEXT_CYAN, EDGE, true,
                        hovered(mouseX, mouseY, box.x() + 4, fy, half, BUTTON_H));
                drawButton(graphics, box.x() + 8 + half, fy, half, BUTTON_H,
                        Component.translatable("graveless.menu.button.access_clear"), RED, RED_DIM,
                        !allowed.isEmpty(), hovered(mouseX, mouseY, box.x() + 8 + half, fy, half, BUTTON_H));
            }
            case OVERLAY_ACCESS_ADD -> {
                List<GravelessNetworking.PlayerEntry> candidates = accessCandidates();
                if (candidates.isEmpty()) {
                    graphics.drawCenteredString(font, Component.translatable("graveless.menu.overlay.no_players"),
                            box.x() + box.w() / 2, listTop + 2, TEXT_MUTED);
                } else {
                    drawOverlayPlayers(graphics, box, listTop, mouseX, mouseY, candidates, null);
                }
            }
            case OVERLAY_BACKUPS -> drawOverlayBackups(graphics, box, listTop, mouseX, mouseY);
            default -> {
            }
        }
        graphics.pose().popPose();
    }

    private void drawOverlayPlayers(GuiGraphics graphics, OverlayBox box, int listTop,
                                    int mouseX, int mouseY, List<GravelessNetworking.PlayerEntry> entries,
                                    Component hoverHint) {
        int maxScroll = Math.max(0, entries.size() - box.visible());
        overlayScroll = Mth.clamp(overlayScroll, 0, maxScroll);
        for (int i = 0; i < box.visible(); i++) {
            int index = overlayScroll + i;
            if (index >= entries.size()) {
                break;
            }
            GravelessNetworking.PlayerEntry entry = entries.get(index);
            int rowY = listTop + i * box.rowH();
            boolean hoveredNow = hovered(mouseX, mouseY, box.x() + 2, rowY, box.w() - 4, box.rowH());
            boolean current = overlay == OVERLAY_PICKER && entry.id().equals(ownerId);
            if (hoveredNow) {
                graphics.fill(box.x() + 2, rowY, box.x() + box.w() - 2, rowY + box.rowH(), 0x3325DFDF);
                if (hoverHint != null) {
                    setDeferredTooltip(List.of(hoverHint), mouseX, mouseY);
                }
            }
            graphics.drawString(font, entry.name(), box.x() + 6, rowY + 2,
                    current ? TEXT_CYAN : hoveredNow ? TEXT_BRIGHT : TEXT_MUTED);
        }
        drawOverlayScrollbar(graphics, box, listTop, entries.size(), maxScroll);
    }

    private void drawOverlayBackups(GuiGraphics graphics, OverlayBox box, int listTop,
                                    int mouseX, int mouseY) {
        if (backups.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("graveless.menu.overlay.backups_empty"),
                    box.x() + box.w() / 2, listTop + 2, TEXT_MUTED);
            return;
        }
        int maxScroll = Math.max(0, backups.size() - box.visible());
        overlayScroll = Mth.clamp(overlayScroll, 0, maxScroll);
        for (int i = 0; i < box.visible(); i++) {
            int index = overlayScroll + i;
            if (index >= backups.size()) {
                break;
            }
            GravelessNetworking.BackupEntry entry = backups.get(index);
            int rowY = listTop + i * box.rowH();
            boolean hoveredNow = hovered(mouseX, mouseY, box.x() + 2, rowY, box.w() - 4, box.rowH());
            boolean armed = index == armedBackup;
            if (hoveredNow || armed) {
                graphics.fill(box.x() + 2, rowY, box.x() + box.w() - 2, rowY + box.rowH(),
                        armed ? 0x5525DFDF : 0x3325DFDF);
            }
            if (armed) {
                graphics.drawString(font, Component.translatable("graveless.menu.overlay.revive_confirm"),
                        box.x() + 6, rowY + 2, TEXT_CYAN);
            } else {
                graphics.drawString(font, timestamp(entry.epochMillis(), entry.gameTime()),
                        box.x() + 6, rowY + 2, hoveredNow ? TEXT_BRIGHT : TEXT_MUTED);
            }
            BlockPos pos = entry.pos().pos();
            graphics.drawString(font, Component.translatable("graveless.menu.overlay.backup_line",
                    entry.itemCount(), pos.getX(), pos.getY(), pos.getZ()),
                    box.x() + 6, rowY + 12, TEXT_MUTED);
        }
        drawOverlayScrollbar(graphics, box, listTop, backups.size(), maxScroll);
    }

    private void drawOverlayScrollbar(GuiGraphics graphics, OverlayBox box, int listTop,
                                      int count, int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }
        int trackX = box.x() + box.w() - 4;
        int trackH = box.visible() * box.rowH();
        graphics.fill(trackX, listTop, trackX + 2, listTop + trackH, 0xFF0A1013);
        int thumbH = Math.max(8, trackH * box.visible() / count);
        int thumbY = listTop + (trackH - thumbH) * overlayScroll / maxScroll;
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, EDGE_DIM);
    }

    private boolean hovered(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private int[] gridMetrics(Layout l) {
        GravelessNetworking.GraveDetailPayload detail = selectedId == null ? null : details.get(selectedId);
        int itemCount = detail == null ? 0 : detail.items().size();
        int viewRows = Math.max(1, (l.gridBottom() - l.gridY()) / SLOT_SIZE);
        int totalRows = Math.max(viewRows, (itemCount + GRID_COLS - 1) / GRID_COLS);
        return new int[]{viewRows, totalRows, Math.max(0, totalRows - viewRows)};
    }

    private int gridIndexAt(Layout l, int mouseX, int mouseY) {
        GravelessNetworking.GraveDetailPayload detail = selectedId == null ? null : details.get(selectedId);
        if (detail == null) {
            return -1;
        }
        int[] metrics = gridMetrics(l);
        int col = (mouseX - l.gridX()) / SLOT_SIZE;
        int row = (mouseY - l.gridY()) / SLOT_SIZE;
        if (mouseX < l.gridX() || col < 0 || col >= GRID_COLS || row < 0 || row >= metrics[0]) {
            return -1;
        }
        int index = (gridScroll + row) * GRID_COLS + col;
        return index < detail.items().size() ? index : -1;
    }

    private void dragGridTo(Layout l, double mouseY) {
        int[] metrics = gridMetrics(l);
        int trackH = metrics[0] * SLOT_SIZE;
        if (metrics[2] <= 0 || trackH <= 0) {
            return;
        }
        double ratio = (mouseY - l.gridY()) / trackH;
        gridScroll = Mth.clamp((int) Math.round(ratio * metrics[1]) - metrics[0] / 2, 0, metrics[2]);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h,
                            Component label, int textColor, int edgeColor, boolean enabled, boolean hoveredNow) {
        int fill = enabled && hoveredNow ? 0xFF12272C : 0xFF0B1417;
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.renderOutline(x, y, w, h, enabled ? edgeColor : 0xFF12272B);
        graphics.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, enabled ? textColor : TEXT_MUTED);
    }

    private String timestamp(long epochMillis, long gameTime) {
        if (epochMillis > 0) {
            return DATE_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
        }
        return Component.translatable("graveless.menu.day", gameTime / 24000L).getString();
    }

    private Component dimensionName(ResourceLocation dimension) {
        StringBuilder pretty = new StringBuilder();
        for (String part : dimension.getPath().split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!pretty.isEmpty()) {
                pretty.append(' ');
            }
            pretty.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return Component.translatableWithFallback(
                "dimension." + dimension.getNamespace() + "." + dimension.getPath(), pretty.toString());
    }

    private String distanceText(GravelessNetworking.GraveSummary summary) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return "?";
        }
        if (!mc.level.dimension().equals(summary.pos().dimension())) {
            return dimensionName(summary.pos().dimension().location()).getString();
        }
        double dist = mc.player.position().distanceTo(Vec3.atCenterOf(summary.pos().pos()));
        return Component.translatable("graveless.menu.blocks", (int) dist).getString();
    }

    @Override
    public boolean mouseClicked(double clickX, double clickY, int button) {
        if (super.mouseClicked(clickX, clickY, button)) {
            return true;
        }
        Layout l = layout();
        int mouseX = (int) clickX;
        int mouseY = (int) clickY;

        if (button != 0) {
            return false;
        }

        if (overlay != OVERLAY_NONE) {
            if (!handleOverlayClick(l, mouseX, mouseY)) {
                overlay = OVERLAY_NONE;
            }
            return true;
        }

        if (hasShiftDown() && selectedId != null && canTakeSelected()) {
            int index = gridIndexAt(l, mouseX, mouseY);
            if (index >= 0) {
                click();
                Services.NETWORK.sendToServer(new GravelessNetworking.GraveExtractPayload(
                        ownerId, selectedId, index));
                return true;
            }
        }

        if (admin && hoveredHeader(l, mouseX, mouseY)) {
            click();
            openOverlay(OVERLAY_PICKER);
            return true;
        }

        if (viewingSelf()) {
            int y = l.bottom() - BUTTON_H;
            int half = (l.listW() - 4) / 2;
            if (hovered(mouseX, mouseY, l.listX(), y, half, BUTTON_H)) {
                click();
                Services.NETWORK.sendToServer(new GravelessNetworking.ProfileActionPayload(
                        GravelessNetworking.ProfileActionPayload.ACTION_TOGGLE_ENABLED, Optional.empty()));
                return true;
            }
            if (hovered(mouseX, mouseY, l.listX() + half + 4, y, half, BUTTON_H)) {
                click();
                openOverlay(OVERLAY_ACCESS);
                return true;
            }
        }

        boolean hasSelection = selectedId != null && findSummary(selectedId) != null;

        int deleteX = l.rightX() + (l.rightW() - 100) / 2;
        if (hasSelection && canDelete() && hovered(mouseX, mouseY, deleteX, l.deleteY(), 100, BUTTON_H)) {
            click();
            if (confirmDelete) {
                Services.NETWORK.sendToServer(new GravelessNetworking.GraveActionPayload(
                        ownerId, selectedId, GravelessNetworking.GraveActionPayload.ACTION_DELETE));
                confirmDelete = false;
            } else {
                confirmDelete = true;
            }
            return true;
        }
        confirmDelete = false;

        for (ButtonSpec spec : buttonSpecs(l)) {
            if (spec.enabled() && hovered(mouseX, mouseY, l.midX(), spec.y(), l.midW(), BUTTON_H)) {
                click();
                runAction(spec.action());
                return true;
            }
        }

        int trackX = l.gridX() + GRID_COLS * SLOT_SIZE + 2;
        int[] metrics = gridMetrics(l);
        if (metrics[2] > 0 && hovered(mouseX, mouseY, trackX - 1, l.gridY(), 6, metrics[0] * SLOT_SIZE)) {
            draggingGridBar = true;
            dragGridTo(l, mouseY);
            return true;
        }

        if (hovered(mouseX, mouseY, l.terrainX(), l.terrainY(), TERRAIN_PX + 4, TERRAIN_PX + 4)) {
            draggingDiorama = true;
            return true;
        }

        if (hovered(mouseX, mouseY, l.listX(), l.listY(), l.listW(), l.listH())) {
            int index = (mouseY - l.listY() - 2 + listScroll) / ROW_HEIGHT;
            if (index >= 0 && index < graves.size()) {
                click();
                select(graves.get(index).recordId());
            }
            return true;
        }
        return false;
    }

    private void openOverlay(int mode) {
        overlay = mode;
        overlayScroll = 0;
        armedBackup = -1;
        confirmDelete = false;
        overlayDragX = 0;
        overlayDragY = 0;
    }

    private boolean handleOverlayClick(Layout l, int mouseX, int mouseY) {
        OverlayBox box = overlayBox(l);
        if (!hovered(mouseX, mouseY, box.x(), box.y(), box.w(), box.h())) {
            return false;
        }
        if (mouseY < box.y() + 14) {
            draggingOverlay = true;
            return true;
        }
        int listTop = box.y() + 16;
        int rowIndex = overlayScroll + (mouseY - listTop) / box.rowH();
        boolean inRows = mouseY >= listTop && mouseY < listTop + box.visible() * box.rowH();

        switch (overlay) {
            case OVERLAY_PICKER -> {
                if (inRows && rowIndex >= 0 && rowIndex < players.size()) {
                    click();
                    GravelessNetworking.PlayerEntry entry = players.get(rowIndex);
                    overlay = OVERLAY_NONE;
                    if (!entry.id().equals(ownerId)) {
                        Services.NETWORK.sendToServer(new GravelessNetworking.GraveViewRequestPayload(entry.id()));
                    }
                }
            }
            case OVERLAY_ACCESS -> {
                int fy = box.y() + box.h() - BUTTON_H - 4;
                int half = (box.w() - 12) / 2;
                if (hovered(mouseX, mouseY, box.x() + 4, fy, half, BUTTON_H)) {
                    click();
                    openOverlay(OVERLAY_ACCESS_ADD);
                    return true;
                }
                if (!allowed.isEmpty() && hovered(mouseX, mouseY, box.x() + 8 + half, fy, half, BUTTON_H)) {
                    click();
                    Services.NETWORK.sendToServer(new GravelessNetworking.ProfileActionPayload(
                            GravelessNetworking.ProfileActionPayload.ACTION_CLEAR, Optional.empty()));
                    return true;
                }
                if (inRows && rowIndex >= 0 && rowIndex < allowed.size()) {
                    click();
                    Services.NETWORK.sendToServer(new GravelessNetworking.ProfileActionPayload(
                            GravelessNetworking.ProfileActionPayload.ACTION_DENY,
                            Optional.of(allowed.get(rowIndex).id())));
                }
            }
            case OVERLAY_ACCESS_ADD -> {
                List<GravelessNetworking.PlayerEntry> candidates = accessCandidates();
                if (inRows && rowIndex >= 0 && rowIndex < candidates.size()) {
                    click();
                    Services.NETWORK.sendToServer(new GravelessNetworking.ProfileActionPayload(
                            GravelessNetworking.ProfileActionPayload.ACTION_ALLOW,
                            Optional.of(candidates.get(rowIndex).id())));
                    overlay = OVERLAY_ACCESS;
                }
            }
            case OVERLAY_BACKUPS -> {
                if (inRows && rowIndex >= 0 && rowIndex < backups.size()) {
                    click();
                    if (armedBackup == rowIndex) {
                        Services.NETWORK.sendToServer(new GravelessNetworking.BackupRevivePayload(
                                ownerId, backups.get(rowIndex).fileName()));
                        armedBackup = -1;
                    } else {
                        armedBackup = rowIndex;
                    }
                } else {
                    armedBackup = -1;
                }
            }
            default -> {
            }
        }
        return true;
    }

    private void runAction(int action) {
        switch (action) {
            case ACTION_TELEPORT -> {
                Services.NETWORK.sendToServer(new GravelessNetworking.GraveActionPayload(
                        ownerId, selectedId, GravelessNetworking.GraveActionPayload.ACTION_TELEPORT));
                onClose();
            }
            case ACTION_TRACK -> Services.NETWORK.sendToServer(new GravelessNetworking.GraveActionPayload(
                    ownerId, selectedId, GravelessNetworking.GraveActionPayload.ACTION_TRACK));
            case ACTION_RESTORE -> Services.NETWORK.sendToServer(new GravelessNetworking.GraveActionPayload(
                    ownerId, selectedId, GravelessNetworking.GraveActionPayload.ACTION_RESTORE));
            case ACTION_CLAIM_XP -> Services.NETWORK.sendToServer(new GravelessNetworking.GraveActionPayload(
                    ownerId, selectedId, GravelessNetworking.GraveActionPayload.ACTION_CLAIM_XP));
            case ACTION_BACKUPS -> {
                openOverlay(OVERLAY_BACKUPS);
                Services.NETWORK.sendToServer(new GravelessNetworking.BackupListRequestPayload(ownerId));
            }
            default -> {
            }
        }
    }

    private boolean hoveredHeader(Layout l, int mouseX, int mouseY) {
        Component label = Component.translatable("graveless.menu.viewing", ownerName.toUpperCase());
        int w = font.width(label) + 12;
        int x = l.listX() + (l.listW() - w) / 2;
        return hovered(mouseX, mouseY, x, l.top() + 23, w, 12);
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingOverlay) {
            overlayDragX += (int) Math.round(dx);
            overlayDragY += (int) Math.round(dy);
            return true;
        }
        if (draggingDiorama) {
            dioramaYaw = (dioramaYaw + (float) dx * 1.5F) % 360.0F;
            return true;
        }
        if (draggingGridBar) {
            dragGridTo(layout(), mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingGridBar = false;
        draggingDiorama = false;
        draggingOverlay = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Layout l = layout();
        if (overlay != OVERLAY_NONE) {
            overlayScroll -= (int) Math.signum(scrollY);
            return true;
        }
        if (hovered((int) mouseX, (int) mouseY, l.terrainX(), l.terrainY(), TERRAIN_PX + 4, TERRAIN_PX + 4)) {
            float factor = scrollY > 0 ? 1.15F : 1.0F / 1.15F;
            dioramaZoom = Mth.clamp(dioramaZoom * factor, 0.5F, 3.5F);
            return true;
        }
        if (hovered((int) mouseX, (int) mouseY, l.listX(), l.listY(), l.listW(), l.listH())) {
            listScroll -= (int) (scrollY * 14);
            clampScroll();
            return true;
        }
        int[] metrics = gridMetrics(l);
        boolean overMid = hovered((int) mouseX, (int) mouseY, l.midX(), l.top() + 24, l.midW(),
                l.gridBottom() - l.top() - 24);
        boolean overPanel = hovered((int) mouseX, (int) mouseY, l.left(), l.top(), l.panelW(), l.panelH());
        if (metrics[2] > 0 && (overMid || overPanel)) {
            gridScroll = Mth.clamp(gridScroll - (int) Math.signum(scrollY), 0, metrics[2]);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void clampScroll() {
        Layout l = layout();
        int maxScroll = Math.max(0, graves.size() * ROW_HEIGHT + 4 - l.listH());
        listScroll = Mth.clamp(listScroll, 0, maxScroll);
    }

    private void revealSelection() {
        int index = -1;
        for (int i = 0; i < graves.size(); i++) {
            if (graves.get(i).recordId().equals(selectedId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        Layout l = layout();
        int rowTop = 2 + index * ROW_HEIGHT;
        if (listScroll > rowTop) {
            listScroll = rowTop;
        } else if (listScroll < rowTop + ROW_HEIGHT - l.listH()) {
            listScroll = rowTop + ROW_HEIGHT - l.listH();
        }
        clampScroll();
    }

    private boolean canTake(GravelessNetworking.GraveSummary summary) {
        if (admin) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.level.dimension().equals(summary.pos().dimension())) {
            return false;
        }
        GhostClientManager.ClientGhost ghost = GhostClientManager.get(summary.recordId());
        BlockPos pos = ghost == null ? summary.pos().pos() : ghost.pos();
        int range = GravelessConfig.SERVER.claimRange.get() + 2;
        return mc.player.position().distanceToSqr(Vec3.atCenterOf(pos)) <= (double) range * range;
    }

    private boolean canTakeSelected() {
        GravelessNetworking.GraveSummary summary = selectedId == null ? null : findSummary(selectedId);
        return summary != null && canTake(summary);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
