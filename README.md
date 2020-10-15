# Rockberte's SmartThings GitHub Repo
This is a collection of anything needed to bring some not native supported smart home devices to work with Samsung SmartThings.

## Device Handler
You can find the following device handlers in this repo:

[HEIMAN Smart Combustible Gas Sensor HS1CG](#heiman-smart-combustible-gas-sensor-hs1cg)

### HEIMAN Smart Combustible Gas Sensor HS1CG
Samsung SmartThings Z-Wave device handler for the HEIMAN Smart Combustible Gas Sensor HS1CG.

The device handler uses the smart things "Smoke Detector" capability as no "Gas Detector" or "Gas Sensor" capability is currently available in the SmartThings capabilities reference.

It just handles Z-Wave gas alarms as defined by the standard.

The sensor seems to send a "Gas detected" event when pressing the test button immediately followed by the idle state (clear). It is not using the "Gas alarm test" state. So you'll never see a test alarm.

The device handler handles the NotificationReport that is send from the sensor in case of a alarm or test alarm.

In addition it supports the "Health Check" capability by reading the devices version information every 8 hours to ensure the device isn't shown OFFLINE.
