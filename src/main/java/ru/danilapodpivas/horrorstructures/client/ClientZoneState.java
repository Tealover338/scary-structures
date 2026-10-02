package ru.danilapodpivas.horrorstructures.client;

public final class ClientZoneState {
    private static String zone = "";
    private static float park;
    private static float labyrinth;
    private static float circus;

    public static void setZone(String newZone) {
        zone = newZone == null ? "" : newZone;
    }

    public static void tick() {
        park = approach(park, zone.equals("abandoned_park") ? 1.0f : 0.0f);
        labyrinth = approach(labyrinth, zone.equals("labyrinth") ? 1.0f : 0.0f);
        circus = approach(circus, zone.equals("circus") ? 1.0f : 0.0f);
    }

    private static float approach(float value, float target) {
        return value < target ? Math.min(target, value + 0.025f) : Math.max(target, value - 0.025f);
    }

    public static float park() { return park; }
    public static float labyrinth() { return labyrinth; }
    public static float circus() { return circus; }
    private ClientZoneState() {}
}
