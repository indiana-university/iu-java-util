#!/usr/bin/bash

# Use getopt to parse arguments
# https://stackoverflow.com/questions/192249/how-do-i-parse-command-line-arguments-in-bash
# https://stackoverflow.com/questions/402377/using-getopts-in-bash-shell-script-to-get-long-and-short-command-line-options
# https://stackoverflow.com/questions/16483119/an-example-of-how-to-use-getopts-in-bash
# NOTE: This requires GNU getopt.  On Mac OS X and FreeBSD, you have to install this
# separately; see below.
TEMP=$(getopt -s bash -o vent:a:c:u:o: --long verbose,ephemeral,no-builder,type:,algorithm:,encyrption:,use:,ops: \
              -n 'jwk' -- "$@")

if [ $? != 0 ] ; then echo "Terminating..." >&2 ; exit 1 ; fi

# Note the quotes around '$TEMP': they are essential!
eval set -- "$TEMP"

VERBOSE=false
EPHEMERAL=false
NO_BUILDER=false
TYPE=NONE
ALGORITHM=NONE
ENCRYPTION=NONE
OPS=NONE
while true; do
  echo "Processing $1"
  echo "Value is $2"
  case "$1" in
    -v | --verbose ) VERBOSE=true; shift ;;
    -e | --ephemeral ) EPHEMERAL=true; shift ;;
    -n | --no-builder ) NO_BUILDER=true; shift ;;
    -t | --type ) TYPE="$2"; shift 2 ;;
    -a | --algorithm ) ALGORITHM="$2"; shift 2 ;;
    -c | --encyrption ) ENCRYPTION="$2"; shift 2 ;;
    -u | --use ) USE="$2"; shift 2 ;;
    -o | --ops ) OPS="$2"; shift 2 ;;
    -- ) shift; break ;;
    * ) break ;;
  esac
done
# echo the command line that will be run before running it
echo "java --module-path \"$(pwd)/crypt/target/iu-java-crypt-7.0.2-SNAPSHOT.jar;$(pwd)/client/target/iu-java-client-7.0.2-SNAPSHOT.jar;$(pwd)/base/target/iu-java-base-7.0.2-SNAPSHOT.jar;C:/Users/anderjak/.m2/repository/jakarta/json/jakarta.json-api/2.1.3/jakarta.json-api-2.1.3.jar;C:/Users/anderjak/.m2/repository/org/eclipse/parsson/parsson/1.1.5/parsson-1.1.5.jar\" -m iu.util.crypt/edu.iu.crypt.JwkTool $VERBOSE $EPHEMERAL $NO_BUILDER $TYPE $ALGORITHM $ENCRYPTION $USE \"$OPS\""

# Use arguments to decide what options to pass to a java program inside a jar file
java --module-path "$(pwd)/crypt/target/iu-java-crypt-7.0.2-SNAPSHOT.jar;$(pwd)/client/target/iu-java-client-7.0.2-SNAPSHOT.jar;$(pwd)/base/target/iu-java-base-7.0.2-SNAPSHOT.jar;C:/Users/anderjak/.m2/repository/jakarta/json/jakarta.json-api/2.1.3/jakarta.json-api-2.1.3.jar;C:/Users/anderjak/.m2/repository/org/eclipse/parsson/parsson/1.1.5/parsson-1.1.5.jar" -m iu.util.crypt/edu.iu.crypt.JwkTool $VERBOSE $EPHEMERAL $NO_BUILDER $TYPE $ALGORITHM $ENCRYPTION $USE "$OPS"
