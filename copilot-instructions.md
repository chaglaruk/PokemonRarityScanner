# GitHub Copilot Custom Instructions
# Dosya yolu: projen/.github/copilot-instructions.md
# VS Code ayarlarında: github.copilot.chat.codeGeneration.useInstructionFiles = true

## Genel Kurallar

Always respond in Turkish unless the user explicitly writes in another language.

Follow the rules defined in AGENTS.md at the project root. If AGENTS.md exists, treat it as the primary source of truth.

## Kod Üretimi

- Never rewrite entire files. Make targeted changes only.
- Always check existing imports before adding new ones.
- Prefer the existing code style in the file over your defaults.
- When generating a new function, add a one-line docstring explaining its purpose.
- Do not add `console.log` or `print` debug statements unless asked.

## Güvenlik

- Never hardcode API keys, passwords, or secrets — use environment variables.
- Flag any input that goes directly to a database query or shell command.
- Use parameterized queries for all database interactions.

## Test

- When writing a new function, suggest a test case immediately after.
- Use the testing framework already present in the project (check package.json or requirements.txt).

## Bağlam

- Before modifying a file, read its top-level structure first.
- If unsure about a function's signature, say so — don't guess.
- Keep responses concise. The user can read code; don't over-explain.

## Token Verimliliği

- Avoid lengthy preambles. Get to the point.
- Don't repeat the user's question back to them.
- Skip obvious comments in generated code.
