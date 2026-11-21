"""Повторяет отказы upstream, две чистые сборки и проверяет границы DTO."""

import hashlib
import json
from pathlib import Path
import struct
import subprocess


PROBE = Path(__file__).resolve().parent
ROOT = PROBE.parents[1]
EVIDENCE = ROOT / "target/openapi-probe-evidence"
MAVEN = [str(ROOT / "mvnw"), "-B", "-f", str(PROBE / "pom.xml")]


def run(name, arguments, expected_failure=None):
    log = EVIDENCE / f"{name}.log"
    with log.open("w") as output:
        result = subprocess.run(MAVEN + arguments, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
    content = log.read_text()
    if expected_failure:
        if result.returncode == 0 or expected_failure not in content:
            raise RuntimeError(f"Отказ {name} изменился; см. {log}")
    elif result.returncode:
        raise RuntimeError(f"Проверка {name} не прошла; см. {log}")
    print(f"{name}: ожидаемый отказ" if expected_failure else f"{name}: успешно", flush=True)


def generated_hashes():
    paths = sorted((PROBE / "target/generated-sources").rglob("*.java"))
    paths += sorted((PROBE / "target/generated-test-sources").rglob("*.java"))
    if not paths:
        raise RuntimeError("Генерация не создала Java-исходники")
    for path in paths:
        content = path.read_text().replace("import javax.annotation.processing.Generated;", "")
        for forbidden in ["import javax.", "import com.fasterxml.jackson.databind.",
                          "import com.fasterxml.jackson.core.", "import org.springframework.",
                          "import jakarta.persistence.", "import java.net.http."]:
            if forbidden in content:
                raise RuntimeError(f"Запрещённый импорт: {path.name}: {forbidden}")
    return {str(path.relative_to(PROBE / "target")): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in paths}


def verify():
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    upstream = f"-Dprobe.templates={PROBE / 'target/upstream-templates'}"
    run("native-upstream", ["clean", "compile", "-Dprobe.library=native", upstream],
        expected_failure="interface expected here")
    run("validation-upstream", ["clean", "test", upstream,
                                "-Dtest=GeneratedModelsTest#jakartaValidationIsPresentOnRequiredNonNullableField"],
        expected_failure="Expecting value to be false but was true")
    run("generation-one", ["clean", "verify"])
    first = generated_hashes()
    run("generation-two", ["clean", "verify"])
    second = generated_hashes()
    if first != second:
        raise RuntimeError("Две чистые генерации Java различаются")
    classes = sorted((PROBE / "target/classes").rglob("*.class"))
    if not classes or any(struct.unpack(">H", path.read_bytes()[6:8])[0] != 61 for path in classes):
        raise RuntimeError("Модели не скомпилированы в bytecode Java 17")
    run("dependencies", ["dependency:tree", "-DoutputType=text",
                         f"-DoutputFile={EVIDENCE / 'dependency-tree.txt'}"])
    (EVIDENCE / "generated-sha256.json").write_text(json.dumps(second, indent=2) + "\n")
    summary = {"generatedJavaFiles": len(second), "classFilesJava17": len(classes),
               "deterministic": True, "nativeUpstream": "EXPECTED_COMPILE_FAILURE",
               "nullableValidationUpstream": "EXPECTED_TEST_FAILURE",
               "adoptionScope": "Только Club response; presence/readOnly/writeOnly/oneOf не подключаются"}
    (EVIDENCE / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    verify()
