import java.math.BigDecimal;
import java.sql.*;
import java.util.Scanner;

public class Main {

    static final String URL  = "jdbc:mysql://localhost:3306/sgiv_tornillo";
    static final String USER = System.getenv().getOrDefault("SGIV_DB_USER", "root");
    static final String PASS = System.getenv().getOrDefault("SGIV_DB_PASSWORD", "");
    static final Scanner in  = new Scanner(System.in);

    // ===================== CLASES =====================

    static class Producto {
        private final int id;
        private final String nombre;
        private final int stock;
        private final int minimo;
        private final BigDecimal precio;

        Producto(int id, String nombre, int stock, int minimo, BigDecimal precio) {
            this.id = id;
            this.nombre = nombre;
            this.stock = stock;
            this.minimo = minimo;
            this.precio = precio;
        }

        public int getId() { return id; }
        public int getStock() { return stock; }
        public int getMinimo() { return minimo; }
        public BigDecimal getPrecio() { return precio; }

        @Override
        public String toString() {
            return id + " | " + nombre + " | $" + precio +
                   " | Stock: " + stock + " | Mínimo: " + minimo +
                   (stock <= minimo ? " | REPONER" : "");
        }
    }

    // ===================== MÉTODOS DE ACCESO =====================

    static Producto buscarProducto(Connection c, int id, boolean bloquear) throws SQLException {
        String sql = "SELECT id_producto, nombre, stock_actual, stock_minimo, precio " +
                     "FROM producto WHERE id_producto = ?" + (bloquear ? " FOR UPDATE" : "");
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Producto inexistente.");
                return new Producto(
                    r.getInt("id_producto"),
                    r.getString("nombre"),
                    r.getInt("stock_actual"),
                    r.getInt("stock_minimo"),
                    r.getBigDecimal("precio")
                );
            }
        }
    }

    static void listarProductos(Connection c, boolean soloAlertas) throws SQLException {
        String sql = "SELECT id_producto, nombre, stock_actual, stock_minimo, precio FROM producto" +
                     (soloAlertas ? " WHERE stock_actual <= stock_minimo" : "") +
                     " ORDER BY nombre";
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            boolean alguno = false;
            while (r.next()) {
                alguno = true;
                System.out.println(new Producto(
                    r.getInt("id_producto"),
                    r.getString("nombre"),
                    r.getInt("stock_actual"),
                    r.getInt("stock_minimo"),
                    r.getBigDecimal("precio")
                ));
            }
            if (!alguno) System.out.println("Sin productos para mostrar.");
        }
    }

    static void registrarIngreso(Connection c, int idProducto, int cantidad) throws SQLException {
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");

        boolean anterior = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            Producto p = buscarProducto(c, idProducto, true);
            int nuevoStock = p.getStock() + cantidad;

            try (PreparedStatement u = c.prepareStatement(
                    "UPDATE producto SET stock_actual = ? WHERE id_producto = ?")) {
                u.setInt(1, nuevoStock);
                u.setInt(2, idProducto);
                u.executeUpdate();
            }

            c.commit();
            System.out.println("Ingreso registrado. Stock actual: " + nuevoStock +
                    (nuevoStock <= p.getMinimo() ? " (ALERTA: reponer)" : ""));
        } catch (Exception e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(anterior);
        }
    }

    static void registrarVenta(Connection c, int idUsuario, int idProducto, int cantidad) throws SQLException {
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");

        boolean anterior = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            Producto p = buscarProducto(c, idProducto, true);

            if (p.getStock() < cantidad) {
                throw new IllegalArgumentException("Stock insuficiente. Disponible: " + p.getStock());
            }

            BigDecimal total = p.getPrecio().multiply(BigDecimal.valueOf(cantidad));
            int nuevoStock = p.getStock() - cantidad;

            // 1. Insertar la venta
            int idVenta;
            try (PreparedStatement insVenta = c.prepareStatement(
                    "INSERT INTO venta (id_usuario, total_general) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                insVenta.setInt(1, idUsuario);
                insVenta.setBigDecimal(2, total);
                insVenta.executeUpdate();

                try (ResultSet keys = insVenta.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("No se pudo obtener el id de la venta.");
                    idVenta = keys.getInt(1);
                }
            }

            // 2. Insertar el detalle
            try (PreparedStatement insDet = c.prepareStatement(
                    "INSERT INTO detalles_ventas (id_venta, id_producto, cantidad, precio_unitario) VALUES (?, ?, ?, ?)")) {
                insDet.setInt(1, idVenta);
                insDet.setInt(2, idProducto);
                insDet.setInt(3, cantidad);
                insDet.setBigDecimal(4, p.getPrecio());
                insDet.executeUpdate();
            }

            // 3. Actualizar stock
            try (PreparedStatement upd = c.prepareStatement(
                    "UPDATE producto SET stock_actual = ? WHERE id_producto = ?")) {
                upd.setInt(1, nuevoStock);
                upd.setInt(2, idProducto);
                upd.executeUpdate();
            }

            c.commit();
            System.out.println("Venta registrada (ID: " + idVenta + "). Total: $" + total +
                    " | Stock actual: " + nuevoStock +
                    (nuevoStock <= p.getMinimo() ? " (ALERTA: reponer)" : ""));
        } catch (Exception e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(anterior);
        }
    }

    static void listarUsuarios(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT id_usuario, nombre, rol FROM usuario ORDER BY nombre")) {
            while (r.next()) {
                System.out.println(r.getInt("id_usuario") + " | " +
                                   r.getString("nombre") + " | " +
                                   r.getString("rol"));
            }
        }
    }

    // ===================== UTILIDADES =====================

    static int numero(String pregunta) {
        System.out.print(pregunta);
        return Integer.parseInt(in.nextLine().trim());
    }

    // ===================== MAIN =====================

    public static void main(String[] args) {
        try (Connection c = DriverManager.getConnection(URL, USER, PASS)) {

            int opcion;
            do {
                System.out.println("\n=== SGIV-Tornillo (Modelo Completo) ===");
                System.out.println("1. Listar productos");
                System.out.println("2. Consultar producto por código");
                System.out.println("3. Registrar venta");
                System.out.println("4. Registrar ingreso de mercadería");
                System.out.println("5. Ver alertas de stock");
                System.out.println("6. Listar usuarios/vendedores");
                System.out.println("0. Salir");

                try {
                    opcion = numero("Opción: ");

                    switch (opcion) {
                        case 1 -> listarProductos(c, false);
                        case 2 -> System.out.println(buscarProducto(c, numero("Código: "), false));
                        case 3 -> {
                            System.out.println("Usuarios disponibles:");
                            listarUsuarios(c);
                            int idUsuario = numero("ID Usuario (vendedor): ");
                            int idProd = numero("Código producto: ");
                            int cant = numero("Cantidad: ");
                            registrarVenta(c, idUsuario, idProd, cant);
                        }
                        case 4 -> {
                            int idProd = numero("Código producto: ");
                            int cant = numero("Cantidad a ingresar: ");
                            registrarIngreso(c, idProd, cant);
                        }
                        case 5 -> listarProductos(c, true);
                        case 6 -> listarUsuarios(c);
                        case 0 -> System.out.println("Fin del programa.");
                        default -> System.out.println("Opción inválida.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Error: debe ingresar un número entero.");
                    opcion = -1;
                } catch (IllegalArgumentException | SQLException e) {
                    System.out.println("Error: " + e.getMessage());
                    opcion = -1;
                }

            } while (opcion != 0);

        } catch (SQLException e) {
            System.err.println("No se pudo conectar a MySQL: " + e.getMessage());
        }
    }
}