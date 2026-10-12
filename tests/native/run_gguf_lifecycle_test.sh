#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
build_dir="$(mktemp -d "${TMPDIR:-/tmp}/lain-native-lifecycle.XXXXXX")"
trap 'rm -rf -- "$build_dir"' EXIT
read -r -a compiler_flags <<< "${CXXFLAGS:-}"

"${CXX:-c++}" -std=c++17 -Wall -Wextra -Werror -pthread "${compiler_flags[@]}" \
    -I "$repo_root/tests/native/stubs" \
    "$repo_root/tests/native/gguf_lifecycle_test.cpp" \
    "$repo_root/android/app/src/main/cpp/lain_gguf_probe.cpp" \
    -o "$build_dir/gguf_lifecycle_test"

"$build_dir/gguf_lifecycle_test"
