"""Generate an offline Manual Gate A status/readiness page.

This is deliberately not a truth editor. It displays the strict UNKNOWN-only
state without embedding screenshots, source filenames, or network resources.
"""

from __future__ import annotations

import html
import re

from manual_gate.ledger_schema import validate_ledger, validate_manifest_for_review


def generate_review_html(manifest: dict, ledger: dict | None = None) -> str:
    """Render a deterministic, self-contained status page after strict validation."""
    validate_manifest_for_review(manifest)
    if ledger is not None:
        validate_ledger(ledger, manifest)

    dev_records = [record for record in manifest["records"] if record["lane"] == "development_candidate"]
    holdout_records = [
        record for record in manifest["records"] if record["lane"] == "prospective_holdout_candidate"
    ]
    ledger_map = {entry["candidateId"]: entry for entry in ledger["entries"]} if ledger else {}
    rows_html = _build_rows(dev_records, ledger_map)
    holdout_rows = _build_holdout_summary(holdout_records)

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Manual Gate A status</title>
<style>
body {{ font-family: Arial, sans-serif; margin: 20px; color: #212529; }}
.warning {{ border: 1px solid #777; padding: 12px; margin: 16px 0; }}
table {{ border-collapse: collapse; width: 100%; margin: 16px 0; }}
th, td {{ border: 1px solid #bbb; padding: 8px; text-align: left; }}
.unknown {{ font-style: italic; }}
</style>
</head>
<body>
<h1>Manual Gate A &mdash; UNKNOWN-only status</h1>
<div class="warning">
This is an offline status/readiness page, not a truth editor. No screenshot is embedded,
no source filename is included, and no value on this page is an approval.
</div>
<p><strong>Gate:</strong> OPEN &nbsp; <strong>Completion:</strong> INCOMPLETE</p>
<p><strong>Dataset:</strong> {html.escape(manifest['datasetId'])} &nbsp;
<strong>Development:</strong> {len(dev_records)} &nbsp;
<strong>Prospective holdout:</strong> {len(holdout_records)}</p>
<h2>Development candidates</h2>
<table><thead><tr><th>#</th><th>Candidate ID</th><th>SHA-256 prefix</th><th>Status</th></tr></thead>
<tbody>{rows_html}</tbody></table>
<h2>Prospective holdout candidates</h2>
<div class="warning">Holdout truth is not displayed or represented. All 20 candidates remain quarantined.</div>
{holdout_rows}
<p>Manual Gate A remains OPEN. Human truth, privacy, and provenance review are deferred.</p>
</body>
</html>"""


def _build_rows(records: list[dict], ledger_map: dict[str, dict]) -> str:
    lines = []
    for index, record in enumerate(records, 1):
        status = ledger_map.get(record["id"], {}).get("reviewStatus", "UNKNOWN")
        lines.append(
            f"<tr><td>{index}</td><td>{html.escape(record['id'])}</td>"
            f"<td>{html.escape(record['sha256'][:12])}&hellip;</td>"
            f'<td><span class="unknown">{html.escape(status)}</span></td></tr>'
        )
    return "\n".join(lines)


def _build_holdout_summary(records: list[dict]) -> str:
    lines = ["<table><thead><tr><th>#</th><th>Candidate ID</th><th>Status</th></tr></thead><tbody>"]
    for index, record in enumerate(records, 1):
        lines.append(
            f"<tr><td>{index}</td><td>{html.escape(record['id'])}</td>"
            '<td><span class="unknown">QUARANTINED</span></td></tr>'
        )
    lines.append("</tbody></table>")
    return "\n".join(lines)


def validate_no_external_references(html_content: str) -> None:
    """Reject common network-capable references in generated HTML."""
    patterns = [
        re.compile(r"https?://", re.IGNORECASE),
        re.compile(r"@import\s+url", re.IGNORECASE),
        re.compile(r"<script[^>]+src\s*=", re.IGNORECASE),
        re.compile(r"<link[^>]+href\s*=", re.IGNORECASE),
        re.compile(r"\bfetch\s*\(", re.IGNORECASE),
        re.compile(r"XMLHttpRequest", re.IGNORECASE),
        re.compile(r"WebSocket", re.IGNORECASE),
    ]
    for pattern in patterns:
        if pattern.search(html_content):
            raise ValueError("External network reference found in generated HTML")
