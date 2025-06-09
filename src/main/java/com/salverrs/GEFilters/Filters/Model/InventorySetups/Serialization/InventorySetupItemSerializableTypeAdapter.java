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

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

public class InventorySetupItemSerializableTypeAdapter extends TypeAdapter<InventorySetupItemSerializable>
{
    @Override
    public void write(JsonWriter out, InventorySetupItemSerializable iss) throws IOException
    {
        if (iss == null)
        {
            out.setSerializeNulls(true);
            out.nullValue();
            out.setSerializeNulls(false);
        }
        else
        {
            out.beginObject();
            out.name("id");
            out.value(iss.getId());
            if (iss.getQ() != null)
            {
                out.name("q");
                out.value(iss.getQ());
            }
            if (iss.getF() != null)
            {
                out.name("f");
                out.value(iss.getF());
            }
            if (iss.getSc() != null)
            {
                out.name("sc");
                out.value(iss.getSc().toString());
            }
            out.endObject();
        }

    }

    @Override
    public InventorySetupItemSerializable read(JsonReader in) throws IOException
    {
        if (in.peek() == JsonToken.NULL)
        {
            in.nextNull();
            return null;
        }

        int id = -1;
        Integer q = null;
        Boolean f = null;
        InventorySetupsStackCompareID sc = null;

        in.beginObject();
        while (in.hasNext())
        {
            JsonToken token = in.peek();
            if (token.equals(JsonToken.NAME))
            {
                //get the current token
                String fieldName = in.nextName();
                switch (fieldName)
                {
                    case "id":
                        id = in.nextInt();
                        break;
                    case "q":
                        q = in.nextInt();
                        break;
                    case "f":
                        f = in.nextBoolean();
                        break;
                    case "sc":
                        sc = InventorySetupsStackCompareID.valueOf(in.nextString());
                        break;
                    default:
                        break;
                }
            }
        }

        in.endObject();
        return new InventorySetupItemSerializable(id, q, f, sc);
    }
}