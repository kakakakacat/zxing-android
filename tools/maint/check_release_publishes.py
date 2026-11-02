#!/usr/bin/env python3
"""Fail if release.yml would publish a POM whose own artifacts it does not publish.

:scanner declares `api(project(":core"))`, so the AAR's POM carries a compile dependency
on this project's own `zxing-core` at the same version. Publishing the AAR without it
produces an artifact nobody can resolve -- which is what release.yml did until this
check existed, behind a comment claiming :core was withheld on purpose.

The check is structural rather than a list to keep in sync: it reads the publish tasks
out of release.yml, generates exactly those POMs, and asserts that every dependency in
them under this project's own group is one of the artifacts being published.

Usage: python3 -I tools/maint/check_release_publishes.py [--gradle ./gradlew]
"""

import argparse
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

GROUP = "io.github.kakakakacat.zxing"
POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
RELEASE_YML = pathlib.Path(".github/workflows/release.yml")

# :scanner:publishReleasePublicationToGitHubPackagesRepository -> ("scanner", "release")
TASK = re.compile(
    r":(?P<project>[A-Za-z0-9_\-]+):publish"
    r"(?P<publication>[A-Za-z0-9]+)PublicationToGitHubPackagesRepository"
)


def publish_tasks(workflow_text):
    found = {}
    for match in TASK.finditer(workflow_text):
        publication = match.group("publication")
        found[match.group("project")] = publication[0].lower() + publication[1:]
    return found


def generate_poms(gradle, tasks, version):
    if not tasks:
        return
    capitalised = [
        f":{project}:generatePomFileFor{publication[0].upper()}{publication[1:]}Publication"
        for project, publication in sorted(tasks.items())
    ]
    subprocess.run(
        [gradle, *capitalised, f"-PscannerVersion={version}"],
        check=True,
        stdout=subprocess.DEVNULL,
    )


def read_pom(project, publication):
    path = pathlib.Path(project) / "build/publications" / publication / "pom-default.xml"
    if not path.exists():
        sys.exit(f"{path} was not generated; the publication name may be wrong")
    root = ET.parse(path).getroot()

    def text(element, tag):
        node = element.find(f"m:{tag}", POM_NS)
        return node.text if node is not None else None

    coordinate = f"{text(root, 'groupId')}:{text(root, 'artifactId')}"
    dependencies = [
        f"{text(dep, 'groupId')}:{text(dep, 'artifactId')}"
        for dep in root.findall("m:dependencies/m:dependency", POM_NS)
    ]
    return coordinate, dependencies


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--gradle", default="./gradlew")
    parser.add_argument("--version", default="0.0.0-publishcheck")
    args = parser.parse_args()

    tasks = publish_tasks(RELEASE_YML.read_text())
    if not tasks:
        sys.exit(f"no GitHub Packages publish task found in {RELEASE_YML}")

    generate_poms(args.gradle, tasks, args.version)

    poms = {project: read_pom(project, pub) for project, pub in sorted(tasks.items())}
    published = {coordinate for coordinate, _ in poms.values()}

    problems = []
    for project, (coordinate, dependencies) in poms.items():
        for dependency in dependencies:
            if dependency.startswith(f"{GROUP}:") and dependency not in published:
                problems.append(
                    f":{project} publishes {coordinate}, whose POM depends on "
                    f"{dependency} -- which {RELEASE_YML} does not publish"
                )

    print(f"{RELEASE_YML} publishes: " + ", ".join(sorted(published)))
    if problems:
        print("\nrelease would be unresolvable:", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1
    print("every in-group dependency of every published POM is itself published")
    return 0


if __name__ == "__main__":
    sys.exit(main())
