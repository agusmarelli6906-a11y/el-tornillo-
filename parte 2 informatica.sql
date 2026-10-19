CREATE DATABASE IF NOT EXISTS sgiv_tornillo;
USE sgiv_tornillo;

-- 1. Proveedor
CREATE TABLE proveedor (
    id_proveedor INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    telefono VARCHAR(30)
);

-- 2. Usuario
CREATE TABLE usuario (
    id_usuario INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    rol VARCHAR(50) NOT NULL
);

-- 3. Producto
CREATE TABLE producto (
    id_producto INT AUTO_INCREMENT PRIMARY KEY,
    id_proveedor INT NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    precio DECIMAL(12,2) NOT NULL,
    stock_actual INT NOT NULL,
    stock_minimo INT NOT NULL,
    CONSTRAINT fk_producto_proveedor 
        FOREIGN KEY (id_proveedor) REFERENCES proveedor(id_proveedor)
);

-- 4. Venta
CREATE TABLE venta (
    id_venta INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_general DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_venta_usuario 
        FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
);

-- 5. Detalles de venta (completado)
CREATE TABLE detalles_ventas (
    id_detalles INT AUTO_INCREMENT PRIMARY KEY,
    id_venta INT NOT NULL,
    id_producto INT NOT NULL,
    cantidad INT NOT NULL,
    precio_unitario DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_detalles_venta 
        FOREIGN KEY (id_venta) REFERENCES venta(id_venta),
    CONSTRAINT fk_detalles_producto 
        FOREIGN KEY (id_producto) REFERENCES producto(id_producto)
);

-- Datos de prueba
INSERT INTO proveedor (nombre, telefono) VALUES 
('Ferretería Mayorista S.A.', '0342-4567891'),
('Distribuidora Herramientas', '0342-1234567');

INSERT INTO usuario (nombre, rol) VALUES 
('Carlos Gómez', 'Vendedor'),
('Ana López', 'Vendedor');

INSERT INTO producto (id_proveedor, nombre, precio, stock_actual, stock_minimo) VALUES
(1, 'Taladro Percutor 500W', 45500.50, 10, 2),
(1, 'Martillo de uña', 8500.00, 25, 5),
(2, 'Cinta métrica 5m', 3200.00, 40, 10),
(2, 'Destornillador Phillips', 2100.00, 3, 5);