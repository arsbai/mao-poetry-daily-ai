"""After build_apk.py: compare real Java widget selection against the JS homepage,
and exercise contain geometry. org.json is a host-test-only Maven dependency.
"""
import argparse,os,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--jdk',required=True);p.add_argument('--android-jar',required=True);p.add_argument('--json-jar',required=True);a=p.parse_args()
java=Path(a.jdk).resolve()/'bin';output=root/'build/native-tests';output.mkdir(parents=True,exist_ok=True)
fixture=output/'daily-fixture.json'
subprocess.run(['node',str(root/'tests/daily_fixture.cjs'),str(root/'web/corpus.json'),str(fixture)],check=True)
classpath=os.pathsep.join([str(Path(a.json_jar).resolve()),str(root/'build/classes'),str(Path(a.android_jar).resolve())])
subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-cp',classpath,'-d',str(output),str(root/'tests/NativePresentationTest.java')],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(output)+os.pathsep+classpath,'cn.xiangdong.poetry.NativePresentationTest',str(root/'web/corpus.json'),str(fixture)],check=True)
