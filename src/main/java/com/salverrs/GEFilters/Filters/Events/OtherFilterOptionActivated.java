package com.salverrs.GEFilters.Filters.Events;

import com.salverrs.GEFilters.Filters.SearchFilter;
import com.salverrs.GEFilters.Filters.Model.FilterOption;
import lombok.Getter;

@Getter
public class OtherFilterOptionActivated {
    private final SearchFilter searchFilter;
    private final FilterOption filterOption;

    public OtherFilterOptionActivated(SearchFilter filter, FilterOption option)
    {
        this.searchFilter = filter;
        this.filterOption = option;
    }
}
