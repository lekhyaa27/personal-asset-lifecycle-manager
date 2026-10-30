-- Personal Asset Lifecycle Manager - reference schema (MySQL 8)
-- NOTE: the app creates these tables automatically (spring.jpa.hibernate.ddl-auto=update).
-- Run this file only if you want to create them manually in MySQL Workbench.
CREATE DATABASE IF NOT EXISTS personal_asset_lifecycle_manager;
USE personal_asset_lifecycle_manager;

CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  full_name VARCHAR(100) NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  created_at DATETIME
);
CREATE TABLE assets (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  name VARCHAR(150) NOT NULL, category VARCHAR(50) NOT NULL,
  brand VARCHAR(100), model VARCHAR(100), serial_number VARCHAR(100),
  purchase_price DECIMAL(12,2) NOT NULL, purchase_date DATE NOT NULL, purchase_location VARCHAR(150),
  asset_condition VARCHAR(20), useful_life_years INT, salvage_value DECIMAL(12,2),
  description VARCHAR(1000), notes VARCHAR(1000),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', disposed_date DATE, created_at DATETIME,
  FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE warranty_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, asset_id BIGINT NOT NULL UNIQUE,
  provider VARCHAR(150), start_date DATE NOT NULL, end_date DATE NOT NULL,
  FOREIGN KEY (asset_id) REFERENCES assets(id)
);
CREATE TABLE insurance_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, asset_id BIGINT NOT NULL UNIQUE,
  provider VARCHAR(150), policy_number VARCHAR(100), start_date DATE NOT NULL, end_date DATE NOT NULL,
  FOREIGN KEY (asset_id) REFERENCES assets(id)
);
CREATE TABLE maintenance_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, asset_id BIGINT NOT NULL,
  maintenance_date DATE NOT NULL, maintenance_type VARCHAR(50) NOT NULL, description VARCHAR(1000),
  provider VARCHAR(150), cost DECIMAL(12,2) NOT NULL, next_maintenance_date DATE, notes VARCHAR(1000),
  FOREIGN KEY (asset_id) REFERENCES assets(id)
);
CREATE TABLE repair_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, asset_id BIGINT NOT NULL,
  repair_date DATE NOT NULL, problem_description VARCHAR(1000) NOT NULL, repair_type VARCHAR(50),
  provider VARCHAR(150), cost DECIMAL(12,2) NOT NULL, parts_replaced VARCHAR(500),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING', completion_date DATE, notes VARCHAR(1000),
  FOREIGN KEY (asset_id) REFERENCES assets(id)
);
CREATE TABLE upgrade_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, asset_id BIGINT NOT NULL,
  upgrade_date DATE NOT NULL, description VARCHAR(1000) NOT NULL, cost DECIMAL(12,2) NOT NULL,
  provider VARCHAR(150), notes VARCHAR(1000),
  FOREIGN KEY (asset_id) REFERENCES assets(id)
);
CREATE TABLE lifecycle_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, asset_id BIGINT NOT NULL,
  event_type VARCHAR(30) NOT NULL, description VARCHAR(500) NOT NULL,
  old_status VARCHAR(20), new_status VARCHAR(20), event_time DATETIME NOT NULL,
  FOREIGN KEY (asset_id) REFERENCES assets(id)
);
CREATE TABLE asset_documents (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, asset_id BIGINT NOT NULL,
  doc_type VARCHAR(50) NOT NULL, original_name VARCHAR(255) NOT NULL, stored_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(100), size_bytes BIGINT, uploaded_at DATETIME,
  FOREIGN KEY (asset_id) REFERENCES assets(id)
);

-- Example queries used by the app (DBMS concepts)
-- JOIN + GROUP BY + SUM: maintenance cost per asset
SELECT a.name, SUM(m.cost) AS total FROM maintenance_records m JOIN assets a ON a.id = m.asset_id
WHERE a.user_id = 1 GROUP BY a.name ORDER BY total DESC;
-- Total lifecycle cost per asset
SELECT a.id, a.name, a.purchase_price + COALESCE((SELECT SUM(cost) FROM maintenance_records WHERE asset_id=a.id),0)
  + COALESCE((SELECT SUM(cost) FROM repair_records WHERE asset_id=a.id),0)
  + COALESCE((SELECT SUM(cost) FROM upgrade_records WHERE asset_id=a.id),0) AS lifecycle_cost
FROM assets a WHERE a.user_id = 1;
-- Warranties expiring in the next 30 days
SELECT a.name, w.end_date FROM warranty_records w JOIN assets a ON a.id = w.asset_id
WHERE a.user_id = 1 AND w.end_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY);
-- Assets per category
SELECT category, COUNT(*) FROM assets WHERE user_id = 1 AND status <> 'DISPOSED' GROUP BY category;
