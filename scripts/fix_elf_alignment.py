#!/usr/bin/env python3
"""
Patch ELF LOAD segment p_align from 4KB to 16KB in .so files.

Android 15+ (especially API 36) requires ELF LOAD segments to be 16KB-aligned.
Moonshine SDK ships with 4KB alignment. This script patches the ELF program headers
in-place to declare 16KB alignment. Safe because the actual segment offsets/addresses
in these libraries are already 16KB-aligned (they just declare 4KB in the header).

Usage: python3 fix_elf_alignment.py <file.so> [file2.so ...]
"""

import struct
import sys

ELF_MAGIC = b'\x7fELF'
PT_LOAD = 1
PAGE_4K = 0x1000
PAGE_16K = 0x4000


def patch_elf(path: str) -> bool:
    with open(path, 'r+b') as f:
        magic = f.read(4)
        if magic != ELF_MAGIC:
            print(f"  SKIP {path}: not an ELF file")
            return False

        ei_class = struct.unpack('B', f.read(1))[0]
        if ei_class != 2:
            print(f"  SKIP {path}: not 64-bit ELF")
            return False

        ei_data = struct.unpack('B', f.read(1))[0]
        endian = '<' if ei_data == 1 else '>'

        # Read ELF header fields
        f.seek(32)  # e_phoff
        e_phoff = struct.unpack(f'{endian}Q', f.read(8))[0]
        f.seek(54)  # e_phentsize
        e_phentsize = struct.unpack(f'{endian}H', f.read(2))[0]
        e_phnum = struct.unpack(f'{endian}H', f.read(2))[0]

        patched = 0
        for i in range(e_phnum):
            off = e_phoff + i * e_phentsize
            f.seek(off)
            p_type = struct.unpack(f'{endian}I', f.read(4))[0]
            if p_type != PT_LOAD:
                continue

            # p_align is at offset 48 within the phdr (64-bit ELF)
            align_off = off + 48
            f.seek(align_off)
            p_align = struct.unpack(f'{endian}Q', f.read(8))[0]

            if p_align == PAGE_4K:
                f.seek(align_off)
                f.write(struct.pack(f'{endian}Q', PAGE_16K))
                patched += 1

        if patched:
            print(f"  PATCHED {path}: {patched} LOAD segments 4KB -> 16KB")
        else:
            print(f"  OK {path}: no 4KB LOAD segments found")
        return patched > 0


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <file.so> [file2.so ...]")
        sys.exit(1)

    any_patched = False
    for path in sys.argv[1:]:
        if patch_elf(path):
            any_patched = True

    sys.exit(0 if any_patched else 1)
