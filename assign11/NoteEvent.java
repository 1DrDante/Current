package assign11;

/**
 * Represents a note event with pitch and duration.
 */
public class NoteEvent extends AudioEvent {
    private final int pitch;
    private final int duration;

    public NoteEvent(int trackNumber, int time, int pitch, int duration) {
        super(trackNumber, time);
        this.pitch = pitch;
        this.duration = duration;
    }

    public int getPitch() {
        return pitch;
    }

    public int getDuration() {
        return duration;
    }
}
