# detekt baseline

`baseline.xml` records findings that existed when detekt was introduced.

CI runs detekt with this baseline and fails on new findings. The baseline must not be regenerated merely to make CI green. Remove entries as the corresponding code is corrected, and regenerate it only after an intentional, reviewed baseline reset.
