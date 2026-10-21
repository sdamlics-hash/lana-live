"""Make a separate Android V3.4 preview from the original, untouched V3.3 ZIP.

This preview checks only GET /v1/status. It never records the token, submits
jobs, streams screenshots via the new bridge, or changes the installed LANA.
"""
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET
import zipfile


def replace_once(content, anchor, value, label):
    if content.count(anchor) != 1:
        raise ValueError(f"Unexpected V3.3 layout: {label} anchor count={content.count(anchor)}")
    return content.replace(anchor, value, 1)


def patch_archive(zip_path, destination, client_source):
    if destination.exists():
        raise ValueError("Destination already exists; no overwrite permitted")
    with zipfile.ZipFile(zip_path) as archive:
        for entry in archive.infolist():
            if entry.filename.startswith("/") or ".." in Path(entry.filename).parts:
                raise ValueError("Unsafe ZIP member name")
        archive.extractall(destination)

    app = destination / "app" / "src" / "main"
    activity = app / "java" / "ai" / "dreibus" / "lanalive" / "MainActivity.kt"
    layout = app / "res" / "layout" / "activity_main.xml"
    gradle = destination / "app" / "build.gradle.kts"
    manifest = app / "AndroidManifest.xml"

    main = activity.read_text(encoding="utf-8")
    init_anchor = "        status = findViewById(R.id.statusText)"
    init_new = """        status = findViewById(R.id.statusText)
        findViewById<Button>(R.id.coreTestButton).setOnClickListener {
            val address = findViewById<EditText>(R.id.coreServerUrl).text.toString()
            val secretField = findViewById<EditText>(R.id.coreToken)
            val secret = secretField.text.toString()
            secretField.text.clear() // No saved key, only an in-memory request.
            status.text = "Проверяю связь с ядром Ланы…"
            Thread {
                val answer = try {
                    LanaCoreClient.checkStatus(address, secret)
                } catch (_: Exception) {
                    "Связь не установлена. Проверьте HTTPS-адрес и доступ к серверу."
                }
                runOnUiThread { if (!isFinishing && !isDestroyed) status.text = answer }
            }.start()
        }"""
    activity.write_text(replace_once(main, init_anchor, init_new, "activity"), encoding="utf-8")

    ui = layout.read_text(encoding="utf-8")
    ui_extra = """        <TextView android:layout_width="match_parent" android:layout_height="wrap_content"
            android:text="ЯДРО ЛАНЫ — ПРОВЕРКА СВЯЗИ" android:textColor="#67FF87"
            android:layout_marginTop="20dp" />
        <EditText android:id="@+id/coreServerUrl" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:hint="https://адрес-защищённого-сервера"
            android:inputType="textUri" android:singleLine="true" />
        <EditText android:id="@+id/coreToken" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:hint="Временный ключ доступа"
            android:inputType="textPassword" android:importantForAutofill="no"
            android:singleLine="true" android:saveEnabled="false" />
        <Button android:id="@+id/coreTestButton" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:text="ПРОВЕРИТЬ СВЯЗЬ С ЯДРОМ" />
"""
    ui = replace_once(ui, "    </LinearLayout>", ui_extra + "    </LinearLayout>", "layout")
    ET.fromstring(ui)
    layout.write_text(ui, encoding="utf-8")
    shutil.copyfile(client_source, activity.with_name("LanaCoreClient.kt"))

    kts = gradle.read_text(encoding="utf-8")
    kts = replace_once(kts, 'applicationId = "ai.dreibus.lanalive"',
                       'applicationId = "ai.dreibus.lanalive.bridgepreview"', "applicationId")
    kts = replace_once(kts, "compileSdk = 35",
                       'compileSdk = 35\n    kotlinOptions { jvmTarget = "1.8" }', "gradle")
    gradle.write_text(kts, encoding="utf-8")
    (destination / "gradle.properties").write_text("android.useAndroidX=true\n", encoding="utf-8")

    xml = manifest.read_text(encoding="utf-8")
    xml = replace_once(xml, 'android:label="LANA LIVE"',
                       'android:label="LANA BRIDGE PREVIEW"', "manifest label")
    ET.fromstring(xml)
    manifest.write_text(xml, encoding="utf-8")
    return destination


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: python android_bridge/patch_v34.py <V3.3-source.zip> <new-output-dir>")
    root = Path(__file__).resolve().parent
    patch_archive(Path(sys.argv[1]), Path(sys.argv[2]), root / "src" / "LanaCoreClient.kt")
    print("LANA Bridge V3.4 preview sources prepared; installed app unchanged")
