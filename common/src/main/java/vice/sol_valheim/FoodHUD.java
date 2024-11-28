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
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;

public class FoodHUD implements ClientGuiEvent.RenderHud
{
    static Minecraft client;

    private static int HudHeight = 10;

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

        int startWidth = width - (size * offset) - offset + 1;
        float ticksLeftPercent = Float.min(1.0F, (float) food.ticksLeft / foodConfig.getTime());
        boolean canEat = ticksLeftPercent < SOLValheim.Config.common.eatAgainPercentage;
        int bgColor = isDrink ? FastColor.ARGB32.color(96, 52, 104, 163) : FastColor.ARGB32.color(96, 0, 0, 0);
        int barColor = canEat ? yellow : white;
        int barBgColor = canEat ? yellowBg : whiteBg;

        var time = (float) food.ticksLeft / (20 * 60);
        var scale = useLargeIcons ? 0.75f : 0.5f;
        var isSeconds = false;
        var minutes = String.format("%.0f", time);

        if (time < 1f)
        {
            isSeconds = true;
            time =  (float) food.ticksLeft / 20;
        }

        var pose = #if PRE_CURRENT_MC_1_19_2 graphics #elif POST_CURRENT_MC_1_20_1 graphics.pose() #endif;

        fill(graphics, startWidth, height, startWidth + size, height + size, bgColor);
        fillCircularBar(graphics, size, size, startWidth, height, barColor, barBgColor, ticksLeftPercent);

        pose.pushPose();
        pose.scale(scale, scale, scale);
        pose.translate(startWidth * (useLargeIcons ? 0.3333f : 1f), height * (useLargeIcons ? 0.3333f : 1f), 0f);

        if (food.item == Items.CAKE && Platform.isModLoaded("farmersdelight"))
        {
            var cakeSlice = SOLValheim.ITEMS.getRegistrar().get(new ResourceLocation("farmersdelight:cake_slice"));
            renderGUIItem(graphics, new ItemStack(cakeSlice == null ? food.item : cakeSlice, 1), startWidth + 1, height + 1);
        }
        else
        {
            renderGUIItem(graphics, new ItemStack(food.item, 1), startWidth + 1, height + 1);
        }

        pose.pushPose();
        pose.translate(0.0f, 0.0f, 200.0f);

        drawFont(graphics, minutes, startWidth + (minutes.length() > 1 ? 6 : 12), height + 10, isSeconds ? FastColor.ARGB32.color(255, 237, 57, 57) : FastColor.ARGB32.color(255, 255, 255, 255));
        if (!foodConfig.extraEffects.isEmpty())
            drawFont(graphics, "+" + foodConfig.extraEffects.size(), startWidth + 6, height, yellow);

        pose.popPose();
        pose.popPose();
    }

    private static void fill(#if PRE_CURRENT_MC_1_19_2 PoseStack #elif POST_CURRENT_MC_1_20_1 GuiGraphics #endif graphics, int width, int height, int x, int y, int color)
    {
        #if PRE_CURRENT_MC_1_19_2
        GuiComponent.fill(graphics, width, height, x, y, color);
        #elif POST_CURRENT_MC_1_20_1
        graphics.fill(width, height, x, y, color);
        #endif
    }

    // There's probably a function to make this easier but idk
    private static Vector2d calcCircularCoords(float alpha) {
        var angle = alpha * 2 * Math.PI;
        var clampedAngle = angle % (Math.PI / 2);
        var a = Math.min(Math.cos(clampedAngle)/Math.sin(clampedAngle), 1);
        var b = Math.min(Math.tan(clampedAngle), 1);

        double a2,b2;
        if (angle < Math.PI / 2) {
            a2 = -b;
            b2 = -a;
        } else if (angle < Math.PI) {
            a2 = -a;
            b2 = b;
        } else if (angle < Math.PI / 2 * 3) {
            a2 = b;
            b2 = a;
        } else {
            a2 = a;
            b2 = -b;
        }

        return new Vector2d(a2, b2);
    }

    private static void fillCircularBar(GuiGraphics graphics, int width, int height, int x, int y, int color, int bgColor, float alpha) {
        #if PRE_CURRENT_MC_1_19_2
        // todo
        #elif POST_CURRENT_MC_1_20_1
        Matrix4f matrix4f = graphics.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();

        int middleX = x + (width / 2);
        int middleY = y + (height / 2);

        // BACKGROUND
        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        // Center Vertex
        if (alpha < 1.00) {
            buffer.vertex(matrix4f, middleX, middleY, 0).color(bgColor).endVertex();
        }
        // Start Vertex - TOP CENTRE
        buffer.vertex(matrix4f, middleX, y, 0).color(bgColor).endVertex();
        // Intermediate Vertices
        if (alpha > 0.125) // TOP LEFT
        {
            buffer.vertex(matrix4f, x, y, 0).color(bgColor).endVertex();
        }
        if (alpha > 0.375) // BOTTOM LEFT
        {
            buffer.vertex(matrix4f, x, y + height, 0).color(bgColor).endVertex();
        }
        if (alpha > 0.625)  // BOTTOM RIGHT
        {
            buffer.vertex(matrix4f, x + width, y + height, 0).color(bgColor).endVertex();
        }
        if (alpha > 0.875) // TOP RIGHT
        {
            buffer.vertex(matrix4f, x + width, y, 0).color(bgColor).endVertex();
        }
        // Endpoint Vertex
        if (alpha < 1.00) {
            Vector2d ePos = calcCircularCoords(alpha);
            buffer.vertex(matrix4f, (float) (middleX + (ePos.x * width / 2)), (float) (middleY + (ePos.y * height / 2)), 0).color(bgColor).endVertex();
        }
        tesselator.end();

        // OUTLINE
        // Couldn't figure out how to get a buffer builder to render a line, using GuiGraphics instead...
        // There some weirdness with this current implementation, probably due to rounding.
        // Start line - TOP LEFT
        var ta = Math.min(alpha, 0.125) / 0.125;
        graphics.hLine((int) Mth.lerp(ta, middleX - 1, x), middleX - 1, y, color);
        // Intermediate lines
        if (alpha > 0.125) // LEFT
        {
            ta = (Math.min(alpha, 0.375) - 0.125) / 0.25 ;
            graphics.vLine(x, (int) Mth.lerp(ta, y, y + height - 1), y, color);
        }
        if (alpha > 0.375) // BOTTOM
        {
            ta = (Math.min(alpha, 0.625) - 0.375) / 0.25 ;
            graphics.hLine(x, (int) Mth.lerp(ta, x, x + width - 1), y + height - 1, color);
        }
        if (alpha > 0.625)  // RIGHT
        {
            ta = (Math.min(alpha, 0.875) - 0.625) / 0.25 ;
            graphics.vLine(x + width - 1, (int) Mth.lerp(ta, y + height - 1, y), y + height - 1, color);
        }
        if (alpha > 0.875) // TOP RIGHT
        {
            ta = (alpha - 0.875) / 0.125 ;
            graphics.hLine((int) Mth.lerp(ta, x + width - 1, middleX), x + width - 1, y, color);
        }

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
