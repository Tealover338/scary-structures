"""Split large vanilla structure templates into chunk sized templates.

The input is streamed. No complete multi-million-block NBT tree is kept in RAM.
Run from the project root: python tools/split_structures.py
"""

from collections import Counter, defaultdict
from pathlib import Path
import gzip
import io
import json
import struct


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/resources/data/horror_structures/structures"
OUTPUT = ROOT / "src/main/resources/data/horror_structures/worldgen_parts"
POSITION_MARKER = b"\x09\x00\x03pos\x03\x00\x00\x00\x03"
ENTITY_POSITION_MARKER = b"\x09\x00\x03pos\x06\x00\x00\x00\x03"
BLOCK_POSITION_MARKER = b"\x09\x00\x08blockPos\x03\x00\x00\x00\x03"
STATE_MARKER = b"\x03\x00\x05state"


def exact(stream, length):
    data = stream.read(length)
    if len(data) != length:
        raise EOFError(f"Expected {length} bytes, got {len(data)}")
    return data


def short(stream):
    return struct.unpack(">H", exact(stream, 2))[0]


def integer(stream):
    return struct.unpack(">i", exact(stream, 4))[0]


def string(stream):
    return exact(stream, short(stream))


def payload(stream, tag_type):
    if tag_type in (1, 2, 3, 4, 5, 6):
        return exact(stream, {1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}[tag_type])
    if tag_type == 8:
        length = exact(stream, 2)
        return length + exact(stream, struct.unpack(">H", length)[0])
    if tag_type in (7, 11, 12):
        length = exact(stream, 4)
        count = struct.unpack(">i", length)[0]
        return length + exact(stream, count * {7: 1, 11: 4, 12: 8}[tag_type])
    if tag_type == 9:
        element = exact(stream, 1)
        length = exact(stream, 4)
        count = struct.unpack(">i", length)[0]
        result = bytearray(element + length)
        for _ in range(count):
            result.extend(payload(stream, element[0]))
        return bytes(result)
    if tag_type == 10:
        result = bytearray()
        while True:
            child = exact(stream, 1)
            result.extend(child)
            if child == b"\x00":
                break
            name_length = exact(stream, 2)
            result.extend(name_length)
            result.extend(exact(stream, struct.unpack(">H", name_length)[0]))
            result.extend(payload(stream, child[0]))
        return bytes(result)
    raise ValueError(f"Unsupported NBT tag {tag_type}")


def named(tag_type, name, data):
    encoded = name.encode("utf-8")
    return bytes([tag_type]) + struct.pack(">H", len(encoded)) + encoded + data


def tag_string_from_compound(raw, target):
    stream = io.BytesIO(raw)
    while True:
        tag_type = exact(stream, 1)[0]
        if tag_type == 0:
            return None
        name = string(stream).decode("utf-8")
        value = payload(stream, tag_type)
        if name == target and tag_type == 8:
            return value[2:].decode("utf-8")


def template_parts(path):
    name = path.stem
    pieces = defaultdict(bytearray)
    entities = defaultdict(bytearray)
    entity_counts = Counter()
    counts = Counter()
    state_heights = defaultdict(Counter)
    palette = []
    air_states = set()
    data_version = 3465
    size = None
    with gzip.open(path, "rb") as stream:
        if exact(stream, 1) != b"\x0a":
            raise ValueError(f"{path} is not a compound NBT")
        string(stream)
        while True:
            tag_type = exact(stream, 1)[0]
            if tag_type == 0:
                break
            field = string(stream).decode("utf-8")
            if field == "size" and tag_type == 9:
                raw = payload(stream, tag_type)
                size = struct.unpack(">iii", raw[5:17])
            elif field == "DataVersion" and tag_type == 3:
                data_version = integer(stream)
            elif field == "palette" and tag_type == 9:
                if exact(stream, 1) != b"\x0a":
                    raise ValueError("Palette must contain compounds")
                palette = [payload(stream, 10) for _ in range(integer(stream))]
                air_states = {index for index, raw in enumerate(palette)
                              if tag_string_from_compound(raw, "Name") in
                              ("minecraft:air", "minecraft:cave_air", "minecraft:void_air")}
            elif field == "blocks" and tag_type == 9:
                if exact(stream, 1) != b"\x0a":
                    raise ValueError("Blocks must contain compounds")
                block_count = integer(stream)
                for index in range(block_count):
                    raw = bytearray(payload(stream, 10))
                    marker = raw.find(POSITION_MARKER)
                    state_marker = raw.find(STATE_MARKER)
                    if marker < 0 or state_marker < 0:
                        raise ValueError(f"Missing block position or state at {index}")
                    position_offset = marker + len(POSITION_MARKER)
                    x, y, z = struct.unpack_from(">iii", raw, position_offset)
                    state = struct.unpack_from(">i", raw, state_marker + len(STATE_MARKER))[0]
                    struct.pack_into(">iii", raw, position_offset, x & 15, y, z & 15)
                    key = (x >> 4, z >> 4)
                    # Keep an empty chunk part, but do not serialize millions of captured air blocks.
                    pieces[key]
                    if state in air_states and (name != "bunker" or y >= 134
                                                or x < 6 or z < 6 or x >= size[0] - 6 or z >= size[2] - 6):
                        continue
                    pieces[key].extend(raw)
                    counts[key] += 1
                    state_heights[state][y] += 1
                print(f"{name}: read {block_count:,} blocks", flush=True)
            elif field == "entities" and tag_type == 9:
                entity_type = exact(stream, 1)
                entity_count = integer(stream)
                if entity_type == b"\x00" and entity_count == 0:
                    continue
                if entity_type != b"\x0a":
                    raise ValueError("Entities must contain compounds")
                for _ in range(entity_count):
                    raw = bytearray(payload(stream, 10))
                    if b"\x0a\x00\x03nbt\x00" in raw:
                        continue
                    marker = raw.find(ENTITY_POSITION_MARKER)
                    if marker < 0:
                        continue
                    position_offset = marker + len(ENTITY_POSITION_MARKER)
                    x, y, z = struct.unpack_from(">ddd", raw, position_offset)
                    chunk_x, chunk_z = int(x) >> 4, int(z) >> 4
                    struct.pack_into(">ddd", raw, position_offset, x - chunk_x * 16, y, z - chunk_z * 16)
                    block_marker = raw.find(BLOCK_POSITION_MARKER)
                    if block_marker >= 0:
                        block_offset = block_marker + len(BLOCK_POSITION_MARKER)
                        block_x, block_y, block_z = struct.unpack_from(">iii", raw, block_offset)
                        struct.pack_into(">iii", raw, block_offset, block_x - chunk_x * 16, block_y, block_z - chunk_z * 16)
                    entities[chunk_x, chunk_z].extend(raw)
                    entity_counts[chunk_x, chunk_z] += 1
            else:
                payload(stream, tag_type)

    if size is None or not palette:
        raise ValueError(f"Missing template size or palette in {path}")
    palette_raw = b"\x0a" + struct.pack(">i", len(palette)) + b"".join(palette)
    grass_states = [index for index, raw in enumerate(palette) if tag_string_from_compound(raw, "Name") in
                    ("minecraft:grass_block", "minecraft:mycelium", "minecraft:podzol")]
    grass_heights = sum((state_heights[index] for index in grass_states), Counter())
    ground = grass_heights.most_common(1)[0][0] if grass_heights else 0
    print(f"{name}: size={size}, grass levels={grass_heights.most_common(6)}, pieces={len(pieces)}", flush=True)

    directory = OUTPUT / name
    directory.mkdir(parents=True, exist_ok=True)
    for (chunk_x, chunk_z), block_data in pieces.items():
        # Vanilla templates commonly store their palette *after* their blocks.
        # Filter here, once every state name is known, rather than while streaming.
        stream = io.BytesIO(block_data)
        filtered = bytearray()
        kept = 0
        for _ in range(counts[chunk_x, chunk_z]):
            raw = payload(stream, 10)
            marker = raw.find(POSITION_MARKER)
            state_marker = raw.find(STATE_MARKER)
            x, y, z = struct.unpack_from(">iii", raw, marker + len(POSITION_MARKER))
            state = struct.unpack_from(">i", raw, state_marker + len(STATE_MARKER))[0]
            if state in air_states and (name != "bunker" or y >= 134
                                        or chunk_x * 16 + x < 6 or chunk_z * 16 + z < 6
                                        or chunk_x * 16 + x >= size[0] - 6
                                        or chunk_z * 16 + z >= size[2] - 6):
                continue
            filtered.extend(raw)
            kept += 1
        block_data = filtered
        counts[chunk_x, chunk_z] = kept
        root = bytearray(b"\x0a\x00\x00")
        root.extend(named(9, "size", b"\x03\x00\x00\x00\x03" + struct.pack(">iii", 16, size[1], 16)))
        root.extend(named(9, "entities", b"\x0a" + struct.pack(">i", entity_counts[chunk_x, chunk_z]) + entities[chunk_x, chunk_z]))
        root.extend(named(9, "blocks", b"\x0a" + struct.pack(">i", counts[chunk_x, chunk_z]) + block_data))
        root.extend(named(9, "palette", palette_raw))
        root.extend(named(3, "DataVersion", struct.pack(">i", data_version)))
        root.extend(b"\x00")
        with gzip.open(directory / f"{chunk_x}_{chunk_z}.nbt", "wb", compresslevel=5) as output:
            output.write(root)
    return {"name": name, "size": size, "grass_level": ground, "grass_levels": grass_heights.most_common(6),
            "blocks": sum(counts.values()), "pieces": len(pieces), "entities": sum(entity_counts.values())}


if __name__ == "__main__":
    summary = []
    for source in sorted(SOURCE.glob("*.nbt")):
        if source.stem not in {"abandoned_park", "bunker", "circus", "labyrinth", "planes"}:
            continue
        summary.append(template_parts(source))
    (ROOT / "tools/structure_stats.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
