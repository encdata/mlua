"""Resolve versioned loader source files using the Gradle target inheritance graph."""

from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[2]
TARGET_CONFIG = json.loads((ROOT / "gradle/mlua-targets.json").read_text(encoding="utf-8"))
TARGET_BY_VERSION = {str(target["minecraft"]): target for target in TARGET_CONFIG["targets"]}


def implementation_source_versions(version: str) -> list[str]:
    """Return the same deduplicated source-root chain as Gradle's targetPathChain."""
    chain = []
    seen_targets = set()
    seen_implementations = set()
    cursor = version
    while cursor:
        if cursor in seen_targets or cursor not in TARGET_BY_VERSION:
            raise ValueError(f"invalid or circular target inheritance while resolving {version}: {cursor}")
        seen_targets.add(cursor)
        target = TARGET_BY_VERSION[cursor]
        implementation = str(target.get("implementationTarget", cursor))
        if implementation not in seen_implementations:
            chain.append(implementation)
            seen_implementations.add(implementation)
        cursor = str(target["inherits"]) if target.get("inherits") else None
    return list(reversed(chain))


def inherited_loader_source(
    loader: str, version: str, source_path: str, fallback: Path | None = None
) -> Path | None:
    """Find the newest existing source in the roots Gradle merges for a target."""
    for source_version in reversed(implementation_source_versions(version)):
        candidate = ROOT / f"mlua-loaders/{loader}/{source_version}/{source_path}"
        if candidate.is_file():
            return candidate
    return fallback
