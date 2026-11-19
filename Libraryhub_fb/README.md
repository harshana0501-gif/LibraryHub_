# VCET ILMS — Scenario 4 (Java + MySQL + FDS + SE All Units) | Reg No: 34

## 1. Setup MySQL Database
Run `database/mysql_schema_and_seed.sql` in MySQL Workbench or MySQL CLI:
```sql
SOURCE database/mysql_schema_and_seed.sql;
```
*(Optional: Place `mysql-connector-j-8.x.jar` inside `backend/lib/` and update `backend/db.properties` with your MySQL password. Even without the JAR, the backend automatically syncs with CSV storage so you can run immediately!)*

## 2. Run Backend & Frontend in PowerShell
**Terminal 1 (Java Backend on Port 8080):**
```powershell
cd backend\src
javac -cp ".;..\lib\*" com\vcet\library\LibraryApiServer.java
java -cp ".;..\lib\*" com.vcet.library.LibraryApiServer
```

**Terminal 2 (Frontend on Port 3000):**
```powershell
cd frontend
npx serve -s . -l 3000
```
Open **http://localhost:3000** in Chrome/Edge.
