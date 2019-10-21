package pm.gnosis.heimdall.data.remote

import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import retrofit2.http.*
import okhttp3.ResponseBody
import pm.gnosis.heimdall.BuildConfig
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory

interface DappletServiceApi {
    @GET("{dappletId}.json")
    fun getDapplet(@Path("dappletId") dappletId: String): Single<ResponseBody>

    /**
     * Companion object to create the GithubApiService
     */
    companion object Factory {
        fun create(): DappletServiceApi {
            val retrofit = Retrofit.Builder()
                    //.client(client)
                    .baseUrl(BuildConfig.DAPPLET_SERVICE_URL)
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()))
                    .build()

            return retrofit.create(DappletServiceApi::class.java);
        }
    }
}
