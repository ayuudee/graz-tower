{ pkgs ? import <nixpkgs> {} }:

let
  jdk = pkgs.jdk21;
in
pkgs.mkShell {
  packages = [
    jdk
    (pkgs.gradle.override { java = jdk; })
    pkgs.tlaplus18
    pkgs.lean4
    pkgs.poppler-utils
    pkgs.jq
  ];

  JAVA_HOME = "${jdk.home}";

  shellHook = ''
    echo "twr dev environment"
    echo "  Java:   $(java -version 2>&1 | head -1)"
    echo "  Gradle: $(gradle --version 2>&1 | grep '^Gradle' || true)"
    echo "  Lean:   $(lean --version 2>/dev/null | head -1 || true)"
  '';
}
