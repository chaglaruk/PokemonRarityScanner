"""Offline HTML review page generator for Manual Gate A candidates.

Generates a self-contained HTML file with no external network dependencies.
All CSS and JavaScript are embedded inline.
"""

from __future__ import annotations

import html
import json
from typing import Any


def generate_review_html(manifest: dict, ledger: dict | None = None) -> str:
    """Generate a self-contained offline review HTML page.

    The output HTML has zero external resource references — no CDN links,
    no remote fonts, no analytics, no fetch calls.

    Args:
        manifest: The candidate manifest dict.
        ledger: Optional review ledger dict for status display.

    Returns:
        Complete HTML string.
    """
    dev_records = [
        r for r in manifest["records"]
        if r["lane"] == "development_candidate"
    ]
    holdout_records = [
        r for r in manifest["records"]
        if r["lane"] == "prospective_holdout_candidate"
    ]

    ledger_map: dict[str, dict] = {}
    if ledger:
        for entry in ledger.get("entries", []):
            ledger_map[entry["candidateId"]] = entry

    gate_status = "OPEN"
    completion = "INCOMPLETE"
    if ledger:
        gate_status = ledger.get("gateStatus", "OPEN")
        completion = ledger.get("completionStatus", "INCOMPLETE")

    rows_html = _build_rows(dev_records, ledger_map, "Development")
    holdout_rows = _build_holdout_summary(holdout_records)

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Manual Gate A Review</title>
<style>
body {{
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
               Helvetica, Arial, sans-serif;
  margin: 20px;
  background: #f8f9fa;
  color: #212529;
}}
h1 {{ color: #343a40; }}
h2 {{ color: #495057; margin-top: 30px; }}
.warning {{
  background: #fff3cd;
  border: 1px solid #ffc107;
  border-radius: 4px;
  padding: 12px 16px;
  margin: 16px 0;
}}
.status {{
  background: #e2e3e5;
  border-radius: 4px;
  padding: 12px 16px;
  margin: 16px 0;
}}
.status.open {{ border-left: 4px solid #dc3545; }}
table {{
  border-collapse: collapse;
  width: 100%;
  margin: 16px 0;
}}
th, td {{
  border: 1px solid #dee2e6;
  padding: 8px 12px;
  text-align: left;
  font-size: 14px;
}}
th {{ background: #e9ecef; }}
tr:nth-child(even) {{ background: #f8f9fa; }}
.unknown {{ color: #6c757d; font-style: italic; }}
.privacy-warning {{ color: #dc3545; font-weight: bold; }}
</style>
</head>
<body>
<h1>Manual Gate A &mdash; Candidate Review</h1>

<div class="warning">
<strong>&#9888; Privacy Notice:</strong> This page is generated offline.
No data is transmitted externally. Screenshots are NOT embedded.
Source filenames are NOT included. Review requires access to the
original source corpus.
</div>

<div class="status open">
<strong>Gate Status:</strong> {html.escape(gate_status)}<br>
<strong>Completion:</strong> {html.escape(completion)}<br>
<strong>Dataset:</strong> {html.escape(manifest.get('datasetId', 'unknown'))}<br>
<strong>Total Records:</strong> {len(manifest['records'])}<br>
<strong>Development:</strong> {len(dev_records)}<br>
<strong>Holdout (quarantined):</strong> {len(holdout_records)}
</div>

<div class="warning">
<strong>&#9888; Trust Boundary:</strong> No truth may be inferred from
scanner suggestions. All species, CP, HP, variant, and date fields
require manual verification from the source screen. Holdout truth
must never be exposed during development.
</div>

<h2>Development Candidates</h2>
<table>
<thead>
<tr>
<th>#</th>
<th>Candidate ID</th>
<th>SHA-256 (prefix)</th>
<th>Dimensions</th>
<th>Review Status</th>
<th>Privacy</th>
<th>Provenance</th>
</tr>
</thead>
<tbody>
{rows_html}
</tbody>
</table>

<h2>Holdout Candidates (Quarantined)</h2>
<div class="warning">
<strong>&#9888; Holdout Quarantine:</strong> These {len(holdout_records)}
candidates are quarantined. Truth must NOT be exposed during development.
Only summary information is shown.
</div>
{holdout_rows}

<hr>
<p style="color: #6c757d; font-size: 12px;">
Generated offline. No external network dependencies.
Manual Gate A remains OPEN. All candidates are non-authoritative.
</p>
</body>
</html>"""


def _build_rows(
    records: list[dict],
    ledger_map: dict[str, dict],
    section: str,
) -> str:
    """Build HTML table rows for development records."""
    lines = []
    for idx, record in enumerate(records, 1):
        rid = html.escape(record["id"])
        sha_prefix = html.escape(record["sha256"][:12])
        dims = f"{record['width']}x{record['height']}"

        entry = ledger_map.get(record["id"])
        if entry:
            status = html.escape(entry["reviewStatus"])
            privacy = html.escape(entry["privacyDisposition"])
            provenance = html.escape(entry["provenanceDisposition"])
        else:
            status = '<span class="unknown">UNKNOWN</span>'
            privacy = '<span class="unknown">NOT_REVIEWED</span>'
            provenance = '<span class="unknown">NOT_VERIFIED</span>'

        privacy_cls = record.get("privacyClassification", "")
        if privacy_cls == "NEEDS_HUMAN_PRIVACY_REVIEW":
            privacy_note = ' <span class="privacy-warning">&#9888;</span>'
        else:
            privacy_note = ""

        lines.append(
            f"<tr><td>{idx}</td><td>{rid}</td><td>{sha_prefix}&hellip;</td>"
            f"<td>{dims}</td><td>{status}</td>"
            f"<td>{privacy}{privacy_note}</td>"
            f"<td>{provenance}</td></tr>"
        )
    return "\n".join(lines)


def _build_holdout_summary(records: list[dict]) -> str:
    """Build a quarantined holdout summary table (no truth exposure)."""
    lines = ["<table>", "<thead><tr>",
             "<th>#</th><th>Candidate ID</th><th>SHA-256 (prefix)</th>"
             "<th>Dimensions</th><th>Status</th>",
             "</tr></thead><tbody>"]
    for idx, record in enumerate(records, 1):
        rid = html.escape(record["id"])
        sha_prefix = html.escape(record["sha256"][:12])
        dims = f"{record['width']}x{record['height']}"
        lines.append(
            f"<tr><td>{idx}</td><td>{rid}</td><td>{sha_prefix}&hellip;</td>"
            f"<td>{dims}</td>"
            f'<td><span class="unknown">QUARANTINED</span></td></tr>'
        )
    lines.append("</tbody></table>")
    return "\n".join(lines)


def validate_no_external_references(html_content: str) -> None:
    """Validate that HTML has no external network dependencies.

    Checks for common external reference patterns like http://, https://,
    CDN links, font imports, etc.
    """
    import re
    external_patterns = [
        re.compile(r'https?://', re.IGNORECASE),
        re.compile(r'@import\s+url', re.IGNORECASE),
        re.compile(r'<link[^>]+href\s*=\s*["\']https?://', re.IGNORECASE),
        re.compile(r'<script[^>]+src\s*=\s*["\']https?://', re.IGNORECASE),
        re.compile(r'fetch\s*\(', re.IGNORECASE),
        re.compile(r'XMLHttpRequest', re.IGNORECASE),
    ]
    for pattern in external_patterns:
        if pattern.search(html_content):
            raise ValueError(
                f"External network reference found: {pattern.pattern}"
            )
