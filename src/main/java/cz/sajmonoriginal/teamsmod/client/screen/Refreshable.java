package cz.sajmonoriginal.teamsmod.client.screen;

/** Marker for our screens that can be re-initialised when the underlying team data changes. */
public interface Refreshable {
    void refresh();
}
