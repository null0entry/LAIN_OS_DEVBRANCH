from __future__ import annotations

import errno
import hashlib
import os
import shutil
import tempfile
from pathlib import Path
from typing import Any, Callable

from lain.capabilities.registry import DEFAULT_REGISTRY
from lain.config import RuntimeConfig
from lain.errors import ErrorCode, LainError
from lain.execution.models import ExecutionOutcome
from lain.security.paths import resolve_allowed_path


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _failure_from_error(error: LainError) -> ExecutionOutcome:
    return ExecutionOutcome.failure(error.code.value, error.message, **error.details)


def _validate_destination_parent(destination: Path) -> None:
    parent = destination.parent
    if not parent.exists() or not parent.is_dir():
        raise LainError(ErrorCode.ARGUMENT_INVALID, "destination parent directory does not exist")


def _publish_exclusive_copy(temp_path: Path, destination: Path) -> None:
    try:
        fd = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError as exc:
        raise LainError(ErrorCode.DESTINATION_EXISTS, "destination already exists") from exc
    try:
        with temp_path.open("rb") as source, os.fdopen(fd, "wb") as target:
            shutil.copyfileobj(source, target)
            target.flush()
            os.fsync(target.fileno())
    except Exception:
        try:
            destination.unlink()
        except FileNotFoundError:
            pass
        raise


def _publish_temp(temp_path: Path, destination: Path, *, overwrite: bool) -> None:
    if overwrite:
        os.replace(temp_path, destination)
        return

    link = getattr(os, "link", None)
    try:
        if link is None:
            _publish_exclusive_copy(temp_path, destination)
            return
        try:
            link(temp_path, destination)
        except FileExistsError as exc:
            raise LainError(ErrorCode.DESTINATION_EXISTS, "destination already exists") from exc
        except OSError as exc:
            unsupported_link_errors = {errno.EACCES, errno.EPERM, errno.EXDEV}
            if hasattr(errno, "EOPNOTSUPP"):
                unsupported_link_errors.add(errno.EOPNOTSUPP)
            if hasattr(errno, "ENOTSUP"):
                unsupported_link_errors.add(errno.ENOTSUP)
            if exc.errno not in unsupported_link_errors:
                raise
            _publish_exclusive_copy(temp_path, destination)
    finally:
        try:
            temp_path.unlink()
        except FileNotFoundError:
            pass


def _temp_file_in(directory: Path) -> tuple[int, Path]:
    fd, raw = tempfile.mkstemp(prefix=".lain-", suffix=".tmp", dir=directory)
    return fd, Path(raw)


def execute_file_write_text(arguments: dict[str, Any], config: RuntimeConfig) -> ExecutionOutcome:
    try:
        args = DEFAULT_REGISTRY.get("file.write_text").validate_arguments(arguments)
        destination = resolve_allowed_path(args["path"], config.allowed_roots)
        _validate_destination_parent(destination)
        if destination.exists() and not args["overwrite"]:
            raise LainError(ErrorCode.DESTINATION_EXISTS, "destination already exists")
        content = args["content"].encode("utf-8")
        if len(content) > config.max_text_write_bytes:
            raise LainError(
                ErrorCode.ARGUMENT_INVALID,
                "text write exceeds configured byte limit",
                details={"size": len(content), "limit": config.max_text_write_bytes},
            )
        fd, temp_path = _temp_file_in(destination.parent)
        try:
            with os.fdopen(fd, "wb") as handle:
                handle.write(content)
                handle.flush()
                os.fsync(handle.fileno())
            _publish_temp(temp_path, destination, overwrite=args["overwrite"])
        except Exception:
            try:
                temp_path.unlink()
            except FileNotFoundError:
                pass
            raise
        digest = sha256_file(destination)
        return ExecutionOutcome.success(path=str(destination), size=len(content), sha256=digest)
    except LainError as exc:
        return _failure_from_error(exc)
    except (OSError, UnicodeError) as exc:
        return ExecutionOutcome.failure(ErrorCode.EXECUTION_FAILED.value, "filesystem write failed", exception=type(exc).__name__)


def _copy_to_temp(source: Path, destination: Path) -> Path:
    fd, temp_path = _temp_file_in(destination.parent)
    os.close(fd)
    try:
        shutil.copy2(source, temp_path)
        with temp_path.open("rb") as handle:
            os.fsync(handle.fileno())
        return temp_path
    except Exception:
        try:
            temp_path.unlink()
        except FileNotFoundError:
            pass
        raise


def _prepare_source_destination(arguments: dict[str, Any], config: RuntimeConfig, capability: str) -> tuple[dict[str, Any], Path, Path]:
    args = DEFAULT_REGISTRY.get(capability).validate_arguments(arguments)
    source = resolve_allowed_path(args["source"], config.allowed_roots, must_exist=True)
    destination = resolve_allowed_path(args["destination"], config.allowed_roots)
    if not source.is_file():
        raise LainError(ErrorCode.ARGUMENT_INVALID, "source must be a regular file")
    _validate_destination_parent(destination)
    if source == destination:
        raise LainError(ErrorCode.ARGUMENT_INVALID, "source and destination must differ")
    if destination.exists() and not args["overwrite"]:
        raise LainError(ErrorCode.DESTINATION_EXISTS, "destination already exists")
    return args, source, destination


def execute_file_copy(arguments: dict[str, Any], config: RuntimeConfig) -> ExecutionOutcome:
    try:
        args, source, destination = _prepare_source_destination(arguments, config, "file.copy")
        before_hash = sha256_file(source)
        before_size = source.stat().st_size
        temp_path = _copy_to_temp(source, destination)
        _publish_temp(temp_path, destination, overwrite=args["overwrite"])
        after_hash = sha256_file(destination)
        return ExecutionOutcome.success(
            source=str(source),
            destination=str(destination),
            size=before_size,
            sha256_before=before_hash,
            sha256_after=after_hash,
        )
    except LainError as exc:
        return _failure_from_error(exc)
    except OSError as exc:
        return ExecutionOutcome.failure(ErrorCode.EXECUTION_FAILED.value, "filesystem copy failed", exception=type(exc).__name__)


def execute_file_move(arguments: dict[str, Any], config: RuntimeConfig) -> ExecutionOutcome:
    try:
        args, source, destination = _prepare_source_destination(arguments, config, "file.move")
        before_hash = sha256_file(source)
        before_size = source.stat().st_size
        temp_path = _copy_to_temp(source, destination)
        _publish_temp(temp_path, destination, overwrite=args["overwrite"])
        published_hash = sha256_file(destination)
        if published_hash != before_hash:
            try:
                destination.unlink()
            except OSError:
                pass
            raise LainError(ErrorCode.VERIFICATION_FAILED, "move destination did not match source before source removal")
        source.unlink()
        return ExecutionOutcome.success(
            source=str(source),
            destination=str(destination),
            size=before_size,
            sha256_before=before_hash,
            sha256_after=published_hash,
        )
    except LainError as exc:
        return _failure_from_error(exc)
    except OSError as exc:
        return ExecutionOutcome.failure(ErrorCode.EXECUTION_FAILED.value, "filesystem move failed", exception=type(exc).__name__)
