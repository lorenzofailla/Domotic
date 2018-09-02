#!/bin/sh
# Mobile Broadband Startup Service script v0.1 alpha by The Fan Club - 
# April 2012 acts as startup service script for nmcli to fire up Mobile 
# Broadband Connections NOTE: Use the name of the Mobile Connection in 
# the Network Manager as the 'id' USAGE: start|stop|status
#
### BEGIN INIT INFO
# Provides:          domotic
# Required-Start:    $ALL
# Required-Stop:     $ALL
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6 
# X-Interactive:     true
# Short-Description: Domotic for linux devices
# Description:       Domotic for linux devices
#  This script will manage Domotic for linux devices.
### END INIT INFO

PID_PATH_NAME=/tmp/domotic.pid
SERVICE_NAME="Domotic for linux devices"
LOG_FILE_NAME=/var/log/domotic.log
JAR_PATH=/usr/local/bin/domotic.jar

NAME="domotic.sh" DESC="Autostart 'Domotic for linux devices' at startup"
test -x $DAEMON || exit 0 

case "$1" in
	start)

           if [ ! -f $PID_PATH_NAME ]; then

    	       echo "Starting $SERVICE_NAME ..."

               nohup java -jar $JAR_PATH > $LOG_FILE_NAME &
               echo $! > $PID_PATH_NAME

               echo "Done."

           else

               echo "$SERVICE_NAME is already running."

           fi
	;;

	stop)

            if [ -f $PID_PATH_NAME ]; then

                echo "Stopping $SERVICE_NAME ..."
                PID=$(cat $PID_PATH_NAME)
                kill $PID;
                rm $PID_PATH_NAME

                echo "Done."

            else

                echo "$SERVICE_NAME is not currently running."

            fi

	;;

    restart)

            if [ -f $PID_PATH_NAME ]; then

                echo "Restarting $SERVICE_NAME ..."
                PID=$(cat $PID_PATH_NAME)
                kill $PID;
                rm $PID_PATH_NAME

                nohup java -jar $JAR_PATH > $LOG_FILE_NAME &
                echo $! > $PID_PATH_NAME
                echo "Done."

            else

                echo "$SERVICE_NAME is not currently running."

            fi

	;;

	status)

            if [ -f $PID_PATH_NAME ]; then

                echo "$SERVICE_NAME is currently running."

            else

                echo "$SERVICE_NAME is not currently running."

            fi
	;;

	*)
	   echo "$SERVICE_NAME"
	   echo $"Usage: $0 {start|stop|status|restart}"
	   exit 1 

esac
exit 0
