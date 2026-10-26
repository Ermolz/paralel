#!/usr/bin/env bash
set -euo pipefail

version=4.1.6
archive_sha256=44da277b8cdc234e71c62473305a09d63f4dcca292ca40335aab7c4bf0e6a566
prefix="${OPENMPI_HOME:-$HOME/openmpi}"
cache="$HOME/.cache/paralel"
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
jobs="${BUILD_JOBS:-4}"

for tool in gcc g++ make javac jar curl tar sha256sum; do
    command -v "$tool" >/dev/null || { echo "Missing tool: $tool" >&2; exit 1; }
done
export JAVA_HOME="${JAVA_HOME:-$(dirname -- "$(dirname -- "$(readlink -f -- "$(command -v javac)")")")}"
export PATH="$JAVA_HOME/bin:$PATH"
[[ -f "$JAVA_HOME/include/jni.h" ]] || { echo "JDK headers missing in $JAVA_HOME" >&2; exit 1; }
[[ "$prefix" != *[[:space:]]* && "$cache" != *[[:space:]]* ]] || {
    echo 'OpenMPI installation and build paths must not contain whitespace.' >&2; exit 1;
}
mkdir -p "$cache" "$project_dir/logs" "$project_dir/lib"

if [[ ! -f "$prefix/lib/mpi.jar" || ! -x "$prefix/bin/mpirun" ]] ||
   ! "$prefix/bin/mpirun" --version | head -n 1 | grep -Fq " $version"; then
    archive="$cache/openmpi-$version.tar.gz"
    if [[ ! -f "$archive" ]]; then
        curl -fL --retry 2 "https://download.open-mpi.org/release/open-mpi/v4.1/openmpi-$version.tar.gz" -o "$archive.part"
        mv -- "$archive.part" "$archive"
    fi
    printf '%s  %s\n' "$archive_sha256" "$archive" | sha256sum --check
    if [[ ! -f "$cache/openmpi-$version/configure" ]]; then
        tar -xzf "$archive" -C "$cache"
    fi
    cd -- "$cache/openmpi-$version"
    ./configure \
        --prefix="$prefix" \
        --disable-mpi-fortran \
        --disable-mpi-cxx \
        --disable-oshmem \
        --without-verbs \
        --without-slurm \
        --without-lsf \
        --without-tm \
        --enable-mpi-java \
        --with-jdk-dir="$JAVA_HOME" \
        > "$project_dir/logs/config.out" 2>&1
    echo 'Configuration complete. Building OpenMPI...'
    make -j "$jobs" > "$project_dir/logs/make.out" 2>&1
    echo 'Build complete. Installing OpenMPI...'
    make install > "$project_dir/logs/install.out" 2>&1
fi

test -s "$prefix/lib/mpi.jar"
test -s "$prefix/lib/libmpi_java.so"
cp -- "$prefix/lib/mpi.jar" "$project_dir/lib/mpi.jar"
maven_artifact="$project_dir/lib/maven/org/open-mpi/mpi/$version"
mkdir -p "$maven_artifact"
cp -- "$prefix/lib/mpi.jar" "$maven_artifact/mpi-$version.jar"
for artifact_file in "$maven_artifact/mpi-$version.jar" "$maven_artifact/mpi-$version.pom"; do
    sha1sum "$artifact_file" | cut -d ' ' -f 1 > "$artifact_file.sha1"
done
if [[ -f "$cache/openmpi-$version/LICENSE" ]]; then
    cp -- "$cache/openmpi-$version/LICENSE" "$project_dir/lib/OPENMPI-LICENSE.txt"
fi
mkdir -p "$HOME/.config/paralel"
{
    printf 'export JAVA_HOME=%q\n' "$JAVA_HOME"
    printf 'export OPENMPI_HOME=%q\n' "$prefix"
    printf '%s\n' 'export PATH="$OPENMPI_HOME/bin:$JAVA_HOME/bin:$PATH"'
    printf '%s\n' 'export LD_LIBRARY_PATH="$OPENMPI_HOME/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"'
} > "$HOME/.config/paralel/env.sh"
if ! grep -Fq '# paralel: OpenMPI environment' "$HOME/.bashrc"; then
    printf '\n%s\n%s\n' '# paralel: OpenMPI environment' 'source "$HOME/.config/paralel/env.sh"' >> "$HOME/.bashrc"
fi
if [[ ! -e "$HOME/hostfile" ]]; then
    cp -- "$project_dir/hostfile" "$HOME/hostfile"
fi
echo "Installed OpenMPI $version in $prefix"
echo "Java library copied to $project_dir/lib/mpi.jar"
