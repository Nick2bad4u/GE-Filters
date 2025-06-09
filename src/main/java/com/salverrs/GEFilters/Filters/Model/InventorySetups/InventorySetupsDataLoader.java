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

// Compatible with inventory-setups v1.19.3 'e0a5eb1d07a68749d448931d24d4ed2000903929' (Oct 7, 2024);
// Modified/simplified from 'InventorySetupsPersistentDataManager' to remove migration logic and to only read from config.

package com.salverrs.GEFilters.Filters.Model.InventorySetups;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.salverrs.GEFilters.Filters.Model.InventorySetups.Serialization.InventorySetupItemSerializable;
import com.salverrs.GEFilters.Filters.Model.InventorySetups.Serialization.InventorySetupItemSerializableTypeAdapter;
import com.salverrs.GEFilters.Filters.Model.InventorySetups.Serialization.InventorySetupSerializable;
import com.salverrs.GEFilters.Filters.Model.InventorySetups.Serialization.LongTypeAdapter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
public class InventorySetupsDataLoader
{
    public static final String CONFIG_GROUP = "inventorysetups";
    private final ConfigManager configManager;
    private Gson gson;
    public static final String CONFIG_KEY_SETUPS_V3_PREFIX = "setupsV3_";
    public static final String CONFIG_KEY_SETUPS_ORDER_V3 = "setupsOrderV3_";

    public InventorySetupsDataLoader(final ConfigManager manager,
                                     final Gson gson)
    {
        this.configManager = manager;
        this.gson = gson.newBuilder().registerTypeAdapter(long.class, new LongTypeAdapter()).create();
        this.gson = gson.newBuilder().registerTypeAdapter(InventorySetupItemSerializable.class, new InventorySetupItemSerializableTypeAdapter()).create();
    }

    public List<InventorySetup> getSetups()
    {
        return loadV3Setups();
    }

    private InventorySetup loadV3Setup(String configKey)
    {
        final String storedData = configManager.getConfiguration(CONFIG_GROUP, configKey);
        try
        {
            return InventorySetupSerializable.convertToInventorySetup(gson.fromJson(storedData, InventorySetupSerializable.class));
        }
        catch (Exception e)
        {
            log.error(String.format("[Ge-filters/Inventory-Setups] Exception occurred while loading %s", configKey), e);
            throw e;
        }
    }

    private List<InventorySetup> loadV3Setups()
    {
        final String wholePrefix = ConfigManager.getWholeKey(CONFIG_GROUP, null, CONFIG_KEY_SETUPS_V3_PREFIX);
        final List<String> loadedSetupWholeKeys = configManager.getConfigurationKeys(wholePrefix);
        Set<String> loadedSetupKeys = loadedSetupWholeKeys.stream().map(
                key -> key.substring(wholePrefix.length() - CONFIG_KEY_SETUPS_V3_PREFIX.length())
        ).collect(Collectors.toSet());

        Type setupsOrderType = new TypeToken<ArrayList<String>>()
        {

        }.getType();
        final String setupsOrderJson = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_ORDER_V3);
        List<String> setupsOrder = gson.fromJson(setupsOrderJson, setupsOrderType);
        if (setupsOrder == null)
        {
            setupsOrder = new ArrayList<>();
        }

        List<InventorySetup> loadedSetups = new ArrayList<>();
        for (final String configHash : setupsOrder)
        {
            final String configKey = CONFIG_KEY_SETUPS_V3_PREFIX + configHash;
            if (loadedSetupKeys.remove(configKey))
            { // Handles if hash is present only in configOrder.
                final InventorySetup setup = loadV3Setup(configKey);
                loadedSetups.add(setup);
            }
        }
        for (final String configKey : loadedSetupKeys)
        {
            // Load any remaining setups not present in setupsOrder. Useful if updateConfig crashes midway.
            //log.info("Loading setup that was missing from Order key: " + configKey);
            final InventorySetup setup = loadV3Setup(configKey);
            loadedSetups.add(setup);
        }
        return loadedSetups;
    }
}
