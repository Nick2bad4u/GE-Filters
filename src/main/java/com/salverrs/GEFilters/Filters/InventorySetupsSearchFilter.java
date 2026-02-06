package com.salverrs.GEFilters.Filters;

import com.google.gson.Gson;
import javax.inject.Inject;
import com.salverrs.GEFilters.Filters.Model.FilterOption;
import com.salverrs.GEFilters.Filters.Model.GeSearch;
import com.salverrs.GEFilters.Filters.Model.GeSearchResultWidget;
import com.salverrs.GEFilters.GEFiltersConfig;
import com.salverrs.GEFilters.GEFiltersPlugin;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import com.salverrs.GEFilters.Filters.Model.InventorySetups.*;
import com.salverrs.GEFilters.Filters.Model.InventorySetups.Serialization.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class InventorySetupsSearchFilter extends SearchFilter {

    private static final int SPRITE_ID_MAIN = SpriteID.TAB_EQUIPMENT;
    private static final String TITLE_MAIN = "Inventory Setups";
    private static final String SEARCH_BASE_MAIN = "inventory-setups";
    private static final String INV_SETUPS_MENU_IDENTIFIER = "Open setup";
    private static final String INV_SETUPS_MENU_IDENTIFIER_2 = "Open Section";
    private static final String SETUPS_EXCEPTION_JSON_KEY = "inventory-setups-exceptions";
    private static final int WIDGET_ID_CHATBOX_GE_SEARCH_RESULTS = InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS;
    private final GEFiltersConfig config;
    private FilterOption inventorySetupsFilter;
    private boolean bankOpen = false;
    private List<String> setupExceptions = new ArrayList<>();
    private Gson gson;
    private List<InventorySetup> inventorySetups = new ArrayList<>();
    private boolean initialLoad = true;

    @Inject
    private PluginManager pluginManager;

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
        this.gson = gson.newBuilder().registerTypeAdapter(long.class, new LongTypeAdapter()).create();
        this.gson = gson.newBuilder().registerTypeAdapter(InventorySetupItemSerializable.class, new InventorySetupItemSerializableTypeAdapter()).create();

        this.dataManager = new InventorySetupsDataLoader(configManager, gson);
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
                generateSetupResults((String)(option.getData()));
            }
            else
            {
                addInvSetupsFilterOptionResults();
            }
        }
    }

    @Nullable
    private String getActiveInventorySetupName()
    {
        if (pluginManager == null)
        {
            return null;
        }

        try
        {
            for (Plugin plugin : pluginManager.getPlugins())
            {
                if (!GEFiltersPlugin.INVENTORY_SETUPS_COMP_NAME.equals(plugin.getName()))
                {
                    continue;
                }

                // If the plugin isn't enabled, it can't have an active setup.
                if (!pluginManager.isPluginEnabled(plugin))
                {
                    return null;
                }

                // Inventory Setups keeps the active setup in its panel state.
                final Field panelField = plugin.getClass().getDeclaredField("panel");
                panelField.setAccessible(true);
                final Object panel = panelField.get(plugin);
                if (panel == null)
                {
                    return null;
                }

                final Method getCurrentSelectedSetup = panel.getClass().getMethod("getCurrentSelectedSetup");
                final Object selectedSetup = getCurrentSelectedSetup.invoke(panel);
                if (selectedSetup == null)
                {
                    return null;
                }

                final Method getName = selectedSetup.getClass().getMethod("getName");
                final Object nameObj = getName.invoke(selectedSetup);
                final String name = nameObj == null ? null : nameObj.toString();
                return (name == null || name.trim().isEmpty()) ? null : name;
            }
        }
        catch (Exception ignored)
        {
            // Best-effort only: Inventory Setups is an external plugin and implementation details can change.
            return null;
        }

        return null;
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

        if (!bankOpen)
            return;

        final Menu menu = client.getMenu();
        final List<MenuEntry> entries = new ArrayList<>(Arrays.asList(menu.getMenuEntries()));
        boolean isSetupsMenu = false;

        for (MenuEntry entry : entries)
        {
            final String option = entry.getOption();
            if (option.contains(INV_SETUPS_MENU_IDENTIFIER) || option.contains(INV_SETUPS_MENU_IDENTIFIER_2))
            {
                isSetupsMenu = true;
                break;
            }
        }

        if (!isSetupsMenu)
            return;

        final Set<String> setupNames = getInventorySetupNames();

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
        final ArrayList<GeSearch> setupFilters = new ArrayList<>();
        final Set<String> setupNames = getInventorySetupNames();

        if (setupNames == null || setupNames.isEmpty())
            return;

        for (String setup : setupNames)
        {
            if (setupExceptions.contains(setup))
                continue;

            setupFilters.add(new GeSearch(setup, (short)SpriteID.TAB_EQUIPMENT));
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
        final List<InventorySetupsItem> additionalFilteredItems = new ArrayList<>(setup.getAdditionalFilteredItems().values());

        List<Short> itemIds = new ArrayList<Short>();

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
        final Widget[] geSearchResultWidgets = Objects.requireNonNull(client.getWidget(WIDGET_ID_CHATBOX_GE_SEARCH_RESULTS)).getDynamicChildren();
        final Queue<Widget> widgetQueue = new LinkedList<Widget>();

        for (Widget w : geSearchResultWidgets)
        {
            widgetQueue.add(w);

            if (widgetQueue.size() == 3)
            {
                final Widget container = widgetQueue.remove();
                final Widget title = widgetQueue.remove();
                final Widget icon = widgetQueue.remove();
                final short itemId = (short)icon.getItemId();

                results.add(new GeSearchResultWidget(container, title, icon, itemId));
            }
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
            final String[] setupExc = gson.fromJson(setupExceptionsJson, String[].class);
            setupExceptions = new ArrayList<>(Arrays.asList(setupExc));
        }
    }

    private short[] getEmptySearchResults(int size)
    {
        return new short[size];
    }

    private InventorySetup getInventorySetup(String name)
    {
        return Objects.requireNonNull(inventorySetups).stream().filter(s -> s.getName().equals(name)).findAny().orElse(null);
    }

    private Set<String> getInventorySetupNames()
    {
        return Objects.requireNonNull(inventorySetups).stream()
                .map(InventorySetup::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private List<Short> getSetupItemIds(List<InventorySetupsItem> items)
    {
        return items.stream().map(i -> (short)i.getId()).collect(Collectors.toList());
    }

    private void addInventorySetupItemResults(List<Short> itemIds)
    {
        final List<Short> finalItems = new ArrayList<>();

        for (Short id : itemIds)
        {
            if (id == -1)
                continue;

            ItemComposition composition = client.getItemDefinition(id);
            ItemComposition unnotedComposition = null;

            final int notedId = composition.getLinkedNoteId();
            if (notedId != -1)
            {
                unnotedComposition = client.getItemDefinition(notedId);
            }

            if (finalItems.contains(id) || finalItems.contains((short)notedId))
                    continue;

            if (composition.isTradeable())
            {
                finalItems.add(id);
            }
            else if (unnotedComposition != null && unnotedComposition.isTradeable())
            {
                finalItems.add((short)notedId);
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
        clientThread.invoke(() -> inventorySetups = dataManager.getSetups());
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged configChanged)
    {
        if (configChanged.getGroup().equals(InventorySetupsDataLoader.CONFIG_GROUP)) {
            loadUpdatedInventorySetups();
        }
    }

}
