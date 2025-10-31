import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class VirtualAssistant {
    private final Scanner in = new Scanner(System.in);
    private final Map<LocalDate, List<Task>> schedules = new HashMap<>();
    private String userName;

    public static void main(String[] args) {
        new VirtualAssistant().start();
    }

    private void start() {
        greetAndGetName();
        mainLoop();
    }

    private void greetAndGetName() {
        System.out.print("Hello! What's your name? \n");
        userName = in.nextLine().trim();
        if (userName.isEmpty()) userName = "User";
        System.out.println("Nice to meet you, " + userName + "!");
        System.out.println("How may I help you ?");
    }

    private void mainLoop() {
        while (true) {
            printMainMenu();
            int choice = readInt("Choose an option: \n");
            switch (choice) {
                case 1 -> showTimeDayDate();
                case 2 -> createScheduleForDay();
                case 4 -> editScheduleMenu();
                case 3 -> showScheduleMenu();
                case 5 -> accomplishTasksMenu();
                case 6 -> {
                    System.out.println("Goodbye, " + userName + ". Have a productive day!");
                    return;
                }
                default -> System.out.println("Invalid option. Try again.");
            }
            System.out.println();
        }
    }

    private void printMainMenu() {
        System.out.println("----- Main Menu -----");
        System.out.println("1) What is the Time ?");
        System.out.println("2) Make schedule for the day");
        System.out.println("3) Show schedule");
        System.out.println("4git commit -m ) Edit schedule");
        System.out.println("5) Mark accomplished tasks & show progress");
        System.out.println("6) Exit");
    }

    // Option 1
    private void showTimeDayDate() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy");
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("hh:mm:ss a");
        System.out.println("Current date: " + now.format(dtf));
        System.out.println("Current time: " + now.format(tf));
        System.out.println("Day of week: " + now.getDayOfWeek());
    }

    // Option 2
    private void createScheduleForDay() {
        LocalDate date = readDate("Enter date for schedule (yyyy-MM-dd): ");
        List<Task> tasks = schedules.computeIfAbsent(date, d -> new ArrayList<>());
        System.out.println("Creating / editing schedule for " + date);
        while (true) {
            System.out.println("1) Add task");
            System.out.println("2) Finish");
            int c = readInt("Choose: ");
            if (c == 1) {
                addTaskToList(tasks);
            } else if (c == 2) {
                break;
            } else {
                System.out.println("Invalid option");
            }
        }
        schedules.put(date, tasks);
        System.out.println("Saved schedule for " + date + " (" + tasks.size() + " tasks).");
    }

    private void addTaskToList(List<Task> tasks) {
        System.out.print("Task title: ");
        String title = in.nextLine().trim();
        if (title.isEmpty()) {
            System.out.println("Task cannot be empty.");
            return;
        }
        int duration = readInt("Estimated duration in minutes (integer): ");
        Task t = new Task(title, duration);
        tasks.add(t);
        System.out.println("Added: " + t);
    }

    // Option 3
    private void editScheduleMenu() {
        LocalDate date = readDate("Enter date of schedule to edit (yyyy-MM-dd): ");
        List<Task> tasks = schedules.get(date);
        if (tasks == null || tasks.isEmpty()) {
            System.out.println("No schedule found for " + date + ". You can create one instead.");
            return;
        }
        while (true) {
            System.out.println("Editing schedule for " + date);
            //printTasksBrief(tasks);
            System.out.println("1) Add task");
            System.out.println("2) Delete task");
            System.out.println("3) Modify task");
            System.out.println("4) Back to main menu");
            int c = readInt("Choose: ");
            switch (c) {
                case 1 -> addTaskToList(tasks);
                case 2 -> deleteTask(tasks);
                case 3 -> modifyTask(tasks);
                case 4 -> {
                    schedules.put(date, tasks);
                    return;
                }
                default -> System.out.println("Invalid option");
            }
        }
    }

    private void deleteTask(List<Task> tasks) {
        int idx = readInt("Enter task number to delete: ") - 1;
        if (idx < 0 || idx >= tasks.size()) {
            System.out.println("Invalid task number.");
            return;
        }
        Task removed = tasks.remove(idx);
        System.out.println("Removed: " + removed);
    }

    private void modifyTask(List<Task> tasks) {
        int idx = readInt("Enter task number to modify: ") - 1;
        if (idx < 0 || idx >= tasks.size()) {
            System.out.println("Invalid task number.");
            return;
        }
        Task t = tasks.get(idx);
        System.out.println("Current: " + t);
        System.out.print("New title (leave blank to keep): ");
        String title = in.nextLine().trim();
        if (!title.isEmpty()) t.setTitle(title);
        String durStr;
        System.out.print("New duration in minutes (leave blank to keep): ");
        durStr = in.nextLine().trim();
        if (!durStr.isEmpty()) {
            try {
                int d = Integer.parseInt(durStr);
                t.setDurationMinutes(d);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number. Duration unchanged.");
            }
        }
        System.out.println("Modified: " + t);
    }

    // Option 4
    private void showScheduleMenu() {
        LocalDate date = readDate("Enter date to view schedule (yyyy-MM-dd): ");
        List<Task> tasks = schedules.getOrDefault(date, Collections.emptyList());
        if (tasks.isEmpty()) {
            System.out.println("No tasks scheduled for " + date);
            return;
        }
        while (true) {
            System.out.println("Viewing schedule for " + date);
            System.out.println("1) Show current task (first uncompleted)");
            System.out.println("2) Show full day schedule");
            System.out.println("3) Back");
            int c = readInt("Choose: ");
            if (c == 1) {
                Optional<Task> current = tasks.stream().filter(t -> !t.isDone()).findFirst();
                if (current.isPresent()) {
                    System.out.println("Current task: " + current.get());
                    return;
                } else {
                    System.out.println("No pending tasks. All done!");
                    return;
                }
            } else if (c == 2) {
                printTasksDetailed(tasks);
                return;
            } else if (c == 3) {
                return;
            } else {
                System.out.println("Invalid option");
                return;
            }
        }
    }

    // Option 5
    private void accomplishTasksMenu() {
        LocalDate date = readDate("Enter date of schedule to mark accomplished (yyyy-MM-dd): ");
        List<Task> tasks = schedules.get(date);
        if (tasks == null || tasks.isEmpty()) {
            System.out.println("No schedule for " + date);
            return;
        }
        while (true) {
            System.out.println("Mark accomplished tasks for " + date);
            System.out.println("1) Mark a task done now");
            System.out.println("2) Mark multiple tasks done (provide numbers separated by commas)");
            System.out.println("3) Mark all at once (EOD)");
            System.out.println("4) Show progress");
            System.out.println("5) Back");
            int c = readInt("Choose: ");
            switch (c) {
                case 1 -> markSingleTaskDone(tasks);
                case 2 -> markMultipleTasksDone(tasks);
                case 3 -> {
                    tasks.forEach(t -> t.setDone(true));
                    System.out.println("All tasks marked done.");
                }
                case 4 -> showProgress(tasks);
                case 5 -> {
                    schedules.put(date, tasks);
                    return;
                }
                default -> System.out.println("Invalid option");
            }
        }
    }

    private void markSingleTaskDone(List<Task> tasks) {
        printTasksBrief(tasks);
        int idx = readInt("Enter task number completed: ") - 1;
        if (idx < 0 || idx >= tasks.size()) {
            System.out.println("Invalid number.");
            return;
        }
        Task t = tasks.get(idx);
        t.setDone(true);
        System.out.println("Marked done: " + t.getTitle());
    }

    private void markMultipleTasksDone(List<Task> tasks) {
        System.out.print("Enter task numbers separated by commas (e.g., 1,3,4): ");
        String line = in.nextLine().trim();
        if (line.isEmpty()) {
            System.out.println("No input given.");
            return;
        }
        String[] parts = line.split(",");
        int marked = 0;
        for (String p : parts) {
            try {
                int idx = Integer.parseInt(p.trim()) - 1;
                if (idx >= 0 && idx < tasks.size()) {
                    tasks.get(idx).setDone(true);
                    marked++;
                }
            } catch (NumberFormatException ignored) {}
        }
        System.out.println("Marked " + marked + " tasks done.");
    }

    private void showProgress(List<Task> tasks) {
        long total = tasks.size();
        long done = tasks.stream().filter(Task::isDone).count();
        double percent = total == 0 ? 100.0 : (done * 100.0 / total);
        System.out.println("Progress: " + done + " / " + total + " tasks completed (" +
                String.format("%.1f", percent) + "%)");
        if (done < total) {
            System.out.println("Pending tasks:");
            tasks.stream().filter(t -> !t.isDone()).forEach(t -> System.out.println(" - " + t.getTitle()));
        } else {
            System.out.println("All tasks completed. Good job!");
        }
    }

    // Helpers
    private LocalDate readDate(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = in.nextLine().trim();
            try {
                return LocalDate.parse(s);
            } catch (Exception e) {
                System.out.println("Invalid format. Please use yyyy-MM-dd.");
            }
        }
    }

    private int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = in.nextLine().trim();
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid integer.");
            }
        }
    }

    private void printTasksBrief(List<Task> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            System.out.printf("%d) %s %s%n", i + 1, t.getTitle(), t.isDone() ? "[DONE]" : "");
        }
    }

    private void printTasksDetailed(List<Task> tasks) {
        System.out.println("Full schedule:");
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            System.out.printf("%d) %s | %d min | %s%n", i + 1, t.getTitle(), t.getDurationMinutes(),
                    t.isDone() ? "DONE" : "PENDING");
        }
        int totalDuration = tasks.stream().mapToInt(Task::getDurationMinutes).sum();
        System.out.println("Total tasks: " + tasks.size() + " | Total estimated minutes: " + totalDuration);
    }

    // Task class
    private static class Task {
        private String title;
        private int durationMinutes;
        private boolean done;

        Task(String title, int durationMinutes) {
            this.title = title;
            this.durationMinutes = Math.max(0, durationMinutes);
            this.done = false;
        }

        String getTitle() {
            return title;
        }

        void setTitle(String title) {
            this.title = title;
        }

        int getDurationMinutes() {
            return durationMinutes;
        }

        void setDurationMinutes(int durationMinutes) {
            this.durationMinutes = Math.max(0, durationMinutes);
        }

        boolean isDone() {
            return done;
        }

        void setDone(boolean done) {
            this.done = done;
        }

        @Override
        public String toString() {
            return title + " (" + durationMinutes + " min) " + (done ? "[DONE]" : "[PENDING]");
        }
    }
}