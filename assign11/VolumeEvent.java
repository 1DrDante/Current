package assign11;

/**
 * Represents a change in volume at a given time.
 */
public class VolumeEvent extends AudioEvent {
    private final int volume;

    public VolumeEvent(int trackNumber, int time, int volume) {
        super(trackNumber, time);
        this.volume = volume;
    }

    public int getVolume() {
        return volume;
    }
}
