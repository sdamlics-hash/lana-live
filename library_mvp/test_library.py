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

    def test_17_gap_does_not_fabricate(self):
        b = self.lib.ingest_txt(self.path, 4)
        self.lib.db.execute('DELETE FROM chunks WHERE book_id=? AND section=1', (b,))
        self.assertEqual(self.lib.search('c end'), [])

    def test_18_gap_keeps_existing_hits(self):
        b = self.lib.ingest_txt(self.path, 4)
        self.lib.db.execute('DELETE FROM chunks WHERE book_id=? AND section=1', (b,))
        self.assertTrue(self.lib.search('end'))

    def test_19_gap_preserves_original_section(self):
        b = self.lib.ingest_txt(self.path, 4)
        self.lib.db.execute('DELETE FROM chunks WHERE book_id=? AND section=1', (b,))
        self.assertEqual(self.lib.search('end')[0][1], 2)

    def test_20_multi_chunk_phrase(self):
        self.lib.ingest_txt(self.path, 3)
        self.assertTrue(self.lib.search('abc xyz end'))

    def test_21_case_insensitive(self):
        self.lib.ingest_txt(self.path, 4)
        self.assertTrue(self.lib.search('XYZ'))

    def test_22_empty_query(self):
        self.lib.ingest_txt(self.path, 4)
        self.assertEqual(self.lib.search(''), [])

    def test_23_zero_chunk_size(self):
        with self.assertRaises(ValueError):
            self.lib.ingest_txt(self.path, 0)

    def test_24_negative_chunk_size(self):
        with self.assertRaises(ValueError):
            self.lib.ingest_txt(self.path, -1)

    def test_25_no_cross_book_join(self):
        other = self.root / 'other.txt'
        other.write_text('QWERTY')
        self.lib.ingest_txt(self.path, 4)
        self.lib.ingest_txt(other, 4)
        self.assertEqual(self.lib.search('endQW'), [])

    def test_26_duplicate_occurrences(self):
        self.path.write_text('aaaaaa')
        self.lib.ingest_txt(self.path, 2)
        self.assertEqual(len(self.lib.search('aaa')), 4)

    def test_27_missing_section_not_complete(self):
        b=self.lib.ingest_txt(self.path,4)
        self.lib.db.execute('DELETE FROM chunks WHERE book_id=? AND section=1',(b,))
        self.lib.ingest_txt(self.path,4,stop_after=0)
        self.assertEqual(self.lib.status(b)[0],'PARTIAL')

    def test_28_corrupted_section_not_complete(self):
        b=self.lib.ingest_txt(self.path,4)
        self.lib.db.execute('UPDATE chunks SET content=? WHERE book_id=? AND section=1',('WRONG',b))
        self.lib.ingest_txt(self.path,4)
        self.assertEqual(self.lib.status(b)[0],'PARTIAL')

    def test_29_missing_section_recovers(self):
        b=self.lib.ingest_txt(self.path,4)
        self.lib.db.execute('DELETE FROM chunks WHERE book_id=? AND section=1',(b,))
        self.lib.ingest_txt(self.path,4)
        self.assertEqual(self.lib.status(b)[0],'COMPLETE')

    def test_30_negative_stop_rejected(self):
        with self.assertRaises(ValueError):
            self.lib.ingest_txt(self.path,4,stop_after=-1)

    def test_31_float_chunk_rejected(self):
        with self.assertRaises(ValueError):
            self.lib.ingest_txt(self.path,2.5)

    def test_32_bool_chunk_rejected(self):
        with self.assertRaises(ValueError):
            self.lib.ingest_txt(self.path,True)

    def test_33_float_stop_rejected(self):
        with self.assertRaises(ValueError):
            self.lib.ingest_txt(self.path,4,stop_after=0.5)

    def test_34_bool_stop_rejected(self):
        with self.assertRaises(ValueError):
            self.lib.ingest_txt(self.path,4,stop_after=False)

    def test_35_zero_stop_partial(self):
        b=self.lib.ingest_txt(self.path,4,stop_after=0)
        self.assertEqual(self.lib.status(b)[0],'PARTIAL')

    def test_36_recovery_after_zero_stop(self):
        b=self.lib.ingest_txt(self.path,4,stop_after=0)
        self.lib.ingest_txt(self.path,4)
        self.assertEqual(self.lib.status(b)[0],'COMPLETE')

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
