package com.chochocho.homephotoclient.backup

import com.chochocho.homephotoclient.data.BackupCapacity
import retrofit2.Response

internal class ServerStorageWait(message: String) : Exception(message)

/** 구형 서버만 사전 조회를 생략한다. 507은 일반 실패/즉시 재전송으로 취급하지 않는다. */
internal fun checkUploadCapacity(response: Response<BackupCapacity>) {
    if (response.code() == 404) return
    if (response.code() == 507) throw ServerStorageWait("서버 공간 확보 대기 중입니다.")
    if (!response.isSuccessful) throw retrofit2.HttpException(response)
    val state = response.body() ?: throw java.io.IOException("서버 수신 용량 응답이 비어 있습니다.")
    if (!state.accepting) throw ServerStorageWait(state.reason ?: "서버 공간 확보 대기 중입니다.")
}
