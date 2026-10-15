"""Small closed grammar for owner-requested controls, independent of inference.

Full matches only: prose, compound requests and unknown app names go through
ordinary routing. Package identities come from app discovery, never guessing.
"""
from __future__ import annotations

import re

from lain.capabilities.registry import DEFAULT_REGISTRY, SETTINGS_PAGES, VOLUME_STREAMS
from lain.errors import LainError
from lain.planning.models import ProposedAction


def parse_device_command(text: str) -> ProposedAction | None:
    if not isinstance(text, str):
        return None
    try:
        if not text.strip() or len(text.encode("utf-8")) > 4096 or "\x00" in text:
            return None
    except UnicodeError:
        return None
    phrase = text.strip().rstrip(".! ")
    normalized = re.sub(r"\s+", " ", phrase.casefold())
    name, args = None, {}
    match = re.fullmatch(r"(?:turn (?:the )?(flashlight|torch) (on|off)|turn (on|off) (?:the )?(flashlight|torch)|(flashlight|torch) (on|off))", normalized)
    if match:
        name, args = "flashlight_set", {"enabled": (match[2] or match[3] or match[6]) == "on"}
    streams = "|".join(VOLUME_STREAMS)
    stream_prefix = rf"(?:(?P<stream>{streams}) )?volume"
    if name is None and (match := re.fullmatch(rf"set {stream_prefix} to (?P<pct>[0-9]{{1,3}})(?: percent|%)", normalized)):
        name, args = "volume_set", {"percentage": int(match['pct']), "stream": match['stream'] or 'media'}
    if name is None and (match := re.fullmatch(rf"{stream_prefix} (?P<direction>up|down|mute|unmute)", normalized)):
        name, args = "volume_adjust", {"direction": match['direction'], "stream": match['stream'] or 'media'}
    if name is None and (match := re.fullmatch(rf"(?P<direction>mute|unmute) (?:the )?{stream_prefix}", normalized)):
        name, args = "volume_adjust", {"direction": match['direction'], "stream": match['stream'] or 'media'}
    if name is None and (match := re.fullmatch(rf"(?:show|get|check) (?:the )?{stream_prefix}", normalized)):
        name, args = "volume_get", {"stream": match['stream'] or 'media'}
    if name is None and (match := re.fullmatch(r"set brightness to ([0-9]{1,3})(?: percent|%)", normalized)):
        name, args = "brightness_set", {"percentage": int(match[1])}
    if name is None and (match := re.fullmatch(r"(?:turn )?(?:auto|automatic) rotation (on|off)", normalized)):
        name, args = "rotation_set", {"enabled": match[1] == 'on'}
    if name is None and (match := re.fullmatch(r"set screen timeout to ([0-9]{1,7}) (milliseconds|seconds|minutes)", normalized)):
        multiplier = {'milliseconds': 1, 'seconds': 1000, 'minutes': 60000}[match[2]]
        name, args = "screen_timeout_set", {"milliseconds": int(match[1]) * multiplier}
    if name is None and (match := re.fullmatch(r"open (.+) settings", normalized)):
        page = {'wi-fi': 'wifi', 'wi fi': 'wifi', 'app permissions': 'app_permissions',
                'write settings': 'write_settings', 'do not disturb': 'dnd'}.get(match[1], match[1])
        if page in SETTINGS_PAGES:
            name, args = "settings_open", {"page": page}
    if name is None and (match := re.fullmatch(r"open (?:app )?([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+)", phrase, re.IGNORECASE)):
        name, args = "app_open", {"package": match[1]}
    if name is None and normalized in {'list apps', 'show apps', 'list installed apps'}:
        name, args = "app_list", {"query": ''}
    if name is None and (match := re.fullmatch(r"find app (.+)", phrase, re.IGNORECASE)):
        name, args = "app_list", {"query": match[1]}
    if name is None:
        return None
    capability = 'android.' + name
    try:
        return ProposedAction(capability, DEFAULT_REGISTRY.get(capability).validate_arguments(args))
    except LainError:
        return None
