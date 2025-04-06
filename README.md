# FriendlySearch
**Описание**: Перед вами Spring Boot приложение, работающее с локально установленной базой данных MySQL, имеющее простой веб-интерфейс и API, через который им можно управлять и получать результаты поисковой выдачи по запросу.

## ⚙️ Технологии
- **Backend**: Spring Boot, [![Java](https://img.shields.io/badge/Java-17-red)](https://openjdk.org/), REST API, Hibernate, MySQL
- **Frontend**: HTML, CSS, JavaScript
- **Инструменты**: Maven, Thymeleaf

## Установка
1. Скачайте и установите MySQL: https://dev.mysql.com/downloads/ .
    
    Войдите в MySQL с помощью команды:
    ```bash
    mysql -u root -p
    ```
    Введите пароль, который вы задали во время установки.

2. Создайте MySQL базу данных search_engine
```bash
create database search_engine
```
3. Создайте пользователя
```sql
CREATE USER 'dbuser'@'localhost' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON search_engine.* TO 'dbuser'@'localhost';
FLUSH PRIVILEGES;
```

4. Скачать и установить jdk-17: https://www.oracle.com/java/technologies/downloads/#java17?er=221886&spm=a2ty_o01.29997173.0.0.5273c9219wJrqU
5. Клонируйте репозиторий:
```bash
https://github.com/Selogaz/searchengine.git
```

6. В корневой директории проекта запустите через терминал:
```bash
 java -jar target/SearchEngine-1.0-SNAPSHOT.jar  
```
7. Программа начнет работать по адресу: `http://localhost:8081/`

