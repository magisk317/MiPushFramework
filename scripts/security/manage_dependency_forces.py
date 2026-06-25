#!/usr/bin/env python3
"""
Wrapper script that delegates to magisk-ci-toolkit.
"""
import os
import sys
import subprocess

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TOOLKIT_SCRIPT = os.path.join(SCRIPT_DIR, '..', '_toolkit', 'security', 'manage_dependency_forces.py')

# Set default project for MiPushFramework
os.environ.setdefault('MAGISK_DEFAULT_PROJECT', ':mipush')

sys.exit(subprocess.call([sys.executable, TOOLKIT_SCRIPT] + sys.argv[1:]))
