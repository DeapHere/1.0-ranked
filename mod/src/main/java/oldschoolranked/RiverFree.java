package oldschoolranked;

/** The river layer of a match world: no rivers within RADIUS blocks of the spawn search's starting point. */
public interface RiverFree {
    int RADIUS = 256;

    void ranked$setRiverFreeCenter(int blockX, int blockZ);
}
