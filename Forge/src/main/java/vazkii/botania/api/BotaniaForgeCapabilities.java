package vazkii.botania.api;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import vazkii.botania.api.block.IExoflameHeatable;
import vazkii.botania.api.block.IHornHarvestable;
import vazkii.botania.api.block.IHourglassTrigger;
import vazkii.botania.api.block.IWandable;
import vazkii.botania.api.item.IAvatarWieldable;
import vazkii.botania.api.item.IBlockProvider;
import vazkii.botania.api.item.ICoordBoundItem;
import vazkii.botania.api.item.IRelic;
import vazkii.botania.api.mana.IManaCollisionGhost;
import vazkii.botania.api.mana.IManaItem;
import vazkii.botania.api.mana.IManaReceiver;
import vazkii.botania.api.mana.IManaTrigger;
import vazkii.botania.api.mana.spark.ISparkAttachable;

public final class BotaniaForgeCapabilities {
	public static final Capability<IAvatarWieldable> AVATAR_WIELDABLE = CapabilityManager.get(new CapabilityToken<>() {});
	public static final Capability<IBlockProvider> BLOCK_PROVIDER = CapabilityManager.get(new CapabilityToken<>() {});
	public static final Capability<ICoordBoundItem> COORD_BOUND_ITEM = CapabilityManager.get(new CapabilityToken<>() {});
	public static final Capability<IManaItem> MANA_ITEM = CapabilityManager.get(new CapabilityToken<>() {});
	public static final Capability<IRelic> RELIC = CapabilityManager.get(new CapabilityToken<>() {});

	public static final Capability<IExoflameHeatable> EXOFLAME_HEATABLE = CapabilityManager.get(new CapabilityToken<>() {});
	public static final Capability<IHornHarvestable> HORN_HARVEST = CapabilityManager.get(new CapabilityToken<>() {});
	public static final Capability<IHourglassTrigger> HOURGLASS_TRIGGER = CapabilityManager.get(new CapabilityToken<>() {});
	public static final Capability<IManaCollisionGhost> MANA_GHOST = CapabilityManager.get(new CapabilityToken<>() {});
	public static final Capability<IManaReceiver> MANA_RECEIVER = CapabilityManager.get(new CapabilityToken<>() {});
	public static final Capability<ISparkAttachable> SPARK_ATTACHABLE = CapabilityManager.get(new CapabilityToken<>() {});
	public static final Capability<IManaTrigger> MANA_TRIGGER = CapabilityManager.get(new CapabilityToken<>() {});
	public static final Capability<IWandable> WANDABLE = CapabilityManager.get(new CapabilityToken<>() {});

	private BotaniaForgeCapabilities() {}
}
