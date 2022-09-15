/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.block.flower.functional;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.random.Weight;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.block_entity.FunctionalFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;
import vazkii.botania.api.recipe.OrechidRecipe;
import vazkii.botania.common.block.BotaniaFlowerBlocks;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;
import vazkii.botania.common.handler.BotaniaSounds;
import vazkii.botania.common.handler.OrechidManager;
import vazkii.botania.xplat.BotaniaConfig;
import vazkii.botania.xplat.XplatAbstractions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class OrechidBlockEntity extends FunctionalFlowerBlockEntity {
	private static final int COST = 17500;
	private static final int COST_GOG = 700;
	private static final int DELAY = 100;
	private static final int DELAY_GOG = 2;
	private static final int RANGE = 5;
	private static final int RANGE_Y = 3;

	protected OrechidBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public OrechidBlockEntity(BlockPos pos, BlockState state) {
		this(BotaniaFlowerBlocks.ORECHID, pos, state);
	}

	@Override
	public void tickFlower() {
		super.tickFlower();

		if (getLevel().isClientSide || redstoneSignal > 0 || !canOperate()) {
			return;
		}

		int cost = getCost();
		if (getMana() >= cost && ticksExisted % getDelay() == 0) {
			BlockPos coords = getCoordsToPut();
			if (coords != null) {
				BlockState state = getOreToPut(coords, getLevel().getBlockState(coords));
				if (state != null) {
					getLevel().setBlockAndUpdate(coords, state);
					if (BotaniaConfig.common().blockBreakParticles()) {
						getLevel().levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, coords, Block.getId(state));
					}
					playSound(coords);

					addMana(-cost);
					sync();
				}
			}
		}
	}

	protected void playSound(BlockPos coords) {
		getLevel().playSound(null, coords, BotaniaSounds.orechid, SoundSource.BLOCKS, 1F, 1F);
	}

	@Nullable
	private BlockState getOreToPut(BlockPos coords, BlockState state) {
		List<Output> values = new ArrayList<>();
		for (OrechidRecipe recipe : OrechidManager.getFor(getLevel().getRecipeManager(), getRecipeType())
				.get(state.getBlock())) {
			Output output = new Output(recipe, recipe.getWeight(getLevel(), coords));
			values.add(output);
		}
		return WeightedRandom.getRandomItem(getLevel().random, values)
				.map(oo -> oo.recipe.getOutput(getLevel(), coords)
						.pick(getLevel().getRandom()))
				.orElse(null);
	}

	private static class Output implements WeightedEntry {
		private final Weight weight;
		private final OrechidRecipe recipe;

		public Output(OrechidRecipe recipe, int weight) {
			this.weight = Weight.of(weight);
			this.recipe = recipe;
		}

		@Override
		public Weight getWeight() {
			return weight;
		}
	}

	private BlockPos getCoordsToPut() {
		List<BlockPos> possibleCoords = new ArrayList<>();
		var matcher = getReplaceMatcher();
		for (BlockPos pos : BlockPos.betweenClosed(getEffectivePos().offset(-getRange(), -getRangeY(), -getRange()),
				getEffectivePos().offset(getRange(), getRangeY(), getRange()))) {
			BlockState state = getLevel().getBlockState(pos);
			if (matcher.test(state)) {
				possibleCoords.add(pos.immutable());
			}
		}

		if (possibleCoords.isEmpty()) {
			return null;
		}
		return possibleCoords.get(getLevel().random.nextInt(possibleCoords.size()));
	}

	public boolean canOperate() {
		return true;
	}

	public RecipeType<? extends OrechidRecipe> getRecipeType() {
		return BotaniaRecipeTypes.ORECHID_TYPE;
	}

	public Predicate<BlockState> getReplaceMatcher() {
		var map = OrechidManager.getFor(getLevel().getRecipeManager(), getRecipeType());
		return state -> map.containsKey(state.getBlock());
	}

	public int getCost() {
		return XplatAbstractions.INSTANCE.gogLoaded() ? COST_GOG : COST;
	}

	public int getDelay() {
		return XplatAbstractions.INSTANCE.gogLoaded() ? DELAY_GOG : DELAY;
	}

	@Override
	public RadiusDescriptor getRadius() {
		return RadiusDescriptor.Rectangle.square(getEffectivePos(), getRange());
	}

	@Override
	public boolean acceptsRedstone() {
		return true;
	}

	public int getRange() {
		return RANGE;
	}

	public int getRangeY() {
		return RANGE_Y;
	}

	@Override
	public int getColor() {
		return 0x818181;
	}

	@Override
	public int getMaxMana() {
		return getCost();
	}

}
