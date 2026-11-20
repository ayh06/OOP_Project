import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;

/** Six UTF-8 text files, using escaped tab-separated fields. No extra libraries. */
public class FileStateStorage implements StateStorage {

    private final Path root;
    private String lastLoadMessage = "";

    public FileStateStorage(String directory) {
        root = Paths.get(User.requireText(directory, "Save directory"));
    }

    public String getLastLoadMessage() {
        return lastLoadMessage;
    }

    @Override
    public ApplicationState load() throws IOException {

        if (Files.exists(root) && !Files.isDirectory(root)) {
            throw new IOException("Save location is not a directory: " + root);
        }

        Path current = root.resolve("current");
        Path backup = root.resolve("backup");
        Path source;

        if (Files.exists(current)) {
            source = current;
            lastLoadMessage = "Loaded saved simulation.";

        } else if (Files.exists(backup)) {
            source = backup;
            lastLoadMessage = "Recovered the previous complete save from backup.";

        } else {

            if (Files.exists(root.resolve("pending"))) {
                throw new IOException(
                    "An incomplete first save exists in " + root.resolve("pending"));
            }

            SimulationClock.restoreStartDateTime(null);
            SimulationClock.restoreMinute(0);

            lastLoadMessage = "Started a new simulation.";

            return new ApplicationState();
        }

        // Validate saved data before changing the global clock
        Loaded loaded = readSnapshot(source);

        SimulationClock.restoreStartDateTime(loaded.startDateTime);
        SimulationClock.restoreMinute(loaded.minute);

        return loaded.state;
    }

    @Override
    public void save(ApplicationState state) throws IOException {

        if (state == null) {
            throw new IllegalArgumentException("State is required.");
        }

        Files.createDirectories(root);

        Path current = root.resolve("current");
        Path backup = root.resolve("backup");
        Path pending = root.resolve("pending");

        // Never silently replace an existing damaged save
        if (Files.exists(current)) {
            readSnapshot(current);
        }

        deleteTree(pending);
        Files.createDirectory(pending);

        writeSnapshot(pending, state);

        Loaded checked = readSnapshot(pending);

        for (TimeAllowance allowance : state.getAllAllowances()) {

            TimeAllowance rebuilt =
                    checked.state.findAllowance(allowance.getStudentId());

            if (rebuilt == null || !allowance.sameAccountingAs(rebuilt)) {
                throw new IOException(
                    "Settle all ended jobs before saving: "
                    + allowance.getStudentId());
            }
        }

        for (GPU gpu : state.getAllGpus()) {

            GPU rebuilt = checked.state.findGpu(gpu.getGpuId());

            Job actual = gpu.getCurrentJob();
            Job expected = rebuilt.getCurrentJob();

            if ((actual == null) != (expected == null)
                    || (actual != null
                    && (!"Running".equals(actual.getStatus())
                    || !actual.getJobId().equals(expected.getJobId())
                    || !state.getAllJobs().contains(actual)))) {

                throw new IOException(
                    "GPU assignments do not match saved jobs.");
            }
        }

        // Promote verified save and retain previous backup
        if (Files.exists(current)) {
            deleteTree(backup);
            moveDirectory(current, backup);
        }

        try {

            moveDirectory(pending, current);

        } catch (IOException failure) {

            if (!Files.exists(current) && Files.exists(backup)) {

                try {
                    moveDirectory(backup, current);

                } catch (IOException recoveryFailure) {
                    failure.addSuppressed(recoveryFailure);
                }
            }

            throw failure;
        }
    }

    private void writeSnapshot(Path directory, ApplicationState state)
            throws IOException {

        ArrayList<String[]> rows = new ArrayList<>();

        // Save users
        for (User user : state.getAllUsers()) {

            String email = "";
            String phone = "";

            if (user instanceof Student) {
                email = ((Student) user).getEmail();
                phone = ((Student) user).getPhone();
            }

            rows.add(new String[] {
                user.getRole(),
                user.getUsername(),
                user.getName(),
                user.passwordForStorage(),
                email,
                phone
            });
        }

        writeRows(directory, "users.txt", rows);

        // Save registration requests
        rows = new ArrayList<>();

        for (AccessRequest request : state.getAllRequests()) {

            rows.add(new String[] {
                request.getUniversityId(),
                request.getName(),
                request.getEmail(),
                request.getPhone(),
                request.passwordForStorage(),
                request.getStatus()
            });
        }

        writeRows(directory, "requests.txt", rows);

        // Save jobs
        rows = new ArrayList<>();

        for (Job job : state.getAllJobs()) {

            rows.add(new String[] {
                job.getJobId(),
                job.getStudentId(),
                job.getTitle(),
                job.getApplicationName(),
                job.getDescription(),
                job.getTaskSize(),
                String.valueOf(job.getSubmissionTime()),
                job.getStatus(),
                job.getAssignedGpuId(),
                String.valueOf(job.getStartTime()),
                String.valueOf(job.getFinishTime()),
                String.valueOf(job.getCancellationTime()),
                job.getCancelledBy(),
                job.getCancellationReason()
            });
        }

        writeRows(directory, "jobs.txt", rows);

        // Save allowances and changes
        rows = new ArrayList<>();

        ArrayList<String[]> historyRows = new ArrayList<>();

        for (TimeAllowance allowance : state.getAllAllowances()) {

            rows.add(new String[] {
                allowance.getStudentId(),
                String.valueOf(allowance.getAllocatedMinutes())
            });

            for (AllowanceChange change : allowance.getChanges()) {

                historyRows.add(new String[] {
                    allowance.getStudentId(),
                    String.valueOf(change.getPreviousTotal()),
                    String.valueOf(change.getNewTotal()),
                    change.getAdminId(),
                    change.getReason(),
                    String.valueOf(change.getChangedAt())
                });
            }
        }

        writeRows(directory, "allowances.txt", rows);
        writeRows(directory, "allowanceChanges.txt", historyRows);

        // Save simulation starting date/time and elapsed minutes
        rows = new ArrayList<>();

        String start = "";

        if (SimulationClock.isInitialized()) {
            start = SimulationClock.getStartDateTime().toString();
        }

        rows.add(new String[] {
            start,
            String.valueOf(SimulationClock.getCurrentMinute())
        });

        writeRows(directory, "clock.txt", rows);
    }

    private Loaded readSnapshot(Path directory) throws IOException {

        try {

            // Read clock (supports old and new formats)
            ArrayList<String[]> clockRows = readClockRows(directory);

            if (clockRows.size() != 1) {
                throw new IllegalArgumentException(
                    "Clock must contain one record.");
            }

            String[] clockData = clockRows.get(0);

            int minute;
            LocalDateTime startDateTime = null;

            if (clockData.length == 1) {

                // Old format: elapsed minutes only
                minute = number(clockData[0]);

                // A legacy save has no selected calendar date/time.
                // It must be initialized before new jobs are accepted.

            } else {

                // New format: starting date/time and elapsed minutes
                String savedStart = clockData[0];

                minute = number(clockData[1]);

                if (savedStart != null && !savedStart.isEmpty()) {
                    startDateTime = LocalDateTime.parse(savedStart);
                }

                if (startDateTime == null && minute != 0) {
                    throw new IllegalArgumentException(
                        "Uninitialized clock cannot have elapsed minutes.");
                }
            }

            if (minute < 0) {
                throw new IllegalArgumentException("Negative saved time.");
            }

            // Read users
            ArrayList<User> users = new ArrayList<>();

            for (String[] row : readRows(directory, "users.txt", 6)) {

                if ("Student".equals(row[0])) {

                    users.add(new Student(
                        row[1], row[2], row[4], row[5], row[3]));

                } else if ("Administrator".equals(row[0])) {

                    Administrator admin = new Administrator();

                    if (!admin.getUsername().equals(row[1])
                            || !admin.getName().equals(row[2])
                            || !admin.matchesPassword(row[3])
                            || !"".equals(row[4])
                            || !"".equals(row[5])) {

                        throw new IllegalArgumentException(
                            "Invalid predefined administrator.");
                    }

                    users.add(admin);

                } else {
                    throw new IllegalArgumentException(
                        "Unknown account role.");
                }
            }

            // Read requests
            ArrayList<AccessRequest> requests = new ArrayList<>();

            for (String[] row : readRows(directory, "requests.txt", 6)) {

                requests.add(AccessRequest.restore(
                    row[0], row[1], row[2],
                    row[3], row[4], row[5]));
            }

            // Read jobs
            ArrayList<Job> jobs = new ArrayList<>();

            for (String[] row : readRows(directory, "jobs.txt", 14)) {

                jobs.add(Job.restore(
                    row[0], row[1], row[2],
                    row[3], row[4], row[5],
                    number(row[6]),
                    row[7], row[8],
                    number(row[9]),
                    number(row[10]),
                    number(row[11]),
                    row[12], row[13], minute));
            }

            // Read allowances
            ArrayList<String[]> allocationRows =
                    readRows(directory, "allowances.txt", 2);

            ArrayList<String[]> historyRows =
                    readRows(directory, "allowanceChanges.txt", 6);

            for (String[] history : historyRows) {

                boolean found = false;

                for (String[] allocation : allocationRows) {

                    if (allocation[0] != null
                            && allocation[0].equals(history[0])) {
                        found = true;
                    }
                }

                if (!found) {
                    throw new IllegalArgumentException(
                        "Allowance history has no owner.");
                }
            }

            ArrayList<TimeAllowance> allowances = new ArrayList<>();

            for (String[] row : allocationRows) {

                String studentId =
                        User.requireText(row[0], "Student ID");

                ArrayList<AllowanceChange> history = new ArrayList<>();

                for (String[] change : historyRows) {

                    if (studentId.equals(change[0])) {

                        history.add(AllowanceChange.restore(
                            number(change[1]),
                            number(change[2]),
                            change[3],
                            change[4],
                            number(change[5])));
                    }
                }

                allowances.add(TimeAllowance.restore(
                    studentId,
                    number(row[1]),
                    jobs,
                    history,
                    minute));
            }

            ApplicationState state =
                    ApplicationState.restore(
                        users, requests, jobs, allowances);

            return new Loaded(state, minute, startDateTime);

        } catch (IllegalArgumentException
                | IllegalStateException
                | ArithmeticException exception) {

            throw new IOException(
                "Invalid saved data in " + directory
                + ": " + exception.getMessage(), exception);
        }
    }

    // Read old or new clock format
    private static ArrayList<String[]> readClockRows(Path directory)
            throws IOException {

        ArrayList<String[]> rows = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(
                directory.resolve("clock.txt"),
                StandardCharsets.UTF_8)) {

            if (!"OOP_TEXT_V1\tclock.txt".equals(reader.readLine())) {
                throw new IOException("Missing or invalid header: clock.txt");
            }

            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {

                lineNumber++;

                String[] fields = line.split("\t", -1);

                if (fields.length != 1 && fields.length != 2) {
                    throw new IOException(
                        "Wrong field count in clock.txt at line "
                        + lineNumber);
                }

                for (int i = 0; i < fields.length; i++) {
                    fields[i] = unescape(fields[i]);
                }

                rows.add(fields);
            }
        }

        return rows;
    }

    private static int number(String text) {
        return Integer.parseInt(text);
    }

    private static void writeRows(
            Path directory,
            String name,
            ArrayList<String[]> rows) throws IOException {

        try (BufferedWriter writer = Files.newBufferedWriter(
                directory.resolve(name),
                StandardCharsets.UTF_8)) {

            writer.write("OOP_TEXT_V1\t" + name);
            writer.newLine();

            for (String[] row : rows) {

                for (int i = 0; i < row.length; i++) {

                    if (i > 0) {
                        writer.write('\t');
                    }

                    writer.write(escape(row[i]));
                }

                writer.newLine();
            }
        }
    }

    private static ArrayList<String[]> readRows(
            Path directory,
            String name,
            int columns) throws IOException {

        ArrayList<String[]> rows = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(
                directory.resolve(name),
                StandardCharsets.UTF_8)) {

            if (!("OOP_TEXT_V1\t" + name).equals(reader.readLine())) {
                throw new IOException("Missing or invalid header: " + name);
            }

            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {

                lineNumber++;

                String[] fields = line.split("\t", -1);

                if (fields.length != columns) {
                    throw new IOException(
                        "Wrong field count in " + name
                        + " at line " + lineNumber);
                }

                for (int i = 0; i < fields.length; i++) {
                    fields[i] = unescape(fields[i]);
                }

                rows.add(fields);
            }
        }

        return rows;
    }

    private static String escape(String value) {

        if (value == null) {
            return "\\N";
        }

        return value.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String unescape(String value) throws IOException {

        if ("\\N".equals(value)) {
            return null;
        }

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {

            char c = value.charAt(i);

            if (c != '\\') {
                result.append(c);
                continue;
            }

            if (++i == value.length()) {
                throw new IOException("Incomplete text escape.");
            }

            switch (value.charAt(i)) {

                case '\\':
                    result.append('\\');
                    break;

                case 't':
                    result.append('\t');
                    break;

                case 'n':
                    result.append('\n');
                    break;

                case 'r':
                    result.append('\r');
                    break;

                default:
                    throw new IOException("Unknown text escape.");
            }
        }

        return result.toString();
    }

    private static void moveDirectory(Path source, Path target)
            throws IOException {

        try {

            Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE);

        } catch (AtomicMoveNotSupportedException exception) {

            Files.move(source, target);
        }
    }

    private static void deleteTree(Path path) throws IOException {

        if (!Files.exists(path)) {
            return;
        }

        if (Files.isDirectory(path)) {

            try (DirectoryStream<Path> children =
                    Files.newDirectoryStream(path)) {

                for (Path child : children) {
                    deleteTree(child);
                }
            }
        }

        Files.delete(path);
    }

    private static class Loaded {

        final ApplicationState state;
        final int minute;
        final LocalDateTime startDateTime;

        Loaded(ApplicationState state,
               int minute,
               LocalDateTime startDateTime) {

            this.state = state;
            this.minute = minute;
            this.startDateTime = startDateTime;
        }
    }
}