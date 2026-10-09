#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
双色球 Python 依赖检测 / 一键安装
================================
对照 requirements.txt 与源码实际 import，检测缺失包并安装。

用法:
  py install_deps.py                 # 检测缺失并安装全部依赖
  py install_deps.py --check         # 只检测，不安装
  py install_deps.py --core-only     # 只装必需包（跳过 tensorflow / torch）
  py install_deps.py --add-path      # 安装后把 python 加入用户 PATH（供 Java 调用）
  py install_deps.py --mirror aliyun # 换源：tsinghua / aliyun / douban / official
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REQ_FILE = os.path.join(HERE, 'requirements.txt')

# 源码实际 import 名（pip 名 -> import 名）
IMPORT_NAME = {
    'numpy': 'numpy',
    'pandas': 'pandas',
    'scikit-learn': 'sklearn',
    'xgboost': 'xgboost',
    'tensorflow': 'tensorflow',
    'torch': 'torch',
}

# 缺失时训练脚本会跳过，不阻断 RF/XGB/融合流程
OPTIONAL_PACKAGES = {'tensorflow', 'torch'}

MIRRORS = {
    'tsinghua': 'https://pypi.tuna.tsinghua.edu.cn/simple',
    'aliyun': 'https://mirrors.aliyun.com/pypi/simple',
    'douban': 'https://pypi.douban.com/simple',
    'official': 'https://pypi.org/simple',
}

MIN_PY, MAX_PY = (3, 9), (3, 12)


def log(msg=''):
    print(msg, flush=True)


def configure_stdio():
    if sys.platform != 'win32':
        return
    try:
        import io
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
    except Exception:
        pass


def parse_requirements(path, core_only=False):
    """解析 requirements.txt，返回 [(pip_name, spec_line), ...]"""
    if not os.path.isfile(path):
        log(f'[错误] 找不到依赖文件: {path}')
        sys.exit(1)
    items = []
    with open(path, encoding='utf-8') as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith('#'):
                continue
            name = re.split(r'[<>=!~\[]', line, maxsplit=1)[0].strip().lower()
            if core_only and name in OPTIONAL_PACKAGES:
                continue
            items.append((name, line))
    return items


def pkg_status(pip_name):
    """返回 (installed: bool, version: str|None, error: str|None)"""
    mod = IMPORT_NAME.get(pip_name, pip_name.replace('-', '_'))
    try:
        m = __import__(mod)
        return True, getattr(m, '__version__', '?'), None
    except Exception as e:
        return False, None, f'{type(e).__name__}: {e}'


def check_python_version():
    v = sys.version_info[:2]
    log(f'[Python] {sys.executable}')
    log(f'[版本]   {sys.version.split()[0]}')
    if v < MIN_PY or v > MAX_PY:
        log(f'[警告] 推荐 Python {MIN_PY[0]}.{MIN_PY[1]} ~ {MAX_PY[0]}.{MAX_PY[1]}，'
            f'当前 {v[0]}.{v[1]}。tensorflow / torch 可能没有对应 wheel。')
        return False
    return True


def pip_cmd(*args, mirror='tsinghua'):
    cmd = [sys.executable, '-m', 'pip', *args]
    if mirror and mirror != 'official':
        index = MIRRORS[mirror]
        if 'install' in args:
            cmd.extend(['-i', index, '--trusted-host', index.split('/')[2]])
    log('  > ' + ' '.join(cmd))
    return subprocess.call(cmd)


def install_packages(specs, mirror):
    if not specs:
        return 0
    code = pip_cmd('install', '--upgrade', 'pip', 'setuptools', 'wheel', mirror=mirror)
    if code != 0:
        log('[警告] 升级 pip 失败，继续尝试安装依赖')
    return pip_cmd('install', '--upgrade', *specs, mirror=mirror)


def python_on_path():
    """PATH 中的 python 是否指向当前解释器（Java 端写死调用 python）"""
    from shutil import which
    found = which('python')
    if not found:
        return False, None
    try:
        real = os.path.normcase(os.path.realpath(found))
        me = os.path.normcase(os.path.realpath(sys.executable))
        if real == me:
            return True, found
        # WindowsApps 商店占位 python.exe 不能用
        if 'windowsapps' in real:
            return False, found
        out = subprocess.check_output(
            [found, '-c', 'import sys; print(sys.executable)'],
            stderr=subprocess.STDOUT, text=True, timeout=10)
        other = os.path.normcase(os.path.realpath(out.strip()))
        return other == me, found
    except Exception:
        return False, found


def add_python_to_user_path():
    if sys.platform != 'win32':
        log('[跳过] --add-path 仅支持 Windows')
        return False
    python_dir = os.path.dirname(sys.executable)
    scripts_dir = os.path.join(python_dir, 'Scripts')
    to_add = [python_dir, scripts_dir]
    try:
        import winreg
        key = winreg.OpenKey(
            winreg.HKEY_CURRENT_USER, 'Environment', 0,
            winreg.KEY_READ | winreg.KEY_WRITE)
        try:
            current, _ = winreg.QueryValueEx(key, 'Path')
        except FileNotFoundError:
            current = ''
        parts = [p for p in current.split(';') if p]
        lower = [os.path.normcase(os.path.normpath(p)) for p in parts]
        changed = False
        # 插到最前，避免被 WindowsApps 商店占位 python.exe 抢先
        for p in reversed(to_add):
            np = os.path.normcase(os.path.normpath(p))
            if np not in lower:
                parts.insert(0, p)
                lower.insert(0, np)
                changed = True
            elif parts and os.path.normcase(os.path.normpath(parts[0])) != np:
                # 已存在但不在最前：挪到最前
                idx = lower.index(np)
                parts.insert(0, parts.pop(idx))
                lower.insert(0, lower.pop(idx))
                changed = True
        if not changed:
            log(f'[PATH] 已包含: {python_dir}')
            winreg.CloseKey(key)
            return True
        new_path = ';'.join(parts)
        winreg.SetValueEx(key, 'Path', 0, winreg.REG_EXPAND_SZ, new_path)
        winreg.CloseKey(key)
        try:
            import ctypes
            HWND_BROADCAST = 0xFFFF
            WM_SETTINGCHANGE = 0x001A
            ctypes.windll.user32.SendMessageTimeoutW(
                HWND_BROADCAST, WM_SETTINGCHANGE, 0, 'Environment', 0, 5000, None)
        except Exception:
            pass
        log(f'[PATH] 已写入用户环境变量（新开终端生效）:')
        log(f'       {python_dir}')
        log(f'       {scripts_dir}')
        return True
    except OSError as e:
        log(f'[PATH] 写入失败: {e}')
        return False


def print_table(rows):
    log('')
    log(f'  {"包名":<16} {"状态":<8} {"版本 / 说明"}')
    log('  ' + '-' * 56)
    for name, ok, ver, err, optional in rows:
        if ok:
            status = '已安装'
            extra = ver or ''
        else:
            status = '不可用'
            extra = err or '未安装'
            if optional:
                extra = f'{extra}  [可选，失败则跳过 LSTM/Transformer]'
        log(f'  {name:<16} {status:<8} {extra}')
    log('')


def main():
    configure_stdio()
    parser = argparse.ArgumentParser(description='双色球 Python 依赖一键安装')
    parser.add_argument('--check', action='store_true', help='只检测，不安装')
    parser.add_argument('--core-only', action='store_true',
                        help='只安装必需包（numpy/pandas/sklearn/xgboost）')
    parser.add_argument('--add-path', action='store_true',
                        help='把当前 Python 加入用户 PATH，供 Java 端 `python` 调用')
    parser.add_argument('--mirror', choices=list(MIRRORS), default='tsinghua',
                        help='pip 源（默认清华）')
    args = parser.parse_args()

    log('=' * 64)
    log('  双色球 Python 依赖检测 / 安装')
    log('=' * 64)
    ok_ver = check_python_version()
    log(f'[依赖文件] {REQ_FILE}')
    log(f'[pip 源]   {args.mirror}  {MIRRORS[args.mirror]}')

    items = parse_requirements(REQ_FILE, core_only=args.core_only)
    rows = []
    missing_core = []
    missing_opt = []
    for name, spec in items:
        installed, ver, err = pkg_status(name)
        optional = name in OPTIONAL_PACKAGES
        rows.append((name, installed, ver, err, optional))
        if not installed:
            (missing_opt if optional else missing_core).append(spec)

    print_table(rows)

    if args.check:
        n_miss = len(missing_core) + len(missing_opt)
        if n_miss == 0:
            log('[结果] 依赖齐全。')
            return 0
        log(f'[结果] 缺少 {n_miss} 个包。运行本脚本（不加 --check）即可安装。')
        return 1

    if not missing_core and not missing_opt:
        log('[结果] 依赖已齐全，无需安装。')
    else:
        if missing_core:
            log('[安装] 必需依赖: ' + ', '.join(missing_core))
            code = install_packages(missing_core, args.mirror)
            if code != 0:
                log('[错误] 必需依赖安装失败，请检查网络 / Python 版本后重试。')
                return code
        if missing_opt:
            log('[安装] 深度学习依赖（体积较大，失败不阻断核心流程）:')
            for spec in missing_opt:
                log(f'  -> {spec}')
                code = install_packages([spec], args.mirror)
                if code != 0:
                    log(f'[警告] {spec} 安装失败，训练时将自动跳过对应模型。')

        log('\n[复查] 安装后状态:')
        rows = []
        still_core = False
        for name, spec in items:
            installed, ver, err = pkg_status(name)
            optional = name in OPTIONAL_PACKAGES
            rows.append((name, installed, ver, err, optional))
            if not installed and not optional:
                still_core = True
        print_table(rows)
        if still_core:
            log('[错误] 仍有必需包未安装成功。')
            return 1
        log('[结果] 核心依赖已就绪。')

    on_path, found = python_on_path()
    if on_path:
        log(f'[PATH] `python` 可用: {found}')
    else:
        log('[PATH] Java 端调用命令为 `python`，当前终端找不到正确的 python。')
        if found:
            log(f'       PATH 中的 python 是占位/错误路径: {found}')
        if args.add_path or sys.platform == 'win32':
            if not args.add_path:
                log('       正在自动写入用户 PATH …')
            add_python_to_user_path()
            log('       请关闭并重新打开终端 / IDE 后再运行 Java 项目。')
        else:
            log(f'       请把以下目录加入 PATH:\n       {os.path.dirname(sys.executable)}')

    if not ok_ver:
        log('[提示] 当前 Python 版本不在推荐区间，若 tensorflow/torch 安装失败属预期。')
    return 0


if __name__ == '__main__':
    sys.exit(main())
