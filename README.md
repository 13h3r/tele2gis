# Telegram Bot for 2gis

This is very simple telegram bot for 2gis. The bot available - [here](http://telegram.me/two_gis_bot)

# Features
Ask for an organization and get list of organizations with their addresses.

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

