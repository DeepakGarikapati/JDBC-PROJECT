package JDBC;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Scanner;
import java.util.regex.Pattern;

public class EmployeeLeaveManagement {

    private static Connection con;
    private static final Scanner sc = new Scanner(System.in);

    private static final String URL ="jdbc:mysql://localhost:3306/EmployeeLeaveDB";
    private static final String USER = "root";
    private static final String PASSWORD = "Deepak@12345";

    private static final int INITIAL_LEAVES = 12;

    public static void main(String[] args) {

        try {
            con = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Database connected successfully!");

            boolean running = true;

            while (running) {
                System.out.println("===== EMPLOYEE LEAVE MANAGEMENT =====");
                System.out.println("1. Employee Registration");
                System.out.println("2. Apply Leave");
                System.out.println("3. Approve/Reject Leave");
                System.out.println("4. Leave Balance Tracking");
                System.out.println("5. View Leave Requests");
                System.out.println("6. Exit");

                int choice = readInt("Enter choice: ", 1, 6);

                switch (choice) {
                    case 1:
                        registerEmployee();
                        break;
                    case 2:
                        applyLeave();
                        break;
                    case 3:
                        processLeave();
                        break;
                    case 4:
                        viewLeaveBalance();
                        break;
                    case 5:
                        viewLeaveRequests();
                        break;
                    case 6:
                        running = false;
                        System.out.println("Thank you!");
                        break;
                    default:
                        System.out.println("Invalid choice.");
                }
            }

        } catch (SQLException e) {
            handleDatabaseError(e);
        } finally {
            try {
                if (con != null && !con.isClosed()) {
                    con.close();
                }
            } catch (SQLException e) {
                System.out.println("Unable to close database connection.");
            }
            sc.close();
        }
    }

    // ================= INPUT VALIDATIONS =================

    private static int readInt(String message, int min, int max) {
        while (true) {
            System.out.print(message);
            String input = sc.nextLine().trim();

            try {
                int value = Integer.parseInt(input);

                if (value < min || value > max) {
                    System.out.println(
                            "Enter a number between " + min + " and " + max + ".");
                    continue;
                }

                return value;

            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Enter a whole number.");
            }
        }
    }

    private static String readText(String message, int maxLength) {
        while (true) {
            System.out.print(message);
            String value = sc.nextLine().trim();

            if (value.isEmpty()) {
                System.out.println("This field cannot be empty.");
            } else if (value.length() > maxLength) {
                System.out.println(
                        "Maximum allowed length is " + maxLength + ".");
            } else {
                return value;
            }
        }
    }

    private static String readName() {
        while (true) {
            String name = readText("Enter employee name: ", 100);

            if (!Pattern.matches("[\\p{L}][\\p{L} .'-]*", name)) {
                System.out.println(
                        "Name must contain valid letters, spaces, dots, "
                        + "apostrophes or hyphens.");
                continue;
            }

            return name;
        }
    }

    private static String readEmail() {
        while (true) {
            String email = readText("Enter email: ", 100);

            if (!Pattern.matches(
                    "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                    email)) {
                System.out.println("Enter a valid email address.");
                continue;
            }

            return email;
        }
    }

    private static String readPhone() {
        while (true) {
            String phone = readText("Enter 10-digit phone: ", 15);

            if (!phone.matches("[0-9]{10}")) {
                System.out.println(
                        "Phone number must contain exactly 10 digits.");
                continue;
            }

            return phone;
        }
    }

    private static LocalDate readDate(String message) {
        while (true) {
            System.out.print(message);
            String input = sc.nextLine().trim();

            try {
                return LocalDate.parse(input);
            } catch (DateTimeParseException e) {
                System.out.println(
                        "Invalid date. Enter the date in YYYY-MM-DD format.");
            }
        }
    }

    private static boolean employeeExists(int employeeId)
            throws SQLException {

        String sql =
                "SELECT employee_id FROM employees WHERE employee_id = ?";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, employeeId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void rollbackQuietly() {
        try {
            if (con != null && !con.isClosed()) {
                con.rollback();
            }
        } catch (SQLException e) {
            System.out.println("Rollback failed: " + e.getMessage());
        }
    }

    private static void handleDatabaseError(SQLException e) {
        String state = e.getSQLState();

        if ("23000".equals(state)) {
            System.out.println(
                    "Duplicate value or database constraint violation.");
        } else if (state != null && state.startsWith("08")) {
            System.out.println(
                    "Database connection error. Check MySQL is running.");
        } else {
            System.out.println("Database operation failed: " + e.getMessage());
        }
    }

    //EMPLOYEE REGISTRATION

    private static void registerEmployee() {

        String name = readName();
        String email = readEmail();
        String phone = readPhone();
        String department = readText("Enter department: ", 50);

        boolean oldAutoCommit = true;

        try {
            oldAutoCommit = con.getAutoCommit();
            con.setAutoCommit(false);

            int employeeId;

            String employeeSql =
                    "INSERT INTO employees "
                    + "(employee_name, email, phone, department) "
                    + "VALUES (?, ?, ?, ?)";

            try (PreparedStatement ps = con.prepareStatement(
                    employeeSql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, name);
                ps.setString(2, email);
                ps.setString(3, phone);
                ps.setString(4, department);

                if (ps.executeUpdate() != 1) {
                    throw new SQLException("Employee registration failed.");
                }

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Employee ID was not generated.");
                    }
                    employeeId = keys.getInt(1);
                }
            }

            String balanceSql =
                    "INSERT INTO leave_balance "
                    + "(employee_id, total_leaves, used_leaves, available_leaves) "
                    + "VALUES (?, ?, 0, ?)";

            try (PreparedStatement ps = con.prepareStatement(balanceSql)) {
                ps.setInt(1, employeeId);
                ps.setInt(2, INITIAL_LEAVES);
                ps.setInt(3, INITIAL_LEAVES);

                if (ps.executeUpdate() != 1) {
                    throw new SQLException("Leave balance creation failed.");
                }
            }

            con.commit();

            System.out.println("Employee registered successfully!");
            System.out.println("Employee ID: " + employeeId);
            System.out.println("Initial leave balance: "
                    + INITIAL_LEAVES + " days.");

        } catch (SQLException e) {
            rollbackQuietly();

            if ("23000".equals(e.getSQLState())) {
                System.out.println(
                        "Registration failed: email or phone may already exist.");
            } else {
                handleDatabaseError(e);
            }

        } finally {
            try {
                if (con != null && !con.isClosed()) {
                    con.setAutoCommit(oldAutoCommit);
                }
            } catch (SQLException e) {
                System.out.println("Could not restore transaction settings.");
            }
        }
    }

    // APPLY LEAVE

    private static void applyLeave() {

        try {
            int employeeId =
                    readInt("Enter employee ID: ", 1, Integer.MAX_VALUE);

            if (!employeeExists(employeeId)) {
                System.out.println("Employee not found. Register first.");
                return;
            }

            String leaveType;

            while (true) {
                leaveType = readText(
                        "Leave type (CASUAL/SICK/EARNED): ", 30)
                        .toUpperCase();

                if (leaveType.equals("CASUAL")
                        || leaveType.equals("SICK")
                        || leaveType.equals("EARNED")) {
                    break;
                }

                System.out.println(
                        "Invalid leave type. Choose CASUAL, SICK or EARNED.");
            }

            LocalDate startDate =
                    readDate("Enter start date (YYYY-MM-DD): ");

            LocalDate endDate =
                    readDate("Enter end date (YYYY-MM-DD): ");

            if (startDate.isBefore(LocalDate.now())) {
                System.out.println("Start date cannot be in the past.");
                return;
            }

            if (endDate.isBefore(startDate)) {
                System.out.println(
                        "End date cannot be earlier than the start date.");
                return;
            }

            long requestedDays =
                    ChronoUnit.DAYS.between(startDate, endDate) + 1;

            if (requestedDays > Integer.MAX_VALUE) {
                System.out.println("Requested leave duration is too long.");
                return;
            }

            String reason = readText("Enter reason for leave: ", 255);

            // Prevent overlap with existing pending or approved requests.
            String overlapSql =
                    "SELECT request_id FROM leave_requests "
                    + "WHERE employee_id = ? "
                    + "AND status IN ('PENDING', 'APPROVED') "
                    + "AND start_date <= ? AND end_date >= ?";

            try (PreparedStatement ps = con.prepareStatement(overlapSql)) {
                ps.setInt(1, employeeId);
                ps.setDate(2, Date.valueOf(endDate));
                ps.setDate(3, Date.valueOf(startDate));

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println(
                                "Leave dates overlap with an existing "
                                + "pending or approved request.");
                        return;
                    }
                }
            }

            String sql =
                    "INSERT INTO leave_requests "
                    + "(employee_id, leave_type, start_date, end_date, "
                    + "days_requested, reason, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 'PENDING')";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, employeeId);
                ps.setString(2, leaveType);
                ps.setDate(3, Date.valueOf(startDate));
                ps.setDate(4, Date.valueOf(endDate));
                ps.setInt(5, (int) requestedDays);
                ps.setString(6, reason);

                if (ps.executeUpdate() == 1) {
                    System.out.println("Leave request submitted successfully.");
                    System.out.println("Days requested: " + requestedDays);
                    System.out.println("Status: PENDING");
                }
            }

        } catch (SQLException e) {
            handleDatabaseError(e);
        }
    }

    // APPROVE / REJECT LEAVE 

    private static void processLeave() {

        boolean oldAutoCommit = true;

        try {
            int requestId =
                    readInt("Enter leave request ID: ", 1, Integer.MAX_VALUE);

            String decision;

            while (true) {
                decision = readText(
                        "Decision (APPROVED/REJECTED): ", 20)
                        .toUpperCase();

                if (decision.equals("APPROVED")
                        || decision.equals("REJECTED")) {
                    break;
                }

                System.out.println("Enter APPROVED or REJECTED.");
            }

            oldAutoCommit = con.getAutoCommit();
            con.setAutoCommit(false);

            int employeeId;
            int days;
            LocalDate startDate;
            LocalDate endDate;
            String status;

            String selectSql =
                    "SELECT employee_id, days_requested, start_date, "
                    + "end_date, status FROM leave_requests "
                    + "WHERE request_id = ? FOR UPDATE";

            try (PreparedStatement ps = con.prepareStatement(selectSql)) {
                ps.setInt(1, requestId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException(
                                "Leave request ID does not exist.");
                    }

                    employeeId = rs.getInt("employee_id");
                    days = rs.getInt("days_requested");
                    startDate = rs.getDate("start_date").toLocalDate();
                    endDate = rs.getDate("end_date").toLocalDate();
                    status = rs.getString("status");
                }
            }

            if (!"PENDING".equals(status)) {
                throw new IllegalArgumentException(
                        "This request has already been processed.");
            }

            if ("APPROVED".equals(decision)) {
                String overlapSql =
                        "SELECT request_id FROM leave_requests "
                        + "WHERE employee_id = ? AND request_id <> ? "
                        + "AND status = 'APPROVED' "
                        + "AND start_date <= ? AND end_date >= ?";

                try (PreparedStatement ps = con.prepareStatement(overlapSql)) {
                    ps.setInt(1, employeeId);
                    ps.setInt(2, requestId);
                    ps.setDate(3, Date.valueOf(endDate));
                    ps.setDate(4, Date.valueOf(startDate));

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            throw new IllegalArgumentException(
                                    "These dates overlap with approved leave.");
                        }
                    }
                }

                int available;

                String balanceSql =
                        "SELECT available_leaves FROM leave_balance "
                        + "WHERE employee_id = ? FOR UPDATE";

                try (PreparedStatement ps =
                        con.prepareStatement(balanceSql)) {
                    ps.setInt(1, employeeId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException(
                                    "Leave balance record not found.");
                        }

                        available = rs.getInt("available_leaves");
                    }
                }

                if (available < days) {
                    throw new IllegalArgumentException(
                            "Insufficient leave balance. Available: "
                                    + available + " days.");
                }

                String updateBalance =
                        "UPDATE leave_balance "
                        + "SET used_leaves = used_leaves + ?, "
                        + "available_leaves = available_leaves - ? "
                        + "WHERE employee_id = ? AND available_leaves >= ?";

                try (PreparedStatement ps =
                        con.prepareStatement(updateBalance)) {
                    ps.setInt(1, days);
                    ps.setInt(2, days);
                    ps.setInt(3, employeeId);
                    ps.setInt(4, days);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                                "Leave balance update failed.");
                    }
                }
            }

            String updateRequest =
                    "UPDATE leave_requests SET status = ? "
                    + "WHERE request_id = ? AND status = 'PENDING'";

            try (PreparedStatement ps =
                    con.prepareStatement(updateRequest)) {
                ps.setString(1, decision);
                ps.setInt(2, requestId);

                if (ps.executeUpdate() != 1) {
                    throw new SQLException(
                            "Leave request status update failed.");
                }
            }

            con.commit();

            System.out.println("Leave request " + decision + " successfully.");

            if ("APPROVED".equals(decision)) {
                System.out.println(days + " leave day(s) deducted.");
            } else {
                System.out.println("Leave balance remains unchanged.");
            }

        } catch (IllegalArgumentException e) {
            rollbackQuietly();
            System.out.println("Cannot process request: " + e.getMessage());

        } catch (SQLException e) {
            rollbackQuietly();
            handleDatabaseError(e);

        } finally {
            try {
                if (con != null && !con.isClosed()) {
                    con.setAutoCommit(oldAutoCommit);
                }
            } catch (SQLException e) {
                System.out.println("Could not restore transaction settings.");
            }
        }
    }

    // LEAVE BALANCE TRACKING

    private static void viewLeaveBalance() {

        try {
            int employeeId =
                    readInt("Enter employee ID: ", 1, Integer.MAX_VALUE);

            String sql =
                    "SELECT e.employee_name, lb.total_leaves, "
                    + "lb.used_leaves, lb.available_leaves "
                    + "FROM employees e "
                    + "JOIN leave_balance lb "
                    + "ON e.employee_id = lb.employee_id "
                    + "WHERE e.employee_id = ?";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, employeeId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println(
                                "Employee or leave balance not found.");
                        return;
                    }

                    System.out.println("\n===== LEAVE BALANCE =====");
                    System.out.println("Employee: "
                            + rs.getString("employee_name"));
                    System.out.println("Total leaves: "
                            + rs.getInt("total_leaves"));
                    System.out.println("Used leaves: "
                            + rs.getInt("used_leaves"));
                    System.out.println("Available leaves: "
                            + rs.getInt("available_leaves"));
                }
            }

        } catch (SQLException e) {
            handleDatabaseError(e);
        }
    }

    // VIEW LEAVE REQUEST

    private static void viewLeaveRequests() {

        String sql =
                "SELECT lr.request_id, e.employee_name, lr.leave_type, "
                + "lr.start_date, lr.end_date, lr.days_requested, "
                + "lr.reason, lr.status "
                + "FROM leave_requests lr "
                + "JOIN employees e ON lr.employee_id = e.employee_id "
                + "ORDER BY lr.request_id DESC";

        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            boolean found = false;

            System.out.println("========== LEAVE REQUESTS ==========");

            while (rs.next()) {
                found = true;

                System.out.println("Request ID: "
                        + rs.getInt("request_id"));
                System.out.println("Employee: "
                        + rs.getString("employee_name"));
                System.out.println("Leave type: "
                        + rs.getString("leave_type"));
                System.out.println("Start date: "
                        + rs.getDate("start_date"));
                System.out.println("End date: "
                        + rs.getDate("end_date"));
                System.out.println("Days requested: "
                        + rs.getInt("days_requested"));
                System.out.println("Reason: "
                        + rs.getString("reason"));
                System.out.println("Status: "
                        + rs.getString("status"));
                System.out.println("------------------------------------");
            }

            if (!found) {
                System.out.println("No leave requests found.");
            }

        } catch (SQLException e) {
            handleDatabaseError(e);
        }
    }
}

