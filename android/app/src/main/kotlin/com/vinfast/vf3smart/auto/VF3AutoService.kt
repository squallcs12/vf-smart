package com.vinfast.vf3smart.auto

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.vinfast.vf3smart.data.repository.VF3Repository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VF3AutoService : CarAppService() {

    @Inject
    lateinit var repository: VF3Repository

    override fun createHostValidator(): HostValidator {
        // Use ALLOW_ALL_HOSTS_VALIDATOR for development to avoid "Package DENIED" errors
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return VF3AutoSession(repository)
    }
}
