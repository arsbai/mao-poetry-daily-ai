"""Build a signed, installable test APK using official Android tools, without Gradle downloads."""
import argparse, os, pathlib, shutil, subprocess, zipfile, sys
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
ROOT=pathlib.Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--jdk',required=True);p.add_argument('--tools',required=True);p.add_argument('--android-jar',required=True);p.add_argument('--output',default=str(ROOT/'build'/'XiangDong-1.1.apk'));a=p.parse_args()
jdk=pathlib.Path(a.jdk).resolve();tools=pathlib.Path(a.tools).resolve();android=pathlib.Path(a.android_jar).resolve();build=ROOT/'build';build.mkdir(exist_ok=True)
for name in ('classes','generated','dex'):
    directory=(build/name).resolve()
    if directory.parent!=build.resolve(): raise SystemExit('Invalid build directory')
    if directory.exists(): shutil.rmtree(directory)
    directory.mkdir()
env=os.environ.copy();env['JAVA_HOME']=str(jdk);env['PATH']=str(jdk/'bin')+os.pathsep+env.get('PATH','')
def run(*args):
    result=subprocess.run([str(x) for x in args],env=env,check=False,capture_output=True,text=True,encoding='utf-8',errors='replace')
    if result.stdout.strip(): print(result.stdout.strip())
    if result.stderr.strip(): print(result.stderr.strip())
    if result.returncode: raise SystemExit(result.returncode)
run(tools/'aapt2.exe','compile','--dir',ROOT/'android/app/src/main/res','-o',build/'resources.zip')
run(tools/'aapt2.exe','link','-I',android,'--manifest',ROOT/'android/app/src/main/AndroidManifest.xml','--min-sdk-version','26','--target-sdk-version','35','--version-code','2','--version-name','1.1','--java',build/'generated','-A',ROOT/'web','-o',build/'unsigned.apk',build/'resources.zip')
sources=list((ROOT/'android/app/src/main/java').rglob('*.java'))+list((build/'generated').rglob('*.java'))
run(jdk/'bin/javac.exe','-J-Duser.language=en','-J-Duser.country=US','-encoding','UTF-8','-source','8','-target','8','-classpath',android,'-d',build/'classes',*sources)
run(jdk/'bin/jar.exe','cf',build/'classes.jar','-C',build/'classes','.')
run(jdk/'bin/java.exe','-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--min-api','26','--output',build/'dex',build/'classes.jar')
with zipfile.ZipFile(build/'unsigned.apk','a') as z:
    for f in (build/'dex').glob('classes*.dex'): z.write(f,f.name)
run(tools/'zipalign.exe','-f','4',build/'unsigned.apk',build/'aligned.apk')
keystore=build/'test-signing-xiangdong.jks'
if not keystore.exists(): run(jdk/'bin/keytool.exe','-genkeypair','-keystore',keystore,'-storepass','android','-keypass','android','-alias','xiangdong-test','-dname','CN=XiangDong Test, O=Local Development, C=CN','-keyalg','RSA','-keysize','2048','-validity','3650')
out=pathlib.Path(a.output);out.parent.mkdir(exist_ok=True,parents=True)
run(jdk/'bin/java.exe','-jar',tools/'lib/apksigner.jar','sign','--ks',keystore,'--ks-key-alias','xiangdong-test','--ks-pass','pass:android','--key-pass','pass:android','--out',out,build/'aligned.apk')
run(jdk/'bin/java.exe','-jar',tools/'lib/apksigner.jar','verify','--verbose',out)
run(tools/'aapt2.exe','dump','badging',out)
print('APK:',out,'bytes:',out.stat().st_size)
