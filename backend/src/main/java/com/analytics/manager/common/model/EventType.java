package com.analytics.manager.common.model;

public enum EventType {
    PAGE_VIEW,
    LOGIN,
    PRODUCT_VIEW,
    SEARCH,
    PURCHASE,
    ERROR;

    public static boolean isValid(String name) {
        if (name == null) return false;
        try {
            valueOf(name.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
