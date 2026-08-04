package com.simpleaccount.app.ui.importbill

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.importdata.BillParser
import com.simpleaccount.app.data.importdata.ImportProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

enum class ImportPhase { IDLE, PARSING, IMPORTING, DONE, ERROR }

data class ImportUiState(
    val phase: ImportPhase = ImportPhase.IDLE,
    val fileName: String = "",
    val inserted: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
    val sourceType: String = "wechat",
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val processor: ImportProcessor,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state = _state.asStateFlow()

    /** 读取 URI 内容并导入。 */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            try {
                resolveContent(uri)
            } catch (e: Exception) {
                _state.value = ImportUiState(phase = ImportPhase.ERROR, error = e.message ?: "读取文件失败")
            }
        }
    }

    private suspend fun resolveContent(uri: Uri) {
        val name = queryName(uri) ?: "bill"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw Exception("无法读取文件")

        var actualData = bytes
        var actualName = name
        var actualExt = name.substringAfterLast('.', "").lowercase()

        if (actualExt == "zip") {
            val (innerName, innerBytes) = extractFirstFile(bytes)
            actualName = innerName
            actualData = innerBytes
            actualExt = innerName.substringAfterLast('.', "").lowercase()
        }

        val contentType = detectSourceType(actualExt, actualName)
        _state.value = _state.value.copy(phase = ImportPhase.PARSING, fileName = actualName, sourceType = contentType)

        val parsed = BillParser.parse(actualData, actualName)
        val result = processor.process(
            rows = parsed.rows,
            sourceType = contentType,
            fileName = actualName,
            parseSkip = parsed.skipCount
        )

        _state.value = ImportUiState(
            phase = ImportPhase.DONE,
            fileName = actualName,
            inserted = result.inserted,
            skipped = result.skipped,
            sourceType = contentType
        )
    }

    private fun detectSourceType(ext: String, name: String): String {
        val n = name.lowercase()
        return when {
            n.contains("alipay") || n.contains("支付宝") -> "alipay"
            else -> "wechat"
        }
    }

    private fun extractFirstFile(data: ByteArray): Pair<String, ByteArray> {
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    if (ext == "xlsx" || ext == "xls" || ext == "csv" || ext == "txt") {
                        val content = zip.readBytes()
                        return entry.name to content
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        throw Exception("ZIP 中没有可解析的账单文件")
    }

    private fun queryName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri, null, null, null, null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    fun reset() {
        _state.value = ImportUiState()
    }
}
