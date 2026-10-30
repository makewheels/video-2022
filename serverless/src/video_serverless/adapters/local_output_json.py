"""Lazy Fastjson getters, including HashMap iteration in stringified JSON objects."""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from decimal import Decimal

from video_serverless.fastjson_input import decode_exit_object
from video_serverless.playback_json import _fast_string
from video_serverless.profile_json import _Number, _Object


@dataclass(eq=False)
class _Node:
    name: str
    value: object
    hashcode: int
    units: tuple[int, ...]
    parent: _Node | None = None
    children: list[_Node | None] = field(default_factory=lambda: [None, None])
    red: bool = True


def _node(name, value):
    encoded = name.encode("utf-16-be", errors="surrogatepass")
    units = tuple((encoded[index] << 8) | encoded[index + 1] for index in range(0, len(encoded), 2))
    hashcode = 0
    for unit in units:
        hashcode = (31 * hashcode + unit) & 0xFFFFFFFF
    hashcode ^= hashcode >> 16
    if hashcode >= 2**31:
        hashcode -= 2**32
    return _Node(encoded.decode("utf-16-be", errors="surrogatepass"), value, hashcode, units)


def _rotate(root, node, direction):
    pivot = node.children[1 - direction]
    assert pivot is not None
    node.children[1 - direction] = pivot.children[direction]
    child = node.children[1 - direction]
    if child is not None:
        child.parent = node
    pivot.parent = node.parent
    if node.parent is None:
        root = pivot
    else:
        node.parent.children[int(node is node.parent.children[1])] = pivot
    pivot.children[direction] = node
    node.parent = pivot
    return root


def _insert(root, node):
    if root is None:
        node.red = False
        return node, None
    parent = root
    while True:
        direction = int((node.hashcode, node.units) > (parent.hashcode, parent.units))
        child = parent.children[direction]
        if child is None:
            parent.children[direction] = node
            node.parent = parent
            break
        parent = child
    inserted_parent = parent
    while node.parent is not None and node.parent.red:
        parent = node.parent
        grandparent = parent.parent
        assert grandparent is not None
        side = int(parent is grandparent.children[1])
        uncle = grandparent.children[1 - side]
        if uncle is not None and uncle.red:
            parent.red = uncle.red = False
            grandparent.red = True
            node = grandparent
            continue
        if node is parent.children[1 - side]:
            node = parent
            root = _rotate(root, node, side)
        parent = node.parent
        assert parent is not None and parent.parent is not None
        grandparent = parent.parent
        parent.red = False
        grandparent.red = True
        root = _rotate(root, grandparent, 1 - side)
    root.red = False
    return root, inserted_parent


def _front(nodes, root):
    nodes.remove(root)
    nodes.insert(0, root)


def _tree(nodes):
    for node in nodes:
        node.parent, node.children, node.red = None, [None, None], True
    root = None
    for node in nodes:
        root, _ = _insert(root, node)
    if root is not None:
        _front(nodes, root)
    return root


def _resize(table, capacity):
    result = {}
    for index, (nodes, root) in table.items():
        parts = ([node for node in nodes if not node.hashcode & capacity],
                 [node for node in nodes if node.hashcode & capacity])
        for offset, part in enumerate(parts):
            if not part:
                continue
            next_root = None
            if root is not None and len(part) > 6:
                next_root = root if len(part) == len(nodes) else _tree(part)
            result[index + offset * capacity] = part, next_root
    return result, capacity * 2


def _entries(pairs):
    table, seen, capacity = {}, {}, 16
    for name, value in pairs:
        node = _node(name, value)
        if node.units in seen:
            seen[node.units].value = value
            continue
        seen[node.units] = node
        index = node.hashcode & (capacity - 1)
        nodes, root = table.get(index, ([], None))
        if root is None:
            nodes.append(node)
        else:
            root, parent = _insert(root, node)
            assert parent is not None
            # HashMap's tree-bin chain inserts after the tree parent, not at the tail.
            nodes.insert(nodes.index(parent) + 1, node)
            _front(nodes, root)
        table[index] = nodes, root
        if root is None and len(nodes) >= 9:
            if capacity < 64:
                table, capacity = _resize(table, capacity)
            else:
                table[index] = nodes, _tree(nodes)
        if len(seen) > capacity * 3 // 4:
            table, capacity = _resize(table, capacity)
    return [node for index in sorted(table) for node in table[index][0]]


def _getter_value(value):
    if isinstance(value, _Object):
        # JSONObject.getString uses JSONObject.toString; ordinary DTO string binding is separate.
        return _Object((node.name, _getter_value(node.value)) for node in _entries(value))
    if isinstance(value, list):
        return [_getter_value(item) for item in value]
    if isinstance(value, str) and not isinstance(value, _Number):
        return value.encode("utf-16-be", errors="surrogatepass").decode("utf-16-be", errors="surrogatepass")
    return value


class LocalOutputSidecar:
    def __init__(self, content: str):
        self._fields = dict(decode_exit_object(content.encode("utf-8")))

    def integer(self, name: str, *, primitive: bool = False) -> int | None:
        value = self._fields.get(name)
        if value is None or value in ("", "null", "NULL"):
            return 0 if primitive else None
        if isinstance(value, bool):
            return int(value)
        if isinstance(value, _Number):
            number = int(Decimal(value))
            return (number + 2**31) % 2**32 - 2**31
        if isinstance(value, str) and re.fullmatch(r"[+-]?[0-9]+", value):
            number = int(value)
            if -(2**31) <= number < 2**31:
                return number
        raise ValueError("LOCAL sidecar integer conversion failed")

    def text(self, name: str) -> str | None:
        return _fast_string(_getter_value(self._fields.get(name)))
