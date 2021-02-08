Weather Processor
====

A simple Babashka project to serve a chart of weather information.
The weather information is currently retrieved from [OpenWeatherMap](https://openweathermap.org) through
a cron job and curl.

On Linux with babashka installed, use `bb ./src/user.clj`

On Raspberry Pi 3 (armv7), with `bb32` script, use `./src/user.clj` (effectively: `java -Xmx32m -Xms32m -jar {babashka-jar} ./src/user.clj`)

## CRON

The cron is set up to ping on the hour.
```bash
0 * * * * /home/pi/weather/cron >> /home/pi/weather/weather.log && echo "" >> /home/pi/weather/weather.log 2>&1
```

### CRON Source

```bash
#!/usr/bin/env bash
API_KEY=xxxx
CITY=bloemfontein
curl -s "https://api.openweathermap.org/data/2.5/weather?q=${CITY}&type=like&appid=${API_KEY}"
```
