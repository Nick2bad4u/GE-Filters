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

import com.salverrs.GEFilters.Filters.Model.InventorySetups.*;

import lombok.Value;
import javax.annotation.Nullable;

@Value
public class InventorySetupItemSerializable
{
    int id;
    @Nullable
    Integer q;		// Quantity (null = 1)
    @Nullable
    Boolean f;		// Fuzzy (null = FALSE)
    @Nullable
    InventorySetupsStackCompareID sc;	// Stack Compare (null = NONE)

    static public InventorySetupItemSerializable convertFromInventorySetupItem(final InventorySetupsItem item)
    {
        if (item == null || InventorySetupsItem.itemIsDummy(item))
        {
            return null;
        }
        Integer quantity = item.getQuantity() != 1 ? item.getQuantity() : null;
        Boolean fuzzy = item.isFuzzy() ? Boolean.TRUE : null;
        InventorySetupsStackCompareID sc = item.getStackCompare() != InventorySetupsStackCompareID.None ? item.getStackCompare() : null;
        return new InventorySetupItemSerializable(item.getId(), quantity, fuzzy, sc);
    }

    static public InventorySetupsItem convertToInventorySetupItem(final InventorySetupItemSerializable is)
    {
        if (is == null)
        {
            return InventorySetupsItem.getDummyItem();
        }
        int id = is.getId();
        // Name is not saved in the serializable object. It must be obtained from the item manager at runtime
        String name = "";
        int quantity = is.getQ() != null ? is.getQ() : 1;
        boolean fuzzy = is.getF() != null ? is.getF() : Boolean.FALSE;
        InventorySetupsStackCompareID sc = is.getSc() != null ? is.getSc() : InventorySetupsStackCompareID.None;
        return new InventorySetupsItem(id, name, quantity, fuzzy, sc);
    }
}