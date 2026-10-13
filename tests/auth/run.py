#!/usr/bin/env python3
"""Host-only tests. Does not contact Firebase or simulate successful authentication."""
import pathlib
import subprocess
import tempfile

root = pathlib.Path(__file__).resolve().parents[2]
source = root / 'app/src/main/java/com/zeytindefteri/app'
with tempfile.TemporaryDirectory(prefix='zeytin-auth-') as out:
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-encoding', 'UTF-8', '-d', out,
                    str(source / 'AuthErrorMessages.java'),
                    str(source / 'AuthRegistrationPolicy.java'),
                    str(source / 'LegalDocuments.java'),
                    str(root / 'tests/auth/AuthRegistrationPolicyTest.java'),
                    str(root / 'tests/auth/AuthErrorMessagesTest.java'),
                    str(root / 'tests/auth/LegalDocumentsTest.java')], check=True)
    subprocess.run(['java', '-cp', out, 'com.zeytindefteri.app.AuthErrorMessagesTest'],
                   cwd=root, check=True)

    subprocess.run(['java', '-cp', out, 'com.zeytindefteri.app.AuthRegistrationPolicyTest'],
                   cwd=root, check=True)

    subprocess.run(['java', '-cp', out, 'com.zeytindefteri.app.LegalDocumentsTest'],
                   cwd=root, check=True)

subprocess.run(['python3', str(root / 'tests/auth/check_layout_budget.py')], cwd=root, check=True)
