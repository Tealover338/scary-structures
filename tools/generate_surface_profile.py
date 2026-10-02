"""Compatibility entry point: rebuild all edge height profiles after splitting templates."""
from pathlib import Path
import runpy

runpy.run_path(str(Path(__file__).with_name('build_edge_profiles.py')), run_name='__main__')
