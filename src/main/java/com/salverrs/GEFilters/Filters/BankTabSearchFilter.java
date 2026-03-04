package com.salverrs.GEFilters.Filters;

import com.google.common.base.MoreObjects;
import com.salverrs.GEFilters.Filters.Model.FilterOption;
import com.salverrs.GEFilters.Filters.Model.GeSearch;
import com.salverrs.GEFilters.Filters.Model.GeSearchResultWidget;
import com.salverrs.GEFilters.GEFiltersPlugin;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static net.runelite.http.api.RuneLiteAPI.GSON;

public class BankTabSearchFilter extends SearchFilter {

    private static final int SPRITE_ID_MAIN = 1453; // SpriteID.MAP_ICON_BANK
    private static final int FALLBACK_TAG_ICON_ITEM_ID = 952; // ItemID.SPADE
    private static final String TITLE_MAIN = "Bank Tags";
    private static final String SEARCH_BASE_MAIN = "bank-tags";
    private static final String TAG_TAB_MENU_IDENTIFIER = "export tag tab";
    private static final String TAG_EXCEPTION_JSON_KEY = "bank-tags-exceptions";
    private static final int WIDGET_ID_CHATBOX_GE_SEARCH_RESULTS = InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private boolean bankOpen = false;
    private FilterOption bankTabFilter;
    private List<String> tagExceptions = new ArrayList<>();

    @Override
    protected void onFilterInitialising()
    {
        bankTabFilter = new FilterOption(TITLE_MAIN, SEARCH_BASE_MAIN);
        setFilterOptions(bankTabFilter);
        setIconSprite(SPRITE_ID_MAIN, -1);
    }

    @Override
    protected void onFilterStarted()
    {
        loadTagExceptions();
    }

    @Override
    protected void onFilterEnabled(FilterOption option)
    {
        if (option == bankTabFilter)
        {
            addBankTabFilterOptionResults();
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

        String targetFormatted = null;
        String targetTag = null;
        boolean isTagMenu = false;

        for (MenuEntry entry : entries)
        {
            final String option = entry.getOption();
            if (option == null)
            {
                continue;
            }

            final String normalizedOption = normalizeMenuOption(option);
            if (normalizedOption.startsWith(TAG_TAB_MENU_IDENTIFIER))
            {
                final String entryTarget = entry.getTarget();
                final String tagName = Text.removeTags(entryTarget == null ? "" : entryTarget).replace("\u00a0"," ");
                isTagMenu = true;
                targetFormatted = entryTarget;
                targetTag = tagName;
                break;
            }
        }

        if (!isTagMenu)
            return;

        if (targetTag == null || targetTag.isEmpty())
        {
            return;
        }

        if (tagExceptions.contains(targetTag))
        {
            menu.createMenuEntry(-1)
                    .setOption("Include on GE Filters")
                    .setTarget(targetFormatted)
                    .setType(MenuAction.RUNELITE)
                    .onClick(removeTagFromExceptions(targetTag));
        }
        else
        {
            menu.createMenuEntry(-1)
                    .setOption("Exclude from GE Filters")
                    .setTarget(targetFormatted)
                    .setType(MenuAction.RUNELITE)
                    .onClick(addTagToExceptions(targetTag));
        }

    }

    private void addBankTabFilterOptionResults()
    {
        final ArrayList<GeSearch> tagFilters = new ArrayList<>();
        final List<String> tagNames = Text.fromCSV(MoreObjects.firstNonNull(configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TABS_CONFIG), ""));

        for (String tag : tagNames)
        {
            if (tagExceptions.contains(tag))
                continue;

            String iconItemId = configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_ICON_PREFIX + tag);
            iconItemId = iconItemId == null ? Integer.toString(FALLBACK_TAG_ICON_ITEM_ID) : iconItemId;

            short iconId = (short) FALLBACK_TAG_ICON_ITEM_ID;
            try
            {
                final int parsedIconId = Integer.parseInt(iconItemId.trim());
                if (parsedIconId > 0 && parsedIconId <= 0xFFFF)
                {
                    iconId = (short) parsedIconId;
                }
            }
            catch (NumberFormatException ignored)
            {
                // Keep fallback spade icon for malformed persisted icon ids.
            }

            tagFilters.add(new GeSearch(tag, iconId));
        }

        setGESearchResults(getEmptySearchResults(tagFilters.size()));
        setSearchResultsHidden(true);

        clientThread.invokeLater(() -> {
            final List<GeSearchResultWidget> searchResultWidgets = getGeSearchResults();
            generateBankTabResults(tagFilters, searchResultWidgets);
            setSearchResultsHidden(false);
        });
    }

    private void generateBankTabResults(List<GeSearch> filters, List<GeSearchResultWidget> searchResults)
    {
        if (searchResults.isEmpty())
            return;

        int resultIndex = 0;
        final int resultSize = searchResults.size();

        for (GeSearch filter : filters)
        {
            if (resultIndex == resultSize)
                break;

            final String search = filter.getName();
            final GeSearchResultWidget searchResult = searchResults.get(resultIndex);

            searchResult.setTitleText(search);
            searchResult.setTooltipText(search);
            searchResult.setItemIcon(filter.getIconItemId());
            searchResult.setOnOpListener((JavaScriptCallback)(e) ->
            {
                final String title = TITLE_MAIN + " - " + search;
                final String searchVal = BankTagsPlugin.TAG_SEARCH + search;

                searchGE(searchVal);
                setTitle(title);
                saveSearchState(searchVal);
            });

            resultIndex++;
        }
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

    private Consumer<MenuEntry> addTagToExceptions(String tag)
    {
        return e ->
        {
            if (tagExceptions.contains(tag))
                return;

            tagExceptions.add(tag);
            saveTagExceptions();
        };
    }

    private Consumer<MenuEntry> removeTagFromExceptions(String tag)
    {
        return e ->
        {
            if (!tagExceptions.contains(tag))
                return;

            tagExceptions.remove(tag);
            saveTagExceptions();
        };
    }

    private void saveTagExceptions()
    {
        final String[] tagExc = new String[tagExceptions.size()];
        tagExceptions.toArray(tagExc);
        final String json = GSON.toJson(tagExc);
        configManager.setConfiguration(GEFiltersPlugin.CONFIG_GROUP_DATA, TAG_EXCEPTION_JSON_KEY, json);
    }

    private void loadTagExceptions()
    {
        final String tagExceptionsJson = configManager.getConfiguration(GEFiltersPlugin.CONFIG_GROUP_DATA, TAG_EXCEPTION_JSON_KEY);
        if (tagExceptionsJson == null || tagExceptionsJson.isEmpty())
        {
            tagExceptions = new ArrayList<>();
        }
        else
        {
            try
            {
                final String[] tagExc = GSON.fromJson(tagExceptionsJson, String[].class);
                tagExceptions = tagExc == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(tagExc));
            }
            catch (RuntimeException ignored)
            {
                tagExceptions = new ArrayList<>();
            }
        }
    }

    private String normalizeMenuOption(String option)
    {
        return HTML_TAG_PATTERN.matcher(option).replaceAll("").trim().toLowerCase(Locale.ROOT);
    }

    private short[] getEmptySearchResults(int size)
    {
        return new short[size];
    }
}
