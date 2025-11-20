package assign11;

/**
 * Base class for audio events stored in a track.
 */
public abstract class AudioEvent implements Comparable<AudioEvent> {
    private final int trackNumber;
    private final int time;

    protected AudioEvent(int trackNumber, int time) {
        this.trackNumber = trackNumber;
        this.time = time;
    }

    /**
     * @return the track associated with the event.
     */
    public int getTrackNumber() {
        return trackNumber;
    }

    /**
     * @return the time for this event in ticks.
     */
    public int getTime() {
        return time;
    }

    @Override
    public int compareTo(AudioEvent other) {
        return Integer.compare(this.time, other.time);
    }
}
