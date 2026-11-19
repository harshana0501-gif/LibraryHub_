-- ============================================================================
-- VCET ILMS - Scenario 4: Library Book Lending and Usage Analysis System
-- Register No: 34 | 3NF Normalized MySQL Database Schema & Initial Dataset
-- ============================================================================

CREATE DATABASE IF NOT EXISTS vcet_ilms_reg34;
USE vcet_ilms_reg34;

-- 1. Categories Table (3NF Normalization)
CREATE TABLE IF NOT EXISTS categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL UNIQUE
);

-- 2. Authors Table (3NF Normalization)
CREATE TABLE IF NOT EXISTS authors (
    author_id INT AUTO_INCREMENT PRIMARY KEY,
    author_name VARCHAR(150) NOT NULL UNIQUE
);

-- 3. Books Table
CREATE TABLE IF NOT EXISTS books (
    book_id INT AUTO_INCREMENT PRIMARY KEY,
    isbn VARCHAR(30) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    author_id INT NOT NULL,
    category_id INT NOT NULL,
    total_copies INT NOT NULL DEFAULT 1,
    available_copies INT NOT NULL DEFAULT 1,
    book_type ENUM('Physical', 'E-Book') NOT NULL DEFAULT 'Physical',
    borrow_count INT NOT NULL DEFAULT 0,
    added_date DATE NOT NULL,
    FOREIGN KEY (author_id) REFERENCES authors(author_id) ON UPDATE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(category_id) ON UPDATE CASCADE
);

-- 4. Members (Students) Table
CREATE TABLE IF NOT EXISTS members (
    member_id INT AUTO_INCREMENT PRIMARY KEY,
    reg_no VARCHAR(20) NOT NULL UNIQUE,
    full_name VARCHAR(120) NOT NULL,
    department VARCHAR(50) NOT NULL,
    email VARCHAR(120) NOT NULL,
    joined_date DATE NOT NULL,
    status ENUM('Active', 'Inactive') NOT NULL DEFAULT 'Active'
);

-- 5. Transactions (Issue / Return / Overdue Fines) Table
CREATE TABLE IF NOT EXISTS transactions (
    txn_id INT AUTO_INCREMENT PRIMARY KEY,
    book_id INT NOT NULL,
    member_id INT NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    return_date DATE NULL,
    fine_amount DECIMAL(8,2) NOT NULL DEFAULT 0.00,
    status ENUM('ISSUED', 'RETURNED') NOT NULL DEFAULT 'ISSUED',
    FOREIGN KEY (book_id) REFERENCES books(book_id),
    FOREIGN KEY (member_id) REFERENCES members(member_id)
);

-- Seed Categories
INSERT IGNORE INTO categories (category_name) VALUES
('Database Systems'),
('Software & Java'),
('Computer Networks'),
('Science & AI'),
('Engineering');

-- Seed Authors
INSERT IGNORE INTO authors (author_name) VALUES
('Abraham Silberschatz'),
('Ian Sommerville'),
('Herbert Schildt'),
('Andrew S. Tanenbaum'),
('Stuart Russell'),
('David Harris'),
('Wes McKinney'),
('Robert C. Martin');

-- Seed Initial Books
INSERT IGNORE INTO books (book_id, isbn, title, author_id, category_id, total_copies, available_copies, book_type, borrow_count, added_date) VALUES
(101, '978-0078022159', 'Database System Concepts', 1, 1, 15, 13, 'Physical', 29, '2026-07-01'),
(102, '978-0133943030', 'Software Engineering', 2, 5, 18, 17, 'Physical', 41, '2026-07-01'),
(103, '978-1260440232', 'Java: The Complete Reference', 3, 2, 14, 13, 'Physical', 34, '2026-07-02'),
(104, '978-0132126953', 'Computer Networks', 4, 3, 12, 11, 'Physical', 22, '2026-07-03'),
(105, '978-0134610993', 'Artificial Intelligence: A Modern Approach', 5, 4, 10, 10, 'Physical', 19, '2026-07-05'),
(106, '978-0124077263', 'Digital Design & Computer Architecture', 6, 5, 9, 8, 'Physical', 15, '2026-07-06'),
(107, '978-1491957660', 'Python for Data Analysis', 7, 4, 10, 10, 'E-Book', 27, '2026-07-08'),
(108, '978-0132350884', 'Clean Code', 8, 2, 10, 10, 'E-Book', 24, '2026-07-10');

-- Seed Students
INSERT IGNORE INTO members (member_id, reg_no, full_name, department, email, joined_date) VALUES
(1, '34', 'Harshana M', 'IT', 'harshana34@vcet.ac.in', '2026-07-01'),
(2, '04', 'Neha Patel', 'CSE', 'neha04@vcet.ac.in', '2026-07-03'),
(3, '64', 'Arun Kumar', 'ECE', 'arun64@vcet.ac.in', '2026-07-05'),
(4, '94', 'Priya Singh', 'IT', 'priya94@vcet.ac.in', '2026-07-08'),
(5, '21', 'Karthik Raja', 'CSE', 'karthik21@vcet.ac.in', '2026-07-12');

-- Seed Date-by-Date Transactions (for Daily Issue Activity Graph & Overdue Fines)
INSERT IGNORE INTO transactions (txn_id, book_id, member_id, issue_date, due_date, return_date, fine_amount, status) VALUES
(1001, 103, 4, '2026-11-18', '2026-12-02', NULL, 0.00, 'ISSUED'),
(1002, 102, 1, '2026-11-17', '2026-12-01', NULL, 0.00, 'ISSUED'),
(1003, 101, 1, '2026-11-16', '2026-11-30', '2026-11-19', 0.00, 'RETURNED'),
(1004, 104, 2, '2026-11-15', '2026-11-29', '2026-11-18', 0.00, 'RETURNED'),
(1005, 106, 4, '2026-10-01', '2026-10-15', NULL, 180.00, 'ISSUED'),
(1006, 104, 3, '2026-09-25', '2026-10-09', NULL, 210.00, 'ISSUED'),
(1007, 101, 2, '2026-09-28', '2026-10-12', NULL, 195.00, 'ISSUED'),
(1008, 102, 5, '2026-11-14', '2026-11-28', '2026-11-17', 0.00, 'RETURNED');
