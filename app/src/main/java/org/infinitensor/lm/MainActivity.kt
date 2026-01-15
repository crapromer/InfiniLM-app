package org.infinitensor.lm

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
import android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.infinitensor.lm.ui.theme.LmAndroidTheme
import android.content.Context
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Close
import androidx.documentfile.provider.DocumentFile
import kotlin.system.measureTimeMillis

object ServiceManager {
    private var isInitialized = false

    fun initialize(modelPath: String) {
        if (!isInitialized) {
            Native.init(modelPath)
            isInitialized = true
        }
    }
}


data class Message(var text: String, val isUser: Boolean)

class MainActivity : ComponentActivity() {
    companion object {
        init {
            try {
                System.loadLibrary("infinilm_chat")
            } catch (e: UnsatisfiedLinkError) {
                throw RuntimeException("Native library not found", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LmAndroidTheme {
                var showModelDialog by remember { mutableStateOf(false) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        titleBar(
                            name = "Chat",
                            onModelButtonClick = { showModelDialog = true }
                        )
                    },
                    content = {paddingValues ->
                        mainAPP(
                            Modifier.padding(paddingValues),
                            showModelDialog = showModelDialog,
                            onDismissDialog = { showModelDialog = false },
                            onShowDialog = { showModelDialog = true }
                        )
                    }
                )
            }
        }

        if (!Environment.isExternalStorageManager()) {
            Toast.makeText(this, "存储权限未被授予", Toast.LENGTH_SHORT).show()
            var intent = Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:$packageName"));
            registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "授权成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "授权失败", Toast.LENGTH_SHORT).show()
                }
            }.launch(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun titleBar(name: String, onModelButtonClick: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = {
            Text("九格大模型")
        },
        actions = {
            TextButton(onClick = onModelButtonClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "选择模型",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("选择模型")
            }
        }
    )
}

@Composable
fun chatScreen(modifier: Modifier){
    var message by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(emptyList<Message>()) }
    var isButtonEnabled by remember { mutableStateOf(true) }
    val context = LocalContext.current

    fun onSubmit() {
        // 开始异步任务
        isButtonEnabled = false
        CoroutineScope(Dispatchers.IO).launch {
            if (message.isNotEmpty()) {
                //Native.start(message)
                val startTime = measureTimeMillis {
                    Native.start(message)
                }
                messages += Message("$message\nprefill耗时: ${startTime}ms", true)
                message = ""
            }
            var botMessage = Message("", false)
            messages += botMessage
            var decodeCount = 0
            var totalDecodeTime = 0L
            while (true) {
                val text: String
                val decodeTime = measureTimeMillis {
                    text = Native.decode()
                }

                if (text.isEmpty()) {
                    break
                } else {
                    withContext(Dispatchers.Main) {
                        botMessage = botMessage.copy(text = botMessage.text + text)
                        //messages[messages.size - 1] = botMessage
                        messages = messages.dropLast(1) + botMessage
                    }
                }
                totalDecodeTime += decodeTime
                decodeCount++

                // 记录单次 decode 耗时

            }

            if (decodeCount > 0) {
                val avgDecodeTime = totalDecodeTime / decodeCount
                botMessage = botMessage.copy(text = botMessage.text + "\ndecode平均耗时: ${avgDecodeTime}ms")
                withContext(Dispatchers.Main) {
                    //messages[messages.size - 1] = botMessage
                    messages = messages.dropLast(1) + botMessage

                }
            }
            // 任务完成，恢复按钮可点击状态
            withContext(Dispatchers.Main) {
                isButtonEnabled = true
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        // 历史聊天记录
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
                .background(Color(0xFFF0F0F0), shape = RoundedCornerShape(12.dp))
        ) {
            messages.forEach { msg ->
                val icon: ImageVector = if (msg.isUser) {
                    Icons.Default.Person  // 用户图标
                } else {
                    Icons.Default.Face  // 机器人图标
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
        }

        // 输入框和发送按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .background(Color.LightGray, shape = MaterialTheme.shapes.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
                textStyle = TextStyle(fontSize = 18.sp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {onSubmit()},
                enabled = message.isNotEmpty() && isButtonEnabled
            ) {
                Text("发送")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun chatPreview() {
    LmAndroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                titleBar(name = "Chat", onModelButtonClick = {})
            },
            content = {paddingValues ->
                chatScreen(Modifier.padding(paddingValues))
            }
        )
    }
}

@Composable
fun mainAPP(
    modifier: Modifier,
    showModelDialog: Boolean,
    onDismissDialog: () -> Unit,
    onShowDialog: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    var modelFolderPath by remember { mutableStateOf<String>("") }
    var isInitialized by remember { mutableStateOf(false) }

    // 初始化时加载已保存的模型路径
    LaunchedEffect(Unit) {
        val savedFolderPath = sharedPreferences.getString("defaultModelFolder", "")
        if (savedFolderPath != null && savedFolderPath.isNotEmpty() && isValidFolder(savedFolderPath)) {
            Log.i("model path", savedFolderPath)
            modelFolderPath = savedFolderPath
            ServiceManager.initialize(modelFolderPath)
            isInitialized = true
        } else {
            isInitialized = true
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val newPath = getAbsolutePathFromUri(context, uri)
                if (newPath.isNotEmpty()) {
                    modelFolderPath = newPath
                    Log.i("model path", modelFolderPath)
                    sharedPreferences.edit().putString("defaultModelFolder", modelFolderPath).apply()
                    ServiceManager.initialize(modelFolderPath)
                    Toast.makeText(context, "模型加载成功", Toast.LENGTH_SHORT).show()
                    onDismissDialog()
                } else {
                    Toast.makeText(context, "无法获取文件夹路径，请重试。", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showModelDialog) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        FolderSelectionDialog(
            currentPath = modelFolderPath,
            onSelectFolder = { folderPicker.launch(intent) },
            onDismiss = onDismissDialog
        )
    }
    
    chatScreen(modifier)
}

@Composable
fun FolderSelectionDialog(
    currentPath: String,
    onSelectFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题
                Text(
                    text = "模型权重文件夹设置",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 当前路径显示区域
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "当前模型路径：",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (currentPath.isEmpty()) {
                            Text(
                                text = "未选择",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                            Text(
                                text = "请选择模型权重文件夹",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                            )
                        } else {
                            Text(
                                text = currentPath,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp),
                                maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                // 按钮区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onSelectFolder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (currentPath.isEmpty()) "选择文件夹" else "更改文件夹")
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("取消")
                    }
                }
            }
        }
    }
}

fun isValidFolder(path:String?): Boolean {
    if (path != ""){
        return true;
    }else{
        return false;
    }
}

fun getAbsolutePathFromUri(context: Context, uri: Uri): String {
    val documentFile = DocumentFile.fromTreeUri(context, uri)
    documentFile?.let {
        // 获取文件名
        val displayName = it.name ?: return ""

        // 假设目标路径为 /storage/emulated/0/Download/
        val basePath = "/storage/emulated/0/"

        // 返回拼接好的绝对路径
        return "$basePath$displayName"
    }
    return ""
}
