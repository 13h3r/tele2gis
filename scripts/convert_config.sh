ARGS=`cat $1| grep '=' | awk '{print "-D"$1"="$3" "}' | xargs echo`
echo JAVA_D_OPTIONS=-Xmx256m $ARGS
echo MAIN_CLASS=bottele.Bot