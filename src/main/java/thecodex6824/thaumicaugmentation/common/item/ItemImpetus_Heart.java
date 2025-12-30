package thecodex6824.thaumicaugmentation.common.item;

import com.brandon3055.draconicevolution.entity.EntityChaosGuardian;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.common.entities.EntityFluxRift;
import thaumcraft.common.lib.network.PacketHandler;
import thaumcraft.common.lib.network.fx.PacketFXBlockBamf;
import thecodex6824.thaumicaugmentation.common.util.IModelProvider;

import java.util.List;

public class ItemImpetus_Heart extends Item implements IModelProvider {

    private static final String PROGRESS_KEY = "IMPETUS_EXPERIMENT";

    public ItemImpetus_Heart() {}

    @Override
    public boolean onEntityItemUpdate(EntityItem entityItem) {
        World world = entityItem.world;
        NBTTagCompound nbt = entityItem.getEntityData();

        if (!world.isRemote) {
            // --- SEQUENTIAL PUFF LOGIC ---
            int puffsRemaining = nbt.getInteger("PuffQueue");
            if (puffsRemaining > 0 && entityItem.ticksExisted % 6 == 0) {
                spawnCircularPuff(entityItem, puffsRemaining);
                nbt.setInteger("PuffQueue", puffsRemaining - 1);

                triggerProgress(entityItem);
            }

            // --- MAIN RIFT LOGIC (Every 2 Seconds) ---
            if (entityItem.ticksExisted % 40 == 0) {
                AxisAlignedBB searchBox = entityItem.getEntityBoundingBox().grow(10.0D);
                List<EntityFluxRift> rifts = world.getEntitiesWithinAABB(EntityFluxRift.class, searchBox);

                for (EntityFluxRift rift : rifts) {
                    if (rift.getRiftSize() >= 580 && !rift.getCollapse()) {

                        if (world.rand.nextInt(6) == 0) {
                            processCharge(entityItem, rift);
                            triggerProgress(entityItem);
                        } else {
                            world.playSound(null, entityItem.posX, entityItem.posY, entityItem.posZ,
                                    SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT, SoundCategory.HOSTILE, 1.0F, 1.2F);

                            nbt.setInteger("PuffQueue", 5);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Ticks the "Observe a Heart's resonance" checkbox in the research book.
     */
    /**
     * Completes the hidden research key "IMPETUS_EXPERIMENT"
     * which checks the box in the main research entry.
     */
    private void triggerProgress(EntityItem entityItem) {
        List<EntityPlayer> players = entityItem.world.getEntitiesWithinAABB(EntityPlayer.class, entityItem.getEntityBoundingBox().grow(12.0D));
        for (EntityPlayer player : players) {
            IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
            if (knowledge != null) {
                // Check if the player has NOT completed the observation step yet
                if (!knowledge.isResearchKnown(PROGRESS_KEY)) {

                    // This is the direct way to complete the research "box"
                    ThaumcraftApi.internalMethods.completeResearch(player, PROGRESS_KEY);

                    // Audio-visual feedback
                    player.sendStatusMessage(new TextComponentString("§5§oThe Heart's resonance reveals a dark truth..."), true);
                    entityItem.world.playSound(null, player.posX, player.posY, player.posZ,
                            SoundEvents.ENTITY_ENDERMEN_SCREAM, SoundCategory.AMBIENT, 0.7F, 0.5F);
                }
            }
        }
    }

    private void processCharge(EntityItem heart, EntityFluxRift rift) {
        World world = heart.world;
        int successes = heart.getEntityData().getInteger("SuccessCount") + 1;
        heart.getEntityData().setInteger("SuccessCount", successes);

        if (successes < 3) {
            EntityLightningBolt bolt = new EntityLightningBolt(world, rift.posX, rift.posY, rift.posZ, true);
            world.addWeatherEffect(bolt);

            if (successes == 2) {
                List<EntityPlayer> players = world.getEntitiesWithinAABB(EntityPlayer.class, heart.getEntityBoundingBox().grow(64.0D));
                for (EntityPlayer player : players) {
                    String warning = TextFormatting.RED + "" + TextFormatting.BOLD + "You hear loud chants going, they speak of an IMPENDING DOOM! " +
                            TextFormatting.GOLD + "Pick up the Impetus Heart or face the consequences!";
                    player.sendMessage(new TextComponentString(warning));
                }
                world.playSound(null, heart.posX, heart.posY, heart.posZ, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 2.0F, 0.5F);
            }
        } else {
            spawnGuardian(heart, rift);
        }
    }

    private void spawnGuardian(EntityItem heart, EntityFluxRift rift) {
        World world = heart.world;
        EntityChaosGuardian guardian = new EntityChaosGuardian(world);

        if (guardian != null) {
            guardian.setPosition(rift.posX, rift.posY, rift.posZ);
            guardian.homeX = (int) rift.posX;
            guardian.homeY = (int) rift.posY;
            guardian.homeZ = (int) rift.posZ;
            guardian.homeSet = true;

            // --- ATMOSPHERIC EFFECTS ---
            WorldInfo info = world.getWorldInfo();
            info.setRaining(true);
            info.setThundering(true);
            info.setRainTime(40);
            info.setThunderTime(40);

            // Large scale particles
            for (int i = 0; i < 30; i++) {
                double offsetX = (world.rand.nextGaussian()) * 5.0D;
                double offsetY = (world.rand.nextGaussian()) * 5.0D;
                double offsetZ = (world.rand.nextGaussian()) * 5.0D;
                PacketHandler.INSTANCE.sendToAllAround(
                        new PacketFXBlockBamf(rift.posX + offsetX, rift.posY + offsetY, rift.posZ + offsetZ, 0, true, true, (EnumFacing)null),
                        new NetworkRegistry.TargetPoint(world.provider.getDimension(), rift.posX, rift.posY, rift.posZ, 128.0D)
                );
            }

            world.createExplosion(null, rift.posX, rift.posY, rift.posZ, 6.0F, true);
            world.playSound(null, rift.posX, rift.posY, rift.posZ, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 10.0F, 0.1F);
            world.playSound(null, rift.posX, rift.posY, rift.posZ, SoundEvents.ENTITY_LIGHTNING_THUNDER, SoundCategory.HOSTILE, 5.0F, 1.0F);

            // --- SPAWN CHAOS SHARDS ---
            // Safely get the item from Draconic Evolution
            net.minecraft.item.ItemStack shardStack = new net.minecraft.item.ItemStack(
                    net.minecraft.item.Item.getByNameOrId("draconicevolution:chaos_shard"), 2);

            if (!shardStack.isEmpty()) {
                EntityItem shards = new EntityItem(world, heart.posX, heart.posY, heart.posZ, shardStack);
                // Physical "Burst" effect
                shards.motionY = 0.4D;
                shards.motionX = (world.rand.nextDouble() - 0.5D) * 0.2D;
                shards.motionZ = (world.rand.nextDouble() - 0.5D) * 0.2D;
                shards.setPickupDelay(20);
                world.spawnEntity(shards);
            }

            // --- CLEANUP AND SPAWN BOSS ---
            rift.setDead();
            heart.setDead();
            world.spawnEntity(guardian);
        }
    }

    private void spawnCircularPuff(EntityItem entityItem, int index) {
        double angle = index * (Math.PI * 2 / 5);
        double radius = 1.0D;
        double px = entityItem.posX + Math.cos(angle) * radius;
        double py = entityItem.posY + 0.5D;
        double pz = entityItem.posZ + Math.sin(angle) * radius;
        PacketHandler.INSTANCE.sendToAllAround(
                new PacketFXBlockBamf(px, py, pz, 0, true, true, (EnumFacing)null),
                new NetworkRegistry.TargetPoint(entityItem.world.provider.getDimension(), px, py, pz, 64.0D)
        );
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerModels() {
        ModelLoader.setCustomModelResourceLocation(this, 0, new ModelResourceLocation(getRegistryName(), "inventory"));
    }
}