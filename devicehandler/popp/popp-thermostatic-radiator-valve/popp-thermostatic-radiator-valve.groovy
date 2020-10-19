/**
 *  POPP Thermostatic Radiator Valve
 *
 *  Copyright 2020 Bernd Brachmaier
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "POPP Thermostatic Radiator Valve", namespace: "rockberte", author: "Bernd Brachmaier") {
		capability "Switch"	
		capability "Temperature Measurement"
		capability "Thermostat Heating Setpoint"
		capability "Battery"

		command "lowerHeatingSetpoint"
		command "raiseHeatingSetpoint"

		fingerprint mfr:"0002", prod:"0115", model:"A010", deviceJoinName: "POPP Thermostatic Radiator Valve"
	}

	tiles {
		multiAttributeTile(name:"temperature", type:"generic", width:3, height:2, canChangeIcon: true) {
			tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
				attributeState("temperature", label:'${currentValue}°', icon: "st.alarm.temperature.normal",
					backgroundColors:[
							// Celsius
							[value: 0, color: "#153591"],
							[value: 7, color: "#1e9cbb"],
							[value: 15, color: "#90d2a7"],
							[value: 23, color: "#44b621"],
							[value: 28, color: "#f1d801"],
							[value: 35, color: "#d04e00"],
							[value: 37, color: "#bc2323"],
							// Fahrenheit
							[value: 40, color: "#153591"],
							[value: 44, color: "#1e9cbb"],
							[value: 59, color: "#90d2a7"],
							[value: 74, color: "#44b621"],
							[value: 84, color: "#f1d801"],
							[value: 95, color: "#d04e00"],
							[value: 96, color: "#bc2323"]
					]
				)
			}
		}

		standardTile("lowerHeatingSetpoint", "device.heatingSetpoint", width:2, height:1, inactiveLabel: false, decoration: "flat") {
			state "heatingSetpoint", action:"lowerHeatingSetpoint", icon:"st.thermostat.thermostat-left"
		}
		valueTile("heatingSetpoint", "device.heatingSetpoint", width:2, height:1, inactiveLabel: false, decoration: "flat") {
			state "heatingSetpoint", label:'${currentValue}° heat', backgroundColor:"#ffffff"
		}
		standardTile("raiseHeatingSetpoint", "device.heatingSetpoint", width:2, height:1, inactiveLabel: false, decoration: "flat") {
			state "heatingSetpoint", action:"raiseHeatingSetpoint", icon:"st.thermostat.thermostat-right"
		}

		valueTile("battery", "device.battery", inactiveLabel: false, width: 2, height: 2, decoration: "flat") {
			state "battery", label:'${currentValue}%\n battery', unit:"%"
		}

		main "temperature"
		details(["temperature", "lowerHeatingSetpoint", "heatingSetpoint", "raiseHeatingSetpoint"])
	}
}

def installed() {
	log.debug("${device.displayName} - installed()")
	// Configure device
	def cmds = [new physicalgraph.device.HubAction(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[zwaveHubNodeId]).format()),
			new physicalgraph.device.HubAction(zwave.manufacturerSpecificV2.manufacturerSpecificGet().format())]
	sendHubCommand(cmds)
	runIn(3, "initialize", [overwrite: true])  // Allow configure command to be sent and acknowledged before proceeding
}

def updated() {
	if(state.lastUpdated && (now() - state.lastUpdated) < 500 ) {
		return
	}
	log.debug("${device.displayName} - updated()")
	// TODO: this is a battery device that must be awake, consider this while updating the device handler
	// TODO: currently the device must be awake while we updated() gets called, otherwise we'll never initialize the device
	// If not set update ManufacturerSpecific data
	if (!getDataValue("manufacturer")) {
		sendHubCommand(new physicalgraph.device.HubAction(zwave.manufacturerSpecificV2.manufacturerSpecificGet().format()))
		runIn(2, "initialize", [overwrite: true])  // Allow configure command to be sent and acknowledged before proceeding
	} else {
		initialize()
	}
	state.lastUpdated = now()
}

def initialize() {
	log.debug("${device.displayName} - initialize()")
	unschedule()
	pollDevice()
}

// z-wave event handling
def parse(String description) {
	log.debug("parse() >> description: ${description}")
	def result = null
	if (description == "updated") {
	} else {
		// Command classes provided to parse
		// 0x80 = Battery
		// 0x72 = ManufacturerSpecific
		// 0x42 = ThermostatOperatingState
		// 0x43 = ThermostatSetpoint
		// 0x31 = SensorMultilevel
		// 0x84 = WakeUp
		def zwcmd = zwave.parse(description, [0x80:1, 0x72:2, 0x42:1, 0x43:2, 0x31:3, 0x84:2])
		if (zwcmd) {
			result = zwaveEvent(zwcmd)
		} else {
			log.debug("parse() >> ${device.displayName} couldn't parse ${description}")
		}
	}
	if (!result) {
		return []
	}
	return [result]
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	log.debug("${device.displayName} - BatteryReport received, value: ${cmd.batteryLevel}")
	def map = [name: "battery", unit: "%"]
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.descriptionText = "${device.displayName} has a low battery"
		map.isStateChange = true
	} else {
		map.value = cmd.batteryLevel
	}
	sendEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv3.SensorMultilevelReport cmd) {
	log.debug("${device.displayName} - SensorMultilevelReport received, value: ${cmd.sensorType}")
	if (cmd.sensorType == 1) {
		def map = [:]
		map.value = getTempInLocalScale(cmd.scaledSensorValue, cmd.scale == 1 ? "F" : "C")
		map.unit = getTemperatureScale()
		map.name = "temperature"    
		sendEvent(map)
	} else {
		log.warn("${device.displayName} - Unexpected sensorType received in SensorMultilevelReport: ${cmd.sensorType}")
	}
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd) {
	log.debug("${device.displayName} - ThermostatSetpointReport received, value: ${cmd.setpointType}")
	if(cmd.setpointType == 1) {
		def heatingSetpoint = getTempInDeviceScale("heatingSetpoint")
		if(state.pendingHeatingSetpoint == null && state.deviceHeatingSetpoint != cmd.scaledValue && heatingSetpoint != cmd.scaledValue) {        
			def cmdScale = cmd.scale == 1 ? "F" : "C"
			sendHeatingSetpointEvent(cmd.scaledValue, cmdScale, true)
		}
		state.deviceHeatingSetpoint = cmd.scaledValue
	} else {
		log.warn("${device.displayName} - Unexpected setpointType received in ThermostatSetpointReport: ${cmd.setpointType}")
	}	
	// So we can respond with same format
	state.size = cmd.size
	state.scale = cmd.scale
	state.precision = cmd.precision
	// Make sure return value is not result from above expression
	return 0
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	log.debug("${device.displayName} - ManufacturerSpecificReport received, value: ${cmd.setpointType}")
	if (cmd.manufacturerName) {
		updateDataValue("manufacturer", cmd.manufacturerName)
	}
	if (cmd.productTypeId) {
		updateDataValue("productTypeId", cmd.productTypeId.toString())
	}
	if (cmd.productId) {
		updateDataValue("productId", cmd.productId.toString())
	}
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd) {
	log.debug("${device.displayName} - WakeUpNotification received")
	sendEvent(descriptionText: "$device.displayName woke up", isStateChange: true)
	runIn(1, "sync")
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.warn("Unexpected zwave command ${cmd}")
}

// capabilities commands
def setHeatingSetpoint(degrees) {
	log.debug("${device.displayName} - setHeatingSetpoint(${degrees})")
	if (degrees) {
		state.heatingSetpoint = degrees.toDouble()
		runIn(2, "updateHeatingSetpoint", [overwrite: true])
	}
}

def on() {
	log.debug("${device.displayName} - on()")
	// TODO: should we switch back to the last "thermostatMode" set before off()
	if(state.thermostatMode == "heat")
		return
	state.thermostatMode = "heat" // TODO: use the "Thermostat Mode" capability and store this in the thermostatMode attribute
	getTempInLocalScale(state.lastHeatHeatingSetpoint, getDeviceScale())
	setHeatingSetpoint(getTempInLocalScale(4, "C"))
}

def off() {
	log.debug("${device.displayName} - off()")
	if(state.thermostatMode == "off")
		return
	state.thermostatMode = "off" // TODO: use the "Thermostat Mode" capability and store this in the thermostatMode attribute
	state.lastHeatHeatingSetpoint = getTempInDeviceScale("heatingSetpoint")
	setHeatingSetpoint(getTempInLocalScale(4, "C"))
}

// device handler commands
def raiseHeatingSetpoint() {
	log.debug("${device.displayName} - raising heating setpoint")
	// alterSetpoint(true, "heatingSetpoint")
}

def lowerHeatingSetpoint() {
	log.debug("${device.displayName} - lowering heating setpoint")
	// alterSetpoint(false, "heatingSetpoint")
}

def sync() {
	log.debug("${device.displayName} - Executing sync()")
	def cmds = []
	if(state.pendingHeatingSetpoint && state.pendingHeatingSetpoint != state.deviceHeatingSetpoint) {
		log.debug("${device.displayName} - setting new setpoint to ${state.pendingHeatingSetpoint}")
		cmds << zwave.thermostatSetpointV1.thermostatSetpointSet(setpointType: 1, scale: state.scale,
				precision: state.precision, scaledValue: state.pendingHeatingSetpoint)
		cmds << zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1)
	}
	cmds << zwave.wakeUpV1.wakeUpNoMoreInformation()
	state.pendingHeatingSetpoint = null
	sendHubCommand(cmds, 1500)
}

def enforceSetpointLimits(targetValue) {
	def locationScale = getTemperatureScale() 
	def minSetpoint = getTempInDeviceScale(4, "C")
	def maxSetpoint = getTempInDeviceScale(28, "C")
	targetValue = getTempInDeviceScale(targetValue, locationScale)
	// Enforce min/mix for setpoint
	if (targetValue > maxSetpoint) {
		targetValue = maxSetpoint
	} else if (targetValue < minSetpoint) {
		targetValue = minSetpoint
	}
	return targetValue
}

def updateHeatingSetpoint() {
	log.debug("${device.displayName} - updateHeatingSetpoint()")
	def heatingSetpoint = enforceSetpointLimits(state.heatingSetpoint) // returns heatingSetpoint in devices scale
	state.heatingSetpoint = null
	// update is only needed in case the radiators setpoint differs from the one to send
	def requiresEvent = false
	if(state.deviceHeatingSetpoint != heatingSetpoint) {
		state.pendingHeatingSetpoint = heatingSetpoint
		requiresEvent = true
	} else {
		requiresEvent = state.pendingHeatingSetpoint != null
		state.pendingHeatingSetpoint = null
	}
	sendHeatingSetpointEvent(heatingSetpoint, state.scale == 1 ? "F" : "C", true) 
}

def sendHeatingSetpointEvent(scaledValue, displayed) {
	sendHeatingSetpointEvent(scaledValue, state.scale == 1 ? "" : "")
}

def sendHeatingSetpointEvent(scaledValue, scale, displayed) {
	def setpoint = getTempInLocalScale(scaledValue, scale)
	def unit = getTemperatureScale()		
	sendEvent(name: "heatingSetpoint", value: setpoint, unit: unit, displayed: displayed)
}

def pollDevice() {
	log.debug("${device.displayName} - pollDevice()")
	def cmds = []
	cmds << new physicalgraph.device.HubAction(zwave.sensorMultilevelV2.sensorMultilevelGet().format()) // current temperature
	cmds << new physicalgraph.device.HubAction(zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1).format()) // current heatingSetpoint
	sendHubCommand(cmds)
}

// Get stored temperature from currentState in current local scale
def getTempInLocalScale(state) {
	def temp = device.currentState(state)
	if (temp && temp.value && temp.unit) {
		return getTempInLocalScale(temp.value.toBigDecimal(), temp.unit)
	}
	return 0
}

// get/convert temperature to current local scale
def getTempInLocalScale(temp, scale) {
	if (temp && scale) {
		def scaledTemp = convertTemperatureIfNeeded(temp.toBigDecimal(), scale).toDouble()
		return (getTemperatureScale() == "F" ? scaledTemp.round(0).toInteger() : roundC(scaledTemp))
	}
	return 0
}

def getTempInDeviceScale(state) {
	def temp = device.currentState(state)
	if (temp && temp.value && temp.unit) {
		return getTempInDeviceScale(temp.value.toBigDecimal(), temp.unit)
	}
	return 0
}

def getTempInDeviceScale(temp, scale) {
	if (temp && scale) {
		def deviceScale = (state.scale == 1) ? "F" : "C"
		return (deviceScale == scale) ? temp :
				(deviceScale == "F" ? celsiusToFahrenheit(temp).toDouble().round(0).toInteger() : roundC(fahrenheitToCelsius(temp)))
	}
	return 0
}

def getDeviceScale() {
	return (state.scale == 1) ? "F" : "C"
}

def roundC (tempC) {
	return (Math.round(tempC.toDouble() * 2))/2
}