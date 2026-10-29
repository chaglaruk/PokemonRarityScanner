package com.pokerarity.scanner.ui.result

import android.content.Intent
import com.pokerarity.scanner.data.model.IvSolveDetails

/** Writes one scan's typed IV and level evidence to the overlay Intent. */
internal fun Intent.putScanIvSolveExtras(solve: IvSolveDetails, estimate: String?) {
    putExtra(ResultActivity.EXTRA_IV_SOLVE_MODE, solve.ivSolveMode.name)
    putExtra(ResultActivity.EXTRA_IV_CANDIDATE_COUNT, solve.ivCandidateCount)
    solve.ivMin?.let { putExtra(ResultActivity.EXTRA_IV_MIN, it) }
    solve.ivMax?.let { putExtra(ResultActivity.EXTRA_IV_MAX, it) }
    solve.levelMin?.let { putExtra(ResultActivity.EXTRA_IV_LEVEL_MIN, it) }
    solve.levelMax?.let { putExtra(ResultActivity.EXTRA_IV_LEVEL_MAX, it) }
    if (solve.levelAlternatives.isNotEmpty()) {
        putExtra(
            ResultActivity.EXTRA_IV_LEVEL_ALTERNATIVES,
            solve.levelAlternatives.toFloatArray()
        )
    }
    estimate?.let { putExtra(ResultActivity.EXTRA_IV_ESTIMATE, it) }
    putStringArrayListExtra(
        ResultActivity.EXTRA_IV_SIGNALS,
        ArrayList(solve.ivSolveSignalsUsed)
    )
}
