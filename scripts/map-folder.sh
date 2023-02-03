#!/bin/sh

if [ "$#" -ne 3 ]; then
    echo "USAGE ./map-folder.sh INPUT_FOLDER MAPPING_FILE OUTPUT_FOLDER"
    echo ""
    echo "INPUT_FOLDER can contain the prefix of the flat files"
    echo "MAPPING_FILE is an XML file created by XMLMaker after saving a mapping"
    echo "OUTPUT_FOLDER is the path to which the generated xml should be put in"
    exit 1;
fi

INPUT_FOLDER=$1
MAPPING_FILE=$2
OUTPUT_FOLDER=$3

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

for FILE in ${INPUT_FOLDER}*;
do
    FILENAME=$(basename -- "$FILE")
    ${SCRIPT_DIR}/../target/xmlMakerFlattener/bin/xmlmaker \
        -flatfiles ${FILE} \
        -mapping ${MAPPING_FILE} \
        -o "${OUTPUT_FOLDER}/$(echo "${FILENAME}" | sed 's/[tc]sv/xml/g')"
done;