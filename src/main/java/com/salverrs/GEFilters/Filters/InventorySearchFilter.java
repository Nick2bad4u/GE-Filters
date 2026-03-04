package com.salverrs.GEFilters.Filters;

import com.salverrs.GEFilters.Filters.Model.FilterOption;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InventorySearchFilter extends SearchFilter {

    private static final int SPRITE_ID_MAIN = 900; // SpriteID.TAB_INVENTORY
    private static final int MAX_STORED_ITEM_ID = 0xFFFF;
    private static final int INVENTORY_CONTAINER_ID = 93; // InventoryID.INVENTORY
    private static final int EQUIPMENT_CONTAINER_ID = 94; // InventoryID.EQUIPMENT
    private static final String TITLE_INVENTORY = "Inventory Items";
    private static final String TITLE_EQUIPMENT = "Equipped Items";
    private static final String SEARCH_BASE_INVENTORY = "inventory-items";
    private static final String SEARCH_BASE_EQUIPMENT = "equipped-items";
    private FilterOption inventoryFilter, equipmentFilter;

    @Override
    protected void onFilterInitialising()
    {
        inventoryFilter = new FilterOption(TITLE_INVENTORY, SEARCH_BASE_INVENTORY);
        equipmentFilter = new FilterOption(TITLE_EQUIPMENT, SEARCH_BASE_EQUIPMENT);

        setFilterOptions(inventoryFilter, equipmentFilter);
        setIconSprite(SPRITE_ID_MAIN, 0);
    }

    @Override
    protected void onFilterStarted()
    {
    }

    @Override
    protected void onFilterEnabled(FilterOption option)
    {
        if (option == inventoryFilter)
        {
            addInventoryContainerResults(INVENTORY_CONTAINER_ID);
        }
        else if (option == equipmentFilter)
        {
            addInventoryContainerResults(EQUIPMENT_CONTAINER_ID);
        }
    }

    private void addInventoryContainerResults(int inventoryID)
    {
        final ItemContainer container = client.getItemContainer(inventoryID);
        if (container == null)
            return;

        final Item[] items = container.getItems();
        final List<Short> itemIds = new ArrayList<>();
        final Set<Short> seenItemIds = new HashSet<>();

        for (Item i : items)
        {
            final int id = i.getId();
            if (id <= 0 || id > MAX_STORED_ITEM_ID)
            {
                continue;
            }

            final short shortId = (short) id;

            if (seenItemIds.contains(shortId))
                continue;

            final ItemComposition composition = client.getItemDefinition(id);
            ItemComposition unnotedComposition = null;

            final int notedId = composition.getLinkedNoteId();
            if (notedId > 0 && notedId <= MAX_STORED_ITEM_ID)
            {
                unnotedComposition = client.getItemDefinition(notedId);
            }

            if (composition.isTradeable())
            {
                itemIds.add(shortId);
                seenItemIds.add(shortId);
            }
            else if (unnotedComposition != null && unnotedComposition.isTradeable())
            {
                final short unnotedId = (short) notedId;
                if (!seenItemIds.contains(unnotedId))
                {
                    itemIds.add(unnotedId);
                    seenItemIds.add(unnotedId);
                }
            }
        }

        final short[] itemResultIds = FilterUtility.getPrimitiveShortArray(itemIds);
        setGESearchResults(itemResultIds);
    }

}
