package com.salverrs.GEFilters;

import net.runelite.client.config.*;

import java.awt.*;

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
			keyName = "enableInvSetupsEquipment",
			name = "Equipment",
			description = "Show equipment items in the Inventory Setups filter.",
			section = inventorySetupsSection,
			position = 4
	)
	default boolean enableInvSetupsEquipment() { return true; }

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
			keyName = "enableInvSetupsAdditionalItems",
			name = "Additional Filtered Items",
			description = "Show additional filtered items the Inventory Setups filter.",
			section = inventorySetupsSection,
			position = 8
	)
	default boolean enableInvSetupsAdditionalItems() { return true; }

	@ConfigItem(
			keyName = "filterTitleColour",
			name = "Filter Title Colour",
			description = "The text colour for filter titles.",
			section = preferencesSection,
			position = 9
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
			position = 10
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
			position = 11
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
			position = 12
	)
	default int filterHorizontalSpacing()
	{
		return 5;
	}


}
