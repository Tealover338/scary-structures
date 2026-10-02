"""Precompute occupancy so the server never parses template NBT during final cleanup."""
import gzip
import struct
from pathlib import Path
import nbtlib

base = Path(__file__).resolve().parents[1] / 'src/main/resources/data/horror_structures'
for directory in (base / 'worldgen_parts').iterdir():
    if not directory.is_dir():
        continue
    out = base / 'occupancy' / directory.name
    out.mkdir(parents=True, exist_ok=True)
    for part in directory.glob('*.nbt'):
        tag = nbtlib.load(part)
        palette = [str(s['Name']) for s in tag['palette']]
        empty = {i for i, n in enumerate(palette) if n in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air')}
        tree = {i for i, n in enumerate(palette) if n.endswith(('_log', '_wood', '_leaves')) or n == 'minecraft:vine'}
        length = int(tag['size'][1]) * 32
        vegetation, solid, air = bytearray(length), bytearray(length), bytearray(length)
        for block in tag['blocks']:
            x, y, z = map(int, block['pos'])
            state = int(block['state']); index = (y << 8) | (z << 4) | x
            target = air if state in empty else solid
            target[index >> 3] |= 1 << (index & 7)
            if state in tree:
                vegetation[index >> 3] |= 1 << (index & 7)
        with gzip.open(out / (part.stem + '.bin'), 'wb') as f:
            f.write(struct.pack('>i', length))
            f.write(vegetation); f.write(solid); f.write(air)
    print(f'{directory.name}: occupancy ready', flush=True)
