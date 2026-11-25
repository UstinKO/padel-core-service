#!/usr/bin/env python3
"""Повторяет чистую генерацию Club и сверяет Java-исходник/bytecode с прошлой сборкой."""
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / 'padel-api-contract'


def generated():
    files = sorted((MODULE / 'target/generated-sources/openapi/src/main/java').rglob('*.java'))
    if [file.name for file in files] != ['Club.java']:
        raise ValueError('Разрешена только генерация Club.java')
    code = files[0].read_text()
    for forbidden in ['import org.springframework.', 'import javax.', 'import jakarta.persistence.',
                      'import com.fasterxml.jackson.databind.', 'import com.fasterxml.jackson.core.',
                      'import java.net.http.', 'import com.padle.core.application.']:
        if forbidden in code:
            raise ValueError('Запрещённый импорт: ' + forbidden)
    classes = list((MODULE / 'target/classes').rglob('*.class'))
    if len(classes) != 1 or int.from_bytes(classes[0].read_bytes()[6:8], 'big') != 61:
        raise ValueError('Требуется одна модель с bytecode Java 17')
    return hashlib.sha256(files[0].read_bytes()).hexdigest()


if __name__ == '__main__':
    first = generated()
    result = ROOT / 'target/club-contract-evidence'
    result.mkdir(parents=True, exist_ok=True)
    with (result / 'clean-generation.log').open('w') as log:
        subprocess.run([str(ROOT / 'mvnw'), '-B', '-pl', 'padel-api-contract', 'clean', 'verify'],
                       cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
    second = generated()
    if first != second:
        raise ValueError('Генерация Club недетерминирована')
    (result / 'generation.json').write_text(json.dumps({'deterministic': True, 'javaFiles': 1,
        'bytecodeRelease': 17, 'clubJavaSha256': second}, indent=2) + '\n')
    print('club-generation: две генерации совпали, одна модель, Java 17')
