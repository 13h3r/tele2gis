# Telegram Bot for 2gis

This is a very simple telegram bot for 2gis. The bot is available - [here](http://telegram.me/two_gis_bot)

# Features
Ask for an organization and get the list of organizations with their addresses.

# Technologies
The bot is fully asynchronous and non-blocking. Akka-stream and akka-http used.

# How to run
Prepare config
```
web-api.key = <YOUR API KEY>
web-api.host = <2GIS API HOST>
telegram.token = <TELEGRAM TOKEN>
```

Build `.env` file 
```
scripts/convert_config.sh <your config> > .env
```

Run docker image
```
docker run --rm -i -t --env-file .env 13h3r/bottele
```

# How to build

```
sbt docker
```

