/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.items;

import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.TargetingInfo;
import blusunrize.immersiveengineering.api.tool.ITool;
import blusunrize.immersiveengineering.api.utils.CapabilityUtils;
import blusunrize.immersiveengineering.api.wires.ConnectionPoint;
import blusunrize.immersiveengineering.api.wires.GlobalWireNetwork;
import blusunrize.immersiveengineering.api.wires.IImmersiveConnectable;
import blusunrize.immersiveengineering.api.wires.LocalWireNetwork;
import blusunrize.immersiveengineering.api.wires.localhandlers.EnergyTransferHandler;
import blusunrize.immersiveengineering.api.wires.localhandlers.EnergyTransferHandler.Path;
import blusunrize.immersiveengineering.api.wires.utils.WireLink;
import blusunrize.immersiveengineering.api.wires.utils.WirecoilUtils;
import blusunrize.immersiveengineering.common.util.ChatUtils;
import blusunrize.immersiveengineering.common.util.Utils;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nullable;
import java.util.List;

public class VoltmeterItem extends IEBaseItem implements ITool
{
	public VoltmeterItem()
	{
		super(new Properties().stacksTo(1));
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level worldIn, List<Component> tooltip, TooltipFlag flagIn)
	{
		super.appendHoverText(stack, worldIn, tooltip, flagIn);
		if(WirecoilUtils.hasWireLink(stack))
		{
			WireLink link = WireLink.readFromItem(stack);
			tooltip.add(new TranslatableComponent(
					Lib.DESC_INFO+"attachedToDim",
					link.cp.getX(),
					link.cp.getY(),
					link.cp.getZ(),
					link.dimension.toString()
			));
		}
	}

	@Override
	public boolean isTool(ItemStack item)
	{
		return true;
	}

	@Override
	public InteractionResult useOn(UseOnContext context)
	{
		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		BlockEntity bEntity = world.getBlockEntity(pos);
		if((player==null||!player.isShiftKeyDown())&&bEntity!=null)
		{
			IEnergyStorage energyCap = CapabilityUtils.getCapability(bEntity, CapabilityEnergy.ENERGY);
			if(energyCap!=null)
			{
				int max = energyCap.getMaxEnergyStored();
				int stored = energyCap.getEnergyStored();
				if(max > 0)
					ChatUtils.sendServerNoSpamMessages(player, new TranslatableComponent(Lib.CHAT_INFO+"energyStorage", stored, max));
				return InteractionResult.SUCCESS;
			}
		}
		if(player!=null&&player.isShiftKeyDown()&&bEntity instanceof IImmersiveConnectable)
		{
			if(world.isClientSide)
				return InteractionResult.SUCCESS;
			TargetingInfo targetingInfo = new TargetingInfo(context);
			BlockPos masterPos = ((IImmersiveConnectable)bEntity).getConnectionMaster(null, targetingInfo);
			BlockPos delta = pos.subtract(masterPos);
			ConnectionPoint cp = ((IImmersiveConnectable)bEntity).getTargetedPoint(targetingInfo, delta);
			if(cp==null)
				return InteractionResult.FAIL;
			if(!WirecoilUtils.hasWireLink(stack))
			{
				WireLink link = WireLink.create(cp, world, delta, targetingInfo);
				link.writeToItem(stack);
			}
			else
			{
				WireLink link = WireLink.readFromItem(stack);
				if(link.dimension.equals(world.dimension()))
				{
					GlobalWireNetwork global = GlobalWireNetwork.getNetwork(world);
					LocalWireNetwork netHere = global.getNullableLocalNet(cp);
					LocalWireNetwork netLink = global.getNullableLocalNet(link.cp);
					if(netHere==netLink&&netHere!=null)
					{
						EnergyTransferHandler energyHandler = netHere.getHandler(EnergyTransferHandler.ID,
								EnergyTransferHandler.class);
						if(energyHandler!=null)
						{
							Path energyPath = energyHandler.getPath(link.cp, cp);
							double loss;
							if(energyPath!=null)
								loss = energyPath.loss;
							else
								loss = 1;
							player.sendMessage(new TranslatableComponent(
									Lib.CHAT_INFO+"averageLoss",
									Utils.formatDouble(loss*100, "###.000")
							), Util.NIL_UUID);
						}
					}
				}
				WirecoilUtils.clearWireLink(stack);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}
