#!/bin/bash
# Rule Signing Helper
# Usage: ./sign_rule.sh '{"senderPattern":".*","forwardTarget":"+1234567890","action":"FORWARD"}'

if [ -z "$1" ]; then
    echo "Usage: $0 '<rule_json>'"
    exit 1
fi

RULE_JSON="$1"
PRIVATE_KEY="rule_signing_private.pem"

if [ ! -f "$PRIVATE_KEY" ]; then
    echo "Error: $PRIVATE_KEY not found"
    exit 1
fi

SIGNATURE=$(echo -n "$RULE_JSON" | openssl dgst -sha256 -sign "$PRIVATE_KEY" | base64 -w 0)

echo "Rule JSON:"
echo "$RULE_JSON"
echo ""
echo "Signature:"
echo "$SIGNATURE"
echo ""
echo "FCM Payload:"
cat <<EOF
{
  "type": "UPDATE_RULES",
  "ruleId": "rule_$(date +%s)",
  "version": "1",
  "ruleJson": "$RULE_JSON",
  "signature": "$SIGNATURE"
}
EOF
