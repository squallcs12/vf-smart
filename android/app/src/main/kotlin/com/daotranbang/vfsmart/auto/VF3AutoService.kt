package com.daotranbang.vfsmart.auto

import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.daotranbang.vfsmart.data.repository.VF3Repository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VF3AutoService : CarAppService() {

    @Inject
    lateinit var repository: VF3Repository

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate — service bound by Android Auto host")
    }

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        Log.d(TAG, "onCreateSession — user opened VF3Smart in Android Auto")
        return VF3AutoSession(repository)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy — host disconnected")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VF3Auto"
    }
}
