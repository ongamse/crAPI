from base64 import b64encode


def validate_header_value(value):
    """
    validates a request derived value before it is used in an outbound
    http header
    :param value: header value taken from the incoming request
    :returns the value if it is a str of printable ascii (plus tab) only
    :raises ValueError if value has CR, LF or any other control character
    """
    if not isinstance(value, str) or not value:
        raise ValueError("invalid header value")
    if not all(ch == "\t" or " " <= ch <= "~" for ch in value):
        raise ValueError("invalid header value")
    return value


def basic_auth(username, password=""):
    token = b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
    return f"Basic {token}"
