/**
 *  Danfoss LC-13 based Thermostatic Radiator Valves (POPP, Devolo, etc.)
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
		capability "Configuration"
		capability "Battery"

		fingerprint mfr:"0002", prod:"0115", model:"A010", deviceJoinName: "POPP Thermostatic Radiator Valve"
		fingerprint mfr:"0002", prod:"0005", model:"0175", deviceJoinName: "Devolo Thermostatic Radiator Valve"
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
		
		standardTile("switch", "device.switch", height: 2, width: 2, decoration: "flat") {
			state "off", action:"on", label: "off", icon: "st.thermostat.heating-cooling-off", backgroundColor:"#ffffff"
			state "on", action:"off", label: "on", icon: "st.thermostat.heat", backgroundColor:"#00a0dc"
		}

		valueTile("heatingSetpoint", "device.heatingSetpoint", width:2, height:2, inactiveLabel: false, decoration: "flat") {
			state "heatingSetpoint", label:'${currentValue}° heat', backgroundColor:"#ffffff"
		}
		
		valueTile("battery", "device.battery", inactiveLabel: false, width: 2, height: 2, decoration: "flat") {
			state "battery", label:'${currentValue}%\n battery', unit:"%"
		}

		main "temperature"
		details(["temperature", "heatingSetpoint", "switch", "battery"])
	}

	preferences {
		input (
			title: "Wake up interval",
			description: "How often should your device automatically sync with the HUB.\nThe lower the value, the shorter the battery life.\n0 or 1-30 (in minutes, default: 5 minutes)",
			type: "paragraph",
			element: "paragraph"
		)
		
		input ( 
			name: "wakeUpInterval", 
			title: null, 
			type: "number", 
			range: "0..30", 
			defaultValue: 5, 
			required: false
		)
	}
}

def installed() {
	log.debug("${device.displayName} - installed()")
	// TODO: set initial states

	// configure device
	/* TODO: check whether this is needed or not */
	def cmds = []
	cmds << new physicalgraph.device.HubAction(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[zwaveHubNodeId]).format())
	sendHubCommand(cmds)

	pollDevice()
}

def updated() {
	if(state.lastUpdated && (now() - state.lastUpdated) < 500 ) {
		return
	}
	log.debug("${device.displayName} - updated()")
	def syncNeeded = false
	if(settings.wakeUpInterval != null) {
		if(state.wakeUpInterval == null) {
			state.wakeUpInterval = [value: null, state: "synced"]
		} 
		if(state.wakeUpInterval.value != ((settings.wakeUpInterval as Integer) * 60)) { 
			state.wakeUpInterval.value = ((settings.wakeUpInterval as Integer) * 60)
			state.wakeUpInterval.state = "notSynced"
			syncNeeded = true
		}
	}	
	if(syncNeeded) { 
		log.debug("${device.displayName} - sync needed")
	}
	state.lastUpdated = now()
}

def configure() {
	log.debug("${device.displayName} - configure()")
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
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv3.SensorMultilevelReport cmd) {
	def map = [:]
	if (cmd.sensorType == 1) {
		def cmdScale = cmd.scale == 1 ? "F" : "C"
		log.debug("${device.displayName} - SensorMultilevelReport received, value: ${cmd.scaledSensorValue} °${cmdScale}")
		map.value = getTempInLocalScale(cmd.scaledSensorValue, cmdScale)
		map.unit = getTemperatureScale()
		map.name = "temperature"
	} else {
		log.warn("${device.displayName} - Unexpected sensorType received in SensorMultilevelReport: ${cmd.sensorType}")
	}
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd) {
	if(cmd.setpointType == 1) {
		def cmdScale = cmd.scale == 1 ? "F" : "C"
		def heatingSetpoint = getTempInDeviceScale("heatingSetpoint")
		log.debug("${device.displayName} - ThermostatSetpointReport received, value: ${cmd.scaledValue} °${cmdScale}")
		if(state.pendingHeatingSetpoint == null && state.deviceHeatingSetpoint != cmd.scaledValue && heatingSetpoint != cmd.scaledValue) {        
			def switchState = getSwitchState(cmd.scaledValue, cmdScale)
			if(switchState == "off") {
				if(state.thermostatMode != "off") {
					state.lastHeatingSetpoint = state.deviceHeatingSetpoint
					state.thermostatMode = "off"
				}
			} else {
				if(state.thermostatMode != "heat")
					state.thermostatMode = "heat"
			}
			sendHeatingSetpointEvent(cmd.scaledValue, cmdScale, true)
			sendEvent(name: "switch", value: getSwitchState(cmd.scaledValue, cmdScale), displayed: true)
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
	sendEvent(descriptionText: "${device.displayName} woke up", isStateChange: true)
	def cmds = []
	if(state.wakeUpInterval?.state == "notSynced" && state.wakeUpInterval?.value != null) {
		cmds << zwave.wakeUpV2.wakeUpIntervalSet(seconds: state.wakeUpInterval.value as Integer, nodeid: zwaveHubNodeId)
		state.wakeUpInterval.state = "synced"
	}
	// if not set yet, update ManufacturerSpecific data
	if (!getDataValue("manufacturer")) {
		cmds << zwave.manufacturerSpecificV2.manufacturerSpecificGet()
	}
	runIn(1, "sync")
	[response(delayBetween(cmds.collect{ it.format() }, 1000))]
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
	log.debug("${device.displayName} - on(), currentMode=${state.thermostatMode}")
	if(state.thermostatMode == "heat")
		return
	state.thermostatMode = "heat" // TODO: use the "Thermostat Mode" capability and store this in the thermostatMode attribute
	def lastHeatingSetpoint = state.lastHeatingSetpoint != null ? getTempInLocalScale(state.lastHeatingSetpoint, getDeviceScale()) : getTempInLocalScale(21, "C")
	setHeatingSetpoint(lastHeatingSetpoint)
}

def off() {
	log.debug("${device.displayName} - off(), currentMode=${state.thermostatMode}")
	if(state.thermostatMode == "off")
		return
	state.thermostatMode = "off" // TODO: use the "Thermostat Mode" capability and store this in the thermostatMode attribute
	state.lastHeatingSetpoint = getTempInDeviceScale("heatingSetpoint")
	setHeatingSetpoint(getTempInLocalScale(4, "C"))
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
	def switchState = getSwitchState(heatingSetpoint, getDeviceScale())
	if(state.deviceHeatingSetpoint != heatingSetpoint) {
		state.pendingHeatingSetpoint = heatingSetpoint
		sendHeatingSetpointEvent(heatingSetpoint, getDeviceScale(), true)
		sendEvent(name: "switch", value: switchState, displayed: true)
	} else {
		if(state.pendingHeatingSetpoint != heatingSetpoint) {
			sendHeatingSetpointEvent(heatingSetpoint, getDeviceScale(), true)
			sendEvent(name: "switch", value: switchState, displayed: true)
		}
		state.pendingHeatingSetpoint = null
	}
}

def sendHeatingSetpointEvent(scaledValue, displayed) {
	sendHeatingSetpointEvent(scaledValue, state.scale == 1 ? "F" : "C")
}

def sendHeatingSetpointEvent(scaledValue, scale, displayed) {
	def setpoint = getTempInLocalScale(scaledValue, scale)
	def unit = getTemperatureScale()		
	sendEvent(name: "heatingSetpoint", value: setpoint, unit: unit, displayed: displayed)
}

def getSwitchState(setpoint, scale) {
	def offSetpoint = getTempInDeviceScale(4, "C")
	def setpointInDeviceScale = getTempInDeviceScale(setpoint, scale)
	return setpointInDeviceScale == offSetpoint ? "off" : "on"
}

def currentTimeCommand() {
	def nowCalendar = Calendar.getInstance(location.timeZone)
	def weekday = nowCalendar.get(Calendar.DAY_OF_WEEK) - 1
	if (weekday == 0) {
		weekday = 7
	}
	log.debug "currentTimeCommand: hour='${nowCalendar.get(Calendar.HOUR_OF_DAY)}', minute='${nowCalendar.get(Calendar.MINUTE)}', DayNum='${weekday}'"
	return zwave.clockV1.clockSet(hour: nowCalendar.get(Calendar.HOUR_OF_DAY), minute: nowCalendar.get(Calendar.MINUTE), weekday: weekday)
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