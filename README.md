# FriendlySearch
Перед вами Spring Boot приложение, работающее с локально установленной базой данных MySQL, имеющее простой веб-интерфейс и API, через который им можно управлять и получать результаты поисковой выдачи по запросу.

## ⚙️ Технологии
- **Backend**: Spring Boot, Java 17, REST API, Hibernate, MySQL
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
EXIT;
```
```sql
mysql -u dbuser -p
```
После этого введите пароль `password`;

4. Скачайте и установить jdk-17: https://www.oracle.com/java/technologies/downloads/#java17?er=221886&spm=a2ty_o01.29997173.0.0.5273c9219wJrqU
5. Скачайте и установите git: https://git-scm.com/downloads
6. Клонируйте репозиторий:
```bash
git clone https://github.com/Selogaz/searchengine.git
```
7. В корневой директории проекта соберите проект с помощью Maven:
```bash
mvn clean package -DskipTests
```
8.  Запустите:
```bash
 java -jar target/SearchEngine-1.0-SNAPSHOT.jar  
```
9. Программа начнет работать по адресу: http://localhost:8081/

