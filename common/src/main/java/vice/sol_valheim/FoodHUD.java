package vice.sol_valheim;


import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;

#if PRE_CURRENT_MC_1_19_2
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.level.Level;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiComponent;

#elif POST_CURRENT_MC_1_20_1

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.CommonColors;

#endif

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import org.joml.Matrix4f;
import org.joml.Vector2d;
import org.joml.Vector2f;
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;

public class FoodHUD implements ClientGuiEvent.RenderHud
{
    static Minecraft client;

    private static final String BACKGROUND_SPRITE = "textures/gui/sprites/meter_background/default.png";
    private static final String BACKGROUND_LARGE_SPRITE = "textures/gui/sprites/meter_background/default_large.png";
    private static final String OUTLINE_SPRITE = "textures/gui/sprites/meter_outline/default.png";
    private static final String OUTLINE_LARGE_SPRITE = "textures/gui/sprites/meter_outline/default_large.png";
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

        for (var food : foodData.ItemEntries) {
            renderFoodSlot(graphics, food, width, size, offset, height, useLargeIcons);
            offset++;
        }

        if (foodData.DrinkSlot != null)
            renderFoodSlot(graphics, foodData.DrinkSlot, width, size, offset, height, useLargeIcons);
    }

    private static void renderFoodSlot(#if PRE_CURRENT_MC_1_19_2 PoseStack #elif POST_CURRENT_MC_1_20_1 GuiGraphics #endif graphics, ValheimFoodData.EatenFoodItem food, int width, int size, int offset, int height, boolean useLargeIcons)
    {
        var foodConfig = ModConfig.getFoodConfig(food.item);
        if (foodConfig == null)
            return;

        var isDrink = food.item.getDefaultInstance().getUseAnimation() == UseAnim.DRINK;
        int white = FastColor.ARGB32.color(255, 255, 255, 255);
        int whiteBg = FastColor.ARGB32.color(128, 255, 255, 255);
        int yellow = FastColor.ARGB32.color(255, 255, 200, 37);
        int yellowBg = FastColor.ARGB32.color(150, 255, 200, 37);
        int red = FastColor.ARGB32.color(255, 237, 57, 57);

        int startWidth = width - (size * offset) - offset + 1;
        float ticksLeftPercent = Float.min(1.0F, (float) food.ticksLeft / foodConfig.getTime());
        boolean canEat = food.canEatEarly();

        int bgColor = isDrink ? FastColor.ARGB32.color(96, 52, 104, 163) : FastColor.ARGB32.color(96, 0, 0, 0);
        int barColor = canEat ? yellow : white;
        int barBgColor = canEat ? yellowBg : whiteBg;

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
        fill(graphics, startWidth, height, startWidth + size, height + size, bgColor);
        String bgTexture = useLargeIcons ? BACKGROUND_LARGE_SPRITE : BACKGROUND_SPRITE;
        renderRadialBar(graphics, bgTexture, size, size, startWidth, height, barBgColor, ticksLeftPercent, useLargeIcons);

        // Outline
        String outlineTexture = useLargeIcons ? OUTLINE_LARGE_SPRITE : OUTLINE_SPRITE;
        renderRadialBar(graphics, outlineTexture, size, size, startWidth, height, barColor, ticksLeftPercent, useLargeIcons);

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
            drawFont(graphics, minutes, startWidth + (minutes.length() > 1 ? 6 : 12), height + 10, isSeconds ? red : white);
            if (!foodConfig.extraEffects.isEmpty())
                drawFont(graphics, "+" + foodConfig.extraEffects.size(), startWidth + 6, height, yellow);
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

    private static Vector2f calcCircularCoords(float alpha) {
        var angle = -alpha * 2 * Math.PI;
        var hyp = Math.sqrt(2);
        var a = Mth.clamp(Math.sin(angle) * hyp, -1, 1);
        var b = Mth.clamp(-Math.cos(angle) * hyp, -1, 1);
        return new Vector2f((float) a, (float) b);
    }

    private static void renderRadialBar(GuiGraphics graphics, String texture, int width, int height, int x, int y, int color, float alpha, boolean useLargeIcons) {
        #if PRE_CURRENT_MC_1_19_2
        // todo
        #elif POST_CURRENT_MC_1_20_1
        Matrix4f matrix4f = graphics.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
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
            Vector2f ePos = calcCircularCoords(alpha);
            buffer.vertex(matrix4f, (middleX + (ePos.x * ((float) width / 2))), (middleY + (ePos.y * ((float) height / 2))), 0)
                    .color(color)
                    .uv( (ePos.x / 2) + 0.5F, (ePos.y / 2) + 0.5F)
                    .endVertex();
        }
        tesselator.end();
        RenderSystem.disableBlend();
        #endif
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
