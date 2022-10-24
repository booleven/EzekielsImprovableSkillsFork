package org.zeith.improvableskills.custom.skills;

import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraftforge.common.MinecraftForge;
import org.zeith.improvableskills.api.evt.ApplySpecialPricesEvent;
import org.zeith.improvableskills.api.registry.PlayerSkillBase;
import org.zeith.improvableskills.data.PlayerDataManager;

public class SkillHuckster
		extends PlayerSkillBase
{
	public SkillHuckster()
	{
		super(10);
		setupScroll();
		getLoot().chance.n = 3;
		getLoot().setLootTable(BuiltInLootTables.LIBRARIAN_GIFT);
		setColor(0x00FF00);
		xpCalculator.setBaseFormula("150*(%lvl%+1)+(%lvl%+1)^3");
		MinecraftForge.EVENT_BUS.addListener(this::specialPrices);
	}
	
	private void specialPrices(ApplySpecialPricesEvent e)
	{
		float modifier = PlayerDataManager.handleDataSafely(e.getPlayer(), data -> getLevelProgress(data.getSkillLevel(this)), 0F) * 0.25F;
		
		if(modifier > 0F)
			for(var offer : e.getEntity().getOffers())
			{
				int j = (int) Math.floor(modifier * (double) offer.getBaseCostA().getCount());
				offer.addToSpecialPriceDiff(-j);
			}
	}
}