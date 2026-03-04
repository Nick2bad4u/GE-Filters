package com.salverrs.GEFilters.Filters;

import com.salverrs.GEFilters.Filters.Model.FilterOption;
import com.salverrs.GEFilters.GEFiltersConfig;
import com.salverrs.GEFilters.GEFiltersPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static net.runelite.http.api.RuneLiteAPI.GSON;

@Slf4j
@Singleton
public class RecentItemsSearchFilter extends SearchFilter {
    private static final int MAX_OFFER_HISTORY_COUNT = 100;
    private static final int VARP_CURRENT_GE_ITEM = 1151;
    private static final int MAX_STORED_ITEM_ID = 0xFFFF;
    private static final String ITEM_ID_SPLIT_REGEX = "[,;\\s]+";
    private static final String ITEM_NAME_SPLIT_REGEX = "[,;\\r\\n]+";
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern MULTI_WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final int WIDGET_ID_CHATBOX_GE_SEARCH_RESULTS = InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS;
    private static final int SPRITE_ID_MAIN = 1367;
    private static final String RECENT_ITEMS_JSON_KEY = "ge-recent-items";
    private static final String PINNED_ITEMS_JSON_KEY = "ge-pinned-items";
    private static final String RECENT_BUY_OFFERS_JSON_KEY = "ge-recent-buy-offers";
    private static final String RECENT_SELL_OFFERS_JSON_KEY = "ge-recent-sell-offers";
    private static final String TITLE_RECENTLY_VIEWED = "Recently Viewed";
    private static final String TITLE_RECENT_BUY_OFFERS = "Recent Buy Offers";
    private static final String TITLE_RECENT_SELL_OFFERS = "Recent Sell Offers";
    private static final String SEARCH_BASE_RECENTLY_VIEWED = "recently-viewed-items";
    public static final String SEARCH_BASE_PINNED_ITEMS = "pinned-items";
    private static final String SEARCH_BASE_RECENT_BUY_OFFERS = "recent-buy-offers";
    private static final String SEARCH_BASE_RECENT_SELL_OFFERS = "recent-sell-offers";
    private static final String MENU_OPTION_REMOVE_RECENT = "Remove from Recently Viewed";
    private static final String MENU_OPTION_PIN = "Pin for Quick Access";
    private static final String MENU_OPTION_UNPIN = "Unpin from Quick Access";
    private static final String MENU_OPTION_SELECT_NORMALIZED = "select";
    private static final String MENU_OPTION_PIN_NORMALIZED = "pin for quick access";
    private static final String MENU_OPTION_UNPIN_NORMALIZED = "unpin from quick access";

    @Inject
    private GEFiltersConfig config;
    @Inject
    private ItemManager itemManager;

    private FilterOption recentlyViewed, recentBuyOffers, recentSellOffers;
    private ArrayList<Short> recentItemIds, pinnedItemIds, recentBuyOffersItemIds, recentSellOffersItemIds;
    private ArrayList<Short> displayedRecentlyViewedItemIds = new ArrayList<>();
    private String cachedIgnoredItemsRaw;
    private Set<Short> cachedIgnoredItemIds = new HashSet<>();
    private String cachedIgnoredNamesRaw;
    private Set<String> cachedIgnoredItemNames = new HashSet<>();
    private final Map<Integer, String> normalizedItemNameCache = new HashMap<>();
    private int suppressedCurrentRecentlyViewedItemId = -1;

    private void ensureRecentListsLoaded()
    {
        if (recentItemIds == null)
        {
            loadRecentItems();
        }

        if (recentBuyOffersItemIds == null)
        {
            loadRecentBuyOfferItems();
        }

        if (pinnedItemIds == null)
        {
            loadPinnedItems();
        }

        if (recentSellOffersItemIds == null)
        {
            loadRecentSellOfferItems();
        }
    }

    private boolean isGrandExchangeOpen()
    {
        return client.getWidget(InterfaceID.GE_OFFERS, 0) != null;
    }

    @Override
    protected void onFilterInitialising()
    {
        ensureRecentListsLoaded();

        recentlyViewed = new FilterOption(TITLE_RECENTLY_VIEWED, SEARCH_BASE_RECENTLY_VIEWED);
        recentBuyOffers = new FilterOption(TITLE_RECENT_BUY_OFFERS, SEARCH_BASE_RECENT_BUY_OFFERS);
        recentSellOffers = new FilterOption(TITLE_RECENT_SELL_OFFERS, SEARCH_BASE_RECENT_SELL_OFFERS);

        setFilterOptions(recentlyViewed, recentBuyOffers, recentSellOffers);
        setIconSprite(SPRITE_ID_MAIN, 0);
    }

    @Override
    protected void onFilterStarted()
    {
        ensureRecentListsLoaded();
        enforceRecentViewedConstraints();
        enforcePinnedConstraints();
    }

    @Override
    protected void onFilterEnabled(FilterOption option)
    {
        if (option == recentlyViewed)
        {
            final ArrayList<Short> displayed = getRecentlyViewedItemsWithCurrent();
            displayedRecentlyViewedItemIds = new ArrayList<>(displayed);
            addItemFilterResults(displayed);
        }
        else if (option == recentBuyOffers)
        {
            addItemFilterResults(recentBuyOffersItemIds);
        }
        else if (option == recentSellOffers)
        {
            addItemFilterResults(recentSellOffersItemIds);
        }
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged newOfferEvent)
    {
        ensureRecentListsLoaded();

        final GrandExchangeOffer offer = newOfferEvent.getOffer();
        final GrandExchangeOfferState offerState = offer.getState();
        final int itemId = offer.getItemId();
        if (itemId <= 0 || itemId > MAX_STORED_ITEM_ID)
        {
            return;
        }

        if (offerState == GrandExchangeOfferState.BUYING)
        {
            appendToIdList(recentBuyOffersItemIds, (short)itemId, MAX_OFFER_HISTORY_COUNT);
            saveRecentBuyOfferItems();
        }
        else if (offerState == GrandExchangeOfferState.SELLING)
        {
            appendToIdList(recentSellOffersItemIds, (short)itemId, MAX_OFFER_HISTORY_COUNT);
            saveRecentSellOfferItems();
        }
    }

    @Subscribe
    protected void onVarbitChanged(VarbitChanged event)
    {
        if (event.getVarpId() != VARP_CURRENT_GE_ITEM)
            return;

        if (!isGrandExchangeOpen())
            return;

        ensureRecentListsLoaded();

        // GE filter result generation can mutate CURRENT_GE_ITEM while custom filter views are open,
        // which pollutes recent-viewed history with synthetic/filter items.
        if (isGeSearchResultsOpen() && SearchFilter.isAnyFilterEnabled())
            return;

        final int recentId = client.getVarpValue(VARP_CURRENT_GE_ITEM);

        addRecentViewedItem(recentId);
    }

    @Subscribe
    protected void onMenuOptionClicked(MenuOptionClicked event)
    {
        super.onMenuOptionClicked(event);

        final String menuOption = event.getMenuOption();
        if (menuOption == null)
            return;

        final String normalizedOption = normalizeMenuOption(menuOption);
        if (MENU_OPTION_PIN_NORMALIZED.equals(normalizedOption) || MENU_OPTION_UNPIN_NORMALIZED.equals(normalizedOption))
        {
            // Pin/unpin is handled by explicit RUNELITE menu-entry callbacks added in onMenuOpened.
            // Avoid handling it here as well, since MenuOptionClicked can expose non-item widget ids.
            return;
        }

        // GE menu options may include color tags / slight variants. We only care about select actions.
        if (!normalizedOption.startsWith(MENU_OPTION_SELECT_NORMALIZED))
            return;

        // Updates must continue even if SearchFilter widgets were just torn down.
        // Restrict to GE context to avoid cross-interface pollution.
        if (!isGrandExchangeOpen())
            return;

        // Only consume/select tracking from the GE search results context.
        if (!isGeSearchResultsOpen())
            return;

        ensureRecentListsLoaded();

        int itemId = resolveEventItemId(event);
        if (itemId <= 0)
        {
            itemId = client.getVarpValue(VARP_CURRENT_GE_ITEM);
        }

        addRecentViewedItem(itemId);
    }

    @Subscribe
    protected void onMenuOpened(MenuOpened event)
    {
        ensureRecentListsLoaded();

        if (!isGrandExchangeOpen() || !isGeSearchResultsOpen())
        {
            return;
        }

        final String currentSearch = client.getVarcStrValue(VarClientID.MESLAYERINPUT);
        final boolean onRecentlyViewed = SEARCH_BASE_RECENTLY_VIEWED.equals(currentSearch);
        final boolean onPinnedItems = SEARCH_BASE_PINNED_ITEMS.equals(currentSearch);
        final boolean pinnedActionsEnabled = config.enablePinnedItemsFilter();

        // Keep manual removal available in Recently Viewed even if pinned actions are disabled.
        if (!pinnedActionsEnabled && !onRecentlyViewed)
        {
            return;
        }

        final MenuEntry[] entries = event.getMenuEntries();
        if (entries == null || entries.length == 0)
        {
            return;
        }

        final Set<Integer> seenItemIds = new HashSet<>();
        for (int idx = entries.length - 1; idx >= 0; --idx)
        {
            final MenuEntry entry = entries[idx];
            final String option = entry.getOption();
            if (option == null)
            {
                continue;
            }

            if (entry.getType() == MenuAction.RUNELITE)
            {
                continue;
            }

            final String normalizedOption = normalizeMenuOption(option);
            if (!normalizedOption.startsWith(MENU_OPTION_SELECT_NORMALIZED))
            {
                continue;
            }

            final int rowIndex = entry.getParam0();

            int itemId = -1;
            String resolvedFrom = "none";
            if (onRecentlyViewed)
            {
                itemId = resolveItemIdFromDisplayedResultsByTarget(displayedRecentlyViewedItemIds, entry.getTarget());
                if (itemId > 0)
                {
                    resolvedFrom = "recent-target";
                }

                if (itemId <= 0)
                {
                    itemId = resolveItemIdFromDisplayedResults(displayedRecentlyViewedItemIds, rowIndex);
                }
                if (itemId > 0)
                {
                    resolvedFrom = "none".equals(resolvedFrom) ? "recent-row" : resolvedFrom;
                }
            }
            else if (onPinnedItems)
            {
                final ArrayList<Short> pinnedSnapshot = getPinnedItemsSnapshot();
                itemId = resolveItemIdFromDisplayedResultsByTarget(pinnedSnapshot, entry.getTarget());
                if (itemId > 0)
                {
                    resolvedFrom = "pinned-target";
                }

                if (itemId <= 0)
                {
                    itemId = resolveItemIdFromDisplayedResults(pinnedSnapshot, rowIndex);
                }
                if (itemId > 0)
                {
                    resolvedFrom = "none".equals(resolvedFrom) ? "pinned-row" : resolvedFrom;
                }
            }

            if (itemId <= 0)
            {
                itemId = resolveMenuEntryItemId(entry);
                if (itemId > 0)
                {
                    resolvedFrom = "entry-item";
                }
            }

            if (itemId <= 0)
            {
                itemId = resolveActiveFilterItemId(entry.getTarget(), rowIndex);
                if (itemId > 0)
                {
                    resolvedFrom = "active-filter";
                }
            }

            if (itemId <= 0)
            {
                itemId = resolveItemIdFromTargetLookup(entry.getTarget());
                if (itemId > 0)
                {
                    resolvedFrom = "target-lookup";
                }
            }

            if (itemId <= 0)
            {
                continue;
            }

            if (itemId > MAX_STORED_ITEM_ID)
            {
                continue;
            }

            if (!seenItemIds.add(itemId))
            {
                continue;
            }

            final int resolvedItemId = itemId;

            final short shortId = (short) resolvedItemId;
            final boolean isPinned = pinnedItemIds != null && pinnedItemIds.contains(shortId);
            final String action = isPinned ? MENU_OPTION_UNPIN : MENU_OPTION_PIN;

            final Menu menu = client.getMenu();
            if (onRecentlyViewed)
            {
                menu.createMenuEntry(-1)
                    .setOption(MENU_OPTION_REMOVE_RECENT)
                    .setTarget(entry.getTarget())
                    .setType(MenuAction.RUNELITE)
                    .setItemId(resolvedItemId)
                    .onClick(e ->
                    {
                        removeRecentlyViewedItem(resolvedItemId);
                    });
            }

            if (pinnedActionsEnabled)
            {
                menu.createMenuEntry(-1)
                        .setOption(action)
                        .setTarget(entry.getTarget())
                        .setType(MenuAction.RUNELITE)
                        .setItemId(resolvedItemId)
                        .onClick(e ->
                        {
                            if (isItemPinned(resolvedItemId))
                            {
                                unpinRecentlyViewedItem(resolvedItemId);
                            }
                            else
                            {
                                pinRecentlyViewedItem(resolvedItemId);
                            }
                        });
            }
        }
    }

    private void appendToIdList(List<Short> itemList, short itemId, int maxCount)
    {
        final int existingIndex = itemList.indexOf(itemId);
        if (existingIndex != -1)
        {
            itemList.remove(existingIndex);
        }

        itemList.add(0, itemId);

        trimToMax(itemList, maxCount);
    }

    private boolean trimToMax(List<Short> itemList, int maxCount)
    {
        boolean changed = false;
        while (itemList.size() > maxCount)
        {
            itemList.remove(itemList.size() - 1);
            changed = true;
        }

        return changed;
    }

    private void addRecentViewedItem(int itemId)
    {
        if (itemId <= 0 || itemId > MAX_STORED_ITEM_ID)
            return;

        if (isIgnoredRecentlyViewedItem(itemId))
            return;

        appendToIdList(recentItemIds, (short)itemId, getConfiguredRecentViewedMaxCount());
        if (suppressedCurrentRecentlyViewedItemId == itemId)
        {
            suppressedCurrentRecentlyViewedItemId = -1;
        }
        saveRecentItems();

        refreshRecentlyViewedResultsIfActive();
    }

    private ArrayList<Short> getRecentlyViewedItemsWithCurrent()
    {
        final ArrayList<Short> items = new ArrayList<>(recentItemIds);
        final int currentItemId = client.getVarpValue(VARP_CURRENT_GE_ITEM);
        if (suppressedCurrentRecentlyViewedItemId != -1 && currentItemId != suppressedCurrentRecentlyViewedItemId)
        {
            suppressedCurrentRecentlyViewedItemId = -1;
        }

        if (currentItemId > 0
                && currentItemId <= MAX_STORED_ITEM_ID
                && currentItemId != suppressedCurrentRecentlyViewedItemId
                && !isIgnoredRecentlyViewedItem(currentItemId))
        {
            final short currentId = (short)currentItemId;
            items.remove((Short)currentId);
            items.add(0, currentId);
        }

        trimToMax(items, getConfiguredRecentViewedMaxCount());

        return items;
    }

    private boolean isGeSearchResultsOpen()
    {
        return client.getWidget(WIDGET_ID_CHATBOX_GE_SEARCH_RESULTS) != null;
    }

    private void addItemFilterResults(List<Short> items)
    {
        if (items == null || items.isEmpty())
        {
            setGESearchResults(new short[0]);
            return;
        }

        final short[] itemIds = FilterUtility.getPrimitiveShortArray(items);
        setGESearchResults(itemIds);
    }

    public void clearRecentlyViewedItems()
    {
        ensureRecentListsLoaded();

        if (!recentItemIds.isEmpty())
        {
            recentItemIds.clear();
            saveRecentItems();
        }

        displayedRecentlyViewedItemIds = new ArrayList<>();
        suppressedCurrentRecentlyViewedItemId = -1;

        refreshRecentlyViewedResultsIfActive();
    }

    public void clearPinnedItems()
    {
        ensureRecentListsLoaded();

        if (!pinnedItemIds.isEmpty())
        {
            pinnedItemIds.clear();
            savePinnedItems();
        }

        refreshPinnedResultsIfActive();
    }

    private void pinRecentlyViewedItem(int itemId)
    {
        if (itemId <= 0 || itemId > MAX_STORED_ITEM_ID)
        {
            return;
        }

        ensureRecentListsLoaded();

        appendToIdList(pinnedItemIds, (short) itemId, getConfiguredPinnedMaxCount());
        savePinnedItems();
        refreshPinnedResultsIfActive();
    }

    private void unpinRecentlyViewedItem(int itemId)
    {
        if (itemId <= 0 || itemId > MAX_STORED_ITEM_ID)
        {
            return;
        }

        ensureRecentListsLoaded();

        final boolean removed = pinnedItemIds.remove((Short) (short) itemId);

        if (removed)
        {
            savePinnedItems();
            refreshPinnedResultsIfActive();
        }
    }

    private void removeRecentlyViewedItem(int itemId)
    {
        if (itemId <= 0 || itemId > MAX_STORED_ITEM_ID)
        {
            return;
        }

        ensureRecentListsLoaded();

        final boolean removed = recentItemIds.remove((Short) (short) itemId);
        displayedRecentlyViewedItemIds.remove((Short) (short) itemId);

        if (removed)
        {
            if (client.getVarpValue(VARP_CURRENT_GE_ITEM) == itemId)
            {
                suppressedCurrentRecentlyViewedItemId = itemId;
            }

            saveRecentItems();
            refreshRecentlyViewedResultsIfActive();

            if (SEARCH_BASE_RECENTLY_VIEWED.equals(client.getVarcStrValue(VarClientID.MESLAYERINPUT)))
            {
                forceUpdateSearch(true);
            }
        }
    }

    private void refreshPinnedResultsIfActive()
    {
        if (SEARCH_BASE_PINNED_ITEMS.equals(client.getVarcStrValue(VarClientID.MESLAYERINPUT)))
        {
            addItemFilterResults(pinnedItemIds);
            // Force an immediate redraw so unpin/pin changes are visible without leaving/re-entering the view.
            forceUpdateSearch(true);
        }
    }

    private void refreshRecentlyViewedResultsIfActive()
    {
        // Keep the results live if the user currently has the Recently Viewed filter open.
        if (SEARCH_BASE_RECENTLY_VIEWED.equals(client.getVarcStrValue(VarClientID.MESLAYERINPUT)))
        {
            final ArrayList<Short> displayed = getRecentlyViewedItemsWithCurrent();
            displayedRecentlyViewedItemIds = new ArrayList<>(displayed);
            addItemFilterResults(displayed);
        }
    }

    private int getConfiguredRecentViewedMaxCount()
    {
        return Math.max(1, config.maxRecentlyViewedItems());
    }

    private int getConfiguredPinnedMaxCount()
    {
        return Math.max(1, config.maxPinnedItems());
    }

    private void enforceRecentViewedConstraints()
    {
        if (recentItemIds == null)
        {
            return;
        }

        if (applyRecentViewedConstraints(recentItemIds))
        {
            saveRecentItems();
            refreshRecentlyViewedResultsIfActive();
        }
    }

    private void enforcePinnedConstraints()
    {
        if (pinnedItemIds == null)
        {
            return;
        }

        if (trimToMax(pinnedItemIds, getConfiguredPinnedMaxCount()))
        {
            savePinnedItems();
            refreshPinnedResultsIfActive();
        }
    }

    private boolean applyRecentViewedConstraints(List<Short> itemList)
    {
        boolean changed = false;

        changed = itemList.removeIf(this::isIgnoredRecentlyViewedItem) || changed;

        changed = trimToMax(itemList, getConfiguredRecentViewedMaxCount()) || changed;
        return changed;
    }

    private boolean isIgnoredRecentlyViewedItem(Short itemId)
    {
        if (itemId == null)
        {
            return false;
        }

        return isIgnoredRecentlyViewedItem(Short.toUnsignedInt(itemId));
    }

    private boolean isIgnoredRecentlyViewedItem(int itemId)
    {
        if (itemId <= 0 || itemId > MAX_STORED_ITEM_ID)
        {
            return false;
        }

        final short itemIdShort = (short) itemId;
        if (getIgnoredRecentlyViewedItemIds().contains(itemIdShort))
        {
            return true;
        }

        final Set<String> ignoredItemNames = getIgnoredRecentlyViewedItemNames();
        if (ignoredItemNames.isEmpty())
        {
            return false;
        }

        final String itemName = getNormalizedItemName(itemId);
        return itemName != null && ignoredItemNames.contains(itemName);
    }

    private Set<Short> getIgnoredRecentlyViewedItemIds()
    {
        String rawValue = config.recentlyViewedIgnoredItemIds();
        if (rawValue == null)
        {
            rawValue = "";
        }

        if (rawValue.equals(cachedIgnoredItemsRaw))
        {
            return cachedIgnoredItemIds;
        }

        final HashSet<Short> parsed = new HashSet<>();
        for (String token : rawValue.split(ITEM_ID_SPLIT_REGEX))
        {
            if (token == null || token.isEmpty())
            {
                continue;
            }

            try
            {
                final int parsedId = Integer.parseInt(token.trim());
                if (parsedId > 0 && parsedId <= MAX_STORED_ITEM_ID)
                {
                    parsed.add((short)parsedId);
                }
            }
            catch (NumberFormatException ignored)
            {
                // Ignore invalid tokens so users can edit freely without breaking history updates.
            }
        }

        cachedIgnoredItemsRaw = rawValue;
        cachedIgnoredItemIds = parsed;
        return cachedIgnoredItemIds;
    }

    private Set<String> getIgnoredRecentlyViewedItemNames()
    {
        String rawValue = config.recentlyViewedIgnoredItemNames();
        if (rawValue == null)
        {
            rawValue = "";
        }

        if (rawValue.equals(cachedIgnoredNamesRaw))
        {
            return cachedIgnoredItemNames;
        }

        final HashSet<String> parsed = new HashSet<>();
        for (String token : rawValue.split(ITEM_NAME_SPLIT_REGEX))
        {
            final String normalized = normalizeItemName(token);
            if (!normalized.isEmpty())
            {
                parsed.add(normalized);
            }
        }

        cachedIgnoredNamesRaw = rawValue;
        cachedIgnoredItemNames = parsed;
        return cachedIgnoredItemNames;
    }

    private String getNormalizedItemName(int itemId)
    {
        if (itemId <= 0 || itemId > MAX_STORED_ITEM_ID)
        {
            return null;
        }

        if (normalizedItemNameCache.containsKey(itemId))
        {
            final String cached = normalizedItemNameCache.get(itemId);
            return cached == null || cached.isEmpty() ? null : cached;
        }

        String normalizedName = "";
        try
        {
            final ItemComposition itemComposition = itemManager.getItemComposition(itemId);
            if (itemComposition != null)
            {
                normalizedName = normalizeItemName(itemComposition.getName());
            }
        }
        catch (RuntimeException ignored)
        {
            // Ignore name lookup failures and continue with ID-only ignore behavior.
        }

        normalizedItemNameCache.put(itemId, normalizedName);
        return normalizedName.isEmpty() ? null : normalizedName;
    }

    private String normalizeItemName(String itemName)
    {
        if (itemName == null)
        {
            return "";
        }

        final String normalized = itemName
                .replace('\u00A0', ' ')
                .toLowerCase(Locale.ROOT)
            .trim();

        final String collapsedWhitespace = MULTI_WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ")
                .trim();

        return "null".equals(collapsedWhitespace) ? "" : collapsedWhitespace;
    }

    private void saveRecentItems()
    {
        saveItemIdsToConfig(recentItemIds, RECENT_ITEMS_JSON_KEY);
    }

    private void saveRecentBuyOfferItems()
    {
        saveItemIdsToConfig(recentBuyOffersItemIds, RECENT_BUY_OFFERS_JSON_KEY);
    }

    private void savePinnedItems()
    {
        saveItemIdsToConfig(pinnedItemIds, PINNED_ITEMS_JSON_KEY);
    }

    private void saveRecentSellOfferItems()
    {
        saveItemIdsToConfig(recentSellOffersItemIds, RECENT_SELL_OFFERS_JSON_KEY);
    }

    private void saveItemIdsToConfig(List<Short> itemIds, String configKey)
    {
        final Short[] items = new Short[itemIds.size()];
        itemIds.toArray(items);

        final String json = GSON.toJson(items);
        configManager.setConfiguration(GEFiltersPlugin.CONFIG_GROUP_DATA, configKey, json);
    }

    private void loadRecentItems()
    {
        recentItemIds = loadItemIdsFromConfig(RECENT_ITEMS_JSON_KEY);
        applyRecentViewedConstraints(recentItemIds);
    }

    private void loadRecentBuyOfferItems()
    {
        recentBuyOffersItemIds = loadItemIdsFromConfig(RECENT_BUY_OFFERS_JSON_KEY);
    }

    private void loadPinnedItems()
    {
        pinnedItemIds = loadItemIdsFromConfig(PINNED_ITEMS_JSON_KEY);
        trimToMax(pinnedItemIds, getConfiguredPinnedMaxCount());
    }

    private void loadRecentSellOfferItems()
    {
        recentSellOffersItemIds = loadItemIdsFromConfig(RECENT_SELL_OFFERS_JSON_KEY);
    }

    private ArrayList<Short> loadItemIdsFromConfig(String configKey)
    {
        final String itemsJson = configManager.getConfiguration(GEFiltersPlugin.CONFIG_GROUP_DATA, configKey);
        if (itemsJson == null || itemsJson.isEmpty())
        {
            return new ArrayList<>();
        }

        try
        {
            final Short[] recentItems = GSON.fromJson(itemsJson, Short[].class);
            if (recentItems == null)
            {
                return new ArrayList<>();
            }

            final ArrayList<Short> filteredItems = new ArrayList<>(recentItems.length);
            for (Short id : recentItems)
            {
                if (id == null)
                {
                    continue;
                }

                final int itemId = Short.toUnsignedInt(id);
                if (itemId > 0 && itemId <= MAX_STORED_ITEM_ID)
                {
                    filteredItems.add((short) itemId);
                }
            }

            return filteredItems;
        }
        catch (RuntimeException ignored)
        {
            // Defensive: tolerate malformed persisted config and continue with an empty list.
            return new ArrayList<>();
        }
    }

    private int resolveEventItemId(MenuOptionClicked event)
    {
        final Widget eventWidget = event.getWidget();
        if (eventWidget != null)
        {
            final int widgetItemId = resolveCandidateItemId(eventWidget.getItemId());
            if (widgetItemId > 0)
            {
                return widgetItemId;
            }
        }

        final int eventItemId = resolveCandidateItemId(event.getItemId());
        if (eventItemId > 0)
        {
            return eventItemId;
        }

        final String currentSearch = client.getVarcStrValue(VarClientID.MESLAYERINPUT);
        final int rowIndex = event.getParam0();
        if (rowIndex >= 0)
        {
            if (SEARCH_BASE_RECENTLY_VIEWED.equals(currentSearch))
            {
                return resolveItemIdFromDisplayedResults(displayedRecentlyViewedItemIds, rowIndex);
            }

            if (SEARCH_BASE_PINNED_ITEMS.equals(currentSearch))
            {
                return resolveItemIdFromDisplayedResults(getPinnedItemsSnapshot(), rowIndex);
            }

            final int activeFilterItemId = resolveActiveFilterItemIdFromRow(rowIndex);
            if (activeFilterItemId > 0)
            {
                return activeFilterItemId;
            }
        }

        return -1;
    }

    private int resolveMenuEntryItemId(MenuEntry entry)
    {
        final Widget widget = entry.getWidget();
        if (widget != null)
        {
            final int widgetItemId = resolveCandidateItemId(widget.getItemId());
            if (widgetItemId > 0)
            {
                return widgetItemId;
            }
        }

        final int itemId = resolveCandidateItemId(entry.getItemId());
        if (itemId > 0)
        {
            return itemId;
        }

        return -1;
    }

    private int resolveCandidateItemId(int candidate)
    {
        return candidate > 0 && candidate <= MAX_STORED_ITEM_ID ? candidate : -1;
    }

    private int resolveActiveFilterItemId(String target, int rowIndex)
    {
        final int itemIdFromTarget = resolveItemIdFromActiveResultsByTarget(target);
        if (itemIdFromTarget > 0)
        {
            return itemIdFromTarget;
        }

        return resolveActiveFilterItemIdFromRow(rowIndex);
    }

    private int resolveItemIdFromActiveResultsByTarget(String target)
    {
        return resolveItemIdByTargetName(getActiveFilterGeSearchResultIdsSnapshot(), target);
    }

    private int resolveItemIdFromDisplayedResultsByTarget(List<Short> displayedItems, String target)
    {
        if (displayedItems == null || displayedItems.isEmpty())
        {
            return -1;
        }

        final short[] itemIds = FilterUtility.getPrimitiveShortArray(displayedItems);
        return resolveItemIdByTargetName(itemIds, target);
    }

    private int resolveItemIdByTargetName(short[] itemIds, String target)
    {
        if (itemIds == null || itemIds.length == 0)
        {
            return -1;
        }

        final String normalizedTarget = normalizeMenuTarget(target);
        if (normalizedTarget.isEmpty())
        {
            return -1;
        }

        for (short itemIdShort : itemIds)
        {
            final int itemId = Short.toUnsignedInt(itemIdShort);
            if (itemId <= 0 || itemId > MAX_STORED_ITEM_ID)
            {
                continue;
            }

            final String normalizedItemName = getNormalizedItemName(itemId);
            if (normalizedTarget.equals(normalizedItemName))
            {
                return itemId;
            }
        }

        return -1;
    }

    private int resolveItemIdFromTargetLookup(String target)
    {
        final String normalizedTarget = normalizeMenuTarget(target);
        if (normalizedTarget.isEmpty())
        {
            return -1;
        }

        try
        {
            final List<ItemPrice> matches = itemManager.search(normalizedTarget);
            for (ItemPrice match : matches)
            {
                final int candidateId = resolveCandidateItemId(match.getId());
                if (candidateId <= 0)
                {
                    continue;
                }

                final String normalizedName = normalizeItemName(match.getName());
                if (normalizedTarget.equals(normalizedName))
                {
                    return candidateId;
                }
            }
        }
        catch (RuntimeException ignored)
        {
            // Defensive: if name lookup fails, keep existing fallback behavior.
        }

        return -1;
    }

    private int resolveItemIdFromDisplayedResults(List<Short> displayedItems, int rowIndex)
    {
        if (displayedItems == null || displayedItems.isEmpty() || rowIndex < 0)
        {
            return -1;
        }

        // Depending on widget context, param0 can represent direct row index or child-index spacing.
        final int[] candidateIndexes = new int[]
                {
                        rowIndex,
                        rowIndex / 3,
                        (rowIndex - 1) / 3,
                        (rowIndex + 1) / 3
                };

        for (int candidate : candidateIndexes)
        {
            if (candidate >= 0 && candidate < displayedItems.size())
            {
                return Short.toUnsignedInt(displayedItems.get(candidate));
            }
        }

        return -1;
    }

    private String normalizeMenuOption(String option)
    {
        return HTML_TAG_PATTERN.matcher(option).replaceAll("").trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMenuTarget(String target)
    {
        if (target == null)
        {
            return "";
        }

        final String withoutTags = HTML_TAG_PATTERN.matcher(target).replaceAll("");
        return normalizeItemName(withoutTags);
    }

    public ArrayList<Short> getPinnedItemsSnapshot()
    {
        ensureRecentListsLoaded();
        return new ArrayList<>(pinnedItemIds);
    }

    public void pinItem(int itemId)
    {
        pinRecentlyViewedItem(itemId);
    }

    public void unpinItem(int itemId)
    {
        unpinRecentlyViewedItem(itemId);
    }

    public boolean isItemPinned(int itemId)
    {
        if (itemId <= 0 || itemId > MAX_STORED_ITEM_ID)
        {
            return false;
        }

        ensureRecentListsLoaded();
        return pinnedItemIds.contains((short) itemId);
    }

}
