package kr.co.ninetyseconds.recommendation

import android.app.Application

class RecommendationApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
