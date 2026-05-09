let
  pkgs = import (builtins.fetchTarball {
    url = "https://github.com/NixOS/nixpkgs/archive/nixos-25.11.tar.gz";
    sha256 = "1lmn8dicfwmsfdaiw18xjjys78bal6yjy3a41j02my7kw0wlb76a";
  }) {};
  jdk = pkgs.jdk21;
in
pkgs.mkShell {
  buildInputs = [
    jdk
    (pkgs.gradle.override { java = jdk; })
    pkgs.tlaplus18
    pkgs.elan
    pkgs.python3
    pkgs.nodejs_22
    pkgs.poppler-utils
    pkgs.jq
    pkgs.git
  ];

  JAVA_HOME = "${jdk.home}";

  shellHook = ''
    export PROJECT_ROOT="$(pwd)"
    export FLOWCTL="$PROJECT_ROOT/.flow/bin/flowctl"
    export PATH="$PROJECT_ROOT/.flow/bin:$PATH"

    echo "twr dev environment"
    echo "  Java:   $(java -version 2>&1 | head -1)"
    echo "  Gradle: $(gradle --version 2>&1 | grep '^Gradle' || true)"
    echo "  Python: $(python3 --version 2>/dev/null || true)"
    echo "  Node:   $(node --version 2>/dev/null || true)"
    echo "  Lean:   $(lean --version 2>/dev/null | head -1 || true)"
    echo "  Flow:   $(flowctl detect --json 2>/dev/null | jq -r 'if .success then "ready" else "not ready" end' 2>/dev/null || echo 'not ready')"
  '';
}
