package com.salverrs.GEFilters.Filters;

import com.salverrs.GEFilters.Filters.Model.FilterOption;
import net.runelite.api.ItemComposition;
import net.runelite.client.config.ConfigManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BankHighlighterSearchFilter extends SearchFilter
{
    private static final int SPRITE_ID_MAIN = 1453; // SpriteID.MAP_ICON_BANK
    private static final int MAX_STORED_ITEM_ID = 0xFFFF;

    private static final String TITLE_MAIN = "Bank Highlighter";
    private static final String SEARCH_BASE_MAIN = "bank-highlighter-items";

    private static final String BANK_HIGHLIGHTER_CONFIG_GROUP = "com/bankhighlighter";
    private static final String INVENTORY_TAGS_GROUP = "inventorytags";
    private static final String TAG_KEY_PREFIX = "tag_";
    private static final String USE_INVENTORY_TAGS_KEY = "useInventoryTagsConfig";

    private FilterOption bankHighlighterFilter;

    @Override
    protected void onFilterInitialising()
    {
        bankHighlighterFilter = new FilterOption(TITLE_MAIN, SEARCH_BASE_MAIN);
        setFilterOptions(bankHighlighterFilter);
        setIconSprite(SPRITE_ID_MAIN, -1);
    }

    @Override
    protected void onFilterStarted()
    {
        // no-op
    }

    @Override
    protected void onFilterEnabled(FilterOption option)
    {
        if (option != bankHighlighterFilter)
        {
            return;
        }

        final List<Short> highlightedItemIds = getBankHighlighterItemIds();
        if (highlightedItemIds.isEmpty())
        {
            setGESearchResults(new short[0]);
            return;
        }

        setGESearchResults(FilterUtility.getPrimitiveShortArray(highlightedItemIds));
    }

    private List<Short> getBankHighlighterItemIds()
    {
        final String sourceGroup = isSharingInventoryTagsConfig()
                ? INVENTORY_TAGS_GROUP
                : BANK_HIGHLIGHTER_CONFIG_GROUP;

        final String wholePrefix = ConfigManager.getWholeKey(sourceGroup, null, TAG_KEY_PREFIX);
        final List<String> wholeKeys = configManager.getConfigurationKeys(wholePrefix);
        if (wholeKeys == null || wholeKeys.isEmpty())
        {
            return Collections.emptyList();
        }

        final Set<Short> seen = new HashSet<>();
        final List<Short> geItemIds = new ArrayList<>();

        for (String wholeKey : wholeKeys)
        {
            final String key = extractConfigKey(wholeKey);
            if (!key.startsWith(TAG_KEY_PREFIX))
            {
                continue;
            }

            final String itemIdPart = key.substring(TAG_KEY_PREFIX.length());
            final int rawItemId;
            try
            {
                rawItemId = Integer.parseInt(itemIdPart);
            }
            catch (NumberFormatException ignored)
            {
                continue;
            }

            if (rawItemId <= 0 || rawItemId > MAX_STORED_ITEM_ID)
            {
                continue;
            }

            final short geItemId = resolveTradeableGeItemId(rawItemId);
            if (geItemId <= 0 || !seen.add(geItemId))
            {
                continue;
            }

            geItemIds.add(geItemId);
        }

        return geItemIds;
    }

    private short resolveTradeableGeItemId(int rawItemId)
    {
        final ItemComposition composition = client.getItemDefinition(rawItemId);
        if (composition.isTradeable())
        {
            return (short) rawItemId;
        }

        final int linkedNoteId = composition.getLinkedNoteId();
        if (linkedNoteId > 0 && linkedNoteId <= MAX_STORED_ITEM_ID)
        {
            final ItemComposition linked = client.getItemDefinition(linkedNoteId);
            if (linked != null && linked.isTradeable())
            {
                return (short) linkedNoteId;
            }
        }

        return 0;
    }

    private boolean isSharingInventoryTagsConfig()
    {
        final String value = configManager.getConfiguration(BANK_HIGHLIGHTER_CONFIG_GROUP, USE_INVENTORY_TAGS_KEY);
        return Boolean.parseBoolean(value);
    }

    private String extractConfigKey(String wholeKey)
    {
        final int lastDot = wholeKey.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < wholeKey.length())
        {
            return wholeKey.substring(lastDot + 1);
        }

        return wholeKey;
    }
}
