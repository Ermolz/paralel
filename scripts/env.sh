#!/usr/bin/env bash
export OPENMPI_HOME="${OPENMPI_HOME:-$HOME/openmpi}"
export JAVA_HOME="${JAVA_HOME:-$(dirname -- "$(dirname -- "$(readlink -f -- "$(command -v javac)")")")}"
export PATH="$OPENMPI_HOME/bin:$JAVA_HOME/bin:$PATH"
export LD_LIBRARY_PATH="$OPENMPI_HOME/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
