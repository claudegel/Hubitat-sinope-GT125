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
 *  Version: 1.0
 *  
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

@Field final List SUPPORTED_HVAC_MODES = ["HVAC_MODE_OFF", "HVAC_MODE_AUTO", "HVAC_MODE_HEAT"]

@Field final String PRESET_BYPASS = 'temporary'
@Field final List PRESET_MODES = ["PRESET_NONE", "PRESET_AWAY", "PRESET_BYPASS"]

@Field final List IMPLEMENTED_DEVICE_TYPES = [10, 20, 21]

metadata {
	definition (name: "Sinope Thermostat", namespace: "rferrazguimaraes", author: "Rangner Ferraz Guimaraes")
    { 
        capability "Actuator"
        capability "Sensor"
        capability "Thermostat"
		capability "TemperatureMeasurement"
        capability "Polling"
		capability "Refresh"
        
		attribute "outdoorTemp", "string"
		attribute "outputPercentDisplay", "number"
        command "tempUp"
        command "tempDown"
        command "test"
	}
    
    preferences {
        input name: "pollIntervals", type: "enum", title: "Set the Poll Interval.", options: [0:"off", 300:"5 minutes", 600:"10 minutes", 900:"15 minutes", 1800:"30 minutes", 3600:"60 minutes"], required: true, defaultValue: "600"
        //input name: "prefDisplayOutdoorTemp", type: "bool", title: "Enable display of outdoor temperature", defaultValue: true
        //input name: "prefDisplayClock", type: "bool", title: "Enable display of clock", defaultValue: true
        //input name: "prefDisplayBacklight", type: "bool", title: "Enable display backlight", defaultValue: true
        //input name: "prefKeyLock", type: "bool", title: "Enable keylock", defaultValue: false
    	input("logDebug", "bool", title: "Enable debug logging", defaultValue: false)
    	input("logWarn", "bool", title: "Enable warn logging", defaultValue: false)
    	input("logError", "bool", title: "Enable error logging", defaultValue: false)
    }
}

def installed(){
    log_debug("Installed device.")
    configure()
}

def configure(){    
    log.warn "configure..."
	//logDebug "binding to Thermostat cluster"
    //unschedule()
    //schedule("0 0 0/1 1/1 * ? *", refreshHourly)
    //schedule("0 0 3 1/1 * ? *", refreshDaily)
    //schedule("0 0/5 * 1/1 * ? *", refreshInfo)

    // Set unused default values (for Google Home Integration)
	sendEvent(name: "thermostatMode", value: "off", isStateChange: true)
	sendEvent(name: "thermostatFanMode", value: "auto", isStateChange: true)
	sendEvent(name: "thermostatOperatingState", value: "idle", isStateChange: true)
	sendEvent(name: "thermostatSetpoint", value: 18, isStateChange: true)
	sendEvent(name: "heatingSetpoint", value: 18, isStateChange: true)
	sendEvent(name: "coolingSetpoint", value: 30, isStateChange: true)
    sendEvent(name: "temperature", value: 20, isStateChange: true)
    updateDataValue("lastRunningMode", "heat")

    poll()
}

def refresh(){
    //Default refresh method which calls immediate update and ensures it is scheduled	
	refreshInfo()
}

def refreshHourly(){
    //parent.set_hourly_report()
}

def refreshDaily(){
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
    def step = 0.5 
    def targetTemp = device.currentValue("thermostatSetpoint")
    def value = targetTemp + step
  	parent.childSetTemp(value, device.deviceNetworkId)
}

def tempDown() {
    log_debug("tempDown()")
    def step = 0.5
    def targetTemp = device.currentValue("thermostatSetpoint")
    def value = targetTemp - step
  	parent.childSetTemp(value, device.deviceNetworkId)
}

def test(){    
    log_warn("teste...")
    refresh()
}

def poll() {
    pollInterval = pollIntervals.toInteger() ?: 0
    unschedule()
    if(pollInterval > 0)
    {
        runIn(pollInterval, poll) 
        log_debug("in poll: (every ${pollInterval} seconds)")
        refresh()
    }
}

def setHeatingSetpoint(temperature) {
    log_debug("setHeatingSetpoint()")
	parent.childSetTemp(temperature, device.deviceNetworkId)
}

def setThermostatSetpoint(temperature) {
    log_debug("setThermostatSetpoint()")
	parent.childSetTemp(temperature, device.deviceNetworkId)
}

def getTemperatureScale() {
    return getDataValue("temperatureScale")
}

//Dont use any of these yet as I havent worked out why they would be needed! 
//Just log that they were triggered for troubleshooting
def heat() {
	log_debug("heat()")
}

def emergencyHeat() {
	log_debug("emergencyHeat()")
}

def setThermostatMode(thermostatMode) {
	log_debug("setThermostatMode() - ${thermostatMode}")
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

def setThermostatFanMode(fanMode) {
	log_debug("setThermostatFanMode is not available for this device - ${fanMode}")
}

def cool() {
	log_debug("cool mode is not available for this device")
}

def setCoolingSetpoint(temperature) {
	log_debug("setCoolingSetpoint is not available for this device")
}

def setSchedule(schedule){
    log_debug("setSchedule is not available for this device")
}

def auto() {
	log_debug("emergencyHeat mode is not available for this device. => Defaulting to heat mode instead.")
    heat()
}

def off() {
	log_debug("off()")
}

def processChildResponse(response)
{
	//Response received from Neviweb Hub so process it (mainly used for refresh, but could also process the success/fail messages)
    log_debug("received processChildResponse response: ${response} unit: ${location?.getTemperatureScale()}")
    switch(response.type) {
        case "temperature":
            def temp = roundToHalf(response.value)
            sendEvent(name: "temperature", value: temp, unit: location?.getTemperatureScale())
            sendEvent(name:"thermostatMode", value: "heat")
            log_debug("received processChildResponse temperature: ${temp}")        
            break
        case "set_point":
            def temp = roundToHalf(response.value)
            sendEvent(name: "thermostatSetpoint", value: temp, unit: location?.getTemperatureScale())
            sendEvent(name: "heatingSetpoint", value: temp, unit: location?.getTemperatureScale())
            sendEvent(name: "coolingSetpoint", value: temp, unit: location?.getTemperatureScale())
            sendEvent(name:"thermostatMode", value: "heat")
            log_debug("received processChildResponse setPoint: ${temp}")
            break
        case "heat_level":
			sendEvent(name: "outputPercentDisplay", value: response.value)
			sendEvent(name: "thermostatOperatingState", value: ((heatLevel > 10) ? "heating" : "idle"))
            log_debug("received processChildResponse heatLevel: ${response.value}")
            break
        case "mode":
            sendEvent(name:"thermostatMode", value: ((response.value > 0) ? "heat" : "off"))
            updateDataValue("lastRunningMode", ((response.value > 0) ? "heat" : "off")) // heat is the only compatible mode for this device
            log_debug("received processChildResponse mode: ${response.value}")
            break
        case "away":
            sendEvent(name: "away", value: response.value)
            log_debug("received processChildResponse away: ${response.value}")
            break
        case "max_temp":
            def temp = roundToHalf(response.value)
            sendEvent(name: "maximumTemperature", value: temp)
            log_debug("received processChildResponse tempmax: ${temp}")
            break
        case "min_temp":
            def temp = roundToHalf(response.value)
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
        default:
            log_error("processChildResponse - Command not found!")
            break
    }
}

def float roundToHalf(float x) {
    return (float) (Math.ceil(x * 2) / 2);
}

def set_outside_temperature(self, outside_temperature){
    //Send command to set new outside temperature.
    if (outside_temperature == None){
        return
    }
    
    parent.set_hourly_report(device.deviceNetworkId, outside_temperature)
    self._outside_temperature = outside_temperature
}

def async_set_outside_temperature(self, outside_temperature){
    //Send command to set new outside temperature.
    parent.set_hourly_report(device.deviceNetworkId, outside_temperature)
    //self._outside_temperature = outside_temperature
}

def set_hvac_mode(self, hvac_mode){
    //Set new hvac mode.
    parent.send_time(device.deviceNetworkId)
    self._type = 10 //temp to cleanup
    if (hvac_mode == HVAC_MODE_OFF){
        parent.set_mode(device.deviceNetworkId, self._type, SINOPE_MODE_OFF)
    } else if (hvac_mode == HVAC_MODE_HEAT) {
        parent.set_mode(device.deviceNetworkId, self._type, SINOPE_MODE_MANUAL)
    } else if (hvac_mode == HVAC_MODE_AUTO){
        parent.set_mode(device.deviceNetworkId, self._type, SINOPE_MODE_AUTO)
    } else {
        log_error("Unable to set hvac mode: %s.", hvac_mode)
    }
}

def set_preset_mode(self, preset_mode){
    // Activate a preset.
    if (preset_mode == self.preset_mode){
        return
    }
    
    self._type = 10 //temp to cleanup
    if (preset_mode == PRESET_AWAY) {
        //Set away mode on device, away_on = 2 away_off =0
        parent.set_away_mode(device.deviceNetworkId, 2)
    } else if (preset_mode == PRESET_BYPASS){
        if (SINOPE_BYPASSABLE_MODES.contains(self._operation_mode)){
            parentset_away_mode(device.deviceNetworkId, 0)      
            parent.set_mode(device.deviceNetworkId, self._type, self._operation_mode | SINOPE_BYPASS_FLAG)
        } else if (preset_mode == PRESET_NONE) {
            // Re-apply current hvac_mode without any preset
            parent.set_away_mode(device.deviceNetworkId, 0)
            self.set_hvac_mode(self.hvac_mode)
        } else {
            log_error("Unable to set preset mode: %s.", preset_mode)
        }
    }
}

private timeStringToMins(String timeString){
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

private timeStringToHoursMins(String timeString){
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

def to_celcius(temp,unit){ //unit = K for kelvin, C for celcius, F for farenheight
    if (unit == "C"){
        return round((temp-32)*0.5555, 2)
    }else{
        return round((temp-273.15),2)
    }
}

def from_celcius(temp){
    return round((temp+1.8)+32, 2)
}

def FormatTemp(temp,invert){
	if (temp!=null){
		if(invert){
			float i=Float.parseFloat(temp+"")
			switch (location?.getTemperatureScale()) {
				case "C":
					return i.round(2)
				break;

				case "F":
					return (Math.round(fToC(i))).toDouble().round(2)
				break;
			}

		}else{

			float i=Float.parseFloat(temp+"")
			switch (location?.getTemperatureScale()) {
				case "C":
					return i.round(2)
				break;

				case "F":
					return (Math.round(cToF(i))).toDouble().round(0)
				break;
			}
		}
    }else{
    	return null
    }
}

def cToF(temp) {
	return ((( 9 * temp ) / 5 ) + 32)
}

def fToC(temp) {
	return ((( temp - 32 ) * 5 ) / 9)
}

def log_debug(logData)
{
    if (parent.childGetDebugState() || logDebug) log.debug("Thermostat - " + logData)
}

def log_warn(logData)
{
    if (parent.childGetWarnState() || logWarn) log.warn("Thermostat - " + logData)
}

def log_error(logData)
{
    if (parent.childGetErrorState() ||logError) log.error("Thermostat - " + logData)
}
