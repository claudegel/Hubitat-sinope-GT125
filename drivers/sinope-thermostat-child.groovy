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
 *  Version: 1.2 - Added support to fahrenheit
 *  Version: 1.3 - Added better log system
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

@Field Utils = Utils_create();
@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

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
@Field final float SINOPE_MIN_TEMPERATURE_CELSIUS = 5.0
@Field final float SINOPE_MAX_TEMPERATURE_CELSIUS = 30.0
@Field final float SINOPE_MIN_TEMPERATURE_FAHRENHEIT = 41.0
@Field final float SINOPE_MAX_TEMPERATURE_FAHRENHEIT = 86.0

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
@Field final Map temperatureSetPointsCelsius = [5.0:"5.0 C", 5.5:"5.5 C", 6.0:"6.0 C", 6.5:"6.5 C", 7.0:"7.0 C", 7.5:"7.5 C", 8.0:"8.0 C", 8.5:"8.5 C", 9:"9.0 C", 9.5:"9.5 C", 10.0:"10.0 C", 10.5:"10.5 C", 11.0:"11.0 C", 11.5:"11.5 C", 12.0:"12.0 C", 12.5:"12.5 C", 13.0:"13.0 C", 13.5:"13.5 C", 14.0:"14.0 C", 14.5:"14.5 C", 15.0:"15.0 C", 15.5:"15.5 C", 16.0:"16.0 C", 16.5:"16.5 C", 17.0:"17.0 C", 17.5:"17.5 C", 18.0:"18.0 C", 18.5:"18.5 C", 19.0:"19.0 C", 19.5:"19.5 C", 20.0:"20.0 C", 20.5:"20.5 C", 21.0:"21.0 C", 21.5:"21.5 C", 22.0:"22.0 C", 22.5:"22.5 C", 23.0:"23.0 C", 23.5:"23.5 C", 24.0:"24.0 C", 24.5:"24.5 C", 25.0:"25.0 C", 25.5:"25.5 C", 26.0:"26.0 C", 26.5:"26.5 C", 27.0:"27.0 C", 27.5:"27.5 C", 28.0:"28.0 C", 28.5:"28.5 C", 29.0:"29.0 C", 29.5:"29.5 C", 30.0:"30.0 C"]
@Field final Map temperatureSetPointsFarenheit = [41.0:"41 F", 42.0:"42 F", 43.0:"43 F", 44.0:"44 F", 45.0:"45 F", 46.0:"46 F", 47.0:"47 F", 48.0:"48 F", 49.0:"49 F", 50.0:"50 F", 51.0:"51 F", 52.0:"52 F", 53.0:"53 F", 54.0:"54 F", 55.0:"55 F", 56.0:"56 F", 57.0:"57 F", 58.0:"58 F", 59.0:"59 F", 60.0:"60 F", 61.0:"61 F", 62.0:"62 F", 63.0:"63 F", 64.0:"64 F", 65.0:"65 F", 66.0:"66 F", 67.0:"67 F", 68.0:"68 F", 69.0:"69 F", 70.0:"70 F", 71.0:"71 F", 72.0:"72 F", 73.0:"73 F", 74.0:"74 F", 75.0:"75 F", 76.0:"76 F", 77.0:"77 F", 78.0:"78 F", 79.0:"79 F", 80.0:"80 F", 81.0:"81 F", 82.0:"82 F", 83.0:"83 F", 84.0:"84 F", 85.0:"85 F", 86.0:"86 F"]

def driverVer() { return "1.3" }

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
        attribute "targetTemperature", "number"
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
        input name: "prefMinSetPoint", type: "enum", title: "Min. Setpoint", options: isCelsius() ? temperatureSetPointsCelsius : temperatureSetPointsFarenheit, defaultValue: isCelsius() ? "5.0" : "41.0", required: true
        input name: "prefMaxSetPoint", type: "enum", title: "Max. Setpoint", options: isCelsius() ? temperatureSetPointsCelsius : temperatureSetPointsFarenheit, defaultValue: isCelsius() ? "30.0" : "86.0", required: true
        input name: "prefAwaySetPoint", type: "enum", title: "Away Setpoint", options: isCelsius() ? temperatureSetPointsCelsius : temperatureSetPointsFarenheit, defaultValue: isCelsius() ? "5.0" : "41.0", required: true
        input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: true
    }
}

def installed() {
    Utils.toLogger("debug", "installed()")
    // Set unused default values (for Google Home Integration)
	sendEvent(name: "thermostatMode", value: "off", isStateChange: true)
	sendEvent(name: "thermostatFanMode", value: "auto", isStateChange: true)
	sendEvent(name: "thermostatOperatingState", value: "idle", isStateChange: true)
	sendEvent(name: "thermostatSetpoint", value: FormatTemperature(18, isFahrenheit()), isStateChange: true)
	sendEvent(name: "heatingSetpoint", value: FormatTemperature(18, isFahrenheit()), isStateChange: true)
	sendEvent(name: "coolingSetpoint", value: FormatTemperature(getMaxTemperature(), isFahrenheit()), isStateChange: true)
    sendEvent(name: "temperature", value: FormatTemperature(20, isFahrenheit()), isStateChange: true)
    sendEvent(name: "targetTemperature", value: FormatTemperature(20, isFahrenheit()), isStateChange: true)
    updateDataValue("lastRunningMode", "heat")
    updateDataValue("driverVersion", driverVer())
    configure()
}

def initialize() {
    Utils.toLogger("debug", "initialize()")
    configure()
}

def configure() {    
    log.warn "configure()"
    unschedule()
    poll()
}

def updated() {
    Utils.toLogger("debug", "updated()")
    
    parameterSetting()
    configure()
}

def parameterSetting() {
    Utils.toLogger("debug", "parameterSetting()")
    parent.set_secondary_display(device.deviceNetworkId, prefDisplaySecondary.toInteger() ?: 1)
    parent.set_temperature_format(device.deviceNetworkId, prefDisplayTemperatureFormat.toInteger() ?: 0)
    parent.set_time_format(device.deviceNetworkId, prefDisplayTimeFormat.toInteger() ?: 0)
    parent.set_early_start(device.deviceNetworkId, prefDisplayEarlyStart.toInteger() ?: 0)
    parent.set_backlight_idle(device.deviceNetworkId, prefDisplayBacklight.toInteger() ?: 0)
    parent.set_keyboard_lock(device.deviceNetworkId, prefDisplayKeypad.toInteger() ?: 0)
    parent.set_min_setpoint(device.deviceNetworkId, FormatTemperature(prefMinSetPoint ?: getMinTemperature(), isFahrenheit()))
    parent.set_max_setpoint(device.deviceNetworkId, FormatTemperature(prefMaxSetPoint ?: getMaxTemperature(), isFahrenheit()))
    parent.set_away_setpoint(device.deviceNetworkId, FormatTemperature(prefAwaySetPoint ?: getMinTemperature(), isFahrenheit()))
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
    updateDataValue("driverVersion", driverVer())
    Utils.toLogger("debug", "Refreshing thermostat data from parent")
    parent.childRequestingRefresh(device.deviceNetworkId)
}

def tempUp() {
    Utils.toLogger("debug", "tempUp()")
    
    def targetTemperature = device.currentValue("targetTemperature")
    if (targetTemperature != null) {
        
        def increment = 0.5
        if(isFahrenheit()) {
            increment = 1.0
        }
        
        targetTemperature = FormatTemperature(targetTemperature + increment, isFahrenheit())
        updateEvents(temperature: targetTemperature, updateDevice: true)
    }
}

def tempDown() {
    Utils.toLogger("debug", "tempDown()")
    
    def targetTemperature = device.currentValue("targetTemperature")
    if (targetTemperature != null) {
        def decrement = 0.5
        if(isFahrenheit()) {
            decrement = 1.0
        }
        
        targetTemperature = FormatTemperature(targetTemperature - decrement, isFahrenheit())      
        updateEvents(temperature: targetTemperature, updateDevice: true)
    }
}

def setTemperature(Double value) {
    Utils.toLogger("debug", "Executing 'setTemperature' with ${value}")
    updateEvents(temperature: value, updateDevice: true)
}

def poll() {
    pollInterval = pollIntervals.toInteger() ?: 0
    unschedule('poll')
    if(pollInterval > 0)
    {
        runIn(pollInterval, 'poll') 
        Utils.toLogger("debug", "in poll: (every ${pollInterval} seconds)")
        refresh()
    }
}

def setHeatingSetpoint(Double value) {
    Utils.toLogger("debug", "setHeatingSetpoint() newSetpoint: ${value}")
    updateEvents(temperature: value, updateDevice: true)
}

def setThermostatSetpoint(Double value) {
    Utils.toLogger("debug", "setThermostatSetpoint() temperature: ${value}")
    updateEvents(temperature: value, updateDevice: true)
}

def getTemperatureScale() {
    return getDataValue("temperatureScale")
}

//Dont use any of these yet as I havent worked out why they would be needed! 
//Just log that they were triggered for troubleshooting
def heat() {
	Utils.toLogger("debug", "heat()")
    def heatPoint = device.currentValue("heatingSetpoint")
    updateEvents(mode: "heat", temperature: heatPoint, updateDevice: true)
}

def emergencyHeat() {
	Utils.toLogger("debug", "emergencyHeat()")
}

def setThermostatMode(String newMode) {
	Utils.toLogger("debug", "setThermostatMode() - ${newMode}")
    def currMode = device.currentValue("thermostatMode")
    if (currMode != newMode){
        updateEvents(mode: newMode, updateDevice: true)
    }
}

def fanOn() {
	Utils.toLogger("debug", "fanOn mode is not available for this device")
}

def fanAuto() {
	Utils.toLogger("debug", "fanAuto mode is not available for this device")
}

def fanCirculate() {
	Utils.toLogger("debug", "fanCirculate mode is not available for this device")
}

def setThermostatFanMode(String value) {
	Utils.toLogger("debug", "setThermostatFanMode is not available for this device - ${fanMode}")
}

def cool() {
	Utils.toLogger("debug", "cool mode is not available for this device")
    //def coolPoint = device.currentValue("coolingSetpoint")
    //updateEvents(mode: "cool", temperature: coolPoint, updateDevice: true)
}

def setCoolingSetpoint(Double value) {
	Utils.toLogger("debug", "setCoolingSetpoint is not available for this device")
    updateEvents(temperature: value, updateDevice: true)
}

def setSchedule(schedule) {
    Utils.toLogger("debug", "setSchedule is not available for this device")
}

def auto() {
	Utils.toLogger("debug", "emergencyHeat mode is not available for this device. => Defaulting to heat mode instead.")
    updateEvents(mode: "auto", updateDevice: true)
}

def off() {
	Utils.toLogger("debug", "off()")
    updateEvents(mode: "off", updateDevice: true)
}

def processChildResponse(response)
{
    def isRead = response.updateType == "read"
    
	//Response received from Neviweb Hub so process it (mainly used for refresh, but could also process the success/fail messages)
    log_debug("received processChildResponse response: ${response}")
    switch(response.type) {
        case "temperature":
            def temperature = FormatTemperature(response.value, isFahrenheit())
            sendEvent(name: "temperature", value: temperature)
            Utils.toLogger("debug", "received processChildResponse temperature: ${temperature}")        
            break
        case "set_point":
            def temperature = FormatTemperature(response.value, isFahrenheit())
            updateEvents(temperature: temperature, updateDevice: false)
            Utils.toLogger("debug", "received processChildResponse setPoint: ${temperature}")
            break
        case "heat_level":
			sendEvent(name: "heatLevel", value: response.value)
			sendEvent(name: "thermostatOperatingState", value: ((heatLevel > 10) ? "heating" : "idle"))
            Utils.toLogger("debug", "received processChildResponse heatLevel: ${response.value}")
            break
        case "mode":
            updateEvents(mode: ((response.value > 0) ? "heat" : "off"), updateDevice: false)
            Utils.toLogger("debug", "received processChildResponse mode: ${response.value}")
            break
        case "away":
            sendEvent(name: "away", value: response.value)
            Utils.toLogger("debug", "received processChildResponse away: ${response.value}")
            break
        case "max_temp":
            def temperature = FormatTemperature(response.value, isFahrenheit())
            sendEvent(name: "maximumTemperature", value: temperature)
            Utils.toLogger("debug", "received processChildResponse tempmax: ${temperature}")
            break
        case "min_temp":
            def temperature = FormatTemperature(response.value, isFahrenheit())
            sendEvent(name: "minimumTemperature", value: temperature)
            Utils.toLogger("debug", "received processChildResponse tempmin: ${temperature}")
            break        
        case "load":
            sendEvent(name: "load", value: response.value)
            Utils.toLogger("debug", "received processChildResponse wattload: ${response.value}")
            break
        case "power_connected":
            sendEvent(name: "power", value: response.value)
            Utils.toLogger("debug", "received processChildResponse power: ${response.value}")
            break
        case "outdoorTemperature":
            sendEvent(name: "outdoorTemperature", value: response.value)
            Utils.toLogger("debug", "received processChildResponse outdoorTemp: ${response.value}")
            break
        case "time":
            sendEvent(name: "time", value: response.value)
            Utils.toLogger("debug", "received processChildResponse time: ${response.value}")
            break
        case "date":
            sendEvent(name: "date", value: response.value)
            Utils.toLogger("debug", "received processChildResponse date: ${response.value}")
            break
        case "sunrise":
            sendEvent(name: "sunrise", value: response.value)
            Utils.toLogger("debug", "received processChildResponse sunrise: ${response.value}")
            break
        case "sunset":
            sendEvent(name: "sunset", value: response.value)
            Utils.toLogger("debug", "received processChildResponse sunset: ${response.value}")
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
            Utils.toLogger("debug", "received processChildResponse temperature_format: ${temperatureFormatOptions.get(response.value)}")
            break
        case "time_format":
            sendEvent(name: "timeFormat", value: timeFormatOptions.get(response.value))
            Utils.toLogger("debug", "received processChildResponse time_format: ${timeFormatOptions.get(response.value)}")
            break
        case "early_start":
            sendEvent(name: "earlyStart", value: earlyStartOptions.get(response.value))
            Utils.toLogger("debug", "received processChildResponse early_start: ${earlyStartOptions.get(response.value)}")
            break
        case "away_temp":
            def temperature = FormatTemperature(response.value, isFahrenheit())
            sendEvent(name: "awayTemperature", value: temperature)
            Utils.toLogger("debug", "received processChildResponse awayTemperature: ${temperature}")
            break
        case "backlight_state":
            sendEvent(name: "backLightState", value: response.value)
            Utils.toLogger("debug", "received processChildResponse backLightState: ${response.value}")
            break
        case "backlight_idle":
            sendEvent(name: "backLightIdle", value: response.value)
            Utils.toLogger("debug", "received processChildResponse backLightIdle: ${response.value}")
            break
        default:
            Utils.toLogger("error", "processChildResponse - Command ${response.type} not found!")
            break
    }
}

private updateEvents(Map args){
    Utils.toLogger("debug", "Executing 'updateEvents' with mode: ${args.mode}, temperature: ${args.temperature} and updateDevice: ${args.updateDevice}")
    // Get args with default values
    def mode = args.get("mode", null)
    def temperature = FormatTemperature(args.get("temperature", null))
    def updateDevice = args.get("updateDevice", false)
    Boolean turnOff = mode == "off"
    def events = []

    if (updateDevice){
        Utils.toLogger("debug", "Executing 'updateDevice' with temperature: ${temperature} and turnOff: ${turnOff}")
        unschedule('updateDevice')
        runIn(2, 'updateDevice', [data:[temperature, mode, turnOff]])
    }
    
    if (!mode){
        mode = device.currentValue("thermostatMode")
    } else {
        events.add(sendEvent(name: "thermostatMode", value: mode))	   
    }
    
    if (!temperature){
        Utils.toLogger("debug", "Temperature not found, using targetTemperature: ${device.currentValue("targetTemperature")}")
        temperature = FormatTemperature(device.currentValue("targetTemperature"))
    }
    
   	sendEvent(name: "coolingSetpoint", value: FormatTemperature(getMaxTemperature()), isStateChange: true) // as SINOPE doesn't control cooling, just update with a dummy value
    
    switch(mode) {
        case "fan":
            sendEvent(name: "statusText", value: "Fan Mode", displayed: false)
            sendEvent(name: "thermostatOperatingState", value: "fan only", displayed: false, isStateChange: true)
            sendEvent(name: "targetTemperature", value: temperature)
            updateDataValue("lastRunningMode", "auto")
            break
        case "dry":
            sendEvent(name: "statusText", value: "Dry Mode", displayed: false)
            sendEvent(name: "thermostatOperatingState", value: "fan only", displayed: false, isStateChange: true)
            sendEvent(name: "targetTemperature", value: temperature)
            updateDataValue("lastRunningMode", "auto")
            break
        case "heat":
            sendEvent(name: "statusText", value: "Heating to ${temperature}°", displayed: false)
            sendEvent(name: "heatingSetpoint", value: temperature, displayed: false, isStateChange: true)
            sendEvent(name: "thermostatSetpoint", value: temperature, displayed: false, isStateChange: true)
            sendEvent(name: "targetTemperature", value: temperature)
            updateDataValue("lastRunningMode", "heat")
            break
        case "cool":
            sendEvent(name: "statusText", value: "Cooling to ${temperature}°", displayed: false)
            sendEvent(name: "thermostatOperatingState", value: "cooling", displayed: false, isStateChange: true)
            sendEvent(name: "coolingSetpoint", value: temperature, displayed: false, isStateChange: true)
            sendEvent(name: "thermostatSetpoint", value: temperature, displayed: false, isStateChange: true)
            sendEvent(name: "targetTemperature", value: temperature)
            updateDataValue("lastRunningMode", "cool")
            break
        case "auto":
            sendEvent(name: "statusText", value: "Auto Mode: ${temperature}°", displayed: false)
            sendEvent(name: "targetTemperature", value: temperature)
            updateDataValue("lastRunningMode", "auto")
            break
        case "off":
            sendEvent(name: "statusText", value: "System is off", displayed: false)
            sendEvent(name: "thermostatOperatingState", value: "idle", displayed: false, isStateChange: true)
            updateDataValue("lastRunningMode", "off")
            break
    }

    if (turnOff){
        sendEvent(name: "switch", value: "off", displayed: false)
    } else {
        sendEvent(name: "switch", value: "on", displayed: false)
    }
}

private updateDevice(temperature, mode, turnOff) {
    Utils.toLogger("debug", "updateDevice with temperature: ${temperature}, mode: ${mode} and turnOff: ${turnOff}")
    if(temperature != null) {
        float temp = FormatTemperature(temperature, isFahrenheit())
        Utils.toLogger("debug", "updateDevice real temperature: ${temp}")
        parent.childSetTemp(device.deviceNetworkId, temp)
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
    Utils.toLogger("debug", "set_hvac_mode - hvac_mode: ${hvac_mode}")

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
            Utils.toLogger("error", "Unable to set preset mode: ${preset_mode}.")
        }
    }
}

private timeStringToMins(String timeString) {
	if (timeString?.contains(':')) {
    	def hoursandmins = timeString.split(":")
        def mins = hoursandmins[0].toInteger() * 60 + hoursandmins[1].toInteger()
        Utils.toLogger("debug", "${timeString} converted to ${mins}")
        return mins
    }
}

private minsToTimeString(Integer intMins) {
	def timeString =  "${(intMins/60).toInteger()}:${(intMins%60).toString().padLeft(2, "0")}"
    Utils.toLogger("debug", "${intMins} converted to ${timeString}")
    return timeString
}

private timeStringToHoursMins(String timeString) {
	if (timeString?.contains(':')) {
    	def hoursMins = timeString.split(":")
        Utils.toLogger("debug", "${timeString} converted to ${hoursMins[0]}:${hoursMins[1]}")
        return hoursMins
    }
}

private minsToHoursMins(Integer intMins) {
	def hoursMins = []
    hoursMins << (intMins/60).toInteger()
    hoursMins << (intMins%60).toInteger()
    Utils.toLogger("debug", "${intMins} converted to ${hoursMins[0]}:${hoursMins[1]}")
    return hoursMins
}

def FormatTemperature(temperature, invert = false) {
    def temperatureFormat = prefDisplayTemperatureFormat    
    def formattedTemperature
	if (temperature != null) {
    	float temperatureFloat = Float.parseFloat(temperature+"")
        if(invert)
        {
            if(temperatureFloat > SINOPE_MAX_TEMPERATURE_CELSIUS)
            {
                temperatureFormat = "0" // to celsius
            }
            else
            {
                temperatureFormat = "1" // to fahrenheit
            }
        }
        
		if(invert) {
			switch (temperatureFormat) {
				case "0": // fahrenheit to celsius
					formattedTemperature = roundToHalf(fahrenheitToCelsius(temperatureFloat))
                    temperatureFormat = "0"
				break

                case "1": // celsius to fahrenheit
					formattedTemperature = (Math.round(celsiusToFahrenheit(temperatureFloat))).toDouble().round(0)
                    temperatureFormat = "1"
				break
			}
		} else {
			switch (temperatureFormat) {
				case "0": // celsius
					formattedTemperature = roundToHalf(temperatureFloat)
				break

				case "1": // fahrenheit
					formattedTemperature = (Math.round(temperatureFloat)).toDouble().round(0)
				break
			}
		}
    } else {
    	return null
    }
    
    switch (temperatureFormat) {
        case "0": // celsius
            formattedTemperature = Math.min(SINOPE_MAX_TEMPERATURE_CELSIUS, Math.max(SINOPE_MIN_TEMPERATURE_CELSIUS, formattedTemperature))
        break

        case "1": // fahrenheit
            formattedTemperature = Math.min(SINOPE_MAX_TEMPERATURE_FAHRENHEIT, Math.max(SINOPE_MIN_TEMPERATURE_FAHRENHEIT, formattedTemperature))
        break
    }
    
    return formattedTemperature
}

float roundToHalf(float x) {
    return (float) (Math.round(x * 2) / 2);
}

boolean isCelsius()
{
    return prefDisplayTemperatureFormat == "0"
}

boolean isFahrenheit()
{
    return !isCelsius()
}

float getMaxTemperature()
{
    if(isFahrenheit()) {
        return SINOPE_MAX_TEMPERATURE_FAHRENHEIT
    } else {
        return SINOPE_MAX_TEMPERATURE_CELSIUS
    }
}

float getMinTemperature()
{
    if(isFahrenheit()) {
        return SINOPE_MIN_TEMPERATURE_FAHRENHEIT
    } else {
        return SINOPE_MIN_TEMPERATURE_CELSIUS
    }
}

def deviceLog(level, msg) {
    Utils.toLogger(level, msg)
}

/**
 * Simple utilities for manipulation
 */

def Utils_create() {
    def instance = [:];
    
    instance.toLogger = { level, msg ->
        if (level && msg) {
            Integer levelIdx = LOG_LEVELS.indexOf(level);
            Integer setLevelIdx = LOG_LEVELS.indexOf(logLevel);
            if (setLevelIdx < 0) {
                setLevelIdx = LOG_LEVELS.indexOf(DEFAULT_LOG_LEVEL);
            }
            if (levelIdx <= setLevelIdx || levelIdx <= parent.childGetLogLevel()) {
                log."${level}" "${device.displayName} ${msg}";
            }
        }
    }

    return instance;
}
