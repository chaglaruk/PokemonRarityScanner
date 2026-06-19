# IV Checker Risk Register

| Risk | Severity | Current control | Follow-up |
| --- | --- | --- | --- |
| Fake exact IV from ambiguous evidence | High | Solver returns `EXACT` only when one candidate remains. | Preserve this invariant when wiring UI. |
| Formula/table drift | Medium | Slice uses explicit CPM table already present in app code and tests known fixtures. | Move CPM/cost tables to shared asset later. |
| Form-specific stats missing | Medium | Slice accepts caller-provided base stats and does not guess form stats. | Add form-aware stats adapter later. |
| Stardust costs for lucky/shadow/purified | Medium | Slice only treats regular stardust as a level bucket. | Add state-aware normalization after reliable state evidence. |
| ToS/privacy regression | High | Pure offline math only; no network, login, automation, memory, or traffic access. | Keep scan pipeline passive. |
| Overbuilt PvP engine | Low | PvP ranking skipped. | Add only after core IV solver is wired and tested. |
