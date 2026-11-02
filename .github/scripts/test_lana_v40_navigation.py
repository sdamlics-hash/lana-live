"""Offline checks for V4.0 emulator tab readiness (no adb, APK or network)."""
import ast
from pathlib import Path
from types import SimpleNamespace
import unittest
import xml.etree.ElementTree as ET

SCRIPT = Path(__file__).with_name("lana-v40-dialogue-smoke.py")


def isolated_select_tab(snapshot, tap):
    tree = ast.parse(SCRIPT.read_text(encoding="utf-8"))
    function = next(node for node in tree.body
                    if isinstance(node, ast.FunctionDef) and node.name == "select_tab")
    namespace = {"snapshot": snapshot, "tap": tap, "ET": ET,
                 "time": SimpleNamespace(sleep=lambda _: None),
                 "find": lambda **_: None}
    exec(compile(ast.Module(body=[function], type_ignores=[]), str(SCRIPT), "exec"),
         namespace)
    return namespace["select_tab"]


class TabReadinessTests(unittest.TestCase):
    def scenario(self, switch_after):
        state = {"selected": 0, "clicks": 0}
        def snapshot():
            root = ET.Element("hierarchy")
            for tab in range(5):
                ET.SubElement(root, "node", {
                    "content-desc": "workspace_tab_" + str(tab),
                    "selected": str(state["selected"] == tab).lower()})
            if state["selected"] == 1:
                ET.SubElement(root, "node", {"content-desc": "workspace_task_input"})
            return root
        def tap(desc=None):
            self.assertEqual(desc, "workspace_tab_1")
            state["clicks"] += 1
            if state["clicks"] >= switch_after:
                state["selected"] = 1
        return isolated_select_tab(snapshot, tap), state

    def test_retries_ignored_first_tap_then_waits_for_input(self):
        select, state = self.scenario(switch_after=2)
        select(1, "workspace_task_input")
        self.assertEqual(state["clicks"], 2)

    def test_does_not_click_again_after_selected_page_ready(self):
        select, state = self.scenario(switch_after=1)
        select(1, "workspace_task_input")
        select(1, "workspace_task_input")
        self.assertEqual(state["clicks"], 1)

    def test_persistent_navigation_failure_still_fails(self):
        select, state = self.scenario(switch_after=100)
        with self.assertRaisesRegex(AssertionError, "Tab not ready"):
            select(1, "workspace_task_input")
        self.assertEqual(state["clicks"], 4)


if __name__ == "__main__":
    unittest.main()
