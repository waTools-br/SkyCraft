#include "Loot.h"

#include <algorithm>
#include <cstring>

namespace skycraft::Loot
{
	namespace
	{
		std::string_view TypeName(RE::FormType a_type)
		{
			switch (a_type) {
			case RE::FormType::Weapon:
				return "weapon";
			case RE::FormType::Armor:
				return "armor";
			case RE::FormType::Ammo:
				return "ammo";
			case RE::FormType::AlchemyItem:
				return "potion";
			case RE::FormType::Ingredient:
				return "ingredient";
			case RE::FormType::Misc:
				return "misc";
			case RE::FormType::Book:
				return "book";
			case RE::FormType::Scroll:
				return "scroll";
			case RE::FormType::KeyMaster:
				return "key";
			case RE::FormType::SoulGem:
				return "soulgem";
			case RE::FormType::Light:
				return "light";
			default:
				return "other";
			}
		}

		struct Source
		{
			std::string        text;
			proto::ItemSource  kind = proto::kSrcWorld;
			bool               chest = false;
		};

		// Where the item came from: the world, a corpse, a container or a living actor. Bought items come
		// from the merchant's chest (a container, usually named "Chest") or the merchant (an actor).
		// The chest test reads the English display name.
		Source FromWhere(RE::FormID a_from)
		{
			if (a_from == 0) {
				return { "world", proto::kSrcWorld };
			}
			auto* ref = RE::TESForm::LookupByID<RE::TESObjectREFR>(a_from);
			if (!ref) {
				return { std::format("{:08X}", a_from), proto::kSrcContainer };
			}
			const char* name = ref->GetDisplayFullName();
			std::string label = name && *name ? name : std::format("{:08X}", a_from);
			if (auto* actor = ref->As<RE::Actor>()) {
				const bool dead = actor->IsDead();
				return { std::format("{} of {}", dead ? "corpse" : "actor", label), dead ? proto::kSrcCorpse : proto::kSrcActor };
			}
			const bool chest = label.find("Chest") != std::string::npos || label.find("chest") != std::string::npos || label.find("Strongbox") != std::string::npos ||
			                   label.find("Coffer") != std::string::npos;
			return { "container " + label, proto::kSrcContainer, chest };
		}

		proto::ItemMaterial MaterialFrom(std::string_view a_keyword)
		{
			// WeapMaterialIron, ArmorMaterialSteelPlate, DLC1WeapMaterialDragonbone, ...
			const auto at = a_keyword.find("Material");
			if (at == std::string_view::npos) {
				return proto::kMatNone;
			}
			const auto m = a_keyword.substr(at + 8);
			auto starts = [&](std::string_view p) { return m.substr(0, p.size()) == p; };
			if (starts("Wood")) return proto::kMatWood;
			if (starts("Iron")) return proto::kMatIron;
			if (starts("Steel")) return proto::kMatSteel;
			if (starts("Imperial")) return proto::kMatImperial;
			if (starts("Orcish")) return proto::kMatOrcish;
			if (starts("Dwarven")) return proto::kMatDwarven;
			if (starts("Elven")) return proto::kMatElven;
			if (starts("Glass")) return proto::kMatGlass;
			if (starts("Ebony")) return proto::kMatEbony;
			if (starts("Daedric")) return proto::kMatDaedric;
			if (starts("Dragon")) return proto::kMatDragon;
			if (starts("Silver")) return proto::kMatSilver;
			if (starts("Hide") || starts("Leather") || starts("Studded") || starts("Fur")) return proto::kMatHide;
			if (starts("Draugr")) return proto::kMatDraugr;
			if (starts("Falmer")) return proto::kMatFalmer;
			return proto::kMatNone;
		}

		// Tags and material from the item's keywords; the keyword list as text for the log.
		std::string ReadKeywords(RE::TESForm* a_item, std::uint32_t& a_tags, std::uint8_t& a_material)
		{
			std::string out;
			auto* form = a_item->As<RE::BGSKeywordForm>();
			if (!form) {
				return out;
			}
			for (std::uint32_t i = 0; i < form->numKeywords; ++i) {
				auto* kw = form->keywords[i];
				if (!kw) {
					continue;
				}
				const char*      raw = kw->GetFormEditorID();
				std::string_view id = raw ? raw : "";
				if (id == "VendorItemFood") a_tags |= proto::kTagFood;
				else if (id == "VendorItemFoodRaw") a_tags |= proto::kTagFoodRaw;
				else if (id == "VendorItemAnimalHide") a_tags |= proto::kTagHide;
				else if (id == "VendorItemOreIngot") a_tags |= proto::kTagOreIngot;
				else if (id == "VendorItemPotion") a_tags |= proto::kTagPotion;
				else if (id == "VendorItemPoison") a_tags |= proto::kTagPoison;
				else if (id == "VendorItemIngredient") a_tags |= proto::kTagIngredient;
				else if (id == "ArmorClothing") a_tags |= proto::kTagClothing;
				else if (id == "ArmorJewelry") a_tags |= proto::kTagJewelry;
				else if (id == "VendorItemGem") a_tags |= proto::kTagGem;
				else if (id == "VendorItemClutter") a_tags |= proto::kTagClutter;
				if (a_material == proto::kMatNone) {
					a_material = MaterialFrom(id);
				}
				if (!out.empty()) {
					out += ' ';
				}
				out += id.empty() ? std::format("{:08X}", kw->GetFormID()) : std::string(id);
			}
			return out;
		}

		// What kind of weapon or armor piece an item is, as Skyrim types it: a weapon's type, the biped slots an
		// armor piece covers (the body wins over the head, for robes with a hood). kKindNone for anything else.
		proto::ItemKind KindOf(RE::TESForm* a_item)
		{
			if (const auto* weapon = a_item->As<RE::TESObjectWEAP>()) {
				switch (weapon->GetWeaponType()) {
				case RE::WEAPON_TYPE::kHandToHandMelee:
					return proto::kKindHandToHand;
				case RE::WEAPON_TYPE::kOneHandSword:
					return proto::kKindSword;
				case RE::WEAPON_TYPE::kOneHandDagger:
					return proto::kKindDagger;
				case RE::WEAPON_TYPE::kOneHandAxe:
					return proto::kKindWarAxe;
				case RE::WEAPON_TYPE::kOneHandMace:
					return proto::kKindMace;
				case RE::WEAPON_TYPE::kTwoHandSword:
					return proto::kKindGreatsword;
				case RE::WEAPON_TYPE::kTwoHandAxe:
					return proto::kKindBattleaxe;
				case RE::WEAPON_TYPE::kBow:
					return proto::kKindBow;
				case RE::WEAPON_TYPE::kStaff:
					return proto::kKindStaff;
				case RE::WEAPON_TYPE::kCrossbow:
					return proto::kKindCrossbow;
				default:
					return proto::kKindNone;
				}
			}
			if (const auto* armor = a_item->As<RE::TESObjectARMO>()) {
				using Slot = RE::BGSBipedObjectForm::BipedObjectSlot;
				const auto on = [armor](Slot a_slot) { return armor->HasPartOf(a_slot); };
				if (on(Slot::kShield)) {
					return proto::kKindShield;
				}
				if (on(Slot::kBody)) {
					return proto::kKindCuirass;
				}
				if (on(Slot::kHead) || on(Slot::kHair) || on(Slot::kLongHair) || on(Slot::kCirclet)) {
					return proto::kKindHelmet;
				}
				if (on(Slot::kHands) || on(Slot::kForearms)) {
					return proto::kKindGauntlets;
				}
				if (on(Slot::kFeet) || on(Slot::kCalves)) {
					return proto::kKindBoots;
				}
			}
			return proto::kKindNone;
		}

		// What every ItemEvent says about an item: tags from its form type and keywords, its kind, material and
		// name. Returns the keyword list as text, for the log.
		std::string Describe(RE::TESForm* a_item, proto::ItemEvent& a_ev)
		{
			switch (a_item->GetFormType()) {
			case RE::FormType::Weapon:
				a_ev.tags |= proto::kTagWeapon;
				break;
			case RE::FormType::Armor:
				a_ev.tags |= proto::kTagArmor;
				break;
			case RE::FormType::Ammo:
				a_ev.tags |= proto::kTagArrow;
				break;
			case RE::FormType::KeyMaster:
				a_ev.tags |= proto::kTagKey;
				break;
			case RE::FormType::Book:
			case RE::FormType::Scroll:
				a_ev.tags |= proto::kTagBook;
				break;
			case RE::FormType::SoulGem:
				// Portal keys: the filled base forms (SoulGemGrandFilled and kin) hold their soul on the form.
				// A gem filled by Soul Trap keeps its soul on the inventory stack instead, so it isn't tagged.
				a_ev.tags |= proto::kTagSoulGem;
				if (const auto* gem = a_item->As<RE::TESSoulGem>(); gem && gem->GetContainedSoul() != RE::SOUL_LEVEL::kNone) {
					a_ev.tags |= proto::kTagSoulFilled;
				}
				break;
			default:
				break;
			}
			if (a_item->GetFormID() == 0xF) {
				a_ev.tags |= proto::kTagGold;
			}
			std::string keywords = ReadKeywords(a_item, a_ev.tags, a_ev.material);
			if (a_ev.tags & (proto::kTagClothing | proto::kTagJewelry)) {
				a_ev.tags &= ~proto::kTagArmor;
			}
			a_ev.tags |= (static_cast<std::uint32_t>(KindOf(a_item)) << proto::kTagKindShift) & proto::kTagKindMask;
			const char* name = a_item->GetName();
			strncpy_s(a_ev.name, name ? name : "", _TRUNCATE);
			return keywords;
		}

		// ---- gear worn by the actors in the actor table (Gear drops) ------------------------------
		constexpr float       kWornLookSeconds = 2.0f;        // how often a live actor's gear is looked at again
		constexpr float       kWornLookFightSeconds = 0.5f;   // in a fight, where weapons change hands
		constexpr float       kWornForgetSeconds = 10.0f;     // gone from the table this long: forgotten
		constexpr std::size_t kWornPerFrame = 8;              // worn ItemEvents sent a frame, so a crowd dying at
		                                                       // once can't fill the item ring and push out loot
		constexpr std::size_t kWornQueueMax = 256;

		struct WornPiece
		{
			RE::FormID   item{ 0 };
			std::int32_t count{ 0 };  // ammo: the quiver; a weapon in both hands: 2
		};

		struct Watched
		{
			std::vector<WornPiece> gear;
			float                  look{ 0.0f };     // seconds to the next look
			float                  unseen{ 0.0f };   // seconds out of the table
			bool                   seen{ false };    // in this frame's table
			bool                   dead{ false };
			bool                   theirs{ false };  // a follower, or raised or summoned by magic: not Minecraft's to drop
		};

		std::unordered_map<RE::FormID, Watched> watched;
		std::deque<proto::ItemEvent>             wornQueue;

		// What a live actor wears now: both hands' weapons, its shield, armor and clothes, its ammo. Bound
		// weapons a spell made and gear Skyrim never lets the player take (non-playable) are left out: they
		// vanish with the actor or stay on it in Skyrim too.
		void Look(RE::Actor* a_actor, Watched& a_watched)
		{
			a_watched.gear.clear();
			a_watched.theirs = a_actor->IsPlayerTeammate() || a_actor->IsCommandedActor() || a_actor->IsSummoned();
			const auto inventory = a_actor->GetInventory([](RE::TESBoundObject& a_obj) { return a_obj.IsWeapon() || a_obj.IsArmor() || a_obj.IsAmmo(); }, true);
			for (const auto& [obj, data] : inventory) {
				const auto& [count, entry] = data;
				if (count <= 0 || !entry || !entry->IsWorn()) {
					continue;
				}
				std::int32_t n = 1;
				if (const auto* weapon = obj->As<RE::TESObjectWEAP>()) {
					if (weapon->IsBound() || weapon->weaponData.flags.all(RE::TESObjectWEAP::Data::Flag::kNonPlayable)) {
						continue;
					}
					if (entry->IsWorn(false) && entry->IsWorn(true)) {
						n = std::min(count, 2);
					}
				} else if (auto* ammo = obj->As<RE::TESAmmo>()) {
					if (ammo->GetRuntimeData().data.flags.all(RE::AMMO_DATA::Flag::kNonPlayable)) {
						continue;
					}
					n = count;
				} else if ((obj->GetFormFlags() & RE::TESObjectARMO::RecordFlags::kNonPlayable) != 0) {
					continue;
				}
				a_watched.gear.push_back({ obj->GetFormID(), n });
			}
		}

		// The frame an actor dies: one worn ItemEvent per piece it was last seen wearing, queued for Minecraft.
		void SendGear(RE::Actor* a_actor, const Watched& a_watched)
		{
			if (a_watched.gear.empty()) {
				return;
			}
			const char*       who = a_actor->GetDisplayFullName();
			const std::string label = who && *who ? std::string(who) : std::format("{:08X}", a_actor->GetFormID());
			// A follower's gear stays on their body for the player to take in Skyrim; a summoned or raised
			// actor's goes with it.
			if (a_watched.theirs || a_actor->IsPlayerTeammate() || a_actor->IsCommandedActor()) {
				logger::info("SkyCraft-loot: {} died; a follower or summoned, its gear stays in Skyrim", label);
				return;
			}
			std::string list;
			for (const auto& piece : a_watched.gear) {
				auto* item = RE::TESForm::LookupByID(piece.item);
				if (!item || wornQueue.size() >= kWornQueueMax) {
					continue;
				}
				proto::ItemEvent ev{};
				ev.formId = piece.item;
				ev.count = -piece.count;  // negative: a Minecraft jar from before kSrcWorn skips it
				Describe(item, ev);
				ev.source = proto::kSrcWorn;
				ev.container = a_actor->GetFormID();
				wornQueue.push_back(ev);
				if (!list.empty()) {
					list += ", ";
				}
				const std::string_view name(ev.name);
				const auto             kind = (ev.tags & proto::kTagKindMask) >> proto::kTagKindShift;
				list += piece.count > 1 ? std::format("{} x {} (kind {})", piece.count, name, kind) : std::format("{} (kind {})", name, kind);
			}
			logger::info("SkyCraft-loot: {} died wearing {}", label, list);
		}

		class ContainerSink final : public RE::BSTEventSink<RE::TESContainerChangedEvent>
		{
		public:
			static ContainerSink* Get()
			{
				static ContainerSink sink;
				return &sink;
			}

			RE::BSEventNotifyControl ProcessEvent(const RE::TESContainerChangedEvent* a_event, RE::BSTEventSource<RE::TESContainerChangedEvent>*) override
			{
				auto* player = RE::PlayerCharacter::GetSingleton();
				if (!a_event || !player || a_event->newContainer != player->GetFormID() || a_event->itemCount <= 0) {
					return RE::BSEventNotifyControl::kContinue;
				}
				auto* item = RE::TESForm::LookupByID(a_event->baseObj);
				if (!item) {
					return RE::BSEventNotifyControl::kContinue;
				}
				proto::ItemEvent ev{};
				ev.formId = item->GetFormID();
				ev.count = a_event->itemCount;
				const std::string keywords = Describe(item, ev);
				std::string_view type = TypeName(item->GetFormType());
				std::int32_t     value = 0;
				if (auto* alch = item->As<RE::AlchemyItem>()) {
					if (alch->IsFood()) {
						type = "food";
					}
					value = alch->GetGoldValue();
				} else if (auto* valueForm = item->As<RE::TESValueForm>()) {
					value = valueForm->value;
				}
				const Source from = FromWhere(a_event->oldContainer);
				ev.source = from.kind;
				// Anything that arrives while Skyrim's shop is open was bought (from the merchant or their chest).
				const bool bought = (from.kind == proto::kSrcContainer || from.kind == proto::kSrcActor) && RE::UI::GetSingleton() &&
				                    RE::UI::GetSingleton()->IsMenuOpen(RE::BarterMenu::MENU_NAME);
				if (bought) {
					ev.source = proto::kSrcBought;
				}
				ev.container = a_event->oldContainer;
				ev.chest = from.chest ? 1 : 0;
				if (auto* bound = item->As<RE::TESBoundObject>()) {
					// Quest items stay in Skyrim: Minecraft never converts them (removing one can break its quest).
					const auto inventory = player->GetInventory([bound](RE::TESBoundObject& a_obj) { return &a_obj == bound; });
					const auto it = inventory.find(bound);
					ev.quest = it != inventory.end() && it->second.second && it->second.second->IsQuestObject() ? 1 : 0;
				}
				const char* name = item->GetName();
				logger::info("SkyCraft-loot: got {} x {} [{} {:08X} kind {}] value {} from {} | {}", ev.count, name && *name ? name : "?", type, ev.formId,
					(ev.tags & proto::kTagKindMask) >> proto::kTagKindShift, value, from.text, keywords);
				Link::Get().PushItem(ev);
				return RE::BSEventNotifyControl::kContinue;
			}
		};
	}

	void Install()
	{
		if (auto* events = RE::ScriptEventSourceHolder::GetSingleton()) {
			events->AddEventSink<RE::TESContainerChangedEvent>(ContainerSink::Get());
			logger::info("SkyCraft-loot: watching the player's inventory");
		}
	}

	void Take(RE::PlayerCharacter* a_player, RE::FormID a_item, std::int32_t a_count)
	{
		auto* item = RE::TESForm::LookupByID<RE::TESBoundObject>(a_item);
		if (!a_player || !item || a_count <= 0) {
			return;
		}
		const auto inventory = a_player->GetInventory([item](RE::TESBoundObject& a_obj) { return &a_obj == item; });
		const auto it = inventory.find(item);
		const std::int32_t have = it != inventory.end() ? it->second.first : 0;
		const std::int32_t take = std::min(a_count, have);
		if (take <= 0) {
			logger::warn("SkyCraft-loot: Minecraft converted {:08X} but the player no longer has it", a_item);
			return;
		}
		// RemoveItem would take a quest item too and could break its quest: it stays (the player keeps both).
		if (it->second.second && it->second.second->IsQuestObject()) {
			logger::warn("SkyCraft-loot: Minecraft converted {} but it's a quest item; left in Skyrim", item->GetName());
			return;
		}
		a_player->RemoveItem(item, take, RE::ITEM_REMOVE_REASON::kRemove, nullptr, nullptr);
		logger::info("SkyCraft-loot: converted {} x {} to Minecraft, removed from Skyrim", take, item->GetName());
	}

	void WatchWorn(RE::Actor* a_actor, float a_delta)
	{
		const auto id = a_actor->GetFormID();
		auto [it, added] = watched.try_emplace(id);
		auto& w = it->second;
		w.seen = true;
		w.unseen = 0.0f;
		const bool dead = a_actor->IsDead();
		if (added) {
			w.dead = dead;  // first seen dead: it died before we watched it
		}
		if (!dead) {
			if (w.dead) {
				w.dead = false;  // alive again: raised, or a spawned creature's form id given to a new one
				w.look = 0.0f;
			}
			w.look -= a_delta;
			if (w.look <= 0.0f) {
				Look(a_actor, w);
				// Spread the looks over a few frames rather than every actor at once.
				w.look = (a_actor->IsInCombat() ? kWornLookFightSeconds : kWornLookSeconds) + static_cast<float>(id & 7) * 0.02f;
			}
			return;
		}
		if (!w.dead) {
			w.dead = true;
			SendGear(a_actor, w);
			w.gear.clear();
		}
	}

	void SendWorn(float a_delta)
	{
		for (auto it = watched.begin(); it != watched.end();) {
			auto& w = it->second;
			if (!w.seen) {
				w.unseen += a_delta;
				if (w.unseen > kWornForgetSeconds) {
					it = watched.erase(it);
					continue;
				}
			}
			w.seen = false;
			++it;
		}
		auto& link = Link::Get();
		for (std::size_t n = 0; n < kWornPerFrame && !wornQueue.empty(); ++n) {
			link.PushItem(wornQueue.front());
			wornQueue.pop_front();
		}
	}

	void ForgetWorn()
	{
		watched.clear();
		wornQueue.clear();
	}
}
