package com.alejandro.mtoconfiguration.mapper.commons;

import org.mapstruct.Mapping;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.CLASS)
@Mapping(target = "id", ignore = true)
@Mapping(target = "createDate", ignore = true)
@Mapping(target = "createUser", ignore = true)
@Mapping(target = "versionDate", ignore = true)
@Mapping(target = "versionUser", ignore = true)
@Mapping(target = "versionNumber", ignore = true)
@Mapping(target = "versionIncremented", ignore = true)
@Mapping(target = "dirty", ignore = true)
public @interface ToEntityIgnoreAudit {
}
