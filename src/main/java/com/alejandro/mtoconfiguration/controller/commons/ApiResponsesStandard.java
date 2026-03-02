package com.alejandro.mtoconfiguration.controller.commons;

import com.alejandro.mtoconfiguration.core.model.exception.DefaultErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponses({
        @ApiResponse(responseCode = ApiConstants.CODE_401, description = ApiConstants.DESC_401,
                content = @Content(schema = @Schema(implementation = DefaultErrorResponse.class))),
        @ApiResponse(responseCode = ApiConstants.CODE_500, description = ApiConstants.DESC_500,
                content = @Content(schema = @Schema(implementation = DefaultErrorResponse.class)))
})
public @interface ApiResponsesStandard {
}
