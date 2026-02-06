package com.vinfast.vf3smart.auto

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.vinfast.vf3smart.BuildConfig
import com.vinfast.vf3smart.data.repository.VF3Repository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VF3AutoService : CarAppService() {

    @Inject
    lateinit var repository: VF3Repository

    override fun createHostValidator(): HostValidator {
        return if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session {
        return VF3AutoSession(repository)
    }
}
