package vice.sol_valheim;


import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;

#if PRE_CURRENT_MC_1_19_2
import net.minecraft.client.renderer.*;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;

#elif POST_CURRENT_MC_1_20_1
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

#endif

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;

public class FoodHUD implements ClientGuiEvent.RenderHud
{
    static Minecraft client;

    private static final String BACKGROUND_SPRITE = "textures/gui/sprites/meter_background/default.png";
    private static final String BACKGROUND_LARGE_SPRITE = "textures/gui/sprites/meter_background/default_large.png";
    private static final String EMPTY_SPRITE = "textures/gui/sprites/panel_background/empty.png";
    private static final String EMPTY_LARGE_SPRITE = "textures/gui/sprites/panel_background/empty_large.png";
    private static final String DRINK_SPRITE = "textures/gui/sprites/placeholder_icon/drink.png";
    private static final String DRINK_LARGE_SPRITE = "textures/gui/sprites/placeholder_icon/drink_large.png";
    private static final String FOOD_SPRITE = "textures/gui/sprites/placeholder_icon/food.png";
    private static final String FOOD_LARGE_SPRITE = "textures/gui/sprites/placeholder_icon/food_large.png";
    private static final String PANEL_SPRITE = "textures/gui/sprites/panel_background/default.png";
    private static final String PANEL_LARGE_SPRITE = "textures/gui/sprites/panel_background/default_large.png";
    private static final String OUTLINE_SPRITE = "textures/gui/sprites/meter_outline/default.png";
    private static final String OUTLINE_LARGE_SPRITE = "textures/gui/sprites/meter_outline/default_large.png";

    private static final int WHITE = FastColor.ARGB32.color(255, 255, 255, 255);
    private static final int WHITE_BG = FastColor.ARGB32.color(128, 255, 255, 255);
    private static final int YELLOW = FastColor.ARGB32.color(255, 255, 200, 37);
    private static final int YELLOW_BG = FastColor.ARGB32.color(150, 255, 200, 37);
    private static final int RED = FastColor.ARGB32.color(255, 237, 57, 57);

    public FoodHUD() {
        ClientGuiEvent.RENDER_HUD.register(this);
        client = Minecraft.getInstance();
    }

    @Override
    public void renderHud(#if PRE_CURRENT_MC_1_19_2 PoseStack #elif POST_CURRENT_MC_1_20_1 GuiGraphics #endif graphics, float tickDelta) {
        if (client.player == null)
            return;

        var solPlayer = (PlayerEntityMixinDataAccessor) client.player;

        var foodData = solPlayer.sol_valheim$getFoodData();
        if (foodData == null)
            return;

        boolean useLargeIcons = SOLValheim.Config.client.useLargeIcons;

        int width = client.getWindow().getGuiScaledWidth() / 2 + 91;
        int height = client.getWindow().getGuiScaledHeight() - 39 - (useLargeIcons ? 6 : 0);

        int offset = 1;
        int size = useLargeIcons ? 14 : 9;

        // Food
        for (var food : foodData.ItemEntries) {
            renderFoodSlot(graphics, food, width, size, offset, height, useLargeIcons);
            offset++;
        }
        // Empty Food
        for (int i = 0; i < foodData.MaxItemSlots - foodData.ItemEntries.size(); i++) {
            int startWidth = width - (size * offset) - offset + 1;
            String panelSprite = useLargeIcons ? EMPTY_LARGE_SPRITE : EMPTY_SPRITE;
            String iconSprite = useLargeIcons ? FOOD_LARGE_SPRITE : FOOD_SPRITE;
            blit(graphics, panelSprite, size, size, startWidth, height, WHITE);
            blit(graphics, iconSprite, size, size, startWidth, height, WHITE);
            offset++;
        }
        // Drink
        if (foodData.DrinkSlot != null) {
            renderFoodSlot(graphics, foodData.DrinkSlot, width, size, offset, height, useLargeIcons);
        } else {
            int startWidth = width - (size * offset) - offset + 1;
            String panelSprite = useLargeIcons ? EMPTY_LARGE_SPRITE : EMPTY_SPRITE;
            String iconSprite = useLargeIcons ? DRINK_LARGE_SPRITE : DRINK_SPRITE;
            blit(graphics, panelSprite, size, size, startWidth, height, WHITE);
            blit(graphics, iconSprite, size, size, startWidth, height, WHITE);
        }

    }

    private static void renderFoodSlot(#if PRE_CURRENT_MC_1_19_2 PoseStack #elif POST_CURRENT_MC_1_20_1 GuiGraphics #endif graphics, ValheimFoodData.EatenFoodItem food, int width, int size, int offset, int height, boolean useLargeIcons)
    {
        var foodConfig = ModConfig.getFoodConfig(food.item);
        if (foodConfig == null)
            return;

        var isDrink = food.item.getDefaultInstance().getUseAnimation() == UseAnim.DRINK;

        int startWidth = width - (size * offset) - offset + 1;
        float ticksLeftPercent = Float.min(1.0F, (float) food.ticksLeft / foodConfig.getTime());
        boolean canEat = food.canEatEarly();

        // todo replace drink background to use a different sprite instead of tinting
        int bgColor = isDrink ? FastColor.ARGB32.color(200, 26, 52, 81) : FastColor.ARGB32.color(180, 0, 0, 0);
        int barColor = canEat ? YELLOW : WHITE;
        int barBgColor = canEat ? YELLOW_BG : WHITE_BG;

        var time = (float) food.ticksLeft / (20 * 60);
        var scale = useLargeIcons ? 0.75f : 0.5f;
        var isSeconds = false;

        if (time < 1f) {
            isSeconds = true;
            time =  (float) food.ticksLeft / 20;
        }
        var minutes = String.format("%.0f", time);

        var pose = #if PRE_CURRENT_MC_1_19_2 graphics #elif POST_CURRENT_MC_1_20_1 graphics.pose() #endif;

        // Background
        String panelTexture = useLargeIcons ? PANEL_LARGE_SPRITE : PANEL_SPRITE;
        blit(graphics, panelTexture, size, size, startWidth, height, bgColor);
        // Meter Background
        String bgTexture = useLargeIcons ? BACKGROUND_LARGE_SPRITE : BACKGROUND_SPRITE;
        renderRadialBar(graphics, bgTexture, size, size, startWidth, height, barBgColor, ticksLeftPercent);
        // Outline
        String outlineTexture = useLargeIcons ? OUTLINE_LARGE_SPRITE : OUTLINE_SPRITE;
        var blinkIntensity = 1 - (Math.min(ticksLeftPercent, 0.5) / 0.5) ;
        var outlineAlpha = canEat ? 1 - (((Math.sin((double) food.ticksLeft / 5) / 2) + 0.5) * blinkIntensity) : 1;
        var outlineColor = FastColor.ARGB32.color((int) (outlineAlpha * 255), FastColor.ARGB32.red(barColor), FastColor.ARGB32.green(barColor), FastColor.ARGB32.blue(barColor));
        renderRadialBar(graphics, outlineTexture, size, size, startWidth, height, outlineColor, ticksLeftPercent);

        // Item
        pose.pushPose(); // Item/Text
        pose.scale(scale, scale, scale);
        pose.translate(startWidth * (useLargeIcons ? 0.3333f : 1f), height * (useLargeIcons ? 0.3333f : 1f), 0f);

        if (food.item == Items.CAKE && Platform.isModLoaded("farmersdelight")) {
            var cakeSlice = SOLValheim.ITEMS.getRegistrar().get(new ResourceLocation("farmersdelight:cake_slice"));
            renderGUIItem(graphics, new ItemStack(cakeSlice == null ? food.item : cakeSlice, 1), startWidth + 1, height + 1);
        }
        else {
            renderGUIItem(graphics, new ItemStack(food.item, 1), startWidth + 1, height + 1);
        }

        // Text
        if (useLargeIcons) {
            pose.pushPose(); // Text
            pose.translate(0.0f, 0.0f, 200.0f);
            drawFont(graphics, minutes, startWidth + (minutes.length() > 1 ? 6 : 12), height + 10, isSeconds ? RED : WHITE);
            if (!foodConfig.extraEffects.isEmpty())
                drawFont(graphics, "+" + foodConfig.extraEffects.size(), startWidth + 6, height, YELLOW);
            pose.popPose(); // Text
        }
        pose.popPose(); // Item/Text
    }

    private static void fill(#if PRE_CURRENT_MC_1_19_2 PoseStack #elif POST_CURRENT_MC_1_20_1 GuiGraphics #endif graphics, int width, int height, int x, int y, int color)
    {
        #if PRE_CURRENT_MC_1_19_2
        GuiComponent.fill(graphics, width, height, x, y, color);
        #elif POST_CURRENT_MC_1_20_1
        graphics.fill(width, height, x, y, color);
        #endif
    }

    private static void blit(#if PRE_CURRENT_MC_1_19_2 PoseStack #elif POST_CURRENT_MC_1_20_1 GuiGraphics #endif graphics, String texture, int width, int height, int x, int y, int color) {
        #if PRE_CURRENT_MC_1_19_2
        Matrix4f matrix4f = graphics.last().pose();
        #elif POST_CURRENT_MC_1_20_1
        Matrix4f matrix4f = graphics.pose().last().pose();
        #endif
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.setShaderTexture(0, ResourceLocation.tryBuild("sol_valheim", texture));
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);

        buffer.vertex(matrix4f, x, y, 0).color(color).uv(0, 0).endVertex();
        buffer.vertex(matrix4f, x, y + height, 0).color(color).uv(0, 1).endVertex();
        buffer.vertex(matrix4f, x + width, y + height, 0).color(color).uv(1, 1).endVertex();
        buffer.vertex(matrix4f, x + width, y, 0).color(color).uv(1, 0).endVertex();

        tesselator.end();
        RenderSystem.disableBlend();
    }

    private static Vector3f calcCircularCoords(float alpha) {
        var angle = -alpha * 2 * Math.PI;
        var hyp = Math.sqrt(2);
        var a = Mth.clamp(Math.sin(angle) * hyp, -1, 1);
        var b = Mth.clamp(-Math.cos(angle) * hyp, -1, 1);
        return new Vector3f((float) a, (float) b, 0);
    }

    private static void renderRadialBar(#if PRE_CURRENT_MC_1_19_2 PoseStack #elif POST_CURRENT_MC_1_20_1 GuiGraphics #endif graphics, String texture, int width, int height, int x, int y, int color, float alpha) {
        #if PRE_CURRENT_MC_1_19_2
        Matrix4f matrix4f = graphics.last().pose();
        #elif POST_CURRENT_MC_1_20_1
        Matrix4f matrix4f = graphics.pose().last().pose();
        #endif
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.setShaderTexture(0, ResourceLocation.tryBuild("sol_valheim", texture));
        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR_TEX);

        float middleX = x + ((float) width / 2);
        float middleY = y + ((float) height / 2);

        // Center Vertex
        if (alpha < 1.00) {
            buffer.vertex(matrix4f, middleX, middleY, 0).color(color).uv(0.5F,0.5F).endVertex();
        }
        // Start Vertex - TOP CENTRE
        buffer.vertex(matrix4f, middleX, y, 0).color(color).uv(0.5F, 0F).endVertex();
        // Intermediate Vertices
        if (alpha > 0.125) { // TOP LEFT
            buffer.vertex(matrix4f, x, y, 0).color(color).uv(0F, 0F).endVertex();
        }
        if (alpha > 0.375) { // BOTTOM LEFT
            buffer.vertex(matrix4f, x, y + height, 0).color(color).uv(0F, 1F).endVertex();
        }
        if (alpha > 0.625) { // BOTTOM RIGHT
            buffer.vertex(matrix4f, x + width, y + height, 0).color(color).uv(1F, 1F).endVertex();
        }
        if (alpha > 0.875) { // TOP RIGHT
            buffer.vertex(matrix4f, x + width, y, 0).color(color).uv(1F, 0F).endVertex();
        }
        // Endpoint Vertex
        if (alpha < 1.00) {
            Vector3f ePos = calcCircularCoords(alpha);
            buffer.vertex(matrix4f, (middleX + (ePos.x() * ((float) width / 2))), (middleY + (ePos.y() * ((float) height / 2))), 0)
                    .color(color)
                    .uv( (ePos.x() / 2) + 0.5F, (ePos.y() / 2) + 0.5F)
                    .endVertex();
        }
        tesselator.end();
        RenderSystem.disableBlend();
    }

    private static void renderGUIItem(#if PRE_CURRENT_MC_1_19_2 PoseStack #elif POST_CURRENT_MC_1_20_1 GuiGraphics #endif graphics, ItemStack stack, int x, int y)
    {
        #if PRE_CURRENT_MC_1_19_2

        var itemRenderer = client.getItemRenderer();
        var bakedModel = itemRenderer.getModel(stack, null, null, 0);

        //itemRenderer.textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS).setFilter(false, false);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        poseStack.translate((double)x, (double)y, (double)(100.0F + itemRenderer.blitOffset));
        poseStack.translate(8.0, 8.0, 0.0);
        poseStack.scale(1.0F, -1.0F, 1.0F);
        poseStack.scale(16.0F, 16.0F, 16.0F);

        var useLargeIcons = true;
        var scale = useLargeIcons ? 0.75f : 0.5f;
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.15, 0.15, 0f);

        RenderSystem.applyModelViewMatrix();
        PoseStack poseStack2 = new PoseStack();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean bl = !bakedModel.usesBlockLight();
        if (bl) {
            Lighting.setupForFlatItems();
        }

        itemRenderer.render(stack, ItemTransforms.TransformType.GUI, false, poseStack2, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, bakedModel);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (bl) {
            Lighting.setupFor3DItems();
        }

        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();

        #elif POST_CURRENT_MC_1_20_1
        graphics.renderItem(stack, x, y);
        #endif
    }

    private static void drawFont(#if PRE_CURRENT_MC_1_19_2 PoseStack #elif POST_CURRENT_MC_1_20_1 GuiGraphics #endif graphics, String str, int x, int y, int color)
    {
        #if PRE_CURRENT_MC_1_19_2
        client.font.draw(graphics, str, x, y, color);
        #elif POST_CURRENT_MC_1_20_1
        graphics.drawString(client.font, str, x, y, color);
        #endif
    }


}
