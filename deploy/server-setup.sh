#!/bin/bash
# Yangi Ubuntu 22.04/24.04 server uchun bir martalik tayyorlov (root yoki sudo bilan). Faqat tavsiya — o'qib, keyin ishga tushiring.
# Nima qiladi: Docker o'rnatadi, firewall (faqat SSH; HTTPS proxy kerak bo'lsa 80/443), avtomatik xavfsizlik yangilanishlari,
# swap (2 GB RAM server uchun), alohida "sellerflow" foydalanuvchisi.
set -euo pipefail
WITH_PROXY="${WITH_PROXY:-false}"

apt-get update
apt-get install -y ca-certificates curl ufw unattended-upgrades fail2ban gnupg
# Docker (rasmiy skript)
if ! command -v docker >/dev/null; then curl -fsSL https://get.docker.com | sh; fi

# Firewall: kiruvchi — faqat SSH (bot long polling ishlatadi, kiruvchi port shart emas)
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
if [ "$WITH_PROXY" = "true" ]; then ufw allow 80/tcp; ufw allow 443/tcp; fi
ufw --force enable
# Eslatma: Docker o'zi e'lon qilgan portlar ufw'ni chetlab o'tadi — shuning uchun compose'da app 127.0.0.1:8080 ga bog'langan.

dpkg-reconfigure -f noninteractive unattended-upgrades
systemctl enable --now fail2ban

# 2 GB RAM: 2 GB swap (OOM'dan himoya)
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

id sellerflow >/dev/null 2>&1 || useradd -m -s /bin/bash -G docker sellerflow
echo "Tayyor. Keyingi qadamlar: docs/PRODUCTION_DEPLOYMENT.md"
