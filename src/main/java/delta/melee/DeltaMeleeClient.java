package delta.melee;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

public class DeltaMeleeClient implements ClientModInitializer {
    public static final String ID = "delta_wt";

    @Override
    public void onInitializeClient() {
        registerGeo();
    }

    // ID_MAPPER is private in 26.3, so register the custom special-model type reflectively.
    @SuppressWarnings("unchecked")
    private static void registerGeo() {
        try {
            var field = SpecialModelRenderers.class.getDeclaredField("ID_MAPPER");
            field.setAccessible(true);
            var mapper = (ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>>) field.get(null);
            mapper.put(Identifier.fromNamespaceAndPath(ID, "geo"), GeoSpecialRenderer.Unbaked.MAP_CODEC);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("delta_wt: cannot register special model type", e);
        }
    }
}
