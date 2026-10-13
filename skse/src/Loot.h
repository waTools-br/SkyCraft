#pragma once

#include "Link.h"

// Looting bridge. Every item that lands in the player's Skyrim inventory (looted corpses, chests,
// items picked up with activate, crafted, bought) is logged, tagged SkyCraft-loot, and sent to
// Minecraft as a proto::ItemEvent. Minecraft decides from its rules file what converts; for those
// it gives the Minecraft items and sends kEvTakeItem back, and Take() removes the original here.
// What a dying actor wore goes the same way (Gear drops), but stays in Skyrim whatever Minecraft does.
namespace skycraft::Loot
{
	void Install();
	// Main thread: remove an item Minecraft converted from the player's Skyrim inventory.
	void Take(RE::PlayerCharacter* a_player, RE::FormID a_item, std::int32_t a_count);

	// Gear drops. Main thread, for each actor as the actor table is written: remembers what it wears (when
	// first seen, then every couple of seconds) and, the frame it dies, queues that gear for Minecraft as
	// worn ItemEvents (proto::kSrcWorn). Skyrim knocks the weapon out of a dying actor's hands, so what goes
	// is what was last seen. The player's followers and summoned or raised actors send nothing.
	void WatchWorn(RE::Actor* a_actor, float a_delta);
	// Main thread, once a frame after the actor table: forgets actors long gone from it and sends a few
	// queued worn ItemEvents, so a crowd dying at once can't fill the item ring.
	void SendWorn(float a_delta);
	// Skyrim has the player (no actor table): forget what was watched and queued.
	void ForgetWorn();
}
