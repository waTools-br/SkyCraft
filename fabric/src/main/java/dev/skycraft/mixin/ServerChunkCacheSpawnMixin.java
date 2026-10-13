package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * No natural spawning in the Skyrim mirror. The spawn game rules are server-wide and on (natural_spawns),
 * so the Nether, Tamriel Wilds and Home fill with animals and monsters; the mirror is a void holding only
 * the player's blocks, and to it SPAWN_MOBS still reads off. tickChunks reads it once a tick for both the
 * natural spawns and the custom spawners (phantoms, patrols, cats, sieges, wandering traders).
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheSpawnMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@WrapOperation(
		method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
	)
	private Object skycraft$noSpawnsInMirror(GameRules rules, GameRule<?> rule, Operation<Object> original) {
		if (rule == GameRules.SPAWN_MOBS && dev.skycraft.SkyDimension.inMirror(this.level)) {
			return Boolean.FALSE;
		}
		return original.call(rules, rule);
	}
}
