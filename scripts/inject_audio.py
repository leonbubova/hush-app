#!/usr/bin/env python3
"""Inject a WAV file into the Android emulator's virtual microphone via gRPC.

Requirements:
    pip install grpcio grpcio-tools

Proto compilation (one-time):
    EMU_PROTO=$(find $ANDROID_HOME -name emulator_controller.proto | head -1)
    python3 -m grpc_tools.protoc -I$(dirname $EMU_PROTO) \
        --python_out=. --grpc_python_out=. $EMU_PROTO

Usage:
    python3 inject_audio.py <wav_file> [grpc_port]
    python3 inject_audio.py test_audio.wav 8554
"""

import glob
import os
import sys
import wave
import time

import grpc

# Add script directory to path for compiled proto imports
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import emulator_controller_pb2 as pb
import emulator_controller_pb2_grpc as pb_grpc


def find_grpc_token():
    """Find the gRPC auth token from the emulator discovery file.

    The Android emulator writes a discovery .ini file with the gRPC JWT token
    at ~/Library/Caches/TemporaryItems/avd/running/pid_<PID>.ini (macOS).
    """
    discovery_dir = os.path.expanduser('~/Library/Caches/TemporaryItems/avd/running')
    for ini_file in glob.glob(os.path.join(discovery_dir, 'pid_*.ini')):
        with open(ini_file) as f:
            for line in f:
                if line.startswith('grpc.token='):
                    return line.strip().split('=', 1)[1]
    return None


def audio_packets(wav_path):
    """Stream AudioPacket messages from a WAV file in 30ms chunks."""
    with wave.open(wav_path, 'rb') as wf:
        rate = wf.getframerate()
        channels = wf.getnchannels()
        sampwidth = wf.getsampwidth()
        n_frames = wf.getnframes()
        duration = n_frames / rate

        print(f"WAV: {rate}Hz, {channels}ch, {sampwidth*8}bit, {n_frames} frames ({duration:.1f}s)")

        chunk_frames = int(rate * 0.03)  # 30ms chunks
        fmt = pb.AudioFormat(
            samplingRate=rate,
            channels=pb.AudioFormat.Mono if channels == 1 else pb.AudioFormat.Stereo,
            format=pb.AudioFormat.AUD_FMT_S16,
            mode=pb.AudioFormat.MODE_UNSPECIFIED,
        )

        packets_sent = 0
        while True:
            data = wf.readframes(chunk_frames)
            if not data:
                break
            yield pb.AudioPacket(format=fmt, audio=data)
            packets_sent += 1

        print(f"Sent {packets_sent} packets ({packets_sent * 0.03:.1f}s of audio)")


def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <wav_file> [grpc_port]")
        sys.exit(1)

    wav_path = sys.argv[1]
    port = sys.argv[2] if len(sys.argv) > 2 else '8554'

    if not os.path.exists(wav_path):
        print(f"ERROR: WAV file not found: {wav_path}")
        sys.exit(1)

    token = find_grpc_token()
    if not token:
        print("ERROR: Could not find gRPC token in discovery directory")
        print("  Looked in: ~/Library/Caches/TemporaryItems/avd/running/pid_*.ini")
        print("  Is the emulator running?")
        sys.exit(1)

    print(f"Connecting to emulator gRPC at localhost:{port}...")
    channel = grpc.insecure_channel(f'localhost:{port}')
    stub = pb_grpc.EmulatorControllerStub(channel)
    metadata = [('authorization', f'Bearer {token}')]

    print(f"Injecting audio from {wav_path}...")
    start = time.time()
    try:
        stub.injectAudio(audio_packets(wav_path), metadata=metadata)
        elapsed = time.time() - start
        print(f"Done in {elapsed:.1f}s")
    except grpc.RpcError as e:
        print(f"gRPC error: {e.code()} — {e.details()}")
        sys.exit(1)


if __name__ == '__main__':
    main()
