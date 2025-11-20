package assign11;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Collections;

import javax.swing.JPanel;

/**
 * A TrackEditor is the interactive GUI component for drawing a sequence of
 * note events or volume changes in a track.
 * 
 * @author CS 1420 course staff and OpenAI ChatGPT
 * @version 2025-07-02
 */
public class TrackEditor extends JPanel implements MouseListener, MouseMotionListener {

    public static enum Mode { NOTE, VOLUME, COPY }
    private Mode mode;

    private int trackNumber;
    private SimpleSynthesizer synth;
    private ArrayList<AudioEvent> events;     // The AudioEvents for this track
    private ArrayList<NoteEvent> notesToCopy; // Stores notes during a copy operation

    private int columns, rows; // The number of columns and rows in the grid
    private boolean drawing;   // Set to true during drawing operations
    private int currentRow, currentColumn; // Used by drawing operations
    private int noteDuration;  // The duration of a note being drawn

    // For defining an area of the grid to copy
    private int copyFromRow, copyFromColumn; // Top and left of area
    private int copyToRow, copyToColumn;     // Bottom and right of area

    // This pitch range matches a piano. You can change these values if desired.
    private static final int lowestPitch = 21;
    private static final int highestPitch = 108;

    private final Color volumeColor = new Color(235, 235, 245);
    private final Color noteColor = new Color(100, 149, 237);
    private final Color copyColor = new Color(0, 180, 0, 100);

    /**
     * Create a new TrackEditor with the default configuration.
     * 
     * @param trackNumber assigned to this track in the midi system
     * @param synthesizer for making sounds
     * @param sequencer for sequencing note events
     */
    public TrackEditor(int trackNumber, int songLength, ArrayList<AudioEvent> events, SimpleSynthesizer synth) {
        columns = songLength;
        rows = highestPitch - lowestPitch + 1;

        this.trackNumber = trackNumber;
        this.synth = synth;
        this.events = events;
        notesToCopy = new ArrayList<NoteEvent>();
        drawing = false;
        mode = Mode.NOTE;

        setBackground(Color.WHITE);

        addMouseListener(this);
        addMouseMotionListener(this);
    }

    /**
     * Removes all events from the track.
     */
    public void clearTrack() {
        events.clear();
        repaint();
    }

    /**
     * Set the song duration in ticks.
     * 
     * @param songLength in ticks
     */
    public void setSongLength(int songLength) {
        columns = songLength;
        if (columns < 1)
            columns = 1;
        repaint();
    }

    /**
     * Set the editor to the specified mode.
     * 
     * @param mode - either Mode.NOTE, Mode.VOLUME, or Mode.COPY
     */
    public void setMode(Mode mode) {
        this.mode = mode;
        // Set volume back to default in case it was changed in volume mode
        synth.setVolume(trackNumber, 100);
    }

    /**
     * This method is called by the system when a component needs to be painted.
     * Which can be at one of three times: --when the component first appears --when
     * the size of the component changes (including resizing by the user) --when
     * repaint() is called
     * 
     * Partially overrides the paintComponent method of JPanel.
     * 
     * @param g -- graphics context onto which we can draw
     */
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        int previousVolume = 100; // the volume begins with value 100 by default
        int previousTime = 0; // the beginning of the song
        for (AudioEvent event : events) {
            if (event instanceof VolumeEvent) {
                int left = colToPixel(previousTime);
                int right = colToPixel(event.getTime());
                int top = rowToPixel(volumeToRow(previousVolume));
                int height = getHeight() - top;
                g.setColor(volumeColor);
                g.fillRect(left, top, right - left, height);

                previousVolume = ((VolumeEvent) event).getVolume();
                previousTime = event.getTime();
            }
        }
        int left = colToPixel(previousTime);
        int right = getWidth();
        int top = rowToPixel(volumeToRow(previousVolume));
        int height = getHeight() - top;
        g.setColor(volumeColor);
        g.fillRect(left, top, right - left, height);

        g.setColor(Color.BLACK);
        for (int r = 0; r <= rows; r++) {
            int y = rowToPixel(r);
            g.drawLine(0, y, getWidth(), y);
        }
        for (int c = 0; c <= columns; c++) {
            int x = colToPixel(c);
            g.drawLine(x, 0, x, getHeight());
        }

        for (int r = 0; r <= rows; r += 12) {
            int y = rowToPixel(r);
            g.fillRect(0, y, getWidth(), 2);
        }

        for (int c = 0; c <= columns; c += 4) {
            int x = colToPixel(c);
            g.fillRect(x, 0, 2, getHeight());
        }

        if (drawing) {
            if (mode == Mode.VOLUME) {
                g.setColor(volumeColor);
                int columnLeft = colToPixel(currentColumn);
                int columnRight = colToPixel(currentColumn + 1);
                int volumeTop = rowToPixel(currentRow);
                g.fillRect(columnLeft, volumeTop, columnRight - columnLeft, getHeight() - volumeTop);

            } else if (mode == Mode.NOTE && noteDuration > 0) {
                g.setColor(noteColor);
                int rowTop = rowToPixel(currentRow);
                int rowHeight = rowToPixel(currentRow + 1) - rowTop;
                int colLeft = colToPixel(currentColumn);
                int colWidth = colToPixel(currentColumn + noteDuration) - colLeft;
                g.fillRect(colLeft, rowTop, colWidth, rowHeight);

            } else if (mode == Mode.COPY) {
                g.setColor(copyColor);
                int topRow = Math.min(copyFromRow, copyToRow);
                int bottomRow = Math.max(copyFromRow, copyToRow);
                int leftCol = Math.min(copyFromColumn, copyToColumn);
                int rightCol = Math.max(copyFromColumn, copyToColumn);
                int rowTop = rowToPixel(topRow);
                int rowHeight = rowToPixel(bottomRow + 1) - rowTop;
                int colLeft = colToPixel(leftCol);
                int colWidth = colToPixel(rightCol + 1) - colLeft;
                g.fillRect(colLeft, rowTop, colWidth, rowHeight);

            }
        }

        g.setColor(noteColor);

        for (AudioEvent event : events) {
            if (event instanceof NoteEvent) {
                NoteEvent note = (NoteEvent) event;
                int rowIndex = pitchToRow(note.getPitch());
                int rowTop = rowToPixel(rowIndex);
                int rowHeight = rowToPixel(rowIndex + 1) - rowTop;
                int colLeft = colToPixel(note.getTime());
                int colWidth = colToPixel(note.getTime() + note.getDuration()) - colLeft;
                g.fillRect(colLeft, rowTop, colWidth, rowHeight);
            }
        }

    } // end of paintComponent

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            drawing = true;
            currentRow = pixelToRow(e.getY());
            currentColumn = pixelToCol(e.getX());
            noteDuration = 1;

            switch (mode) {
            case NOTE:
                synth.noteOn(trackNumber, rowToPitch(currentRow));
                noteDuration = 1;
                break;
            case VOLUME:
                int volume = rowToVolume(currentRow);
                synth.setVolume(trackNumber, volume);
                synth.noteOn(trackNumber, 60);
                break;
            case COPY:
                copyFromRow = currentRow;
                copyToRow = currentRow;
                copyFromColumn = currentColumn;
                copyToColumn = currentColumn;
                break;
            default:
                break;
            }
            repaint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (!drawing || e.getButton() != MouseEvent.BUTTON1) {
            return;
        }
        if (mode == Mode.NOTE) {
            synth.noteOff(trackNumber, rowToPitch(currentRow));
            if (noteDuration > 0) {
                events.add(new NoteEvent(trackNumber, currentColumn, rowToPitch(currentRow), noteDuration));
                Collections.sort(events);
            }
        } else if (mode == Mode.VOLUME) {
            synth.noteOff(trackNumber, 60);
            events.add(new VolumeEvent(trackNumber, currentColumn, rowToVolume(currentRow)));
            Collections.sort(events);
        } else if (mode == Mode.COPY) {
            notesToCopy.clear();
            int topRow = Math.min(copyFromRow, copyToRow);
            int bottomRow = Math.max(copyFromRow, copyToRow);
            int leftCol = Math.min(copyFromColumn, copyToColumn);
            int rightCol = Math.max(copyFromColumn, copyToColumn);
            for (AudioEvent event : events) {
                if (event instanceof NoteEvent) {
                    NoteEvent note = (NoteEvent) event;
                    int rowIndex = pitchToRow(note.getPitch());
                    if (note.getTime() >= leftCol && note.getTime() <= rightCol && rowIndex >= topRow
                            && rowIndex <= bottomRow) {
                        notesToCopy.add(note);
                    }
                }
            }
        }
        drawing = false;
        repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!drawing) {
            return;
        }
        int row = pixelToRow(e.getY());
        int col = pixelToCol(e.getX());
        if (mode == Mode.NOTE) {
            noteDuration = col - currentColumn + 1;
            if (row != currentRow) {
                synth.noteOff(trackNumber, rowToPitch(currentRow));
                synth.noteOn(trackNumber, rowToPitch(row));
                currentRow = row;
            }
        } else if (mode == Mode.VOLUME) {
            if (row != currentRow) {
                int volume = rowToVolume(row);
                synth.setVolume(trackNumber, volume);
                synth.noteOn(trackNumber, 60);
                currentRow = row;
            }
        } else if (mode == Mode.COPY) {
            copyToRow = row;
            copyToColumn = col;
        }
        repaint();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            return;
        }
        int row = pixelToRow(e.getY());
        int col = pixelToCol(e.getX());
        if (mode == Mode.NOTE) {
            for (int i = 0; i < events.size(); i++) {
                AudioEvent event = events.get(i);
                if (event instanceof NoteEvent) {
                    NoteEvent note = (NoteEvent) event;
                    if (note.getTime() == col && pitchToRow(note.getPitch()) == row) {
                        events.remove(i);
                        break;
                    }
                }
            }
        } else if (mode == Mode.COPY) {
            int timeOffset = col - copyFromColumn;
            int pitchOffset = row - copyFromRow;
            for (NoteEvent note : notesToCopy) {
                NoteEvent pasted = new NoteEvent(trackNumber, note.getTime() + timeOffset, note.getPitch() + pitchOffset,
                        note.getDuration());
                events.add(pasted);
            }
            Collections.sort(events);
        }
        repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        // Not used
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // Not used
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // Not used
    }
}

