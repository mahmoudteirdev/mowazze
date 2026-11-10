<p align="center">
  <img src="docs/readme/header.svg" alt="Mowazze - موزّع" width="100%" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android Version" />
  <img src="https://img.shields.io/badge/Kotlin-2.0.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-1.7-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/Shizuku-API%20v23-2B579A?style=for-the-badge" alt="Shizuku" />
  <img src="https://img.shields.io/badge/Root-Magisk%20%7C%20KernelSU%20%7C%20APatch-E53935?style=for-the-badge" alt="Root" />
  <img src="https://img.shields.io/badge/Tests-Passing%20(100%25)-00C853?style=for-the-badge" alt="Tests" />
  <img src="https://img.shields.io/badge/Language-Arabic%20RTL%20%7C%20English-0D9488?style=for-the-badge" alt="Bilingual" />
</p>

---

## 📖 نظرة عامة (Overview)

**موزّع (Mowazze)** هو تطبيق أندرويد متطور واحترافي لمشاركة الإنترنت (Wi-Fi Hotspot & Repeater) مبني وفق أحدث معايير الأداء والتصميم الحديث **Jetpack Compose (Material 3)**.

تم تصميم التطبيق ليعمل عبر **ثلاث مستويات مختلفة من الصلاحيات (No-Root / Shizuku / Root)**، مما يمنح المستخدم أقصى درجات المرونة للتحكم في شبكته، وتجاوز قيود الشركات المصنّعة (مثل أجهزة سامسونج التي تفصل شبكة الواي فاي عند تشغيل الهوتسبوت)، مع توفير حزمة أدوات روت خارقة تشمل تخطي حظر الـ TTL، مشاركة الـ VPN، ومنع الإعلانات وتغيير عنوان الماك (MAC Address).

---

## ✨ المميزات الرئيسية (Key Features)

### 1. مستويات التشغيل الثلاثة (3 Permission Levels)
* **بدون روت (No-Root Repeater):**
  * مشاركة إنترنت الواي فاي عبر تقنية **Wi-Fi Direct** دون الحاجة لإطفاء شبكة الواي فاي الأصلية المتصل بها.
  * خادم بروكسي مدمج خفيف فائق السرعة مبني بمكتبة **Kotlin Coroutines NIO** على المنفذ `8245`.
  * تزويد الأجهزة المتصلة بملف الإعداد التلقائي `http://192.168.49.1:8245/proxy.pac`.
* **عبر Shizuku (Native Hotspot Control):**
  * التحكم بنقطة الاتصال الرسمية للنظام بدون تعديل النظام أو الحاجة للروت عبر واجهات `TetheringManager` و `Shizuku API v23`.
  * دعم التبديل التلقائي الذكي بين الهوتسبوت والواي فاي دايركت في محرك `AdaptiveShareEngine`.
* **أدوات الروت الخارقة (Advanced Root Suite):**
  * **تخطي حظر الهوتسبوت (TTL Bypass):** تثبيت الـ TTL على `64` (أندرويد/لينكس)، `65` (ويندوز)، أو `128` لإخفاء استهلاك نقطة الاتصال عن شركات الاتصالات وحزم IPv6 (`HL`).
  * **مشاركة اتصال الـ VPN (VPN Hotspot Sharing):** توجيه حركة مرور الأجهزة المتصلة عبر واجهة `tun+` والـ NAT فورياً.
  * **حظر إعلانات الشبكة (Network-wide AdBlock):** إعادة توجيه استعلامات DNS لمانع الإعلانات AdGuard DNS لحماية كافة الأجهزة المتصلة بدون تطبيقات إضافية.
  * **تغيير وتزييف الـ MAC (MAC Address Changer):** استخراج وتزييف وتوليد عناوين MAC عشوائية لبطاقة الواي فاي `wlan0` مع زر استعادة العنوان الأصلي.
  * **إسقاط حزم الأجهزة على مستوى النواة (Kernel MAC Dropping):** حظر الأجهزة المتطفلة مباشرة عبر جداول `iptables` في النواة.

---

### 2. إدارة الأجهزة واستهلاك البيانات (Devices & Bandwidth Control)
* **كشف الأجهزة المتصلة لحظياً:** عرض تفاصيل الأجهزة المتصلة مع نوع الجهاز (هاتف، لابتوب، جهاز لوحي).
* **سقف السرعة لكل جهاز (Speed Limiting):** تحديد سقف استهلاك السرعة لكل متصل (1، 2، 5، 10 ميجابت أو بلا حدود).
* **الحظر الفوري (Instant Blocking & Undo):** حظر فوري لأي متصل مع إمكانية التراجع الفوري عبر إشعار Snackbar.
* **الحد الأقصى للمتصلين (Max Devices):** تحديد سقف عدد الأجهزة المسموح باتصالها (1، 2، 3، 5، 8، 10 أو بلا حدود).
* **باقة البيانات اليومية وتنبيه 80% (Daily Quota):**
  * تحديد باقة استهلاك يومية (1، 2، 5، 10 جيجابايت أو بلا حد).
  * شريط تقدم مرئي في بطاقة الاستهلاك اليومي.
  * إشعار وتنبيه تلقائي عند وصول الاستهلاك إلى 80%.

---

### 3. سرعة الاتصال وتجربة المستخدم (Fast Connect & UX)
* **رمز QR فوري بملء الشاشة:** نافذة منبثقة فائقة السطوع لعرض رمز الاستجابة السريعة (QR Code) لتسهيل مسح الكود من الهواتف الأخرى واللابتوبات.
* **تعليمات البروكسي للأجهزة:** بطاقة إرشادية مخصصة في الشاشة الرئيسية توضح بدقة عنوان البروكسي (`192.168.49.1:8245`) وزر النسخ بنقرة واحدة.
* **تصميم عربي أصيل (RTL Native):** دعم اللغة العربية كخيار رئيسي أول بأرقام لاتينية مقروءة وخط `IBM Plex Sans Arabic`، مع دعم كامل للغة الإنجليزية.
* **دعم الأجهزة اللوحية (Tablet Dual-Pane):** واجهة تتكيف تلقائياً مع الشاشات العريضة عبر نظام `NavigationRail` وعرض الشاشتين جنباً إلى جنب.
* **الوضع الداكن والفاتح (Dark & Light Theme):** نظام ألوان متكامل متوافق مع مبادئ Material 3 وتباين وصول مريح للعين (Accessibility).

---

## 📊 مقارنة مستويات الصلاحيات (Capabilities Matrix)

| الميزة / Capability | بدون روت (No-Root) | عبر Shizuku | بالروت (Root) |
|---|:---:|:---:|:---:|
| **مشاركة نت الواي فاي (Repeater)** |  (عبر Wi-Fi Direct) |  (إذا كان الجهاز يدعم ذلك) |  (بشكل كامل) |
| **إنشاء هوتسبوت نظام رسمي** | ❌ (يتطلب إعداد يدوي) |  (مباشر وتلقائي) |  (مباشر وتلقائي) |
| **قراءة الأجهزة المتصلة** |  (عبر ARP & P2P) |  (TetheringManager) |  (نواة لينكس) |
| **تحديد سرعة المتصلين** | ❌ (يحتاج روت/شيزوكو) |  (Traffic Shaping) |  (Traffic Shaping) |
| **حظر الأجهزة** | ❌ (إشعار طلب الصلاحية) |  (قائمة الحظر) |  (إسقاط فوري بالنواة) |
| **تخطي قيود الـ TTL** | ❌ | ❌ |  (iptables & ip6tables) |
| **مشاركة إنترنت الـ VPN** | ❌ | ❌ |  (iptables NAT Masquerade) |
| **مانع الإعلانات الشامل** | ❌ | ❌ |  (DNS Redirection) |
| **تغيير عنوان الـ MAC** | ❌ | ❌ |  (ip link wlan0) |

---

## 🏗️ الهيكل البرمجي (Architecture)

تم بناء المشروع باتباع أحدث أنماط هندسة برمجيات أندرويد الحديثة (Clean Architecture + MVI/Unidirectional Data Flow):

```text
com.mowazze3
├── data/
│   ├── proxy/               # خادم البروكسي المحلي المدمج (MowazzeProxyServer)
│   ├── root/                # مدير شبكة الروت وتنفيذ أوامر iptables و MAC (RootNetworkManager)
│   ├── AdaptiveShareEngine  # المحرك المتكيف الذكي (التبديل بين Shizuku و Wi-Fi Direct)
│   ├── WifiDirectShareEngine# محرك الـ Wi-Fi Direct P2P
│   ├── ShizukuShareEngine   # محرك الهوتسبوت عبر واجهة Shizuku
│   ├── DataStoreSettings    # حفظ وتخزين الإعدادات عبر Jetpack DataStore
│   └── ShareRepository      # المصدر الموحد للبيانات وحالة المشاركة (Single Source of Truth)
├── domain/                  # النماذج، منطق الأعمال، وحساب الاستهلاك والتحقق
├── service/                 # الخدمة الأمامية (ShareService) للعمل في الخلفية
└── ui/
    ├── home/                # الشاشة الرئيسية، كود الـ QR، وتعليمات البروكسي
    ├── devices/             # شاشة المتصلين، الاستهلاك، ونوافذ السرعة والحظر
    ├── settings/            # الإعدادات، محدد الباقة، وشيت أدوات الروت المتقدمة (RootToolsSheet)
    ├── onboarding/          # معالج الإعداد وفحص الصلاحيات وربط Shizuku
    └── theme/               # توكنز الألوان، الخطوط، والمسافات
```

---

## 🛠️ متطلبات البناء والتطوير (Building & Setup)

1. **Android Studio:** الإصدار الحديث (Hedgehog أو أحدث).
2. **Java Development Kit (JDK):** يتطلب Java 17+ (يوصى باستخدام JBR الخاص بـ Android Studio):
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   ```
3. **تشغيل كافة اختبارات الوحدة:**
   ```powershell
   .\gradlew test
   ```
4. **بناء حزمة APK التجريبية (Debug APK):**
   ```powershell
   .\gradlew assembleDebug
   ```
5. **التثبيت على الهاتف عبر ADB:**
   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

---

## 📄 الترخيص (License)

هذا المشروع منشور تحت رخصة **Apache License 2.0**. تفاصيل الخطوط وتراخيص خط `IBM Plex Sans Arabic` مضمنة تحت ترخيص **SIL Open Font License (OFL 1.1)**.

---

<p align="center">
  صنع بإتقان وشغف لتقديم أفضل تجربة مشاركة إنترنت حرة على أندرويد 🤍
</p>
