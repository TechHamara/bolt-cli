#!/bin/bash

# NOTE: This script is only supposed to be run by Bolt's CI (GH Actions)

set -e

pat="$1"

# @params:
#   $1 -> file path in GH repo relative to exe/
function fetch() {
  echo "$(curl -s -u TechHamara:$pat https://api.github.com/repos/TechHamara/bolt-pack/contents/exe/$1)"
}

# @params:
#   $1 -> file path in GH repo relative to exe/
#   $2 -> base64 encoded content of the file that is to be uploaded
#   $3 -> SHA of the file that is to be uploaded
function upload() {
  if  [ ! -d build ]; then
    mkdir build
  fi

  echo "Writing curl.args for $1..."
  cat > build/curl.args <<- EOF
{"message":"Update $1","content":"$2","sha":$3}
EOF

  echo "Uploading $1..."
  curl -X PUT -u TechHamara:"$pat" \
    -H "Accept: application/vnd.github.v3+json" \
    https://api.github.com/repos/TechHamara/bolt-pack/contents/exe/$1 \
    -d @build/curl.args
}

# @params
#   $1 -> is Linux or Windows?
#   $2 -> file to be encoded
function encode() {
  if (( "$1" )); then
    echo "$(base64 -w0 $2)"
  else
    echo "$(base64 -i $2)"
  fi
}

if [ "$OS" = "Windows_NT" ]; then
  res=$(fetch "win")
  exeSha=$(echo "$res" | jq '.[0].sha')
  swapSha=$(echo "$res" | jq '.[1].sha')

  upload "win/bolt.exe" "$(encode true build/bin/bolt.exe)" "$exeSha"
  upload "win/swap.exe" "$(encode true build/bin/swap.exe)" "$swapSha"
else
  case $(uname -sm) in
  "Darwin x86_64")
    res=$(fetch "mac")
    exeSha=$(echo "$res" | jq '.[0].sha')
    upload "mac/bolt" "$(encode false build/bin/bolt)" "$exeSha"
    ;;
  *)
    res=$(fetch "linux")
    exeSha=$(echo "$res" | jq '.[0].sha')
    upload "linux/bolt" "$(encode true build/bin/bolt)" "$exeSha"
    ;;
  esac
fi
