package org.zeith.improvableskills.custom.items;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.zeith.hammerlib.net.Network;
import org.zeith.hammerlib.util.java.Chars;
import org.zeith.improvableskills.ImprovableSkills;
import org.zeith.improvableskills.SyncSkills;
import org.zeith.improvableskills.api.registry.PlayerSkillBase;
import org.zeith.improvableskills.api.tooltip.SkillTooltip;
import org.zeith.improvableskills.data.PlayerDataManager;
import org.zeith.improvableskills.init.ItemsIS;
import org.zeith.improvableskills.net.PacketScrollUnlockedSkill;

import javax.annotation.Nullable;
import java.util.*;

public class ItemSkillScroll
		extends Item
{
	private static final Map<String, PlayerSkillBase> SKILL_MAP = new HashMap<>();
	
	public ItemSkillScroll()
	{
		super(new Properties().stacksTo(1).tab(ImprovableSkills.TAB));
	}
	
	@Nullable
	public static PlayerSkillBase getSkillFromScroll(ItemStack stack)
	{
		if(!stack.isEmpty() && stack.getItem() instanceof ItemSkillScroll && stack.hasTag() && stack.getTag().contains("Skill", Tag.TAG_STRING))
		{
			String skill = stack.getTag().getString("Skill");
			
			if(SKILL_MAP.containsKey(skill))
				return SKILL_MAP.get(skill);
			
			PlayerSkillBase b = ImprovableSkills.SKILLS().getValue(new ResourceLocation(stack.getTag().getString("Skill")));
			
			SKILL_MAP.put(skill, b);
			
			return b;
		}
		return null;
	}
	
	public static ItemStack of(PlayerSkillBase base)
	{
		if(base.getScrollState().hasScroll())
		{
			ItemStack stack = new ItemStack(ItemsIS.SKILL_SCROLL);
			CompoundTag tag = new CompoundTag();
			tag.putString("Skill", base.getRegistryName().toString());
			stack.setTag(tag);
			return stack;
		}
		return ItemStack.EMPTY;
	}
	
	public static void getItems(NonNullList<ItemStack> items)
	{
		ImprovableSkills.SKILLS()
				.getValues()
				.stream()
				.filter(skill -> skill.getScrollState().hasScroll())
				.sorted(Comparator.comparing(PlayerSkillBase::getUnlocalizedName))
				.forEach(skill -> items.add(ItemSkillScroll.of(skill)));
	}
	
	@Override
	public void fillItemCategory(CreativeModeTab tab, NonNullList<ItemStack> items)
	{
		if(allowedIn(tab))
			getItems(items);
	}
	
	@Override
	public void appendHoverText(ItemStack stack, Level worldIn, List<Component> tooltip, TooltipFlag flagIn)
	{
		PlayerSkillBase base = getSkillFromScroll(stack);
		if(base == null)
			return;
		tooltip.add(base.getLocalizedName(SyncSkills.getData()).withStyle(ChatFormatting.GRAY));
		if(flagIn.isAdvanced())
			tooltip.add(Component.literal(" - " + base.getRegistryName()).withStyle(ChatFormatting.DARK_GRAY));
		
		if(ImprovableSkills.PROXY.hasShiftDown())
			tooltip.add(Component.literal(I18n.get("recipe." + base.getRegistryName().getNamespace() + ":skill." + base.getRegistryName().getPath()).replace('&', Chars.SECTION_SIGN)).withStyle(ChatFormatting.GRAY));
		else
			tooltip.add(Component.literal(I18n.get("text.improvableskills:shiftfrecipe").replace('&', Chars.SECTION_SIGN)).withStyle(ChatFormatting.GRAY));
	}
	
	@Override
	public InteractionResultHolder<ItemStack> use(Level worldIn, Player playerIn, InteractionHand handIn)
	{
		var held = playerIn.getItemInHand(handIn);
		
		if(worldIn.isClientSide) return new InteractionResultHolder<>(InteractionResult.PASS, held);
		
		return PlayerDataManager.handleDataSafely(playerIn, data ->
		{
			PlayerSkillBase base = getSkillFromScroll(held);
			
			if(base == null)
				return new InteractionResultHolder<>(InteractionResult.PASS, held);
			
			if(!data.hasSkillScroll(base) && data.unlockSkillScroll(base, true))
			{
				ItemStack used = held.copy();
				held.shrink(1);
				
				playerIn.swing(handIn);
				worldIn.playSound(null, playerIn.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 1F);
				
				int slot = handIn == InteractionHand.OFF_HAND ? -2 : playerIn.getInventory().selected;
				Network.sendTo(new PacketScrollUnlockedSkill(slot, used, base.getRegistryName()), playerIn);
				
				return new InteractionResultHolder<>(InteractionResult.SUCCESS, held);
			} else if(data.getSkillLevel(base) < base.getMaxLevel())
			{
				data.setSkillLevel(base, data.getSkillLevel(base) + 1);
				ItemStack used = held.copy();
				held.shrink(1);
				
				playerIn.swing(handIn);
				worldIn.playSound(null, playerIn.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 1F);
				
				int slot = handIn == InteractionHand.OFF_HAND ? -2 : playerIn.getInventory().selected;
				Network.sendTo(new PacketScrollUnlockedSkill(slot, used, base.getRegistryName()), playerIn);
				return new InteractionResultHolder<>(InteractionResult.SUCCESS, held);
			}
			
			return new InteractionResultHolder<>(InteractionResult.PASS, held);
		}, new InteractionResultHolder<>(InteractionResult.PASS, held));
	}
	
	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack)
	{
		return Optional.ofNullable(getSkillFromScroll(stack)).map(SkillTooltip::new);
	}
}