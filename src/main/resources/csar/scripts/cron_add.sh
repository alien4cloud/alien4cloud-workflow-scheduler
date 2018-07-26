#!/bin/bash

# ID Generation
genid() {
  local id=0
  if [ -f $HOME/.cron.id ]; then
    id=$(< $HOME/.cron.id)
    id=$(expr $id + 1);
  fi
  echo $id > $HOME/.cron.id
  return $id
}

# Ensure exclusive access
exec 200> $HOME/.cron.lock || exit 1
flock 200 || exit 1

# Generate TASK ID
genid
export TASKID="TASK-$?"

# Create the modification script
SCRIPT=$(mktemp --tmpdir cronedit.XXXXXXXX.sh)

cat <<EOF > $SCRIPT
#!/bin/bash
TMP=\$(mktemp --tmpdir crontab.XXXXXXXX)
crontab -l > \$TMP
echo '${expression} ${command} # [$TASKID]' >> \$TMP
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