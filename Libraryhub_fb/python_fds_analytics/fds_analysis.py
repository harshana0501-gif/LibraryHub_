"""
VCET ILMS - Scenario 4: Foundations of Data Science (FDS) Analytics
Register No: 34 | Connects to MySQL / Java REST API and visualizes:
1. Date-by-Date Daily Issue & Return Activity
2. Borrowing Pattern by Category
3. Top Most Borrowed Books
"""
import os
import json
import urllib.request
import pandas as pd
import matplotlib.pyplot as plt

API_URL = "http://localhost:8080/api"
CSV_DIR = os.path.join(os.path.dirname(__file__), "..", "database", "csv_storage")

def get_data():
    try:
        with urllib.request.urlopen(f"{API_URL}/books", timeout=2) as r:
            books = pd.DataFrame(json.loads(r.read().decode()))
        with urllib.request.urlopen(f"{API_URL}/transactions", timeout=2) as r:
            txns = pd.DataFrame(json.loads(r.read().decode()))
        print("[OK] Retrieved live data from Java + MySQL Backend (:8080)")
        return books, txns
    except Exception:
        print("[INFO] Backend server offline; reading database CSV files...")
        books = pd.read_csv(os.path.join(CSV_DIR, "books.csv"))
        txns = pd.read_csv(os.path.join(CSV_DIR, "transactions.csv"))
        books.rename(columns={"book_id": "id", "borrow_count": "borrowCount"}, inplace=True)
        txns.rename(columns={"txn_id": "id", "book_id": "bookId", "issue_date": "issueDate"}, inplace=True)
        return books, txns

def plot_fds_charts():
    books, txns = get_data()
    fig, axes = plt.subplots(1, 3, figsize=(16, 5))
    fig.suptitle("VCET ILMS Scenario 4 - FDS Borrowing Pattern Analysis (Reg No: 34)", fontsize=13, fontweight="bold")

    # 1. Date-by-Date Daily Issue Activity
    daily = txns.groupby("issueDate").size().sort_index()
    daily.plot(kind="line", marker="o", ax=axes[0], color="#10a37f", linewidth=2.5)
    axes[0].set_title("1. Daily Issue Activity (Date-by-Date)")
    axes[0].set_ylabel("Books Issued")
    axes[0].tick_params(axis="x", rotation=30)
    axes[0].grid(True, linestyle="--", alpha=0.5)

    # 2. Borrowing by Category
    cat = books.groupby("category")["borrowCount"].sum().sort_values(ascending=False)
    cat.plot(kind="bar", ax=axes[1], color="#2563eb", edgecolor="black")
    axes[1].set_title("2. Borrowing by Subject Category")
    axes[1].set_ylabel("Total Borrows")
    axes[1].tick_params(axis="x", rotation=25)

    # 3. Top Books
    top_b = books.sort_values("borrowCount", ascending=True).tail(5)
    axes[2].barh(top_b["title"].str.slice(0, 24), top_b["borrowCount"], color="#7c3aed")
    axes[2].set_title("3. Most Borrowed Books")
    axes[2].set_xlabel("Borrow Count")

    plt.tight_layout()
    out = os.path.join(os.path.dirname(__file__), "fds_daily_and_category_analysis_reg34.png")
    plt.savefig(out, dpi=150)
    print(f"[SAVED] FDS Chart exported to: {out}")

if __name__ == "__main__":
    plot_fds_charts()
