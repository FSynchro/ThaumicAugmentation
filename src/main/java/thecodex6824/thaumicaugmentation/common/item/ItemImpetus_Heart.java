package thecodex6824.thaumicaugmentation.common.item;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import thecodex6824.thaumicaugmentation.common.util.IModelProvider;

public class ItemImpetus_Heart extends Item implements IModelProvider {

    public ItemImpetus_Heart() {
        // We leave this empty because RegistryHandler is already
        // handling the registration and naming.
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerModels() {
        // This is the ONLY thing this class needs to do.
        // It tells the game to look for impetus_heart.json
        ModelLoader.setCustomModelResourceLocation(this, 0, new ModelResourceLocation(getRegistryName(), "inventory"));
    }
}