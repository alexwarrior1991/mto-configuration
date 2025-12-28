package com.alejandro.mtoconfiguration.model.commons;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PageableDTO {
    Integer page;
    Integer size;
    List<String> sortBy;
    List<String> sortDirection;
}
