#!/usr/bin/env python3
"""Small Linux ROI-pack experiment. Host evidence only, never an Android performance gate."""
import argparse
import ctypes
import datetime
import hashlib
import json
import os
import pathlib
import platform
import shutil
import statistics
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent
CXX_FLAGS = ['-O3', '-DNDEBUG', '-std=c++17', '-march=x86-64']
RUST_FLAGS = ['--edition=2021', '--crate-type=cdylib', '-C', 'opt-level=3',
              '-C', 'codegen-units=1', '-C', 'panic=abort', '-C', 'target-cpu=x86-64']
BACKENDS = ['cpp', 'rust', 'java', 'jni_cpp', 'jni_rust']


def run(command, **kwargs):
    return subprocess.run([str(x) for x in command], check=True, text=True, capture_output=True, **kwargs).stdout


def build(directory, include):
    directory.mkdir(parents=True, exist_ok=True)
    cxx = os.environ.get('CXX', 'clang++')
    rustc = os.environ.get('RUSTC', 'rustc')
    if platform.machine() not in ('x86_64', 'AMD64'):
        raise SystemExit('This first driver is explicitly x86_64 Linux; add a separately recorded ARM target before running there')
    if sys.platform != 'linux':
        raise SystemExit('This first driver uses Linux JNI headers and dlopen')
    run([cxx, *CXX_FLAGS, '-fPIC', '-shared', ROOT/'roi.cpp', '-o', directory/'libroi_cpp.so'])
    run([rustc, *RUST_FLAGS, ROOT/'roi.rs', '-o', directory/'libroi_rust.so'])
    run([cxx, *CXX_FLAGS, ROOT/'driver.cpp', '-ldl', '-o', directory/'driver'])
    run([cxx, *CXX_FLAGS, '-fPIC', '-shared', '-I'+str(include), '-I'+str(include/'linux'),
         ROOT/'jni.cpp', '-ldl', '-o', directory/'libroi_jni.so'])
    run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', directory, ROOT/'RoiPackExperiment.java'])
    rust_version = run([rustc, '-vV']).strip()
    cpp_version = run([cxx, '--version']).splitlines()[0]
    java_version = subprocess.run(['java', '-version'], text=True, capture_output=True, check=True).stderr.strip()
    return {'cpp': cpp_version, 'rust': rust_version, 'java': java_version,
            'cpp_flags': CXX_FLAGS, 'rust_flags': RUST_FLAGS,
            'java_flags': ['-Xms128m', '-Xmx128m', '-XX:CompileCommand=dontinline,RoiPackExperiment::packJava'],
            'jni_array_access': 'GetByteArrayElements + ReleaseByteArrayElements(mode=0); VM may copy'}


def fixtures(directory):
    folder = directory/'fixtures'
    folder.mkdir(exist_ok=True)
    cases = []
    lines = []
    for label, fw, fh in [('VGA',640,480),('HD',1280,720),('FHD',1920,1080)]:
        w,h = fw*5//8, fh*5//8
        left,top = (fw-w)//2, (fh-h)//2
        expected = bytes(((left+x)*13+(top+y)*7+17)&255 for y in range(h) for x in range(w))
        expected_path = folder/f'{label}.expected'
        expected_path.write_bytes(expected)
        for layout,pixel,padding in [('packed',1,0),('padded',1,32),('pixel2',2,32)]:
            row = fw*pixel+padding
            source = bytearray([0xA5])*(row*fh)
            for y in range(fh):
                start=y*row
                for x in range(fw):
                    source[start+x*pixel]=(x*13+y*7+17)&255
            name = f'{label}_{layout}'
            source_path = folder/f'{name}.bin'
            source_path.write_bytes(source)
            cases.append({'case': name, 'frame_width':fw, 'frame_height':fh, 'width':w, 'height':h,
                'left':left, 'top':top, 'row_stride':row, 'pixel_stride':pixel,
                'input_sha256':hashlib.sha256(source).hexdigest(),
                'expected_sha256':hashlib.sha256(expected).hexdigest(), 'output_bytes':len(expected)})
            lines.append(f'{name} {source_path} {expected_path} {w} {h} {row} {pixel} {left} {top}')
    manifest = directory/'fixtures.tsv'
    manifest.write_text('\n'.join(lines)+'\n')
    return cases,manifest


def check_libraries(directory, cases):
    """Exact outputs, untouched source, output guards, nulls and adversarial geometry."""
    count = 0
    for backend in ('cpp','rust'):
        library = ctypes.CDLL(str(directory/f'libroi_{backend}.so'))
        pack = library.roi_pack
        pack.argtypes = [ctypes.c_void_p,ctypes.c_size_t,ctypes.c_void_p,ctypes.c_size_t]+[ctypes.c_size_t]*6
        pack.restype = ctypes.c_int
        for case in cases:
            raw = (directory/'fixtures'/f'{case["case"]}.bin').read_bytes()
            expected = (directory/'fixtures'/f'{case["case"].split("_")[0]}.expected').read_bytes()
            src = (ctypes.c_ubyte*len(raw)).from_buffer_copy(raw)
            guarded = (ctypes.c_ubyte*(len(expected)+32))(*([0xCD]*(len(expected)+32)))
            dst = ctypes.cast(ctypes.byref(guarded,16), ctypes.c_void_p)
            geometry = [case[k] for k in ('width','height','row_stride','pixel_stride','left','top')]
            assert pack(src,len(raw),dst,len(expected),*geometry)==0
            assert bytes(guarded)[16:-16]==expected
            assert bytes(guarded)[:16]==b'\xcd'*16 and bytes(guarded)[-16:]==b'\xcd'*16
            assert bytes(src)==raw
            # Every rejected call must leave all destination bytes unchanged.
            snapshot=bytes(guarded)
            malformed=[(len(raw),len(expected),[0,*geometry[1:]]),
                       (len(raw),len(expected)-1,geometry),
                       (len(raw)-case['row_stride']*case['frame_height'],len(expected),geometry),
                       (len(raw),len(expected),[geometry[0],geometry[1],1,*geometry[3:]]),
                       (len(raw),len(expected),[*geometry[:3],0,*geometry[4:]]),
                       (len(raw),len(expected),[*geometry[:4],ctypes.c_size_t(-1).value,geometry[5]]),
                       (len(raw),len(expected),[*geometry[:5],ctypes.c_size_t(-1).value])]
            for src_len,dst_len,g in malformed:
                assert pack(src,src_len,dst,dst_len,*g)==-1
                assert bytes(guarded)==snapshot
                count+=1
            assert pack(None,len(raw),dst,len(expected),*geometry)==-1
            assert pack(src,len(raw),None,len(expected),*geometry)==-1
            assert bytes(guarded)==snapshot
            count+=3
    return count


def collect(directory, manifest, backend, args):
    if backend in ('cpp','rust'):
        command=[directory/'driver',directory/f'libroi_{backend}.so',manifest,
                 args.samples,args.warmup_ms,args.batch_ms]
    else:
        native=backend.removeprefix('jni_') if backend!='java' else 'cpp'
        command=['java','-Xms128m','-Xmx128m',
                 '-XX:CompileCommand=dontinline,RoiPackExperiment::packJava', '-cp', directory,
                 'RoiPackExperiment',backend,directory/'libroi_jni.so',directory/f'libroi_{native}.so',
                 manifest,args.samples,args.warmup_ms,args.batch_ms]
    result = run(command)
    values=[json.loads(line) for line in result.splitlines() if line.startswith('{')]
    if len(values)!=9 or len({v['case'] for v in values})!=9:
        raise RuntimeError(f'{backend}: incomplete matrix')
    return values


def metadata(versions):
    cpu='unknown'
    info=pathlib.Path('/proc/cpuinfo')
    if info.exists():
        cpu=next((s.split(':',1)[1].strip() for s in info.read_text().splitlines() if s.startswith('model name')),cpu)
    source_hashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in ROOT.iterdir()
                   if p.suffix in ('.py','.cpp','.rs','.h','.java')}
    return {'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'scope':'shared x86_64 Linux host; not Android, no HAL/camera/decoder; no clock/thermal control',
            'machine':platform.machine(), 'os':platform.platform(), 'cpu_model':cpu,
            'affinity':sorted(os.sched_getaffinity(0)) if hasattr(os,'sched_getaffinity') else None,
            'base_commit':run(['git','-C',ROOT,'rev-parse','HEAD']).strip(),
            'versions':versions,'source_sha256':source_hashes}


def summary(report):
    rows=[]
    for case in report['fixtures']:
        name=case['case']
        row={'case':name}
        for backend in BACKENDS:
            medians=[statistics.median(item['ns_per_copy']) for run_ in report['runs']
                     if run_['backend']==backend for item in run_['cases'] if item['case']==name]
            row[backend]={'median_ns':statistics.median(medians),
                          'process_medians_ns':medians,'min_process_median_ns':min(medians),
                          'max_process_median_ns':max(medians)}
        rows.append(row)
    return rows


def markdown(report):
    text=['# ROI packing language experiment / ROI 打包语言对照实验','',
          'Host-only exploratory data; **not an Android speedup claim**. / 仅为宿主机探索数据，**不代表 Android 提速**。','',
          f"CPU: {report['environment']['cpu_model']}; {report['environment']['machine']}.",
          f"{report['protocol']['rounds']} fresh processes per backend; {report['protocol']['samples']} batch samples per case/process.",
          'Cells show the median of process medians, in microseconds per copy. / 单元格为各进程中位数的中位数，单位微秒/次。','',
          '## Native call, without JNI / 不含 JNI 的原生调用','',
          '| Case | C++ µs | Rust µs | Rust / C++ |','| --- | ---: | ---: | ---: |']
    for row in report['summary']:
        cpp=row['cpp']['median_ns']; rust=row['rust']['median_ns']
        text.append(f"| {row['case']} | {cpp/1000:.3f} | {rust/1000:.3f} | {rust/cpp:.3f}× |")
    text+=['','## Java API call, including JNI where used / Java API 调用，包含相应 JNI 开销','',
           '| Case | Java µs | JNI → C++ µs | JNI → Rust µs |','| --- | ---: | ---: | ---: |']
    for row in report['summary']:
        text.append(f"| {row['case']} | {row['java']['median_ns']/1000:.3f} | {row['jni_cpp']['median_ns']/1000:.3f} | {row['jni_rust']['median_ns']/1000:.3f} |")
    text+=['','## Limits / 局限','',
      '- Input, expected bytes and source hashes are recorded in JSON; output is checked before timing and after every batch. / JSON 记录输入、期望输出及源代码哈希；计时前及每批后校验输出。',
      '- Native rows include a shared C ABI dispatch; Java rows include direct-buffer-to-byte-array copying. Both JNI implementations use the identical bridge and array access, which may copy. / 原生表包含相同 C ABI 分派；Java 表为 direct buffer 到 byte[] 的复制；两种 JNI 实现共用桥接及可能复制的数组访问。',
      '- Java runs on HotSpot, not ART. Rust safe indexing and C++ restrict pointers express the same nonoverlap contract but need not optimize identically. / Java 使用 HotSpot 而非 ART；Rust 安全索引与 C++ restrict 表达相同不重叠约定，但优化结果可能不同。',
      '- Shared-host frequency, scheduling and temperature are uncontrolled. Process order is rotated; raw samples and process medians are included. No statistical significance or language-wide ranking is claimed. / 共享宿主的频率、调度、温度不可控；轮换进程顺序并保留原始样本与进程中位数，不宣称统计显著性或语言整体排名。',
      '- This kernel is ROI packing only. The existing direct native Y-plane path can bypass packing. Full decoder latency, correctness, battery and ARM32/ARM64 remain unmeasured. / 本实验仅测 ROI 打包；现有直接 native Y 平面路径可绕过此操作，完整解码、识别率、功耗及 ARM32/ARM64 尚未测量。','']
    return '\n'.join(text)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify-only',action='store_true')
    parser.add_argument('--out',type=pathlib.Path,default=ROOT/'build/result.json')
    parser.add_argument('--rounds',type=int,default=3)
    parser.add_argument('--samples',type=int,default=21)
    parser.add_argument('--warmup-ms',type=float,default=500)
    parser.add_argument('--batch-ms',type=float,default=10)
    parser.add_argument('--cpu',type=int)
    parser.add_argument('--jni-include',type=pathlib.Path)
    args=parser.parse_args()
    if args.rounds<1 or args.samples<1 or args.warmup_ms<0 or args.batch_ms<0:
        parser.error('invalid protocol parameters')
    if args.cpu is not None: os.sched_setaffinity(0,{args.cpu})
    java=pathlib.Path(shutil.which('java')).resolve()
    include=args.jni_include or pathlib.Path(os.environ.get('JAVA_HOME',java.parent.parent))/'include'
    if not (include/'jni.h').exists(): parser.error('JDK JNI headers missing; use --jni-include')
    directory=ROOT/'build'
    versions=build(directory,include)
    cases,manifest=fixtures(directory)
    checks=check_libraries(directory,cases)
    print(f'Native guard/correctness checks: {checks}',flush=True)
    if args.verify_only:
        args.rounds=1; args.samples=1; args.warmup_ms=0; args.batch_ms=0
    results=[]; orders=[]
    for index in range(args.rounds):
        offset=(index*2)%len(BACKENDS)
        order=BACKENDS[offset:]+BACKENDS[:offset]
        orders.append(order)
        for backend in order:
            values=collect(directory,manifest,backend,args)
            results.append({'round':index+1,'backend':backend,'cases':values})
            print(f'round {index+1}: {backend}: 9 cases correct',flush=True)
    if args.verify_only:
        print('Correctness only: timings discarded; no performance verdict')
        return
    report={'experiment':'roi-pack-v1','environment':metadata(versions),'fixtures':cases,
            'protocol':{'rounds':args.rounds,'samples':args.samples,'warmup_ms':args.warmup_ms,
                        'target_batch_ms':args.batch_ms,'orders':orders,
                        'timed_allocation':'no workspace allocation; Java may allocate a duplicate cursor; JNI array access may allocate/copy'},
            'native_correctness_checks':checks,'runs':results}
    report['summary']=summary(report)
    args.out.parent.mkdir(parents=True,exist_ok=True)
    args.out.write_text(json.dumps(report,indent=2)+'\n')
    args.out.with_suffix('.md').write_text(markdown(report))
    print(f'Wrote {args.out.name} and {args.out.with_suffix(".md").name}',flush=True)


if __name__=='__main__': main()
