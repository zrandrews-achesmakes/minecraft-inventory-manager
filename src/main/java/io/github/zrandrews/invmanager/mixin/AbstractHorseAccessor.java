package io.github.zrandrews.invmanager.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

/** Gives the Sorter access to a llama/donkey/mule's chest. */
@Mixin(AbstractHorse.class)
public interface AbstractHorseAccessor {
	@Accessor("inventory")
	SimpleContainer invmanager$getInventory();
}
