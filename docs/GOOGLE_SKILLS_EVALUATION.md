# Purpose: Evaluate google/skills for future use without installing now.

# Google Skills Evaluation

## What Is google/skills?

[google/skills](https://github.com/google/skills) is a collection of
Google-maintained agent skill files focused on Google Cloud services, Firebase,
Gemini API, and cloud infrastructure. These are designed for AI coding agents
working with Google Cloud and Firebase projects.

## How It Differs From addyosmani/agent-skills

| Aspect | addyosmani/agent-skills | google/skills |
| --- | --- | --- |
| Focus | General development workflow | Google Cloud / Firebase services |
| Dependencies | None (Markdown guidance only) | Often requires Google Cloud SDK, Firebase CLI, API keys |
| Runtime impact | None | May create cloud resources, authenticate, incur costs |
| Applicability | Any project | Projects using Google Cloud / Firebase |
| Current relevance | High (workflow, testing, git, security) | Low (project does not use Google Cloud) |

## Why google/skills Should Not Be Installed Now

1. **No Google Cloud usage**: PokeRarityScanner does not currently use Firebase,
   Cloud Run, Cloud SQL, or Gemini API.
2. **Dependency risk**: Installing Google skills could prompt agents to add
   Firebase or Cloud dependencies prematurely.
3. **Credential risk**: Many Google skills assume authenticated Google Cloud
   access, which violates the project's safety rule against touching credentials
   or creating cloud resources.
4. **Scope creep**: Adding cloud infrastructure skills before the project needs
   them encourages architectural changes that are not yet justified.
5. **Passive-first principle**: The project prioritizes staying passive and
   local. Cloud integrations should be an explicit, deliberate decision.

## Skills That Could Become Useful Later

| Skill | When It Becomes Relevant |
| --- | --- |
| `firebase-basics` | If the project migrates telemetry or metadata sync to Firebase |
| `cloud-run-basics` | If the telemetry backend moves to Cloud Run |
| `cloud-sql-basics` | If the telemetry backend uses Cloud SQL |
| `google-cloud-waf-security` | If Cloud infrastructure is deployed and needs security review |
| `google-cloud-waf-reliability` | If Cloud infrastructure needs reliability/SRE review |
| `gemini-api` | If the project integrates Gemini for species recognition or analysis |
| `gemini-agents-api` | If the project uses Gemini agent APIs for autonomous features |

## Installation Rule

> Install only selected Google skills when the project actually starts Firebase,
> Cloud Run, Google Cloud, or Gemini API work. Do not pre-install.
