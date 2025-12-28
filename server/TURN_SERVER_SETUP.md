# Настройка своего TURN сервера (для обхода блокировок и NAT)

Если публичные STUN сервера (Google, Ekiga и др.) недоступны или заблокированы в вашем регионе, единственный надежный способ обеспечить видеосвязь — это поднять свой **TURN сервер** на том же VPS, где работает ваш Python сервер (`155.212.170.166`).

TURN сервер будет пересылать трафик звонков через себя, что гарантирует соединение даже за строгими фаерволами.

## 1. Установка Coturn (Ubuntu/Debian)

Зайдите на ваш сервер по SSH и выполните:

```bash
sudo apt-get update
sudo apt-get install coturn
```

## 2. Настройка

Остановите службу перед настройкой:
```bash
sudo systemctl stop coturn
```

Создайте резервную копию конфига и откройте новый:
```bash
sudo mv /etc/turnserver.conf /etc/turnserver.conf.backup
sudo nano /etc/turnserver.conf
```

Вставьте следующую конфигурацию (замените `155.212.170.166` на внешний IP вашего сервера, если он другой):

```ini
# Порт для прослушивания (стандартный)
listening-port=3478

# Внешний IP вашего сервера
external-ip=155.212.170.166

# Разрешаем UDP и TCP
listening-ip=0.0.0.0

# Аутентификация
lt-cred-mech
user=admin:password123
realm=messenger.example.com

# Логирование (опционально, для отладки)
log-file=/var/log/turnserver.log
verbose

# Безопасность (отключаем лишнее)
no-cli
no-tls
no-dtls
```

> **Важно:** Замените `user=admin:password123` на ваш желаемый логин и пароль.

## 3. Запуск

Включите автозапуск и запустите службу:

```bash
sudo sed -i 's/#TURNSERVER_ENABLED=1/TURNSERVER_ENABLED=1/g' /etc/default/coturn
sudo systemctl start coturn
sudo systemctl enable coturn
```

Проверьте статус:
```bash
sudo systemctl status coturn
```

## 4. Обновление Android приложения

Теперь откройте файл `WebRtcManager.kt` в Android Studio и раскомментируйте секцию TURN сервера, указав ваши данные:

```kotlin
// WebRtcManager.kt

PeerConnection.IceServer.builder("turn:155.212.170.166:3478")
    .setUsername("admin")       // Ваш логин из конфига
    .setPassword("password123") // Ваш пароль из конфига
    .createIceServer()
```

## 5. Проверка портов

Убедитесь, что порт `3478` (UDP и TCP) открыт на вашем сервере (в фаерволе ufw или security groups хостинга).

```bash
sudo ufw allow 3478/udp
sudo ufw allow 3478/tcp
```
