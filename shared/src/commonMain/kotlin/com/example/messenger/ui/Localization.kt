package com.example.messenger.ui

object Strings {
    private val ru = mapOf(
        // Common
        "cancel" to "Отмена",
        "ok" to "ОК",
        "save" to "Сохранить",
        "create" to "Создать",
        "delete" to "Удалить",
        "error" to "Ошибка",
        "success" to "Успешно",
        
        // Navigation / Drawer
        "contacts" to "Контакты",
        "profile" to "Профиль",
        "auth_requests" to "Запросы авторизации",
        "settings" to "Настройки",
        "logout" to "Выход",
        
        // Profile
        "my_profile" to "Мой профиль",
        "my_public_key" to "Мой публичный ключ",
        "copy_clipboard" to "Копировать",
        "qr_error" to "Ошибка генерации QR кода",
        
        // Auth Requests
        "device" to "Устройство",
        "accept" to "Принять",
        "reject" to "Отклонить",
        
        // Contact List
        "groups" to "Группы",
        "menu" to "Меню",
        "refresh" to "Обновить",
        "add" to "Добавить",
        "create_group" to "Создать группу",
        "add_contact" to "Добавить контакт",
        "contact" to "Контакт",
        "group" to "Группа",
        "name" to "Имя",
        "public_key" to "Публичный ключ",
        "select_members" to "Выберите участников",
        
        // Settings
        "appearance" to "Внешний вид",
        "theme" to "Тема",
        "theme_system" to "Системная",
        "theme_light" to "Светлая",
        "theme_dark" to "Темная",
        "language" to "Язык",
        "notifications" to "Уведомления",
        "enable_notifications" to "Включить уведомления",
        "security" to "Безопасность",
        "panic_config" to "Настройка паники",
        "delete_contacts" to "Удалить контакты",
        "delete_messages" to "Удалить сообщения",
        "delete_keys" to "Удалить ключи",
        "auto_lock" to "Автоблокировка",
        "disabled" to "Отключено",
        "min" to "мин",
        "app_updates" to "Обновление приложения",
        "check_update" to "Проверить обновления",
        "checking" to "Проверка...",
        "update_available" to "Доступно обновление: ",
        "install" to "Установить",
        "danger_zone" to "Опасная зона",
        "clear_all_data" to "Удалить все данные",
        "clear_dialog_title" to "Удалить все данные",
        "clear_dialog_text" to "Вы уверены? Это действие необратимо удалит все контакты и сообщения.",
        "delete_all" to "Удалить все"
    )

    private val en = mapOf(
        // Common
        "cancel" to "Cancel",
        "ok" to "OK",
        "save" to "Save",
        "delete" to "Delete",
        "error" to "Error",
        "success" to "Success",
        
        // Navigation / Drawer
        "contacts" to "Contacts",
        "profile" to "Profile",
        "auth_requests" to "Auth Requests",
        "settings" to "Settings",
        "logout" to "Logout",
        
        // Profile
        "my_profile" to "My Profile",
        "my_public_key" to "My Public Key",
        "copy_clipboard" to "Copy to Clipboard",
        "qr_error" to "Error generating QR Code",
        
        // Auth Requests
        "device" to "Device",
        "accept" to "Accept",
        "reject" to "Reject",
        
        // Contact List
        "groups" to "Groups",
        "menu" to "Menu",
        "refresh" to "Refresh",
        "add" to "Add",
        "create_group" to "Create Group",
        "add_contact" to "Add Contact",
        "contact" to "Contact",
        "group" to "Group",
        "name" to "Name",
        "public_key" to "Public Key",
        "select_members" to "Select Members",
        
        // Settings
        "appearance" to "Appearance",
        "theme" to "Theme",
        "theme_system" to "System",
        "theme_light" to "Light",
        "theme_dark" to "Dark",
        "language" to "Language",
        "notifications" to "Notifications",
        "enable_notifications" to "Enable Notifications",
        "security" to "Security",
        "panic_config" to "Panic Button Configuration",
        "delete_contacts" to "Delete Contacts",
        "delete_messages" to "Delete Messages",
        "delete_keys" to "Delete Keys",
        "auto_lock" to "Auto Lock",
        "disabled" to "Disabled",
        "min" to "min",
        "app_updates" to "App Updates",
        "check_update" to "Check for Updates",
        "checking" to "Checking...",
        "update_available" to "Update Available: ",
        "install" to "Install",
        "danger_zone" to "Danger Zone",
        "clear_all_data" to "Clear All Data",
        "clear_dialog_title" to "Clear All Data",
        "clear_dialog_text" to "Are you sure? This action will irreversibly delete all contacts and messages.",
        "delete_all" to "Delete All",
        
        // Backup
        "backup_restore" to "Backup & Restore",
        "export_backup" to "Export Backup",
        "import_backup" to "Import Backup",
        "backup_password_hint" to "Encryption Password (Optional)",
        "backup_filename_hint" to "Filename in Downloads",
        "export" to "Export",
        "import" to "Import"
    )

    fun get(key: String, language: String): String {
        return if (language == "ru") {
            ru[key] ?: en[key] ?: key
        } else {
            en[key] ?: key
        }
    }
}
