Worked for 10m 33s

### Android Assist App कैसे बनाते हैं? Android 10 से Android 17 तक

तुम्हारे `compileSdk 37`, `targetSdk 37` और `minSdk 29` वाले Kotlin + XML project के हिसाब से समझते हैं।

सबसे पहले एक जरूरी बात: Android में Assist App बनने के लिए अपना पूरा Google Assistant जैसा voice assistant बनाना जरूरी नहीं है। Android इसके लिए एक lightweight implementation भी देता है।

### 1\. इसका असली नाम क्या है?

इस feature से जुड़े कुछ अलग-अलग नाम हैं। इन्हें एक समझकर code लिखने लगे तो बेकार का chutiyapa हो जाएगा।

| नाम                     | इसका मतलब                                            |
| ----------------------- | ---------------------------------------------------- |
| Assist app              | Settings में दिखने वाला user-facing नाम              |
| Digital assistant app   | कुछ Android versions/OEMs में इस्तेमाल होने वाला नाम |
| ROLE_ASSISTANT          | Android का official role identifier                  |
| Intent.ACTION_ASSIST    | Assistant invocation के लिए Activity Intent          |
| VoiceInteractionService | Full voice assistant का Android service API          |
| VoiceInteractionSession | Assistant का interaction session और UI               |

सबसे जरूरी API constant:

```kotlin
RoleManager.ROLE_ASSISTANT
```

इसकी actual string value है:

```text
android.app.role.ASSISTANT
```

`ROLE_ASSIST` नाम का constant इस्तेमाल नहीं करना है। सही नाम `ROLE_ASSISTANT` है। यह Android API 29 से उपलब्ध है। Android Developers +1

### 2\. App को Assist app बनाने के दो तरीके हैं

Android के official role requirements के अनुसार दो रास्ते हैं:

Option A: `ACTION_ASSIST` Activity

Recommended for basic Assist integration

* Manifest में एक Activity register करनी है।

* System उस Activity को assist request पर launch कर सकता है।

* Android की Default Apps list में आने के लिए यह एक qualifying implementation है।

* Voice recognition या always-on service बनाना जरूरी नहीं है।

Option B: `VoiceInteractionService`

Full voice assistant

* VoiceInteractionService implement करना होगा।

* VoiceInteractionSessionService और RecognitionService भी चाहिए।

* Assistant session UI, voice recognition और assist context handling जैसी capabilities मिलती हैं।

* System इस service को selected assistant होने पर हमेशा running रख सकता है।

Android Open Source Project +2

मेरी recommendation: पहले Option A implement करो। जब app को real voice assistant बनाना हो, तभी Option B अपनाना। सिर्फ Settings में दिखने के लिए तीन services बनाने की जरूरत नहीं है।

### 3\. Option A: `ACTION_ASSIST` Activity implement करना

यह सबसे सीधा और हल्का तरीका है।

Step 1: AndroidManifest.xml

```xml
<activity
    android:name=".assistant.AssistEntryActivity"
    android:exported="true"
    android:label="@string/app_name">

    <intent-filter>
        <action android:name="android.intent.action.ASSIST" />

        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>

</activity>
```

बस इतना करने से तुम्हारी Activity `ACTION_ASSIST` Intent handle करने के लिए register हो जाएगी।

ध्यान दो:

* `android:exported="true"` जरूरी है, क्योंकि Android System को इस Activity को launch करना है।

* `CATEGORY_DEFAULT` implicit Intent resolution के लिए जरूरी है।

* कोई special permission declare करने की जरूरत नहीं है।

* कोई additional dependency भी जरूरी नहीं है।

यह Android की `ACTION_ASSIST` Intent contract के अनुसार है। Android Developers +1

Step 2: AssistEntryActivity.kt

```kotlin
package com.example.app.assistant

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AssistEntryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action != Intent.ACTION_ASSIST) {
            finish()
            return
        }

        val sourcePackage = intent.getStringExtra(
            Intent.EXTRA_ASSIST_PACKAGE
        )

        val sourceUid = intent.getIntExtra(
            Intent.EXTRA_ASSIST_UID,
            -1
        )

        // Replace this with your actual assistant UI.
        setContentView(
            TextView(this).apply {
                text = buildString {
                    appendLine("Assistant opened")
                    appendLine("Source: ${sourcePackage ?: "Unknown"}")
                    append("UID: $sourceUid")
                }
                textSize = 18f
            }
        )
    }
}
```

यहाँ दो useful extras हैं:

| Extra                       | जानकारी                                          |
| --------------------------- | ------------------------------------------------ |
| Intent.EXTRA_ASSIST_PACKAGE | उस समय foreground में चल रहे app का package name |
| Intent.EXTRA_ASSIST_UID     | उस foreground app का UID                         |

`EXTRA_ASSIST_PACKAGE` API 18 से और `EXTRA_ASSIST_UID` API 23 से उपलब्ध हैं। तुम्हारा minimum API 29 है, इसलिए दोनों सीधे इस्तेमाल कर सकते हो। ये optional contextual values हैं, हमेशा मिलेंगी इसकी guarantee नहीं है। Android Developers

यह Activity सिर्फ sample UI दिखाती है। असली assistant में इसी जगह search, commands, contextual actions या अपना assistant interface implement करना होगा।

### 4\. User से app को Default Assistant बनवाना

Manifest में Activity register कर देने से app eligible बनती है। लेकिन वह अपने आप default नहीं बनती।

इसके लिए Android का official `RoleManager` API इस्तेमाल करना है।

### Default Assistant role request करना

Modern तरीका `ActivityResultContracts.StartActivityForResult` है।

MainActivity.kt

```kotlin
import android.app.role.RoleManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val assistantRoleLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // Never trust only the activity result.
            // Re-check the actual role state.
            updateAssistantStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updateAssistantStatus()
    }

    private fun requestAssistantRole() {
        val roleManager = getSystemService(
            RoleManager::class.java
        )

        if (!roleManager.isRoleAvailable(
                RoleManager.ROLE_ASSISTANT
            )
        ) {
            // Assistant role is unavailable on this device.
            return
        }

        if (roleManager.isRoleHeld(
                RoleManager.ROLE_ASSISTANT
            )
        ) {
            // Already the default assistant.
            return
        }

        val requestIntent = roleManager.createRequestRoleIntent(
            RoleManager.ROLE_ASSISTANT
        )

        assistantRoleLauncher.launch(requestIntent)
    }

    private fun updateAssistantStatus() {
        val roleManager = getSystemService(
            RoleManager::class.java
        )

        val isDefaultAssistant =
            roleManager.isRoleAvailable(
                RoleManager.ROLE_ASSISTANT
            ) && roleManager.isRoleHeld(
                RoleManager.ROLE_ASSISTANT
            )

        // Update your UI using isDefaultAssistant.
        // true  = This app holds the assistant role.
        // false = This app does not hold the assistant role.
    }

    override fun onResume() {
        super.onResume()
        updateAssistantStatus()
    }
}
```

इस code में तीन important methods हैं:

| Method                    | काम                                            |
| ------------------------- | ---------------------------------------------- |
| isRoleAvailable()         | Device पर Assistant role उपलब्ध है या नहीं     |
| isRoleHeld()              | तुम्हारा app अभी role holder है या नहीं        |
| createRequestRoleIntent() | System का role selection/confirmation UI खोलना |

इन सभी APIs को Android 10 यानी API 29 में introduce किया गया था। Role request पर अंतिम फैसला user का होता है। App खुद को silently default assistant नहीं बना सकता। Android Developers +1

### User को Settings से भी select करने देना

User को manually यह रास्ता भी बता सकते हो:

```text
Settings
  → Apps
    → Default apps
      → Assist app / Digital assistant app
        → Your App
```

Exact label manufacturer और Android version के अनुसार बदल सकता है।

### 5\. कैसे पता चलेगा कि हमारा App Default Assist App है?

यह सबसे important हिस्सा है।

इसका सीधा तरीका:

```kotlin
val roleManager = getSystemService(RoleManager::class.java)

val isDefaultAssistant =
    roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
```

true

तुम्हारा app Assistant role holder है।

false

तुम्हारा app Assistant role holder नहीं है।

मैं recommend करूंगा कि इसे एक अलग utility function में रखो:

AssistantRoleChecker.kt

```kotlin
package com.example.app.assistant

import android.app.role.RoleManager
import android.content.Context

object AssistantRoleChecker {

    fun isAvailable(context: Context): Boolean {
        val roleManager = context.getSystemService(
            RoleManager::class.java
        )

        return roleManager.isRoleAvailable(
            RoleManager.ROLE_ASSISTANT
        )
    }

    fun isDefaultAssistant(context: Context): Boolean {
        val roleManager = context.getSystemService(
            RoleManager::class.java
        )

        return roleManager.isRoleAvailable(
            RoleManager.ROLE_ASSISTANT
        ) && roleManager.isRoleHeld(
            RoleManager.ROLE_ASSISTANT
        )
    }
}
```

अब कहीं भी इस्तेमाल कर सकते हो:

```kotlin
val isAssistant =
    AssistantRoleChecker.isDefaultAssistant(this)
```

एक जरूरी distinction: यदि तुम `ACTION_ASSIST` Activity वाला तरीका अपना रहे हो, तो यही role-check सही है। लेकिन इससे यह साबित नहीं होता कि तुम्हारा `VoiceInteractionService` भी active है।

Full VoiceInteractionService वाले implementation में उसकी अलग जाँच भी कर सकते हो:

```kotlin
val isVoiceServiceActive =
    VoiceInteractionService.isActiveService(
        this,
        ComponentName(
            this,
            AppVoiceInteractionService::class.java
        )
    )
```

इसका परिणाम बताता है कि दिया गया service component वर्तमान में active voice interaction service है या नहीं। Android Developers +1

### 6\. Option B: Full `VoiceInteractionService` implementation

अब मान लो तुम्हें ऐसा assistant बनाना है जिसे user system gesture से open कर सके, जो voice commands ले सके और foreground app का assist context process कर सके।

इसके लिए Android का native Voice Interaction Framework इस्तेमाल करना होगा।

इस architecture में तीन मुख्य components हैं:

VoiceInteractionService

System-bound service · Assistant lifecycle

VoiceInteractionSessionService

Assistant UI and sessions

RecognitionService

Speech recognition

Session service and speech recognition are separate responsibilities.

### 6.1 VoiceInteractionService

```kotlin
package com.example.app.assistant

import android.service.voice.VoiceInteractionService

class AppVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()

        // Initialize lightweight assistant components.
        // Avoid heavy processing here.
    }
}
```

यह Android System के साथ तुम्हारे assistant का मुख्य connection है।

एक important बात: Android इसे selected assistant होने पर हमेशा running रख सकता है। इसलिए इसमें heavy UI, network operations या expensive initialization नहीं डालना चाहिए। Android Developers +1

### 6.2 VoiceInteractionSessionService

```kotlin
package com.example.app.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class AppVoiceInteractionSessionService :
    VoiceInteractionSessionService() {

    override fun onNewSession(
        args: Bundle
    ): VoiceInteractionSession {
        return AppVoiceInteractionSession(this)
    }
}
```

यह हर नए assistant interaction के लिए session create करेगा।

### 6.3 VoiceInteractionSession

```kotlin
package com.example.app.assistant

import android.content.Context
import android.service.voice.VoiceInteractionSession
import android.util.Log

class AppVoiceInteractionSession(
    context: Context
) : VoiceInteractionSession(context) {

    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)

        val structure = state.assistStructure

        Log.d(
            "AppAssistant",
            "Focused: ${state.isFocused}, " +
                "Structure available: ${structure != null}"
        )

        // Process available assist context here.
    }
}
```

इसका मुख्य callback है:

```kotlin
onHandleAssist(state: AssistState)
```

यह callback API 29 में introduce हुआ था और पुराने deprecated `onHandleAssist(Bundle, AssistStructure, AssistContent)` की जगह इस्तेमाल करना चाहिए।

इससे available contextual information मिल सकती है:

* `state.assistStructure`

* `state.activityId`

* `state.isFocused`

ध्यान रहे, structure हमेशा उपलब्ध नहीं होगा। Source app, user privacy settings और secure windows इसकी availability प्रभावित करते हैं। Android Developers +1

### 6.4 RecognitionService

Full VoiceInteractionService implementation में RecognitionService भी जरूरी है।

इसके लिए Android metadata में दोनों services specify करता है:

```xml
android:sessionService="..."
android:recognitionService="..."
```

इनमें से कोई missing हुआ तो platform VoiceInteractionService metadata को invalid मान सकता है।

तुम्हें एक actual `RecognitionService` subclass implement करना होगा, जिसमें ये callbacks होते हैं:

```kotlin
onStartListening()
onStopListening()
onCancel()
```

इनकी जिम्मेदारी speech engine से microphone input लेकर recognized speech को callbacks के जरिए वापस देना है। केवल खाली service register कर देना functional voice assistant नहीं बनाता। Git at Google +1

### 6.5 Manifest configuration

Full voice implementation के लिए:

```xml
<service
    android:name=".assistant.AppVoiceInteractionService"
    android:exported="true"
    android:permission="android.permission.BIND_VOICE_INTERACTION">

    <intent-filter>
        <action android:name="android.service.voice.VoiceInteractionService" />
    </intent-filter>

    <meta-data
        android:name="android.voice_interaction"
        android:resource="@xml/voice_interaction_service" />

</service>

<service
    android:name=".assistant.AppVoiceInteractionSessionService"
    android:exported="true"
    android:process=":voice_session"
    android:permission="android.permission.BIND_VOICE_INTERACTION" />
```

RecognitionService का अपना service declaration भी जोड़ना होगा:

```xml
<service
    android:name=".assistant.AppRecognitionService"
    android:exported="true"
    android:permission="android.permission.BIND_SPEECH_RECOGNITION">

    <intent-filter>
        <action android:name="android.speech.RecognitionService" />
    </intent-filter>

    <meta-data
        android:name="android.speech"
        android:resource="@xml/recognition_service" />

</service>
```

यहाँ `AppRecognitionService` तुम्हारी वास्तविक speech recognition implementation होगी।

res/xml/voice_interaction_service.xml

```xml
<?xml version="1.0" encoding="utf-8"?>

<voice-interaction-service
    xmlns:android="http://schemas.android.com/apk/res/android"

    android:sessionService="com.example.app.assistant.AppVoiceInteractionSessionService"

    android:recognitionService="com.example.app.assistant.AppRecognitionService"

    android:supportsAssist="true"

    android:usesAssistData="true"

    android:usesAssistScreenshots="false" />
```

`supportsAssist="true"` विशेष रूप से महत्वपूर्ण है। इससे service declare करती है कि वह assist action handle कर सकती है।

`sessionService` और `recognitionService` दोनों valid components होने चाहिए। इन्हें सिर्फ manifest में लिखकर छोड़ना पर्याप्त नहीं है। Android Developers +2

### 7\. Android 17 (API 37) का नया feature: Screen Context Access

यह हिस्सा खास तौर पर तुम्हारे SDK 37 के लिए महत्वपूर्ण है।

Android 17 में नया public API आया है:

```kotlin
android.app.voiceinteraction.VoiceInteractionManager
```

इसका इस्तेमाल यह पता लगाने के लिए किया जाता है कि current assistant को screen context पढ़ने की अनुमति है या नहीं।

इसके तीन states हैं:

| Constant                                        | Meaning                             |
| ----------------------------------------------- | ----------------------------------- |
| READ_SCREEN_CONTEXT_REQUEST_STATE_GRANTED       | Access मिल चुका है                  |
| READ_SCREEN_CONTEXT_REQUEST_STATE_REQUESTABLE   | User से permission मांगी जा सकती है |
| READ_SCREEN_CONTEXT_REQUEST_STATE_UNREQUESTABLE | Access request नहीं की जा सकती      |

इस API के तीन important methods हैं:

```kotlin
canReadScreenContext()
getReadScreenContextRequestState()
createRequestReadScreenContextIntent()
```

Android Developers +1

### Screen context access check करना

```kotlin
import android.app.voiceinteraction.VoiceInteractionManager
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(37)
fun canReadScreenContext(): Boolean {

    val manager = getSystemService(
        VoiceInteractionManager::class.java
    )

    return manager?.canReadScreenContext() == true
}
```

### User से access request करना

```kotlin
import android.app.voiceinteraction.VoiceInteractionManager
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(37)
private val screenContextLauncher =
    registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Check access again after user's decision.
        val granted = canReadScreenContext()
    }

@RequiresApi(37)
private fun requestScreenContextAccess() {

    val manager = getSystemService(
        VoiceInteractionManager::class.java
    ) ?: return

    when (manager.readScreenContextRequestState) {

        VoiceInteractionManager
            .READ_SCREEN_CONTEXT_REQUEST_STATE_GRANTED -> {
            // Already granted.
        }

        VoiceInteractionManager
            .READ_SCREEN_CONTEXT_REQUEST_STATE_REQUESTABLE -> {

            screenContextLauncher.launch(
                manager.createRequestReadScreenContextIntent()
            )
        }

        VoiceInteractionManager
            .READ_SCREEN_CONTEXT_REQUEST_STATE_UNREQUESTABLE -> {
            // Access cannot currently be requested.
        }
    }
}
```

यह screen context permission और Default Assistant role दो अलग चीजें हैं।

पहले user को तुम्हारा app default assistant बनाना होगा। उसके बाद ही यह screen context access request की जा सकती है। System user को अलग से अनुमति मांगने वाला dialog दिखाता है। Android Developers

### 8\. Android 10 से Android 17 compatibility

यहाँ सभी APIs की minimum versions देखो:

| API / Feature                       | Minimum Android |
| ----------------------------------- | --------------- |
| Intent.ACTION_ASSIST                | API 16          |
| VoiceInteractionService             | API 21          |
| VoiceInteractionSession             | API 21          |
| RoleManager                         | API 29          |
| ROLE_ASSISTANT                      | API 29          |
| isRoleHeld()                        | API 29          |
| VoiceInteractionSession.AssistState | API 29          |
| VoiceInteractionManager             | API 37          |
| Screen Context Access               | API 37          |

Android Developers +4

तुम्हारे project के लिए configuration:

```kotlin
android {
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        targetSdk = 37
    }
}
```

AGP 9.5.0 और Gradle 9.8.1 इस feature के implementation को नहीं बदलते। ये build tooling हैं, Assistant APIs Android Framework का हिस्सा हैं।

### 9\. Testing कैसे करना है?

मेरी recommendation है कि इन tests को क्रम से करो।

Test 1: App eligibility

* `ACTION_ASSIST` Activity manifest में registered है।

* App install करो।

* Role request UI open करो।

* देखो कि app Assistant selection में available है या नहीं।

Test 2: Role selection

* अपने app को default assistant बनाओ।

* `isRoleHeld(ROLE_ASSISTANT)` check करो।

* App को force stop करके reopen करने के बाद भी status check करो।

* Settings से किसी दूसरे assistant को select करके देखो कि तुम्हारे app का status `false` हो जाता है।

Test 3: Assist invocation

* App को default assistant बनाओ।

* System के configured assistant invocation gesture से open करो।

* `AssistEntryActivity` में `ACTION_ASSIST` check करो।

* Foreground package extra उपलब्ध है या नहीं, verify करो।

Test 4: Full voice interaction

* VoiceInteractionService का active status check करो।

* Session creation verify करो।

* `onHandleAssist()` और `onHandleScreenshot()` callbacks test करो।

* Assist data unavailable होने की स्थिति भी test करो।

Test 5: Android 17

* Role selection और screen context access अलग-अलग test करो।

* Granted, requestable और unrequestable states verify करो।

तुम्हारे Moto G73 के बताए Android 14 setup पर पहले चार basic integration tests के relevant हिस्से test कर सकते हो। API 37 के नए screen context permission को runtime में test करने के लिए Android 17 device या emulator चाहिए।

### 10\. मेरी final recommendation

मैं तुम्हारे project में इसे इस तरह organize करूंगा:

```text
assistant/
├── AssistEntryActivity.kt
├── AssistantRoleChecker.kt
│
├── voice/
│   ├── AppVoiceInteractionService.kt
│   ├── AppVoiceInteractionSessionService.kt
│   ├── AppVoiceInteractionSession.kt
│   └── AppRecognitionService.kt
│
└── screencontext/
    └── ScreenContextManager.kt
```

इसमें:

| Component                         | Responsibility                           |
| --------------------------------- | ---------------------------------------- |
| AssistEntryActivity               | ACTION_ASSIST handle करना                |
| AssistantRoleChecker              | Default assistant status और role request |
| AppVoiceInteractionService        | System voice interaction lifecycle       |
| AppVoiceInteractionSessionService | Sessions create करना                     |
| AppVoiceInteractionSession        | Assistant UI और assist data              |
| AppRecognitionService             | Speech recognition                       |
| ScreenContextManager              | Android 17 screen context access         |

तुम शुरुआत में सिर्फ ये दो files और manifest वाला implementation रख सकते हो:

```text
assistant/
├── AssistEntryActivity.kt
└── AssistantRoleChecker.kt
```

बाद में जरूरत पड़ने पर voice और screen context modules जोड़ देना।

### Official documentation

इन references को bookmark कर लो:

* [RoleManager API](https://developer.android.com/reference/android/app/role/RoleManager?utm_source=chatgpt.com)

* [Intent.ACTION_ASSIST](https://developer.android.com/reference/android/content/Intent?utm_source=chatgpt.com#ACTION_ASSIST)

* [VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService?utm_source=chatgpt.com)

* [VoiceInteractionSession](https://developer.android.com/reference/android/service/voice/VoiceInteractionSession?utm_source=chatgpt.com)

* [VoiceInteractionManager (API 37)](https://developer.android.com/reference/android/app/voiceinteraction/VoiceInteractionManager?utm_source=chatgpt.com)

* [Android Role Requirements (AOSP)](https://source.android.com/docs/core/permissions/android-roles?utm_source=chatgpt.com)

एक आखिरी बात: `ACTION_ASSIST` implement करने से app selectable default assistant बन सकता है, लेकिन इससे app Google Assistant जैसी speech recognition, hotword detection या full screen understanding capabilities अपने आप हासिल नहीं कर लेता। वे अलग implementations और user permissions हैं।

तुम्हारे वर्तमान उद्देश्य के लिए `ACTION_ASSIST + RoleManager + AssistantRoleChecker` सबसे सही starting implementation है।
