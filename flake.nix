{
  description = "Language Server for Java using the Java compiler API";
  inputs.nixpkgs.url = "github:nixos/nixpkgs/nixos-23.05";

  outputs = { self, nixpkgs, ... }:
    let
      pkgs = import nixpkgs { system = "x86_64-linux"; };
    in
    
    {
      devShells.x86_64-linux.default = pkgs.mkShell {
        packages = [
          pkgs.graalvm19-ce
        ];
      };

      packages.x86_64-linux.default = pkgs.callPackage ./java-language-server.nix {
        lib = pkgs.lib;
        jdk = pkgs.graalvm19-ce;
      }; 
    };
}
