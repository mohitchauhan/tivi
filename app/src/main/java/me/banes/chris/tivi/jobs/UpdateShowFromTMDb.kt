/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.banes.chris.tivi.jobs

import com.evernote.android.job.Job
import com.evernote.android.job.JobRequest
import com.evernote.android.job.util.support.PersistableBundleCompat
import me.banes.chris.tivi.data.daos.TiviShowDao
import me.banes.chris.tivi.tmdb.TmdbShowFetcher
import me.banes.chris.tivi.util.AppRxSchedulers
import timber.log.Timber
import javax.inject.Inject

class UpdateShowFromTMDb @Inject constructor(
    private val rxSchedulers: AppRxSchedulers,
    private val showsDao: TiviShowDao,
    private val tmdbShowFetcher: TmdbShowFetcher
) : Job() {
    companion object {
        const val TAG = "show-update-tmdb"
        private const val PARAM_SHOW_TMDB_ID = "show-tmdb-id"
        private const val PARAM_FORCE_REFRESH = "force"

        fun buildRequest(showTmdbId: Int, forceRefresh: Boolean = false): JobRequest.Builder {
            return JobRequest.Builder(TAG).addExtras(
                    PersistableBundleCompat().apply {
                        putInt(PARAM_SHOW_TMDB_ID, showTmdbId)
                        putBoolean(PARAM_FORCE_REFRESH, forceRefresh)
                    }
            )
        }
    }

    override fun onRunJob(params: Params): Result {
        val tmdbId = params.extras.getInt(PARAM_SHOW_TMDB_ID, -1)
        require(tmdbId != -1)

        val forceRefresh = params.extras.getBoolean(PARAM_FORCE_REFRESH, false)

        Timber.d("$TAG job running for id: $tmdbId")

        return try {
            var needRefresh = forceRefresh
            if (!needRefresh) {
                val show = showsDao.getShowWithTmdbIdMaybe(tmdbId)
                        .subscribeOn(rxSchedulers.database)
                        .blockingGet()
                needRefresh = show != null && show.needsUpdateFromTmdb()
            }
            if (needRefresh) {
                tmdbShowFetcher.updateShow(tmdbId).blockingAwait()
            }
            Result.SUCCESS
        } catch (exception: Exception) {
            Result.FAILURE
        }
    }
}