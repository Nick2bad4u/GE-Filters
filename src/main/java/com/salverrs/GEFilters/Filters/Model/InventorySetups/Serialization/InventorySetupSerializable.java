// All credit to Inventory Setups maintainers - https://github.com/dillydill123/inventory-setups
/*
 * Copyright (c) 2019, dillydill123 <https://github.com/dillydill123>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.salverrs.GEFilters.Filters.Model.InventorySetups.Serialization;

import com.salverrs.GEFilters.Filters.Model.*;
import com.salverrs.GEFilters.Filters.Model.InventorySetups.InventorySetup;
import com.salverrs.GEFilters.Filters.Model.InventorySetups.InventorySetupsItem;
import joptsimple.internal.Strings;
import lombok.Value;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Value
public class InventorySetupSerializable
{

    List<InventorySetupItemSerializable> inv;	// inventory
    List<InventorySetupItemSerializable> eq;	// equipment
    @Nullable
    List<InventorySetupItemSerializable> rp;	// rune pouch (null = No rp)
    @Nullable
    List<InventorySetupItemSerializable> bp;	// bolt pouch (null = No bp)
    @Nullable
    List<InventorySetupItemSerializable> qv;	// quiver (null = No qv)
    @Nullable
    Map<Integer, InventorySetupItemSerializable> afi;		// additional filtered items (null = No afi)
    String name;	// name of setup
    @Nullable
    String notes;	// notes (null = empty notes)
    Color hc; 		// highlight color
    @Nullable
    Boolean hd;		// highlight difference (null = false)
    @Nullable
    Color dc;		// display color (null = no color)
    @Nullable
    Boolean fb;		// filter bank (null = false)
    @Nullable
    Boolean uh;		// unordered highlight (null = false)
    @Nullable
    Integer sb;		// Spell book (null = 0 standard)
    @Nullable
    Boolean fv;		// favorite (null = false)
    @Nullable
    Integer iId;	// iconID (null = default item ID for icon view)

    static public InventorySetupSerializable convertFromInventorySetup(final InventorySetup inventorySetup)
    {

        List<InventorySetupItemSerializable> inv = convertListFromInventorySetup(inventorySetup.getInventory());
        List<InventorySetupItemSerializable> eq = convertListFromInventorySetup(inventorySetup.getEquipment());
        List<InventorySetupItemSerializable> rp = convertListFromInventorySetup(inventorySetup.getRune_pouch());
        List<InventorySetupItemSerializable> bp = convertListFromInventorySetup(inventorySetup.getBoltPouch());
        List<InventorySetupItemSerializable> qv = convertListFromInventorySetup(inventorySetup.getQuiver());

        Map<Integer, InventorySetupItemSerializable> afi = null;
        if (inventorySetup.getAdditionalFilteredItems() != null && !inventorySetup.getAdditionalFilteredItems().isEmpty())
        {
            afi = new HashMap<>();
            for (final Integer key : inventorySetup.getAdditionalFilteredItems().keySet())
            {
                afi.put(key, InventorySetupItemSerializable.convertFromInventorySetupItem(inventorySetup.getAdditionalFilteredItems().get(key)));
            }
        }

        String name = inventorySetup.getName();
        String notes = !Strings.isNullOrEmpty(inventorySetup.getNotes()) ? inventorySetup.getNotes() : null;
        Color hc = inventorySetup.getHighlightColor();
        Boolean hd = inventorySetup.isHighlightDifference() ? Boolean.TRUE : null;
        Color dc = inventorySetup.getDisplayColor();
        Boolean fb = inventorySetup.isFilterBank() ? Boolean.TRUE : null;
        Boolean uh = inventorySetup.isUnorderedHighlight() ? Boolean.TRUE : null;
        Integer sb = inventorySetup.getSpellBook() != 0 ? inventorySetup.getSpellBook() : null;
        Boolean fv = inventorySetup.isFavorite() ? Boolean.TRUE : null;
        Integer iId = inventorySetup.getIconID() > 0 ? inventorySetup.getIconID() : null;

        return new InventorySetupSerializable(inv, eq, rp, bp, qv, afi, name, notes, hc, hd, dc, fb, uh, sb, fv, iId);
    }

    static private List<InventorySetupItemSerializable> convertListFromInventorySetup(final List<InventorySetupsItem> items)
    {
        List<InventorySetupItemSerializable> iss_list = null;
        if (items != null)
        {
            iss_list = new ArrayList<>();
            for (final InventorySetupsItem item : items)
            {
                iss_list.add(InventorySetupItemSerializable.convertFromInventorySetupItem(item));
            }
        }
        return iss_list;
    }

    static private List<InventorySetupsItem> convertListToInventorySetup(final List<InventorySetupItemSerializable> iss_items)
    {
        List<InventorySetupsItem> itemList = null;
        if (iss_items != null)
        {
            itemList = new ArrayList<>();
            for (final InventorySetupItemSerializable iss_item : iss_items)
            {
                itemList.add(InventorySetupItemSerializable.convertToInventorySetupItem(iss_item));
            }
        }
        return itemList;
    }

    static public InventorySetup convertToInventorySetup(final InventorySetupSerializable iss)
    {

        // Note that items will not have a name. They will need to be retrieved from the item manager
        // Either immediately after or delayed when a setup is opened
        List<InventorySetupsItem> inv = convertListToInventorySetup(iss.getInv());
        List<InventorySetupsItem> eq = convertListToInventorySetup(iss.getEq());
        List<InventorySetupsItem> rp = convertListToInventorySetup(iss.getRp());
        List<InventorySetupsItem> bp = convertListToInventorySetup(iss.getBp());
        List<InventorySetupsItem> qv = convertListToInventorySetup(iss.getQv());
        Map<Integer, InventorySetupsItem> afi = new HashMap<>();
        if (iss.getAfi() != null)
        {
            for (final Integer key : iss.getAfi().keySet())
            {
                afi.put(key, InventorySetupItemSerializable.convertToInventorySetupItem(iss.getAfi().get(key)));
            }
        }
        String name = iss.getName();
        String notes = iss.getNotes() != null ? iss.getNotes() : "";
        Color hc = iss.getHc();
        boolean hd = iss.getHd() != null ? iss.getHd() : Boolean.FALSE;
        Color dc = iss.getDc();
        boolean fb = iss.getFb() != null ? iss.getFb() : Boolean.FALSE;
        boolean uh = iss.getUh() != null ? iss.getUh() : Boolean.FALSE;
        int sb = iss.getSb() != null ? iss.getSb() : 0;
        boolean fv = iss.getFv() != null ? iss.getFv() : Boolean.FALSE;
        int iId = iss.getIId() != null ? iss.getIId() : -1;

        return new InventorySetup(inv, eq, rp, bp, qv, afi, name, notes, hc, hd, dc, fb, uh, sb, fv, iId);
    }


}