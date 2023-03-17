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
 *  Version: 1.4 - Reformatted the supportedThermostatModes and supportedThermostatFanModes JSON_OBJECT attributes so that they work properly with Hubitat 2.3.3 and later
 *  Version: 1.5 - Major refactor to fix overloading hub error message
 *  Version: 1.6 - Exposed Thermostat Operacional Mode in the preferences
 *  Version: 1.7 - Added better support to location (home or away)
 *  Version: 1.8 - Fixed new installation
 */

/*
Support for Sinope thermostat.
family 10 = thermostat TH1120RF 3000W and 4000W
family 20 = thermostat TH1300RF 3600W floor, TH1500RF double pole thermostat
family 21 = thermostat TH1400RF low voltage
For more details about this platform, please refer to the documentation at
https://www.sinopetech.com/en/support/#api
*/

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import groovy.transform.Field
import groovy.json.JsonOutput

@Field Utils = Utils_create();
@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

@Field final Map MESSAGE_TYPES = [
    TEMPERATURE:         0,
    SET_POINT:           1,
    HEAT_LEVEL:          2,
    MODE:                3,
    AWAY:                4,
    MAX_TEMP:            5,
    MIN_TEMP:            6,
    LOAD:                7,
    POWER_CONNECTED:     8,
    SECONDARY_DISPLAY:   9,
    TIME:                10,
    DATE:                11,
    SUNRISE:             12,
    SUNSET:              13,
    LOCK:                14,
    TEMPERATURE_FORMAT:  15,
    TIME_FORMAT:         16,
    EARLY_START:         17,
    AWAY_TEMP:           18,
    BACKLIGHT_STATE:     19,
    BACKLIGHT_IDLE:      20,
    OUTDOOR_TEMP:        21
]

@Field final Map MODE = [
                         SINOPE_MODE_OFF:   0,
                         SINOPE_MODE_FREEZE_PROTECT:  1,
                         SINOPE_MODE_MANUAL:  2,
                         SINOPE_MODE_AUTO:  3,
                         SINOPE_MODE_AWAY: 5
                        ]

@Field final Map LOCATION = [
                        HOME: "00",
                        AWAY: "02"
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
@Field final Map temperatureFormatOptions = [0:"Celsius", 1:"Fahrenheit"]
@Field final Map timeFormatOptions = [0:"24h", 1:"12h"]
@Field final Map backlightOptions = [0:"Automatic", 100:"Always On"]
@Field final Map earlyStartOptions = [0:"Disabled", 1:"Enabled"]
@Field final Map keypadOptions = [0:"Unlocked", 1:"Locked"]
@Field final Map thermostatOperationalModeOptions = [3:"Automatic", 2:"Manual", 0:"Off"] // values based on Map MODE
@Field final Map temperatureSetPointsCelsius = [5.0:"5.0 C", 5.5:"5.5 C", 6.0:"6.0 C", 6.5:"6.5 C", 7.0:"7.0 C", 7.5:"7.5 C", 8.0:"8.0 C", 8.5:"8.5 C", 9:"9.0 C", 9.5:"9.5 C", 10.0:"10.0 C", 10.5:"10.5 C", 11.0:"11.0 C", 11.5:"11.5 C", 12.0:"12.0 C", 12.5:"12.5 C", 13.0:"13.0 C", 13.5:"13.5 C", 14.0:"14.0 C", 14.5:"14.5 C", 15.0:"15.0 C", 15.5:"15.5 C", 16.0:"16.0 C", 16.5:"16.5 C", 17.0:"17.0 C", 17.5:"17.5 C", 18.0:"18.0 C", 18.5:"18.5 C", 19.0:"19.0 C", 19.5:"19.5 C", 20.0:"20.0 C", 20.5:"20.5 C", 21.0:"21.0 C", 21.5:"21.5 C", 22.0:"22.0 C", 22.5:"22.5 C", 23.0:"23.0 C", 23.5:"23.5 C", 24.0:"24.0 C", 24.5:"24.5 C", 25.0:"25.0 C", 25.5:"25.5 C", 26.0:"26.0 C", 26.5:"26.5 C", 27.0:"27.0 C", 27.5:"27.5 C", 28.0:"28.0 C", 28.5:"28.5 C", 29.0:"29.0 C", 29.5:"29.5 C", 30.0:"30.0 C"]
@Field final Map temperatureSetPointsFarenheit = [41.0:"41 F", 42.0:"42 F", 43.0:"43 F", 44.0:"44 F", 45.0:"45 F", 46.0:"46 F", 47.0:"47 F", 48.0:"48 F", 49.0:"49 F", 50.0:"50 F", 51.0:"51 F", 52.0:"52 F", 53.0:"53 F", 54.0:"54 F", 55.0:"55 F", 56.0:"56 F", 57.0:"57 F", 58.0:"58 F", 59.0:"59 F", 60.0:"60 F", 61.0:"61 F", 62.0:"62 F", 63.0:"63 F", 64.0:"64 F", 65.0:"65 F", 66.0:"66 F", 67.0:"67 F", 68.0:"68 F", 69.0:"69 F", 70.0:"70 F", 71.0:"71 F", 72.0:"72 F", 73.0:"73 F", 74.0:"74 F", 75.0:"75 F", 76.0:"76 F", 77.0:"77 F", 78.0:"78 F", 79.0:"79 F", 80.0:"80 F", 81.0:"81 F", 82.0:"82 F", 83.0:"83 F", 84.0:"84 F", 85.0:"85 F", 86.0:"86 F"]

def driverVer() { return "1.8" }

metadata {
	definition (name: "Sinope Thermostat", namespace: "rferrazguimaraes", author: "Rangner Ferraz Guimaraes")
    { 
        capability "Initialize"
        capability "Actuator"
		capability "Sensor"
		capability "Temperature Measurement"
		capability "Thermostat"
        capability "Polling"
        capability "Refresh"
        
        attribute "outdoorTemp", "string"
        attribute "heatLevel", "number"
        attribute "minimumTemperature", "number"
        attribute "maximumTemperature", "number"
        attribute "awayTemperature", "number"        
        attribute "load", "number"
        attribute "timeFormat", "string"
        attribute "date", "string"
        attribute "time", "string"
        attribute "sunrise", "string"
        attribute "sunset", "string"
        attribute "lock", "string"
        attribute "location", "string"
        attribute "secondaryDisplay", "string"
        attribute "temperatureFormatDisplay", "string"
        attribute "temperatureFormatHub", "string"
        attribute "thermostatOperationalMode", "string"
        attribute "earlyStart", "string"
        attribute "backLightIdle", "number"
        
        attribute "supportedThermostatFanModes", "JSON_OBJECT"
        attribute "supportedThermostatModes", "JSON_OBJECT"        
               
        command "tempUp"
        command "tempDown" 
    }
    
    preferences {
        input name: "pollIntervals", type: "enum", title: "Set the Poll Interval.", options: pollIntervalOptions, defaultValue: "600", required: true 
        input name: "prefThermostatOperationalMode", type: "enum", title: "Thermostat Operational Mode", options: thermostatOperationalModeOptions, defaultValue: "0", required: true
        input name: "prefDisplaySecondary", type: "enum", title: "Secondary Display", options: displaySecondaryOptions, defaultValue: "1", required: true
        input name: "prefDisplayTemperatureFormat", type: "enum", title: "Display Temperature Format", options: temperatureFormatOptions, defaultValue: "0", required: true
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
    initialize()
}

def initialize() {
    Utils.toLogger("debug", "initialize()")
    if (state?.lastRunningMode == null) {
        // Set unused default values (for Google Home Integration)
    	setSupportedThermostatModes(JsonOutput.toJson(["heat", "idle"]))
    	setSupportedThermostatFanModes(JsonOutput.toJson([""]))
    	sendEvent(name: "thermostatMode", value: "heat", isStateChange: true)
    	sendEvent(name: "thermostatOperatingState", value: "idle", isStateChange: true)
    	sendEvent(name: "thermostatFanMode", value: "auto", isStateChange: true)
    	sendEvent(name: "coolingSetpoint", value: FormatTemperature(getMaxTemperature()), isStateChange: true)
        sendEvent(name: "thermostatSetpoint", value: FormatTemperature(18), isStateChange: true)
	    sendEvent(name: "heatingSetpoint", value: FormatTemperature(18), isStateChange: true)
        updateDataValue("lastRunningMode", "heat")
    	state.lastRunningMode = "heat"
    }    
    updateDataValue("driverVersion", driverVer())
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
    parent.set_min_setpoint(device.deviceNetworkId, FormatTemperature(prefMinSetPoint ?: getMinTemperature(), "C"))
    parent.set_max_setpoint(device.deviceNetworkId, FormatTemperature(prefMaxSetPoint ?: getMaxTemperature(), "C"))
    parent.set_away_setpoint(device.deviceNetworkId, FormatTemperature(prefAwaySetPoint ?: getMinTemperature(), "C"))
    set_hvac_mode(prefThermostatOperationalMode.toInteger())
}

def refresh() {
    //Default refresh method which calls immediate update and ensures it is scheduled	
    Utils.toLogger("debug", "refresh()")
	refreshInfo()
}

def refreshHourly() {
    Utils.toLogger("debug", "refreshHourly()")
    //parent.set_hourly_report()
}

def refreshDaily() {
    Utils.toLogger("debug", "refreshDaily()")
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
    def heatingSetpoint = FormatTemperature(device.currentValue("heatingSetpoint"))
    Utils.toLogger("debug", "tempUp() - heatingSetpoint before: ${currentSetpoint}")
    def maxSetpoint = getMaxTemperature()

    if (heatingSetpoint < maxSetpoint) {
        heatingSetpoint += getScaleStep()
        Utils.toLogger("debug", "tempUp() - heatingSetpoint after: ${heatingSetpoint}")
        setHeatingSetpoint(heatingSetpoint)
    }   
}

def tempDown() {
    Utils.toLogger("debug", "tempDown()")
    
    def heatingSetpoint = FormatTemperature(device.currentValue("heatingSetpoint"))
    Utils.toLogger("debug", "tempDown() - heatingSetpoint before: ${heatingSetpoint}")
    def minSetpoint = getMinTemperature()

    if (heatingSetpoint > minSetpoint) {
        heatingSetpoint -= getScaleStep()
        Utils.toLogger("debug", "tempDown() - heatingSetpoint after: ${heatingSetpoint}")
        setHeatingSetpoint(heatingSetpoint)
    }
}

def setTemperature(temperature) {
	Utils.toLogger("debug", "setTemperature(${temperature}) was called")
    sendTemperatureEvent("temperature", temperature)
	runIn(1, manageCycle)
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

def setHeatingSetpoint(setpoint) {
	Utils.toLogger("debug", "setHeatingSetpoint(${setpoint}) was called")
    if (setpoint != null) {        
        def formattedTemperature = FormatTemperature(setpoint)
	    updateSetpoints(null, formattedTemperature, null, null)
        runIn(2, 'updateChidTemp', [data:[temperature: formattedTemperature]])
    }    
}

def updateChidTemp(data) {
    Utils.toLogger("debug", "updateChidTemp(${data.temperature}) was called")
    def temperature = FormatTemperature(data.temperature, "C")
    Utils.toLogger("debug", "updateChidTemp - temperature in celsius: ${temperature}")
    parent.childSetTemp(device.deviceNetworkId, temperature)
}

def setThermostatSetpoint(setpoint) {
	Utils.toLogger("debug", "setThermostatSetpoint(${setpoint}) was called")
	updateSetpoints(setpoint, null, null, null)
}

def setThermostatOperatingState(operatingState) {
	Utils.toLogger("debug", "setThermostatOperatingState (${operatingState}) was called")
	updateSetpoints(null, null, null, operatingState)
	sendEvent(name: "thermostatOperatingState", value: operatingState, descriptionText: getDescriptionText("thermostatOperatingState set to ${operatingState}"))
}

def setSupportedThermostatFanModes(fanModes) {
	Utils.toLogger("debug", "setSupportedThermostatFanModes(${fanModes}) was called")
	// (auto, circulate, on)
	sendEvent(name: "supportedThermostatFanModes", value: fanModes)
}

def setSupportedThermostatModes(modes) {
	Utils.toLogger("debug", "setSupportedThermostatModes(${modes}) was called")
	// (auto, cool, emergency heat, heat, off)
	sendEvent(name: "supportedThermostatModes", value: modes)
}

private sendTemperatureEvent(name, val) {
	sendEvent(name: "${name}", value: val, unit: "°${getTemperatureScale()}", descriptionText: getDescriptionText("${name} is ${val} °${getTemperatureScale()}"), isStateChange: true)
}

//Dont use any of these yet as I havent worked out why they would be needed! 
//Just log that they were triggered for troubleshooting
def heat() {
	Utils.toLogger("debug", "heat()")
    setThermostatMode("heat")
}

def emergencyHeat() {
	Utils.toLogger("debug", "emergencyHeat()")
}

def setThermostatMode(mode) {
	sendEvent(name: "thermostatMode", value: "${mode}", descriptionText: getDescriptionText("thermostatMode is ${mode}"))
	setThermostatOperatingState("idle")
	updateSetpoints(null, null, null, mode)
	runIn(1, manageCycle)
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
    sendEvent(name:"thermostatFanMode", "off")
}

def cool() {
	Utils.toLogger("debug", "cool mode is not available for this device")
}

def setCoolingSetpoint(Double value) {
	Utils.toLogger("debug", "setCoolingSetpoint is not available for this device")
}

def auto() {
	Utils.toLogger("debug", "emergencyHeat mode is not available for this device. => Defaulting to heat mode instead.")
    setThermostatMode("auto")
}

def off() {
	Utils.toLogger("debug", "off()")
    setThermostatMode("off")
}

def processChildResponse(response) {
	//Response received from Neviweb Hub so process it (mainly used for refresh, but could also process the success/fail messages)
    Utils.toLogger("debug", "received processChildResponse deviceID:${response.device} response: ${formatResponse(response)}")
    runIn(1, "executeChildResponse", [overwrite: false, data: [response: response]])
}

def capitalizeFully(String str, String delim="_") { //optional delimiter
    def li = []
    str.split(delim).each { word ->
        li.add(word.capitalize())
    }
    return li.join(delim)
}

def keyForValue(map, value) {
    map.find { it.value == value }?.key
}

def formatResponse(response) {
    def type = capitalizeFully("${keyForValue(MESSAGE_TYPES, response.type)}".toLowerCase())
    def value = response.value
    return "Type:${type} - Value: ${value}"
}

def formatMode(value) {
    def type = capitalizeFully("${keyForValue(MODE, value)}".toLowerCase())
    return type - "Sinope_Mode_"
}

def formatLocation(value) {
    def type = capitalizeFully("${keyForValue(LOCATION, value)}".toLowerCase())
    return type
}

def executeChildResponse(data) {
    def response = data.response
    Utils.toLogger("debug", "received executeChildResponse deviceID:${response.device} response: ${formatResponse(response)}")
    switch(response.type) {
        case [MESSAGE_TYPES.TEMPERATURE]:
            def temperature = FormatTemperature(response.value)
            sendTemperatureEvent("temperature", temperature)
            Utils.toLogger("debug", "received executeChildResponse temperature: ${temperature}")    
            break
        case [MESSAGE_TYPES.SET_POINT]:
            def setPoint = FormatTemperature(response.value)
            updateSetpoints(null, setPoint, null, null)
            runIn(1, manageCycle)
            Utils.toLogger("debug", "received executeChildResponse setPoint: ${setPoint}")
            break
        case [MESSAGE_TYPES.HEAT_LEVEL]:
			sendEvent(name: "heatLevel", value: response.value)
			sendEvent(name: "thermostatOperatingState", value: (response.value > 10) ? "heating" : "idle")
            Utils.toLogger("debug", "received executeChildResponse heatLevel: ${response.value}")
            break
        case [MESSAGE_TYPES.MODE]:        
            setThermostatMode((response.value > 0) ? "heat" : "off")
            sendEvent(name: "thermostatOperationalMode", value: formatMode(response.value))
            Utils.toLogger("debug", "received executeChildResponse thermostatOperationalMode: ${formatMode(response.value)}")
            break
        case [MESSAGE_TYPES.AWAY]:
            sendEvent(name: "location", value: formatLocation(response.value))
            Utils.toLogger("debug", "received executeChildResponse loation: ${formatLocation(response.value)}")
            break
        case [MESSAGE_TYPES.MAX_TEMP]:
            def temperature = FormatTemperature(response.value)
            sendEvent(name: "maximumTemperature", value: temperature)
            Utils.toLogger("debug", "received executeChildResponse tempmax: ${temperature}")
            break
        case [MESSAGE_TYPES.MIN_TEMP]:
            def temperature = FormatTemperature(response.value)
            sendEvent(name: "minimumTemperature", value: temperature)
            Utils.toLogger("debug", "received executeChildResponse tempmin: ${temperature}")
            break        
        case [MESSAGE_TYPES.LOAD]:
            sendEvent(name: "load", value: response.value)
            Utils.toLogger("debug", "received executeChildResponse wattload: ${response.value}")
            break
        case [MESSAGE_TYPES.POWER_CONNECTED]:
            sendEvent(name: "power", value: response.value)
            Utils.toLogger("debug", "received executeChildResponse power: ${response.value}")
            break
        case [MESSAGE_TYPES.OUTDOOR_TEMP]:
            sendEvent(name: "outdoorTemperature", value: response.value)
            Utils.toLogger("debug", "received executeChildResponse outdoorTemp: ${response.value}")
            break
        case [MESSAGE_TYPES.TIME]:
            sendEvent(name: "time", value: response.value)
            Utils.toLogger("debug", "received executeChildResponse time: ${response.value}")
            break
        case [MESSAGE_TYPES.DATE]:
            sendEvent(name: "date", value: response.value)
            Utils.toLogger("debug", "received executeChildResponse date: ${response.value}")
            break
        case [MESSAGE_TYPES.SUNRISE]:
            sendEvent(name: "sunrise", value: response.value)
            Utils.toLogger("debug", "received executeChildResponse sunrise: ${response.value}")
            break
        case [MESSAGE_TYPES.SUNSET]:
            sendEvent(name: "sunset", value: response.value)
            Utils.toLogger("debug", "received executeChildResponse sunset: ${response.value}")
            break
        case [MESSAGE_TYPES.LOCK]:
            sendEvent(name: "lock", value: keypadOptions.get(response.value))
            Utils.toLogger("debug", "received executeChildResponse lock: ${keypadOptions.get(response.value)}")
            break
        case [MESSAGE_TYPES.SECONDARY_DISPLAY]:
            sendEvent(name: "secondaryDisplay", value: displaySecondaryOptions.get(response.value))
            Utils.toLogger("debug", "received executeChildResponse secondary_display: ${displaySecondaryOptions.get(response.value)}")
            break
        case [MESSAGE_TYPES.TEMPERATURE_FORMAT]:
            sendEvent(name: "temperatureFormatDisplay", value: temperatureFormatOptions.get(response.value))
            sendEvent(name: "temperatureFormatHub", value: temperatureFormatOptions.get(isCelsius() ? 0 : 1))
            Utils.toLogger("debug", "received executeChildResponse temperature_format_display: ${temperatureFormatOptions.get(response.value)}")
            Utils.toLogger("debug", "received executeChildResponse temperature_format_hub: ${temperatureFormatOptions.get(isCelsius() ? 0 : 1)}")
            break
        case [MESSAGE_TYPES.TIME_FORMAT]:
            sendEvent(name: "timeFormat", value: timeFormatOptions.get(response.value))
            Utils.toLogger("debug", "received executeChildResponse time_format: ${timeFormatOptions.get(response.value)}")
            break
        case [MESSAGE_TYPES.EARLY_START]:
            sendEvent(name: "earlyStart", value: earlyStartOptions.get(response.value))
            Utils.toLogger("debug", "received executeChildResponse early_start: ${earlyStartOptions.get(response.value)}")
            break
        case [MESSAGE_TYPES.AWAY_TEMP]:
            def temperature = FormatTemperature(response.value)
            sendEvent(name: "awayTemperature", value: temperature)
            Utils.toLogger("debug", "received executeChildResponse awayTemperature: ${temperature}")
            break
        case [MESSAGE_TYPES.BACKLIGHT_STATE]:
            sendEvent(name: "backLightState", value: response.value)
            Utils.toLogger("debug", "received executeChildResponse backLightState: ${response.value}")
            break
        case [MESSAGE_TYPES.BACKLIGHT_IDLE]:
            sendEvent(name: "backLightIdle", value: response.value)
            Utils.toLogger("debug", "received executeChildResponse backLightIdle: ${response.value}")
            break
        default:
            Utils.toLogger("error", "executeChildResponse - Command ${response.type} not found!")
            break
    }
}

private updateSetpoints(sp = null, hsp = null, csp = null, operatingState = null) {
    Utils.toLogger("debug", "updateSetpoints was called sp: ${sp} - hsp: ${hsp} - csp: ${csp} - operatingState: ${operatingState}")
	if (operatingState in ["off"]) return
	if (hsp == null) hsp = FormatTemperature(device.currentValue("heatingSetpoint", true))
	if (csp == null) csp = FormatTemperature(device.currentValue("coolingSetpoint", true))
	if (sp == null) sp = FormatTemperature(device.currentValue("thermostatSetpoint", true))

	if (operatingState == null) operatingState = state.lastRunningMode

	def hspChange = isStateChange(device, "heatingSetpoint", hsp.toString())
	def cspChange = isStateChange(device, "coolingSetpoint", csp.toString())
	def spChange = isStateChange(device, "thermostatSetpoint", sp.toString())
	def osChange = operatingState != state.lastRunningMode

	def newOS
	def descriptionText
	def name
	def value
	def unit = "°${getTemperatureScale()}"
	switch (operatingState) {
		case ["pending heat","heating","heat"]:
			newOS = "heat"
			if (spChange) {
				hspChange = true
				hsp = sp
			} else if (hspChange || osChange) {
				spChange = true
				sp = hsp
			}
			if (csp - 2 < hsp) {
				csp = hsp + 2
				cspChange = true
			}
			break
		case ["pending cool","cooling","cool"]:
			newOS = "cool"
			if (spChange) {
				cspChange = true
				csp = sp
			} else if (cspChange || osChange) {
				spChange = true
				sp = csp
			}
			if (hsp + 2 > csp) {
				hsp = csp - 2
				hspChange = true
			}
			break
		default:
			return
	}

    if (hspChange) {
		value = hsp
		name = "heatingSetpoint"
		descriptionText = "${device.displayName} ${name} was set to ${value}${unit}"
		Utils.toLogger("info", descriptionText)
		sendEvent(name: name, value: value, descriptionText: descriptionText, unit: unit, stateChange: true)
	}
	if (cspChange) {
		value = csp
		name = "coolingSetpoint"
		descriptionText = "${device.displayName} ${name} was set to ${value}${unit}"
		Utils.toLogger("info", descriptionText)
		sendEvent(name: name, value: value, descriptionText: descriptionText, unit: unit, stateChange: true)
	}
	if (spChange) {
		value = sp
		name = "thermostatSetpoint"
		descriptionText = "${device.displayName} ${name} was set to ${value}${unit}"
		Utils.toLogger("info", descriptionText)
		sendEvent(name: name, value: value, descriptionText: descriptionText, unit: unit, stateChange: true)
	}

	state.lastRunningMode = newOS
	updateDataValue("lastRunningMode", newOS)
}

def manageCycle(){
    Utils.toLogger("debug", "manageCycle was called")
	def ambientTempChangePerCycle = 0.25
	def hvacTempChangePerCycle = 0.75

	def hysteresis = (hysteresis ?: 0.5).toBigDecimal()

	def coolingSetpoint = device.currentValue("coolingSetpoint") ?: FormatTemperature(getMaxTemperature())
	def heatingSetpoint = device.currentValue("heatingSetpoint") ?: FormatTemperature(getMinTemperature())
	def temperature = device.currentValue("temperature") ?: FormatTemperature(getMinTemperature())

	def thermostatMode = device.currentValue("thermostatMode") ?: "off"
	def thermostatOperatingState = device.currentValue("thermostatOperatingState") ?: "idle"

	def ambientGain = (temperature + ambientTempChangePerCycle).setScale(2)
	def ambientLoss = (temperature - ambientTempChangePerCycle).setScale(2)
	def coolLoss = (temperature - hvacTempChangePerCycle).setScale(2)
	def heatGain = (temperature + hvacTempChangePerCycle).setScale(2)

	def coolingOn = (temperature >= (coolingSetpoint + hysteresis))
	if (thermostatOperatingState == "cooling") coolingOn = temperature >= (coolingSetpoint - hysteresis)

	def heatingOn = (temperature <= (heatingSetpoint - hysteresis))
	if (thermostatOperatingState == "heating") heatingOn = (temperature <= (heatingSetpoint + hysteresis))
	
	if (thermostatMode == "cool") {
		if (coolingOn && thermostatOperatingState != "cooling") setThermostatOperatingState("cooling")
		else if (thermostatOperatingState != "idle") setThermostatOperatingState("idle")
	} else if (thermostatMode == "heat") {
		if (heatingOn && thermostatOperatingState != "heating") setThermostatOperatingState("heating")
		else if (thermostatOperatingState != "idle") setThermostatOperatingState("idle")
	} else if (thermostatMode == "auto") {
		if (heatingOn && coolingOn) Utils.toLogger("error", "cooling and heating are on - temp:${temperature}")
		else if (coolingOn && thermostatOperatingState != "cooling") setThermostatOperatingState("cooling")
		else if (heatingOn && thermostatOperatingState != "heating") setThermostatOperatingState("heating")
		else if ((!coolingOn || !heatingOn) && thermostatOperatingState != "idle") setThermostatOperatingState("idle")
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
    def type = 10 //temp to cleanup  - based on IMPLEMENTED_DEVICE_TYPES
    parent.set_mode(device.deviceNetworkId, type, hvac_mode)
}

def set_preset_mode(self, preset_mode) {
    // Activate a preset.
    if (preset_mode == self.preset_mode) {
        return
    }
    
    def type = 10 //temp to cleanup - based on IMPLEMENTED_DEVICE_TYPES
    if (preset_mode == PRESET_AWAY) {
        //Set away mode on device, away_on = 2 away_off =0
        parent.set_away_mode(device.deviceNetworkId, 2)
    } else if (preset_mode == PRESET_BYPASS) {
        if (SINOPE_BYPASSABLE_MODES.contains(self._operation_mode)) {
            parent.set_away_mode(device.deviceNetworkId, 0)      
            parent.set_mode(device.deviceNetworkId, type, self._operation_mode | SINOPE_BYPASS_FLAG)
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

float FormatTemperature(temperature) {
    FormatTemperature(temperature, getTemperatureScale())
}

float FormatTemperature(temperature, locationScale) {
    Utils.toLogger("debug", "FormatTemperature - temperature before conversion: ${temperature} - locationScale: ${locationScale}")

    def formattedTemperature = temperature as Float
    
    if(formattedTemperature > SINOPE_MAX_TEMPERATURE_CELSIUS) {
        if (locationScale == "C") { // convert to celsius
            formattedTemperature = fahrenheitToCelsius(formattedTemperature)
            Utils.toLogger("debug", "FormatTemperature - temperature converted to celsius: ${formattedTemperature}")
        } else {
            formattedTemperature = convertTemperatureIfNeeded(formattedTemperature, locationScale, 1).toBigDecimal()
        }
    } else {
        if (locationScale == "F") { // convert to fahrenheit
            formattedTemperature = celsiusToFahrenheit(formattedTemperature)
            Utils.toLogger("debug", "FormatTemperature - temperature converted to fahrenheit: ${formattedTemperature}")
        } else {
            formattedTemperature = convertTemperatureIfNeeded(formattedTemperature, locationScale, 1).toBigDecimal()
        }
    }
    
    if (locationScale == "C") { // celsius
        formattedTemperature = roundToHalf(formattedTemperature)
        Utils.toLogger("debug", "FormatTemperature - temperature roundToHalf celsius: ${formattedTemperature}")
        formattedTemperature = Math.min(SINOPE_MAX_TEMPERATURE_CELSIUS, Math.max(SINOPE_MIN_TEMPERATURE_CELSIUS, formattedTemperature))
    } else { // fahrenheit
        formattedTemperature = (Math.round(formattedTemperature)).toDouble().round(0)
        Utils.toLogger("debug", "FormatTemperature - temperature round fahrenheit: ${formattedTemperature}")
        formattedTemperature = Math.min(SINOPE_MAX_TEMPERATURE_FAHRENHEIT, Math.max(SINOPE_MIN_TEMPERATURE_FAHRENHEIT, formattedTemperature))
    }
    
    Utils.toLogger("debug", "FormatTemperature - temperature after conversion: ${formattedTemperature}")
    
    return formattedTemperature
}

float roundToHalf(float x) {
    return (float) (Math.round(x * 2) / 2);
}

def getTemperatureScale() {
    return "${location.temperatureScale}"
}

boolean isCelsius() {
    return getTemperatureScale() == "C"
}

boolean isFahrenheit() {
    return !isCelsius()
}

float getScaleStep() {
    def step
    
    if (isCelsius()) {
        step = 0.5
    } else {
        step = 1
    }
    
    return step
}

float getMaxTemperature() {
    if(isCelsius()) {
        return SINOPE_MAX_TEMPERATURE_CELSIUS
    } else {
        return SINOPE_MAX_TEMPERATURE_FAHRENHEIT
    }
}

float getMinTemperature() {
    if(isCelsius()) {
        return SINOPE_MIN_TEMPERATURE_CELSIUS
    } else {
        return SINOPE_MIN_TEMPERATURE_FAHRENHEIT
    }
}

private getDescriptionText(msg) {
	def descriptionText = "${device.displayName} ${msg}"
	Utils.toLogger("info", "${descriptionText}")
	return descriptionText
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
