package com.daotranbang.vfsmart.data.network

import com.daotranbang.vfsmart.data.model.*
import retrofit2.http.*

/**
 * Retrofit API service for VF3-Smart ESP32 device
 * Base URL: http://<device-ip>/
 *
 * Authentication: All POST endpoints require X-API-Key header (handled by AuthInterceptor)
 * GET /car/status does not require authentication
 */
interface VF3ApiService {

    /**
     * Get complete car status (no authentication required)
     * Also available via WebSocket at ws://<device-ip>/ws for real-time updates
     */
    @GET("/car/status")
    suspend fun getCarStatus(): CarStatus

    /**
     * Lock the car (requires API key)
     * Triggers 1-second relay pulse on car_lock output
     */
    @POST("/car/lock")
    suspend fun lockCar(): LockResponse

    /**
     * Unlock the car (requires API key)
     * Triggers 1-second relay pulse on car_unlock output
     */
    @POST("/car/unlock")
    suspend fun unlockCar(): LockResponse

    /**
     * Control accessory power (requires API key)
     * @param state: "on", "off", or "toggle"
     */
    @FormUrlEncoded
    @POST("/car/accessory-power")
    suspend fun controlAccessoryPower(
        @Field("state") state: String
    ): ControlResponse

    /**
     * Control inside cameras (requires API key)
     * @param state: "on", "off", or "toggle"
     */
    @FormUrlEncoded
    @POST("/car/inside-cameras")
    suspend fun controlInsideCameras(
        @Field("state") state: String
    ): ControlResponse

    /**
     * Start auto-closing windows (requires API key)
     * Triggers 30-second window close operation
     */
    @POST("/car/windows/close")
    suspend fun closeWindows(): WindowResponse

    /**
     * Stop window operation immediately (requires API key)
     */
    @POST("/car/windows/stop")
    suspend fun stopWindows(): WindowResponse

    /**
     * Control window down operation (requires API key)
     * @param side: "left", "right", or "both"
     * @param state: "on" or "off"
     */
    @FormUrlEncoded
    @POST("/car/windows/down")
    suspend fun controlWindowsDown(
        @Field("side") side: String,
        @Field("state") state: String
    ): WindowResponse

    /**
     * Control window up operation (requires API key)
     * @param side: "left", "right", or "both"
     * @param state: "on" or "off"
     */
    @FormUrlEncoded
    @POST("/car/windows/up")
    suspend fun controlWindowsUp(
        @Field("side") side: String,
        @Field("state") state: String
    ): WindowResponse

    /**
     * Control buzzer/horn (requires API key)
     * @param state: "on", "off", or "beep"
     * @param duration: Duration in milliseconds (optional, for "beep" mode)
     */
    @FormUrlEncoded
    @POST("/car/buzzer")
    suspend fun controlBuzzer(
        @Field("state") state: String,
        @Field("duration") duration: Int? = null
    ): BuzzerResponse

    /**
     * Control light reminder system (requires API key)
     * @param state: "on", "off", "enable", "disable", or "toggle"
     */
    @FormUrlEncoded
    @POST("/car/light-reminder")
    suspend fun controlLightReminder(
        @Field("state") state: String
    ): ControlResponse

    /**
     * Manually unlock charger port (requires API key)
     * Triggers 1-second pulse to unlock charger
     */
    @POST("/car/charger-unlock")
    suspend fun unlockCharger(): ChargerResponse

    /**
     * Control side mirrors (requires API key)
     * @param action: "open" or "close"
     */
    @FormUrlEncoded
    @POST("/car/side-mirrors")
    suspend fun controlSideMirrors(
        @Field("action") action: String
    ): ControlResponse

    /**
     * Control ODO screen (requires API key)
     * @param state: "on", "off", or "toggle"
     */
    @FormUrlEncoded
    @POST("/car/odo-screen")
    suspend fun controlOdoScreen(
        @Field("state") state: String
    ): ControlResponse

    /**
     * Control armrest (requires API key)
     * @param state: "on", "off", or "toggle"
     */
    @FormUrlEncoded
    @POST("/car/armrest")
    suspend fun controlArmrest(
        @Field("state") state: String
    ): ControlResponse

    /**
     * Control dashcam (requires API key)
     * @param state: "on", "off", or "toggle"
     */
    @FormUrlEncoded
    @POST("/car/dashcam")
    suspend fun controlDashcam(
        @Field("state") state: String
    ): ControlResponse

    /**
     * Get TPMS sensor ID assignments (no auth required)
     */
    @GET("/tpms/calibrate")
    suspend fun getTpmsCalibration(): TpmsCalibrationResponse

    /**
     * Reset all TPMS sensor assignments (requires API key)
     */
    @FormUrlEncoded
    @POST("/tpms/calibrate")
    suspend fun tpmsCalibrate(
        @Field("action") action: String,
        @Field("a") posA: String? = null,
        @Field("b") posB: String? = null
    ): TpmsCalibrationResponse
}
