"""独立真实模型对照/压力测试；只写--output，不写业务数据库及预测结果文件。

compare: --baseline 原predict.py --script 新predict.py --model-dir 模型目录
stress: --script 新predict.py --seconds 3600 --min-requests 10000
"""
import argparse
import hashlib
import importlib.metadata
import importlib.util
import json
import os
from pathlib import Path
import random
import subprocess
import sys
import threading
import time


def inputs():
    rng = random.Random(20261020)
    return [{'type': 'predict', 'model': 'second_fusion_model',
             'red_balls': sorted(rng.sample(range(1, 34), 6)), 'blue_ball': rng.randint(1, 16)}
            for _ in range(64)]


def worker(args):
    script = Path(args.script).resolve()
    sys.path.insert(0, str(script.parent))
    sys.argv = [str(script), '--model-dir', str(Path(args.model_dir).resolve())]
    spec = importlib.util.spec_from_file_location('subject', script)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    stop = None
    if hasattr(module, 'start_diagnostics'):
        stop = module.start_diagnostics(module.log)
    module.load_all_models()
    probabilities = {}
    lock = threading.Lock()
    original = getattr(module, '_model_proba', module.model_proba)

    def capture(key, *values):
        result = original(key, *values)
        with lock:
            probabilities[key] = result
        return result

    if hasattr(module, '_model_proba'):
        module._model_proba = capture
    else:
        module.model_proba = capture
    start = last = time.monotonic()
    count = 0
    cases = inputs()
    try:
        while count < (args.min_requests if args.mode == 'stress' else len(cases)) or (
                args.mode == 'stress' and time.monotonic() - start < args.seconds):
            probabilities.clear()
            response = module.handle_prediction(cases[count % len(cases)])
            if response.get('probability') is None or response.get('error'):
                raise RuntimeError(str(response))
            if any(value is None for value in probabilities.values()):
                raise RuntimeError('子模型返回None: ' + str(probabilities))
            if args.mode == 'compare':
                print(json.dumps({'index': count, 'response': response, 'submodels': probabilities}), flush=True)
            count += 1
            now = time.monotonic()
            if now - last >= 60:
                print(json.dumps({'progress': count, 'elapsed': now - start, 'pid': os.getpid()}), flush=True)
                last = now
        print(json.dumps({'completed': count, 'elapsed': time.monotonic() - start, 'pid': os.getpid()}), flush=True)
    finally:
        if hasattr(module, 'MODEL_EXECUTOR'):
            module.MODEL_EXECUTOR.shutdown(wait=True)
            module.LSTM_EXECUTOR.shutdown(wait=True)
        if stop:
            stop()


def run(args, script, label):
    out = Path(args.output)
    command = [sys.executable, '-u', __file__, args.mode, '--worker', '--script', str(script),
               '--model-dir', args.model_dir, '--seconds', str(args.seconds), '--min-requests', str(args.min_requests)]
    with (out / (label + '.jsonl')).open('w', encoding='utf-8') as stdout, (
            out / (label + '.stderr.log')).open('w', encoding='utf-8') as stderr:
        child = subprocess.Popen(command, stdout=stdout, stderr=stderr,
                                 env={**os.environ, 'PYTHONIOENCODING': 'utf-8', 'PYTHONFAULTHANDLER': '1'})
        (out / (label + '.pid')).write_text(str(child.pid))
        try:
            child.wait(timeout=max(600, args.seconds + 7200) if args.mode == 'stress' else 600)
        except subprocess.TimeoutExpired:
            child.kill()
            child.wait()
            raise
    (out / (label + '.exit.json')).write_text(json.dumps({'pid': child.pid, 'exit_code': child.returncode}))
    if child.returncode:
        raise RuntimeError(f'{label} exited {child.returncode}; see stderr')
    return [json.loads(line) for line in (out / (label + '.jsonl')).read_text(encoding='utf-8').splitlines()
            if line.startswith('{')]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['compare', 'stress'])
    parser.add_argument('--script', required=True)
    parser.add_argument('--baseline')
    parser.add_argument('--model-dir', required=True)
    parser.add_argument('--output', default='target/stability/results')
    parser.add_argument('--seconds', type=int, default=3600)
    parser.add_argument('--min-requests', type=int, default=10000)
    parser.add_argument('--worker', action='store_true')
    args = parser.parse_args()
    if args.worker:
        worker(args)
        return
    out = Path(args.output)
    out.mkdir(parents=True, exist_ok=True)
    def digest(path):
        with path.open('rb') as stream:
            return hashlib.file_digest(stream, 'sha256').hexdigest()
    snapshot = {'python': sys.version, 'packages': {d.metadata['Name']: d.version for d in importlib.metadata.distributions()},
                'models': {p.name: digest(p)
                           for p in sorted(Path(args.model_dir).iterdir()) if p.is_file()},
                'script_sha256': hashlib.sha256(Path(args.script).read_bytes()).hexdigest(), 'inputs': inputs()}
    history = Path(args.model_dir).parent / 'history.csv'
    if history.exists(): snapshot['history_sha256'] = digest(history)
    if args.baseline: snapshot['baseline_sha256'] = digest(Path(args.baseline))
    (out / 'environment.json').write_text(json.dumps(snapshot, indent=2), encoding='utf-8')
    if args.mode == 'compare':
        if not args.baseline:
            parser.error('compare requires --baseline')
        before = run(args, args.baseline, 'baseline')
        after = run(args, args.script, 'fixed')
        repeat = run(args, args.baseline, 'baseline-repeat')
        def compare(left, right):
            maxima = {}
            for x, y in zip(left[:-1], right[:-1]):
                assert x['index'] == y['index']
                assert x['response']['reason'] == y['response']['reason']
                assert x['submodels'].keys() == y['submodels'].keys()
                for key in x['submodels']:
                    delta = abs(x['submodels'][key] - y['submodels'][key])
                    maxima[key] = max(maxima.get(key, 0), delta)
                    assert delta <= 1e-12, (key, delta)
                delta = abs(x['response']['probability'] - y['response']['probability'])
                maxima['fusion'] = max(maxima.get('fusion', 0), delta)
                assert delta <= 1e-12, ('fusion', delta)
            assert len(left) == len(right) == len(inputs()) + 1
            return maxima
        differences = compare(before, after)
        baseline_noise = compare(before, repeat)
        (out / 'comparison.json').write_text(json.dumps({'cases': len(inputs()), 'absolute_tolerance': 1e-12,
            'max_differences': differences, 'baseline_repeat_differences': baseline_noise, 'reason_equal': True}, indent=2))
        print('PASS: 64 cases, reason exact; per-model and fusion deltas <=1e-12; see comparison.json for baseline noise')
    else:
        result = run(args, args.script, 'stress')
        summary = result[-1]
        if summary['completed'] < args.min_requests or summary['elapsed'] < args.seconds:
            raise AssertionError(summary)
        print(json.dumps(summary))


if __name__ == '__main__':
    main()
