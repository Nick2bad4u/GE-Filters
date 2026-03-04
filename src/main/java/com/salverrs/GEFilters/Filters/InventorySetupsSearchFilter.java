package com.salverrs.GEFilters.Filters;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import javax.inject.Inject;
import com.salverrs.GEFilters.Filters.Model.FilterOption;
import com.salverrs.GEFilters.Filters.Model.GeSearch;
import com.salverrs.GEFilters.Filters.Model.GeSearchResultWidget;
import com.salverrs.GEFilters.Filters.Model.InventorySetups.InventorySetup;
import com.salverrs.GEFilters.Filters.Model.InventorySetups.InventorySetupsDataLoader;
import com.salverrs.GEFilters.Filters.Model.InventorySetups.InventorySetupsItem;
import com.salverrs.GEFilters.Filters.Model.InventorySetups.Serialization.InventorySetupItemSerializable;
import com.salverrs.GEFilters.Filters.Model.InventorySetups.Serialization.InventorySetupItemSerializableTypeAdapter;
import com.salverrs.GEFilters.Filters.Model.InventorySetups.Serialization.LongTypeAdapter;
import com.salverrs.GEFilters.GEFiltersConfig;
import com.salverrs.GEFilters.GEFiltersPlugin;
import lombok.Getter;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

public class InventorySetupsSearchFilter extends SearchFilter {

    private static final int SPRITE_ID_MAIN = 901; // SpriteID.TAB_EQUIPMENT
    private static final String TITLE_MAIN = "Inventory Setups";
    private static final String SEARCH_BASE_MAIN = "inventory-setups";
    private static final String INV_SETUPS_MENU_IDENTIFIER = "open setup";
    private static final String INV_SETUPS_MENU_IDENTIFIER_2 = "open section";
    private static final String SETUPS_EXCEPTION_JSON_KEY = "inventory-setups-exceptions";
    private static final int WIDGET_ID_CHATBOX_GE_SEARCH_RESULTS = InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS;
    private static final int MAX_STORED_ITEM_ID = 0xFFFF;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
	/**
	 * Inventory Setups uses Bank Tags layouts with a stable tag prefix. We can infer the setup name
	 * from the currently active bank tag when Inventory Setups bank filtering/layout is active.
	 */
	private static final String INV_SETUP_TAG_PREFIX = "_invsetup_";
	private static final String CONFIG_KEY_LAST_ACTIVE_SETUP = "invSetupsLastActiveSetup";
    private final GEFiltersConfig config;
    private FilterOption inventorySetupsFilter;
    private boolean bankOpen = false;
    private List<String> setupExceptions = new ArrayList<>();
    private Gson gson;
    private List<InventorySetup> inventorySetups = new ArrayList<>();
    private boolean initialLoad = true;

    /**
     * Some development/test harnesses may instantiate this plugin without fully installing the Bank Tags
     * module bindings, which would otherwise fail plugin construction if this dependency is required.
     *
     * We don't require BankTagsService for core functionality here, so keep this optional.
     */
    @com.google.inject.Inject(optional = true)
    @SuppressWarnings("unused")
    private net.runelite.client.plugins.banktags.BankTagsService bankTagsService;

    @Getter
    private final InventorySetupsDataLoader dataManager;

    @Inject
    public InventorySetupsSearchFilter(GEFiltersConfig config, ConfigManager configManager, Gson gson) {
        this.config = config;
        this.gson = gson.newBuilder()
            .registerTypeAdapter(long.class, new LongTypeAdapter())
            .registerTypeAdapter(InventorySetupItemSerializable.class, new InventorySetupItemSerializableTypeAdapter())
            .create();

        this.dataManager = new InventorySetupsDataLoader(configManager, this.gson);
    }

    @Override
    protected void onFilterInitialising()
    {
        inventorySetupsFilter = new FilterOption(TITLE_MAIN, SEARCH_BASE_MAIN);
        setFilterOptions(inventorySetupsFilter);
        setIconSprite(SPRITE_ID_MAIN, 0);
    }

    @Override
    protected void onFilterStarted()
    {
        loadSetupExceptions();
        ensureSetupsLoadedBestEffort();

        if (initialLoad) {
            loadUpdatedInventorySetups();
            initialLoad = false;
        }
    }

    @Override
    protected void onFilterEnabled(FilterOption option)
    {
        if (option == inventorySetupsFilter)
        {
            // If Inventory Setups has an active (currently selected) setup, optionally skip the setup list
            // and go straight to filtering for that setup.
            if (option.getData() == null && config.invSetupsAutoSelectActiveSetup())
            {
                final String activeSetupName = getActiveInventorySetupName();
                if (activeSetupName != null)
                {
                    // Ensure setups are loaded before attempting to resolve the setup.
                    if (inventorySetups == null || inventorySetups.isEmpty())
                    {
                        loadUpdatedInventorySetups();
                    }

                    if (getInventorySetup(activeSetupName) != null)
                    {
                        inventorySetupsFilter.setData(activeSetupName);
                        generateSetupResults(activeSetupName);
                        return;
                    }
                }
            }

            if (option.getData() != null)
            {
                if (option.getData() instanceof String)
                {
                    generateSetupResults((String) option.getData());
                }
                else
                {
                    option.setData(null);
                    addInvSetupsFilterOptionResults();
                }
            }
            else
            {
                addInvSetupsFilterOptionResults();
            }
        }
    }

    private String getActiveInventorySetupName()
    {
        final String inferred = inferActiveSetupFromBankTags();
        if (inferred != null)
        {
            return inferred;
        }

        // Fallback: use the last inferred setup persisted by the plugin.
        final String last = configManager.getConfiguration(GEFiltersPlugin.CONFIG_GROUP_DATA, CONFIG_KEY_LAST_ACTIVE_SETUP);
        if (last == null || last.trim().isEmpty())
        {
            return null;
        }

        ensureSetupsLoadedBestEffort();
        if (setupExceptions.contains(last) || getInventorySetup(last) == null)
        {
            return null;
        }

        return last;
    }

    private String inferActiveSetupFromBankTags()
    {
        if (bankTagsService == null)
        {
            return null;
        }

        final String activeTag = bankTagsService.getActiveTag();
        if (activeTag == null || !activeTag.startsWith(INV_SETUP_TAG_PREFIX))
        {
            return null;
        }

        final String targetHash = activeTag.substring(INV_SETUP_TAG_PREFIX.length());
        if (targetHash.isEmpty())
        {
            return null;
        }

        ensureSetupsLoadedBestEffort();
        if (inventorySetups == null || inventorySetups.isEmpty())
        {
            return null;
        }

        for (InventorySetup setup : inventorySetups)
        {
            final String name = setup.getName();
            if (name == null || setupExceptions.contains(name))
            {
                continue;
            }

            final String hash = Hashing.murmur3_128().hashUnencodedChars(name).toString();
            if (targetHash.equals(hash))
            {
                configManager.setConfiguration(GEFiltersPlugin.CONFIG_GROUP_DATA, CONFIG_KEY_LAST_ACTIVE_SETUP, name);
                return name;
            }
        }

        return null;
    }

    private void ensureSetupsLoadedBestEffort()
    {
        if (dataManager == null)
        {
            return;
        }

        if (inventorySetups != null && !inventorySetups.isEmpty())
        {
            return;
        }

        if (client != null && client.isClientThread())
        {
            inventorySetups = dataManager.getSetups();
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.BANKMAIN)
        {
            bankOpen = true;
        }
    }

    @Override
    public void onWidgetClosed(WidgetClosed event)
    {
        super.onWidgetClosed(event);

        if (event.getGroupId() == InterfaceID.BANKMAIN)
        {
            bankOpen = false;
        }
    }

    @Subscribe
    protected void onClientTick(ClientTick clientTick)
    {
        if (client.getGameState() != GameState.LOGGED_IN || client.isMenuOpen())
            return;

        final boolean bankWidgetOpen = client.getWidget(InterfaceID.BANKMAIN, 0) != null;
        if (!bankOpen && bankWidgetOpen)
        {
            bankOpen = true;
        }
        else if (bankOpen && !bankWidgetOpen)
        {
            bankOpen = false;
        }

        if (!bankOpen)
            return;

        final Menu menu = client.getMenu();
        final MenuEntry[] menuEntries = menu.getMenuEntries();
        if (menuEntries == null || menuEntries.length == 0)
        {
            return;
        }

        final List<MenuEntry> entries = new ArrayList<>(Arrays.asList(menuEntries));
        boolean isSetupsMenu = false;

        for (MenuEntry entry : entries)
        {
            final String option = entry.getOption();
            if (option == null)
            {
                continue;
            }

            final String normalizedOption = normalizeMenuOption(option);
            if (normalizedOption.startsWith(INV_SETUPS_MENU_IDENTIFIER) || normalizedOption.startsWith(INV_SETUPS_MENU_IDENTIFIER_2))
            {
                isSetupsMenu = true;
                break;
            }
        }

        if (!isSetupsMenu)
            return;

        final Set<String> setupNames = getInventorySetupNames();
        if (setupNames == null || setupNames.isEmpty())
        {
            return;
        }

        final Menu parent = menu.createMenuEntry(-1)
                .setOption("GE Filters Setups")
                .setTarget("")
                .setType(MenuAction.RUNELITE)
                .createSubMenu();


        for (String setup : setupNames)
        {
            if (setupExceptions.contains(setup))
            {
                parent.createMenuEntry(-1)
                        .setOption("Include")
                        .setTarget(setup)
                        .setType(MenuAction.RUNELITE)
                        .onClick(removeSetupFromExceptions(setup));
            }
            else
            {
                parent.createMenuEntry(-1)
                        .setOption("Exclude")
                        .setTarget(setup)
                        .setType(MenuAction.RUNELITE)
                        .onClick(addSetupToExceptions(setup));
            }
        }
    }

    private void addInvSetupsFilterOptionResults()
    {
        ensureSetupsLoadedBestEffort();

        final ArrayList<GeSearch> setupFilters = new ArrayList<>();
        final Set<String> setupNames = getInventorySetupNames();

        if (setupNames == null || setupNames.isEmpty())
            return;

        for (String setup : setupNames)
        {
            if (setupExceptions.contains(setup))
                continue;

            setupFilters.add(new GeSearch(setup, (short) SPRITE_ID_MAIN));
        }

        setGESearchResults(getEmptySearchResults(setupFilters.size()));
        setSearchResultsHidden(true);

        clientThread.invokeLater(() -> {
            final List<GeSearchResultWidget> searchResultWidgets = getGeSearchResults();
            generateInvSetupsResults(setupFilters, searchResultWidgets);
            setSearchResultsHidden(false);
        });
    }

    private void generateInvSetupsResults(List<GeSearch> filters, List<GeSearchResultWidget> searchResults)
    {
        if (searchResults.isEmpty())
            return;

        int resultIndex = 0;
        final int resultSize = searchResults.size();

        for (GeSearch filter : filters)
        {
            if (resultIndex == resultSize)
                break;

            final String setupName = filter.getName();
            final GeSearchResultWidget searchResult = searchResults.get(resultIndex);

            searchResult.setTitleText(setupName);
            searchResult.setTooltipText(setupName);
            searchResult.setSpriteId(filter.getIconItemId());
            searchResult.setSpriteSize(22, 24);
            searchResult.setSpriteOffset(5, 2);

            searchResult.setOnOpListener((JavaScriptCallback)(e) ->
            {
                final String title = TITLE_MAIN + " - " + setupName;
                inventorySetupsFilter.setData(setupName);
                searchGE(inventorySetupsFilter.getSearchValue());
                setTitle(title);
            });

            resultIndex++;
        }
    }

    private void generateSetupResults(String setupName)
    {
        final String title = TITLE_MAIN + " - " + setupName;
        final InventorySetup setup = getInventorySetup(setupName);

        if (setup == null)
            return;

        final List<InventorySetupsItem> invItems = setup.getInventory();
        final List<InventorySetupsItem> equipmentItems = setup.getEquipment();
        final List<InventorySetupsItem> runePouchItems = setup.getRune_pouch();
        final List<InventorySetupsItem> boltPouchItems = setup.getBoltPouch();
        final List<InventorySetupsItem> quiverItems = setup.getQuiver();
        final Map<Integer, InventorySetupsItem> additionalItemsMap = setup.getAdditionalFilteredItems();
        final List<InventorySetupsItem> additionalFilteredItems = additionalItemsMap == null
            ? Collections.emptyList()
            : new ArrayList<>(additionalItemsMap.values());

        List<Short> itemIds = new ArrayList<>();

        if (config.enableInvSetupsEquipment() && equipmentItems != null)
            itemIds.addAll(getSetupItemIds(equipmentItems));

        if (config.enableInvSetupsInventory() && invItems != null)
            itemIds.addAll(getSetupItemIds(invItems));

        if (config.enableInvSetupsRunePouch() && runePouchItems != null)
            itemIds.addAll(getSetupItemIds(runePouchItems));

        if (config.enableInvSetupsBoltPouch() && boltPouchItems != null)
            itemIds.addAll(getSetupItemIds(boltPouchItems));

        if (config.enableInvSetupsQuiver() && quiverItems != null)
            itemIds.addAll(getSetupItemIds(quiverItems));

        if (config.enableInvSetupsAdditionalItems())
            itemIds.addAll(getSetupItemIds(additionalFilteredItems));

        setTitle(title);
        addInventorySetupItemResults(itemIds);
        saveSearchState(SEARCH_BASE_MAIN);
    }

    private List<GeSearchResultWidget> getGeSearchResults()
    {
        final List<GeSearchResultWidget> results = new ArrayList<>();
        final Widget searchResultsWidget = client.getWidget(WIDGET_ID_CHATBOX_GE_SEARCH_RESULTS);
        if (searchResultsWidget == null)
        {
            return results;
        }

        final Widget[] geSearchResultWidgets = searchResultsWidget.getDynamicChildren();
        if (geSearchResultWidgets == null)
        {
            return results;
        }

        for (int i = 0; i + 2 < geSearchResultWidgets.length; i += 3)
        {
            final Widget container = geSearchResultWidgets[i];
            final Widget title = geSearchResultWidgets[i + 1];
            final Widget icon = geSearchResultWidgets[i + 2];
            if (container == null || title == null || icon == null)
            {
                continue;
            }

            results.add(new GeSearchResultWidget(container, title, icon));
        }

        return results;
    }

    private Consumer<MenuEntry> addSetupToExceptions(String setup)
    {
        return e ->
        {
            if (setupExceptions.contains(setup))
                return;

            setupExceptions.add(setup);
            saveSetupExceptions();
        };
    }

    private Consumer<MenuEntry> removeSetupFromExceptions(String setup)
    {
        return e ->
        {
            if (!setupExceptions.contains(setup))
                return;

            setupExceptions.remove(setup);
            saveSetupExceptions();
        };
    }

    private void saveSetupExceptions()
    {
        final String[] setupExc = new String[setupExceptions.size()];
        setupExceptions.toArray(setupExc);
        final String json = gson.toJson(setupExc);
        configManager.setConfiguration(GEFiltersPlugin.CONFIG_GROUP_DATA, SETUPS_EXCEPTION_JSON_KEY, json);
    }

    private void loadSetupExceptions()
    {
        final String setupExceptionsJson = configManager.getConfiguration(GEFiltersPlugin.CONFIG_GROUP_DATA, SETUPS_EXCEPTION_JSON_KEY);
        if (setupExceptionsJson == null || setupExceptionsJson.isEmpty())
        {
            setupExceptions = new ArrayList<>();
        }
        else
        {
            try
            {
                final String[] setupExc = gson.fromJson(setupExceptionsJson, String[].class);
                setupExceptions = setupExc == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(setupExc));
            }
            catch (RuntimeException ignored)
            {
                setupExceptions = new ArrayList<>();
            }
        }
    }

    private short[] getEmptySearchResults(int size)
    {
        return new short[size];
    }

    private InventorySetup getInventorySetup(String name)
    {
        ensureSetupsLoadedBestEffort();

        if (name == null || inventorySetups == null || inventorySetups.isEmpty())
        {
            return null;
        }

        return inventorySetups.stream()
                .filter(Objects::nonNull)
                .filter(s -> name.equals(s.getName()))
                .findAny()
                .orElse(null);
    }

    private Set<String> getInventorySetupNames()
    {
        ensureSetupsLoadedBestEffort();

        if (inventorySetups == null || inventorySetups.isEmpty())
        {
            return Collections.emptySet();
        }

        return inventorySetups.stream()
                .filter(Objects::nonNull)
                .map(InventorySetup::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private List<Short> getSetupItemIds(List<InventorySetupsItem> items)
    {
        if (items == null || items.isEmpty())
        {
            return Collections.emptyList();
        }

        return items.stream()
                .filter(Objects::nonNull)
                .map(InventorySetupsItem::getId)
                .filter(id -> id > 0 && id <= MAX_STORED_ITEM_ID)
            .map(Integer::shortValue)
                .collect(Collectors.toList());
    }

    private void addInventorySetupItemResults(List<Short> itemIds)
    {
        final List<Short> finalItems = new ArrayList<>();
        final Set<Short> seenItems = new HashSet<>();

        for (Short id : itemIds)
        {
            if (id == null)
                continue;

            final int itemId = Short.toUnsignedInt(id);
            if (itemId == 0)
            {
                continue;
            }

            ItemComposition composition = client.getItemDefinition(itemId);
            ItemComposition unnotedComposition = null;

            final int notedId = composition.getLinkedNoteId();
            if (notedId > 0 && notedId <= MAX_STORED_ITEM_ID)
            {
                unnotedComposition = client.getItemDefinition(notedId);
            }

            final short shortItemId = (short) itemId;
            if (seenItems.contains(shortItemId) || (notedId > 0 && notedId <= MAX_STORED_ITEM_ID && seenItems.contains((short) notedId)))
            {
                continue;
            }

            if (composition.isTradeable())
            {
                finalItems.add(shortItemId);
                seenItems.add(shortItemId);
            }
            else if (unnotedComposition != null && unnotedComposition.isTradeable())
            {
                finalItems.add((short)notedId);
                seenItems.add((short) notedId);
            }
        }

        final short[] itemResultIds = FilterUtility.getPrimitiveShortArray(finalItems);
        setGESearchResults(itemResultIds);
    }

    private void loadUpdatedInventorySetups()
    {
        if (dataManager == null || clientThread == null)
        {
            return;
        }

        // Ensure this runs on the client thread, but do it immediately where possible so the
        // setup list/auto-selection is available on the same GE interaction.
        if (client != null && client.isClientThread())
        {
            inventorySetups = dataManager.getSetups();
            return;
        }

        clientThread.invoke(() -> inventorySetups = dataManager.getSetups());
    }

    private String normalizeMenuOption(String option)
    {
        return HTML_TAG_PATTERN.matcher(option).replaceAll("").trim().toLowerCase(Locale.ROOT);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged configChanged)
    {
        if (InventorySetupsDataLoader.CONFIG_GROUP.equals(configChanged.getGroup())) {
            loadUpdatedInventorySetups();
        }
    }

}
