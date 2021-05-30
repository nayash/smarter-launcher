/*
 *  Copyright (c) 2021. Asutosh Nayak (nayak.asutosh@ymail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
    }
}