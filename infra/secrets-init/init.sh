#!/bin/sh
set -eu
if [ ! -s /secrets/cardforge.pan.hmac-key ]; then
 umask 077
 head -c 32 /dev/urandom | base64 > /secrets/cardforge.pan.hmac-key
fi
# The local non-root application user must be able to read this development key.
chmod 644 /secrets/cardforge.pan.hmac-key
