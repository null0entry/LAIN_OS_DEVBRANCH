"""Groq Chat Completions adapter for LAIN_OS planner subprocess requests."""
from __future__ import annotations

import argparse
import json
import socket
import ssl
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any, BinaryIO, Callable, TextIO

from lain.planner_adapters.openai_protocol import (
    PlannerProtocolError,
    build_chat_body,
    build_response_schema,
    normalize_openai_response,
    normalize_proposal,
    parse_planner_input,
)

ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
MODELS = ("openai/gpt-oss-120b", "openai/gpt-oss-20b")
DEFAULT_MODEL = MODELS[0]
TIMEOUT_SECONDS = 30.0
MAX_RESPONSE_BYTES = 1_048_576
MAX_COMPLETION_TOKENS = 1024
USER_AGENT = "Mozilla/5.0 LAIN_OS/0.0.1"
AdapterError = PlannerProtocolError


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: ANN001
        return None


def _fail(message: str) -> AdapterError:
    return AdapterError(message)


def read_key(path_text: str) -> str:
    path = Path(path_text)
    try:
        if not path.is_file():
            raise _fail("key file is not a regular readable file")
        key = path.read_text(encoding="utf-8").strip()
    except AdapterError:
        raise
    except (OSError, UnicodeError) as exc:
        raise _fail("key file is not a regular readable file") from exc
    if not key:
        raise _fail("key file is empty")
    return key


def build_api_body(request: dict[str, Any], model: str) -> bytes:
    body = json.loads(build_chat_body(
        request,
        model,
        response_mode="json_schema",
        max_completion_tokens=MAX_COMPLETION_TOKENS,
    ))
    body["reasoning_effort"] = "low"
    return json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def _default_open(request: urllib.request.Request, timeout: float):
    return urllib.request.build_opener(_NoRedirect()).open(request, timeout=timeout)


def _groq_protocol_error(exc: PlannerProtocolError) -> AdapterError:
    mapping = {
        "malformed upstream response": "malformed Groq response",
        "completion did not finish normally": "Groq completion did not finish normally",
        "missing response content": "missing Groq response content",
    }
    return _fail(mapping.get(str(exc), str(exc)))


def call_groq(
    planner_input: dict[str, Any],
    key: str,
    model: str,
    *,
    opener: Callable[[urllib.request.Request, float], BinaryIO] = _default_open,
) -> dict[str, Any]:
    request = urllib.request.Request(
        ENDPOINT,
        data=build_api_body(planner_input, model),
        method="POST",
        headers={
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": USER_AGENT,
        },
    )
    try:
        with opener(request, TIMEOUT_SECONDS) as response:
            raw = response.read(MAX_RESPONSE_BYTES + 1)
    except urllib.error.HTTPError as exc:
        exc.close()
        raise _fail(f"Groq HTTP error ({exc.code})") from exc
    except (urllib.error.URLError, TimeoutError, socket.timeout, ssl.SSLError, OSError) as exc:
        raise _fail("Groq network request failed") from exc
    if len(raw) > MAX_RESPONSE_BYTES:
        raise _fail("Groq response exceeds byte limit")
    try:
        return normalize_openai_response(raw, planner_input)
    except PlannerProtocolError as exc:
        raise _groq_protocol_error(exc) from exc


def main(
    argv: list[str] | None = None,
    *,
    stdin: TextIO = sys.stdin,
    stdout: TextIO = sys.stdout,
    stderr: TextIO = sys.stderr,
    opener: Callable[[urllib.request.Request, float], BinaryIO] = _default_open,
) -> int:
    parser = argparse.ArgumentParser(description="Use Groq as a LAIN_OS subprocess planner")
    parser.add_argument("--key-file", required=True, metavar="PATH", help="read the Groq API key from PATH")
    parser.add_argument("--model", choices=MODELS, default=DEFAULT_MODEL)
    try:
        args = parser.parse_args(argv)
        key = read_key(args.key_file)
        planner_input = parse_planner_input(stdin.read())
        proposal = call_groq(planner_input, key, args.model, opener=opener)
        stdout.write(json.dumps(proposal, ensure_ascii=False, separators=(",", ":")) + "\n")
        return 0
    except AdapterError as exc:
        print(f"lain-groq-planner: {exc}", file=stderr)
        return 1
    except Exception:
        print("lain-groq-planner: unexpected adapter failure", file=stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
