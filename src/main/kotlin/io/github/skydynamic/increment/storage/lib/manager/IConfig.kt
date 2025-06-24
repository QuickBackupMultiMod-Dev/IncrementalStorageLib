package io.github.skydynamic.increment.storage.lib.manager

interface IConfig {
    fun getStoragePath(): String {
        return "./storage/"
    }
}