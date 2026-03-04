package com.salverrs.GEFilters;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

import java.awt.Color;

@ConfigGroup(GEFiltersPlugin.CONFIG_GROUP)
public interface GEFiltersConfig extends Config
{
	@ConfigSection(
			name = "Filters",
			description = "Grand Exchange search filters.",
			position = 0
	)
	String filtersSection = "filters";

	@ConfigSection(
			name = "Inventory Setups",
			description = "Toggle item types from Inventory Setups that will be included in the filter.",
			position = 2
	)
	String inventorySetupsSection = "invsetupsfilter";

	@ConfigSection(
			name = "Preferences",
			description = "Grand Exchange search filter preferences.",
			position = 3
	)
	String preferencesSection = "preferences";

	@ConfigItem(
		keyName = "enableBankTagFilter",
		name = "Enable Bank Tag Filter",
		description = "Filters GE items by bank tag. Requires the Bank Tag plugin to be enabled.",
		section = filtersSection,
		position = 0
	)
	default boolean enableBankTagFilter()
	{
		return true;
	}


	@ConfigItem(
			keyName = "enableInventorySetupsFilter",
			name = "Enable Inventory Setups Filter",
			description = "Filters GE items by inventory setups. Requires the Inventory Setups plugin to be enabled.",
			section = filtersSection,
			position = 1
	)
	default boolean enableInventorySetupsFilter()
	{
		return true;
	}

	@ConfigItem(
			keyName = "enableInventoryFilter",
			name = "Enable Inventory Filter",
			description = "Filters GE items by inventory/equipped items.",
			section = filtersSection,
			position = 2
	)
	default boolean enableInventoryFilter()
	{
		return true;
	}

	@ConfigItem(
			keyName = "enableRecentItemsFilter",
			name = "Enable Recent Items Filter",
			description = "Filters GE items by recently viewed or recent buy/sell offers.",
			section = filtersSection,
			position = 3
	)
	default boolean enableRecentItemsFilter()
	{
		return true;
	}

	@ConfigItem(
			keyName = "enablePinnedItemsFilter",
			name = "Enable Pinned Items Filter",
			description = "Show a standalone Pinned Items filter button/tab.",
			section = filtersSection,
			position = 4
	)
	default boolean enablePinnedItemsFilter()
	{
		return true;
	}

	@ConfigItem(
			keyName = "enableBankHighlighterFilter",
			name = "Enable Bank Highlighter Filter",
			description = "Filters GE items by items tagged in the Bank Highlighter plugin.",
			section = filtersSection,
			position = 5
	)
	default boolean enableBankHighlighterFilter()
	{
		return true;
	}


	@ConfigItem(
			keyName = "enableInvSetupsEquipment",
			name = "Equipment",
			description = "Show equipment items in the Inventory Setups filter.",
			section = inventorySetupsSection,
			position = 4
	)
	default boolean enableInvSetupsEquipment() { return true; }

	@ConfigItem(
			keyName = "invSetupsAutoSelectActiveSetup",
			name = "Auto-select active setup",
			description = "When enabled, GE Filters will automatically use the currently selected Inventory Setup (in the Inventory Setups plugin) instead of showing the setup list.",
			section = inventorySetupsSection,
			position = 3
	)
	default boolean invSetupsAutoSelectActiveSetup(){ return false; }

	@ConfigItem(
		position = 8,
		keyName = "autoSelectFilterOnBuy",
		name = "Auto-select filter on buy",
		description = "Automatically enables one GE Filters tab when opening GE item search for buying."
	)
	default AutoSelectFilterOnBuyMode autoSelectFilterOnBuy()
	{
		return AutoSelectFilterOnBuyMode.OFF;
	}

	@ConfigItem(
		position = 9,
		keyName = "filterButtonsBothSides",
		name = "Wider buttons on both sides",
		description = "Use wider labeled filter buttons and place them on both sides of the GE search box."
	)
	default boolean filterButtonsBothSides()
	{
		return false;
	}

	enum AutoSelectFilterOnBuyMode
	{
		OFF,
		INVENTORY,
		RECENT_ITEMS,
		PINNED_ITEMS,
		BANK_TAGS,
		INVENTORY_SETUPS
	}

	@ConfigItem(
			keyName = "enableInvSetupsInventory",
			name = "Inventory",
			description = "Show inventory items in the Inventory Setups filter.",
			section = inventorySetupsSection,
			position = 5
	)
	default boolean enableInvSetupsInventory() { return true; }

	@ConfigItem(
			keyName = "enableInvSetupsRunePouch",
			name = "Rune Pouch",
			description = "Show Rune pouch runes in the Inventory Setups filter.",
			section = inventorySetupsSection,
			position = 6
	)
	default boolean enableInvSetupsRunePouch() { return true; }

	@ConfigItem(
			keyName = "enableInvSetupsBoltPouch",
			name = "Bolt Pouch",
			description = "Show Bolt pouch bolts in the Inventory Setups filter.",
			section = inventorySetupsSection,
			position = 7
	)
	default boolean enableInvSetupsBoltPouch() { return true; }

	@ConfigItem(
			keyName = "enableInvSetupsQuiver",
			name = "Quiver",
			description = "Show Quiver ammo in the Inventory Setups filter.",
			section = inventorySetupsSection,
			position = 8
	)
	default boolean enableInvSetupsQuiver() { return true; }

	@ConfigItem(
			keyName = "enableInvSetupsAdditionalItems",
			name = "Additional Filtered Items",
			description = "Include Inventory Setups \"Additional Filtered Items\" entries (items listed outside inventory/equipment/rune pouch/bolt pouch/quiver sections).",
			section = inventorySetupsSection,
			position = 9
	)
	default boolean enableInvSetupsAdditionalItems() { return true; }

	@ConfigItem(
			keyName = "filterTitleColour",
			name = "Filter Title Colour",
			description = "The text colour for filter titles.",
			section = preferencesSection,
			position = 10
	)
	default Color filterTitleColour()
	{
		return new Color(178, 0, 0);
	}


	@ConfigItem(
			keyName = "keyPressOverridesFilter",
			name = "Typing Overrides Active Filter",
			description = "When enabled typing will override the currently active filter and perform a regular search.",
			section = preferencesSection,
			position = 11
	)
	default boolean keyPressOverridesFilter()
	{
		return true;
	}

	@ConfigItem(
			keyName = "hideSearchPrefix",
			name = "Hide Default Search Prefix",
			description = "Hide 'What would you like to buy?' from GE searches.",
			section = preferencesSection,
			position = 12
	)
	default boolean hideSearchPrefix()
	{
		return true;
	}

	@Range(
			max = 20,
			min = 0
	)
	@ConfigItem(
			keyName = "filterHorizontalSpacing",
			name = "Horizontal Spacing",
			description = "The horizontal space between filter buttons (px).",
			section = preferencesSection,
			position = 13
	)
	default int filterHorizontalSpacing()
	{
		return 5;
	}

	@ConfigItem(
			keyName = "clearRecentlyViewedList",
			name = "Clear Recently Viewed (Popup)",
			description = "Opens a confirmation popup, then clears Recently Viewed and resets this toggle. Max list cap is 500.",
			warning = "Clear Recently Viewed now? This is immediate and cannot be undone.",
			section = preferencesSection,
			position = 14
	)
	default boolean clearRecentlyViewedList()
	{
		return false;
	}

	@Range(
			max = 500,
			min = 1
	)
	@ConfigItem(
			keyName = "maxRecentlyViewedItems",
			name = "Max Recently Viewed Items",
			description = "Maximum number of items kept in Recently Viewed (1-500).",
			section = preferencesSection,
			position = 15
	)
	default int maxRecentlyViewedItems()
	{
		return 100;
	}

	@ConfigItem(
			keyName = "recentlyViewedIgnoredItemIds",
			name = "Ignored Recently Viewed Item IDs",
			description = "Comma, space, or semicolon-separated item IDs to ignore when adding to Recently Viewed. Combined with ignored names.",
			section = preferencesSection,
			position = 16
	)
	default String recentlyViewedIgnoredItemIds()
	{
		return "";
	}

	@ConfigItem(
			keyName = "recentlyViewedIgnoredItemNames",
			name = "Ignored Recently Viewed Item Names",
			description = "Comma, semicolon, or newline-separated item names to ignore when adding to Recently Viewed. Combined with ignored IDs.",
			section = preferencesSection,
			position = 17
	)
	default String recentlyViewedIgnoredItemNames()
	{
		return "";
	}

	@ConfigItem(
			keyName = "clearPinnedItemsList",
			name = "Clear Pinned Items (Popup)",
			description = "Opens a confirmation popup, then clears Pinned Items and resets this toggle.",
			warning = "Clear Pinned Items now? This is immediate and cannot be undone.",
			section = preferencesSection,
			position = 19
	)
	default boolean clearPinnedItemsList()
	{
		return false;
	}

	@Range(
			max = 500,
			min = 1
	)
	@ConfigItem(
			keyName = "maxPinnedItems",
			name = "Max Pinned Items",
			description = "Maximum number of items kept in Pinned Items (1-500).",
			section = preferencesSection,
			position = 18
	)
	default int maxPinnedItems()
	{
		return 100;
	}


}
