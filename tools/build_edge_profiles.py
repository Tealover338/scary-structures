"""Build terrain heights, extending missing border samples from actual saved soil."""
from collections import deque
from pathlib import Path
import nbtlib

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'src/main/resources/data/horror_structures'
GROUND = {'grass_block', 'moss_block', 'dirt', 'coarse_dirt', 'rooted_dirt', 'podzol',
          'mycelium', 'stone', 'gravel', 'sand', 'red_sand', 'tuff', 'andesite',
          'diorite', 'granite', 'deepslate', 'clay'}

for name, width, depth in [('bunker', 249, 182), ('abandoned_park', 230, 220)]:
    heights = [-1] * (width * depth)
    for part in (BASE / 'worldgen_parts' / name).glob('*.nbt'):
        cx, cz = map(int, part.stem.split('_'))
        tag = nbtlib.load(part)
        ground = {i for i, s in enumerate(tag['palette'])
                  if str(s['Name']).removeprefix('minecraft:') in GROUND}
        for block in tag['blocks']:
            if int(block['state']) not in ground:
                continue
            lx, y, lz = map(int, block['pos'])
            x, z = cx * 16 + lx, cz * 16 + lz
            if x < width and z < depth:
                heights[z * width + x] = max(heights[z * width + x], y)
    missing = sum(h < 0 for h in heights)
    queue = deque(i for i, h in enumerate(heights) if h >= 0)
    if not queue:
        raise ValueError(f'No ground in {name}')
    while queue:
        i = queue.popleft()
        x, z = i % width, i // width
        for nx, nz in ((x-1, z), (x+1, z), (x, z-1), (x, z+1)):
            if 0 <= nx < width and 0 <= nz < depth and heights[nz * width + nx] < 0:
                heights[nz * width + nx] = heights[i]
                queue.append(nz * width + nx)
    out = BASE / 'surface_profiles' / f'{name}.bin'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(bytes(heights))
    print(f'{name}: {missing} missing columns extended, range {min(heights)}..{max(heights)}', flush=True)
