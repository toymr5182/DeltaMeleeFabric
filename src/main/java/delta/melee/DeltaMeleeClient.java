package delta.melee;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;

public class DeltaMeleeClient implements ClientModInitializer {
    public static final String ID = "delta_wt";
    @Override
    public void onInitializeClient() {
        // lets items/*.json use {"type":"delta_wt:geo","id":"<model>"}
        SpecialModelRenderers.ID_MAPPER.put(Identifier.fromNamespaceAndPath(ID, "geo"), GeoSpecialRenderer.Unbaked.MAP_CODEC);
    }
}
