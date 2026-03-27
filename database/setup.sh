#!/bin/bash
# ChatFlow A3 — MySQL Setup Script
# Run on EC2-3 (consumer + MySQL co-located node)
# Usage: sudo bash setup.sh

set -euo pipefail

MYSQL_ROOT_PASS="rootpassword123"
CHATFLOW_DB="chatflow"
CHATFLOW_USER="chatflow"
CHATFLOW_PASS="chatflow123"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=== [1/6] Updating apt and installing MySQL 8.0 ==="
apt update -y
apt install -y mysql-server

echo "=== [2/6] Starting and enabling MySQL ==="
systemctl start mysql
systemctl enable mysql

echo "=== [3/6] Securing MySQL and setting root password ==="
mysql -u root <<SQL
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '${MYSQL_ROOT_PASS}';
DELETE FROM mysql.user WHERE User='';
DELETE FROM mysql.user WHERE User='root' AND Host NOT IN ('localhost', '127.0.0.1', '::1');
DROP DATABASE IF EXISTS test;
DELETE FROM mysql.db WHERE Db='test' OR Db='test\\_%';
FLUSH PRIVILEGES;
SQL

echo "=== [4/6] Creating database and chatflow user ==="
mysql -u root -p"${MYSQL_ROOT_PASS}" <<SQL
CREATE DATABASE IF NOT EXISTS ${CHATFLOW_DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${CHATFLOW_USER}'@'%' IDENTIFIED BY '${CHATFLOW_PASS}';
GRANT ALL PRIVILEGES ON ${CHATFLOW_DB}.* TO '${CHATFLOW_USER}'@'%';
FLUSH PRIVILEGES;
SQL

echo "=== [5/6] Applying schema ==="
mysql -u root -p"${MYSQL_ROOT_PASS}" "${CHATFLOW_DB}" < "${SCRIPT_DIR}/schema.sql"

echo "=== [6/6] Configuring remote access (bind-address) ==="
MYSQL_CONF="/etc/mysql/mysql.conf.d/mysqld.cnf"
if grep -q "^bind-address" "${MYSQL_CONF}"; then
    sed -i 's/^bind-address.*/bind-address = 0.0.0.0/' "${MYSQL_CONF}"
else
    echo "bind-address = 0.0.0.0" >> "${MYSQL_CONF}"
fi
systemctl restart mysql

echo "=== MySQL setup complete ==="
echo "  DB:   ${CHATFLOW_DB}"
echo "  User: ${CHATFLOW_USER}@%"
echo "  Port: 3306"
echo ""
echo "Remember to open port 3306 in the EC2 security group (internal only)."
