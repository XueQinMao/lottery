"""只向stderr记录进程资源；不参与特征、模型或JSON协议。无需额外依赖。"""
import ctypes
import json
import os
import threading
import time


def process_metrics():
    metrics = {'pid': os.getpid(), 'time': time.time()}
    if os.name == 'nt':
        from ctypes import wintypes as w

        class Memory(ctypes.Structure):
            _fields_ = [('cb', w.DWORD), ('PageFaultCount', w.DWORD)] + [
                (name, ctypes.c_size_t) for name in (
                    'PeakWorkingSetSize', 'WorkingSetSize', 'QuotaPeakPagedPoolUsage',
                    'QuotaPagedPoolUsage', 'QuotaPeakNonPagedPoolUsage', 'QuotaNonPagedPoolUsage',
                    'PagefileUsage', 'PeakPagefileUsage', 'PrivateUsage')]

        class ThreadEntry(ctypes.Structure):
            _fields_ = [(name, w.DWORD) for name in (
                'dwSize', 'cntUsage', 'th32ThreadID', 'th32OwnerProcessID')]
            _fields_ += [('tpBasePri', w.LONG), ('tpDeltaPri', w.LONG), ('dwFlags', w.DWORD)]

        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        psapi = ctypes.WinDLL('psapi', use_last_error=True)
        kernel.GetCurrentProcess.restype = w.HANDLE
        handle = kernel.GetCurrentProcess()
        kernel.GetProcessHandleCount.argtypes = [w.HANDLE, ctypes.POINTER(w.DWORD)]
        psapi.GetProcessMemoryInfo.argtypes = [w.HANDLE, ctypes.POINTER(Memory), w.DWORD]
        memory = Memory()
        memory.cb = ctypes.sizeof(memory)
        handles = w.DWORD()
        if not psapi.GetProcessMemoryInfo(handle, ctypes.byref(memory), memory.cb):
            raise ctypes.WinError(ctypes.get_last_error())
        kernel.GetProcessHandleCount(handle, ctypes.byref(handles))
        metrics.update(working_set=memory.WorkingSetSize, private_bytes=memory.PrivateUsage,
                       handles=handles.value)
        kernel.CreateToolhelp32Snapshot.argtypes = [w.DWORD, w.DWORD]
        kernel.CreateToolhelp32Snapshot.restype = w.HANDLE
        kernel.Thread32First.argtypes = [w.HANDLE, ctypes.POINTER(ThreadEntry)]
        kernel.Thread32Next.argtypes = [w.HANDLE, ctypes.POINTER(ThreadEntry)]
        kernel.CloseHandle.argtypes = [w.HANDLE]
        snapshot = kernel.CreateToolhelp32Snapshot(4, 0)
        if snapshot != ctypes.c_void_p(-1).value:
            try:
                entry = ThreadEntry()
                entry.dwSize = ctypes.sizeof(entry)
                ok = kernel.Thread32First(snapshot, ctypes.byref(entry))
                count = 0
                while ok:
                    count += entry.th32OwnerProcessID == os.getpid()
                    ok = kernel.Thread32Next(snapshot, ctypes.byref(entry))
                metrics['threads'] = count
            finally:
                kernel.CloseHandle(snapshot)
    else:
        # Linux原生线程数与驻留内存；没有/proc的平台保留Python线程指标。
        metrics['python_threads'] = threading.active_count()
        try:
            with open('/proc/self/status') as stream:
                status = dict(line.split(':', 1) for line in stream if ':' in line)
            metrics.update(threads=int(status['Threads']),
                           working_set=int(status['VmRSS'].split()[0]) * 1024)
        except (OSError, KeyError):
            pass
    return metrics


def start_diagnostics(log, interval=60):
    stop = threading.Event()

    def report():
        while not stop.is_set():
            try:
                log('[资源] ' + json.dumps(process_metrics(), sort_keys=True))
            except Exception as error:
                log('[资源监控失败] ' + str(error))
            stop.wait(interval)

    thread = threading.Thread(target=report, name='prediction-resource-monitor', daemon=True)
    thread.start()

    def close():
        stop.set()
        thread.join(timeout=2)

    return close
