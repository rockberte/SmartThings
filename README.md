# Rockberte's SmartThings GitHub Repo
This is a collection of anything needed to bring some smart home devices to work with Samsung SmartThings that are not natively supported.

## Device Handler for Z-Wave Devices
You can find the following device handlers in this repo:

- [HEIMAN Smart Combustible Gas Sensor HS1CG](#heiman-smart-combustible-gas-sensor-hs1cg)
- [POPP Thermostatic Radiator Valve POPE010101](#popp-thermostatic-radiator-valve-pope010101)

### HEIMAN Smart Combustible Gas Sensor HS1CG
Device handler for the HEIMAN Smart Combustible Gas Sensor HS1CG.

The device handler uses the smart things "Smoke Detector" capability as no "Gas Detector" or "Gas Sensor" capability is currently available in the SmartThings capabilities reference.

It just handles Z-Wave gas alarms as defined by the standard.

The sensor seems to send a "Gas detected" event when pressing the test button immediately followed by the idle state (clear). It is not using the "Gas alarm test" state. So you'll never see a test alarm.

The device handler handles the NotificationReport that is send from the sensor in case of an alarm or test alarm.

In addition it supports the "Health Check" capability by reading the devices version information every 8 hours to ensure the device isn't shown OFFLINE.

### POPP Thermostatic Radiator Valve POPE010101
Device handler for the POPP Thermostatic Radiator Valve POPE010101.

It supports the main capabilities of the POPP TRV which are "Thermostat Heating Setpoint", "Battery" and "Temperature Measurement".

This is a battery device that does not support FLIRS and therefore wakes up periodically. The device handler ensures that all changed settings are sent to the device after it woke up and performs the necessary handshake to avoid the "E5" error.

This device handler supports the new SmartThings application.