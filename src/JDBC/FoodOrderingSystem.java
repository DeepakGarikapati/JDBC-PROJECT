
package JDBC;

import java.sql.*;
import java.math.BigDecimal;
import java.util.Scanner;
import java.util.regex.Pattern;

public class FoodOrderingSystem {

    private static final String URL =
            "jdbc:mysql://localhost:3306/foodorderdb";

    private static final String USER = "root";
    private static final String PASSWORD = "Deepak@12345";

    private static Connection con;
    private static final Scanner sc = new Scanner(System.in);

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("[0-9]{10,15}");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public static void main(String[] args) {

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            con = DriverManager.getConnection(URL, USER, PASSWORD);

            System.out.println("Database connected successfully!");

            runMenu();

        } catch (ClassNotFoundException e) {
            System.out.println(
                    "MySQL JDBC driver not found. Add Connector/J to Eclipse.");

        } catch (SQLException e) {
            handleDatabaseError(e);

        } finally {
            try {
                if (con != null && !con.isClosed()) {
                    con.close();
                    System.out.println("Database connection closed.");
                }
            } catch (SQLException e) {
                System.out.println("Error closing connection: "
                        + e.getMessage());
            }

            sc.close();
        }
    }

    // Main menu
    private static void runMenu() {

        while (true) {
            System.out.println("\n===== FOOD ORDERING SYSTEM =====");
            System.out.println("1. Customer Registration");
            System.out.println("2. View Menu");
            System.out.println("3. Place Order");
            System.out.println("4. Order History");
            System.out.println("5. Bill Generation");
            System.out.println("6. Exit");

            int choice = readInt("Enter your choice: ", 1, 6);

            switch (choice) {
                case 1:
                    registerCustomer();
                    break;

                case 2:
                    viewMenu();
                    break;

                case 3:
                    placeOrder();
                    break;

                case 4:
                    orderHistory();
                    break;

                case 5:
                    generateBill();
                    break;

                case 6:
                    System.out.println("Thank you for using our system!");
                    return;

                default:
                    System.out.println("Invalid choice.");
            }
        }
    }

    // Read and validate integer input
    private static int readInt(String message, int min, int max) {

        while (true) {
            System.out.print(message);
            String input = sc.nextLine().trim();

            try {
                int value = Integer.parseInt(input);

                if (value < min || value > max) {
                    System.out.println(
                            "Enter a value between " + min + " and " + max + ".");
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

    // 1. Customer registration
    private static void registerCustomer() {

        System.out.println("\n--- Customer Registration ---");

        String name = readRequired("Enter customer name: ");

        if (name.length() > 100) {
            System.out.println("Name cannot exceed 100 characters.");
            return;
        }

        String phone;

        while (true) {
            System.out.print("Enter phone number (10-15 digits): ");
            phone = sc.nextLine().trim();

            if (PHONE_PATTERN.matcher(phone).matches()) {
                break;
            }

            System.out.println("Invalid phone number.");
        }

        String email;

        while (true) {
            System.out.print("Enter email (or press Enter to skip): ");
            email = sc.nextLine().trim();

            if (email.isEmpty()
                    || (email.length() <= 100
                    && EMAIL_PATTERN.matcher(email).matches())) {
                break;
            }

            System.out.println("Enter a valid email of at most 100 characters.");
        }

        String sql = "INSERT INTO customers "
                + "(customer_name, phone, email) VALUES (?, ?, ?)";

        try (PreparedStatement ps = con.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name);
            ps.setString(2, phone);

            if (email.isEmpty()) {
                ps.setNull(3, Types.VARCHAR);
            } else {
                ps.setString(3, email);
            }

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    System.out.println(
                            "Customer registered successfully!");
                    System.out.println(
                            "Customer ID: " + rs.getInt(1));
                }
            }

        } catch (SQLException e) {
            handleDatabaseError(e);
        }
    }

    // 2. Display menu
    private static void viewMenu() {

        String sql = "SELECT food_id, food_name, category, price, available "
                + "FROM menu ORDER BY food_id";

        System.out.println("--- Restaurant Menu ---");

        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.printf("%-8s %-25s %-18s %-12s %-12s%n",
                    "Food ID", "Food Name", "Category", "Price", "Available");

            System.out.println(
                    "--------------------------------------------------------------------------");

            boolean found = false;

            while (rs.next()) {
                found = true;

                System.out.printf("%-8d %-25s %-18s Rs. %-8s %-12s%n",
                        rs.getInt("food_id"),
                        rs.getString("food_name"),
                        rs.getString("category"),
                        rs.getBigDecimal("price").toPlainString(),
                        rs.getBoolean("available") ? "Yes" : "No");
            }

            if (!found) {
                System.out.println("No menu items found.");
            }

        } catch (SQLException e) {
            handleDatabaseError(e);
        }
    }

    // 3. Place an order using a database transaction
    private static void placeOrder() {

        System.out.println("--- Place Order ---");

        int customerId = readInt(
                "Enter customer ID: ", 1, Integer.MAX_VALUE);

        if (!customerExists(customerId)) {
            System.out.println(
                    "Customer ID not found. Please register first.");
            return;
        }

        viewMenu();

        int foodId = readInt(
                "Enter food ID: ", 1, Integer.MAX_VALUE);

        int quantity = readInt(
                "Enter quantity (1-1000): ", 1, 1000);

        boolean oldAutoCommit = true;
        boolean transactionStarted = false;

        try {
            oldAutoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            transactionStarted = true;

            BigDecimal price;
            String foodSql = "SELECT price FROM menu "
                    + "WHERE food_id = ? AND available = TRUE "
                    + "FOR UPDATE";

            try (PreparedStatement ps = con.prepareStatement(foodSql)) {
                ps.setInt(1, foodId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException(
                                "Food item does not exist or is unavailable.");
                    }

                    price = rs.getBigDecimal("price");
                }
            }

            if (price == null || price.signum() <= 0) {
                throw new IllegalArgumentException(
                        "Invalid food price.");
            }

            BigDecimal total = price.multiply(
                    BigDecimal.valueOf(quantity));

         
            String orderSql = "INSERT INTO orders "
                    + "(customer_id, total_amount, status) "
                    + "VALUES (?, ?, 'PLACED')";

            int orderId;

            try (PreparedStatement ps = con.prepareStatement(
                    orderSql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setInt(1, customerId);
                ps.setBigDecimal(2, total);

                int affected = ps.executeUpdate();

                if (affected != 1) {
                    throw new SQLException(
                            "Order record could not be created.");
                }
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException(
                                "Could not obtain the generated order ID.");
                    }

                    orderId = keys.getInt(1);
                }
            }
            String itemSql = "INSERT INTO order_items "
                    + "(order_id, food_id, quantity, item_price) "
                    + "VALUES (?, ?, ?, ?)";

            try (PreparedStatement ps = con.prepareStatement(itemSql)) {

                ps.setInt(1, orderId);
                ps.setInt(2, foodId);
                ps.setInt(3, quantity);
                ps.setBigDecimal(4, price);

                int affected = ps.executeUpdate();

                if (affected != 1) {
                    throw new SQLException(
                            "Order item could not be saved.");
                }
            }
            con.commit();

            System.out.println("\nOrder placed successfully!");
            System.out.println("Order ID: " + orderId);
            System.out.println("Food ID: " + foodId);
            System.out.println("Quantity: " + quantity);
            System.out.println("Price per item: Rs. " + price);
            System.out.println("Total amount: Rs. " + total);

        } catch (IllegalArgumentException e) {

            rollbackTransaction(transactionStarted);
            System.out.println("Validation error: " + e.getMessage());

        } catch (SQLException e) {

            rollbackTransaction(transactionStarted);
            handleDatabaseError(e);

        } finally {

            if (transactionStarted) {
                try {
                    con.setAutoCommit(oldAutoCommit);
                } catch (SQLException e) {
                    System.out.println(
                            "Warning: Could not restore auto-commit: "
                                    + e.getMessage());
                }
            }
        }
    }

    // Verify customer ID
    private static boolean customerExists(int customerId) {

        String sql = "SELECT 1 FROM customers WHERE customer_id = ?";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, customerId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            handleDatabaseError(e);
            return false;
        }
    }

    // Roll back incomplete transactions
    private static void rollbackTransaction(boolean started) {

        if (!started) {
            return;
        }

        try {
            con.rollback();
            System.out.println("Incomplete order rolled back.");

        } catch (SQLException e) {
            System.out.println(
                    "Rollback failed: " + e.getMessage());
        }
    }

    // 4. Order history for a customer
    private static void orderHistory() {

        System.out.println("\n--- Order History ---");

        int customerId = readInt(
                "Enter customer ID: ", 1, Integer.MAX_VALUE);

        String sql = "SELECT o.order_id, o.order_date, "
                + "o.total_amount, o.status, "
                + "m.food_name, oi.quantity, oi.item_price "
                + "FROM orders o "
                + "INNER JOIN order_items oi ON o.order_id = oi.order_id "
                + "INNER JOIN menu m ON oi.food_id = m.food_id "
                + "WHERE o.customer_id = ? "
                + "ORDER BY o.order_date DESC, o.order_id DESC";

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, customerId);

            try (ResultSet rs = ps.executeQuery()) {

                boolean found = false;
                int previousOrderId = -1;

                while (rs.next()) {
                    found = true;

                    int currentOrderId = rs.getInt("order_id");

                    if (currentOrderId != previousOrderId) {
                        System.out.println("\nOrder ID: " + currentOrderId);
                        System.out.println(
                                "Date: " + rs.getTimestamp("order_date"));
                        System.out.println(
                                "Status: " + rs.getString("status"));
                        System.out.println(
                                "Total: Rs. "
                                        + rs.getBigDecimal("total_amount"));

                        previousOrderId = currentOrderId;
                    }

                    System.out.println(
                            "  Item: " + rs.getString("food_name")
                            + " | Quantity: " + rs.getInt("quantity")
                            + " | Unit price: Rs. "
                            + rs.getBigDecimal("item_price"));
                }

                if (!found) {
                    System.out.println(
                            "No orders found for this customer.");
                }
            }

        } catch (SQLException e) {
            handleDatabaseError(e);
        }
    }

    // 5. Generate a bill for an order
    private static void generateBill() {

        System.out.println("\n--- Bill Generation ---");

        int orderId = readInt(
                "Enter order ID: ", 1, Integer.MAX_VALUE);

        String sql = "SELECT o.order_id, o.customer_id, "
                + "c.customer_name, c.phone, o.order_date, "
                + "o.status, o.total_amount, m.food_name, "
                + "oi.quantity, oi.item_price "
                + "FROM orders o "
                + "INNER JOIN customers c ON o.customer_id = c.customer_id "
                + "INNER JOIN order_items oi ON o.order_id = oi.order_id "
                + "INNER JOIN menu m ON oi.food_id = m.food_id "
                + "WHERE o.order_id = ? "
                + "ORDER BY oi.order_item_id";

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, orderId);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    System.out.println(
                            "Order not found, or the order has no items.");
                    return;
                }

                System.out.println("========== RESTAURANT BILL ==========");
                System.out.println("Order ID: " + rs.getInt("order_id"));
                System.out.println(
                        "Customer ID: " + rs.getInt("customer_id"));
                System.out.println(
                        "Customer: " + rs.getString("customer_name"));
                System.out.println("Phone: " + rs.getString("phone"));
                System.out.println(
                        "Date: " + rs.getTimestamp("order_date"));
                System.out.println("Status: " + rs.getString("status"));

                System.out.println("-------------------------------------");
                System.out.printf("%-22s %8s %12s%n",
                        "Food", "Quantity", "Unit Price");

                do {
                    String foodName = rs.getString("food_name");
                    int quantity = rs.getInt("quantity");
                    BigDecimal price = rs.getBigDecimal("item_price");

                    System.out.printf("%-22s %8d %12s%n",
                            foodName, quantity, price.toPlainString());

                } while (rs.next());
                System.out.println("-------------------------------------");

                BigDecimal total = getOrderTotal(orderId);

                if (total != null) {
                    System.out.println("TOTAL AMOUNT: Rs. " + total);
                }

                System.out.println("=====================================");
            }

        } catch (SQLException e) {
            handleDatabaseError(e);
        }
    }

    private static BigDecimal getOrderTotal(int orderId)
            throws SQLException {

        String sql = "SELECT total_amount FROM orders WHERE order_id = ?";

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, orderId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("total_amount");
                }
            }
        }

        return null;
    }

    // database error handling
    private static void handleDatabaseError(SQLException e) {

        System.out.println("Database operation failed.");
        System.out.println("SQL State: " + e.getSQLState());
        System.out.println("Error Code: " + e.getErrorCode());

        String state = e.getSQLState();
        int code = e.getErrorCode();

        if (code == 1062) {
            System.out.println(
                    "Duplicate value. Phone number or email may already exist.");

        } else if (code == 1452) {
            System.out.println(
                    "Foreign key violation. Check the referenced customer, "
                    + "order, or food ID.");

        } else if (code == 1451) {
            System.out.println(
                    "This record is referenced by another table.");

        } else if (state != null && state.startsWith("08")) {
            System.out.println(
                    "Database connection error. Check MySQL Server and JDBC settings.");

        } else if ("23000".equals(state)) {
            System.out.println(
                    "A database integrity constraint was violated.");

        } else {
            System.out.println("Details: " + e.getMessage());
        }
    }
}
