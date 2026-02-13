package com.salverrs.GEFilters.Filters;

import com.salverrs.GEFilters.Filters.Model.FilterOption;
import com.salverrs.GEFilters.GEFiltersPlugin;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.runelite.http.api.RuneLiteAPI.GSON;

public class RecentItemsSearchFilter extends SearchFilter {
    private static final int MAX_HISTORY_COUNT = 100;
    private static final int VARP_CURRENT_GE_ITEM = 1151;
    private static final String MENU_OPTION_SELECT = "Select";
    private static final int WIDGET_ID_CHATBOX_GE_SEARCH_RESULTS = InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS;
    private static final int SPRITE_ID_MAIN = 1367;
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
            addItemFilterResults(getRecentlyViewedItemsWithCurrent());
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

        if (event.getVarpId() != VARP_CURRENT_GE_ITEM)
            return;

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

        if (!ready)
            return;

        if (!isGeSearchResultsOpen())
            return;

        final String menuOption = event.getMenuOption();
        if (menuOption == null)
            return;

        // GE menu options may include color tags / slight variants. We only care about select actions.
        final String normalizedOption = menuOption.replaceAll("<[^>]*>", "").trim();
        if (!normalizedOption.startsWith(MENU_OPTION_SELECT))
            return;

        int itemId = event.getItemId();
        final net.runelite.api.widgets.Widget eventWidget = event.getWidget();
        if (itemId <= 0 && eventWidget != null)
            itemId = eventWidget.getItemId();

        if (itemId <= 0)
            itemId = client.getVarpValue(VARP_CURRENT_GE_ITEM);

        addRecentViewedItem(itemId);
    }

    private void appendToIdList(List<Short> itemList, short itemId)
    {
        final int existingIndex = itemList.indexOf(itemId);
        if (existingIndex != -1)
        {
            itemList.remove(existingIndex);
        }

        itemList.add(0, itemId);

        if (itemList.size() > MAX_HISTORY_COUNT)
        {
            itemList.remove(itemList.size() - 1);
        }
    }

    private void addRecentViewedItem(int itemId)
    {
        if (itemId <= 0)
            return;

        appendToIdList(recentItemIds, (short)itemId);
        saveRecentItems();

        // Keep the results live if the user currently has the Recently Viewed filter open.
        if (SEARCH_BASE_RECENTLY_VIEWED.equals(client.getVarcStrValue(VarClientID.MESLAYERINPUT)))
        {
            addItemFilterResults(getRecentlyViewedItemsWithCurrent());
        }
    }

    private ArrayList<Short> getRecentlyViewedItemsWithCurrent()
    {
        final ArrayList<Short> items = new ArrayList<>(recentItemIds);
        final int currentItemId = client.getVarpValue(VARP_CURRENT_GE_ITEM);
        if (currentItemId > 0)
        {
            final short currentId = (short)currentItemId;
            items.remove((Short)currentId);
            items.add(0, currentId);
        }

        return items;
    }

    private boolean isGeSearchResultsOpen()
    {
        return client.getWidget(WIDGET_ID_CHATBOX_GE_SEARCH_RESULTS) != null;
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
