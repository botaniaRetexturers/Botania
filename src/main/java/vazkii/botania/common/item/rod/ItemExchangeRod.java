/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.item.rod;

import com.google.common.collect.ImmutableList;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.item.IBlockProvider;
import vazkii.botania.api.item.IManaProficiencyArmor;
import vazkii.botania.api.item.IWireframeCoordinateListProvider;
import vazkii.botania.api.mana.IManaUsingItem;
import vazkii.botania.api.mana.ManaItemHandler;
import vazkii.botania.client.core.handler.ItemsRemainingRenderHandler;
import vazkii.botania.common.block.BlockPlatform;
import vazkii.botania.common.core.helper.ItemNBTHelper;
import vazkii.botania.common.core.helper.PlayerHelper;
import vazkii.botania.common.item.ItemTemperanceStone;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

public class ItemExchangeRod extends Item implements IManaUsingItem, IWireframeCoordinateListProvider {

	private static final int RANGE = 3;
	private static final int COST = 40;

	private static final String TAG_REPLACEMENT_BLOCK = "blockName";
	private static final String TAG_REPLACEMENT_ITEM = "placedItem";
	private static final String TAG_TARGET_BLOCK_NAME = "targetBlock";
	private static final String TAG_SWAPPING = "swapping";
	private static final String TAG_SELECT_X = "selectX";
	private static final String TAG_SELECT_Y = "selectY";
	private static final String TAG_SELECT_Z = "selectZ";
	private static final String TAG_EXTRA_RANGE = "extraRange";
	private static final String TAG_SWAP_HIT_VEC = "swapHitVec";
	private static final String TAG_SWAP_DIRECTION = "swapDirection";
	private static final String TAG_TEMPERANCE_STONE = "temperanceStone";

	public ItemExchangeRod(Settings props) {
		super(props);
	}

	@Nonnull
	@Override
	public ActionResult useOnBlock(ItemUsageContext ctx) {
		World world = ctx.getWorld();
		BlockPos pos = ctx.getBlockPos();
		PlayerEntity player = ctx.getPlayer();
		ItemStack stack = ctx.getStack();
		BlockState wstate = world.getBlockState(pos);
		Block block = wstate.getBlock();

		if (player != null && player.isSneaking()) {
			BlockEntity tile = world.getBlockEntity(pos);
			if (tile == null && block.asItem() != Items.AIR && BlockPlatform.isValidBlock(wstate, world, pos)
					&& wstate.isOpaque()
					&& block.asItem() instanceof BlockItem) {
				setItemToPlace(stack, block.asItem());
				setSwapDirection(stack, ctx.getSide());
				setHitPos(stack, ctx.getHitPos());

				displayRemainderCounter(player, stack);
				return ActionResult.SUCCESS;
			}
		} else if (canExchange(stack) && !ItemNBTHelper.getBoolean(stack, TAG_SWAPPING, false)) {
			Item replacement = getItemToPlace(stack);
			List<BlockPos> swap = getTargetPositions(world, stack, replacement, pos, block, ctx.getSide());
			if (swap.size() > 0) {
				ItemNBTHelper.setBoolean(stack, TAG_SWAPPING, true);
				ItemNBTHelper.setInt(stack, TAG_SELECT_X, pos.getX());
				ItemNBTHelper.setInt(stack, TAG_SELECT_Y, pos.getY());
				ItemNBTHelper.setInt(stack, TAG_SELECT_Z, pos.getZ());
				setTarget(stack, block);
			}
		}

		return ActionResult.SUCCESS;
	}

	@Override
	public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity player) {
		if (!world.isClient) {
			ItemStack stack = player.getStackInHand(Hand.MAIN_HAND);

			if (!stack.isEmpty() && stack.getItem() == this && canExchange(stack)
					&& ManaItemHandler.instance().requestManaExactForTool(stack, player, COST, false)) {
				int cost = exchange(world, player, pos, stack, getItemToPlace(stack));
				if (cost > 0) {
					ManaItemHandler.instance().requestManaForTool(stack, player, cost, true);
				}
			}
		}
		return false;
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean equipped) {
		if (!canExchange(stack) || !(entity instanceof PlayerEntity)) {
			return;
		}

		// TODO 1.17 remove this - reading legacy replacement block
		if (stack.hasTag() && stack.getTag().contains(TAG_REPLACEMENT_BLOCK, 10)) {
			BlockState state = NbtHelper.toBlockState(stack.getSubTag(TAG_REPLACEMENT_BLOCK));
			setItemToPlace(stack, state.getBlock().asItem());
			stack.removeSubTag(TAG_REPLACEMENT_BLOCK);
		}

		PlayerEntity player = (PlayerEntity) entity;

		int extraRange = ItemNBTHelper.getInt(stack, TAG_EXTRA_RANGE, 1);
		int extraRangeNew = IManaProficiencyArmor.hasProficiency(player, stack) ? 3 : 1;
		if (extraRange != extraRangeNew) {
			ItemNBTHelper.setInt(stack, TAG_EXTRA_RANGE, extraRangeNew);
		}
		boolean temperanceActive = ItemTemperanceStone.hasTemperanceActive(player);
		if (temperanceActive != stack.getOrCreateTag().getBoolean(TAG_TEMPERANCE_STONE)) {
			stack.getOrCreateTag().putBoolean(TAG_TEMPERANCE_STONE, temperanceActive);
		}

		Item replacement = getItemToPlace(stack);
		if (ItemNBTHelper.getBoolean(stack, TAG_SWAPPING, false)) {
			if (!ManaItemHandler.instance().requestManaExactForTool(stack, player, COST, false)) {
				endSwapping(stack);
				return;
			}

			int x = ItemNBTHelper.getInt(stack, TAG_SELECT_X, 0);
			int y = ItemNBTHelper.getInt(stack, TAG_SELECT_Y, 0);
			int z = ItemNBTHelper.getInt(stack, TAG_SELECT_Z, 0);
			Block target = getTargetState(stack);
			List<BlockPos> swap = getTargetPositions(world, stack, replacement, new BlockPos(x, y, z), target, getSwapDirection(stack));
			if (swap.size() == 0) {
				endSwapping(stack);
				return;
			}

			BlockPos coords = swap.get(world.random.nextInt(swap.size()));
			int exchange = exchange(world, player, coords, stack, replacement);
			if (exchange > 0) {
				ManaItemHandler.instance().requestManaForTool(stack, player, exchange, true);
			} else {
				endSwapping(stack);
			}
		}
	}

	public List<BlockPos> getTargetPositions(World world, ItemStack stack, Item toPlace, BlockPos pos, Block toReplace, Direction clickedSide) {
		// Our result list
		List<BlockPos> coordsList = new ArrayList<>();

		// We subtract 1 from the effective range as the center tile is included
		// So, with a range of 3, we are visiting tiles at -2, -1, 0, 1, 2
		// If we have the stone of temperance, on one axis we only visit 0.
		Direction.Axis axis = clickedSide.getAxis();
		int xRange = getRange(stack, axis, Direction.Axis.X);
		int yRange = getRange(stack, axis, Direction.Axis.Y);
		int zRange = getRange(stack, axis, Direction.Axis.Z);

		// Iterate in all 3 dimensions through our possible positions.
		for (int offsetX = -xRange; offsetX <= xRange; offsetX++) {
			for (int offsetY = -yRange; offsetY <= yRange; offsetY++) {
				for (int offsetZ = -zRange; offsetZ <= zRange; offsetZ++) {
					BlockPos pos_ = pos.add(offsetX, offsetY, offsetZ);

					BlockState currentState = world.getBlockState(pos_);

					// If this block is not our target, ignore it, as we don't need
					// to consider replacing it
					if (currentState.getBlock() != toReplace) {
						continue;
					}

					// If this block is already the block we're swapping to,
					// we don't need to swap again
					if (currentState.getBlock().asItem() == toPlace) {
						continue;
					}

					// Check to see if the block is visible on any side:
					for (Direction dir : Direction.values()) {
						BlockPos adjPos = pos_.offset(dir);
						BlockState adjState = world.getBlockState(adjPos);

						if (!Block.isFaceFullSquare(adjState.getSidesShape(world, pos), dir.getOpposite())) {
							coordsList.add(pos_);
							break;
						}
					}
				}
			}
		}

		return coordsList;
	}

	public int exchange(World world, PlayerEntity player, BlockPos pos, ItemStack rod, Item replacement) {
		BlockEntity tile = world.getBlockEntity(pos);
		if (tile != null) {
			return 0;
		}

		ItemStack placeStack = removeFromInventory(player, rod, replacement, false);
		if (!placeStack.isEmpty()) {
			BlockState stateAt = world.getBlockState(pos);
			Block blockAt = stateAt.getBlock();
			if (!stateAt.isAir() && stateAt.calcBlockBreakingDelta(player, world, pos) > 0
					&& stateAt.getBlock().asItem() != replacement) {
				float hardness = stateAt.getHardness(world, pos);
				if (!world.isClient) {
					world.breakBlock(pos, !player.abilities.creativeMode);
					BlockHitResult hit = new BlockHitResult(getHitPos(rod, pos), getSwapDirection(rod), pos, false);
					ActionResult result = PlayerHelper.substituteUse(new ItemUsageContext(player, Hand.MAIN_HAND, hit), placeStack);
					// TODO: provide an use context that overrides player facing direction/yaw?
					//  currently it pulls from the player directly

					if (!player.abilities.creativeMode) {
						if (result.isAccepted()) {
							removeFromInventory(player, rod, replacement, true);
							displayRemainderCounter(player, rod);
						} else {
							((ServerWorld) world).spawnParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
									2, 0.1, 0.1, 0.1, 0);
						}
					}
				}
				return hardness <= 10 ? COST : (int) (0.5 * COST + 3 * hardness);
			}
		}

		return 0;
	}

	public boolean canExchange(ItemStack stack) {
		return getItemToPlace(stack) != Items.AIR;
	}

	public static ItemStack removeFromInventory(PlayerEntity player, Inventory inv, ItemStack tool, Item requested, boolean doit) {
		List<ItemStack> providers = new ArrayList<>();
		for (int i = inv.size() - 1; i >= 0; i--) {
			ItemStack invStack = inv.getStack(i);
			if (invStack.isEmpty()) {
				continue;
			}

			Item item = invStack.getItem();
			if (item == requested) {
				ItemStack ret;
				if (doit) {
					ret = inv.removeStack(i, 1);
				} else {
					ret = invStack.copy();
					ret.setCount(1);
				}
				return ret;
			}

			if (item instanceof IBlockProvider) {
				providers.add(invStack);
			}
		}

		if (requested instanceof BlockItem) {
			Block block = ((BlockItem) requested).getBlock();
			for (ItemStack provStack : providers) {
				IBlockProvider prov = (IBlockProvider) provStack.getItem();
				if (prov.provideBlock(player, tool, provStack, block, doit)) {
					return new ItemStack(requested);
				}
			}
		}

		return ItemStack.EMPTY;
	}

	public static ItemStack removeFromInventory(PlayerEntity player, ItemStack tool, Item item, boolean doit) {
		if (player.abilities.creativeMode) {
			return new ItemStack(item);
		}

		ItemStack outStack = removeFromInventory(player, BotaniaAPI.instance().getAccessoriesInventory(player), tool, item, doit);
		if (outStack.isEmpty()) {
			outStack = removeFromInventory(player, player.inventory, tool, item, doit);
		}
		return outStack;
	}

	public static int getInventoryItemCount(PlayerEntity player, ItemStack stack, Item item) {
		if (player.abilities.creativeMode) {
			return -1;
		}

		int baubleCount = getInventoryItemCount(player, BotaniaAPI.instance().getAccessoriesInventory(player), stack, item);
		if (baubleCount == -1) {
			return -1;
		}

		int count = getInventoryItemCount(player, player.inventory, stack, item);
		if (count == -1) {
			return -1;
		}

		return count + baubleCount;
	}

	public static int getInventoryItemCount(PlayerEntity player, Inventory inv, ItemStack stack, Item requested) {
		if (player.abilities.creativeMode) {
			return -1;
		}

		int count = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack invStack = inv.getStack(i);
			if (invStack.isEmpty()) {
				continue;
			}

			Item item = invStack.getItem();
			if (item == requested.asItem()) {
				count += invStack.getCount();
			}

			if (item instanceof IBlockProvider && requested instanceof BlockItem) {
				IBlockProvider prov = (IBlockProvider) item;
				int provCount = prov.getBlockCount(player, stack, invStack, ((BlockItem) requested).getBlock());
				if (provCount == -1) {
					return -1;
				}
				count += provCount;
			}
		}

		return count;
	}

	public void displayRemainderCounter(PlayerEntity player, ItemStack stack) {
		if (!player.world.isClient) {
			Item item = getItemToPlace(stack);
			int count = getInventoryItemCount(player, stack, item);
			ItemsRemainingRenderHandler.send(player, new ItemStack(item), count);
		}
	}

	@Override
	public boolean usesMana(ItemStack stack) {
		return true;
	}

	private void setItemToPlace(ItemStack stack, Item item) {
		ItemNBTHelper.setString(stack, TAG_REPLACEMENT_ITEM, Registry.ITEM.getKey(item).toString());
	}

	private Item getItemToPlace(ItemStack stack) {
		return Registry.ITEM.get(new Identifier(ItemNBTHelper.getString(stack, TAG_REPLACEMENT_ITEM, "air")));
	}

	private void setHitPos(ItemStack stack, Vec3d vec) {
		ListTag list = new ListTag();
		list.add(DoubleTag.of(MathHelper.fractionalPart(vec.getX())));
		list.add(DoubleTag.of(MathHelper.fractionalPart(vec.getY())));
		list.add(DoubleTag.of(MathHelper.fractionalPart(vec.getZ())));
		stack.getOrCreateTag().put(TAG_SWAP_HIT_VEC, list);
	}

	private Vec3d getHitPos(ItemStack stack, BlockPos pos) {
		ListTag list = stack.getOrCreateTag().getList(TAG_SWAP_HIT_VEC, 6);
		return new Vec3d(pos.getX() + list.getDouble(0),
				pos.getY() + list.getDouble(1),
				pos.getZ() + list.getDouble(2));
	}

	private void setSwapDirection(ItemStack stack, Direction direction) {
		stack.getOrCreateTag().putInt(TAG_SWAP_DIRECTION, direction.getId());
	}

	private Direction getSwapDirection(ItemStack stack) {
		return Direction.byId(stack.getOrCreateTag().getInt(TAG_SWAP_DIRECTION));
	}

	private int getRange(ItemStack stack, Direction.Axis clickAxis, Direction.Axis rangeAxis) {
		if (stack.getOrCreateTag().getBoolean(TAG_TEMPERANCE_STONE) && rangeAxis == clickAxis) {
			return 0;
		}
		return RANGE + ItemNBTHelper.getInt(stack, TAG_EXTRA_RANGE, 1) - 1;
	}

	private static void endSwapping(ItemStack stack) {
		ItemNBTHelper.setBoolean(stack, TAG_SWAPPING, false);
		ItemNBTHelper.removeEntry(stack, TAG_SELECT_X);
		ItemNBTHelper.removeEntry(stack, TAG_SELECT_Y);
		ItemNBTHelper.removeEntry(stack, TAG_SELECT_Z);
		ItemNBTHelper.removeEntry(stack, TAG_TARGET_BLOCK_NAME);
	}

	@Nonnull
	@Override
	public Text getName(@Nonnull ItemStack stack) {
		Item item = getItemToPlace(stack);
		MutableText cmp = super.getName(stack).shallowCopy();
		if (item != Items.AIR) {
			cmp.append(" (");
			Text sub = new ItemStack(item).getName();
			cmp.append(sub.shallowCopy().formatted(Formatting.GREEN));
			cmp.append(")");
		}
		return cmp;
	}

	private void setTarget(ItemStack stack, Block block) {
		ItemNBTHelper.setString(stack, TAG_TARGET_BLOCK_NAME, Registry.BLOCK.getId(block).toString());
	}

	public static Block getTargetState(ItemStack stack) {
		Identifier id = new Identifier(ItemNBTHelper.getString(stack, TAG_TARGET_BLOCK_NAME, "minecraft:air"));
		return Registry.BLOCK.get(id);
	}

	@Override
	@Environment(EnvType.CLIENT)
	public List<BlockPos> getWireframesToDraw(PlayerEntity player, ItemStack stack) {
		ItemStack holding = player.getMainHandStack();
		if (holding != stack || !canExchange(stack)) {
			return ImmutableList.of();
		}

		HitResult pos = MinecraftClient.getInstance().crosshairTarget;
		if (pos != null && pos.getType() == HitResult.Type.BLOCK) {
			BlockPos bPos = ((BlockHitResult) pos).getBlockPos();
			Block target = MinecraftClient.getInstance().world.getBlockState(bPos).getBlock();
			if (ItemNBTHelper.getBoolean(stack, TAG_SWAPPING, false)) {
				bPos = new BlockPos(
						ItemNBTHelper.getInt(stack, TAG_SELECT_X, 0),
						ItemNBTHelper.getInt(stack, TAG_SELECT_Y, 0),
						ItemNBTHelper.getInt(stack, TAG_SELECT_Z, 0)
				);
				target = getTargetState(stack);
			}

			if (!player.world.isAir(bPos)) {
				Item item = getItemToPlace(stack);
				List<BlockPos> coordsList = getTargetPositions(player.world, stack, item, bPos, target, ((BlockHitResult) pos).getSide());
				coordsList.removeIf(bPos::equals);
				return coordsList;
			}

		}
		return ImmutableList.of();
	}

}
