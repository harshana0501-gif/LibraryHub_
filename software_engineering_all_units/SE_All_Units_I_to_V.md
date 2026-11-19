# Software Engineering — Complete All Units (I to V) Documentation
## Project: Scenario 4 — Library Book Lending and Usage Analysis System (VCET ILMS)
**Register No:** 34 | **Tech Stack:** Java REST Backend + MySQL 3NF Database + HTML/JS/Chart.js Frontend + Python FDS Analytics

---

## UNIT I: Software Process Models & Project Management
- **Process Model Adopted:** **Incremental & Iterative Agile-Scrum Process Model**
  - *Why:* Enables rapid delivery of core book/member management (Sprint 1), followed by manual date circulation & fine logic (Sprint 2), MySQL 3NF integration (Sprint 3), and real-time date-by-date visualization (Sprint 4).
- **Project Scheduling (WBS & Milestones):**
  - Milestone 1: Problem Statement & IEEE 830 SRS
  - Milestone 2: 3NF Relational Schema (`vcet_ilms_reg34`) & UML Diagrams
  - Milestone 3: Java REST API (`com.vcet.library.LibraryApiServer`) + Frontend Dashboard
  - Milestone 4: Python FDS Data Analysis & Test Case Verification

---

## UNIT II: Requirements Engineering & SRS (All 8 Functional Requirements)
| Req ID | Functional Requirement | Implementation in System |
| :--- | :--- | :--- |
| **FR-1** | Maintain book, author, category, and student membership details | MySQL tables (`books`, `authors`, `categories`, `members`) + Librarian Add Book Modal & Members form |
| **FR-2** | Issue and return books | `POST /api/issue` and `POST /api/return` endpoints + Circulation UI |
| **FR-3** | Prevent issuing unavailable copies | Validates `available_copies > 0`; blocks loan with HTTP `409 Conflict` if `0` copies remain |
| **FR-4** | Record issue dates, due dates, and return dates | Manual date pickers for Issue Date, Due Date, and Return Date saved in `transactions` |
| **FR-5** | Calculate overdue fines according to defined rule (`Rs. 5/day`) | Computes `DATEDIFF(return_date, due_date) * 5.0` and updates transaction status to `RETURNED` |
| **FR-6** | Generate reports on available books, overdue books, borrowing history | Real-time Dashboard KPIs, Overdue Table, History Ledger, and CSV Export |
| **FR-7** | Retrieve transaction data from database for Python analysis | `python_fds_analytics/fds_analysis.py` reads live API / database records |
| **FR-8** | Visualize borrowing patterns by category, month/date, and book | Date-by-Date Daily Issue/Return Line Chart, Category Donut/Bar Chart, and Top Books Chart |

---

## UNIT III: System Design, UML Modeling & 3NF Database Normalization
- **3NF Normalization:**
  - `authors` and `categories` are separated from `books` to eliminate transitive dependencies.
  - `transactions` references `book_id` and `member_id` via foreign keys with `ENUM('ISSUED','RETURNED')`.
- **Architectural Design:** 3-Tier Client-Server Architecture (`Frontend :3000` ↔ `Java Backend :8080` ↔ `MySQL :3306`).

---

## UNIT IV: Software Testing & Quality Assurance
| Test ID | Test Case Description | Input | Expected Output | Status |
| :--- | :--- | :--- | :--- | :--- |
| **TC-01** | Add New Book & Update Count | Title: "OS Concepts", Copies: 5 | Saved in MySQL; Total Books KPI increments by +5 | PASS |
| **TC-02** | Manual Issue & Due Date | Issue: `2026-11-20`, Due: `2026-12-04` | Status = `ISSUED`; Daily Activity graph increments on `2026-11-20` | PASS |
| **TC-03** | Prevent 0-Copy Issue | Book with `available_copies = 0` | Blocked with HTTP `409 Conflict` alert | PASS |
| **TC-04** | Manual Return Date & Status | Return Date: 3 days after Due Date | Fine = `Rs. 15.00`; Status updated to `RETURNED` | PASS |
| **TC-05** | Python FDS Visualization | Execute `fds_analysis.py` | Generates 3-panel PNG chart (Daily, Category, Top Books) | PASS |

---

## UNIT V: Software Maintenance, Configuration & Risk Management
- **Configuration Management:** Clean separation of `backend/`, `database/`, `frontend/`, `python_fds_analytics/`, and `software_engineering_all_units/`.
- **Fault Tolerance / Risk Mitigation:** Dual-persistence engine in `LibraryApiServer.java` automatically uses MySQL JDBC when `mysql-connector-j.jar` is present and falls back seamlessly to synced CSV storage otherwise.
