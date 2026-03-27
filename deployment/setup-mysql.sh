#!/bin/bash
# Setup MySQL 8.0 on Amazon Linux 2023 / Ubuntu EC2 instance
# Run as: sudo bash setup-mysql.sh
# After running, apply schema: mysql -u chatflow -pchatflow123 chatflow < /path/to/schema.sql

set -e

echo "=== Detecting OS ==="
if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS=$ID
else
    OS="unknown"
fi

echo "OS: $OS"

install_mysql_amazon_linux() {
    echo "=== Installing MySQL 8.0 (Amazon Linux) ==="
    # Add MySQL 8.0 repo
    dnf install -y https://dev.mysql.com/get/mysql80-community-release-el9-5.noarch.rpm || \
        yum install -y https://dev.mysql.com/get/mysql80-community-release-el9-5.noarch.rpm
    dnf install -y mysql-community-server || yum install -y mysql-community-server
}

install_mysql_ubuntu() {
    echo "=== Installing MySQL 8.0 (Ubuntu) ==="
    apt-get update
    apt-get install -y mysql-server
}

case "$OS" in
    amzn|rhel|centos|fedora)
        install_mysql_amazon_linux
        ;;
    ubuntu|debian)
        install_mysql_ubuntu
        ;;
    *)
        echo "Unsupported OS: $OS — trying apt-get fallback"
        apt-get update && apt-get install -y mysql-server
        ;;
esac

echo "=== Starting MySQL ==="
systemctl enable mysqld || systemctl enable mysql
systemctl start mysqld || systemctl start mysql

# Get temporary root password (Amazon Linux MySQL)
if [ -f /var/log/mysqld.log ]; then
    TEMP_PASS=$(grep 'temporary password' /var/log/mysqld.log | tail -1 | awk '{print $NF}')
    echo "Temporary root password: $TEMP_PASS"
fi

echo "=== Configuring MySQL for remote access ==="
# Set bind-address to 0.0.0.0 to allow remote connections from server-v3
MYSQL_CONF=""
for conf in /etc/mysql/mysql.conf.d/mysqld.cnf /etc/my.cnf /etc/mysql/my.cnf; do
    if [ -f "$conf" ]; then
        MYSQL_CONF=$conf
        break
    fi
done

if [ -n "$MYSQL_CONF" ]; then
    if grep -q "bind-address" "$MYSQL_CONF"; then
        sed -i 's/^bind-address\s*=.*/bind-address = 0.0.0.0/' "$MYSQL_CONF"
    else
        echo "bind-address = 0.0.0.0" >> "$MYSQL_CONF"
    fi
    echo "Updated bind-address in $MYSQL_CONF"
fi

systemctl restart mysqld || systemctl restart mysql

echo ""
echo "=== MANUAL STEPS REQUIRED ==="
echo "Run the following commands as root/admin:"
echo ""
echo "  sudo mysql"
echo ""
echo "  -- If using temp password (Amazon Linux), first:"
echo "  ALTER USER 'root'@'localhost' IDENTIFIED BY 'YourNewRootPass!';"
echo ""
echo "  -- Create database and user:"
echo "  CREATE DATABASE IF NOT EXISTS chatflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
echo "  CREATE USER IF NOT EXISTS 'chatflow'@'%' IDENTIFIED BY 'chatflow123';"
echo "  GRANT ALL PRIVILEGES ON chatflow.* TO 'chatflow'@'%';"
echo "  FLUSH PRIVILEGES;"
echo "  EXIT;"
echo ""
echo "  -- Apply schema:"
echo "  mysql -u chatflow -pchatflow123 chatflow < /home/ec2-user/schema.sql"
echo ""
echo "=== Opening port 3306 in firewall (if firewalld active) ==="
if systemctl is-active --quiet firewalld; then
    firewall-cmd --permanent --add-port=3306/tcp
    firewall-cmd --reload
    echo "Port 3306 opened."
else
    echo "firewalld not active — configure EC2 Security Group to allow port 3306 from server-v3 private IP."
fi

echo ""
echo "=== MySQL Setup Complete ==="
echo "Version: $(mysqld --version 2>/dev/null || mysql --version)"
