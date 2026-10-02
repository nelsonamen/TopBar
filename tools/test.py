#!/usr/bin/env python3
"""Exercise Hello World against the SDK without a device."""
import os
import sys
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
java_home = os.environ.get('JAVA_HOME')
def tool(name):
    return str(Path(java_home) / 'bin' / name) if java_home else name
sources = [*sorted((root / 'sdk/src').rglob('*.java')), *sorted((root / 'src').rglob('*.java')), *sorted((root / 'tests').rglob('*.java'))]
with tempfile.TemporaryDirectory(prefix='kiosk-plugin-test-') as directory:
    subprocess.run([tool('javac'), '--release', '8', '-d', directory, *map(str, sources)], check=True)
    for test in ['HelloWorldTest', 'me.jxl.kiosk.plugins.hello.ShizukuDemoTest', 'me.jxl.kiosk.plugins.hello.HomeAssistantDemoTest']:
        subprocess.run([tool('java'), '-ea', '-cp', directory, test], check=True)

subprocess.run([sys.executable, str(root / 'tools/test_android_sdk.py')], check=True)

subprocess.run([sys.executable, str(root / 'tools/test_plugin_manifest.py')], check=True)

subprocess.run([sys.executable, str(root / "tools/test_plugin_assets.py")], check=True)
