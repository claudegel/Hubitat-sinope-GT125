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
 *  Sinope Neviweb Hub (Parent Device of Sinope Thermostat)
 *
 *  Author: Rangner Ferraz Guimaraes
 *  Date: 2020-11-22
 *  Version: 1.0 - Initial commit
 *  Version: 1.1 - Fixed thread issues + added options to thermostat
 *  Version: 1.2 - Fixed zero length response introduced by firmware 2.2.6
 *  Version: 1.3 - Fixed how the parameters are set (firmware update broke it)
 *  Version: 1.4 - Decreased the amount of overload messages (login and close connection messages), changed log system
 *  Version: 1.5 - Major refactor to fix overloading hub error message
 *  Version: 1.6 - Fixed new installation
 */

import groovy.transform.Field
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import hubitat.helper.HexUtils
import hubitat.helper.InterfaceUtils
import hubitat.device.Protocol

@Field Utils = Utils_create();
@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

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

@Field final Map UPDATE_TYPES = [
    READ:         0,
    WRITE:        1,
    REPORT:       2
]

@Field final Map RESPONSE_TYPES = [
    GET_API_KEY:      0,
    ADD_THERMOSTAT:   1,
    SEND_REQUEST:     2,
    DATA_WRITE_1:     3,
    DATA_WRITE_2:     4,
    DATA_READ_1:      5,
    DATA_READ_2:      6,
    DATA_REPORT_1:    7,
    DATA_REPORT_2:    8,
    LOGIN_REQUEST:    9,
    CLOSE_CONNECTION: 10
]

@Field final static String all_unit = "FFFFFFFF"
//sequential number to identify the current request. Could be any unique number that is different at each request
// could we use timestamp value ?
@Field final static int seq_num = 12345678 
@Field final static int seq = 0

// command type
@Field final static String data_read_command = "4002"
@Field final static String data_report_command = "4202"
@Field final static String data_write_command = "4402"

//thermostat data read
@Field final static String data_heat_level = "20020000" //0 to 100%
@Field final static String data_mode = "11020000" // 0=off, 1=freeze protect, 2=manual, 3=auto, 5=away
@Field final static String data_temperature = "03020000" //room temperature
@Field final static String data_setpoint = "08020000" //thermostat set point
@Field final static String data_away = "00070000"  //set device mode to away, 0=home, 2=away

//thermostat info read
@Field final static String data_temperature_format = "00090000" // 0 = celcius, 1 = fahrenheit
@Field final static String data_time_format = "01090000" // 0 = 24h, 1 = 12h
@Field final static String data_load = "000D0000" // 0-65519 watt, 1=1 watt, (2 bytes)
@Field final static String data_secondary_display = "30090000" // 0 = default setpoint, 1 = outdoor temp.
@Field final static String data_min_temp = "0A020000" // Minimum room setpoint, 5-30oC (2 bytes)
@Field final static String data_max_temp = "0B020000" // Maximum room setpoint, 5-30oC (2 bytes)
@Field final static String data_away_temp = "0C020000" // away room setpoint, 5-30oC (2 bytes)

// thermostat data report
@Field final static String data_outdoor_temperature = "04020000" //to show on thermostat, must be sent at least every hour
@Field final static String data_time = "00060000" //must be sent at least once a day or before write request for auto mode
@Field final static String data_date = "01060000"
@Field final static String data_sunrise = "20060000" //must be sent once a day
@Field final static String data_sunset = "21060000" //must be sent once a day

// thermostat data write
@Field final static String data_early_start = "60080000"  //0=disabled, 1=enabled
@Field final static String data_backlight = "0B090000" // variable  = intensity adjustable for TH1300RF
// values, 0 = always on, 1 = variable idle/on in use, 2 = off idle/variabe in use, 3 = always variable
@Field final static String data_backlight_idle = "09090000" // intensity when idle, 0 == off to 100 = always on

// light and dimmer
@Field final static String data_light_intensity = "00100000"  // 0 to 100, off to on, 101 = last level
@Field final static String data_light_mode = "09100000"  // 1=manual, 2=auto, 3=random or away, 130= bypass auto
@Field final static String data_light_timer = "000F0000"   // time in minutes the light will stay on 0--255
@Field final static String data_light_event = "010F0000"  //0= no event sent, 1=timer active, 2= event sent for turn_on or turn_off

// Power control
@Field final static String data_power_intensity = "00100000"  // 0 to 100, off to on
@Field final static String data_power_mode = "09100000"  // 1=manual, 2=auto, 3=random or away, 130= bypass auto
@Field final static String data_power_connected = "000D0000" // actual load connected to the device
@Field final static String data_power_load = "020D0000" // load used by the device
@Field final static String data_power_event = "010F0000"  //0= no event sent, 1=timer active, 2= event sent for turn_on or turn_off
@Field final static String data_power_timer = "000F0000" // time in minutes the power will stay on 0--255

// general
@Field final static String data_lock = "02090000" // 0 = unlock, 1 = lock, for keyboard device
@Field final static List crc8Table = [	0x00, 0x07, 0x0e, 0x09, 0x1c, 0x1b, 0x12, 0x15,
                                        0x38, 0x3f, 0x36, 0x31, 0x24, 0x23, 0x2a, 0x2d,
                                        0x70, 0x77, 0x7e, 0x79, 0x6c, 0x6b, 0x62, 0x65,
                                        0x48, 0x4f, 0x46, 0x41, 0x54, 0x53, 0x5a, 0x5d,
                                        0xe0, 0xe7, 0xee, 0xe9, 0xfc, 0xfb, 0xf2, 0xf5,
                                        0xd8, 0xdf, 0xd6, 0xd1, 0xc4, 0xc3, 0xca, 0xcd,
                                        0x90, 0x97, 0x9e, 0x99, 0x8c, 0x8b, 0x82, 0x85,
                                        0xa8, 0xaf, 0xa6, 0xa1, 0xb4, 0xb3, 0xba, 0xbd,
                                        0xc7, 0xc0, 0xc9, 0xce, 0xdb, 0xdc, 0xd5, 0xd2,
                                        0xff, 0xf8, 0xf1, 0xf6, 0xe3, 0xe4, 0xed, 0xea,
                                        0xb7, 0xb0, 0xb9, 0xbe, 0xab, 0xac, 0xa5, 0xa2,
                                        0x8f, 0x88, 0x81, 0x86, 0x93, 0x94, 0x9d, 0x9a,
                                        0x27, 0x20, 0x29, 0x2e, 0x3b, 0x3c, 0x35, 0x32,
                                        0x1f, 0x18, 0x11, 0x16, 0x03, 0x04, 0x0d, 0x0a,
                                        0x57, 0x50, 0x59, 0x5e, 0x4b, 0x4c, 0x45, 0x42,
                                        0x6f, 0x68, 0x61, 0x66, 0x73, 0x74, 0x7d, 0x7a,
                                        0x89, 0x8e, 0x87, 0x80, 0x95, 0x92, 0x9b, 0x9c,
                                        0xb1, 0xb6, 0xbf, 0xb8, 0xad, 0xaa, 0xa3, 0xa4,
                                        0xf9, 0xfe, 0xf7, 0xf0, 0xe5, 0xe2, 0xeb, 0xec,
                                        0xc1, 0xc6, 0xcf, 0xc8, 0xdd, 0xda, 0xd3, 0xd4,
                                        0x69, 0x6e, 0x67, 0x60, 0x75, 0x72, 0x7b, 0x7c,
                                        0x51, 0x56, 0x5f, 0x58, 0x4d, 0x4a, 0x43, 0x44,
                                        0x19, 0x1e, 0x17, 0x10, 0x05, 0x02, 0x0b, 0x0c,
                                        0x21, 0x26, 0x2f, 0x28, 0x3d, 0x3a, 0x33, 0x34,
                                        0x4e, 0x49, 0x40, 0x47, 0x52, 0x55, 0x5c, 0x5b,
                                        0x76, 0x71, 0x78, 0x7f, 0x6a, 0x6d, 0x64, 0x63,
                                        0x3e, 0x39, 0x30, 0x37, 0x22, 0x25, 0x2c, 0x2b,
                                        0x06, 0x01, 0x08, 0x0f, 0x1a, 0x1d, 0x14, 0x13,
                                        0xae, 0xa9, 0xa0, 0xa7, 0xb2, 0xb5, 0xbc, 0xbb,
                                        0x96, 0x91, 0x98, 0x9f, 0x8a, 0x8d, 0x84, 0x83,
                                        0xde, 0xd9, 0xd0, 0xd7, 0xc2, 0xc5, 0xcc, 0xcb,
                                        0xe6, 0xe1, 0xe8, 0xef, 0xfa, 0xfd, 0xf4, 0xf3]

@Field static java.util.concurrent.ConcurrentLinkedQueue commandQueue = new java.util.concurrent.ConcurrentLinkedQueue()
@Field static java.util.concurrent.Semaphore mutex = new java.util.concurrent.Semaphore(1)
@Field static java.util.concurrent.Semaphore mutexSendCommand = new java.util.concurrent.Semaphore(1)
@Field static String socketState = "closed"
@Field static long lastLockQuery = 0
@Field static int queueSize = 0
@Field static int socketErrors = 0

def driverVer() { return "1.6" }

metadata {
    definition(name: "Sinope Neviweb Hub", namespace: "rferrazguimaraes", author: "Rangner Ferraz Guimaraes") {
        capability "Refresh"
        capability "Configuration"

        command "getAPIKey"        
        command "addThermostat"
        command "removeAllThermostats"
    }

    preferences {
        input("sinopehubip", "string", title: "Neviweb Hub IP Address", description: "e.g. 192.168.1.X", required: true)
        input("sinopehubport", "string", title: "Neviweb Hub IP Port", description: "Default 4550", required: true, defaultValue: 4550)
        input("sinopehubid", "string", title: "Neviweb Hub ID (no spaces)", required: true)
        input name: "pollIntervals", type: "enum", title: "Set the Poll Interval.", options: [0:"off", 60:"1 minute", 120:"2 minutes", 300:"5 minutes"], required: true, defaultValue: "300"
        input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: true
    }
}

def refresh() {
    Utils.toLogger("debug", "Refreshing all children (done from Bridge)")
    getChildDevices().each
    {
        def dni = it.deviceNetworkId
        if (dni != null)
        {
            Utils.toLogger("debug", "Requesting (${counter}) updated temperature information for ${dni}")
            it.refreshInfo()
        }
    }
}

def initialize() {
    Utils.toLogger("debug", "initialize()")
    configure()
}

def installed() {
    Utils.toLogger("debug", "installed()")
    configure()
}

def uninstalled() {
    Utils.toLogger("debug", "uninstalled()")
    removeAllThermostats()
}

def updated() {
    Utils.toLogger("debug", "updated()")
    configure()    
}

def configure() {    
    Utils.toLogger("warn", "configure...")
    updateDataValue("driverVersion", driverVer())
    resetPoolCommand()
    unschedule()
    schedule("0/1 * * * * ?", runAllActions1Sec)
    schedule("0 0 3 1/1 * ? *", dailyReport) // at 3:00 AM
}

def addThermostat() {
    synchronized(mutexSendCommand) {
        Utils.toLogger("debug", "Adding Thermostat")
        resetCloseSocketTimer()
        sendCommand(makeRequest(RESPONSE_TYPES.SEND_REQUEST, byteArrayToHexString(login_request())))
        sendCommand(makeRequest(RESPONSE_TYPES.ADD_THERMOSTAT, ""))
    }
}

def removeAllThermostats() {
    Utils.toLogger("debug", "Removing All Child Thermostats")
    try {
        getChildDevices()?.each {
            try {
                deleteChildDevice(it.deviceNetworkId)
            } catch (e) {
                Utils.toLogger("error", "Error deleting ${it.deviceNetworkId}, probably locked into a SmartApp: ${e}")
            }
        }
    } catch (err) {
        Utils.toLogger("debug", "Either no children exist or error finding child devices for some reason: ${err}")
    }
}


def getDeviceIDfromDNI(dni)
{
    //Get back to the device ID (Sinope name) from the device ID (child device DNI)
    return dni.replaceAll("Sinope-", "") //.replaceAll(".", " ")
}

//These functions can be called by the children in order to request something from the bridge
def childRequestingRefresh(String dni) {
    //Send Refresh command - this will occur for all thermostats, not just the one which requested it
    def deviceID = getDeviceIDfromDNI(dni)
    Utils.toLogger("debug", "Requesting refreshed info for ${dni}")
    
    sendRequest(self, data_read_request(data_read_command, deviceID, data_mode))
    sendRequest(self, data_read_request(data_read_command, deviceID, data_temperature))
    sendRequest(self, data_read_request(data_read_command, deviceID, data_setpoint))
    sendRequest(self, data_read_request(data_read_command, deviceID, data_heat_level))
    sendRequest(self, data_read_request(data_read_command, deviceID, data_away))
    sendRequest(self, data_read_request(data_read_command, deviceID, data_min_temp))
    sendRequest(self, data_read_request(data_read_command, deviceID, data_max_temp))
    sendRequest(self, data_read_request(data_read_command, deviceID, data_load))
}

def dailyReport() {
    resetPoolCommand() // reset the queue so we are able to set the correct time (sometimes it takes too long depending on the queue size)
    set_daily_report(device)
}

def childSetTemp(String dni, float temperature) {
    //Send Child Set Temp command
    def deviceID = getDeviceIDfromDNI(dni)
    Utils.toLogger("debug", "Requesting ${temperature} degrees for child ${deviceID}")
    sendRequest(dni, data_write_request(data_write_command, deviceID, data_setpoint, set_temperature(temperature)))
}

def get_climate_device_data(String dni) {
    Utils.toLogger("debug", "get_climate_device_data")
     // Get device data
     // send requests
    try {
        def deviceID = getDeviceIDfromDNI(dni)
        sendRequest(self, data_read_request(data_read_command, deviceID, data_temperature))
        sendRequest(self, data_read_request(data_read_command, deviceID, data_setpoint))
        sendRequest(self, data_read_request(data_read_command, deviceID, data_heat_level))
        sendRequest(self, data_read_request(data_read_command, deviceID, data_mode))
        sendRequest(self, data_read_request(data_read_command, deviceID, data_away))
    } catch(e) {
        Utils.toLogger("debug", "Cannot get climate data")
    }
}

def get_climate_device_info(String dni) {
    // Get information for this device
    // send requests
    try{
        def deviceID = getDeviceIDfromDNI(dni)
        sendRequest(self, data_read_request(data_read_command, deviceID, data_min_temp))
        sendRequest(self, data_read_request(data_read_command, deviceID, data_max_temp))
        sendRequest(self, data_read_request(data_read_command, deviceID, data_load))
        sendRequest(self, data_read_request(data_read_command, deviceID, data_power_connected))
    } catch(e) {
        Utils.toLogger("debug", "Cannot get climate info")
    }
}

def get_power_load(data) { // get power in watt use by the device
    def sequence = data[12..19]
    def deviceID = data[26..33]
    def status = data[20..21]
    if (status != "0A") {
        Utils.toLogger("debug", "Status code: ${status} (Wrong answer ? ${deviceID}) ${data}")
        return None // device didn't answer, wrong device
    } else {
        def tc2 = data[46..47]
        def tc4 = data[48..49]
        def lepower = tc4+tc2
        return hexToFloatInt(lepower)
        //return int(float.fromhex(lepower))
    }
}

def get_temperature(data) {
    Utils.toLogger("debug", "get_temperature data: ${data}")
    def sequence = data[12..19]
    Utils.toLogger("debug", "get_temperature sequence: ${sequence}")
    def deviceID = data[26..33]
    Utils.toLogger("debug", "get_temperature deviceID: ${deviceID}")
    def status = data[20..21]
    Utils.toLogger("debug", "get_temperature status: ${status}")
    if (status != "0A") {
        Utils.toLogger("debug", "Status code: ${status} (device didn't answer, wrong device ${deviceID}), Data:(${data})", status, deviceID, data)
        return None // device didn't answer, wrong device
    } else {
        def tc2 = data[46..47]
        def tc4 = data[48..49]
        def latemp = tc4+tc2
        if (latemp == "7FFC" || latemp == "7FFA") {
            Utils.toLogger("debug", "Error code: ${latemp} (None or invalid value for ${deviceID}), Data:(${data})", latemp, deviceID, data)
            return 0
        } else if (latemp == "7ff8" || latemp == "7FFF") {
            Utils.toLogger("debug", "Error code: ${latemp} (Temperature higher than maximum range for ${deviceID})", latemp, deviceID)
            return 0
        } else if (latemp == "7FF9" || latemp == "8000" || latemp == "8001") {
            Utils.toLogger("debug", "Error code: ${latemp} (Temperature lower than minimum range for ${deviceID})", latemp, deviceID)
            return 0
        } else if (latemp == "7FF6" || latemp == "7FF7" || latemp == "7FFd" || latemp == "7FFE") {
            Utils.toLogger("debug", "Error code: ${latemp} (Defective device temperature sensor for ${deviceID}), Data:(${data})", latemp, deviceID, data)
            return 0
        } else if (latemp == "7FFb") {
            Utils.toLogger("debug", "Error code: ${latemp} (Overload for ${deviceID})", latemp, deviceID)
            return 0
        } else if (latemp == "7FF5") {
            Utils.toLogger("debug", "Error code: ${latemp} (Internal error for ${deviceID}), Data:(${data})", latemp, deviceID, data)
            return 0
        } else {
            return Math.round(hexToInt(latemp)* 0.01 * 100) / 100
            //round(hexToInt(latemp)*0.01, 2)
        }
    }
}

def get_away(data) {
    def sequence = data[12..19]
    def deviceID = data[26..33]
    def status = data[20..21]
    if (status != "0A") {
        Utils.toLogger("debug", "Status code: ${status} (device didn't answer, wrong device ${deviceID}) Data:(${data})")
        return None // device didn't answer, wrong device
    } else {
        tc2 = data[46..47]
        //return int(float.fromhex(tc2))
    }
}

def put_mode(mode) { //0=off,1=freeze protect,2=manual,3=auto,5=away
    return "01"+byteArrayToHexString(packInt(mode)[0..0] as byte[])
}
 
def get_mode(data) {
    def sequence = data[12..19]
    def deviceID = data[26..33]
    def status = data[20..21]
    if (status != "0A") {
        Utils.toLogger("error", "Status code: ${status} (Wrong answer for: ${deviceID}) Data:(${data})")
        return None // device didn't answer, wrong device
    } else {
        def tc2 = data[46..47]
        Utils.toLogger("debug", "get_mode sequence: ${sequence} deviceID: ${deviceID}) status: ${status} tc2: ${tc2}")
        return hexToFloatInt(tc2)
        //return int(float.fromhex(tc2))
    }
}

def get_dst(timeZone) { // daylight saving time is on or not
    if (timeZone.useDaylightTime() && timeZone.inDaylightTime(new Date())) {
        return 128
    }
    return 0
}

def set_away(away) { //0=home,2=away
    return "01" + byteArrayToHexString(packInt(away)[0..0] as byte[])
}

def set_away_mode(String dni, away) {
    // Set device away mode. We need to send time before sending new away mode
    try{
        def tz = location.timeZone
        if (dni == "all") {
            def deviceID = "FFFFFFFF"
            sendRequest(dni, data_report_request(data_report_command, deviceID, data_time, set_time(tz)))
            sendRequest(dni, data_report_request(data_report_command, deviceID, data_away, set_away(away)))
        } else {
            def deviceID = getDeviceIDfromDNI(dni)
            sendRequest(dni, data_report_request(data_report_command, deviceID, data_time, set_time(tz)))
            sendRequest(dni, data_write_request(data_write_command, deviceID, data_away, set_away(away)))
        }
    } catch(e) {
        Utils.toLogger("error", "Cannot set device away")
    } 
}

def set_all_away(self, away) {
    //Set all devices to away mode 0=home, 2=away
    try {
        sendRequest(self, data_report_request(data_report_command, all_unit, data_away, set_away(away)))
    } catch (e) {
        Utils.toLogger("error", "Cannot set all devices to away or home mode: ${e}")
    }
}

def get_light_state(data) {
    def sequence = data[12..19]
    def deviceID = data[26..33]
    def status = data[20..21]
    if (status != "0A") {
        Utils.toLogger("error", "get_light_state - Status code: ${status} (Wrong answer for: ${deviceID}) Data:(${data})")
        return None // device didn't answer, wrong device
    } else {
        def tc2 = data[46..47]
        return hexToFloatInt(tc2)
    }
}

def set_light_state(state) { // 0,1,2,3
    return "01" + byteArrayToHexString(packInt(state)[0..0] as byte[])
}

def get_light_idle(data) {
    def sequence = data[12..19]
    def deviceID = data[26..33]
    def status = data[20..21]
    if (status != "0A") {
        Utils.toLogger("error", "get_light_idle - Status code: ${status} (Wrong answer for: ${deviceID}) Data:(${data})")
        return None // device didn't answer, wrong device
    } else {
        def tc2 = data[46..47]
        return hexToFloatInt(tc2)
    }
}

def set_light_idle(level) { // 0 to 100
    return "01" + byteArrayToHexString(packInt(level)[0..0] as byte[])
}

def set_backlight_state(String dni, state) {
    // set backlight state, 0 = full intensity, 1 = variable intensity when idle, 2 = off when idle, 3 = always variable intensity
    try {
        def deviceID = getDeviceIDfromDNI(dni)
        sendRequest(self, data_write_request(data_write_command, deviceID, data_backlight, set_light_state(state)))
    } catch (e) {
        Utils.toLogger("error", "Cannot change backlight device state: ${e}")
    }
}

def set_backlight_idle(String dni, level) {
    // Set backlight intensity when idle, 0 off to 100 full
    try {
        def deviceID = getDeviceIDfromDNI(dni)
        sendRequest(self, data_write_request(data_write_command, deviceID, data_backlight_idle, set_light_idle(level)))
    } catch (e) {
        Utils.toLogger("error", "Cannot change device backlight level: ${e}")
    }
}

def get_lock(data) {
    def sequence = data[12..19]
    def deviceID = data[26..33]
    def status = data[20..21]
    if (status != "0A") {
        Utils.toLogger("error", "get_lock - Status code: ${status} (Wrong answer for: ${deviceID}) Data:(${data})")
        return None // device didn't answer, wrong device
    } else {
        def tc2 = data[46..47]
        return hexToFloatInt(tc2)
    }
}

def set_lock(lock) {
    return "01" + byteArrayToHexString(packInt(lock)[0..0] as byte[])
}

def set_keyboard_lock(String dni, lock) {
    //lock/unlock device keyboard, unlock=0, lock=1
    try {
        def deviceID = getDeviceIDfromDNI(dni)
        sendRequest(dni, data_write_request(data_write_command, deviceID, data_lock, set_lock(lock)))
    } catch (e) {
        Utils.toLogger("error", "Cannot change lock device state: ${e}")
    }
}

def get_secondary_display(data) {
    def sequence = data[12..19]
    def deviceID = data[26..33]
    def status = data[20..21]
    if (status != "0A") {
        Utils.toLogger("error", "get_secondary_display - Status code: ${status} (Wrong answer for: ${deviceID}) Data:(${data})")
        return None // device didn't answer, wrong device
    } else {
        def tc2 = data[46..47]
        return hexToFloatInt(tc2)
    }
}

def set_secondary_display(display) { // 0 = default setpoint, 1 = outdoor temp.
    return "01" + byteArrayToHexString(packInt(display)[0..0] as byte[])
}

def set_secondary_display(String dni, display) {
    // 0 = default setpoint, 1 = outdoor temp.
    try {
        def deviceID = getDeviceIDfromDNI(dni)
        sendRequest(dni, data_write_request(data_write_command, deviceID, data_secondary_display, set_secondary_display(display)))
    } catch (e) {
        Utils.toLogger("error", "Cannot change secondary display: ${e}")
    }
}

def get_temperature_format(data) {
    def sequence = data[12..19]
    def deviceID = data[26..33]
    def status = data[20..21]
    if (status != "0A") {
        Utils.toLogger("error", "get_temperature_format - Status code: ${status} (Wrong answer for: ${deviceID}) Data:(${data})")
        return None // device didn't answer, wrong device
    } else {
        def tc2 = data[46..47]
        return hexToFloatInt(tc2)
    }
}

def set_temperature_format(temperatureFormat) { // 0 = celcius, 1 = fahrenheit
    return "01" + byteArrayToHexString(packInt(temperatureFormat)[0..0] as byte[])
}

def set_temperature_format(String dni, temperatureFormat) {
    // 0 = celcius, 1 = fahrenheit
    try {
        def deviceID = getDeviceIDfromDNI(dni)
        sendRequest(dni, data_write_request(data_write_command, deviceID, data_temperature_format, set_temperature_format(temperatureFormat)))
    } catch (e) {
        Utils.toLogger("error", "Cannot change temperature format: ${e}")
    }
}

def get_time_format(data) {
    def sequence = data[12..19]
    def deviceID = data[26..33]
    def status = data[20..21]
    if (status != "0A") {
        Utils.toLogger("error", "get_time_format - Status code: ${status} (Wrong answer for: ${deviceID}) Data:(${data})")
        return None // device didn't answer, wrong device
    } else {
        def tc2 = data[46..47]
        return hexToFloatInt(tc2)
    }
}

def set_time_format(timeFormat) { // 0 = 24h, 1 = 12h
    return "01" + byteArrayToHexString(packInt(timeFormat)[0..0] as byte[])
}

def set_time_format(String dni, timeFormat) {
    // 0 = 24h, 1 = 12h
    try {
        def deviceID = getDeviceIDfromDNI(dni)
        sendRequest(dni, data_write_request(data_write_command, deviceID, data_time_format, set_time_format(timeFormat)))
    } catch (e) {
        Utils.toLogger("error", "Cannot change time format: ${e}")
    }
}

def get_early_start(data) {
    def sequence = data[12..19]
    def deviceID = data[26..33]
    def status = data[20..21]
    if (status != "0A") {
        Utils.toLogger("error", "get_early_start - Status code: ${status} (Wrong answer for: ${deviceID}) Data:(${data})")
        return None // device didn't answer, wrong device
    } else {
        def tc2 = data[46..47]
        return hexToFloatInt(tc2)
    }
}

def set_early_start(timeFormat) { // 0 = disabled, 1 = enabled
    return "01" + byteArrayToHexString(packInt(timeFormat)[0..0] as byte[])
}

def set_early_start(dni, isEnable) {
    // 0 = disabled, 1 = enabled
    try {
        def deviceID = getDeviceIDfromDNI(dni)
        sendRequest(dni, data_write_request(data_write_command, deviceID, data_early_start, set_early_start(isEnable)))
    } catch (e) {
        Utils.toLogger("error", "Cannot change time format: ${e}")
    }
}

def set_min_setpoint(dni, temperature) {
    try {
        def deviceID = getDeviceIDfromDNI(dni)
        sendRequest(dni, data_write_request(data_write_command, deviceID, data_min_temp, set_temperature(temperature)))
    } catch (e) {
        Utils.toLogger("error", "Cannot set min temperature setpoint ${e}")
    }
}

def set_max_setpoint(dni, temperature) {
    try {
        def deviceID = getDeviceIDfromDNI(dni)
        sendRequest(dni, data_write_request(data_write_command, deviceID, data_max_temp, set_temperature(temperature)))
    } catch (e) {
        Utils.toLogger("error", "Cannot set max temperature setpoint ${e}")
    }
}

def set_away_setpoint(dni, temperature) {
    try {
        def deviceID = getDeviceIDfromDNI(dni)
        sendRequest(dni, data_write_request(data_write_command, deviceID, data_away_temp, set_temperature(temperature)))
    } catch (e) {
        Utils.toLogger("error", "Cannot set away temperature setpoint ${e}")
    }
}

def set_daily_report(self) {
    //Set report to send data to each devices once a day. Needed to get proper auto mode operation
    try {
        def tz = location.timeZone
        sendRequest(self, data_report_request(data_report_command, all_unit, data_time, set_time(tz)))
        sendRequest(self, data_report_request(data_report_command, all_unit, data_date, set_date(tz)))
        sendRequest(self, data_report_request(data_report_command, all_unit, data_sunrise, set_sun_time(tz, "sunrise")))
        sendRequest(self, data_report_request(data_report_command, all_unit, data_sunset, set_sun_time(tz, "sunset")))
    } catch (e) {
        Utils.toLogger("error", "Cannot send daily report to each devices: ${e}")
    }
}

def set_hourly_report(self, device_id, outside_temperature) {
    //we need to send temperature once per hour if we want it to be displayed on second thermostat display line
    //We also need to send command to switch from setpoint temperature to outside temperature on second thermostat display
    try {
        sendRequest(self, data_write_request(data_write_command, device_id, data_secondary_display, put_mode(1)))
        sendRequest(self, data_report_request(data_report_command, device_id, data_outdoor_temperature, set_temperature(outside_temperature)))
    } catch (e) {
        Utils.toLogger("error", "Cannot send outside temperature report to each devices: ${e}")
    }
}

def set_date(zone) {
    Utils.toLogger("debug", "set_date - zone is ${zone}")
    //now = datetime.now(zone)
    //def timestamp = 1486146877214 // milliseconds
    //def date = new Date( timestamp ).toString()    
    //Date currentDate = new Date()
    //def timestamp = currentDate.getTime();
    //Utils.toLogger("debug", "set_date - timestamp: ${timestamp}")    
    //Date now = new Date( ((long)1604285937.1535 - (long)3600)  * 1000 )
    //Utils.toLogger("debug", "set_date - timestamp: ${now}") 
    Date now = new Date()
    def day = now.getDay() - 1
    //Utils.toLogger("debug", "set_date - day: ${day}")
    if (day == -1) {
        day = 6
    }
    //Utils.toLogger("debug", "set_date - day: ${day}")
    def w = byteArrayToHexString(packInt(day)[0..0] as byte[]) //day of week, 0=monday converted to bytes
    //Utils.toLogger("debug", "set_date - w: ${w}")
    def d = byteArrayToHexString(packInt(now.format('dd', zone).toInteger())[0..0] as byte[]) //day of month converted to bytes
    //Utils.toLogger("debug", "set_date - d: ${d}")
    def m = byteArrayToHexString(packInt(now.format('MM', zone).toInteger())[0..0] as byte[]) //month converted to bytes
    //Utils.toLogger("debug", "set_date - m: ${m}")
    def y = byteArrayToHexString(packInt(now.format('yy', zone).toInteger())[0..0] as byte[]) //year converted to bytes
    //Utils.toLogger("debug", "set_date - y: ${y}")
    def date = '04'+w+d+m+y //xxwwddmmyy,  xx = length of data date = 04
    //Utils.toLogger("debug", "set_date - time: ${date}")
    return date
}

def set_time(zone) {
    Utils.toLogger("debug", "set_time - zone is ${zone}")
    //now = datetime.now(zone)
    Date now = new Date()
    //Date now = new Date( ((long)1604285937.1535 - (long)3600)  * 1000 )
    //Utils.toLogger("debug", "set_time - timestamp: ${now}")
    def s = byteArrayToHexString(packInt(now.format('ss', zone).toInteger())[0..0] as byte[]) //second converted to bytes
    //Utils.toLogger("debug", "set_time - s: ${s}")
    def m = byteArrayToHexString(packInt(now.format('mm', zone).toInteger())[0..0] as byte[]) //minutes converted to bytes
    //Utils.toLogger("debug", "set_time - m: ${m}")
    def h = byteArrayToHexString(packInt(now.format('HH', zone).toInteger()+get_dst(zone))[0..0] as byte[]) //hours converted to bytes
    //Utils.toLogger("debug", "set_time - h: ${h}")
    def time = '03'+s+m+h //xxssmmhh  24hr, 16:09:00 pm, xx = length of data time = 03
    //Utils.toLogger("debug", "set_time - time: ${time}")
    return time
}

def set_sun_time(zone, period) { // period = sunrise or sunset
    //Utils.toLogger("debug", "set_sun_time - period is ${period}")
    def Date now = period == "sunrise" ? location.sunrise : location.sunset
    //now = new Date( ((long)1604285937.1535 - (long)3600)  * 1000 )    
    //Utils.toLogger("debug", "set_sun_time - timestamp: ${now}")
    def s = byteArrayToHexString(packInt(now.format('ss', zone).toInteger())[0..0] as byte[]) //second converted to bytes
    //Utils.toLogger("debug", "set_sun_time - s: ${s}")
    def m = byteArrayToHexString(packInt(now.format('mm', zone).toInteger())[0..0] as byte[]) //minutes converted to bytes
    //Utils.toLogger("debug", "set_sun_time - m: ${m}")
    def h = byteArrayToHexString(packInt(now.format('HH', zone).toInteger()+get_dst(location.timeZone))[0..0] as byte[])  //hours converted to bytes
    //Utils.toLogger("debug", "set_sun_time - h: ${h}")
    def time = '03'+s+m+h //xxssmmhh  24hr, 16:09:00 pm, xx = length of data time = 03
    //Utils.toLogger("debug", "set_sun_time - time: ${time}")
    return time
}

def get_heat_level(data) {
    def sequence = data[12..19]
    def deviceID = data[26..33]
    def status = data[20..21]
    if (status != "0A") {
        Utils.toLogger("debug", "Status code for device ${deviceID}: (wrong answer ? ${status}), data:(${data})")
        return None // device didn't answer, wrong device
    } else {
        def tc2 = data[46..47]
        return hexToFloatInt(tc2)
        //return int(float.fromhex(tc2))
    }
}

def set_temperature(temp_celcius) {
    def temp = temp_celcius*100 as Integer
    //Utils.toLogger("debug", "set_temperature - temp is ${temp}")
    //Utils.toLogger("debug", "set_temperature - packint is ${packInt(temp)[0..1]}")
    //Utils.toLogger("debug", "set_temperature - temp is 02${byteArrayToHexString(packInt(temp)[0..1] as byte[])}")
    return "02" + byteArrayToHexString(packInt(temp)[0..1] as byte[])
}

def send_time(dni) {
    //Send current time to device. it is required to set device mode to auto
    try {
        def deviceID = getDeviceIDfromDNI(dni)
        def tz = location.timeZone
        sendRequest(dni, data_write_request(data_write_command, deviceID, data_time, set_time(tz)))
    } catch(e) {
        Utils.toLogger("error", "Cannot send current time to device")
    }
}
 
def set_mode(dni, device_type, mode) {
    // Set device operation mode
    // prepare data
    try{
        def deviceID = getDeviceIDfromDNI(dni)
        if (device_type.toInteger() < 100) {
            sendRequest(dni, data_write_request(data_write_command, deviceID, data_mode, put_mode(mode)))
        } else {
            sendRequest(dni, data_write_request(data_write_command, deviceID, data_light_mode, put_mode(mode)))
        }
    } catch(e) {
        Utils.toLogger("error", "Cannot set device operation mode")
    }
}

def get_result(data) { // check if data write was successfull, return True or False
    def sequence = data[12..19]
    def deviceID = data[26..33]
    def tc2 = data[20..21]
    if (tc2.toString() == "0A") { //data read or write
        return True
    } else if (tc2.toString() =="01") { //data report
        return True
    } else {
        Utils.toLogger("error", "Status code: ${tc2} (Wrong answer ? ${deviceID}) ${data}")
    }
    return False
}

// This function receives the response from the Sinope Hub bridge and updates things, or passes the response to an individual thermostat
def parse(response) {
    Utils.toLogger("debug", "parse Response is ${response} and size is ${hexStringToByteArray(response).size()}")
    
    def respLength = response.length()
    if(respLength == null || respLength == 0) {
        return
    }
    
    if (respLength == 1024) {
        // Length is max so concatenate together
        def concatStr = getDataValue("fullMessage")
        if (concatStr == null) {
		    concatStr = response
        } else {
            concatStr = concatStr + response
        }
        device.updateDataValue("fullMessage", concatStr)
    } else {
		// Length less than max so concatenate with previous if required, and then process
		def concatStr = getDataValue("fullMessage")
        if (concatStr == null) {
		    concatStr = response
        } else {
            concatStr = concatStr + response
            device.updateDataValue("fullMessage", "")
        }
		processResponse(concatStr)
	}
}

def processResponse(response)
{
    resetCloseSocketTimer()
    unschedule(runAllActions5Sec)
    schedule("0/1 * * * * ?", runAllActions1Sec)
    def responseType = device.getDataValue("responseType") as Integer
    Utils.toLogger("debug", "Processing response to ${responseType}")

    switch(responseType) {
        case RESPONSE_TYPES.GET_API_KEY:
            Utils.toLogger("debug", "received getAPIKey command state.APIKey before: ${state.APIKey}")
            state.APIKey = retreive_key(response)[0..15]
            Utils.toLogger("debug", "state.APIKey after: ${state.APIKey}")
            if (sinopehubapikey == "0000000000000000") {
                Utils.toLogger("info", "API Key request failed. Check your Neviweb Hub ID")
                state.APIKey = null
            } else {
                Utils.toLogger("info", "API Key saved: ${state.APIKey}")
            }
        break
        case RESPONSE_TYPES.ADD_THERMOSTAT:
            Utils.toLogger("debug", "received addThermostat command response: ${response}")
            def deviceID = response[14..21]
            Utils.toLogger("debug", "deviceID: ${deviceID}")

            //Prepare Thermostat name - prepend and postpend of settings if they exist
            def thisthermostatname = "Sinope Thermostat" //+ deviceID

            //Prepare DNI - Remove spaces and replace with a hyphen to prevent problems with HTML requests
            def thisthermostatdni = "${deviceID}" //item.key.replaceAll(" ", "+")

            Utils.toLogger("debug", "Adding child Name: ${thisthermostatname}, DNI: ${thisthermostatdni}, Stat ID: ${deviceID} to Hub: ${device.hub.id}")
            try {
                addChildDevice("rferrazguimaraes", "Sinope Thermostat", thisthermostatdni, [name: thisthermostatname, isComponent: false])
            } catch (e) {
                Utils.toLogger("error", "Couldnt add device, probably already exists: ${e}")
            }
        break
        case RESPONSE_TYPES.SEND_REQUEST:
            sendRequestResponse(response)
        break
        case RESPONSE_TYPES.DATA_WRITE_1:
            analyseDataResponse(response)
        break
        case RESPONSE_TYPES.DATA_WRITE_2:
            updateChild(UPDATE_TYPES.WRITE, response)
        break
        case RESPONSE_TYPES.DATA_READ_1:
            analyseDataResponse(response)
        break
        case RESPONSE_TYPES.DATA_READ_2:
            updateChild(UPDATE_TYPES.READ, response)
        break
        case RESPONSE_TYPES.DATA_REPORT_1:
            analyseDataResponse(response)
        break
        case RESPONSE_TYPES.DATA_REPORT_2:
            updateChild(UPDATE_TYPES.REPORT, response)
        break
        default:
            Utils.toLogger("error", "Unknown source of response")
        break
    }
}

def get_data_push(data) { //will be used to send data pushed by GT125 when light is turned on or off directly to HA device
    def deviceID = data[26..33]
    def status = data[20..21]
    def tc2 = data[46..47]
//    return int(float.fromhex(tc2))
    return None
}

def sendRequestResponse(response) {
    Utils.toLogger("debug", "received sendRequestResponse command response: ${response}")
    def status = response[0..13]
    Utils.toLogger("debug", "status is ${status}")
    if (status == "55000C001101FF") {
        Utils.toLogger("error", "Login fail, please check your APIKey")
    } else { // 55000C00110100 - Login ok
        Utils.toLogger("debug", "Login ok !")
    }
}

def analyseDataResponse(response) {
    Utils.toLogger("debug", "received analyseDataResponse command response: ${response}")
    if (crc_check(hexStringToByteArray(response))) {  // receive acknoledge, check status and if we will receive more data
        Utils.toLogger("debug", "analyseDataResponse response accepted")
        Utils.toLogger("debug", "Reply et longueur du data = ${hexStringToByteArray(response).size()} - ${response}")
        def deviceID = response[26..33]
        Utils.toLogger("debug", "deviceID: ${deviceID}")
        def responseType = device.getDataValue("responseType") as Integer
        if (hexStringToByteArray(response).size() == 19) {
            Utils.toLogger("debug", "hexStringToByteArray(response).size() == 19")
            def seq_num = response[12..19] //sequence id to link response to the correct request
            Utils.toLogger("debug", "seq_num: ${seq_num}")
            def status = response[20..21]
            Utils.toLogger("debug", "status: ${status}")
            def more = response[24..25] //check if we will receive other data
            Utils.toLogger("debug", "more: ${more}")
            if (status == "00") { // request status = ok for read and write, we go on (read=00, report=01, write=00)
                Utils.toLogger("debug", "request status = ok for read and write")
                if (more == "01") { //GT125 is sending another data response
                    Utils.toLogger("debug", "analyseDataResponse - GT125 is sending another data response")
                    if(responseType == RESPONSE_TYPES.DATA_READ_1) {
                        device.updateDataValue("responseType", "${RESPONSE_TYPES.DATA_READ_2}")
                    } else if(responseType == RESPONSE_TYPES.DATA_REPORT_1) {
                        device.updateDataValue("responseType", "${RESPONSE_TYPES.DATA_REPORT_2}")
                    } else {
                        device.updateDataValue("responseType", "${RESPONSE_TYPES.DATA_WRITE_2}")
                    }
                } else {
                    Utils.toLogger("debug", "No more response...")
                    //closeSocket()
                    //return False
                }
            } else if (status == "01") { //status ok for data report
                Utils.toLogger("debug", "status ok for data report")
                if(responseType == RESPONSE_TYPES.DATA_READ_1) {
                    updateChild(UPDATE_TYPES.READ, response)
                } else if(responseType == RESPONSE_TYPES.DATA_REPORT_1) {
                    updateChild(UPDATE_TYPES.REPORT, response)
                } else {
                    updateChild(UPDATE_TYPES.WRITE, response)
                }
                //closeSocket()
                //return response
            } else {
                Utils.toLogger("error", "analyseDataResponse - Error... status: ${status} deviceID: ${deviceID}")
                error_info(status, deviceID)
                //closeSocket()
                //return False
            }
        } else if (hexStringToByteArray(response).size() > 19) { // case data received with the acknowledge
            Utils.toLogger("debug", "hexStringToByteArray(response).size() > 19")
            def sizeResponse = response.size()
            //def datarec = response[19..sizeResponse-1]
            def datarec = response[38..sizeResponse-1]
            Utils.toLogger("debug", "datarec: ${datarec}")
            //def state = binascii.hexlify(datarec)[20..21]
            if(datarec.size() >= 21) {
                def state = datarec[20..21]
                if (state == "00") { // request has been queued, will receive another answer later
                    Utils.toLogger("debug", "analyseDataResponse - Request queued for device ${deviceID}, waiting...")
                } else if (state == "0A") { //we got an answer
                    Utils.toLogger("debug", "we got an answer: ${datarec}")
                    if(responseType == RESPONSE_TYPES.DATA_READ_1) {
                        updateChild(UPDATE_TYPES.READ, datarec)
                    } else if(responseType == RESPONSE_TYPES.DATA_REPORT_1) {
                        updateChild(UPDATE_TYPES.REPORT, datarec)
                    } else {
                        updateChild(UPDATE_TYPES.WRITE, response)
                    }
                    //return datarec
                } else if (state == "0B") { // we receive a push notification
                    Utils.toLogger("debug", "we receive a push notification ${datarec}")
                    get_data_push(datarec)
                } else {
                    Utils.toLogger("error", "analyseDataResponse - Bad answer received, data: ${datarec}")
                    error_info(state, deviceID)
                    //closeSocket()
                    //return False	
                }
            } else {
                Utils.toLogger("error", "Bad response, Check response: ${response} - datarec: ${datarec}")
                //closeSocket()
            }
        } else {
            Utils.toLogger("error", "Bad response, Check data ${response}")
            //closeSocket()
        }            
    } else {
        Utils.toLogger("error", "Bad response, crc error...")
        //closeSocket()
    } 
}    
    
def updateChild(updateType, response) {       
    
    Utils.toLogger("debug", "received updateChild command ${updateType} response: ${response}") 
    def canUpdateChild = updateType == UPDATE_TYPES.REPORT
    def deviceID = response[26..33]
    if(!canUpdateChild)
    {
        def status = response[20..21]
        Utils.toLogger("debug", "updateChild - GT125 is sending another data response")
        def state = status
        //while (state != "0a") {
        //datarec = sock.recv(1024)
        //state = binascii.hexlify(datarec)[20..21]
        state = response[20..21]
        Utils.toLogger("debug", "more: ${state}")
        if (state == "00") { // request has been queued, will receive another answer later
            Utils.toLogger("debug", "!!!!!!!!!Request queued for device ${deviceID}, waiting...")
        } else if (state == "0A") { //we got an answer
            Utils.toLogger("debug", "we got an answer: ${response}")
            canUpdateChild = true
            //break
        } else if (state == "0B") { // we receive a push notification
            Utils.toLogger("debug", "we receive a push notification")
            get_data_push(response)
        } else {
            Utils.toLogger("error", "updateChild - Bad answer received, data: ${response}")//, binascii.hexlify(datarec))
            error_info(state, deviceID)
            //return False
            //break
        }
    }
    
    if(canUpdateChild) {
        if(deviceID == all_unit)
        {
            try {
                for (resultDevice in getChildDevices())
                {
                    sendInfoChild(updateType, response, resultDevice.deviceNetworkId, resultDevice)
                }
            } catch (e) {
                Utils.toLogger("error", "Couldnt process response, probably this child doesnt exist: ${e} in all_unit")
            }
        } else {
            try {
                def resultDevice = getChildDevices().find {
                    it.deviceNetworkId == deviceID
                }
                
                sendInfoChild(updateType, response, deviceID, resultDevice)
            } catch (e) {
                Utils.toLogger("error", "Couldnt process response, probably this child doesnt exist: ${e}")
            }
        }
    }
}

def keyForValue(map, value) {
    map.find { it.value == value }?.key
}

def sendInfoChild(updateType, response, deviceID, resultDevice)
{
    Utils.toLogger("debug", "sendInfoChild - Sending response ${response} to ${deviceID} resultDevice: ${resultDevice} updateType: ${updateType}")

    if(deviceID != resultDevice.deviceNetworkId) {                    
        Utils.toLogger("error", "Device ID is different - deviceID: ${deviceID} - resultDevice.deviceNetworkId: ${resultDevice.deviceNetworkId}")
    }    
    
    if(resultDevice != null) {
        //Utils.toLogger("debug", "!!!!!Sending response to ${deviceID} resultdevice: ${resultDevice}")
        if(updateType != UPDATE_TYPES.REPORT)
        {
            def commandType = response[36..43]
            switch(commandType) {
                case data_temperature:
                    def temperature = get_temperature(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.TEMPERATURE, value: temperature])
                break
                case data_setpoint:
                    def setPoint = get_temperature(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.SET_POINT, value: setPoint])
                break
                case data_heat_level:
                    def heatLevel = get_heat_level(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.HEAT_LEVEL, value: heatLevel])
                break
                case data_mode:
                    def mode = get_mode(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.MODE, value: mode])
                break
                case data_away:
                    def away = get_away(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.AWAY, value: away])
                break
                case data_max_temp:
                    def tempMax = get_temperature(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.MAX_TEMP, value: tempMax])
                break
                case data_min_temp:
                    def tempMin = get_temperature(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.MIN_TEMP, value: tempMin])
                break        
                case data_load:
                    def wattLoad = get_power_load(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.LOAD, value: wattLoad])
                break
                case data_power_connected:
                    def wattOveride = get_power_load(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.POWER_CONNECTED, value: wattOveride])
                break
                case data_secondary_display:
                    def secondaryDisplay = get_secondary_display(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.SECONDARY_DISPLAY, value: secondaryDisplay])
                break
                case data_time:
                    def outdoorTemp = get_temperature(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.TIME, value: outdoorTemp])
                break
                case data_date:
                    def outdoorTemp = get_temperature(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.DATE, value: outdoorTemp])
                break
                case data_sunrise:
                    def outdoorTemp = get_temperature(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.SUNRISE, value: outdoorTemp])
                break
                case data_sunset:
                    def outdoorTemp = get_temperature(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.SUNSET, value: outdoorTemp])
                break
                case data_lock:
                    def lock = get_lock(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.LOCK, value: lock])
                break
                case data_temperature_format:
                    def displayFormat = get_temperature_format(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.TEMPERATURE_FORMAT, value: displayFormat])
                break
                case data_time_format:
                    def timeFormat = get_time_format(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.TIME_FORMAT, value: timeFormat])
                break
                case data_early_start:
                    def earlyStart = get_early_start(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.EARLY_START, value: earlyStart])
                break
                case data_away_temp:
                    def awayTemp = get_temperature(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.AWAY_TEMP, value: awayTemp])
                break
                case data_backlight:
                    def backlightState = get_light_state(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.BACKLIGHT_STATE, value: backlightState])
                break
                case data_backlight_idle:
                    def backlightIdle = get_light_idle(response)
                    resultDevice.processChildResponse([device: deviceID, type: MESSAGE_TYPES.BACKLIGHT_IDLE, value: backlightIdle])
                break
                default:
                    Utils.toLogger("error", "updateChild - Command ${commandType} not found!")
                break
            }
        }
    } else {
        Utils.toLogger("error", "Couldnt process response, probably this child doesnt exist - deviceID: ${deviceID}")
    }
}

def error_info(bug, device) {
    if (bug == 'FF' || bug == 'ff') {
        Utils.toLogger("error", "in request for ${device} : Request failed (${bug}).")
    } else if (bug == '02') {
        Utils.toLogger("error", "in request for ${device} : Request aborted (${bug}).")
    } else if (bug == 'FE' || bug == 'fe') {
        Utils.toLogger("error", "in request for ${device} : Buffer full, retry later (${bug}).")
    } else if (bug == 'FC' || bug == 'fc') {
        Utils.toLogger("error", "in request for ${device} : Device not responding (${bug}), no more answer, request aborted.")
    } else if (bug == 'FB' || bug == 'fb') {
        Utils.toLogger("error", "in request for ${device} : Abort failed, request not found in queue (${bug}).")
    } else if (bug == 'FA' || bug == 'fa') {
        Utils.toLogger("error", "in request for ${device} : Unknown device or destination deviceID is invalid or not a member of this network (${bug}).")
    } else if (bug == 'FD' || bug == 'fd') {
        Utils.toLogger("error", "in request for ${device} : Error message reserved (${bug}), info not available.")
    } else {
        Utils.toLogger("error", "in request for ${device} : Unknown error (${bug}).")
    }
}

//These functions are helper functions to talk to the Sinope Bridge
def sendCommand(paramsMap) {
    Utils.toLogger("debug", "sendCommand - paramsMap: ${paramsMap}")
    commandQueue.offer(paramsMap)
}

def resetPoolCommand() {
    closeSocket()
    commandQueue.clear()
    queueSize = 0
    socketErrors = 0
	lastLockQuery = 0
    socketState = "closed"
}

def runAllActions1Sec()
{
    runAllActions()
}

def runAllActions5Sec()
{
    runAllActions()
}

def runAllActions()
{
    def Integer waitTime = 30000
    if (mutex.tryAcquire(waitTime, TimeUnit.MILLISECONDS)) {
        if (commandQueue.size() > 0) {
            Utils.toLogger("debug", "poolCommand canSendCommand")

            if(openSocket()) { 
                try {
                    Utils.toLogger("debug", "poolCommand sendSocketMessage commandQueue size: ${commandQueue.size()}")
                    def paramsMap = commandQueue.poll()

                    if(paramsMap != null) {
                        Utils.toLogger("debug", "runAllActions setting responseType to: ${paramsMap.type}")
                        device.updateDataValue("responseType", "${paramsMap.type}")

                        switch(paramsMap.type) {
                            case RESPONSE_TYPES.GET_API_KEY:
                                Utils.toLogger("info", "Press 'Web' button on hub")
                            break
                            case RESPONSE_TYPES.ADD_THERMOSTAT:
                                Utils.toLogger("info", "Press the two buttons on the thermostat")
                            break
                            case RESPONSE_TYPES.LOGIN_REQUEST:
                                Utils.toLogger("info", "login request ${paramsMap.data}")
                            break
                            case RESPONSE_TYPES.CLOSE_CONNECTION:
                                closeSocket()
                            break
                        }

                        if(paramsMap.data != "") {                        
                            InterfaceUtils.sendSocketMessage(device, paramsMap.data)
                            resetCloseSocketTimer()
                            unschedule(runAllActions1Sec)
                            schedule("0/5 * * * * ?", runAllActions5Sec)
                        }

                        pauseExecution(750)
                    }
                } catch(e) {
                    Utils.toLogger("error", "poolCommand: resetting pool command exception = [${e}]")
                }                    
            }
        }

        if(commandQueue.size() < queueSize && queueSize != (commandQueue.size() + 1)) {
            Utils.toLogger("error", "poolCommand queueSize is different - queueSize: ${queueSize} - commandSize: ${commandQueue.size()}")
        }        
        
        if(commandQueue.size() > 0) {
            Utils.toLogger("debug", "Setting queueSize: ${commandQueue.size()}")
        }
        
        queueSize = commandQueue.size()

        def duration = (pollIntervals.toInteger() ?: 60) * 1000
        if (now() - lastLockQuery >= duration) {
            resetCloseSocketTimer()
            if (socketState != "closed" && commandQueue.size() == 0) {

                Utils.toLogger("debug", "Trying to close socket - socketState: ${socketState}")
                closeSocket()
            }	
        }
    } else {
        Utils.toLogger("error", "Lock Acquire failed ... Aborting!")
    }
    
    mutex.release()  
}

def resetCloseSocketTimer()
{
    lastLockQuery = now()
}

def openSocket() {
	if (socketState == "open") {
		Utils.toLogger("debug", "openSocket: Socket already opened.")
		return true
	}
	Utils.toLogger("debug", "openSocket: Connecting to ${sinopehubip}:${sinopehubport}")
	try {
        Utils.toLogger("debug", "opening socket")
		InterfaceUtils.socketConnect(device, sinopehubip, sinopehubport.toInteger(), byteInterface: true)
		//pauseExecution(1000)
        pauseExecution(100)
		socketState = "open"
		Utils.toLogger("debug", "openSocket: Socket opened.")
		socketErrors = 0
		return true
	} catch(e) {
		Utils.toLogger("error", "openSocket: exception = ${e}")
        closeSocket()
		return false
	}
}

def closeSocket() {
	Utils.toLogger("debug", "closeSocket: Socket close requested.")
	socketState = "closing"
    InterfaceUtils.socketClose(device)
	socketState = "closed"
	pauseExecution(100)
	Utils.toLogger("debug", "closeSocket: Socket closed.")
	return true
}

def socketStatus(String message) {
	Utils.toLogger("warn", "socketStatus - Socket [${socketState}]  Message [${message}]")
	
	if (socketState == "closed") 
    { 
        return 
    }
    
	switch(message) {
		case "send error: Broken pipe (Write failed)":
			closeSocket()
			Utils.toLogger("debug", "socketStatus - Write Failed - Attempting reconnect")
			return //openSocket()
			break;
		case "receive error: Stream closed.":
		case "receive error: Stream is closed":
			socketErrors = socketErrors + 1
			if ((socketState != "closing") && (SocketErrors < 10)) {
				//closeSocket()
				//Utils.toLogger("debug", "socketStatus - Stream Closed - Attempting reconnect [${SocketErrors}]")
				return //openSocket()
			}
			socketState = "closed"
			if (socketErrors > 9) {
				Utils.toLogger("debug", "socketStatus - Stream Closed - Too many reconnects - execute initialize() to restart")
			}
			return
			break;
		case "send error: Socket closed":
			closeSocket()
			Utils.toLogger("debug", "socketStatus - Socket Closed - Attempting reconnect")
			return //openSocket()
			break;
	}
    
	Utils.toLogger("debug", "socketStatus - UNHANDLED socket status [${message}]")
}

private sendEventPublish(evt)	{
	def var = "${evt.name + 'Publish'}"
	def pub = this[var]
    if (pub) {
        sendEvent(name: evt.name, value: evt.value, descriptionText: evt.descriptionText, unit: evt.unit, displayed: evt.displayed);
    }
}

def invert(id) {
    Utils.toLogger("debug", "invert id:${id}")
    """The Api_ID must be sent in reversed order"""
    def k1 = id[14..15]
    def k2 = id[12..13]
    def k3 = id[10..11]
    def k4 = id[8..9]
    def k5 = id[6..7]
    def k6 = id[4..5]
    def k7 = id[2..3]
    def k8 = id[0..1]
    return k1+k2+k3+k4+k5+k6+k7+k8
}

def makeRequest(type, data)
{
    Utils.toLogger("debug", "makeRequest - type:${type} - data: ${data}")
    def paramsMap = [type: type, data: data]
    return paramsMap
}

def getAPIKey() {
    if (sinopehubid != /^[0-9A-Fa-f]{16}$/) {
        Utils.toLogger("error", "Neviweb Hub ID is invalid. 16 HEX characters, no spaces.")
    }
    
    sendCommand(makeRequest(RESPONSE_TYPES.GET_API_KEY, byteArrayToHexString(key_request(invert(sinopehubid)))))
}

def sendRequest(dni, paramsMap) { //data
    synchronized(mutexSendCommand) {
        Utils.toLogger("debug", "sendRequest - login_request:${byteArrayToHexString(login_request())}")
        loginRequestCommand = makeRequest(RESPONSE_TYPES.SEND_REQUEST, byteArrayToHexString(login_request()))
        if(!commandQueue.contains(loginRequestCommand)) {
            sendCommand(loginRequestCommand)
            Utils.toLogger("debug", "Added login request command to queue")
        } else {
            Utils.toLogger("debug", "Login request command not added, it was already added before")
        }
        
        sendCommand(paramsMap)
        
        closeConnectionCommand = makeRequest(RESPONSE_TYPES.CLOSE_CONNECTION, "")
        if(commandQueue.contains(closeConnectionCommand)) {
            commandQueue.remove(closeConnectionCommand)
            Utils.toLogger("debug", "Close connection removed, another one will be added at the end of the queue")
        }
        
        sendCommand(closeConnectionCommand)
    }
}

def login_request() {
    //Utils.toLogger("debug", "login_request")
    if(state.APIKey == null) {
        Utils.toLogger("error", "APIkey is invalid! You need to acquire the APIkey by pressing 'Get APIkey' button")
    }

    //Utils.toLogger("debug", "state.APIKey ${state.APIKey}")
    def login_data = "550012001001"+invert(sinopehubid)+state.APIKey
    def login_crc = hexStringToByteArray(crc_count(hexStringToByteArray(login_data)))
    return (hexStringToByteArray(login_data).toList()+login_crc.toList()) as byte[]
}

def ping_request() {
    def ping_data = "550002001200"
    def ping_crc = hexStringToByteArray(crc_count(hexStringToByteArray(ping_data)))
    return (hexStringToByteArray(ping_data).toList()+ping_crc.toList()) as byte[]
}

def crc_count(bufer) {
    //Utils.toLogger("debug", "crc_count ${byteArrayToHexString(bufer)}")
    def hexdigest = crc8Digest(bufer)
    //Utils.toLogger("debug", "crc_count hexdigest: ${hexdigest}")
    return hexdigest;
}

def crc_check(bufer) {
    //Utils.toLogger("debug", "crc_check ${byteArrayToHexString(bufer)}")
    def hexdigest = crc8Digest(bufer)
    //Utils.toLogger("debug", "crc_check hexdigest: ${hexdigest}")
    if(hexdigest == "00") {
        //Utils.toLogger("debug", "crc8Digest(bufer) == 00")
        return "00"
    }
    
    return None
}

def key_request(serial) {
    def key_data = "55000A000A01"+serial
    def key_crc = hexStringToByteArray(crc_count(hexStringToByteArray(key_data)))
    return (hexStringToByteArray(key_data).toList() + key_crc.toList()) as byte[]
}

def retreive_key(data) {
    def size = data.size()
    def binary = data[18..size-1]
    def key = binary[0..15]
    return key
}

def hexStringToByteArray(String s) {
    return hubitat.helper.HexUtils.hexStringToByteArray(s)
}

def byteArrayToHexString(byte[] value) {
    return hubitat.helper.HexUtils.byteArrayToHexString(value)
}

def hexStringToIntArray(String s) {
    return hubitat.helper.HexUtils.hexStringToIntArray(s)
}

def intArrayToHexString(byte[] value) {
    return hubitat.helper.HexUtils.intArrayToHexString(value)
}

def hexToInt(hex) {
    Integer.parseInt(hex, 16)
}

def intToHex(value) {
    Integer.toHexString(value) 
}

def hexToFloatInt(hex) {
    //Utils.toLogger("debug", "hexToFloatInt ${hex}")
    Long i = Long.parseLong(hex, 16)
    //Utils.toLogger("debug", "hexToFloatInt i:${i}")
    return i.toInteger()
}

def crc8Update(bytes_) {
    //debug("crc8Update - start")
    def sum = 0x00
    for (byte_ in bytes_) {
        Integer position = (sum^byte_) as Integer
        sum = crc8Table[position]
    }
    
    //debug("crc8Update - exit")
    return sum
}

def crc8Digest(data) {
    //debug("crc8Digest - data is ${byteArrayToHexString(data)}")
    def sum = crc8Update(data)
    //debug("crc8Digest - sum is ${sum}")
    def hex = intToHex(sum)
    //debug("crc8Digest - hex is ${hex}")
    def size = hex.size()
    //debug("crc8Digest - size is ${size}")
    //return hex[2..size-1].padLeft(2, "0")
    return hex.padLeft(2, "0")
}

def packInt(int data) {
    def array = [(byte)((data >> 0) & 0xff),
                 (byte)((data >> 8) & 0xff),
                 (byte)((data >> 16) & 0xff),
                 (byte)((data >> 24) & 0xff)]
    return array
}

def unpackInt(byte[] data) {    
    if (data == null || data.length != 4) return 0x0;
    // ----------
    return (int)( // NOTE: type cast not necessary for int
            (0xff & data[0]) << 0  |
            (0xff & data[1]) << 8  |
            (0xff & data[2]) << 16 |
            (0xff & data[3]) << 24
            );
}

def get_seq(seq) { // could be improuved
    def sequence = ""
    Random random = new Random()
    //for _ in range(4) {
    1.upto(4) {
        //value = randint(10, 99)
        value = random.nextInt(89) + 10
        sequence += value.toString()
    }
    sequence = "64951454" // to remove rfg
    Utils.toLogger("debug", "sequencial number = ${sequence}")
    return sequence
}

def count_data(data) {
    Utils.toLogger("debug", "count_data - data is ${data}")
    def size = data.length()/2 as Integer
    return byteArrayToHexString(packInt(size)[0..0] as byte[])
}

def count_data_frame(data) {
    def size = data.length()/2 as Integer
    return byteArrayToHexString(packInt(size)[0..1] as byte[])
}

def data_read_request(String... arg) { // command,unit_id,data_app
    Utils.toLogger("debug", "data_read_request - arg is ${arg}")
    if(arg.size() != 3) {
        Utils.toLogger("error", "data_read_request - arg size is invalid! size: ${arg.size()}")
        return
    } else if(arg[0] == null) {
        Utils.toLogger("error", "data_read_request - command invalid!")
        return
    } else if (arg[1] == null) {
        Utils.toLogger("error", "data_read_request - deviceID invalid!")
        return
    } else if (arg[2] == null) {
        Utils.toLogger("error", "data_read_request - data_app invalid!")
        return
    }
    
    def head = "5500"
//    data_command = arg[0]
    def data_seq = get_seq(seq)
    def data_type = "00"
    def data_res = "000000000000"
    def app_data_size = "04"
    def size = count_data_frame(arg[0]+data_seq+data_type+data_res+arg[1]+app_data_size+arg[2])
    //Utils.toLogger("debug", "data_read_request size is ${size}")
    def data_frame = head+size+arg[0]+data_seq+data_type+data_res+arg[1]+app_data_size+arg[2]
    //Utils.toLogger("debug", "data_read_request data_frame is ${data_frame}")
    def read_crc = hexStringToByteArray(crc_count(hexStringToByteArray(data_frame)))
    //Utils.toLogger("debug", "data_read_request read_crc is ${byteArrayToHexString(read_crc)}")
    //Utils.toLogger("debug", "data_read_request is ${byteArrayToHexString((hexStringToByteArray(data_frame).toList() + read_crc.toList()) as byte[])}")
    def data = byteArrayToHexString((hexStringToByteArray(data_frame).toList() + read_crc.toList()) as byte[])
    def paramsMap = makeRequest(RESPONSE_TYPES.DATA_READ_1, data)
    return paramsMap
}

def data_report_request(String... arg) { // data = size+time or size+temperature (command,unit_id,data_app,data)
    Utils.toLogger("debug", "data_report_request - arg is ${arg}")
    if(arg.size() != 4) {
        Utils.toLogger("error", "data_report_request - arg size is invalid! size: ${arg.size()}")
        return
    } else if(arg[0] == null) {
        Utils.toLogger("error", "data_report_request - command invalid!")
        return
    } else if (arg[1] == null) {
        Utils.toLogger("error", "data_report_request - deviceID invalid!")
        return
    } else if (arg[2] == null) {
        Utils.toLogger("error", "data_report_request - data_app invalid!")
        return
    } else if (arg[3] == null) {
        Utils.toLogger("error", "data_report_request - data invalid!")
        return
    }

    def head = "5500"
//    data_command = arg[0]
    def data_seq = get_seq(seq)
    //Utils.toLogger("debug", "data_report_request data_seq is ${data_seq}")
    def data_type = "00"
    def data_res = "000000000000"
    def app_data_size = count_data(arg[2]+arg[3])
    //Utils.toLogger("debug", "data_report_request app_data_size is ${app_data_size}")
    def size = count_data_frame(arg[0]+data_seq+data_type+data_res+arg[1]+app_data_size+arg[2]+arg[3])
    //Utils.toLogger("debug", "data_report_request size is ${size}")
    def data_frame = head+size+arg[0]+data_seq+data_type+data_res+arg[1]+app_data_size+arg[2]+arg[3]
    //Utils.toLogger("debug", "data_report_request data_frame is ${data_frame}")
    def read_crc = hexStringToByteArray(crc_count(hexStringToByteArray(data_frame)))
    //Utils.toLogger("debug", "data_report_request is ${byteArrayToHexString((hexStringToByteArray(data_frame).toList() + read_crc.toList()) as byte[])}")
    def data = byteArrayToHexString((hexStringToByteArray(data_frame).toList() + read_crc.toList()) as byte[])
    def paramsMap = makeRequest(RESPONSE_TYPES.DATA_REPORT_1, data)
    return paramsMap
}

def data_write_request(String... arg) { // data = size+data to send (command,unit_id,data_app,data)
    Utils.toLogger("debug", "data_write_request - arg is ${arg}")
    if(arg.size() != 4) {
        Utils.toLogger("error", "data_write_request - arg size is invalid! size: ${arg.size()}")
        return
    } else if(arg[0] == null) {
        Utils.toLogger("error", "data_write_request - command invalid!")
        return
    } else if (arg[1] == null) {
        Utils.toLogger("error", "data_write_request - deviceID invalid!")
        return
    } else if (arg[2] == null) {
        Utils.toLogger("error", "data_write_request - data_app invalid!")
        return
    } else if (arg[3] == null) {
        Utils.toLogger("error", "data_write_request - data invalid!")
        return
    }
    
    def head = "5500"
//    data_command = arg[0]
    def data_seq = get_seq(seq)
    Utils.toLogger("debug", "data_write_request data_seq is ${data_seq}")
    def data_type = "00"
    def data_res = "000000000000"
    def app_data_size = count_data(arg[2]+arg[3])
    //Utils.toLogger("debug", "data_write_request app_data_size is ${app_data_size}")
    def size = count_data_frame(arg[0]+data_seq+data_type+data_res+arg[1]+app_data_size+arg[2]+arg[3])
    //Utils.toLogger("debug", "data_write_request size is ${size}")
    def data_frame = head+size+arg[0]+data_seq+data_type+data_res+arg[1]+app_data_size+arg[2]+arg[3]
    //Utils.toLogger("debug", "data_write_request data_frame is ${data_frame}")
    def read_crc = hexStringToByteArray(crc_count(hexStringToByteArray(data_frame)))
    //Utils.toLogger("debug", "data_write_request read_crc is ${byteArrayToHexString(read_crc)}")
    //Utils.toLogger("debug", "data_write_request is ${byteArrayToHexString((hexStringToByteArray(data_frame).toList() + read_crc.toList()) as byte[])}")
    def data = byteArrayToHexString((hexStringToByteArray(data_frame).toList() + read_crc.toList()) as byte[])
    def paramsMap = makeRequest(RESPONSE_TYPES.DATA_WRITE_1, data)
    return paramsMap
}

//This function helps control printing
def childGetLogLevel() {
    Integer setLevelIdx = LOG_LEVELS.indexOf(logLevel);
    if (setLevelIdx < 0) {
        setLevelIdx = LOG_LEVELS.indexOf(DEFAULT_LOG_LEVEL);
    }
    return setLevelIdx
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
            if (levelIdx <= setLevelIdx) {
                log."${level}" "${device.displayName} - ${msg}";
            }
        }
    }

    return instance;
}
