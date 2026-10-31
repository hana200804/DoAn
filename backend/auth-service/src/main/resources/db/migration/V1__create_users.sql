CREATE TABLE users (
  id            CHAR(36)                  NOT NULL,
  email         VARCHAR(255)              NOT NULL,
  password_hash VARCHAR(100)              NOT NULL,
  display_name  VARCHAR(100)              NOT NULL,
  role          ENUM ('DRIVER', 'ADMIN')  NOT NULL DEFAULT 'DRIVER',
  vehicle_type  ENUM ('CAR', 'BUS', 'TRUCK') NOT NULL DEFAULT 'CAR',
  status        ENUM ('ACTIVE', 'LOCKED') NOT NULL DEFAULT 'ACTIVE',
  created_at    DATETIME(3)               NOT NULL,
  updated_at    DATETIME(3)               NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB;
