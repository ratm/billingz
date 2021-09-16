package com.zuko.billingz.lib.extra

class Result<T> private constructor(val status: Status, val data: T?, val msg: String?) {

    enum class Status { UNKNOWN, SUCCESS, ERROR, LOADING }

    companion object {
        @JvmStatic
        fun <T> success(data: T): Result<T> {
            return Result(Status.SUCCESS, data, null)
        }

        @JvmStatic
        fun <T> error(data: T, msg: String?): Result<T> {
            return Result(Status.ERROR, data, msg)
        }

        @JvmStatic
        fun <T> loading(data: T, msg: String?): Result<T> {
            return Result(Status.LOADING, data, msg)
        }
    }
}