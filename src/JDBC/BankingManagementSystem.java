
package JDBC;

import java.sql.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Scanner;
import java.util.regex.Pattern;

public class BankingManagementSystem {

    private static final String URL ="jdbc:mysql://localhost:3306/BankingDB_New";
    private static final String USER = "root";
    private static final String PASSWORD = "Deepak@12345";

    private static final BigDecimal MAX_AMOUNT =
            new BigDecimal("9999999999.99");

    private static Connection con;
    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            con = DriverManager.getConnection(URL, USER, PASSWORD);

            System.out.println("Banking database connected successfully!");

            runMenu();

        } catch (ClassNotFoundException e) {
            System.out.println(
                    "MySQL JDBC driver missing. Add Connector/J to Eclipse.");

        } catch (SQLException e) {
            handleDatabaseError(e);

        } finally {
            try {
                if (con != null && !con.isClosed()) {
                    con.close();
                }
            } catch (SQLException e) {
                System.out.println("Connection closing error: "
                        + e.getMessage());
            }

            sc.close();
        }
    }

    // MAIN MENU
    private static void runMenu() {

        while (true) {
            System.out.println("\n====== BANKING MANAGEMENT SYSTEM ======");
            System.out.println("1. Account Creation");
            System.out.println("2. Deposit");
            System.out.println("3. Withdrawal");
            System.out.println("4. Fund Transfer");
            System.out.println("5. Transaction History");
            System.out.println("6. View Accounts");
            System.out.println("7. Exit");

            int choice = readInt("Enter your choice: ", 1, 7);

            switch (choice) {
                case 1:
                    createAccount();
                    break;
                case 2:
                    deposit();
                    break;
                case 3:
                    withdraw();
                    break;
                case 4:
                    transferFunds();
                    break;
                case 5:
                    transactionHistory();
                    break;
                case 6:
                    viewAccounts();
                    break;
                case 7:
                    System.out.println("Thank you for banking with us!");
                    return;
            }
        }
    }

    // INPUT VALIDATION
    private static int readInt(String message, int min, int max) {

        while (true) {
            System.out.print(message);
            String input = sc.nextLine().trim();

            try {
                int value = Integer.parseInt(input);

                if (value < min || value > max) {
                    System.out.println(
                            "Enter a number between " + min + " and " + max);
                    continue;
                }

                return value;

            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Enter a whole number.");
            }
        }
    }

    private static String readRequired(String message) {

        while (true) {
            System.out.print(message);
            String value = sc.nextLine().trim();

            if (!value.isEmpty()) {
                return value;
            }

            System.out.println("This field cannot be empty.");
        }
    }

    private static BigDecimal readAmount() {

        while (true) {
            System.out.print("Enter amount (greater than zero): ");
            String input = sc.nextLine().trim();

            try {
                BigDecimal amount = new BigDecimal(input);

                if (amount.scale() > 2) {
                    System.out.println(
                            "Enter no more than two decimal places.");
                    continue;
                }

                amount = amount.setScale(2, RoundingMode.UNNECESSARY);

                if (amount.signum() <= 0
                        || amount.compareTo(MAX_AMOUNT) > 0) {
                    System.out.println("Amount is outside the allowed range.");
                    continue;
                }

                return amount;

            } catch (NumberFormatException
                    | ArithmeticException e) {
                System.out.println("Invalid amount. Example: 1500.00");
            }
        }
    }

    // DATABASE CHECK
    private static boolean customerExists(int customerId)
            throws SQLException {

        String sql =
                "SELECT 1 FROM customers WHERE customer_id = ?";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, customerId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // 1. ACCOUNT CREATION
    private static void createAccount() {

        System.out.println("\n--- Account Creation ---");

        int customerId = readInt(
                "Enter customer ID: ", 1, Integer.MAX_VALUE);

        String accountNumber;

        while (true) {
            accountNumber = readRequired(
                    "Enter account number (5-20 letters, digits or hyphens): ");

            if (Pattern.matches(
                    "[A-Za-z0-9-]{5,20}", accountNumber)) {
                break;
            }

            System.out.println("Invalid account number format.");
        }

        System.out.println("1. SAVINGS");
        System.out.println("2. CURRENT");

        int typeChoice = readInt("Select account type: ", 1, 2);

        String accountType =
                typeChoice == 1 ? "SAVINGS" : "CURRENT";

        String sql = "INSERT INTO accounts "
                + "(customer_id, account_number, account_type, balance) "
                + "VALUES (?, ?, ?, 0.00)";

        try {
            if (!customerExists(customerId)) {
                System.out.println("Customer ID does not exist.");
                return;
            }

            try (PreparedStatement ps = con.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setInt(1, customerId);
                ps.setString(2, accountNumber);
                ps.setString(3, accountType);

                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        System.out.println("Account created successfully!");
                        System.out.println(
                                "Account ID: " + rs.getInt(1));
                        System.out.println(
                                "Account Number: " + accountNumber);
                        System.out.println(
                                "Opening balance: Rs. 0.00");
                    }
                }
            }

        } catch (SQLException e) {
            handleDatabaseError(e);
        }
    }

    // 2. DEPOSIT
    private static void deposit() {

        System.out.println("--- Deposit ---");

        int accountId = readInt(
                "Enter account ID: ", 1, Integer.MAX_VALUE);

        BigDecimal amount = readAmount();

        executeBalanceTransaction(accountId, amount, "DEPOSIT");
    }

    // 3. WITHDRAWAL
    private static void withdraw() {

        System.out.println("\n--- Withdrawal ---");

        int accountId = readInt(
                "Enter account ID: ", 1, Integer.MAX_VALUE);

        BigDecimal amount = readAmount();

        executeBalanceTransaction(accountId, amount, "WITHDRAWAL");
    }

    // DEPOSIT AND WITHDRAWAL WITH TRANSACTION SAFETY
    private static void executeBalanceTransaction(
            int accountId, BigDecimal amount, String type) {

        boolean oldAutoCommit = true;
        boolean started = false;

        try {
            oldAutoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            started = true;

            BigDecimal balance;

            // Lock account while checking and updating balance
            String lockSql =
                    "SELECT balance FROM accounts "
                    + "WHERE account_id = ? FOR UPDATE";

            try (PreparedStatement ps = con.prepareStatement(lockSql)) {
                ps.setInt(1, accountId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException(
                                "Account ID does not exist.");
                    }

                    balance = rs.getBigDecimal("balance");
                }
            }

            BigDecimal newBalance;

            if ("DEPOSIT".equals(type)) {
                newBalance = balance.add(amount);
            } else {
                if (balance.compareTo(amount) < 0) {
                    throw new IllegalArgumentException(
                            "Insufficient balance. Available: Rs. " + balance);
                }

                newBalance = balance.subtract(amount);
            }

            if (newBalance.compareTo(MAX_AMOUNT) > 0) {
                throw new IllegalArgumentException(
                        "Balance exceeds the database limit.");
            }

            String updateSql =
                    "UPDATE accounts SET balance = ? WHERE account_id = ?";

            try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                ps.setBigDecimal(1, newBalance);
                ps.setInt(2, accountId);

                if (ps.executeUpdate() != 1) {
                    throw new SQLException("Account balance update failed.");
                }
            }

            insertTransaction(accountId, type, amount, null);

            con.commit();

            System.out.println(type + " successful!");
            System.out.println("Previous balance: Rs. " + balance);
            System.out.println("Transaction amount: Rs. " + amount);
            System.out.println("New balance: Rs. " + newBalance);

        } catch (IllegalArgumentException e) {
            rollback(started);
            System.out.println("Validation error: " + e.getMessage());

        } catch (SQLException e) {
            rollback(started);
            handleDatabaseError(e);

        } finally {
            restoreAutoCommit(oldAutoCommit, started);
        }
    }

    // 4. FUND TRANSFER
    private static void transferFunds() {

        System.out.println("\n--- Fund Transfer ---");

        int sourceId = readInt(
                "Enter source account ID: ", 1, Integer.MAX_VALUE);

        int destinationId = readInt(
                "Enter destination account ID: ", 1, Integer.MAX_VALUE);

        if (sourceId == destinationId) {
            System.out.println(
                    "Source and destination accounts must be different.");
            return;
        }

        BigDecimal amount = readAmount();

        boolean oldAutoCommit = true;
        boolean started = false;

        try {
            oldAutoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            started = true;

            BigDecimal sourceBalance = null;
            BigDecimal destinationBalance = null;

  
            String lockSql =
                    "SELECT account_id, balance FROM accounts "
                    + "WHERE account_id IN (?, ?) "
                    + "ORDER BY account_id FOR UPDATE";

            try (PreparedStatement ps = con.prepareStatement(lockSql)) {
                ps.setInt(1, sourceId);
                ps.setInt(2, destinationId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("account_id");
                        BigDecimal balance = rs.getBigDecimal("balance");

                        if (id == sourceId) {
                            sourceBalance = balance;
                        } else if (id == destinationId) {
                            destinationBalance = balance;
                        }
                    }
                }
            }

            if (sourceBalance == null) {
                throw new IllegalArgumentException(
                        "Source account does not exist.");
            }

            if (destinationBalance == null) {
                throw new IllegalArgumentException(
                        "Destination account does not exist.");
            }

            if (sourceBalance.compareTo(amount) < 0) {
                throw new IllegalArgumentException(
                        "Insufficient source balance: Rs. " + sourceBalance);
            }

            BigDecimal newSourceBalance =
                    sourceBalance.subtract(amount);

            BigDecimal newDestinationBalance =
                    destinationBalance.add(amount);

            if (newDestinationBalance.compareTo(MAX_AMOUNT) > 0) {
                throw new IllegalArgumentException(
                        "Destination balance would exceed the allowed limit.");
            }

            String updateSql =
                    "UPDATE accounts SET balance = ? WHERE account_id = ?";

            // Deduct money from source
            try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                ps.setBigDecimal(1, newSourceBalance);
                ps.setInt(2, sourceId);

                if (ps.executeUpdate() != 1) {
                    throw new SQLException(
                            "Source account update failed.");
                }
            }

            // Add money to destination
            try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                ps.setBigDecimal(1, newDestinationBalance);
                ps.setInt(2, destinationId);

                if (ps.executeUpdate() != 1) {
                    throw new SQLException(
                            "Destination account update failed.");
                }
            }

            // Record both sides of the transfer
            insertTransaction(
                    sourceId, "TRANSFER_OUT", amount, destinationId);

            insertTransaction(
                    destinationId, "TRANSFER_IN", amount, sourceId);

            // Both balances and both records commit together
            con.commit();

            System.out.println("Fund transfer successful!");
            System.out.println("Transferred: Rs. " + amount);
            System.out.println(
                    "Source balance: Rs. " + newSourceBalance);
            System.out.println(
                    "Destination balance: Rs. " + newDestinationBalance);

        } catch (IllegalArgumentException e) {
            rollback(started);
            System.out.println("Transfer failed: " + e.getMessage());

        } catch (SQLException e) {
            rollback(started);
            System.out.println(
                    "Transfer failed. Database changes rolled back.");
            handleDatabaseError(e);

        } finally {
            restoreAutoCommit(oldAutoCommit, started);
        }
    }

    // INSERT A TRANSACTION RECORD
    private static void insertTransaction(
            int accountId,
            String type,
            BigDecimal amount,
            Integer relatedAccountId) throws SQLException {

        String sql = "INSERT INTO transactions "
                + "(account_id, transaction_type, amount, related_account_id) "
                + "VALUES (?, ?, ?, ?)";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setString(2, type);
            ps.setBigDecimal(3, amount);

            if (relatedAccountId == null) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setInt(4, relatedAccountId);
            }

            if (ps.executeUpdate() != 1) {
                throw new SQLException(
                        "Transaction history record could not be saved.");
            }
        }
    }

    // 5. TRANSACTION HISTORY
    private static void transactionHistory() {

        System.out.println("\n--- Transaction History ---");

        int accountId = readInt(
                "Enter account ID: ", 1, Integer.MAX_VALUE);

        String sql = "SELECT transaction_id, account_id, "
                + "transaction_type, amount, related_account_id, "
                + "transaction_date FROM transactions "
                + "WHERE account_id = ? "
                + "ORDER BY transaction_date DESC, transaction_id DESC";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, accountId);

            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;

                System.out.printf(
                        "%-8s %-14s %-14s %-15s %-20s%n",
                        "Txn ID", "Type", "Amount", "Related Account", "Date");

                while (rs.next()) {
                    found = true;

                    int related = rs.getInt("related_account_id");
                    String relatedText = rs.wasNull()
                            ? "-"
                            : String.valueOf(related);

                    System.out.printf(
                            "%-8d %-14s %-14s %-15s %-20s%n",
                            rs.getInt("transaction_id"),
                            rs.getString("transaction_type"),
                            rs.getBigDecimal("amount"),
                            relatedText,
                            rs.getTimestamp("transaction_date"));
                }

                if (!found) {
                    System.out.println(
                            "No transactions found for this account.");
                }
            }

        } catch (SQLException e) {
            handleDatabaseError(e);
        }
    }

    // 6. VIEW ALL ACCOUNTS
    private static void viewAccounts() {

        String sql = "SELECT a.account_id, a.account_number, "
                + "a.account_type, a.balance, "
                + "c.customer_name, c.customer_id "
                + "FROM accounts a "
                + "JOIN customers c ON a.customer_id = c.customer_id "
                + "ORDER BY a.account_id";

        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("\n--- Bank Accounts ---");

            System.out.printf(
                    "%-8s %-15s %-10s %-15s %-12s%n",
                    "ID", "Account No.", "Type", "Balance", "Customer ID");

            boolean found = false;

            while (rs.next()) {
                found = true;

                System.out.printf(
                        "%-8d %-15s %-10s %-15s %-12d%n",
                        rs.getInt("account_id"),
                        rs.getString("account_number"),
                        rs.getString("account_type"),
                        rs.getBigDecimal("balance"),
                        rs.getInt("customer_id"));
            }

            if (!found) {
                System.out.println(
                        "No accounts exist. Create an account first.");
            }

        } catch (SQLException e) {
            handleDatabaseError(e);
        }
    }

    // ROLLBACK ON FAILURE
    private static void rollback(boolean started) {

        if (!started) {
            return;
        }

        try {
            con.rollback();
            System.out.println("Database transaction rolled back.");

        } catch (SQLException e) {
            System.out.println(
                    "Rollback failed: " + e.getMessage());
        }
    }

    private static void restoreAutoCommit(
            boolean oldAutoCommit, boolean started) {

        if (!started) {
            return;
        }

        try {
            con.setAutoCommit(oldAutoCommit);

        } catch (SQLException e) {
            System.out.println(
                    "Could not restore connection settings: "
                    + e.getMessage());
        }
    }

    // CENTRALIZED DATABASE EXCEPTION HANDLING
    private static void handleDatabaseError(SQLException e) {

        System.out.println("Database operation failed.");
        System.out.println("SQL State: " + e.getSQLState());
        System.out.println("Error Code: " + e.getErrorCode());

        if (e.getErrorCode() == 1062) {
            System.out.println(
                    "Duplicate account number, phone, or email.");

        } else if (e.getErrorCode() == 1452) {
            System.out.println(
                    "Referenced customer or account does not exist.");

        } else if (e.getErrorCode() == 1451) {
            System.out.println(
                    "Cannot delete a record referenced by another table.");

        } else if (e.getErrorCode() == 1205
                || e.getErrorCode() == 1213) {
            System.out.println(
                    "Database lock timeout or deadlock. Retry the operation.");

        } else if (e.getSQLState() != null
                && e.getSQLState().startsWith("08")) {
            System.out.println(
                    "Connection problem. Check MySQL Server and credentials.");

        } else if ("23000".equals(e.getSQLState())) {
            System.out.println(
                    "A database integrity constraint was violated.");

        } else {
            System.out.println("Details: " + e.getMessage());
        }
    }
}
