package com.salverrs.GEFilters.Filters.Model;

import net.runelite.api.widgets.Widget;

public class GeSearchResultWidget {
    private final Widget container;
    private final Widget title;
    private final Widget icon;
    private final int baseIconOriginalX;
    private final int baseIconOriginalY;

    public GeSearchResultWidget(Widget container, Widget title, Widget icon)
    {
        this.container = container;
        this.title = title;
        this.icon = icon;
        this.baseIconOriginalX = icon.getOriginalX();
        this.baseIconOriginalY = icon.getOriginalY();
    }

    public void setTooltipText(String text)
    {
        container.setName("<col=ff9040>" + text + "</col>");
    }

    public void setTitleText(String text)
    {
        title.setText(text);
    }

    public void setSpriteId(short spriteId)
    {
        icon.setType(5);
        icon.setContentType(0);
        icon.setItemId(-1);
        icon.setModelId(-1);
        icon.setModelType(1);

        icon.setSpriteId(spriteId);
        icon.revalidate();
    }

    public void setSpriteOffset(int xOffset, int yOffset)
    {
        icon.setOriginalX(baseIconOriginalX + xOffset);
        icon.setOriginalY(baseIconOriginalY + yOffset);
        icon.revalidate();
    }

    public void setSpriteSize(int width, int height)
    {
        icon.setOriginalWidth(width);
        icon.setWidthMode(0);

        icon.setOriginalHeight(height);
        icon.setHeightMode(0);

        icon.revalidate();
    }

    public void setItemIcon(short itemId)
    {
        final int resolvedItemId = Short.toUnsignedInt(itemId);
        icon.setItemId(resolvedItemId);
        icon.setSpriteId(resolvedItemId);
        icon.revalidate();
    }

    public void setOnOpListener(Object... args)
    {
        container.setOnOpListener(args);
    }
}
