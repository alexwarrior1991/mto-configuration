package com.alejandro.mtoconfiguration.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InfrastructureUtils {

    @Autowired
    private HttpServletRequest request;
}
