package org.zeith.improvableskills.proxy;

import com.mojang.math.Vector3f;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.zeith.hammerlib.client.utils.RenderUtils;
import org.zeith.hammerlib.net.Network;
import org.zeith.improvableskills.ImprovableSkills;
import org.zeith.improvableskills.SyncSkills;
import org.zeith.improvableskills.api.PlayerSkillData;
import org.zeith.improvableskills.api.registry.PlayerAbilityBase;
import org.zeith.improvableskills.api.registry.PlayerSkillBase;
import org.zeith.improvableskills.api.tooltip.AbilityTooltip;
import org.zeith.improvableskills.api.tooltip.SkillTooltip;
import org.zeith.improvableskills.cfg.ConfigsIS;
import org.zeith.improvableskills.client.gui.abil.ench.GuiEnchPowBook;
import org.zeith.improvableskills.client.gui.abil.ench.GuiPortableEnchantment;
import org.zeith.improvableskills.client.gui.base.GuiCustomButton;
import org.zeith.improvableskills.client.rendering.OnTopEffects;
import org.zeith.improvableskills.client.rendering.particle.SparkleParticle;
import org.zeith.improvableskills.client.rendering.tooltip.AbilityTooltipRenderer;
import org.zeith.improvableskills.client.rendering.tooltip.SkillTooltipRenderer;
import org.zeith.improvableskills.custom.items.ItemAbilityScroll;
import org.zeith.improvableskills.custom.items.ItemSkillScroll;
import org.zeith.improvableskills.custom.particles.ParticleDataSparkle;
import org.zeith.improvableskills.init.*;
import org.zeith.improvableskills.net.PacketOpenSkillsBook;

import java.util.*;

public class ISClient
		extends ISServer
{
	@Override
	public void register(IEventBus modBus)
	{
		super.register(modBus);
		modBus.addListener(this::registerOverlays);
		modBus.addListener(this::clientSetup);
		modBus.addListener(this::registerItemColors);
		modBus.addListener(this::registerParticles);
		modBus.addListener(this::registerTooltipImages);
		
		var mcfBus = MinecraftForge.EVENT_BUS;
		mcfBus.addListener(this::addInvButtons);
		mcfBus.addListener(this::renderInventory);
	}
	
	@Override
	public boolean hasShiftDown()
	{
		return Screen.hasShiftDown();
	}
	
	public Player getClientPlayer()
	{
		return Minecraft.getInstance().player;
	}
	
	@Override
	public void sparkle(Level level, double x, double y, double z, double xMove, double yMove, double zMove, int color, int maxAge)
	{
		level.addParticle(new ParticleDataSparkle(new Vector3f(Vec3.fromRGB24(color)), 1F, maxAge), x, y, z, xMove, yMove, zMove);
	}
	
	private void registerTooltipImages(RegisterClientTooltipComponentFactoriesEvent e)
	{
		e.register(SkillTooltip.class, SkillTooltipRenderer::new);
		e.register(AbilityTooltip.class, AbilityTooltipRenderer::new);
	}
	
	private void registerParticles(RegisterParticleProvidersEvent e)
	{
		e.register(ParticleTypesIS.SPARKLE, SparkleParticle.Provider::new);
	}
	
	private void registerItemColors(RegisterColorHandlersEvent.Item e)
	{
		e.register((stack, layer) ->
		{
			PlayerSkillBase b = ItemSkillScroll.getSkillFromScroll(stack);
			if(layer == 1 && b != null)
				return 255 << 24 | b.getColor();
			return 0xFF_FFFFFF;
		}, ItemsIS.SKILL_SCROLL);
		
		e.register((stack, layer) ->
		{
			PlayerAbilityBase b = ItemAbilityScroll.getAbilityFromScroll(stack);
			if(layer == 1 && b != null)
				return 255 << 24 | b.getColor();
			return 0xFF_FFFFFF;
		}, ItemsIS.ABILITY_SCROLL);
	}
	
	private void clientSetup(FMLClientSetupEvent e)
	{
		MenuScreens.register(GuiHooksIS.ENCH_POWER_BOOK_IO, GuiEnchPowBook::new);
		MenuScreens.register(GuiHooksIS.REPAIR, AnvilScreen::new);
		MenuScreens.register(GuiHooksIS.ENCHANTMENT, GuiPortableEnchantment::new);
		MenuScreens.register(GuiHooksIS.CRAFTING, CraftingScreen::new);
	}
	
	private void registerOverlays(RegisterGuiOverlaysEvent e)
	{
		e.registerAboveAll("ontop_effects", new OnTopEffects());
	}
	
	private Button openSkills;
	private boolean hovered;
	
	private void addInvButtons(ScreenEvent.Init.Post e)
	{
		Minecraft mc = Minecraft.getInstance();
		
		SyncSkills.doCheck(mc.player);
		
		if(e.getScreen() instanceof InventoryScreen inv && ConfigsIS.addBookToInv)
		{
			PlayerSkillData data = SyncSkills.getData();
			
			e.addListener(openSkills = new GuiCustomButton(0, inv.getGuiLeft() + (inv.getXSize() - 16) / 2 - 1, inv.getGuiTop() + 24, 16, 16, Component.literal(""), this::openSkillBook)
					.setCustomClickSound(SoundsIS.PAGE_TURNS)
			);
			
			openSkills.setAlpha(0F);
			openSkills.active = data.hasCraftedSkillsBook();
		}
	}
	
	private void renderInventory(ScreenEvent.Render.Post e)
	{
		if(e.getScreen() instanceof InventoryScreen inv && openSkills != null && ConfigsIS.addBookToInv)
		{
			int mx = e.getMouseX();
			int my = e.getMouseY();
			
			openSkills.x = inv.getGuiLeft() + (inv.getXSize() - 16) / 2 - 1;
			openSkills.y = inv.getGuiTop() + 24;
			
			PlayerSkillData data = SyncSkills.getData();
			
			openSkills.active = true;
			hovered = openSkills.isMouseOver(mx, my);
			openSkills.active = data.hasCraftedSkillsBook();
			
			ItemStack book = new ItemStack(ItemsIS.SKILLS_BOOK);
			
			RenderUtils.renderItemIntoGui(e.getPoseStack(), book, openSkills.x, openSkills.y);
			
			if(hovered)
			{
				List<Component> arr = new ArrayList<>();
				
				arr.add(ItemsIS.SKILLS_BOOK.getDescription());
				if(!openSkills.active) arr.add(Component.translatable("gui." + ImprovableSkills.MOD_ID + ".locked"));
				arr.add(Component.literal(ImprovableSkills.MOD_NAME).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
				
				inv.renderTooltip(e.getPoseStack(), arr, Optional.empty(), openSkills.x + 12, openSkills.y + 4);
			}
		}
	}
	
	private void openSkillBook(Button e)
	{
		/* Grab skills and open GUI */
		Network.sendToServer(new PacketOpenSkillsBook());
	}
}