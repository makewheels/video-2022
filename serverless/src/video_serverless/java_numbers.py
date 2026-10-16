"""Text formatting for the finite legacy Double values covered by HTTP contract fixtures."""

from decimal import Decimal


def double_text(value: float) -> str:
    if value == 0 or 0.001 <= abs(value) < 10000000:
        result = format(Decimal(str(value)), "f")
        return result if "." in result else result + ".0"
    mantissa, exponent = format(Decimal(str(value)), "e").split("e")
    mantissa = mantissa.rstrip("0").rstrip(".") if "." in mantissa else mantissa
    return (mantissa if "." in mantissa else mantissa + ".0") + "E" + str(int(exponent))
