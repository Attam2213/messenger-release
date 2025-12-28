# Инструкция по развертыванию сервера на Debian 11 (VDS)

## 1. Подготовка окружения
Зайдите на сервер по SSH и выполните следующие команды:

```bash
# Обновляем пакеты
sudo apt update && sudo apt upgrade -y

# Устанавливаем Python 3 и pip (если не установлены)
sudo apt install python3 python3-pip -y
```

## 2. Загрузка файлов
Создайте папку для сервера и загрузите туда файлы `server.py` и `requirements.txt`.
Можно использовать SCP, FileZilla или создать файлы через nano.

```bash
mkdir -p /opt/messenger-server
cd /opt/messenger-server
# (Тут загрузите файлы)
```

## 3. Установка зависимостей

```bash
pip3 install -r requirements.txt
```

## 4. Запуск сервера (Тестовый режим)
Чтобы проверить, что все работает:

```bash
python3 -m uvicorn server:app --host 0.0.0.0 --port 8000
```

## 5. Настройка автозапуска (Systemd)
Чтобы сервер работал постоянно и перезапускался при сбоях, создайте службу systemd.

1. Создайте файл службы:
```bash
sudo nano /etc/systemd/system/messenger.service
```

2. Вставьте туда следующий контент:
```ini
[Unit]
Description=Messenger API Server
After=network.target

[Service]
User=root
WorkingDirectory=/opt/messenger-server
ExecStart=/usr/bin/python3 -m uvicorn server:app --host 0.0.0.0 --port 8000
Restart=always

[Install]
WantedBy=multi-user.target
```

3. Запустите службу:
```bash
sudo systemctl daemon-reload
sudo systemctl enable messenger
sudo systemctl start messenger
```

4. Проверьте статус:
```bash
sudo systemctl status messenger
```

## 6. Настройка Android приложения
Не забудьте обновить `BASE_URL` в файле `MessengerApi.kt` в Android проекте:
```kotlin
private const val BASE_URL = "http://<ВАШ_IP_VDS>:8000/"
```
