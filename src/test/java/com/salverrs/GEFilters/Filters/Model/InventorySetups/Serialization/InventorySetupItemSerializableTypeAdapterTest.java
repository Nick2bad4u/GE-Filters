package com.salverrs.GEFilters.Filters.Model.InventorySetups.Serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.salverrs.GEFilters.Filters.Model.InventorySetups.InventorySetupsItem;
import com.salverrs.GEFilters.Filters.Model.InventorySetups.InventorySetupsStackCompareID;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class InventorySetupItemSerializableTypeAdapterTest
{
    private Gson gson;

    @Before
    public void setUp()
    {
        gson = new GsonBuilder()
                .registerTypeAdapter(InventorySetupItemSerializable.class, new InventorySetupItemSerializableTypeAdapter())
                .create();
    }

    @Test
    public void read_ignoresUnknownFields()
    {
        final String json = "{\"id\":4151,\"q\":2,\"f\":true,\"unknown\":\"ignored\"}";

        final InventorySetupItemSerializable parsed = gson.fromJson(json, InventorySetupItemSerializable.class);

        assertNotNull(parsed);
        assertEquals(4151, parsed.getId());
        assertEquals(Integer.valueOf(2), parsed.getQ());
        assertEquals(Boolean.TRUE, parsed.getF());
    }

    @Test
    public void read_unknownStackCompareFallsBackToNone()
    {
        final String json = "{\"id\":4151,\"q\":1,\"f\":false,\"sc\":\"invalid\"}";

        final InventorySetupItemSerializable parsed = gson.fromJson(json, InventorySetupItemSerializable.class);

        assertNotNull(parsed);
        assertEquals(InventorySetupsStackCompareID.None, parsed.getSc());
    }

    @Test
    public void convertFromInventorySetupItem_handlesNullStackCompare()
    {
        final InventorySetupsItem source = new InventorySetupsItem(4151, "Abyssal whip", 1, false, null);

        final InventorySetupItemSerializable converted = InventorySetupItemSerializable.convertFromInventorySetupItem(source);

        assertNotNull(converted);
        assertEquals(4151, converted.getId());
        assertNull(converted.getQ());
        assertNull(converted.getF());
        assertNull(converted.getSc());
    }
}
