package delta.melee;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.joml.Vector3fc;

/**
 * Renders a baked Bedrock geo model as a "special" item model.
 * NOTE: written against the 1.21.9+/26.x special-model API without being able to compile it here;
 * if the compiler complains, the fixes are mostly package names / small signature differences.
 */
public final class GeoSpecialRenderer implements SpecialModelRenderer<Void> {
    private final GeoModel model;
    private final Identifier texture;

    GeoSpecialRenderer(GeoModel model, Identifier texture) { this.model = model; this.texture = texture; }

    @Override
    public void submit(@Nullable Void arg, ItemDisplayContext ctx, PoseStack pose, SubmitNodeCollector out,
                       int light, int overlay, boolean foil, int outlineColor) {
        out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(texture), (p, vc) -> {
            for (float[] q : model.quads) {
                for (int i = 0; i < 4; i++) {
                    vc.addVertex(p, q[i*5], q[i*5+1], q[i*5+2])
                      .setColor(-1).setUv(q[i*5+3], q[i*5+4])
                      .setOverlay(overlay).setLight(light)
                      .setNormal(p, q[20], q[21], q[22]);
                }
            }
        });
    }

    @Override
    public void getExtents(Consumer<Vector3fc> out) {
        for (float x : new float[]{model.minX, model.maxX})
            for (float y : new float[]{model.minY, model.maxY})
                for (float z : new float[]{model.minZ, model.maxZ})
                    out.accept(new org.joml.Vector3f(x, y, z));
    }

    @Override
    public @Nullable Void extractArgument(ItemStack stack) { return null; }

    public record Unbaked(String id) implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i ->
            i.group(Codec.STRING.fieldOf("id").forGetter(Unbaked::id)).apply(i, Unbaked::new));

        @Override
        public @Nullable SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext ctx) {
            var rm = Minecraft.getInstance().getResourceManager();
            Identifier geo = Identifier.fromNamespaceAndPath(DeltaMeleeClient.ID, "geo_models/melee/" + id + "_geo.json");
            try (Reader r = new java.io.InputStreamReader(rm.getResourceOrThrow(geo).open(), StandardCharsets.UTF_8)) {
                GeoModel m = GeoModel.load(r, null);
                Identifier tex = Identifier.fromNamespaceAndPath(DeltaMeleeClient.ID, "textures/melee/" + id + "_uv.png");
                return new GeoSpecialRenderer(m, tex);
            } catch (Exception e) {
                throw new IllegalStateException("delta_wt: cannot load model " + id, e);
            }
        }

        @Override
        public MapCodec<Unbaked> type() { return MAP_CODEC; }
    }
}
