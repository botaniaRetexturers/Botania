/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.api.mana;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.level.Level;

public interface ManaNetworkCallback {
	Event<ManaNetworkCallback> EVENT = EventFactory.createArrayBacked(ManaNetworkCallback.class,
			listeners -> (level, thing, typ, act) -> {
				for (ManaNetworkCallback listener : listeners) {
					listener.onNetworkChange(level, thing, typ, act);
				}
			});

	/**
	 * @param thing If {@code type} is {@link ManaBlockType#COLLECTOR}, an {@link IManaCollector},
	 *              otherwise if {@code type} is {@link ManaBlockType#POOL}, an {@link IManaPool}
	 */
	void onNetworkChange(Level level, IManaReceiver thing, ManaBlockType type, ManaNetworkAction action);
}
