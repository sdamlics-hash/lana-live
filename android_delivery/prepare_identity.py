from pathlib import Path
import re, sys
import xml.etree.ElementTree as ET
root=Path(sys.argv[1])
version=int(sys.argv[2])
assert version in (1051,1052,1053,1054,1055,1056,1057,1058,1059,1060,1061,1062,1063,1064,1065,1066,1067,1068)
p=root/'app/build.gradle.kts'; source=p.read_text()
source,n=re.subn(r'versionCode\s*=\s*\d+',f'versionCode = {version}',source)
assert n==1
pattern=r'(applicationId\s*=\s*")([^"]+)(")'
matches=list(re.finditer(pattern,source))
assert len(matches)==1
original=matches[0].group(2)
target='ai.dreibus.lanalive.stablevoice'
p.write_text(re.sub(pattern,lambda m:m.group(1)+target+m.group(3),source))
manifest=root/'app/src/main/AndroidManifest.xml'
a='{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android','http://schemas.android.com/apk/res/android')
ET.register_namespace('tools','http://schemas.android.com/tools')
tree=ET.parse(manifest); app=tree.getroot().find('application');assert app is not None
app.set(a+'label','LANA')
for node in tree.iter():
    authority=node.get(a+'authorities')
    if authority:
        assert '${applicationId}' in authority or original in authority
        node.set(a+'authorities',authority.replace(original,target))
tree.write(manifest,encoding='unicode')
print('Same package; candidate version',version)
