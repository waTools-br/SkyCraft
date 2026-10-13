package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A trial spawner placed in the mirror stays idle, as it did while SPAWN_MOBS was off everywhere. */
@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerMixin {
	@WrapOperation(
		method = "canSpawnInLevel",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
	)
	private Object skycraft$noSpawnsInMirror(GameRules rules, GameRule<?> rule, Operation<Object> original, @Local(argsOnly = true) ServerLevel level) {
		if (rule == GameRules.SPAWN_MOBS && dev.skycraft.SkyDimension.inMirror(level)) {
			return Boolean.FALSE;
		}
		return original.call(rules, rule);
	}
}
