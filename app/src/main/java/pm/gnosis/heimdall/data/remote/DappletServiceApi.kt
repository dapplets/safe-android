package pm.gnosis.heimdall.data.remote

import io.reactivex.Single
import retrofit2.http.*
import okhttp3.ResponseBody

interface DappletServiceApi {
    @GET("{dappletId}.json")
    fun getDapplet(@Path("dappletId") dappletId: String): Single<ResponseBody>
}
