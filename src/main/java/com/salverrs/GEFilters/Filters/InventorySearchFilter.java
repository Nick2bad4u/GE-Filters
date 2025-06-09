package com.salverrs.GEFilters.Filters;

import com.salverrs.GEFilters.Filters.Model.FilterOption;
import net.runelite.api.*;

import java.util.ArrayList;
import java.util.List;

public class InventorySearchFilter extends SearchFilter {

    private static final int SPRITE_ID_MAIN = SpriteID.TAB_INVENTORY;
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
            addInventoryContainerResults(InventoryID.INVENTORY);
        }
        else if (option == equipmentFilter)
        {
            addInventoryContainerResults(InventoryID.EQUIPMENT);
        }
    }

    private void addInventoryContainerResults(InventoryID inventoryID)
    {
        final ItemContainer container = client.getItemContainer(inventoryID);
        if (container == null)
            return;

        final Item[] items = container.getItems();
        final List<Short> itemIds = new ArrayList<>();

        for (Item i : items)
        {
            final int id = i.getId();

            if (itemIds.contains((short)id))
                continue;

            final ItemComposition composition = client.getItemDefinition(id);
            ItemComposition unnotedComposition = null;

            final int notedId = composition.getLinkedNoteId();
            if (notedId != -1)
            {
                unnotedComposition = client.getItemDefinition(notedId);
            }

            if (composition.isTradeable())
            {
                itemIds.add((short)id);
            }
            else if (unnotedComposition != null && unnotedComposition.isTradeable())
            {
                itemIds.add((short)notedId);
            }
        }

        final short[] itemResultIds = FilterUtility.getPrimitiveShortArray(itemIds);
        setGESearchResults(itemResultIds);
    }

}
