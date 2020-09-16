# HEIMAN Gas Senser HS1CG SmartThings Device Handler
Samsung SmartThings Z-Wave device handler for the HEIMAN Smart Combustible Gas Senser HS1CG.

The device handler uses the smart things "Smoke Detector" capability as no "Gas Detector" or "Gas Sensor" capability is currently available in the SmartThings capabilities reference.

It just handles Z-Wave gas alarms as defined by the standard.

The sensor seems to send a "Gas detected" event when pressing the test button immediately followed by the idle state (clear). It is not using the "Gas alarm test" state. So you'll never see a test alarm.

The device handler currently just handles the NotificationReport that is send from the sensor in case of a alarm or test alarm.

In addition it supports the "Health Check" capability by reading the devices version information every 8 hours to ensure the device isn't shown OFFLINE.