"""Offline TXT indexing prototype. Not integrated into Android."""
import hashlib
import sqlite3
from pathlib import Path

SCHEMA = """
CREATE TABLE IF NOT EXISTS books (
 id TEXT PRIMARY KEY, title TEXT NOT NULL, sha256 TEXT NOT NULL,
 chunk_size INTEGER NOT NULL, sections INTEGER NOT NULL, status TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS chunks (
 book_id TEXT NOT NULL, section INTEGER NOT NULL,
 content TEXT NOT NULL, PRIMARY KEY(book_id, section),
 FOREIGN KEY(book_id) REFERENCES books(id) ON DELETE CASCADE
);
"""

class Library:
    def __init__(self, db_path):
        self.db = sqlite3.connect(db_path)
        self.db.execute("PRAGMA foreign_keys=ON")
        self.db.executescript(SCHEMA)

    def ingest_txt(self, path, chunk_size=2000, stop_after=None):
        if chunk_size < 1:
            raise ValueError("chunk_size must be positive")
        data = Path(path).read_bytes()
        content = data.decode("utf-8", errors="strict")
        if not content.strip():
            raise ValueError("Empty book cannot be indexed")
        digest = hashlib.sha256(data).hexdigest()
        sections = (len(content) + chunk_size - 1) // chunk_size
        with self.db:
            self.db.execute(
                "INSERT OR IGNORE INTO books VALUES (?,?,?,?,?,?)",
                (digest, Path(path).name, digest, chunk_size, sections, "PROCESSING"))
            row = self.db.execute(
                "SELECT chunk_size, sections FROM books WHERE id=?", (digest,)).fetchone()
            if row != (chunk_size, sections):
                raise ValueError("Different chunk size for existing book")
        for i in range(sections):
            if stop_after is not None and i >= stop_after:
                break
            with self.db:
                self.db.execute(
                    "INSERT OR IGNORE INTO chunks VALUES (?,?,?)",
                    (digest, i, content[i*chunk_size:(i+1)*chunk_size]))
        count = self.db.execute(
            "SELECT COUNT(*) FROM chunks WHERE book_id=?", (digest,)).fetchone()[0]
        with self.db:
            self.db.execute(
                "UPDATE books SET status=? WHERE id=?",
                ("COMPLETE" if count == sections else "PARTIAL", digest))
        return digest

    def status(self, book_id):
        return self.db.execute(
            "SELECT status, sections FROM books WHERE id=?", (book_id,)).fetchone()

    def search(self, term):
        if not term:
            return []
        # Reassemble chunks so matches crossing chunk boundaries are found.
        rows = self.db.execute(
            "SELECT b.id,b.title,b.chunk_size,c.section,c.content "
            "FROM books b JOIN chunks c ON b.id=c.book_id "
            "ORDER BY b.title,b.id,c.section").fetchall()
        grouped = {}
        for book_id, title, size, section, content in rows:
            grouped.setdefault(book_id, [title, size, []])[2].append((section, content))
        matches = []
        for title, size, chunks in grouped.values():
            # Never join across missing sections: that would invent text.
            runs = []
            for section, part in chunks:
                if not runs or section != runs[-1][-1][0] + 1:
                    runs.append([])
                runs[-1].append((section, part))
            for run in runs:
                full = "".join(part for _, part in run)
                offset = 0
                while True:
                    hit = full.lower().find(term.lower(), offset)
                    if hit == -1:
                        break
                    section_index = hit // size
                    matches.append((title, run[section_index][0], run[section_index][1]))
                    offset = hit + 1
        return matches

    def delete(self, book_id):
        with self.db:
            self.db.execute("DELETE FROM books WHERE id=?", (book_id,))

    def close(self):
        self.db.close()
