#!/bin/bash

STTY_ORIG=$(stty -g)

stty raw -echo

trap "stty $STTY_ORIG" EXIT

java -cp ./build/libs/apple1emu.jar a1em.Apple1 
