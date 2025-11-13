import javax.swing.*;
import javax.swing.Timer;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.TrayIcon.MessageType;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VirtualAssistantGUI extends JFrame {
    private final Map<LocalDate, List<Task>> schedules = new HashMap<>();
    private String userName;
    private JLabel clockLabel;
    private JPanel mainPanel;
    private TaskListPanel taskListPanel;
    private JLabel greetingLabel;
    private Timer greetingHideTimer;
    private Color accentColor = new Color(41, 128, 185); // RGB accent
    // Fixed accent for buttons (decoupled from RGB animation)
    private final Color buttonAccent = new Color(41, 128, 185);
    private final Timer rgbTimer;
    private float hue = 0;
    private SystemTray tray;
    private TrayIcon trayIcon;

    public static void main(String[] args) {
        try {
            // Set system look and feel with dark theme modifications
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            SwingUtilities.invokeLater(() -> {
                new VirtualAssistantGUI().setVisible(true);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public VirtualAssistantGUI() {
        setTitle("V A M P");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 600);
        setLocationRelativeTo(null);

        // Initialize system tray
        setupSystemTray();

        // Set up the main panel with dark theme
        setupMainPanel();

        // Start textual clock in header
        setupClock();

        // Setup RGB animation timer (kept for header accent only)
        rgbTimer = new Timer(50, e -> updateRGBEffect());
        rgbTimer.start();

        // Start the reminder thread
        startReminderThread();

        // Show a small startup dialog (after the window is displayed) to ask name
        new javax.swing.Timer(300, ev -> {
            ((javax.swing.Timer) ev.getSource()).stop();
            showStartupNameDialog();
        }).start();
    }

    /**
     * Initialize the system tray icon (if supported) and create a simple programmatic icon.
     * Runs on EDT during construction.
     */
    private void setupSystemTray() {
        if (SystemTray.isSupported()) {
            tray = SystemTray.getSystemTray();
            // Create a simple icon programmatically
            int iconSize = 16;
            BufferedImage image = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(accentColor);
            g2d.fillOval(0, 0, iconSize, iconSize);
            g2d.dispose();

            trayIcon = new TrayIcon(image, "V A M P");
            trayIcon.setImageAutoSize(true);

            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Show a desktop notification via system tray (if supported) and play a notification sound.
     */
    private void showNotification(String title, String message, MessageType type) {
        if (SystemTray.isSupported()) {
            trayIcon.displayMessage(title, message, type);
            playNotificationSound();
        }
    }

    /**
     * Play a local notification sound file if present (notification.wav).
     * Exceptions are swallowed to avoid breaking the UI thread.
     */
    private void playNotificationSound() {
        try {
            File soundFile = new File("notification.wav");
            if (!soundFile.exists()) return;
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundFile);
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            clip.start();
        } catch (Exception e) {
            // ignore missing sound
        }
    }

    /**
     * Build the primary UI layout: header, task list panel and bottom buttons.
     */
    private void setupMainPanel() {
    mainPanel = new JPanel(new BorderLayout());
    mainPanel.setBackground(Colors.BACKGROUND); // Dark background

        // Create gradient header panel
        JPanel headerPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                int w = getWidth();
                int h = getHeight();
                GradientPaint gp = new GradientPaint(0, 0, Colors.ACCENT,
                                                    w, h, Colors.ACCENT_LIGHT);
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, w, h);
                g2d.dispose();
            }
        };
        headerPanel.setPreferredSize(new Dimension(1000, 100));
        headerPanel.setLayout(new BorderLayout());

        // Left side: greeting only (start empty, will show greeting or phrase)
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 18));
        leftPanel.setOpaque(false);

        // Greeting label (starts empty, will be updated by showAndHideGreeting)
        greetingLabel = new JLabel(" ");
        greetingLabel.setFont(new Font("Arial", Font.BOLD, 48)); // large for greeting
        greetingLabel.setForeground(Color.WHITE);
        greetingLabel.setVisible(true);

        greetingLabel.setVisible(false); // Start invisible until user enters name
        leftPanel.add(greetingLabel);
        headerPanel.add(leftPanel, BorderLayout.WEST);

        // Right side: simple textual clock (no RGB)
        clockLabel = new JLabel();
        clockLabel.setFont(new Font("Arial", Font.BOLD, 36));
        clockLabel.setForeground(Color.WHITE);
        clockLabel.setHorizontalAlignment(SwingConstants.CENTER);
        clockLabel.setVisible(true); // Show clock immediately
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 20));
        rightPanel.setOpaque(false);
        rightPanel.add(clockLabel);
        headerPanel.add(rightPanel, BorderLayout.EAST);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Create task list panel (shows day progress + checklist)
        taskListPanel = new TaskListPanel();
        mainPanel.add(taskListPanel, BorderLayout.CENTER);

        // Create button panel
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
    buttonPanel.setBackground(Colors.PANEL_BG);

        addStyledButton(buttonPanel, "Add Task", this::showAddTaskDialog);
        addStyledButton(buttonPanel, "View Schedule", this::showViewScheduleDialog);
        addStyledButton(buttonPanel, "Launch App", this::showAppLauncherDialog);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private void addStyledButton(JPanel panel, String text, ActionListener action) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Use fixed button accent (no RGB effect on buttons)
                GradientPaint gp = new GradientPaint(0, 0, buttonAccent.brighter(),
                                                    0, getHeight(), buttonAccent.darker());
                g2d.setPaint(gp);
                g2d.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 15, 15));
                g2d.dispose();
                super.paintComponent(g);
            }
        };
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> action.actionPerformed(e));
        panel.add(button);
    }

    /**
     * Start a Swing Timer to update the textual clock label every second.
     */
    private void setupClock() {
        Timer timer = new Timer(1000, e -> {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            // Use fixed white color, no RGB
            clockLabel.setText(now.format(timeFormatter));
        });
        timer.start();
    }

    /**
     * Show a modal startup dialog to request the user's first name. Updates header greeting.
     */
    private void showStartupNameDialog() {
        // Modal dialog asking for first name, shown after main window opens
        JDialog d = new JDialog(this, "Welcome", true);
        d.setLayout(new GridBagLayout());
    d.getContentPane().setBackground(Colors.PANEL_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 12, 8, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel prompt = new JLabel("What's your first name?");
        prompt.setForeground(Color.WHITE);
        prompt.setFont(new Font("Arial", Font.BOLD, 16));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        d.add(prompt, gbc);

        JTextField fname = new JTextField(20);
    fname.setBackground(Colors.INPUT_BG);
        fname.setForeground(Color.WHITE);
        gbc.gridy = 1; gbc.gridwidth = 2;
        d.add(fname, gbc);

        JButton save = createStyledButton("Save");
        JButton skip = createStyledButton("Skip");

        save.addActionListener(ev -> {
            String input = fname.getText();
            if (input != null && !input.trim().isEmpty()) {
                showAndHideGreetingFromName(input.trim());
                // Clock is already visible, no need to show it
            }
            d.dispose();
        });
        skip.addActionListener(ev -> {
            // Keep header empty
            greetingLabel.setVisible(false);
            clockLabel.setVisible(true); // Only show clock
            d.dispose();
        });

        gbc.gridwidth = 1; gbc.gridy = 2; gbc.gridx = 0;
        d.add(save, gbc);
        gbc.gridx = 1; d.add(skip, gbc);

        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    /**
     * Display a large welcome greeting using the provided name, then replace it
     * with a short phrase after a few seconds.
     */
    private void showAndHideGreetingFromName(String fullName) {
        // Use first token as first name
        String first = fullName.split("\\s+")[0];
        userName = first;
        
        // Show big greeting in header immediately
        greetingLabel.setFont(new Font("Arial", Font.BOLD, 48));
        greetingLabel.setText("Welcome To VAMP " + first + " !");
        greetingLabel.setVisible(true);

        // Hide greeting after 5 seconds and show default 'Upgrade your effort' with same font size
        if (greetingHideTimer != null && greetingHideTimer.isRunning()) greetingHideTimer.stop();
        greetingHideTimer = new Timer(5000, ev -> {
            greetingLabel.setFont(new Font("Arial", Font.BOLD, 48));  // Keep same large font
            greetingLabel.setText("Upgrade Your Efforts !");
        });
        greetingHideTimer.setRepeats(false);
        greetingHideTimer.start();
    }

    // textual header clock is used instead of a round clock (see clockLabel)

    /**
     * Update the dynamic header accent color (HSB hue rotation).
     */
    private void updateRGBEffect() {
        hue = (hue + 0.01f) % 1.0f;
        accentColor = Color.getHSBColor(hue, 0.8f, 0.9f);
        mainPanel.repaint();
    }

    /**
     * Start a background daemon thread that periodically checks tasks and
     * shows reminders / break notifications when appropriate.
     */
    private void startReminderThread() {
        Thread reminder = new Thread(() -> {
            final Set<UUID> remindedStarts = Collections.synchronizedSet(new HashSet<>());
            long focusedStart = -1L;
            while (true) {
                try {
                    LocalDate today = LocalDate.now();
                    LocalDateTime now = LocalDateTime.now();
                    List<Task> todays = schedules.getOrDefault(today, Collections.emptyList());

                    List<Task> sorted = todays.stream()
                            .filter(t -> t.getStartTime() != null)
                            .sorted(Comparator.comparing(Task::getStartTime))
                            .collect(Collectors.toList());

                    boolean anyTaskInProgress = false;
                    for (Task t : sorted) {
                        if (t.isDone()) continue;
                        LocalTime st = t.getStartTime();
                        LocalTime et = t.getEndTime();
                        if (st == null) continue;

                        LocalDateTime taskStart = LocalDateTime.of(today, st);
                        LocalDateTime taskEnd = et == null ? taskStart.plusMinutes(t.getDurationMinutes())
                                                         : LocalDateTime.of(today, et);

                        if (!remindedStarts.contains(t.getId())) {
                            LocalDateTime remindAt = taskStart.minusMinutes(5);
                            if (!now.isBefore(remindAt) && now.isBefore(taskStart)) {
                                showNotification("Task Reminder",
                                              "Task starting in 5 minutes: " + t.getTitle(),
                                              MessageType.INFO);
                                remindedStarts.add(t.getId());
                            }
                        }

                        if (!now.isBefore(taskStart) && now.isBefore(taskEnd)) {
                            anyTaskInProgress = true;
                        }
                    }

                    if (anyTaskInProgress) {
                        if (focusedStart == -1L) focusedStart = Instant.now().getEpochSecond();
                        long elapsedSeconds = Instant.now().getEpochSecond() - focusedStart;
                        if (elapsedSeconds >= 60 * 60) {
                            showNotification("Break Time",
                                          "You've been focused for 1 hour. Take a 5-10 minute break!",
                                          MessageType.WARNING);
                            focusedStart = Instant.now().getEpochSecond();
                        }
                    } else {
                        focusedStart = -1L;
                    }

                    Thread.sleep(30 * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        reminder.setDaemon(true);
        reminder.start();
    }

    /**
     * Show the Add Task dialog (modal). Reads inputs and creates a Task on save.
     */
    private void showAddTaskDialog(ActionEvent e) {
        JDialog dialog = new JDialog(this, "Add New Task", true);
        dialog.setLayout(new GridBagLayout());
    dialog.getContentPane().setBackground(Colors.PANEL_BG);
        dialog.setSize(400, 350);
        dialog.setLocationRelativeTo(this);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Title input
        JLabel titleLabel = createLabel("Task Title:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        dialog.add(titleLabel, gbc);

        JTextField titleField = createStyledTextField();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        dialog.add(titleField, gbc);

        // Date picker
        JLabel dateLabel = createLabel("Date:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        dialog.add(dateLabel, gbc);

        // Custom date picker with spinners
        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    datePanel.setBackground(Colors.PANEL_BG);

        SpinnerModel yearModel = new SpinnerNumberModel(LocalDate.now().getYear(), 2000, 2100, 1);
        SpinnerModel monthModel = new SpinnerNumberModel(LocalDate.now().getMonthValue(), 1, 12, 1);
        SpinnerModel dayModel = new SpinnerNumberModel(LocalDate.now().getDayOfMonth(), 1, 31, 1);

        // Create year spinner with explicit format
        JSpinner yearSpinner = new JSpinner(yearModel);
        JSpinner.NumberEditor yearEditor = new JSpinner.NumberEditor(yearSpinner, "#"); // Use # for no grouping
        yearSpinner.setEditor(yearEditor);
        styleSpinnerEditor(yearSpinner); // Apply our dark theme styling

        JSpinner monthSpinner = createStyledSpinner(monthModel);
        JSpinner daySpinner = createStyledSpinner(dayModel);

        datePanel.add(yearSpinner);
        datePanel.add(createLabel("-"));
        datePanel.add(monthSpinner);
        datePanel.add(createLabel("-"));
        datePanel.add(daySpinner);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        dialog.add(datePanel, gbc);

        // Start time
        JLabel startLabel = createLabel("Start Time:");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        dialog.add(startLabel, gbc);

        JPanel startTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    startTimePanel.setBackground(Colors.PANEL_BG);

        SpinnerModel startHourModel = new SpinnerNumberModel(LocalTime.now().getHour(), 0, 23, 1);
        SpinnerModel startMinModel = new SpinnerNumberModel(LocalTime.now().getMinute(), 0, 59, 1);

        JSpinner startHourSpinner = createStyledSpinner(startHourModel);
        JSpinner startMinSpinner = createStyledSpinner(startMinModel);

        startTimePanel.add(startHourSpinner);
        startTimePanel.add(createLabel(":"));
        startTimePanel.add(startMinSpinner);

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        dialog.add(startTimePanel, gbc);

        // Duration
        JLabel durationLabel = createLabel("Duration (minutes):");
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        dialog.add(durationLabel, gbc);

        SpinnerModel durationModel = new SpinnerNumberModel(30, 1, 1440, 5);
        JSpinner durationSpinner = createStyledSpinner(durationModel);
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        dialog.add(durationSpinner, gbc);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
    buttonPanel.setBackground(Colors.PANEL_BG);

        JButton addButton = createStyledButton("Add Task");
        JButton cancelButton = createStyledButton("Cancel");

        addButton.addActionListener(evt -> {
            String title = titleField.getText().trim();
            if (title.isEmpty()) {
                showNotification("Error", "Task title cannot be empty", MessageType.ERROR);
                return;
            }

            LocalDate date = LocalDate.of(
                (int) yearSpinner.getValue(),
                (int) monthSpinner.getValue(),
                (int) daySpinner.getValue()
            );

            LocalTime startTime = LocalTime.of(
                (int) startHourSpinner.getValue(),
                (int) startMinSpinner.getValue()
            );

            int duration = (int) durationSpinner.getValue();
            LocalTime endTime = startTime.plusMinutes(duration);

            Task task = new Task(title, startTime, endTime);
            List<Task> dateTasks = schedules.computeIfAbsent(date, k -> new ArrayList<>());
            dateTasks.add(task);

            // Refresh main window progress/checklist
            if (taskListPanel != null) taskListPanel.refresh();

            showNotification("Task Added", 
                           "New task scheduled for " + date.toString(), 
                           MessageType.INFO);
            dialog.dispose();
        });

        cancelButton.addActionListener(evt -> dialog.dispose());

        buttonPanel.add(addButton);
        buttonPanel.add(cancelButton);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        gbc.insets = new Insets(20, 10, 10, 10);
        dialog.add(buttonPanel, gbc);

        dialog.setVisible(true);
    }

    /**
     * Rebuild the list of tasks shown in the View Schedule dialog. Ensures rows
     * keep consistent sizing and that controls are attached to the correct task.
     */
    private void rebuildTaskList(JPanel listPanel, LocalDate selectedDate, JPanel schedulePanel, JDialog dialog) {
        listPanel.removeAll();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        
        List<Task> todays = schedules.getOrDefault(selectedDate, Collections.emptyList());
        // Sort tasks by start time to match timeline order
        List<Task> sortedTasks = todays.stream()
            .sorted((a, b) -> {
                if (a.getStartTime() == null) return b.getStartTime() == null ? 0 : 1;
                if (b.getStartTime() == null) return -1;
                return a.getStartTime().compareTo(b.getStartTime());
            })
            .collect(Collectors.toList());

        for (Task t : sortedTasks) {
            // Create a fixed-height panel for consistent spacing
            JPanel row = new JPanel() {
                @Override
                public Dimension getMaximumSize() {
                    Dimension pref = getPreferredSize();
                    return new Dimension(super.getMaximumSize().width, pref.height);
                }
            };
            row.setLayout(new BorderLayout());
            row.setBackground(Colors.ROW_BG);
            row.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));

            // Left side: Task info with fixed width
            JPanel infoPanel = new JPanel(new BorderLayout());
            infoPanel.setOpaque(false);
            
            String timeText = (t.getStartTime() != null ? t.getStartTime().toString() : "") +
                              (t.getEndTime() != null ? " - " + t.getEndTime().toString() : "");
            
            // Create a fixed-width label panel
            JLabel lbl = new JLabel(t.getTitle() + " (" + timeText + ")") {
                @Override
                public Dimension getPreferredSize() {
                    Dimension d = super.getPreferredSize();
                    return new Dimension(Math.min(400, d.width), d.height);
                }
            };
            lbl.setForeground(Color.WHITE);
            infoPanel.add(lbl, BorderLayout.WEST);

            // Right side: Controls with fixed spacing
            JPanel controls = new JPanel();
            controls.setLayout(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            controls.setOpaque(false);
            
            JButton editBtn = createStyledButton("Edit");
            JButton delBtn = createStyledButton("Delete");

            editBtn.addActionListener(evt2 -> {
                EditTaskDialog editDialog = new EditTaskDialog(this, t, selectedDate, () -> {
                    schedulePanel.repaint();
                    rebuildTaskList(listPanel, selectedDate, schedulePanel, dialog);
                    if (taskListPanel != null) taskListPanel.refresh();
                });
                editDialog.setLocationRelativeTo(dialog);
                editDialog.setVisible(true);
            });

            delBtn.addActionListener(evt2 -> {
                int confirm = JOptionPane.showConfirmDialog(dialog, 
                    "Delete task '" + t.getTitle() + "'?", 
                    "Confirm", 
                    JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    List<Task> list = schedules.getOrDefault(selectedDate, new ArrayList<>());
                    list.removeIf(x -> x.getId().equals(t.getId()));
                    schedulePanel.repaint();
                    rebuildTaskList(listPanel, selectedDate, schedulePanel, dialog);
                    if (taskListPanel != null) taskListPanel.refresh();
                }
            });

            controls.add(editBtn); 
            controls.add(delBtn);

            row.add(infoPanel, BorderLayout.CENTER);
            row.add(controls, BorderLayout.EAST);
            
            // Add rigid spacing between rows
            listPanel.add(Box.createRigidArea(new Dimension(0, 2)));
            listPanel.add(row);
        }
        listPanel.revalidate(); listPanel.repaint();
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Arial", Font.BOLD, 12));
        return label;
    }

    private JTextField createStyledTextField() {
        JTextField field = new JTextField(20) {
            @Override
            protected void paintComponent(Graphics g) {
                if (!isOpaque()) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setColor(getBackground());
                    g2d.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 10, 10));
                    g2d.dispose();
                }
                super.paintComponent(g);
            }
        };
        field.setOpaque(false);
        field.setBackground(Colors.INPUT_BG);
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        return field;
    }

    private void styleSpinnerEditor(JSpinner spinner) {
    spinner.setBackground(Colors.INPUT_BG);
    spinner.setForeground(Color.WHITE);
        spinner.setBorder(BorderFactory.createEmptyBorder());

        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBackground(Colors.INPUT_BG);
            tf.setForeground(Color.WHITE);
            tf.setCaretColor(Color.WHITE);
            tf.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        }
    }

    private JSpinner createStyledSpinner(SpinnerModel model) {
        JSpinner spinner = new JSpinner(model);
        styleSpinnerEditor(spinner);

        // Set format to avoid grouping for number editors
        if (spinner.getEditor() instanceof JSpinner.NumberEditor) {
            JSpinner.NumberEditor numberEditor = (JSpinner.NumberEditor)spinner.getEditor();
            numberEditor.getFormat().setGroupingUsed(false);
        }

        return spinner;
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Fixed button accent so RGB hue animation does not affect buttons
                GradientPaint gp = new GradientPaint(0, 0, buttonAccent.brighter(),
                                                    0, getHeight(), buttonAccent.darker());
                g2d.setPaint(gp);
                g2d.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 15, 15));
                g2d.dispose();
                super.paintComponent(g);
            }
        };
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    /**
     * Show the View Schedule dialog which displays a timeline and editable list
     * of tasks for a selected date.
     */
    private void showViewScheduleDialog(ActionEvent e) {
        JDialog dialog = new JDialog(this, "View Schedule", true);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(new Color(24, 24, 24));
        dialog.setSize(600, 400);
        dialog.setLocationRelativeTo(this);

        // Initialize selected date holder to allow modification in lambda
        final LocalDate[] dateRef = {LocalDate.now()};

        // Create schedule timeline panel
        final JPanel schedulePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                List<Task> tasks = schedules.getOrDefault(dateRef[0], Collections.emptyList());
                List<Task> sortedTasks = tasks.stream()
                    .filter(t -> t.getStartTime() != null)
                    .sorted(Comparator.comparing(Task::getStartTime))
                    .collect(Collectors.toList());

                int y = 20;
                int width = getWidth() - 40;

                // Draw timeline
                g2d.setColor(new Color(150,150,150));
                g2d.drawLine(20, y, width + 20, y);

                // Draw hour markers
                g2d.setColor(new Color(180, 180, 180));
                g2d.setFont(new Font("Arial", Font.PLAIN, 10));
                for (int hour = 0; hour < 24; hour++) {
                    int x = 20 + (hour * width) / 24;
                    g2d.drawLine(x, y - 8, x, y + 8);
                    g2d.drawString(String.format("%02d", hour), x - 6, y - 12);
                }

                // Draw tasks
                for (Task task : sortedTasks) {
                    if (task.getStartTime() == null) continue;

                    long startMinutes = task.getStartTime().getHour() * 60 + task.getStartTime().getMinute();
                    long taskWidth = task.getDurationMinutes();

                    int x = 20 + (int)((startMinutes * width) / (24 * 60));
                    int w = Math.max(3, (int)((taskWidth * width) / (24 * 60)));

                    // Draw task block
                    g2d.setColor(task.isDone() ? new Color(46, 204, 113) : buttonAccent);
                    g2d.fill(new RoundRectangle2D.Double(x, y + 10, w, 30, 10, 10));

                    // Draw task title and time
                    g2d.setColor(Color.WHITE);
                    g2d.setFont(new Font("Arial", Font.BOLD, 12));
                    String timeText = task.getStartTime().toString() + 
                                    (task.getEndTime() != null ? " - " + task.getEndTime().toString() : "");
                    g2d.drawString(task.getTitle(), x + 5, y + 25);
                    g2d.drawString(timeText, x + 5, y + 38);

                    y += 50;
                }

                g2d.dispose();
            }
        };
        schedulePanel.setBackground(new Color(24, 24, 24));

        // Create task list panel
        final JPanel taskListPanel = new JPanel();
        taskListPanel.setLayout(new BoxLayout(taskListPanel, BoxLayout.Y_AXIS));
        taskListPanel.setBackground(new Color(24, 24, 24));

        // Header with title and date picker
        JPanel headerPanel = new JPanel(new BorderLayout(12, 0));
        headerPanel.setBackground(new Color(24,24,24));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        
        JLabel titleLabel = new JLabel("Your Schedule");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        
        // Date picker panel
        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        datePanel.setOpaque(false);
        
        SpinnerModel yearModel = new SpinnerNumberModel(dateRef[0].getYear(), 2000, 2100, 1);
        SpinnerModel monthModel = new SpinnerNumberModel(dateRef[0].getMonthValue(), 1, 12, 1);
        SpinnerModel dayModel = new SpinnerNumberModel(dateRef[0].getDayOfMonth(), 1, 31, 1);
        
        // Create year spinner with explicit format
        JSpinner yearSpinner = new JSpinner(yearModel);
        JSpinner.NumberEditor yearEditor = new JSpinner.NumberEditor(yearSpinner, "#"); // Use # for no grouping
        yearSpinner.setEditor(yearEditor);
        styleSpinnerEditor(yearSpinner); // Apply our dark theme styling

        JSpinner monthSpinner = createStyledSpinner(monthModel);
        JSpinner daySpinner = createStyledSpinner(dayModel);
        
        datePanel.add(yearSpinner);
        datePanel.add(new JLabel("-") {{ setForeground(Color.WHITE); }});
        datePanel.add(monthSpinner);
        datePanel.add(new JLabel("-") {{ setForeground(Color.WHITE); }});
        datePanel.add(daySpinner);
        
        JButton updateBtn = createStyledButton("Update");
        updateBtn.addActionListener(evt -> {
            dateRef[0] = LocalDate.of(
                (int)yearSpinner.getValue(),
                (int)monthSpinner.getValue(),
                (int)daySpinner.getValue()
            );
            schedulePanel.repaint();
            rebuildTaskList(taskListPanel, dateRef[0], schedulePanel, dialog);
        });
        datePanel.add(updateBtn);
        
        headerPanel.add(datePanel, BorderLayout.EAST);
        dialog.add(headerPanel, BorderLayout.NORTH);

        // Add scroll pane for timeline
        JScrollPane timelineScroll = new JScrollPane(schedulePanel);
        timelineScroll.setBorder(null);
        timelineScroll.getViewport().setBackground(new Color(24, 24, 24));

        // Build initial task list
        rebuildTaskList(taskListPanel, dateRef[0], schedulePanel, dialog);

        JScrollPane listScroll = new JScrollPane(taskListPanel);
        listScroll.setBorder(null);
        listScroll.getViewport().setBackground(new Color(24,24,24));

        // Split pane with timeline above and task list below
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, timelineScroll, listScroll);
        split.setResizeWeight(0.7);
        split.setBorder(null);
        dialog.add(split, BorderLayout.CENTER);

        // Close button for dialog
        JButton closeButton = createStyledButton("Close");
        closeButton.addActionListener(evt -> dialog.dispose());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBackground(new Color(24,24,24));
        buttonPanel.add(closeButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        // Set preferred size for schedule panel based on content
        schedulePanel.setPreferredSize(new Dimension(550,
            Math.max(300, schedules.getOrDefault(dateRef[0], Collections.emptyList()).size() * 60)));

        dialog.setVisible(true);
    }

    /**
     * Ask user for an application name and launch it (non-blocking).
     */
    private void showAppLauncherDialog(ActionEvent evt) {
        String appName = JOptionPane.showInputDialog(this, 
            "Enter application name to launch:", "Launch Application", 
            JOptionPane.QUESTION_MESSAGE);

        if (appName != null && !appName.trim().isEmpty()) {
            // Launch app in background thread to prevent UI freezing
            new Thread(() -> {
                try {
                    launchApplication(appName.trim());
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        showNotification("Launch Failed", 
                            "Could not launch " + appName + ": " + ex.getMessage(),
                            MessageType.ERROR);
                    });
                }
            }).start();
        }
    }

    /**
     * Try to launch an application by name. Uses several fallbacks on Windows
     * (known paths, registry App Paths, protocol handlers, cmd start).
     */
    private void launchApplication(String appName) {
        try {
            String command = "";
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("windows")) {
                // First try explicit known app mappings for common apps
                String lower = appName.toLowerCase();
                String pf = System.getenv("ProgramFiles");
                String pfx = System.getenv("ProgramFiles(x86)");
                String local = System.getenv("LOCALAPPDATA");
                String sys32 = System.getenv("SystemRoot") + "\\System32";

                java.util.List<String> candidates = new java.util.ArrayList<>();

                // Add common system utilities
                if (lower.contains("calc") || lower.equals("calculator")) {
                    candidates.add(sys32 + "\\calc.exe");
                    candidates.add(sys32 + "\\calculator.exe");
                }
                if (lower.contains("wordpad")) {
                    candidates.add(sys32 + "\\write.exe");
                }
                if (lower.contains("notepad")) {
                    candidates.add(sys32 + "\\notepad.exe");
                }

                // Office apps
                if (lower.contains("powerpoint") || lower.contains("pptx")) {
                    if (pf != null) candidates.add(pf + "\\Microsoft Office\\root\\Office16\\POWERPNT.EXE");
                    if (pf != null) candidates.add(pf + "\\Microsoft Office\\Office16\\POWERPNT.EXE");
                    if (pfx != null) candidates.add(pfx + "\\Microsoft Office\\root\\Office16\\POWERPNT.EXE");
                    if (pfx != null) candidates.add(pfx + "\\Microsoft Office\\Office16\\POWERPNT.EXE");
                }

                // Web browsers
                if (lower.contains("chrome")) {
                    if (pf != null) candidates.add(pf + "\\Google\\Chrome\\Application\\chrome.exe");
                    if (pfx != null) candidates.add(pfx + "\\Google\\Chrome\\Application\\chrome.exe");
                }
                if (lower.contains("edge")) {
                    candidates.add(System.getenv("SystemDrive") + "\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe");
                }
                if (lower.contains("whatsapp")) {
                    if (local != null) candidates.add(local + "\\WhatsApp\\WhatsApp.exe");
                    if (pf != null) candidates.add(pf + "\\WhatsApp\\WhatsApp.exe");
                }
                
                // Social/Professional apps
                if (lower.contains("linkedin")) {
                    // Try to open LinkedIn in default browser
                    try {
                        new ProcessBuilder("cmd", "/c", "start", "", "https://www.linkedin.com").start();
                        showNotification("Application Launch", "Opening LinkedIn in browser", MessageType.INFO);
                        return;
                    } catch (IOException ex) {
                        // continue to other methods
                    }
                }
                if (lower.contains("copilot")) {
                    // Prefer protocol handler if registered
                    if (registryHasKey("HKEY_CLASSES_ROOT\\ms-copilot")) {
                        try {
                            new ProcessBuilder("cmd", "/c", "start", "", "ms-copilot:").start();
                            showNotification("Application Launch", "Launching Copilot via protocol", MessageType.INFO);
                            return;
                        } catch (IOException ex) {
                            // continue to other methods
                        }
                    }
                    // Try known Copilot paths and registry App Paths
                    String regCop = queryRegistryAppPath("copilot.exe");
                    if (regCop != null) candidates.add(regCop);
                    if (pf != null) candidates.add(pf + "\\Copilot\\Copilot.exe");
                    if (pfx != null) candidates.add(pfx + "\\Copilot\\Copilot.exe");
                }

                // Try explicit candidate paths first
                for (String p : candidates) {
                    if (p == null) continue;
                    File f = new File(p);
                    if (f.exists()) {
                        new ProcessBuilder(f.getAbsolutePath()).start();
                        showNotification("Application Launch", "Launching " + appName, MessageType.INFO);
                        return;
                    }
                }

                // Try protocol handler for WhatsApp before other fallbacks
                if (lower.contains("whatsapp")) {
                    if (registryHasKey("HKEY_CLASSES_ROOT\\whatsapp")) {
                        try {
                            new ProcessBuilder("cmd", "/c", "start", "", "whatsapp:").start();
                            showNotification("Application Launch", "Launching WhatsApp via protocol", MessageType.INFO);
                            return;
                        } catch (IOException ex) {
                            // continue to other methods
                        }
                    }
                    // try whatsapp web via default browser as last resort
                    try {
                        new ProcessBuilder("cmd", "/c", "start", "", "https://web.whatsapp.com/").start();
                        showNotification("Application Launch", "Opening WhatsApp Web", MessageType.INFO);
                        return;
                    } catch (IOException ex) {
                        // ignore
                    }
                }

                // Check registry App Paths for common exes if not found yet
                if (lower.contains("chrome")) {
                    String reg = queryRegistryAppPath("chrome.exe");
                    if (reg != null) {
                        new ProcessBuilder(reg).start();
                        showNotification("Application Launch", "Launching " + appName, MessageType.INFO);
                        return;
                    }
                }
                if (lower.contains("whatsapp")) {
                    String reg = queryRegistryAppPath("WhatsApp.exe");
                    if (reg != null) {
                        new ProcessBuilder(reg).start();
                        showNotification("Application Launch", "Launching " + appName, MessageType.INFO);
                        return;
                    }
                }
                if (lower.contains("copilot")) {
                    String reg = queryRegistryAppPath("copilot.exe");
                    if (reg != null) {
                        new ProcessBuilder(reg).start();
                        showNotification("Application Launch", "Launching " + appName, MessageType.INFO);
                        return;
                    }
                }

                // If explicit mappings didn't find the app, fall back to recursive search across common program folders
                String[] commonPaths = { pf, pfx, local, System.getenv("APPDATA") };
                File found = null;
                for (String basePath : commonPaths) {
                    if (basePath == null) continue;
                    found = findApplicationRecursive(new File(basePath), appName);
                    if (found != null) break;
                }

                if (found != null) {
                    new ProcessBuilder(found.getAbsolutePath()).start();
                    showNotification("Application Launch", "Launching " + appName, MessageType.INFO);
                    return;
                }

                // Final fallback: use cmd start which will attempt associations and PATH
                new ProcessBuilder("cmd", "/c", "start", "", appName).start();
                showNotification("Application Launch", "Attempting to launch " + appName, MessageType.INFO);
                return;
            } else if (os.contains("mac")) {
                command = "open -a " + appName;
            } else {
                command = appName.toLowerCase();
            }
            // For non-Windows platforms, try a simple ProcessBuilder
            ProcessBuilder pb = new ProcessBuilder(command.split(" "));
            pb.start();
            showNotification("Application Launch", "Launching " + appName, MessageType.INFO);
        } catch (IOException e) {
            showNotification("Launch Failed", 
                           "Could not launch " + appName, 
                           MessageType.ERROR);
        }
    }

    // Removed unused method findApplication

    // Better recursive search returning first match (or null)
    /**
     * Recursively search a directory for an executable whose name contains the appName.
     */
    private File findApplicationRecursive(File dir, String appName) {
        if (dir == null || !dir.exists()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        String lower = appName.toLowerCase();
        for (File f : files) {
            try {
                if (f.isDirectory()) {
                    // If directory name contains appName, try to find exe inside
                    if (f.getName().toLowerCase().contains(lower)) {
                        File[] inner = f.listFiles((d, name) -> name.toLowerCase().endsWith(".exe") || name.toLowerCase().endsWith(".lnk"));
                        if (inner != null && inner.length > 0) return inner[0];
                    }
                    File found = findApplicationRecursive(f, appName);
                    if (found != null) return found;
                } else {
                    String nm = f.getName().toLowerCase();
                    if (nm.contains(lower) && (nm.endsWith(".exe") || nm.endsWith(".lnk"))) return f;
                }
            } catch (SecurityException se) {
                // skip unreadable directories
            }
        }
        return null;
    }

    // Try to find an executable via the Windows registry App Paths key
    /**
     * Query Windows registry App Paths for a known exe and return the full path if found.
     */
    private String queryRegistryAppPath(String exeName) {
        try {
            Process p = new ProcessBuilder("reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\" + exeName, "/ve").start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            Pattern pat = Pattern.compile("REG_\\w+\\s+(.*\\.exe)", Pattern.CASE_INSENSITIVE);
            while ((line = r.readLine()) != null) {
                Matcher m = pat.matcher(line);
                if (m.find()) {
                    return m.group(1).trim();
                }
            }
            p.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception ex) {
            // ignore
        }
        return null;
    }

    /**
     * Check whether a registry key exists (Windows only helper).
     */
    private boolean registryHasKey(String key) {
        try {
            Process p = new ProcessBuilder("reg", "query", key).start();
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    // Custom TaskListPanel class for day-level progress and checklist
    private class TaskListPanel extends JPanel {
        private final JProgressBar dayProgress;
        private final JPanel checklistPanel;

        public TaskListPanel() {
            setBackground(new Color(24, 24, 24));
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            setLayout(new BorderLayout(8,8));

            // Top: big progress display for the day
            JPanel headerPanel = new JPanel(new BorderLayout(4, 4));
            headerPanel.setBackground(new Color(24,24,24));
            
            // Title label in header
            JLabel title = new JLabel("Today's Progress");
            title.setForeground(Color.WHITE);
            title.setFont(new Font("Arial", Font.BOLD, 16));
            title.setHorizontalAlignment(SwingConstants.CENTER);
            headerPanel.add(title, BorderLayout.SOUTH);

            // Progress bar below title
            dayProgress = new JProgressBar(0, 100);
            dayProgress.setStringPainted(true);
            dayProgress.setForeground(new Color(41,128,185));
            dayProgress.setBackground(new Color(45,45,45));
            dayProgress.setPreferredSize(new Dimension(200, 36));

            JPanel progressPanel = new JPanel(new BorderLayout());
            progressPanel.setBackground(new Color(24,24,24));
            progressPanel.add(dayProgress, BorderLayout.CENTER);

            JPanel topPanel = new JPanel(new BorderLayout(8, 8));
            topPanel.setBackground(new Color(24,24,24));
            topPanel.add(headerPanel, BorderLayout.NORTH);
            topPanel.add(progressPanel, BorderLayout.CENTER);

            add(topPanel, BorderLayout.NORTH);

            // Center: checklist with tasks
            checklistPanel = new JPanel();
            checklistPanel.setLayout(new BoxLayout(checklistPanel, BoxLayout.Y_AXIS));
            checklistPanel.setBackground(new Color(24,24,24));

            JScrollPane scroll = new JScrollPane(checklistPanel);
            scroll.setBorder(null);
            scroll.getViewport().setBackground(new Color(24,24,24));
            add(scroll, BorderLayout.CENTER);

            refresh();
        }

        public void refresh() {
            SwingUtilities.invokeLater(() -> {
                checklistPanel.removeAll();
                LocalDate today = LocalDate.now();
                java.util.List<Task> todays = schedules.getOrDefault(today, Collections.emptyList());
                // Sort tasks by start time
                todays.sort((a, b) -> {
                    if (a.getStartTime() == null) return b.getStartTime() == null ? 0 : 1;
                    if (b.getStartTime() == null) return -1;
                    return a.getStartTime().compareTo(b.getStartTime());
                });

                int total = todays.size();
                int done = 0;
                for (Task t : todays) if (t.isDone()) done++;
                int percent = total == 0 ? 0 : (int) Math.round(100.0 * done / total);
                dayProgress.setValue(percent);
                dayProgress.setString(percent + "% completed (" + done + "/" + total + ")");

                for (Task t : todays) {
                    JCheckBox cb = new JCheckBox(t.getTitle() + (t.getStartTime()!=null? " ("+t.getStartTime().toString()+")":""));
                    cb.setSelected(t.isDone());
                    cb.setBackground(new Color(34,34,34));
                    cb.setForeground(t.isDone()? new Color(46,204,113): Color.WHITE);
                    cb.setFont(new Font("Arial", Font.PLAIN, 13));
                    cb.addActionListener(e -> {
                        t.setDone(cb.isSelected());
                        cb.setForeground(cb.isSelected()? new Color(46,204,113): Color.WHITE);
                        refresh();
                    });
                    JPanel row = new JPanel(new BorderLayout());
                    row.setBackground(Colors.PANEL_BG);
                    row.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
                    row.add(cb, BorderLayout.WEST);
                    checklistPanel.add(row);
                }

                checklistPanel.revalidate();
                checklistPanel.repaint();
            });
        }
    }

    // Task class has been moved to its own file (Task.java) for reuse by dialogs
}