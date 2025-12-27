package thecodex6824.thaumicaugmentation.common.tile;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import thaumcraft.api.aspects.Aspect;
import thecodex6824.thaumicaugmentation.api.block.property.IDirectionalBlock;
import thecodex6824.thaumicaugmentation.api.block.property.IEnabledBlock;
import thecodex6824.thaumicaugmentation.common.block.BlockFluxInducerRF;
import thecodex6824.thaumicaugmentation.common.network.PacketParticleEffect;
import thecodex6824.thaumicaugmentation.common.network.PacketParticleEffect.ParticleEffect;
import thecodex6824.thaumicaugmentation.common.network.TANetwork;
import thecodex6824.thaumicaugmentation.api.TASounds;

import java.util.concurrent.ThreadLocalRandom;

public class TileFluxInducerRF extends TileEntity implements ITickable {

    private final RFStorage energy = new RFStorage(10_000_000, 2_000_000);

    private int rfPerTick = 0;
    private int rfAccumulated = 0;
    private int batchCounter = 0;
    private static final int BATCH_INTERVAL = 20;

    private int evalCounter = 0;
    private static final int EVAL_INTERVAL = 300;
    private boolean rfMetThisCycle = true;
    private boolean canBoost = false;

    private int zapTimer = ThreadLocalRandom.current().nextInt(140, 400);

    public TileFluxInducerRF() {}

    @Override
    public void update() {
        if (world.isRemote) return;

        IBlockState state = world.getBlockState(pos);

        if (!state.getValue(IEnabledBlock.ENABLED)) {
            canBoost = false;
            return;
        }

        EnumFacing facing = state.getValue(IDirectionalBlock.DIRECTION);
        TileEntity te = world.getTileEntity(pos.offset(facing));
        boolean facingFeeder = te instanceof TileRiftFeeder;

        if (!facingFeeder) {
            canBoost = false;
            return;
        }

        rfAccumulated += rfPerTick;
        batchCounter++;

        if (batchCounter >= BATCH_INTERVAL) {
            int required = rfAccumulated;
            int extracted = energy.extractEnergy(required, true);

            if (extracted == required) {
                energy.extractEnergy(required, false);
            } else {
                rfMetThisCycle = false;
                spawnUnderpoweredEffects();
            }

            rfAccumulated = 0;
            batchCounter = 0;
        }

        evalCounter++;
        if (evalCounter >= EVAL_INTERVAL) {
            canBoost = rfMetThisCycle && facingFeeder;
            rfMetThisCycle = true;
            evalCounter = 0;
            markDirty();
        }

        if (!canBoost) {
            zapTimer--;
            if (zapTimer <= 0) {
                playZapSound();
                zapTimer = ThreadLocalRandom.current().nextInt(140, 400);
            }
        }
    }

    public void setRiftSizeForCost(int riftSize) {
        if (riftSize < 198) {
            rfPerTick = 5000;
        } else if (riftSize <= 200) {
            rfPerTick = 50000;
        } else {
            rfPerTick = 50000 + (riftSize - 200) * 4875;
        }
    }

    private void spawnUnderpoweredEffects() {
        TANetwork.INSTANCE.sendToAllAround(
                new PacketParticleEffect(ParticleEffect.ESSENTIA_TRAIL,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        Aspect.FLUX.getColor()),
                new net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint(
                        world.provider.getDimension(),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 32)
        );
    }

    private void playZapSound() {
        world.playSound(null, pos, TASounds.RIFT_ENERGY_ZAP, SoundCategory.BLOCKS, 1.0F, 0.9F);
    }

    public boolean canBoost() {
        return canBoost;
    }

    @Override
    public boolean hasCapability(Capability<?> cap, @Nullable EnumFacing side) {
        return cap == CapabilityEnergy.ENERGY || super.hasCapability(cap, side);
    }

    @Override
    public <T> T getCapability(Capability<T> cap, @Nullable EnumFacing side) {
        if (cap == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.cast(energy);
        }
        return super.getCapability(cap, side);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("Energy", energy.getEnergyStored());
        tag.setBoolean("CanBoost", canBoost);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        energy.setEnergy(tag.getInteger("Energy"));
        canBoost = tag.getBoolean("CanBoost");
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new SPacketUpdateTileEntity(pos, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    private static class RFStorage implements IEnergyStorage {
        private int energy;
        private final int capacity;
        private final int maxReceive;

        public RFStorage(int capacity, int maxReceive) {
            this.capacity = capacity;
            this.maxReceive = maxReceive;
        }

        public void setEnergy(int e) {
            this.energy = Math.min(e, capacity);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int energyReceived = Math.min(capacity - energy, Math.min(this.maxReceive, maxReceive));
            if (!simulate) energy += energyReceived;
            return energyReceived;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = Math.min(energy, maxExtract);
            if (!simulate) energy -= extracted;
            return extracted;
        }

        @Override
        public int getEnergyStored() { return energy; }
        @Override
        public int getMaxEnergyStored() { return capacity; }
        @Override
        public boolean canExtract() { return false; }
        @Override
        public boolean canReceive() { return true; }
    }
} // Final closing brace for the main class