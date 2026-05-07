{
  description = "twr2 development and research verification shell";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.11";
  };

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; };
      jdk = pkgs.jdk21;
      researchTools = with pkgs; [
        elan
        jq
        nodejs_22
        poppler-utils
        python3
        tlaplus18
      ];
    in
    {
      devShells.${system} = {
        default = pkgs.mkShell {
          buildInputs = [
            jdk
            (pkgs.gradle.override { java = jdk; })
          ] ++ researchTools;

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
        };

        r1 = pkgs.mkShell {
          buildInputs = with pkgs; [
            coreutils
            elan
            jq
            nodejs_22
            python3
          ];

          shellHook = ''
            export PROJECT_ROOT="$(pwd)"
            export FLOWCTL="$PROJECT_ROOT/.flow/bin/flowctl"
            export PATH="$PROJECT_ROOT/.flow/bin:$PATH"
            echo "fwr dev shell ready  (node $(node -v), elan $(elan --version 2>/dev/null || echo 'installed'), flowctl $(flowctl detect --json 2>/dev/null | jq -r 'if .success then "ready" else "not ready" end' 2>/dev/null || echo 'not ready'))"
          '';
        };
      };
    };
}
