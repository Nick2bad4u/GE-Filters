package com.salverrs.GEFilters.Filters.Model;

import lombok.Getter;

@Getter
public class GeSearch {
    private final String name;
    private final short iconItemId;

    public GeSearch(String name, short iconItemId)
    {
        this.name = name;
        this.iconItemId = iconItemId;
    }
}
