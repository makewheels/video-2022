"""Java String.trim semantics, without broadening whitespace rules."""


def trim(value: str) -> str:
    return value.strip("".join(chr(number) for number in range(33)))
