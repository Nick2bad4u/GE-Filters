package com.salverrs.GEFilters.Filters;

import com.salverrs.GEFilters.Filters.Events.OtherFilterOptionActivated;
import com.salverrs.GEFilters.Filters.Model.FilterOption;
import com.salverrs.GEFilters.Filters.Model.SearchState;
import com.salverrs.GEFilters.GEFiltersConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.ScriptEvent;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.GrandExchangeSearched;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.JagexColors;
import javax.inject.Inject;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Heavily inspired by Quest helper's quest item filter - Credit to Zoinkwiz

@Slf4j
public abstract class SearchFilter
{
    public static final int ICON_SIZE = 20;
    public static final int WIDE_BUTTON_WIDTH = 56;
    private static final int SPRITE_UNKNOWN_BUTTON_SQUARE_SMALL = 195;
    private static final int SPRITE_UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED = 196;
    private static final String CLEAR_FILTER_OPTION = "Clear Filter";
    private static final String QUEST_HELPER_COMP_WIDGET_NAME = "quest helper";
    private static final String QUEST_HELPER_COMP_NAME = "Quest Helper";
    private static final String QUEST_HELPER_FILTER_OPTION = "View missing items";
    private static final int FILTER_TOGGLE_SOUND_ID = SoundEffectID.UI_BOOP;
    private static final int KEY_PRESS_SCRIPT_ID = 905;
    private static final int ICON_BG_SIZE_OFFSET = 6;
    private static final int ICON_BG_POS_OFFSET = 3;
    private static final int GE_SEARCH_MODE = 14;
    private static final int WIDGET_ID_CHATBOX_GE_SEARCH_RESULTS = InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS;
    private static final int WIDGET_ID_CHATBOX_CONTAINER = InterfaceID.Chatbox.MES_LAYER;
    private static final int WIDGET_ID_CHATBOX_TITLE = InterfaceID.Chatbox.MES_TEXT;
    private static final int WIDGET_ID_CHATBOX_FULL_INPUT = InterfaceID.Chatbox.MES_TEXT2;
    private static int enabledFilterCount;
    private static SearchFilter activeFilter;
    private boolean qhEnabled;
    private Widget container;
    private Widget iconWidget;
    private Widget backgroundWidget;
    private Widget titleWidget;
    private Widget searchBoxWidget;
    private FilterOption lastOptionActivated;
    private SearchState lastSearchResults;
    private List<String> filterTitles;
    private String currentTitle;
    private int iconSpriteId;
    private int iconSpriteSizeOffset;
    private boolean filterEnabled;
    private short[] lastGeSearchResultIds = new short[0];

    protected boolean ready;

    private Map<String, FilterOption> filterTitleMap;
    private Map<String, FilterOption> filterSearchMap;

    @Inject
    protected ConfigManager configManager;
    @Inject
    protected Client client;
    @Inject
    protected ClientThread clientThread;
    @Inject
    private EventBus eventBus;
    @Inject
    private PluginManager pluginManager;
    @Inject
    private GEFiltersConfig config;

    public void start(int xOffset, int yOffset)
    {
        if (isChatInputHidden())
        {
            return;
        }

        if (!ready)
        {
            onFilterInitialising();
        }

        onFilterStarted();

        checkQuestHelperState();
        if (!createWidgets(xOffset, yOffset))
        {
            return;
        }
        refreshFilterMenuOptions(false);
        handleReactivation();

        ready = true;
    }

    public void stop()
    {
        container = null;
        trySetHidden(titleWidget, true);
        trySetHidden(iconWidget, true);
        trySetHidden(backgroundWidget, true);

        if (filterEnabled)
        {
            disableFilter(true);
        }

        if (activeFilter == this)
        {
            activeFilter = null;
        }

        // When the GE is closed (or the plugin hides filters), stop responding to events.
        // This allows the plugin to keep subscribers registered without causing stale widgets
        // or consuming events in other interfaces which reuse the GE chatbox search.
        ready = false;
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        if (event.getGroupId() == InterfaceID.GE_OFFERS)
        {
            disableFilter(true);
        }
    }

    @Subscribe
    protected void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!ready)
            return;

        final String optionClicked = event.getMenuOption();
        if (optionClicked == null)
            return;

        if (optionClicked.contains(QUEST_HELPER_FILTER_OPTION))
        {
            disableFilter(true);
            return;
        }

        final Widget widget = event.getWidget();
        final boolean widgetMatches = widget == backgroundWidget || widget == iconWidget;
        if (!widgetMatches)
        {
            return;
        }

        final boolean knownOption = CLEAR_FILTER_OPTION.equals(optionClicked) || filterTitleMap.containsKey(optionClicked);
        if (!knownOption)
            return;

        if (CLEAR_FILTER_OPTION.equals(optionClicked))
        {
            disableFilter(true);
        }
        else
        {
            resolveQuestHelperFilterState();
            FilterOption option = filterTitleMap.get(optionClicked);
            if (option == null)
                return;

            enableFilter(option, false, true);
        }

        client.playSoundEffect(FILTER_TOGGLE_SOUND_ID);
    }

    @Subscribe
    protected void onGrandExchangeSearched(GrandExchangeSearched event)
    {
        if (!ready)
            return;

        final String input = client.getVarcStrValue(VarClientID.MESLAYERINPUT);
        if (!filterSearchMap.containsKey(input))
            return;

        final FilterOption option = filterSearchMap.get(input);

        event.consume();
        onFilterEnabled(option);
    }

    @Subscribe
    protected void onOtherFilterOptionActivated(OtherFilterOptionActivated event)
    {
        if (!ready)
            return;

        if (event.getSearchFilter() == this && event.getFilterOption() == lastOptionActivated)
            return;

        if (filterEnabled)
        {
            disableFilter(false);
        }
    }

    @Subscribe
    private void onScriptPreFired(ScriptPreFired event)
    {
        if (!ready || !filterEnabled || !config.keyPressOverridesFilter())
            return;

        if (event.getScriptId() != KEY_PRESS_SCRIPT_ID)
            return;

        if (!isItemSearchInput())
            return;

        final ScriptEvent scriptEvent = event.getScriptEvent();
        final int typedKeyChar = scriptEvent.getTypedKeyChar();

        if (typedKeyChar == 0)
            return;

        final String character = Character.toString((char)typedKeyChar);
        disableFilter(true);
        searchGE(character, false);
    }

    protected abstract void onFilterInitialising();

    protected abstract void onFilterStarted();

    protected abstract void onFilterEnabled(FilterOption option);

    protected void searchGE(String searchTerm)
    {
        searchGE(searchTerm, true);
    }

    protected void searchGE(String searchTerm, boolean hideSearch)
    {
        client.setVarcStrValue(VarClientID.MESLAYERINPUT, searchTerm);
        client.setVarcIntValue(VarClientID.MESLAYERMODE, GE_SEARCH_MODE);
        forceUpdateSearch(hideSearch);
    }

    protected void setGESearchResults(short[] itemIds)
    {
        lastGeSearchResultIds = itemIds == null ? new short[0] : itemIds.clone();
        client.setGeSearchResultIndex(0);
        client.setGeSearchResultCount(lastGeSearchResultIds.length);
        client.setGeSearchResultIds(lastGeSearchResultIds);
    }

    protected int resolveActiveFilterItemIdFromRow(int rowIndex)
    {
        final short[] activeResultIds = getActiveFilterGeSearchResultIdsSnapshot();
        if (activeResultIds.length == 0 || rowIndex < 0)
        {
            return -1;
        }

        // Depending on widget context, param0 can represent direct row index or child-index spacing.
        final int[] candidateIndexes = new int[]
                {
                        rowIndex,
                        rowIndex / 3,
                        (rowIndex - 1) / 3,
                        (rowIndex + 1) / 3
                };

        for (int candidate : candidateIndexes)
        {
            if (candidate >= 0 && candidate < activeResultIds.length)
            {
                return Short.toUnsignedInt(activeResultIds[candidate]);
            }
        }

        return -1;
    }

    private short[] getGeSearchResultIdsSnapshot()
    {
        return lastGeSearchResultIds.clone();
    }

    protected static short[] getActiveFilterGeSearchResultIdsSnapshot()
    {
        return activeFilter == null ? new short[0] : activeFilter.getGeSearchResultIdsSnapshot();
    }

    protected void setTitle(String title)
    {
        Color titleColour = config.filterTitleColour();
        final int r = titleColour.getRed();
        final int g = titleColour.getGreen();
        final int b = titleColour.getBlue();
        final String hexColor = String.format("%02x%02x%02x", r, g, b);

        currentTitle = title;
        titleWidget.setText("<col=" + hexColor + ">" + currentTitle + "</col>");
    }

    protected void setFilterOptions(FilterOption ...options)
    {
        filterTitles = new ArrayList<>();
        filterTitleMap = new HashMap<>();
        filterSearchMap = new HashMap<>();

        for (FilterOption option : options)
        {
            final String title = option.getTitle();
            filterTitles.add(title);
            filterTitleMap.put(title, option);
            filterSearchMap.put(option.getSearchValue(), option);
        }

    }

    protected final void autoEnableFilter(FilterOption option)
    {
        if (!ready || option == null)
            return;

        enableFilter(option, false, true);
    }

    public final void autoEnablePrimaryFilterOption()
    {
        if (filterTitles == null || filterTitles.isEmpty())
            return;

        autoEnableFilter(filterTitleMap.get(filterTitles.get(0)));
    }

    protected void setIconSprite(int spriteId, int sizeOffset)
    {
        iconSpriteId = spriteId;
        iconSpriteSizeOffset = sizeOffset;
    }

    protected void saveSearchState(String search)
    {
        lastSearchResults = new SearchState(currentTitle, search);
    }

    protected boolean hasPreviousSearchState()
    {
        return lastSearchResults != null;
    }

    protected void loadPreviousSearchState()
    {
        if (lastSearchResults == null)
            return;

        final String title = lastSearchResults.getTitle();
        final String searchVal = lastSearchResults.getSearchValue();

        setTitle(title);

        if (searchVal != null)
        {
            searchGE(searchVal);
        }
    }

    protected void resetPreviousSearchState()
    {
        lastSearchResults = null;
    }

    protected void forceUpdateSearch(boolean hideSearchBox)
    {
        clientThread.invokeLater(() ->
        {
            if (searchBoxWidget == null)
                return;

            final Object[] scriptArgs = searchBoxWidget.getOnKeyListener();
            if (scriptArgs == null)
                return;

            client.runScript(scriptArgs);
            searchBoxWidget.setHidden(hideSearchBox);
        });
    }

    protected void setSearchResultsHidden(boolean hidden)
    {
        final Widget resultsContainer = client.getWidget(WIDGET_ID_CHATBOX_GE_SEARCH_RESULTS);
        if (resultsContainer != null)
        {
            resultsContainer.setHidden(hidden);
        }
    }

    public static boolean isAnyFilterEnabled()
    {
        return enabledFilterCount > 0;
    }

    public static int getConfiguredButtonWidth(GEFiltersConfig config)
    {
        return config != null && config.filterButtonsBothSides() ? WIDE_BUTTON_WIDTH : ICON_SIZE;
    }

    private void enableFilter(FilterOption option, boolean silent, boolean clearData)
    {
        if (!ready)
            return;

        if (option == null)
        {
            return;
        }

        final boolean wasEnabled = filterEnabled;
        filterEnabled = true;
        activeFilter = this;
        if (!wasEnabled)
        {
            enabledFilterCount++;
        }

        refreshFilterMenuOptions(true);
        setWidgetActivationState(true, true);

        if (!silent)
        {
            setTitle(option.getTitle());
            searchGE(option.getSearchValue());
        }

        if (clearData)
        {
            option.setData(null);
        }

        lastOptionActivated = option;
        eventBus.post(new OtherFilterOptionActivated(this, option));
    }

    private void disableFilter(boolean clearSearch)
    {
        if (!ready || !filterEnabled)
            return;

        filterEnabled = false;
        if (activeFilter == this)
        {
            activeFilter = null;
        }
        if (enabledFilterCount > 0)
        {
            enabledFilterCount--;
        }

        refreshFilterMenuOptions(false);
        resetPreviousSearchState();

        filterTitleMap.values().forEach(f -> f.setData(null));

        clientThread.invokeLater(() -> {
            setWidgetActivationState(false, true);
        });

        if (clearSearch)
        {
            client.setVarcStrValue(VarClientID.MESLAYERINPUT, "");
            client.setVarcIntValue(VarClientID.MESLAYERMODE, GE_SEARCH_MODE);
            forceUpdateSearch(false);
        }
    }

    private void handleReactivation()
    {
        if (filterEnabled)
        {
            if (hasPreviousSearchState())
            {
                enableFilter(lastOptionActivated, true, false);
                loadPreviousSearchState();
            }
            else
            {
                enableFilter(lastOptionActivated, false, false);
            }
        }
        else
        {
            resetPreviousSearchState();
        }
    }

    private void setWidgetActivationState(boolean filterEnabled, boolean hideSearchBox)
    {
        if (backgroundWidget != null)
        {
            backgroundWidget.setSpriteId(filterEnabled ? SPRITE_UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED : SPRITE_UNKNOWN_BUTTON_SQUARE_SMALL);
            backgroundWidget.revalidate();
        }

        if (searchBoxWidget != null)
        {
            searchBoxWidget.setHidden(hideSearchBox);
        }

        if (titleWidget != null)
        {
            titleWidget.setHidden(!filterEnabled);
        }
    }

    private void refreshFilterMenuOptions(boolean showClearOption)
    {
        clearFilterOptions();

        if (showClearOption)
        {
            setFilterWidgetAction(0, CLEAR_FILTER_OPTION);
        }

        int i = showClearOption ? 1 : 0;
        for (String optionTitle : filterTitles)
        {
            setFilterWidgetAction(i, optionTitle);
            i++;
        }
    }

    private void setFilterWidgetAction(int index, String action)
    {
        if (backgroundWidget != null)
        {
            backgroundWidget.setAction(index, action);
        }

        if (iconWidget != null)
        {
            iconWidget.setAction(index, action);
        }

    }

    private void clearFilterOptions()
    {
        for (int i = 0; i <= filterTitleMap.size(); i++)
        {
            setFilterWidgetAction(i, null);
        }
    }

    private void checkQuestHelperState()
    {
        final Collection<Plugin> plugins = pluginManager.getPlugins();
        for (Plugin plugin : plugins)
        {
            final String name = plugin.getName();
            if (name.equals(QUEST_HELPER_COMP_NAME))
            {
                qhEnabled = pluginManager.isPluginEnabled(plugin);
                return;
            }
        }

        qhEnabled = false;
    }

    private void resolveQuestHelperFilterState()
    {
        if (!qhEnabled)
            return;

        final Widget[] children = container.getChildren();
        if (children == null)
            return;

        Widget qhIcon = null;

        for (int i = 0; i < children.length; i++)
        {
            final Widget child = children[i];
            if (child != null && QUEST_HELPER_COMP_WIDGET_NAME.equals(child.getName()))
            {
                qhIcon = child;
                break;
            }
        }

        if (qhIcon == null || qhIcon.getSpriteId() != SPRITE_UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED)
            return;

        final Object[] filterArgs = qhIcon.getOnOpListener();
        if (filterArgs == null)
            return;

        client.runScript(filterArgs);
    }

    private boolean isChatInputHidden()
    {
        final Widget widget = client.getWidget(WIDGET_ID_CHATBOX_CONTAINER);
        return widget == null || widget.isHidden();
    }

    private boolean isItemSearchInput() // Search title is hidden on search input but not for quantity inputs
    {
        if (isChatInputHidden())
            return false;

        final Widget title = client.getWidget(WIDGET_ID_CHATBOX_TITLE);
        return title != null && title.isHidden();
    }

    private boolean createWidgets(int xOffset, int yOffset)
    {
        container = client.getWidget(WIDGET_ID_CHATBOX_CONTAINER);
        if (container == null)
        {
            return false;
        }

        searchBoxWidget = client.getWidget(WIDGET_ID_CHATBOX_FULL_INPUT);
        titleWidget = createTitleWidget();
        final int buttonWidth = getConfiguredButtonWidth(config);
        final int iconSize = ICON_SIZE - ICON_BG_SIZE_OFFSET + iconSpriteSizeOffset;
        final int iconX = xOffset + ((buttonWidth - iconSize) / 2);
        final int iconY = yOffset + ICON_BG_POS_OFFSET - (iconSpriteSizeOffset / 2);

        backgroundWidget = createGraphicWidget(SPRITE_UNKNOWN_BUTTON_SQUARE_SMALL, buttonWidth, ICON_SIZE, xOffset, yOffset);
        iconWidget = createGraphicWidget(
                iconSpriteId,
                iconSize, iconSize,
                iconX, iconY);

        return true;
    }

    private Widget createGraphicWidget(int spriteId, int width, int height, int x, int y)
    {
        final Widget widget = container.createChild(-1, WidgetType.GRAPHIC);

        widget.setOriginalX(x);
        widget.setOriginalY(y);
        widget.setOriginalWidth(width);
        widget.setOriginalHeight(height);

        widget.setSpriteId(spriteId);
        widget.setOnOpListener((JavaScriptCallback)e -> { });
        widget.setHasListener(true);
        widget.revalidate();

        return widget;
    }

    private Widget createTitleWidget()
    {
        final Widget chatBoxWidget = client.getWidget(WIDGET_ID_CHATBOX_FULL_INPUT);
        final Widget widget = container.createChild(-1, WidgetType.TEXT);

        if (chatBoxWidget == null)
            return widget;

        widget.setOriginalWidth(chatBoxWidget.getWidth());
        widget.setOriginalHeight(chatBoxWidget.getHeight());
        widget.setOriginalX(0);
        widget.setOriginalY(0);

        widget.setTextShadowed(false);
        widget.setXTextAlignment(1);
        widget.setYTextAlignment(1);
        widget.setFontId(FontID.BOLD_12);
        widget.setTextColor(JagexColors.CHAT_GAME_EXAMINE_TEXT_OPAQUE_BACKGROUND.getRGB());

        widget.setHidden(true);
        widget.revalidate();

        return widget;
    }

    private void trySetHidden(Widget widget, boolean hidden)
    {
        if (widget != null)
        {
            widget.setHidden(hidden);
        }
    }

}
