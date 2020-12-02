/**
 *  Copyright 2020 Rangner Ferraz Guimaraes
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
 *  Sinope Thermostat (Child Device of Sinope Hub Bridge)
 *
 *  Author: Rangner Ferraz Guimaraes (rferrazguimaraes)
 *  Date: 2020-11-22
 *  Version: 1.0 - Initial commit
 *  Version: 1.1 - Fixed thread issues + added options to thermostat
 */

/*
Support for Sinope thermostat.
family 10 = thermostat TH1120RF 3000W and 4000W
family 20 = thermostat TH1300RF 3600W floor, TH1500RF double pole thermostat
family 21 = thermostat TH1400RF low voltage
For more details about this platform, please refer to the documentation at
https://www.sinopetech.com/en/support/#api
*/

import groovy.transform.Field

@Field final Map MODE = [
                         SINOPE_MODE_OFF:   0,
                         SINOPE_MODE_FREEZE_PROTECT:  1,
                         SINOPE_MODE_MANUAL:  2,
                         SINOPE_MODE_AUTO:  3,
                         SINOPE_MODE_AWAY: 5
                        ]

@Field final int SINOPE_BYPASS_FLAG = 128
@Field final List SINOPE_BYPASSABLE_MODES = [MODE.SINOPE_MODE_FREEZE_PROTECT, MODE.SINOPE_MODE_AUTO, MODE.SINOPE_MODE_AWAY]
@Field final List SINOPE_MODE_AUTO_BYPASS = [MODE.SINOPE_MODE_AUTO, MODE.SINOPE_BYPASS_FLAG]

@Field final String PRESET_BYPASS = 'temporary'
@Field final List PRESET_MODES = ["PRESET_NONE", "PRESET_AWAY", "PRESET_BYPASS"]

@Field final List IMPLEMENTED_DEVICE_TYPES = [10, 20, 21]

@Field final Map pollIntervalOptions = [0:"off", 300:"5 minutes", 600:"10 minutes", 900:"15 minutes", 1800:"30 minutes", 3600:"60 minutes"]
@Field final Map displaySecondaryOptions = [0:"SetPoint", 1:"Outside Temperature"]
@Field final Map temperatureFormatOptions = [0:"Celsius", 1:"Farenheit"]
@Field final Map timeFormatOptions = [0:"24h", 1:"12h"]
@Field final Map backlightOptions = [0:"Automatic", 100:"Always On"]
@Field final Map earlyStartOptions = [0:"Disabled", 1:"Enabled"]
@Field final Map keypadOptions = [0:"Unlocked", 1:"Locked"]
@Field final Map temperatureSetPointOptions = [5.0:"5.0 C", 5.5:"5.5 C", 6.0:"6.0 C", 6.5:"6.5 C", 7.0:"7.0 C", 7.5:"7.5 C", 8.0:"8.0 C", 8.5:"8.5 C", 9:"9.0 C", 9.5:"9.5 C", 10.0:"10.0 C", 10.5:"10.5 C", 11.0:"11.0 C", 11.5:"11.5 C", 12.0:"12.0 C", 12.5:"12.5 C", 13.0:"13.0 C", 13.5:"13.5 C", 14.0:"14.0 C", 14.5:"14.5 C", 15.0:"15.0 C", 15.5:"15.5 C", 16.0:"16.0 C", 16.5:"16.5 C", 17.0:"17.0 C", 17.5:"17.5 C", 18.0:"18.0 C", 18.5:"18.5 C", 19.0:"19.0 C", 19.5:"19.5 C", 20.0:"20.0 C", 20.5:"20.5 C", 21.0:"21.0 C", 21.5:"21.5 C", 22.0:"22.0 C", 22.5:"22.5 C", 23.0:"23.0 C", 23.5:"23.5 C", 24.0:"24.0 C", 24.5:"24.5 C", 25.0:"25.0 C", 25.5:"25.5 C", 26.0:"26.0 C", 26.5:"26.5 C", 27.0:"27.0 C", 27.5:"27.5 C", 28.0:"28.0 C", 28.5:"28.5 C", 29.0:"29.0 C", 29.5:"29.5 C", 30.0:"30.0 C"]

metadata {
	definition (name: "Sinope Thermostat", namespace: "rferrazguimaraes", author: "Rangner Ferraz Guimaraes")
    { 
        capability "Initialize"
        capability "Actuator"
        capability "Sensor"
        capability "Thermostat"
		capability "TemperatureMeasurement"
        capability "Polling"
		capability "Refresh"
        
		attribute "outdoorTemp", "string"
		attribute "heatLevel", "number"
        attribute "targetTemp", "number"
        attribute "currMode", "string"
        attribute "statusText", "string"
        
        command "tempUp"
        command "tempDown"
	}
    
    preferences {
        input name: "pollIntervals", type: "enum", title: "Set the Poll Interval.", options: pollIntervalOptions, defaultValue: "600", required: true 
        input name: "prefDisplaySecondary", type: "enum", title: "Secondary Display", options: displaySecondaryOptions, defaultValue: "1", required: true
        input name: "prefDisplayTemperatureFormat", type: "enum", title: "Temperature Format", options: temperatureFormatOptions, defaultValue: "0", required: true
        input name: "prefDisplayTimeFormat", type: "enum", title: "Time Format", options: timeFormatOptions, defaultValue: "0", required: true
        input name: "prefDisplayBacklight", type: "enum", title: "Backlight", options: backlightOptions, defaultValue: "0", required: true
        input name: "prefDisplayEarlyStart", type: "enum", title: "Early Start", options: earlyStartOptions, defaultValue: "0", required: true
        input name: "prefDisplayKeypad", type: "enum", title: "Keypad", options: keypadOptions, defaultValue: "0", required: true
        input name: "prefMinSetPoint", type: "enum", title: "Min. Setpoint", options: temperatureSetPointOptions, defaultValue: "5.0", required: true
        input name: "prefMaxSetPoint", type: "enum", title: "Max. Setpoint", options: temperatureSetPointOptions, defaultValue: "30.0", required: true
        input name: "prefAwaySetPoint", type: "enum", title: "Away Setpoint", options: temperatureSetPointOptions, defaultValue: "5.0", required: true
        //input name: "wakeUpIntervalInMins", "number", title: "Wake Up Interval (min). Default 5mins.", description: "Wakes up and send\receives new temperature setting", range: "1..30", displayDuringSetup: true
        input("logDebug", "bool", title: "Enable debug logging", defaultValue: false)
    	input("logWarn", "bool", title: "Enable warn logging", defaultValue: false)
    	input("logError", "bool", title: "Enable error logging", defaultValue: false)
    }
}

def installed() {
    log_debug("installed()")
    // Set unused default values (for Google Home Integration)
	sendEvent(name: "thermostatMode", value: "off", isStateChange: true)
	sendEvent(name: "thermostatFanMode", value: "auto", isStateChange: true)
	sendEvent(name: "thermostatOperatingState", value: "idle", isStateChange: true)
	sendEvent(name: "thermostatSetpoint", value: 18, isStateChange: true)
	sendEvent(name: "heatingSetpoint", value: 18, isStateChange: true)
	sendEvent(name: "coolingSetpoint", value: 30, isStateChange: true)
    sendEvent(name: "temperature", value: 20, isStateChange: true)
    sendEvent(name: "targetTemp", value: 20, isStateChange: true)
    updateDataValue("lastRunningMode", "heat")
    configure()
}

def initialize() {
    log_debug("initialize()")
    configure()
}

def configure() {    
    log.warn "configure()"

    unschedule()
    poll()
}

def updated() {
    log_debug("updated()")
    
    parameterSetting()
    configure()
}

def parameterSetting() {
    log_debug("parameterSetting()")
    parent.set_secondary_display(device.deviceNetworkId, prefDisplaySecondary.toInteger() ?: 1)
    parent.set_temperature_format(device.deviceNetworkId, prefDisplayTemperatureFormat.toInteger() ?: 0)
    parent.set_time_format(device.deviceNetworkId, prefDisplayTimeFormat.toInteger() ?: 0)
    parent.set_early_start(device.deviceNetworkId, prefDisplayEarlyStart.toInteger() ?: 0)
    parent.set_backlight_idle(device.deviceNetworkId, prefDisplayBacklight.toInteger() ?: 0)
    parent.set_keyboard_lock(device.deviceNetworkId, prefDisplayKeypad.toInteger() ?: 0)
    parent.set_min_setpoint(device.deviceNetworkId, FormatTemp(prefMinSetPoint ?: 5.0))
    parent.set_max_setpoint(device.deviceNetworkId, FormatTemp(prefMaxSetPoint ?: 30.0))
    parent.set_away_setpoint(device.deviceNetworkId, FormatTemp(prefAwaySetPoint ?: 5.0))
}

def refresh() {
    //Default refresh method which calls immediate update and ensures it is scheduled	
	refreshInfo()
}

def refreshHourly() {
    //parent.set_hourly_report()
}

def refreshDaily() {
     parent.set_daily_report(device.deviceNetworkId)
}

def refreshInfo()
{
    //Get the latest data from Sinope and update the state.
	//Actually get a refresh from the parent/NeviwebHub
    log_debug("Refreshing thermostat data from parent")
    parent.childRequestingRefresh(device.deviceNetworkId)
}

def tempUp() {
    log.debug("tempUp()")
    
    def targetTemp = device.currentValue("targetTemp")
    if (targetTemp != null) {
        switch (location?.getTemperatureScale()) {
            case "C":
            targetTemp = FormatTemp(targetTemp + 0.5)
            if (targetTemp <= 5) {
                targetTemp = 5
            }      
            break;

            default:
            targetTemp = FormatTemp(targetTemp + 1)
            if (targetTemp <= 41) {
                targetTemp = 41
            }  
            break;
        }
        
        updateEvents(temperature: targetTemp, updateDevice: true)
    }
}

def tempDown() {
    log_debug("tempDown()")
    
    def targetTemp = device.currentValue("targetTemp")
    if (targetTemp != null) {
        switch (location?.getTemperatureScale()) {
            case "C":
            targetTemp = FormatTemp(targetTemp - 0.5)
            if (targetTemp <= 5) {
                targetTemp = 5
            }      
            break;

            default:
            targetTemp = FormatTemp(targetTemp - 1)
            if (targetTemp <= 41) {
                targetTemp = 41
            }  
            break;
        }
        
        updateEvents(temperature: targetTemp, updateDevice: true)
    }
}

def setTemperature(Double value) {
    log.debug "Executing 'setTemperature' with ${value}"
    updateEvents(temperature: value, updateDevice: true)
}

def poll() {
    pollInterval = pollIntervals.toInteger() ?: 0
    unschedule('poll')
    if(pollInterval > 0)
    {
        runIn(pollInterval, 'poll') 
        log_debug("in poll: (every ${pollInterval} seconds)")
        refresh()
    }
}

def setHeatingSetpoint(Double value) {
    log_debug("setHeatingSetpoint() newSetpoint: ${value}")
    updateEvents(temperature: value, updateDevice: true)
}

def setThermostatSetpoint(Double value) {
    log_debug("setThermostatSetpoint() temperature: ${value}")
    updateEvents(temperature: value, updateDevice: true)
}

def getTemperatureScale() {
    return getDataValue("temperatureScale")
}

//Dont use any of these yet as I havent worked out why they would be needed! 
//Just log that they were triggered for troubleshooting
def heat() {
	log_debug("heat()")
    def heatPoint = device.currentValue("heatingSetpoint")
    updateEvents(mode: "heat", temperature: heatPoint, updateDevice: true)
}

def emergencyHeat() {
	log_debug("emergencyHeat()")
}

def setThermostatMode(String newMode) {
	log_debug("setThermostatMode() - ${newMode}")
    def currMode = device.currentValue("thermostatMode")
    if (currMode != newMode){
        updateEvents(mode: newMode, updateDevice: true)
    }
}

def fanOn() {
	log_debug("fanOn mode is not available for this device")
}

def fanAuto() {
	log_debug("fanAuto mode is not available for this device")
}

def fanCirculate() {
	log_debug("fanCirculate mode is not available for this device")
}

def setThermostatFanMode(String value) {
	log_debug("setThermostatFanMode is not available for this device - ${fanMode}")
}

def cool() {
	log_debug("cool mode is not available for this device")
    //def coolPoint = device.currentValue("coolingSetpoint")
    //updateEvents(mode: "cool", temperature: coolPoint, updateDevice: true)
}

def setCoolingSetpoint(Double value) {
	log_debug("setCoolingSetpoint is not available for this device")
    updateEvents(temperature: value, updateDevice: true)
}

def setSchedule(schedule) {
    log_debug("setSchedule is not available for this device")
}

def auto() {
	log_debug("emergencyHeat mode is not available for this device. => Defaulting to heat mode instead.")
    updateEvents(mode: "auto", updateDevice: true)
}

def off() {
	log_debug("off()")
    updateEvents(mode: "off", updateDevice: true)
}

def processChildResponse(response)
{
    def isRead = response.updateType == "read"
	//Response received from Neviweb Hub so process it (mainly used for refresh, but could also process the success/fail messages)
    log_debug("received processChildResponse response: ${response}")
    switch(response.type) {
        case "temperature":
            def temp = FormatTemp(response.value)
            sendEvent(name: "temperature", value: temp)
            //log_debug("received processChildResponse temperature: ${temp}")        
            break
        case "set_point":
            def temp = FormatTemp(response.value)
            updateEvents(temperature: temp, updateDevice: false)
            //log_debug("received processChildResponse setPoint: ${temp}")
            break
        case "heat_level":
			sendEvent(name: "heatLevel", value: response.value)
			sendEvent(name: "thermostatOperatingState", value: ((heatLevel > 10) ? "heating" : "idle"))
            log_debug("received processChildResponse heatLevel: ${response.value}")
            break
        case "mode":
            updateEvents(mode: ((response.value > 0) ? "heat" : "off"), updateDevice: false)
            log_debug("received processChildResponse mode: ${response.value}")
            break
        case "away":
            sendEvent(name: "away", value: response.value)
            log_debug("received processChildResponse away: ${response.value}")
            break
        case "max_temp":
            def temp = FormatTemp(response.value, false)
            sendEvent(name: "maximumTemperature", value: temp)
            log_debug("received processChildResponse tempmax: ${temp}")
            break
        case "min_temp":
            def temp = FormatTemp(response.value, false)
            sendEvent(name: "minimumTemperature", value: temp)
            log_debug("received processChildResponse tempmin: ${temp}")
            break        
        case "load":
            sendEvent(name: "load", value: response.value)
            log_debug("received processChildResponse wattload: ${response.value}")
            break
        case "power_connected":
            sendEvent(name: "power", value: response.value)
            log_debug("received processChildResponse power: ${response.value}")
            break
        case "outdoorTemperature":
            sendEvent(name: "outdoorTemperature", value: response.value)
            log_debug("received processChildResponse outdoorTemp: ${response.value}")
            break
        case "time":
            sendEvent(name: "time", value: response.value)
            log_debug("received processChildResponse time: ${response.value}")
            break
        case "date":
            sendEvent(name: "date", value: response.value)
            log_debug("received processChildResponse date: ${response.value}")
            break
        case "sunrise":
            sendEvent(name: "sunrise", value: response.value)
            log_debug("received processChildResponse sunrise: ${response.value}")
            break
        case "sunset":
            sendEvent(name: "sunset", value: response.value)
            log_debug("received processChildResponse sunset: ${response.value}")
            break
        case "lock":
            sendEvent(name: "lock", value: keypadOptions.get(response.value))
            log_debug("received processChildResponse lock: ${keypadOptions.get(response.value)}")
            break
        case "secondary_display":
            sendEvent(name: "secondaryDisplay", value: displaySecondaryOptions.get(response.value))
            log_debug("received processChildResponse secondary_display: ${displaySecondaryOptions.get(response.value)}")
            break
        case "temperature_format":
            sendEvent(name: "temperatureFormat", value: temperatureFormatOptions.get(response.value))
            log_debug("received processChildResponse temperature_format: ${temperatureFormatOptions.get(response.value)}")
            break
        case "time_format":
            sendEvent(name: "timeFormat", value: timeFormatOptions.get(response.value))
            log_debug("received processChildResponse time_format: ${timeFormatOptions.get(response.value)}")
            break
        case "early_start":
            sendEvent(name: "earlyStart", value: earlyStartOptions.get(response.value))
            log_debug("received processChildResponse early_start: ${earlyStartOptions.get(response.value)}")
            break
        case "away_temp":
            sendEvent(name: "awayTemperature", value: response.value)
            log_debug("received processChildResponse awayTemperature: ${response.value}")
            break
        case "backlight_state":
            sendEvent(name: "backLightState", value: response.value)
            log_debug("received processChildResponse backLightState: ${response.value}")
            break
        case "backlight_idle":
            sendEvent(name: "backLightIdle", value: response.value)
            log_debug("received processChildResponse backLightIdle: ${response.value}")
            break
        default:
            log_error("processChildResponse - Command ${response.type} not found!")
            break
    }
}

private updateEvents(Map args){
    log_debug("Executing 'updateEvents' with mode: ${args.mode}, temperature: ${args.temperature} and updateDevice: ${args.updateDevice}")
    // Get args with default values
    def mode = args.get("mode", null)
    def temperature = FormatTemp(args.get("temperature", null))
    def updateDevice = args.get("updateDevice", false)
    Boolean turnOff = mode == "off"
    def events = []

    if (updateDevice){
        log_debug("Executing 'updateDevice' with temperature: ${temperature} and turnOff: ${turnOff}")
        unschedule('updateDevice')
        runIn(2, 'updateDevice', [data:[temperature, mode, turnOff]])
    }
    
    if (!mode){
        mode = device.currentValue("thermostatMode")
    } else {
        events.add(sendEvent(name: "thermostatMode", value: mode))	   
    }
    
    if (!temperature){
        temperature = FormatTemp(device.currentValue("targetTemp"))
    }
    
    switch(mode) {
        case "fan":
            events.add(sendEvent(name: "statusText", value: "Fan Mode", displayed: false))
            events.add(sendEvent(name: "thermostatOperatingState", value: "fan only", displayed: false, isStateChange: true))
            events.add(sendEvent(name: "targetTemp", value: null))
            updateDataValue("lastRunningMode", "auto")
            break
        case "dry":
            events.add(sendEvent(name: "statusText", value: "Dry Mode", displayed: false))
            events.add(sendEvent(name: "thermostatOperatingState", value: "fan only", displayed: false, isStateChange: true))
            events.add(sendEvent(name: "targetTemp", value: null))
            updateDataValue("lastRunningMode", "auto")
            break
        case "heat":
            events.add(sendEvent(name: "statusText", value: "Heating to ${temperature}°", displayed: false))
            //events.add(sendEvent(name: "thermostatOperatingState", value: "heating", displayed: false, isStateChange: true))
            events.add(sendEvent(name: "heatingSetpoint", value: temperature, displayed: false, isStateChange: true))
            events.add(sendEvent(name: "thermostatSetpoint", value: temperature, displayed: false, isStateChange: true))
            events.add(sendEvent(name: "targetTemp", value: temperature))
            updateDataValue("lastRunningMode", "heat")
            break
        case "cool":
            events.add(sendEvent(name: "statusText", value: "Cooling to ${temperature}°", displayed: false))
            events.add(sendEvent(name: "thermostatOperatingState", value: "cooling", displayed: false, isStateChange: true))
            events.add(sendEvent(name: "coolingSetpoint", value: temperature, displayed: false, isStateChange: true))
            events.add(sendEvent(name: "thermostatSetpoint", value: temperature, displayed: false, isStateChange: true))
            events.add(sendEvent(name: "targetTemp", value: temperature))
            updateDataValue("lastRunningMode", "cool")
            break
        case "auto":
            events.add(sendEvent(name: "statusText", value: "Auto Mode: ${temperature}°", displayed: false))
            events.add(sendEvent(name: "targetTemp", value: temperature))
            updateDataValue("lastRunningMode", "auto")
            break
        case "off":
            events.add(sendEvent(name: "statusText", value: "System is off", displayed: false))
            events.add(sendEvent(name: "thermostatOperatingState", value: "idle", displayed: false, isStateChange: true))
            updateDataValue("lastRunningMode", "off")
            break
    }

    if (turnOff){
        events.add(sendEvent(name: "switch", value: "off", displayed: false))
    } else {
        events.add(sendEvent(name: "switch", value: "on", displayed: false))
    }
}

private updateDevice(temperature, mode, turnOff) {
    log_debug("updateDevice with temperature: ${temperature}, mode: ${mode} and turnOff: ${turnOff}")
    if(temperature != null) {
        parent.childSetTemp(device.deviceNetworkId, temperature)
    }
    
    if(mode != null) {
        String modeAttr = /*turnOff ? "currMode" :*/ "thermostatMode"
        def currentMode = device.currentState(thermostatMode)?.value
        if(currentMode != mode)
        {
            switch(mode) {
                case "heat":
                    set_hvac_mode(MODE.SINOPE_MODE_MANUAL)
                break
                case "auto":
                    set_hvac_mode(MODE.SINOPE_MODE_AUTO)
                break
                case "off":
                    set_hvac_mode(MODE.SINOPE_MODE_OFF)
                break
            }
        }
    }
}

def set_outside_temperature(self, outside_temperature) {
    //Send command to set new outside temperature.
    if (outside_temperature == None) {
        return
    }
    
    parent.set_hourly_report(device.deviceNetworkId, outside_temperature)
    self._outside_temperature = outside_temperature
}

def async_set_outside_temperature(self, outside_temperature) {
    //Send command to set new outside temperature.
    parent.set_hourly_report(device.deviceNetworkId, outside_temperature)
    //self._outside_temperature = outside_temperature
}

def set_hvac_mode(hvac_mode) {
    log_debug("set_hvac_mode - hvac_mode: ${hvac_mode}")

    //Set new hvac mode.
    parent.send_time(device.deviceNetworkId)
    def type = 10 //temp to cleanup
    parent.set_mode(device.deviceNetworkId, type, hvac_mode)
}

def set_preset_mode(self, preset_mode) {
    // Activate a preset.
    if (preset_mode == self.preset_mode) {
        return
    }
    
    self._type = 10 //temp to cleanup
    if (preset_mode == PRESET_AWAY) {
        //Set away mode on device, away_on = 2 away_off =0
        parent.set_away_mode(device.deviceNetworkId, 2)
    } else if (preset_mode == PRESET_BYPASS) {
        if (SINOPE_BYPASSABLE_MODES.contains(self._operation_mode)) {
            parent.set_away_mode(device.deviceNetworkId, 0)      
            parent.set_mode(device.deviceNetworkId, self._type, self._operation_mode | SINOPE_BYPASS_FLAG)
        } else if (preset_mode == PRESET_NONE) {
            // Re-apply current hvac_mode without any preset
            parent.set_away_mode(device.deviceNetworkId, 0)
            //self.set_hvac_mode(self.hvac_mode)
        } else {
            log_error("Unable to set preset mode: ${preset_mode}.")
        }
    }
}

private timeStringToMins(String timeString) {
	if (timeString?.contains(':')) {
    	def hoursandmins = timeString.split(":")
        def mins = hoursandmins[0].toInteger() * 60 + hoursandmins[1].toInteger()
        log_debug("${timeString} converted to ${mins}")
        return mins
    }
}

private minsToTimeString(Integer intMins) {
	def timeString =  "${(intMins/60).toInteger()}:${(intMins%60).toString().padLeft(2, "0")}"
    log_debug("${intMins} converted to ${timeString}")
    return timeString
}

private timeStringToHoursMins(String timeString) {
	if (timeString?.contains(':')) {
    	def hoursMins = timeString.split(":")
        log_debug("${timeString} converted to ${hoursMins[0]}:${hoursMins[1]}")
        return hoursMins
    }
}

private minsToHoursMins(Integer intMins) {
	def hoursMins = []
    hoursMins << (intMins/60).toInteger()
    hoursMins << (intMins%60).toInteger()
    log_debug("${intMins} converted to ${hoursMins[0]}:${hoursMins[1]}")
    return hoursMins
}

def FormatTemp(temp, invert = false) {
	if (temp != null) {
		if(invert) {
			float i = Float.parseFloat(temp+"")
			switch (location?.getTemperatureScale()) {
				case "C":
					return roundToHalf(i)
				break;

				case "F":
					return roundToHalf((Math.round(fToC(i))).toDouble())
				break;
			}
		} else {
			float i = Float.parseFloat(temp+"")
			switch (location?.getTemperatureScale()) {
				case "C":
					return roundToHalf(i)
				break;

				case "F":
					return (Math.round(cToF(i))).toDouble().round(0)
				break;
			}
		}
    } else {
    	return null
    }
}

def float roundToHalf(float x) {
    return (float) (Math.ceil(x * 2) / 2);
}

def cToF(temp) {
	return ((( 9 * temp ) / 5 ) + 32)
	log.info "celsius -> fahrenheit"
}

def fToC(temp) {
	return ((( temp - 32 ) * 5 ) / 9)
	log.info "fahrenheit -> celsius"
}

def log_debug(logData)
{
    if (parent.childGetDebugState() || logDebug) log.debug("${device} (${device.deviceNetworkId}) - " + logData)
}

def log_warn(logData)
{
    if (parent.childGetWarnState() || logWarn) log.warn("${device} (${device.deviceNetworkId}) - " + logData)
}

def log_error(logData)
{
    if (parent.childGetErrorState() ||logError) log.error("${device} (${device.deviceNetworkId}) - " + logData)
}
