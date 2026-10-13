"""Compatibility entrypoint: Settings UI regressions now include the 07.1 atomic render host."""
from pathlib import Path
import runpy
runpy.run_path(str(Path(__file__).with_name('settings071_check.py')),run_name='__main__')
