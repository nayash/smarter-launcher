/*
 *     Copyright (C) 2021.  Asutosh Nayak (nayak.asutosh@ymail.com)
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.outliers.smartlauncher.consts

class Constants {
    companion object {
        const val PREF_NAME = "smart_launcher_pref"
        const val LAUNCH_SEQUENCE_SAVE_FILE = "launch_sequence_list_save.txt"
        const val LAUNCH_HISTORY_SAVE_FILE = "launch_history_map_save.txt"
        const val APP_SUGGESTIONS_SAVE_FILE = "app_suggestions_list_save.txt"
        const val ACTION_LAUNCHER_DATA_REFRESH = "launcher-data-changed"
        const val DISTANCE_TYPE_COSINE = "cosine"
        const val DISTANCE_TYPE_EUCLIDEAN = "euclidean"
        const val MIN_K = 20
    }
}