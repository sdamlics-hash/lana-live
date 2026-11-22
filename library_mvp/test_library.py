import tempfile
import unittest
from pathlib import Path
from library import Library

class LibraryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.lib = Library(self.root / "db.sqlite")
        self.addCleanup(self.lib.close)
        self.path = self.root / "book.txt"
        self.path.write_text("abc xyz end", encoding="utf-8")

    def test_01_complete(self):
        b = self.lib.ingest_txt(self.path, 4)
        self.assertEqual(self.lib.status(b), ("COMPLETE", 3))

    def test_02_last_chunk(self):
        self.lib.ingest_txt(self.path, 4)
        self.assertTrue(self.lib.search("end"))

    def test_03_provenance(self):
        self.lib.ingest_txt(self.path, 4)
        self.assertEqual(self.lib.search("end")[0][1], 2)

    def test_04_dedup(self):
        self.lib.ingest_txt(self.path, 4)
        self.lib.ingest_txt(self.path, 4)
        self.assertEqual(self.lib.db.execute("select count(*) from chunks").fetchone()[0], 3)

    def test_05_partial(self):
        b = self.lib.ingest_txt(self.path, 4, stop_after=1)
        self.assertEqual(self.lib.status(b)[0], "PARTIAL")

    def test_06_resume(self):
        self.lib.ingest_txt(self.path, 4, stop_after=1)
        b = self.lib.ingest_txt(self.path, 4)
        self.assertEqual(self.lib.status(b)[0], "COMPLETE")

    def test_07_no_duplicates_after_resume(self):
        self.lib.ingest_txt(self.path, 4, stop_after=1)
        self.lib.ingest_txt(self.path, 4)
        self.assertEqual(self.lib.db.execute("select count(*) from chunks").fetchone()[0], 3)

    def test_08_invalid_utf8(self):
        self.path.write_bytes(bytes([255]))
        with self.assertRaises(UnicodeDecodeError):
            self.lib.ingest_txt(self.path)

    def test_09_absent_search(self):
        self.lib.ingest_txt(self.path)
        self.assertEqual(self.lib.search("missing"), [])

    def test_10_delete(self):
        b = self.lib.ingest_txt(self.path)
        self.lib.delete(b)
        self.assertEqual(self.lib.db.execute("select count(*) from chunks").fetchone()[0], 0)

    def test_11_chunk_size_collision(self):
        self.lib.ingest_txt(self.path, 4)
        with self.assertRaises(ValueError):
            self.lib.ingest_txt(self.path, 3)

    def test_12_empty_book(self):
        self.path.write_text("")
        with self.assertRaises(ValueError):
            self.lib.ingest_txt(self.path)

    def test_13_five_books(self):
        for i in range(5):
            p = self.root / ("book" + str(i) + ".txt")
            p.write_text("Chapter " + str(i) + " END")
            self.lib.ingest_txt(p, 4)
        self.assertEqual(self.lib.db.execute("select count(*) from books").fetchone()[0], 5)

    def test_15_cross_chunk_search(self):
        self.lib.ingest_txt(self.path, 4)
        self.assertTrue(self.lib.search("c xy"))

    def test_16_cross_chunk_no_false_match(self):
        self.lib.ingest_txt(self.path, 4, stop_after=1)
        self.assertEqual(self.lib.search("c xy"), [])

    def test_14_restart(self):
        b = self.lib.ingest_txt(self.path, 4, stop_after=1)
        other = Library(self.root / "db.sqlite")
        try:
            other.ingest_txt(self.path, 4)
            self.assertEqual(other.status(b)[0], "COMPLETE")
        finally:
            other.close()

if __name__ == "__main__":
    unittest.main()
