package com.salverrs.GEFilters.Filters;

import com.salverrs.GEFilters.Filters.Model.FilterOption;
import com.salverrs.GEFilters.GEFiltersPlugin;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.SpriteID;
import net.runelite.api.VarPlayer;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.runelite.http.api.RuneLiteAPI.GSON;

public class RecentItemsSearchFilter extends SearchFilter {
    private static final int MAX_HISTORY_COUNT = 100;
    private static final int SPRITE_ID_MAIN = SpriteID.HOUSE_VIEWER_ROTATE_ANTICLOCKWISE;
    private static final String RECENT_ITEMS_JSON_KEY = "ge-recent-items";
    private static final String RECENT_BUY_OFFERS_JSON_KEY = "ge-recent-buy-offers";
    private static final String RECENT_SELL_OFFERS_JSON_KEY = "ge-recent-sell-offers";
    private static final String TITLE_RECENTLY_VIEWED = "Recently Viewed";
    private static final String TITLE_RECENT_BUY_OFFERS = "Recent Buy Offers";
    private static final String TITLE_RECENT_SELL_OFFERS = "Recent Sell Offers";
    private static final String SEARCH_BASE_RECENTLY_VIEWED = "recently-viewed-items";
    private static final String SEARCH_BASE_RECENT_BUY_OFFERS = "recent-buy-offers";
    private static final String SEARCH_BASE_RECENT_SELL_OFFERS = "recent-sell-offers";

    private FilterOption recentlyViewed, recentBuyOffers, recentSellOffers;
    private ArrayList<Short> recentItemIds, recentBuyOffersItemIds, recentSellOffersItemIds;

    @Override
    protected void onFilterInitialising()
    {
        loadRecentItems();
        loadRecentBuyOfferItems();
        loadRecentSellOfferItems();

        recentlyViewed = new FilterOption(TITLE_RECENTLY_VIEWED, SEARCH_BASE_RECENTLY_VIEWED);
        recentBuyOffers = new FilterOption(TITLE_RECENT_BUY_OFFERS, SEARCH_BASE_RECENT_BUY_OFFERS);
        recentSellOffers = new FilterOption(TITLE_RECENT_SELL_OFFERS, SEARCH_BASE_RECENT_SELL_OFFERS);

        setFilterOptions(recentlyViewed, recentBuyOffers, recentSellOffers);
        setIconSprite(SPRITE_ID_MAIN, 0);
    }

    @Override
    protected void onFilterStarted()
    {
        loadRecentItems();
        loadRecentBuyOfferItems();
        loadRecentSellOfferItems();
    }

    @Override
    protected void onFilterEnabled(FilterOption option)
    {
        if (option == recentlyViewed)
        {
            addItemFilterResults(recentItemIds);
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
        if (!ready)
            return;

        final GrandExchangeOffer offer = newOfferEvent.getOffer();
        final GrandExchangeOfferState offerState = offer.getState();

        if (offerState == GrandExchangeOfferState.BUYING)
        {
            appendToIdList(recentBuyOffersItemIds, (short)offer.getItemId());
            saveRecentBuyOfferItems();
        }
        else if (offerState == GrandExchangeOfferState.SELLING)
        {
            appendToIdList(recentSellOffersItemIds, (short)offer.getItemId());
            saveRecentSellOfferItems();
        }
    }

    @Subscribe
    protected void onVarbitChanged(VarbitChanged event)
    {
        if (!ready)
            return;

        if (event.getVarpId() != VarPlayer.CURRENT_GE_ITEM)
            return;

        final int recentId = client.getVarpValue(VarPlayer.CURRENT_GE_ITEM);

        if (recentId == -1 || recentId == 0)
            return;

        appendToIdList(recentItemIds, (short)recentId);
        saveRecentItems();
    }

    private void appendToIdList(List<Short> itemList, short itemId)
    {
        final int existingIndex = itemList.indexOf(itemId);
        if (existingIndex != -1)
        {
            itemList.remove(existingIndex);
        }

        itemList.add(0, itemId);

        if (itemList.size() == MAX_HISTORY_COUNT)
        {
            itemList.remove(MAX_HISTORY_COUNT - 1);
        }
    }

    private void addItemFilterResults(ArrayList<Short> items)
    {
        if (items == null || items.isEmpty())
            return;

        final short[] itemIds = FilterUtility.getPrimitiveShortArray(items);
        setGESearchResults(itemIds);
    }

    private void saveRecentItems()
    {
        saveItemIdsToConfig(recentItemIds, RECENT_ITEMS_JSON_KEY);
    }

    private void saveRecentBuyOfferItems()
    {
        saveItemIdsToConfig(recentBuyOffersItemIds, RECENT_BUY_OFFERS_JSON_KEY);
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
    }

    private void loadRecentBuyOfferItems()
    {
        recentBuyOffersItemIds = loadItemIdsFromConfig(RECENT_BUY_OFFERS_JSON_KEY);
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
            return new ArrayList<Short>();
        }
        else
        {
            final Short[] recentItems = GSON.fromJson(itemsJson, Short[].class);
            return new ArrayList<>(Arrays.asList(recentItems));
        }
    }

}
