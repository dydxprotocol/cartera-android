#/bin/sh

# This script gets the wallet ids from the web3modal api

json_file_path="../cartera/src/main/res/raw/wc_modal_ids.json"

# create a tmp file path
tmp_file_path="/tmp/wc_ids.json"

all_wc_ids = ""

# loop through the pages
for i in {1..5} 
do
    curl -H "Host: api.web3modal.org" -H "accept: */*" -H "content-type: application/json" -H "x-sdk-version: swift-1.20.3" -H "accept-language: en-US,en;q=0.9" \
        -H "x-project-id: 47559b2ec96c09aed9ff2cb54a31ab0e" -H "user-agent: CarteraExample/1 CFNetwork/1568.100.1 Darwin/24.1.0" -H "x-sdk-type: wcm" -H "referer: dYdX" \
        "https://api.web3modal.org/getWallets?entries=100&page=$i&platform=android" > $tmp_file_path

    # get the wallet ids
    wc_ids=$(jq '.data[].id' $tmp_file_path)
    all_wc_ids="$all_wc_ids $wc_ids"
done

# replace spaces with commas 
all_wc_ids=$(echo $all_wc_ids | tr ' ' ',')

# insert a newline after each comma
all_wc_ids=$(echo $all_wc_ids | sed 's/,/,\n/g')


echo "[ $all_wc_ids ]" > $json_file_path

# remove the tmp file
rm $tmp_file_path