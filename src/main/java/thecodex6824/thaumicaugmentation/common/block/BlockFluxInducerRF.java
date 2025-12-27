package thecodex6824.thaumicaugmentation.common.block;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import thecodex6824.thaumicaugmentation.api.block.property.IDirectionalBlock;
import thecodex6824.thaumicaugmentation.api.block.property.IEnabledBlock;
import thecodex6824.thaumicaugmentation.common.block.prefab.BlockTABase;
import thecodex6824.thaumicaugmentation.common.block.trait.IItemBlockProvider;
import thecodex6824.thaumicaugmentation.common.tile.TileFluxInducerRF;
import thecodex6824.thaumicaugmentation.common.util.BitUtil;

public class BlockFluxInducerRF extends BlockTABase implements IDirectionalBlock, IEnabledBlock, IItemBlockProvider {

    public BlockFluxInducerRF() {
        super(Material.IRON);
        setHardness(1.5F);
        setResistance(15.0F);
        setSoundType(SoundType.METAL);
        // Default state ensures the block is active and pointing UP when first defined
        setDefaultState(this.blockState.getBaseState()
                .withProperty(IDirectionalBlock.DIRECTION, EnumFacing.UP)
                .withProperty(IEnabledBlock.ENABLED, true));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, IDirectionalBlock.DIRECTION, IEnabledBlock.ENABLED);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState()
                .withProperty(IDirectionalBlock.DIRECTION, EnumFacing.byIndex(BitUtil.getBits(meta, 0, 3)))
                .withProperty(IEnabledBlock.ENABLED, BitUtil.isBitSet(meta, 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int meta = state.getValue(IDirectionalBlock.DIRECTION).getIndex();
        meta = BitUtil.setBit(meta, 3, state.getValue(IEnabledBlock.ENABLED));
        return meta;
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        EnumFacing direction = EnumFacing.getDirectionFromEntityLiving(pos, placer);
        if (placer.isSneaking()) {
            direction = direction.getOpposite();
        }
        return getDefaultState()
                .withProperty(IDirectionalBlock.DIRECTION, direction)
                .withProperty(IEnabledBlock.ENABLED, true);
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(IDirectionalBlock.DIRECTION, rot.rotate(state.getValue(IDirectionalBlock.DIRECTION)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(IDirectionalBlock.DIRECTION)));
    }

    protected void update(IBlockState state, World world, BlockPos pos) {
        boolean powered = world.isBlockPowered(pos);
        // If redstone state doesn't match our enabled state, toggle it
        if (powered == state.getValue(IEnabledBlock.ENABLED)) {
            world.setBlockState(pos, state.cycleProperty(IEnabledBlock.ENABLED), 3);
        }
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        update(state, world, pos);
        super.onBlockAdded(world, pos, state);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        update(state, world, pos);
    }

    @Override
    public boolean canHarvestBlock(IBlockAccess world, BlockPos pos, EntityPlayer player) {
        return true;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileFluxInducerRF();
    }

    // --- Rendering methods to support the non-full Rift Feeder model shape ---

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullBlock(IBlockState state) {
        return false;
    }

    @Override
    public boolean isTopSolid(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isNormalCube(IBlockState state, IBlockAccess world, BlockPos pos) {
        return false;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess worldIn, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT; // Changed to CUTOUT to better support complex model transparency
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }
}