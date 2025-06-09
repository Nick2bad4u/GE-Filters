package com.salverrs.GEFilters.Filters.Model;

import net.runelite.api.widgets.Widget;

public class GeSearchResultWidget {
    private short itemId;
    private Widget container;
    private Widget title;
    private Widget icon;

    public GeSearchResultWidget(Widget container, Widget title, Widget icon, short itemId)
    {
        this.container = container;
        this.title = title;
        this.icon = icon;
        this.itemId = itemId;
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
        icon.setOriginalX(icon.getOriginalX() + xOffset);
        icon.setOriginalY(icon.getOriginalY() + yOffset);
        icon.revalidate();
    }

    public void setSpriteSize(int width, int height)
    {
        icon.setOriginalWidth(width);
        icon.setWidthMode(0);

        icon.setOriginalHeight(height);
        icon.setWidthMode(0);

        icon.revalidate();
    }

    public void setItemIcon(short itemId)
    {
        icon.setItemId(itemId);
        icon.setSpriteId(itemId);
        icon.revalidate();
    }

    public void setOnOpListener(Object... args)
    {
        container.setOnOpListener(args);
    }
}
