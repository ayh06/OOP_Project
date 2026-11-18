import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SimulationClock {

    private static int currentMinute = 0;

    private static LocalDateTime startDateTime = null;

    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Get current simulation minute
    public static int getCurrentMinute() {
        return currentMinute;
    }

    // Set starting date and time
    public static void setStartDateTime(LocalDateTime dateTime) {

        if (dateTime == null) {
            throw new IllegalArgumentException("Invalid date and time");
        }

        if (startDateTime != null) {
            throw new IllegalStateException("Clock already initialized");
        }

        startDateTime = dateTime;
    }

    // Check if clock is initialized
    public static boolean isInitialized() {
        return startDateTime != null;
    }

    // Get starting date and time
    public static LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    // Get current date and time
    public static LocalDateTime getCurrentDateTime() {

        if (!isInitialized()) {
            throw new IllegalStateException("Clock is not initialized");
        }

        return startDateTime.plusMinutes(currentMinute);
    }

    // Display date and time
    public static String getFormattedTime() {

        if (!isInitialized()) {
            return "Not initialized";
        }

        return getCurrentDateTime().format(formatter);
    }

    // Advance simulation by one minute
    public static void advanceMinute() {

        if (!isInitialized()) {
            throw new IllegalStateException("Set the date and time first");
        }

        currentMinute = Math.addExact(currentMinute, 1);
    }

    // Restore saved simulation minute
    static void restoreMinute(int savedMinute) {

        if (savedMinute < 0) {
            throw new IllegalArgumentException("Saved time cannot be negative");
        }

        currentMinute = savedMinute;
    }

    // Restore saved starting date and time
    static void restoreStartDateTime(LocalDateTime savedDateTime) {
        startDateTime = savedDateTime;
    }

    // Display simulation time
    @Override
    public String toString() {
        return getFormattedTime();
    }
}