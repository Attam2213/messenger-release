#!/bin/bash

# Auto-setup script for Coturn TURN server
# Matches Android App Config: user=admin, password=password123

echo ">>> Installing Coturn..."
apt-get update
apt-get install -y coturn

echo ">>> Stopping Coturn for configuration..."
systemctl stop coturn

echo ">>> Backing up default config..."
mv /etc/turnserver.conf /etc/turnserver.conf.backup.$(date +%s)

echo ">>> Writing new configuration..."
EXTERNAL_IP="155.212.170.166"

cat <<EOF > /etc/turnserver.conf
# Basic Configuration
listening-port=3478
external-ip=$EXTERNAL_IP
listening-ip=0.0.0.0

# Authentication (Matches Android App)
lt-cred-mech
user=admin:password123
realm=messenger.example.com

# Relay Ports (Critical for Audio/Video)
min-port=49152
max-port=65535

# Logs
log-file=/var/log/turnserver.log
verbose

# Security
no-cli
no-tls
no-dtls

# Allow local testing
allow-loopback-peers
EOF

echo ">>> Enabling Coturn..."
sed -i 's/#TURNSERVER_ENABLED=1/TURNSERVER_ENABLED=1/g' /etc/default/coturn

echo ">>> Starting Coturn Service..."
systemctl start coturn
systemctl enable coturn

echo ">>> Opening Firewall Ports (UFW)..."
ufw allow 3478/udp
ufw allow 3478/tcp
ufw allow 49152:65535/udp
ufw allow 49152:65535/tcp

echo ">>> Status Check:"
systemctl status coturn --no-pager

echo ""
echo ">>> DONE! TURN Server is running on $EXTERNAL_IP:3478"
echo ">>> User: admin"
echo ">>> Password: password123"
