package com.alejandro.mtoconfiguration.model.commons;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class SearchRequestDTO {
    private Map<String, Object> filters;
    private PageableDTO pageable;
}
