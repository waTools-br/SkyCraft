package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The rest of what SPAWN_MOBS and SPAWN_MONSTERS allow stays out of the mirror too (see
 * ServerChunkCacheSpawnMixin): isSpawningMonsters, which zombies calling reinforcements, creaking hearts
 * and Nether portals spawning zombified piglins ask, and the skeleton horse traps lightning brings.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSpawnMixin {
	@ModifyReturnValue(method = "isSpawningMonsters", at = @At("RETURN"))
	private boolean skycraft$noMonstersInMirror(boolean original) {
		return original && !dev.skycraft.SkyDimension.inMirror((ServerLevel) (Object) this);
	}

	@WrapOperation(
		method = "tickThunder",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
	)
	private Object skycraft$noTrapHorsesInMirror(GameRules rules, GameRule<?> rule, Operation<Object> original) {
		if (rule == GameRules.SPAWN_MOBS && dev.skycraft.SkyDimension.inMirror((ServerLevel) (Object) this)) {
			return Boolean.FALSE;
		}
		return original.call(rules, rule);
	}
}
