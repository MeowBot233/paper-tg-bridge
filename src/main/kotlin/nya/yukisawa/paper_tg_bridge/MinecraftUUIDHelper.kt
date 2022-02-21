package nya.yukisawa.paper_tg_bridge

import retrofit2.http.GET
import retrofit2.http.Path


interface UuidHelper {
    @GET("{username}")
    suspend fun getUUID(@Path("username") username: String): Result
}

data class Result(
    val code: String,
    val message: String,
    val data: Data? = null
)

data class Data(
    val player: Player
)

data class Player(
    val username: String,
    val id: String
)