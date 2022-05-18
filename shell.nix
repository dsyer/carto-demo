with import <nixpkgs> { };

mkShell {

  name = "env";
  buildInputs = [
    python3Packages.python
    python3Packages.venvShellHook
    nodejs wasmtime wabt
  ];

  venvDir = "./.venv";
  postVenvCreation = ''
    unset SOURCE_DATE_EPOCH
    pip install wasmtime
    pip install pyclibrary
    pip install urllib3
  '';

  postShellHook = ''
    # allow pip to install wheels
    unset SOURCE_DATE_EPOCH
  '';

}