package com.salverrs.GEFilters.Filters;

import com.salverrs.GEFilters.Filters.Model.FilterOption;
import com.salverrs.GEFilters.GEFiltersConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class PinnedItemsSearchFilter extends SearchFilter
{
    private static final int SPRITE_ID_MAIN = 1130; // SpriteID.WORLD_SWITCHER_STAR_FREE
    private static final String TITLE_PINNED_ITEMS = "Pinned Items";
    private static final String SEARCH_BASE_PINNED_ITEMS = RecentItemsSearchFilter.SEARCH_BASE_PINNED_ITEMS;
    private static final int MAX_STORED_ITEM_ID = 0xFFFF;
    private static final String MENU_OPTION_PIN = "Pin for Quick Access";
    private static final String MENU_OPTION_UNPIN = "Unpin from Quick Access";
    private static final String MENU_OPTION_SELECT_NORMALIZED = "select";
    private static final int WIDGET_ID_CHATBOX_GE_SEARCH_RESULTS = InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern MULTI_WHITESPACE_PATTERN = Pattern.compile("\\s+");

    @Inject
    private GEFiltersConfig config;

    @Inject
    private RecentItemsSearchFilter recentItemsSearchFilter;

    @Inject
    private ItemManager itemManager;

    private FilterOption pinnedItems;
    private List<Short> displayedPinnedItemIds = new ArrayList<>();

    @Override
    protected void onFilterInitialising()
    {
        pinnedItems = new FilterOption(TITLE_PINNED_ITEMS, SEARCH_BASE_PINNED_ITEMS);
        setFilterOptions(pinnedItems);
        setIconSprite(SPRITE_ID_MAIN, 0);
    }

    @Override
    protected void onFilterStarted()
    {
        // No-op.
    }

    @Override
    protected void onFilterEnabled(FilterOption option)
    {
        refreshPinnedResults();
    }

    private void refreshPinnedResults()
    {
        displayedPinnedItemIds = recentItemsSearchFilter.getPinnedItemsSnapshot();
        addItemFilterResults(displayedPinnedItemIds);
    }

    @Subscribe
    protected void onMenuOpened(MenuOpened event)
    {
        // Avoid duplicate pin/unpin entries when RecentItemsSearchFilter is active.
        if (config.enableRecentItemsFilter())
        {
            return;
        }

        if (!config.enablePinnedItemsFilter())
        {
            return;
        }

        if (!isGrandExchangeOpen() || !isGeSearchResultsOpen())
        {
            return;
        }

        final MenuEntry[] entries = event.getMenuEntries();
        if (entries == null || entries.length == 0)
        {
            return;
        }

        final String currentSearch = client.getVarcStrValue(VarClientID.MESLAYERINPUT);
        final boolean onPinnedItems = SEARCH_BASE_PINNED_ITEMS.equals(currentSearch);

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
            if (onPinnedItems)
            {
                itemId = resolveItemIdFromDisplayedResultsByTarget(displayedPinnedItemIds, entry.getTarget());

                if (itemId <= 0)
                {
                    itemId = resolveItemIdFromDisplayedResults(displayedPinnedItemIds, rowIndex);
                }
            }

            if (itemId <= 0)
            {
                itemId = resolveMenuEntryItemId(entry);
            }

            if (itemId <= 0)
            {
                itemId = resolveActiveFilterItemId(entry.getTarget(), rowIndex);
            }

            if (itemId <= 0)
            {
                itemId = resolveItemIdFromTargetLookup(entry.getTarget());
            }

            if (itemId <= 0 || itemId > MAX_STORED_ITEM_ID || !seenItemIds.add(itemId))
            {
                continue;
            }

            final int resolvedItemId = itemId;
            final boolean isPinned = recentItemsSearchFilter.isItemPinned(resolvedItemId);
            final String action = isPinned ? MENU_OPTION_UNPIN : MENU_OPTION_PIN;

            final Menu menu = client.getMenu();
            menu.createMenuEntry(-1)
                    .setOption(action)
                    .setTarget(entry.getTarget())
                    .setType(MenuAction.RUNELITE)
                    .setItemId(resolvedItemId)
                    .onClick(e ->
                    {
                        if (recentItemsSearchFilter.isItemPinned(resolvedItemId))
                        {
                            recentItemsSearchFilter.unpinItem(resolvedItemId);
                        }
                        else
                        {
                            recentItemsSearchFilter.pinItem(resolvedItemId);
                        }

                        refreshPinnedResultsIfActive();
                    });
        }
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

    private void refreshPinnedResultsIfActive()
    {
        if (SEARCH_BASE_PINNED_ITEMS.equals(client.getVarcStrValue(VarClientID.MESLAYERINPUT)))
        {
            refreshPinnedResults();
            // Keep pinned view in sync immediately after pin/unpin actions.
            forceUpdateSearch(true);
        }
    }

    private boolean isGeSearchResultsOpen()
    {
        return client.getWidget(WIDGET_ID_CHATBOX_GE_SEARCH_RESULTS) != null;
    }

    private boolean isGrandExchangeOpen()
    {
        return client.getWidget(InterfaceID.GE_OFFERS, 0) != null;
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

    private String getNormalizedItemName(int itemId)
    {
        try
        {
            final ItemComposition itemComposition = itemManager.getItemComposition(itemId);
            return normalizeItemName(itemComposition.getName());
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
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

        return MULTI_WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
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

}
