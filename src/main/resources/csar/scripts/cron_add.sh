#!/bin/bash

echo ${cronid} >> ENV

# Ensure exclusive access
exec 200> $HOME/.cron.lock || exit 1
flock 200 || exit 1

# Create the modification script
SCRIPT=$(mktemp --tmpdir cronedit.XXXXXXXX.sh)

cat <<EOF > $SCRIPT
#!/bin/bash
TMP=\$(mktemp --tmpdir crontab.XXXXXXXX)
crontab -l > \$TMP
echo '${expression} ${command} # <$cronid>' >> \$TMP
crontab \$TMP
CR=\$?
rm \$TMP
exit \$CR
EOF
chmod 755 $SCRIPT

# Now running it
if [ -v ${user} ]; then
	$SCRIPT
	CR=$?
else
	sudo -nu ${user} /bin/bash -c $SCRIPT
fi

# Clean script
rm $SCRIPT

exit $CR