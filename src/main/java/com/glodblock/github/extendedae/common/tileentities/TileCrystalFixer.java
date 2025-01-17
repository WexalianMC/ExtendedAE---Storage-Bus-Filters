package com.glodblock.github.extendedae.common.tileentities;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalBlockPos;
import appeng.blockentity.grid.AENetworkInvBlockEntity;
import appeng.util.Platform;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.glodblock.github.extendedae.api.ExtendedAEAPI;
import com.glodblock.github.extendedae.api.ICrystalFixer;
import com.glodblock.github.extendedae.common.EAEItemAndBlock;
import com.glodblock.github.glodium.util.GlodUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Set;


public class TileCrystalFixer extends AENetworkInvBlockEntity implements IGridTickable {

    private final AppEngInternalInventory inv = new AppEngInternalInventory(this, 1);
    private int progress = 0;

    public TileCrystalFixer(BlockPos pos, BlockState blockState) {
        super(GlodUtil.getTileType(TileCrystalFixer.class, TileCrystalFixer::new, EAEItemAndBlock.CRYSTAL_FIXER), pos, blockState);
        this.getMainNode().setFlags().setIdlePowerUsage(0).addService(IGridTickable.class, this);
        this.inv.setFilter(new Filter());
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(2, 10, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (doWork(ticksSinceLastCall)) {
            return TickRateModulation.FASTER;
        }
        return TickRateModulation.SLOWER;
    }

    private boolean canFixCrystal(BlockState blockState) {
        for (ICrystalFixer fixer : ExtendedAEAPI.INSTANCE.getCrystalFixers()) {
            if (fixer.isCrystal(blockState.getBlock()) && fixer.canFix(blockState, this.inv.getStackInSlot(0))) {
                return true;
            }
        }
        return false;
    }

    private Block getNextCrystalBlock(BlockState crystal) {
        for (ICrystalFixer fixer : ExtendedAEAPI.INSTANCE.getCrystalFixers()) {
            if (fixer.isCrystal(crystal.getBlock())) {
                return fixer.getNextCrystalBlock(crystal);
            }
        }
        return null;
    }

    private boolean doWork(int ticksSinceLastCall) {
        if (this.getLevel() == null || !checkFuel()) {
            return false;
        }
        var blockPos = this.getBlockPos().offset(this.getFront().getNormal());
        var blockState = this.getLevel().getBlockState(blockPos);
        var random = this.getLevel().getRandom();
        if (canFixCrystal(blockState)) {
            if (this.userPower(ticksSinceLastCall * 50) > 0) {
                this.progress += random.nextInt(5);
                this.consumeFuel(random);
            }
            if (this.progress >= 100) {
                Block block = getNextCrystalBlock(blockState);
                if (block != null) {
                    this.getLevel().setBlockAndUpdate(blockPos, block.defaultBlockState());
                }
                this.progress = 0;
            }
            return true;
        } else {
            this.progress = 0;
            return false;
        }
    }

    private boolean checkFuel() {
        return !this.inv.getStackInSlot(0).isEmpty();
    }

    private void consumeFuel(RandomSource random) {
        if (random.nextInt(10) < 1) {
            this.inv.extractItem(0, 1, false);
        }
    }

    public int getProgress() {
        return this.progress;
    }

    private long userPower(int value) {
        if (this.getGridNode() == null) {
            return 0;
        }
        var grid = this.getGridNode().getGrid();
        if (grid != null) {
            return (long) grid.getEnergyService().extractAEPower(value, Actionable.MODULATE, PowerMultiplier.CONFIG);
        } else {
            return 0;
        }
    }

    @Override
    protected boolean readFromStream(FriendlyByteBuf data) {
        var changed = super.readFromStream(data);
        this.inv.setItemDirect(0, data.readItem());
        return changed;
    }

    @Override
    protected void writeToStream(FriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeItem(this.inv.getStackInSlot(0));
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.of(orientation.getSide(RelativeSide.BACK));
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        data.putInt("progress", this.progress);
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        this.progress = data.getInt("progress");
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.inv;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.markForUpdate();
    }

    public void refuel(Player player) {
        if (!Platform.hasPermissions(new DimensionalBlockPos(this), player)) {
            return;
        }
        var playerInv = player.getInventory();
        ItemStack held = playerInv.getSelected();
        if (held.isEmpty()) {
            var stuff = this.inv.extractItem(0, Integer.MAX_VALUE, false);
            if (!stuff.isEmpty()) {
                playerInv.placeItemBackInInventory(stuff);
            }
        } else {
            var notAdded = this.inv.insertItem(0, held, false);
            playerInv.setItem(playerInv.selected, notAdded);
        }
    }

    private static class Filter implements IAEItemFilter {

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            for (ICrystalFixer fixer : ExtendedAEAPI.INSTANCE.getCrystalFixers()) {
                if (fixer.getFuelType().equals(stack.getItem())) {
                    return true;
                }
            }
            return false;
        }
    }

}
