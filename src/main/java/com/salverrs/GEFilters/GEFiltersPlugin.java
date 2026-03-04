package com.salverrs.GEFilters;

import com.google.inject.Provides;
import javax.inject.Inject;

import com.salverrs.GEFilters.Filters.BankHighlighterSearchFilter;
import com.salverrs.GEFilters.Filters.BankTabSearchFilter;
import com.salverrs.GEFilters.Filters.InventorySearchFilter;
import com.salverrs.GEFilters.Filters.InventorySetupsSearchFilter;
import com.salverrs.GEFilters.Filters.PinnedItemsSearchFilter;
import com.salverrs.GEFilters.Filters.RecentItemsSearchFilter;
import com.salverrs.GEFilters.Filters.SearchFilter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;


@Slf4j
@PluginDescriptor(
	name = "GE Filters",
	description = "Provides advanced search filters for the Grand Exchange, allowing users to sort and organize items efficiently for market flipping, bank setups, and more.",
	tags = {"ge","filter","grand","exchange","search","bank","tag","inventory","setups","sort","market","flipping","equipment","items","tool","qol","utility"},
	enabledByDefault = true
)
@PluginDependency(BankTagsPlugin.class)
public class GEFiltersPlugin extends Plugin
{
	public static final String CONFIG_GROUP = "GE_FILTERS_CONFIG";
	public static final String CONFIG_GROUP_DATA = "GE_FILTERS_CONFIG_DATA";
	private static final String CONFIG_KEY_CLEAR_RECENTLY_VIEWED = "clearRecentlyViewedList";
	private static final String CONFIG_KEY_CLEAR_PINNED_ITEMS = "clearPinnedItemsList";
	public static final String BANK_TAGS_COMP_NAME = "Bank Tags";
	public static final String BANK_HIGHLIGHTER_COMP_NAME = "Bank Highlighter";
	private static final String SEARCH_BUY_PREFIX_TEXT = "What would you like to buy?";
	private static final Pattern SEARCH_BUY_PREFIX_PATTERN = Pattern.compile("^What would you like to buy\\?\\s*\\*?\\s*");
	private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
	public static final String INVENTORY_SETUPS_COMP_NAME = "Inventory Setups";
	private static final int WIDGET_ID_CHATBOX_GE_SEARCH_RESULTS = InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS;
	private static final int WIDGET_ID_CHATBOX_CONTAINER = InterfaceID.Chatbox.MES_LAYER;
	private static final int SEARCH_BOX_LOADED_ID = ScriptID.GE_ITEM_SEARCH;

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ConfigManager configManager;
	@Inject
	private GEFiltersConfig config;
	@Inject
	private EventBus eventBus;
	@Inject
	private BankTabSearchFilter bankTabSearchFilter;
	@Inject
	private BankHighlighterSearchFilter bankHighlighterSearchFilter;
	@Inject
	private InventorySetupsSearchFilter inventorySetupsSearchFilter;
	@Inject
	private RecentItemsSearchFilter recentItemsSearchFilter;
	@Inject
	private PinnedItemsSearchFilter pinnedItemsSearchFilter;
	@Inject
	private InventorySearchFilter inventorySearchFilter;
	@Inject
	private PluginManager pluginManager;

	private List<SearchFilter> filters;
	/**
	 * Guard against starting filters multiple times while the GE chatbox search is already open.
	 * Multiple starts can create duplicate widgets where the visible widget is no longer the one
	 * referenced by the SearchFilter instance, making clicks appear to do nothing.
	 */
	private boolean filtersRunning;
	private boolean pendingAutoSelectOnBuy;
	private boolean autoSelectAppliedThisSearch;
	/**
	 * RuneLite/OSRS has started re-using the GE search chatbox interface in other contexts
	 * (e.g. the sailing mermaid riddle/puzzle). We only want to show GE Filters when the
	 * actual Grand Exchange offers interface is open.
	 */
	private boolean grandExchangeInterfaceOpen;

	@Override
	protected void startUp() throws Exception
	{
		log.info("GE Filters started!");
		clientThread.invoke(() ->
		{
			loadFilters();
			tryStartFilters();
		});
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("GE Filters stopped!");
		clientThread.invoke(this::stopFilters);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		// Chatbox message layer closed (eg. item selected or input cancelled). If we keep widgets around
		// they can become detached/stale and won't reappear until the GE is re-opened.
		if (event.getScriptId() == ScriptID.MESSAGE_LAYER_CLOSE)
		{
			if (filtersRunning)
			{
				clientThread.invoke(this::hideFilters);
			}
			autoSelectAppliedThisSearch = false;
			return;
		}

		// The GE chatbox search is sometimes rebuilt without re-running the full GE_ITEM_SEARCH script.
		// Ensure filters start whenever the message-layer input is rebuilt as well.
		if (event.getScriptId() == SEARCH_BOX_LOADED_ID || event.getScriptId() == ScriptID.CHAT_TEXT_INPUT_REBUILD)
		{
			clientThread.invoke(() ->
			{
				final boolean buySearchPromptVisible = isBuySearchPromptVisible();
				if (buySearchPromptVisible && !autoSelectAppliedThisSearch)
				{
					pendingAutoSelectOnBuy = true;
				}

				tryStartFilters();
				hideSearchPrefixIfPresent();
				if (buySearchPromptVisible && !autoSelectAppliedThisSearch)
				{
					autoSelectConfiguredFilterOnBuy();
					autoSelectAppliedThisSearch = true;
					pendingAutoSelectOnBuy = false;
				}
			});
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// When returning to login screen, any open GE state is invalid.
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			grandExchangeInterfaceOpen = false;
			clientThread.invoke(this::hideFilters);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.GE_OFFERS)
		{
			grandExchangeInterfaceOpen = true;
			autoSelectAppliedThisSearch = false;
			clientThread.invoke(this::tryStartFilters);
			clientThread.invokeLater(this::hideSearchPrefixIfPresent);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.GE_OFFERS)
		{
			grandExchangeInterfaceOpen = false;
			autoSelectAppliedThisSearch = false;
			clientThread.invoke(this::hideFilters);
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (filtersRunning)
		{
			hideSearchPrefixIfPresent();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (!GEFiltersPlugin.CONFIG_GROUP.equals(configChanged.getGroup()))
			return;

		if (CONFIG_KEY_CLEAR_RECENTLY_VIEWED.equals(configChanged.getKey()))
		{
			if (config.clearRecentlyViewedList())
			{
				clientThread.invoke(() ->
				{
					recentItemsSearchFilter.clearRecentlyViewedItems();
					configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_CLEAR_RECENTLY_VIEWED, false);
				});
			}

			return;
		}

		if (CONFIG_KEY_CLEAR_PINNED_ITEMS.equals(configChanged.getKey()))
		{
			if (config.clearPinnedItemsList())
			{
				clientThread.invoke(() ->
				{
					recentItemsSearchFilter.clearPinnedItems();
					configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_CLEAR_PINNED_ITEMS, false);
				});
			}

			return;
		}

		clientThread.invoke(() ->
		{
			stopFilters();
			loadFilters();
			tryStartFilters();
		});
	}

	private void loadFilters()
	{
		filters = new ArrayList<>();

		if (config.enableBankTagFilter() && isPluginEnabled(BANK_TAGS_COMP_NAME))
		{
			filters.add(bankTabSearchFilter);
		}

		if (config.enableInventorySetupsFilter() && isPluginEnabled(INVENTORY_SETUPS_COMP_NAME))
		{
			filters.add(inventorySetupsSearchFilter);
		}

		if (config.enableInventoryFilter())
		{
			filters.add(inventorySearchFilter);
		}

		if (config.enableBankHighlighterFilter() && isPluginEnabled(BANK_HIGHLIGHTER_COMP_NAME))
		{
			filters.add(bankHighlighterSearchFilter);
		}

		if (config.enableRecentItemsFilter())
		{
			filters.add(recentItemsSearchFilter);
		}

		if (config.enablePinnedItemsFilter())
		{
			filters.add(pinnedItemsSearchFilter);
		}

		registerFilterEvents();
	}

	private void tryStartFilters()
	{
		// If the plugin is enabled while the GE is already open we may not receive WidgetLoaded.
		// Infer state from the presence of the GE root widget.
		if (!grandExchangeInterfaceOpen && client.getWidget(InterfaceID.GE_OFFERS, 0) != null)
		{
			grandExchangeInterfaceOpen = true;
		}

		// The GE search chatbox can be reused by other interfaces.
		// Only initialize filters while the GE offers interface is open.
		if (!grandExchangeInterfaceOpen)
		{
			return;
		}

		// If something went wrong with teardown (missed close event), don't get stuck forever.
		if (filtersRunning && !isSearchVisible())
		{
			hideFilters();
			return;
		}

		if (filtersRunning)
		{
			return;
		}

		if (isSearchVisible())
		{
			startFilters();
		}
	}

	private void hideSearchPrefixIfPresent()
	{
		if (!config.hideSearchPrefix())
		{
			return;
		}

		// Only touch text when GE search UI is visible.
		if (!isSearchVisible())
		{
			return;
		}

		// Use the documented API rather than hardcoded group/child ids.
		Widget input = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
		if (input == null)
		{
			input = client.getFocusedInputFieldWidget();
		}

		if (input == null)
		{
			return;
		}

		final String text = input.getText();
		final String normalized = normalizeChatboxText(text);
		final String stripped = SEARCH_BUY_PREFIX_PATTERN.matcher(normalized).replaceFirst("");
		if (!stripped.equals(normalized))
		{
			input.setText(stripped);
			input.revalidate();
		}
	}

	private boolean isBuySearchPromptVisible()
	{
		if (!isSearchVisible())
		{
			return false;
		}

		Widget input = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
		if (input == null)
		{
			input = client.getFocusedInputFieldWidget();
		}

		if (input == null)
		{
			return false;
		}

		final String text = input.getText();
		final String normalized = normalizeChatboxText(text);
		return normalized.startsWith(SEARCH_BUY_PREFIX_TEXT);
	}

	private String normalizeChatboxText(String text)
	{
		if (text == null || text.isEmpty())
		{
			return "";
		}

		return HTML_TAG_PATTERN.matcher(text).replaceAll("").trim();
	}

	private void autoSelectConfiguredFilterOnBuy()
	{
		final boolean inventorySetupsAvailable = config.enableInventorySetupsFilter()
				&& isPluginEnabled(INVENTORY_SETUPS_COMP_NAME);

		// User-requested precedence: if both global auto-select and Inventory Setups auto-enter are enabled,
		// Inventory Setups should win.
		if (inventorySetupsAvailable
				&& config.invSetupsAutoSelectActiveSetup()
				&& config.autoSelectFilterOnBuy() != GEFiltersConfig.AutoSelectFilterOnBuyMode.OFF)
		{
			inventorySetupsSearchFilter.autoEnablePrimaryFilterOption();
			return;
		}

		switch (config.autoSelectFilterOnBuy())
		{
			case INVENTORY:
				inventorySearchFilter.autoEnablePrimaryFilterOption();
				break;
			case RECENT_ITEMS:
				recentItemsSearchFilter.autoEnablePrimaryFilterOption();
				break;
			case PINNED_ITEMS:
				pinnedItemsSearchFilter.autoEnablePrimaryFilterOption();
				break;
			case BANK_TAGS:
				bankTabSearchFilter.autoEnablePrimaryFilterOption();
				break;
			case INVENTORY_SETUPS:
				inventorySetupsSearchFilter.autoEnablePrimaryFilterOption();
				break;
			case OFF:
			default:
				break;
		}
	}

	private void startFilters()
	{
		if (filters == null || filters.isEmpty())
		{
			filtersRunning = false;
			pendingAutoSelectOnBuy = false;
			autoSelectAppliedThisSearch = false;
			return;
		}

		filtersRunning = true;
		final int buttonWidth = SearchFilter.getConfiguredButtonWidth(config);
		final int horizontalSpacing = buttonWidth + config.filterHorizontalSpacing();
		final boolean bothSides = config.filterButtonsBothSides();
		final int filterCount = filters.size();
		final int leftCount = bothSides ? (filterCount + 1) / 2 : filterCount;

		int containerWidth = 0;
		if (bothSides)
		{
			final Widget chatboxContainer = client.getWidget(WIDGET_ID_CHATBOX_CONTAINER);
			containerWidth = chatboxContainer != null ? chatboxContainer.getWidth() : 0;
			if (containerWidth <= 0)
			{
				containerWidth = (horizontalSpacing * Math.max(filterCount, 4)) + buttonWidth;
			}
		}

		for (int i = 0; i < filterCount; i++)
		{
			final int xOffset;
			if (!bothSides)
			{
				xOffset = i * horizontalSpacing;
			}
			else if (i < leftCount)
			{
				xOffset = i * horizontalSpacing;
			}
			else
			{
				final int rightIndex = i - leftCount;
				xOffset = Math.max(0, containerWidth - buttonWidth - (rightIndex * horizontalSpacing));
			}

			final SearchFilter filter = filters.get(i);
			if (filter != null)
			{
				filter.start(xOffset, 0);
			}
		}

		if (pendingAutoSelectOnBuy)
		{
			autoSelectConfiguredFilterOnBuy();
			autoSelectAppliedThisSearch = true;
			pendingAutoSelectOnBuy = false;
		}
	}

	private void stopFilters()
	{
		filtersRunning = false;
		pendingAutoSelectOnBuy = false;
		autoSelectAppliedThisSearch = false;
		hideFilters();
		unregisterFilterEvents();
	}

	/**
	 * Hide/stop filter widgets without unregistering event subscribers.
	 *
	 * We keep filters registered for the plugin lifetime, and rely on SearchFilter#ready
	 * to ignore events while GE search is not active.
	 */
	private void hideFilters()
	{
		filtersRunning = false;
		pendingAutoSelectOnBuy = false;
		autoSelectAppliedThisSearch = false;

		if (filters == null)
		{
			return;
		}

		for (SearchFilter filter : filters)
		{
			if (filter != null)
			{
				filter.stop();
			}
		}
	}

	private void registerFilterEvents()
	{
		if (filters == null || filters.isEmpty())
		{
			return;
		}

		for (SearchFilter filter : filters)
		{
			if (filter == null)
			{
				continue;
			}

			eventBus.register(filter);
		}
	}

	private void unregisterFilterEvents()
	{
		if (filters == null || filters.isEmpty())
		{
			return;
		}

		for (SearchFilter filter : filters)
		{
			if (filter == null)
			{
				continue;
			}

			eventBus.unregister(filter);
		}
	}

	private boolean isPluginEnabled(String pluginName)
	{
		final Collection<Plugin> plugins = pluginManager.getPlugins();
		for (Plugin plugin : plugins)
		{
			final String name = plugin.getName();
			if (pluginName.equals(name))
			{
				return pluginManager.isPluginEnabled(plugin);
			}
		}

		return false;
	}

	private boolean isSearchVisible()
	{
		if (!grandExchangeInterfaceOpen)
		{
			return false;
		}

		// Avoid starting filters before the underlying chatbox container is actually available.
		// If we start too early, SearchFilter#start will no-op and filtersRunning may get stuck.
		final Widget container = client.getWidget(WIDGET_ID_CHATBOX_CONTAINER);
		if (container == null || container.isHidden())
		{
			return false;
		}

		final Widget widget = client.getWidget(WIDGET_ID_CHATBOX_GE_SEARCH_RESULTS);
		// The scroll contents can be hidden (e.g. "previous search" view) while the GE search interface
		// is still active. We only care that the widget exists.
		return widget != null;
	}

	@Provides
	GEFiltersConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GEFiltersConfig.class);
	}
}
