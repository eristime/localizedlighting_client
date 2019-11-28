# Localizedlighting client

This repository contains client to https://github.com/eristime/localizedlighting_server



## How to run

1. Open up project with Android studio.
2. Change clientToken in app/config.kt to correspond CLIENT_SECRET with the server.

## Activities:

### EntryActivity: 
The first screen user sees. Checks if NFC enabled.
### LoadingActivity:
"text/plain"-type NFC-triggers invoke this activity. Checks the server is up and logs in.
### MainActivity: 
- UI for adjusting lighting 
- Sends the requests to change switch levels to the server.
- Starts up a foreground service SwitchOccupiedService.kt to have the websocket connection open to the server
- Setting lighting to default level invokes EntryActivity