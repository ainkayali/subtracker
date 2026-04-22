package com.subtracker

import android.app.Application
import androidx.room.Room

class App : Application() {
    val db by lazy { Room.databaseBuilder(this, AppDb::class.java, "subs.db").build() }
}
