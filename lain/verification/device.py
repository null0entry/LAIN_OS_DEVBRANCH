"""Closed Android evidence validation; acceptance is never fabricated readback."""
from __future__ import annotations

import json
import re

from lain.capabilities.registry import IDENTITY_PATTERN, PACKAGE_PATTERN
from lain.protocol.models import VerificationResult, VerificationStatus


DEVICE_FAILURE_REASONS = frozenset({
    'permission_denied', 'special_access_required', 'hardware_unavailable', 'camera_busy',
    'fixed_volume', 'policy_rejected', 'background_launch_restricted', 'handler_unavailable',
    'accessibility_unavailable', 'snapshot_stale', 'target_unavailable', 'target_ambiguous',
    'target_disabled', 'protected_surface', 'operation_rejected',
})


def validate_device_failure(details: dict) -> dict:
    _object(details, ('reason',))
    if not isinstance(details['reason'], str) or details['reason'] not in DEVICE_FAILURE_REASONS:
        raise ValueError('invalid device failure reason')
    return details


def _object(value, keys):
    if not isinstance(value, dict) or set(value) != set(keys):
        raise ValueError('invalid device evidence object')


def _integer(value, minimum=0, maximum=2**31 - 1):
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError('invalid device evidence integer')


def _boolean(value):
    if type(value) is not bool:
        raise ValueError('invalid device evidence boolean')


def _string(value, maximum=256, *, empty=True, pattern=None):
    if not isinstance(value, str) or (not empty and not value) or len(value) > maximum or '\x00' in value:
        raise ValueError('invalid device evidence string')
    value.encode('utf-8')
    if pattern is not None and re.fullmatch(pattern, value) is None:
        raise ValueError('invalid device evidence identity')


def _volume(value):
    if value is None:
        return
    _object(value, ('index', 'min_index', 'max_index', 'muted'))
    _integer(value['min_index'])
    _integer(value['max_index'], value['min_index'])
    _integer(value['index'], value['min_index'], value['max_index'])
    _boolean(value['muted'])


def _bounded(value):
    if len(json.dumps(value, ensure_ascii=False, separators=(',', ':')).encode('utf-8')) > 32768:
        raise ValueError('oversized device observation')


def _snapshot(snapshot):
    _bounded(snapshot)
    _object(snapshot, ('snapshot_id', 'package', 'window_id', 'captured_at_ms', 'expires_at_ms', 'nodes', 'truncated'))
    _string(snapshot['snapshot_id'], 128, empty=False, pattern=IDENTITY_PATTERN)
    _string(snapshot['package'], 255, empty=False, pattern=PACKAGE_PATTERN)
    _integer(snapshot['window_id'])
    _integer(snapshot['captured_at_ms'], 0, 2**63 - 1)
    _integer(snapshot['expires_at_ms'], snapshot['captured_at_ms'] + 1, min(2**63 - 1, snapshot['captured_at_ms'] + 120000))
    _boolean(snapshot['truncated'])
    nodes = snapshot['nodes']
    if not isinstance(nodes, list) or len(nodes) > 128:
        raise ValueError('invalid UI nodes')
    seen = set()
    for node in nodes:
        _object(node, ('node_id', 'depth', 'text', 'description', 'class_name', 'view_id',
                       'enabled', 'clickable', 'scrollable', 'editable', 'password', 'bounds'))
        _string(node['node_id'], 128, empty=False, pattern=IDENTITY_PATTERN)
        if node['node_id'] in seen:
            raise ValueError('duplicate UI identity')
        seen.add(node['node_id'])
        _integer(node['depth'], 0, 20)
        for key in ('text', 'description', 'class_name', 'view_id'):
            _string(node[key])
        for key in ('enabled', 'clickable', 'scrollable', 'editable', 'password'):
            _boolean(node[key])
        if node['password'] and (node['text'] or node['description']):
            raise ValueError('private UI field observation')
        bounds = node['bounds']
        if not isinstance(bounds, list) or len(bounds) != 4:
            raise ValueError('invalid UI bounds')
        for coordinate in bounds:
            _integer(coordinate, -(2**31))
        if bounds[2] < bounds[0] or bounds[3] < bounds[1]:
            raise ValueError('invalid UI rectangle')


def validate_device_evidence(name: str, args: dict, details: dict) -> dict:
    """Validate every field before preserving a native result in trusted history.

    Native code owns actual platform reads, launchable inventory and UI freshness.
    This boundary rejects fabricated schema/claims; verification compares readings
    to the exact request rather than trusting a native 'matched' string.
    """
    if name in {'android.flashlight_set', 'android.rotation_set', 'android.brightness_set', 'android.screen_timeout_set'}:
        _object(details, ('observed',))
        observed = details['observed']
        if observed is not None:
            if name in {'android.flashlight_set', 'android.rotation_set'}:
                _object(observed, ('enabled',))
                _boolean(observed['enabled'])
            elif name == 'android.brightness_set':
                _object(observed, ('value', 'mode'))
                _integer(observed['value'], 0, 255)
                if observed['mode'] not in ('manual', 'automatic'):
                    raise ValueError('invalid brightness mode')
            else:
                _object(observed, ('milliseconds',))
                _integer(observed['milliseconds'], 1)
    elif name in {'android.volume_get', 'android.volume_set', 'android.volume_adjust'}:
        keys = ('stream', 'before', 'observed') if name.endswith('adjust') else ('stream', 'observed')
        _object(details, keys)
        if details['stream'] != args.get('stream', 'media'):
            raise ValueError('wrong volume stream evidence')
        _volume(details['observed'])
        if name.endswith('adjust'):
            _volume(details['before'])
    elif name == 'android.app_list':
        _bounded(details)
        _object(details, ('query', 'apps', 'truncated'))
        if details['query'] != args.get('query', ''):
            raise ValueError('wrong inventory query')
        _boolean(details['truncated'])
        apps = details['apps']
        if not isinstance(apps, list) or len(apps) > 128:
            raise ValueError('invalid app inventory')
        seen = set()
        for app in apps:
            _object(app, ('package', 'label'))
            _string(app['package'], 255, empty=False, pattern=PACKAGE_PATTERN)
            _string(app['label'], 256, empty=False)
            if app['package'] in seen:
                raise ValueError('duplicate app identity')
            seen.add(app['package'])
    elif name == 'android.ui.inspect':
        _object(details, ('snapshot',))
        _snapshot(details['snapshot'])
    else:
        # Navigation/Accessibility acknowledgements attest only platform acceptance.
        keys = {
            'android.settings_open': ('page',), 'android.app_open': ('package',),
            'android.ui.click': ('snapshot_id', 'node_id'),
            'android.ui.scroll': ('snapshot_id', 'node_id', 'direction'),
            'android.ui.set_text': ('snapshot_id', 'node_id'),
            'android.ui.global_action': ('action',),
        }[name]
        _object(details, (*keys, 'accepted'))
        if details['accepted'] is not True or any(details[key] != args[key] for key in keys):
            raise ValueError('wrong device acceptance evidence')
    return details


def verify_device_evidence(name: str, args: dict, details: dict) -> VerificationResult:
    try:
        validate_device_evidence(name, args, details)
    except (ValueError, KeyError, TypeError, UnicodeError):
        return VerificationResult(VerificationStatus.FAILED, {'reason': 'invalid native device evidence'})
    if name in {'android.volume_get', 'android.app_list', 'android.ui.inspect'}:
        if name == 'android.volume_get' and details['observed'] is None:
            return VerificationResult(VerificationStatus.UNAVAILABLE, {'reason': 'volume readback unavailable'})
        return VerificationResult(VerificationStatus.PASSED, {'reason': 'bounded structured observation obtained'})
    if 'observed' not in details:
        return VerificationResult(VerificationStatus.LIMITED, {'reason': 'Android accepted the request; downstream UI effect is not verified'})
    observed = details['observed']
    if observed is None or (name == 'android.volume_adjust' and details['before'] is None):
        return VerificationResult(VerificationStatus.LIMITED, {'reason': 'command accepted; state readback unavailable'})
    if name in {'android.flashlight_set', 'android.rotation_set'}:
        matched = observed['enabled'] == args['enabled']
    elif name == 'android.brightness_set':
        matched = observed['mode'] == 'manual' and observed['value'] == (args['percentage'] * 255 + 50) // 100
    elif name == 'android.screen_timeout_set':
        matched = observed['milliseconds'] == args['milliseconds']
    elif name == 'android.volume_set':
        matched = observed['index'] == observed['min_index'] + (args['percentage'] * (observed['max_index'] - observed['min_index']) + 50) // 100
    else:
        before = details['before']
        direction = args['direction']
        if before['max_index'] != observed['max_index'] or before['min_index'] != observed['min_index']:
            matched = False
        elif direction in {'mute', 'unmute'}:
            matched = observed['muted'] == (direction == 'mute')
        else:
            expected = max(before['min_index'], min(before['max_index'], before['index'] + (1 if direction == 'up' else -1)))
            matched = observed['index'] == expected
    return VerificationResult(VerificationStatus.PASSED if matched else VerificationStatus.FAILED,
                              {'reason': 'state readback matched request' if matched else 'state readback did not match request'})
