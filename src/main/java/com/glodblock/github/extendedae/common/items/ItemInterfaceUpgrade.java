package com.glodblock.github.extendedae.common.items;

import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.parts.misc.InterfacePart;
import com.glodblock.github.extendedae.common.EAEItemAndBlock;
import com.glodblock.github.extendedae.common.tileentities.TileExInterface;
import net.minecraft.world.item.Item;

public class ItemInterfaceUpgrade extends ItemUpgrade {

    public ItemInterfaceUpgrade() {
        super(new Item.Properties());
        this.addTile(InterfaceBlockEntity.class, EAEItemAndBlock.EX_INTERFACE, TileExInterface.class);
        this.addPart(InterfacePart.class, EAEItemAndBlock.EX_INTERFACE_PART);
    }

}
