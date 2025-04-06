# FriendlySearch
**Описание**: Перед вами Spring Boot приложение, работающее с локально установленной базой данных MySQL, имеющее простой веб-интерфейс и API, через который им можно управлять и получать результаты поисковой выдачи по запросу.

## ⚙️ Технологии
- **Backend**: Spring Boot, [![Java](https://img.shields.io/badge/Java-17-red)](https://openjdk.org/), REST API, Hibernate, MySQL
- **Frontend**: HTML, CSS, JavaScript
- **Инструменты**: Maven, Thymeleaf

## Установка
1. Установка MySQL  
  - Скачайте MySQL
    Перейдите на официальный сайт MySQL: https://dev.mysql.com/downloads/ .
    Выберите подходящую версию MySQL для вашей операционной системы:
    - Для Windows: скачайте MySQL Installer.
    - Для macOS: используйте Homebrew (`brew install mysql`) или скачайте DMG-файл.
    - Для Linux: следуйте инструкциям для вашей дистрибуции (например, `sudo apt install mysql-server` для Ubuntu).

  - Установите MySQL
    Запустите установщик и следуйте инструкциям мастера установки.
    Во время установки вам будет предложено задать пароль для пользователя root. Запомните его, так как он понадобится позже.

  - Проверьте установку
    Откройте терминал (или командную строку) и выполните команду: 
    ```bash
    mysql --version
    ```
    Если MySQL установлен правильно, вы увидите версию программы.
    Войдите в MySQL с помощью команды:
    ```bash
    mysql -u root -p
    ```
    Введите пароль, который вы задали во время установки.

2. Создайте MySQL базу данных search_engine
```bash
create database search_engine
```
3. Клонируйте репозиторий:
```bash
https://github.com/Selogaz/searchengine.git
```

4. В корневой директории проекта запустите через терминал:
```bash
 java -jar target/SearchEngine-1.0-SNAPSHOT.jar  
```

Для запуска приложения необходимо:
- Создать MySQL базу данных search_engine
- Установить JDK 17 для исключения конфликтов версий
- Запустить на локальном хосте на порте 8081
