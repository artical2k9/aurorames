package com.mes.audit.envers;

public final class ServiceIdentityContext {

    private static final ThreadLocal<String> CURRENT_SERVICE = new ThreadLocal<>();

    private ServiceIdentityContext() {
    }

    public static void set(String serviceName) {
        CURRENT_SERVICE.set(serviceName);
    }

    public static String get() {
        return CURRENT_SERVICE.get();
    }

    public static void clear() {
        CURRENT_SERVICE.remove();
    }
}
