package com.alejandro.mtoconfiguration.configuration.security;

public final class JwtClaimNames {

    private JwtClaimNames() {
    }

    public static final String REALM_ACCESS = "realm_access";
    public static final String RESOURCE_ACCESS = "resource_access";
    public static final String ROLES = "roles";
    public static final String SCOPE = "scope";
    public static final String AUTHORIZATION = "authorization";
    public static final String PERMISSIONS = "permissions";
    public static final String AUDIENCE = "aud";
    public static final String PREFERRED_USERNAME = "preferred_username";
    public static final String EMAIL = "email";
    public static final String NAME = "name";
}
