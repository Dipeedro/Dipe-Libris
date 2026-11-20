package com.example.core.bibliographic.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SourceQueryStatus
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

interface ConnectivityChecker {
  fun isOnline(): Boolean
}

class AndroidConnectivityChecker(private val context: Context) : ConnectivityChecker {
  override fun isOnline(): Boolean {
    return runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
          ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      }
      .getOrDefault(true)
  }
}

data class SourceAdapterResult(
  val status: SourceQueryStatus,
  val candidates: List<SearchEditionCandidate> = emptyList(),
)

data class AdapterSearchRequest(
  val rawQuery: String,
  val parsedIntent: com.example.core.bibliographic.domain.ParsedSearchIntent,
  val page: Int = 1,
  val pageSize: Int = 30,
  val expandSecondary: Boolean = false,
)

interface BibliographicSourceAdapter {
  val source: MetadataSource

  suspend fun searchByIsbn(isbn13: String?, isbn10: String?, rawIsbn: String): SourceAdapterResult

  suspend fun searchByQuery(query: String): SourceAdapterResult

  suspend fun searchPaginated(request: AdapterSearchRequest): SourceAdapterResult {
    val isbn = request.parsedIntent.parsedIsbn
    return if (isbn != null) {
      if (request.page > 1) {
        SourceAdapterResult(SourceQueryStatus(source = source, state = ProviderOutcomeState.EMPTY))
      } else {
        searchByIsbn(isbn.isbn13, isbn.isbn10, isbn.cleaned)
      }
    } else {
      if (request.page > 1) {
        SourceAdapterResult(SourceQueryStatus(source = source, state = ProviderOutcomeState.EMPTY))
      } else {
        searchByQuery(request.rawQuery)
      }
    }
  }

  suspend fun fetchEditionsForWork(externalWorkKey: String): SourceAdapterResult =
    SourceAdapterResult(
      status = SourceQueryStatus(source = source, state = ProviderOutcomeState.EMPTY),
      candidates = emptyList(),
    )
}

object BibliographicHttpClients {
  const val USER_AGENT = "DipeLibris/1.3 (Android; personal-library-catalog; mailto:support@dipelibris.app)"
  const val CONTACT_EMAIL = "support@dipelibris.app"

  val json: Json =
    Json {
      ignoreUnknownKeys = true
      isLenient = true
      coerceInputValues = true
      explicitNulls = false
    }

  val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(7, TimeUnit.SECONDS)
      .writeTimeout(5, TimeUnit.SECONDS)
      .connectionPool(okhttp3.ConnectionPool(8, 3, TimeUnit.MINUTES))
      .addInterceptor { chain ->
        val original: Request = chain.request()
        val request =
          original
            .newBuilder()
            .header("User-Agent", USER_AGENT)
            .apply {
              if (original.header("Accept") == null) {
                header("Accept", "application/json")
              }
            }
            .build()
        chain.proceed(request)
      }
      .build()
  }
}

internal fun extractYear(raw: String?): Int? {
  if (raw.isNullOrBlank()) return null
  val match = Regex("(1[4-9]\\d{2}|20\\d{2}|2100)").find(raw) ?: return null
  return match.value.toIntOrNull()?.takeIf { it in 1400..2100 }
}
