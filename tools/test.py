#!/usr/bin/env python3
"""Run plugin manifest and assets validation."""
import sys
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]

subprocess.run([sys.executable, str(root / 'tools/test_plugin_manifest.py')], check=True)
subprocess.run([sys.executable, str(root / 'tools/test_plugin_assets.py')], check=True)
