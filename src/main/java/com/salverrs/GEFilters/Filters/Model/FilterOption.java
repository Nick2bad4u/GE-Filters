package com.salverrs.GEFilters.Filters.Model;

import lombok.Getter;
import lombok.Setter;

@Getter
public class FilterOption
{
    private final String title;
    private final String searchValue;

    @Setter
    private Object data;

    public FilterOption(String title, String searchValue)
    {
        this.title = title;
        this.searchValue = searchValue;
    }
}
