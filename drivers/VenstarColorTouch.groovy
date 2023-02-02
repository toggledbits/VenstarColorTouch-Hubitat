/**
 *  Venstar ColorTouch Thermostat Driver for Hubitat Elevation
 *
 *  (C) 2021-2023 Patrick H. Rigney (toggledbits), All Rights Reserved
 *
 *  Licensed under the MIT License.
 *  https://github.com/toggledbits/VenstarColorTouch-Hubitat/blob/main/LICENSE
 *
 *  Project repo: https://github.com/toggledbits/VenstarColorTouch-Hubitat
 *
 *  TO-DO: Query sensors and maybe create child devices; query runtimes.
 *
 *  Revision History
 *  Stamp By           Description
 *  ----- ------------ ---------------------------------------------------------
 *  23033 toggledbits  Fix supportedThermostatModes and supportedFanModes attrs
 *                     for dashboard menus. Thanks to jonathanb for the tip.
 *                     Fix initialization of modes and order of operations.
 *                     Detect change of driver version and re-initialize.
 *  23022 toggledbits  Proper URL-encoding of parameter values in both GET and
 *                     POST (should have done this from the very beginning, so
 *                     now's the second-best time to get this right). Merged
 *                     arktronic pull request (below) for T3900.
 *  22365 arktronic    Use POST for body, works better with T3900 (pull request
 *                     merged by toggledbits into 23022).
 *  22208 toggledbits  Save info response data earlier, for clarity in debug.
 *                     Improve messages around response errors, with special
 *                     handling for parsing errors. Fix runIn* method name
 *                     parameter (others have reported that use of non-string
 *                     leads to inconsistent results).
 *  22138 toggledbits  Add Initialize capability (completion).
 *  22104 toggledbits  It appears the older T59xx/69xx thermostats report humid-
 *                     ity oddly as compared to newer models. Try to work around
 *                     this.
 *  22101 toggledbits  Fix repeating message in log when activestage is not
 *                     reported by thermostat. Fix handling of 0 poll interval.
 *                     Refresh command still works when polling is disabled.
 *  21334 toggledbits  Trim input on mode commands; the dashboard seems to be
 *                     passing values with extra spaces.
 *  21270 toggledbits  Settable temp units; T2000/5x00/6x00 support.
 *  21268 toggledbits  Support for Digest username/password authentication
 *                     (requires HTTPS on thermostat).
 *  21265 toggledbits  Rebirth.
 *  ----------------------------------------------------------------------------
 */

metadata {
    definition (name: "Venstar ColorTouch Thermostat",
        namespace: "toggledbits.com",
        author: "Patrick Rigney",
        filename: "toggledbits-venstar-colortouch",
        importUri: "https://raw.githubusercontent.com/toggledbits/VenstarColorTouch-Hubitat/main/drivers/VenstarColorTouch.groovy") {

        capability "Refresh"
        capability "Initialize"
        capability "Thermostat"
        capability "Sensor"
        capability "Actuator"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "PresenceSensor"

        attribute "activeStages", "number"
        attribute "coolingSetpoint", "number"
        attribute "dehumidifcationSetpoint", "number"
        attribute "heatingSetpoint", "number"
        attribute "humidificationSetpoint", "number"
        attribute "humidity", "number"
        attribute "lastUpdate", "date"
        attribute "lastUpdateEpoch", "number"
        attribute "lastdata", "string"
        attribute "name", "string"
        attribute "online", "boolean"
        attribute "override", "enum", [ "off", "on", "unknown" ]
        attribute "presence", "enum", [ "present", "not present", "unknown" ]
        attribute "program", "enum", [ "running", "stopped", "unknown" ]
        attribute "schedulePeriod", "enum", [ "morning", "day", "evening", "night", "n/a", "unknown" ]
        attribute "supportedThermostatFanModes", "JSON_OBJECT"
        attribute "supportedThermostatModes", "JSON_OBJECT"
        attribute "temperature", "number"
        attribute "thermostatFanMode", "enum", [ "auto", "on" ]
        attribute "thermostatFanOperatingState", "enum", [ "off", "on" ]
        attribute "thermostatMode", "enum", [ "off", "auto", "heat", "cool", "emergency heat" ]
        attribute "thermostatOperatingState", "enum", [ "idle", "heating", "cooling", "delayed", "fan only" ]
        attribute "thermostatSetpoint", "number"

        command "auto"
        command "away"
        command "cool"
        command "emergencyHeat"
        command "fanAuto"
        command "fanCirculate"
        command "fanOn"
        command "heat"
        command "home"
        command "initialize"
        command "off"
        command "programRun"
        command "programStop"
        command "refresh"
        command "setCoolingSetpoint", [[ name: "temperature", type: "NUMBER", description:"New cooling setpoint" ]]
        command "setDehumidificationSetpoint", [[ name: "setpoint", type: "NUMBER", description: "New dehumidification setpoint" ]]
        command "setHeatingSetpoint", [[ name: "temperature", type: "NUMBER", description:"New heating setpoint" ]]
        command "setHumidificationSetpoint", [[name:"setpoint", type:"NUMBER", description:"New humidification setpoint"]]
        command "setPollingInterval", [[ name: "interval", type: "NUMBER", description:"New polling interval (seconds)" ]]
        command "setProgram", [[ name: "action", type: "ENUM", constraints: ["run","stop"] ]]
        command "setThermostatFanMode", [[ name: "fanmode", type: "ENUM", constraints: ["auto","on","circulate"] ]]
        command "setThermostatMode", [[ name: "thermostatmode", type: "ENUM", constraints: ["off","heat","cool","auto","emergency heat"] ]]
    }

    preferences {
        section {
            input (
                type: "string",
                name: "thermostatIp",
                title: "Thermostat IP Address",
                required: true
            )
            input (
                type: "enum",
                name: "thermostatModel",
                title: "Thermostat Model",
                options: [
                    "0": "T78x0/79x0/88x0/89x0/39x0",
                    "1": "T58x0/68x0",
                    "2": "T59x0/69x0"
                ],
                required: true,
                defaultValue: "0"
            )
            input (
                type: "enum",
                name: "requestProto",
                title: "Local API Protocol",
                options: [ "http", "https" ],
                required: true,
                defaultValue: "http"
            )
            input (
                type: "string",
                name: "authUser",
                title: "Auth Username",
                required: false
            )
            input (
                type: "string",
                name: "authPass",
                title: "Auth Password",
                required: false
            )
            input (
                type: "enum",
                name: "configUnits",
                title: "Temperature Unit",
                description: "By default, temperature units are the hub's configured unit. You can override to force C or F here.",
                options: [
                    "hub": "(default: hub's configured unit)",
                    "C": "Celsius",
                    "F": "Fahrenheit"
                ],
                required: true,
                defaultValue: "hub"
            )
            input (
                type: "number",
                name: "pollInterval",
                title: "Polling Interval",
                description: "Interval, in seconds, between queries to the thermostat for update (0=no poll/disable)",
                range: "0..86400",
                required: true,
                defaultValue: 60
            )
            input (
                type: "bool",
                name: "autoProgramStop",
                title: "Auto Program Stop",
                description: "Some commands (e.g. mode changes) will not be honored when the programmed schedule is running; turning this on (default) stops the program before executing these commands",
                defaultValue: true
            )
            input (
                type: "bool",
                name: "enableDebugLogging",
                title: "Enable debug logging",
                defaultValue: false
            )
        }
    }
}

private def D( msg ) {
    if ( enableDebugLogging ) {
        log.debug "${device.displayName}: ${msg}"
    }
}

private def W( msg ) {
    log.warn "${device.displayName}: ${msg}"
}

private def E( msg ) {
    log.error "${device.displayName}: ${msg}"
}

private def round( n, d=1 ) {
    return Math.round( n * 10**d + 0.5 ) / 10**d
}

def installed() {
    D( "installed()" )
    log.info("Venstar ColorTouch Driver by toggledbits version 23033 installed")
    state.pollInterval = 60
    initialize()
}

def initialize() {
    D( "initialize()" )

    state.remove( "lastdata" )

    updated()
}

def updated() {
    D( "updated()" )

    unschedule()

    state.pollInterval = pollInterval as Integer
    state.lastpoll = 0
    state.failCount = 0

    // Fan modes are static, so set them here (rarely); thermostat modes are set by refresh
    // now because that is returned by the info query.
    updateChanged( "supportedThermostatFanModes", groovy.json.JsonOutput.toJson([ "auto", "on" ]),
                  "Supported fan modes have changed" )

    sendCommand( "", null, { r,e -> handleHelloResponse(r,e) } )
}

def refresh() {
    D( "refresh()" )
    log.info( "Refreshing (querying thermostat)" )
    do_refresh()
}

def do_refresh() {
    D( "do_refresh()" )

    if ( "" != thermostatIp ) {
        if ( state.driver_version != 23033 || null == state.model ) {
            W("Driver version change detected; re-initializing")
            state.driver_version = 23033
            updated()  // comes back here eventually
            return
        }

        /* Schedule next query immediately */
        unschedule()
        if ( state.pollInterval > 0 ) {
            if ( state.pollInterval < 20 ) {
                state.pollInterval = 20
            }
            runIn( state.pollInterval, "do_refresh" )
            D("do_refresh() armed for next poll in ${state.pollInterval} secs")
        } else {
            log.info( "Requesting refresh (polling is disabled)" )
            updateChanged( "online", false, "OFF-LINE (not polling)" )
        }

        // Even if polling is off, a refresh will query and refresh the device.
        sendCommand( "query/info", null, { r, e -> queryInfoCallback(r, e) } )
    } else {
        updateChanged( "online", false, "OFF-LINE (not configured)" )
    }
}

private def coalesce( v, cl, df=null ) {
    if ( v != null ) {
        v = cl.call( v )
    }
    return ( v == null && df != null ) ? df : v
}

private def updateChanged( key, val, desctext=null, unit=null ) {
    def oldval = device.currentValue( key )
    // Some odd type handling goes on inside sendEvent() apparently. Booleans are converted to strings, so passing
    // in a boolean gets you a string back. But null is handled differently. If you set (real) null, or set the string
    // "null", it comes back from currentValue() as real null. Asymmetrical, gotcha.
    // D("Current value of ${key} is (${oldval==null?"null":oldval.class})${oldval} new is (${val==null?"null":val.class})${val}")
    if ( ! ( null == val || val instanceof Number || val instanceof String ) ) {
        // Investigate: this may have distrupted dashboard's expectation for enums (using JSON now).
        val = val.toString()
    }
    D("key ${key} oldval=${oldval} val=${val}")
    if ( oldval != val ) {
        sendEvent( name: key, value: val, unit: unit, linkText: device.displayName, descriptionText: desctext )
        if ( null != desctext && "" != desctext ) {
            log.info( "${device.displayName}: ${desctext}" )
        } else {
            log.info( "${device.displayName}: ${key} changed from ${oldval} to ${val}" )
        }
        return true
    }
    return false
}

private def handleHelloResponse( response, err ) {
    D("handleHelloResponse(${response}, ${err})")
    if ( err == null && response.getStatus() == 200 ) {
        def data = response.getData()
        log.info "${device.displayName} successful \"hello\" query to thermostat."
        D("thermostat's hello data is ${data}")
        state.api_ver = data.api_ver
        state.type = data.type
        state.model = data.model ?: "UNKNOWN"
        state.firmware = data.firmware

        do_refresh()
        return
    }

    updateChanged( "online", false, "OFF-LINE (invalid hello)" )

    if ( err ) {
        E("hello failed: ${err}")
    } else {
        E("hello failed: ${response.getStatus()} ${response.getStatusLine()}")
    }

    unschedule()
    if ( ( state.pollInterval ?: 0 ) > 0 ) {
        runIn( state.pollInterval, "do_refresh" )
    }
}

private def queryInfoCallback(response, err) {
    D("queryInfoCallback(): response=${response}, err=${err}")

    // D("queryInfoCallback(response) status ${response.getStatus()} headers ${response.getHeaders()}")

    state.lastpoll = now()

    if ( err != null ) {
        E("queryInfoCallback(): can't query thermostat info: ${err}")
        // state.lastdata = null
        sendEvent( name: "lastdata", value: err as String )
        if ( ++state.failCount >= 3 ) {
            updateChanged( "online", false, "OFF-LINE (thermostat unreachable)" )
        }
    } else if ( 200 == response.getStatus() ) {
        def scale = configUnits == "hub" ? getTemperatureScale() : configUnits
        def data = response.getData()      /* or maybe parseJson( response.data ) ? */

        // D("state is ${state}")
        D("queryInfoCallback(): data is ${data}")
        // state.lastdata = null
        sendEvent( name: "lastdata", value: data as String )
        sendEvent( name: "lastUpdate", value: new Date() )
        sendEvent( name: "lastUpdateEpoch", value: now() )

        if ( data == null ) {
            /* Error or unparsable response from thermostat */
            E("queryInfoCallback(): error or unparsable response from thermostat")
            if ( ++state.failCount >= 3 ) {
                updateChanged( "online", false, "OFF-LINE (thermostat response error)" )
            }
            return
        }

        /* Process the response */
        state.failCount = 0

        state.availablemodes = data.availablemodes ?: 0
        updateChanged( "supportedThermostatModes",
                      groovy.json.JsonOutput.toJson(coalesce( state.availablemodes, { val->[
                          [ "off","heat","cool","auto" ], /* 0 */
                          [ "off","heat","cool" ],        /* 1 */
                          [ "off","heat" ],               /* 2 */
                          [ "off","cool" ]                /* 3 */
                      ][val] } )), "Supported operating modes have changed" )

        state.mode = data.mode
        state.fan = data.fan
        if ( data.tempunits != null ) {
            state.tempunits = data.tempunits
        } else if ( "T2" == state.model ) {
            D("forcing degrees Celsius for ${state.model}")
            state.tempunits = 1
        } else if ( data.heattempmax != null ) {
            state.tempunits = data.heattempmax >= 40 ? 0 : 1
            D("missing tempunits; guessing ${state.tempunits?"C":"F"} from heattempmax=${data.heattempmax}")
        } else {
            state.tempunits = null
            scale = null
            W("can't ascertain temperature units; no conversion applied")
        }
        state.spacetemp = data.spacetemp
        state.heattemp = data.heattemp
        state.heattempmin = data.heattempmin
        state.heattempmax = data.heattempmax
        state.cooltemp = data.cooltemp
        state.cooltempmin = data.cooltempmin
        state.cooltempmax = data.cooltempmax
        state.setpointdelta = data.setpointdelta
        state.hum_setpoint = data.hum_setpoint
        state.dehum_setpoint = data.dehum_setpoint
        state.schedule = data.schedule

        if ( state.pollInterval > 0 ) {
            updateChanged( "online", true, "Thermostat is now ONLINE" )
        }

        updateChanged( "name", data.name, "Thermostat name changed to ${data.name}" )

        def t = ['off','heat','cool','auto'][ data.mode ]
        updateChanged( "thermostatMode", t, "Thermostat mode changed to ${t}" )

        /* We add our own HE operating state "fan only" when thermostat is idle but fan is on */
        t = ['idle','heating','cooling','delayed'][ data.state ]
        if ( t == "idle" && data.fanstate != 0 ) {
            t = "fan only"
        }
        updateChanged( "thermostatOperatingState", t, "Thermostat state now ${t}" )

        t = [ 'auto', 'on' ][ data.fan ]
        updateChanged( "thermostatFanMode", t, "Thermostat fan mode is now ${t}" )
        if ( data.fanstate != 0 ) {
            updateChanged( "thermostatFanOperatingState", 'on', "Fan is now running" )
        } else {
            updateChanged( "thermostatFanOperatingState", 'off', "Fan is now stopped" )
        }

        if ( null != data.activestage ) {
            updateChanged( "activeStages", data.activestage, "Number of active stages is now ${data.activestage}" )
        }

        /* Convert thermostat's temperature units to hub's units and store */
        if ( scale == "F" && data.tempunits == 1 ) {
            /* Thermostat is C and Hubitat is F */
            data.spacetemp = coalesce( data.spacetemp, { temp->round( celsiusToFahrenheit( temp ) ) } )
            data.cooltemp = coalesce( data.cooltemp, { temp->round( celsiusToFahrenheit( temp ) ) } )
            data.heattemp = coalesce( data.heattemp, { temp->round( celsiusToFahrenheit( temp ) ) } )
        } else if ( scale == "C" && data.tempunits == 0 ) {
            data.spacetemp = coalesce( data.spacetemp, { temp->round( fahrenheitToCelsius( temp ) ) } )
            data.cooltemp = coalesce( data.cooltemp, { temp->round( fahrenheitToCelsius( temp ) ) } )
            data.heattemp = coalesce( data.heattemp, { temp->round( fahrenheitToCelsius( temp ) ) } )
        }
        updateChanged( "temperature", data.spacetemp, "Ambient temperature is now ${data.spacetemp}", scale )
        updateChanged( "coolingSetpoint", data.cooltemp, "Cooling setpoint is now ${data.cooltemp}", scale )
        updateChanged( "heatingSetpoint", data.heattemp, "Heating setpoint is now ${data.heattemp}", scale )

        /* Generic setpoint. If thermostat mode is heating, or currently in heating state, set to heating setpoint.
         * Apply same logic for cooling.
         */
        if ( data.mode == 1 || data.state == 1 ) {
            updateChanged( "thermostatSetpoint", data.heattemp, "Setpoint is now ${data.heattemp}", scale )
        } else if ( data.mode == 2 || data.state == 2 ) {
            updateChanged( "thermostatSetpoint", data.cooltemp, "Setpoint is now ${data.cooltemp}", scale )
        }

        /* Humidity support is broken on T59x0 (fields are odd), so work around that. T7xx0/8xx0 are normal/expected. */
        if ( thermostatModel == "2" ) {
            /* This is based on T59x0 running firmware 4.08 2022-04-14 */
            updateChanged( "humidity", data.hum_setpoint, "Ambient relative humidity is now ${data.hum_setpoint}", "%" )
            updateChanged( "humidificationSetpoint", data.hum, "Humidification setpoint is now ${data.hum}", "%" )
            updateChanged( "dehumidifcationSetpoint", data.dehum_setpoint, "Dehumidification setpoint is now ${data.dehum_setpoint}", "%" )
        } else if ( thermostatModel != "1" && null != data.hum ) {
            /* This is normal. */
            updateChanged( "humidity", data.hum, "Ambient relative humidity is now ${data.hum}", "%" )
            updateChanged( "humidificationSetpoint", data.hum_setpoint, "Humidification setpoint is now ${data.hum_setpoint}", "%" )
            updateChanged( "dehumidifcationSetpoint", data.dehum_setpoint, "Dehumidification setpoint is now ${data.dehum_setpoint}", "%" )
        }

        t = coalesce( data.schedule, { val->['stopped','running'][val] }, 'unknown' )
        updateChanged( "program", t, "Program is now ${t}" )
        t = coalesce( data.schedulepart, { val->val==255?'n/a':(['morning','day','evening','night'][val]) }, 'unknown' )
        updateChanged( "schedulePeriod", t, "Schedule period is now ${t}" )

        t = coalesce( data.away, { val->['present','not present'][val] }, 'unknown' )
        updateChanged( "presence", t, "Thermostat now in ${t == 'unknown' ? t : ['HOME','AWAY'][data.away]} mode (presence)" )

        if ( state.type != 'residential' ) {
            t = coalesce( data.override, { val->['off','on'][val] }, 'unknown' )
            updateChanged( "override", t, "Override now ${t}" )
        }
    } else {
        E( "info query failed: ${response.getStatus()}, ${response.getStatusLine()}" )
        // state.lastdata = null
        sendEvent( name: "lastdata", value: response.getStatusLine() )
        if ( ++state.failCount >= 3 ) {
            updateChanged( "online", false, "OFF-LINE (error)" )
        }
    }
}

private def basicAuth() {
    /* RFC7617: user:pass -- Aladdin:open sesame --> QWxhZGRpbjpvcGVuIHNlc2FtZQ== */
    return "Basic ${authUser}:${authPass}".bytes.encodeBase64().toString()
}

private def digestAuth( path, authReq, method="GET" ) {
    /* Algorithm determined by server; defaults to MD5 */
    def algMap = [ 'MD5':'MD5', 'SHA-256':'SHA-256' ] /* supported algorithm and java..MessageDigest equivalent name */
    def respalg = ( authReq?.algorithm ?: "MD5" ).trim().toUpperCase()
    if ( algMap[ respalg ] == null ) {
        respalg = 'MD5' /* fallback */
    }
    def H = { s->java.security.MessageDigest.getInstance(algMap[respalg]).digest(s.getBytes("UTF-8")).encodeHex().toString() }
    def HA1 = H("${authUser.trim()}:${authReq.realm}:${authPass}")
    def HA2 = H("${method.toUpperCase()}:${path}")
    def cnonce = java.util.UUID.randomUUID().toString().replaceAll('-', '').substring(0, 8)
    /* Reset NC per nonce. In a perfect world, we'd store the original www-authenticate response values and just build a new
     * header for each subsequent request, until we get a 401 from the server, when we toss it and start over with the new
     * data the server provides and that response. That would eliminate the double-querying that is going on now for every
     * request. But, I haven't figure out how HE does persistent storage for that kind of thing that isn't in the 'state'
     * structure, which is kind of public, so not a great choice for storage of this data.
     */
    if ( state.nonce == authReq.nonce ) {
        state.nc = state.nc + 1
    } else {
        state.nc = 1
        state.nonce = authReq.nonce
    }
    def response = H("${HA1}:${authReq.nonce}:${state.nc}:${cnonce}:${authReq.qop}:${HA2}")
    return "Digest username=\"${authUser.trim()}\", realm=\"${authReq.realm}\", qop=\"${authReq.qop}\", algorithm=\"${respalg}\"" +
        ", uri=\"${path}\", nonce=\"${authReq.nonce}\", cnonce=\"${cnonce}\", opaque=\"${authReq.opaque == null ? "" : authReq.opaque}\"" +
        ", nc=${state.nc}, response=\"${response}\""
}

private def _request( rp, callback ) {
    D("_request(${rp}, callback")
    def tries = 0
    def last_e = null
    while ( true ) {
        if ( ++tries > 3 ) {
            E("_request(): too many retries; aborting (${rp.url})")
            callback.call( e.getResponse(), last_e )
            break
        }
        try {
            if ( rp.headers?.Authorization == null ) {
                state.authmethod = 'none'
            }
            if ( "https" == requestProto ) {
                rp.ignoreSSLIssues = true
            }
            D("_request() sending request ${rp}")
            if ( rp.body == null ) {
                D("_request: requesting (GET) ${rp.uri}")
                httpGet( rp ) { resp->callback.call( resp, null ) }
            }
            else {
                D("_request: sending POST to ${rp.uri} with body ${rp.body}")
                httpPost( rp ) { resp->callback.call( resp, null ) }
            }
            break
        } catch ( groovyx.net.http.HttpResponseException e ) {
            D("_request(): caught response exception ${e.class}: ${e}")
            last_e = e
            if ( e.getResponse().getStatus() == 401 && "" != authUser ) {
                if ( rp.headers?.Authorization != null ) {
                    D("_request(): attempted auth failed with ${rp.headers.Authorization}")
                    state.nc = 0
                    callback.call( e.getResponse(), e )
                    break
                }
                /* Do auth if we can */
                def authstring = e.getResponse().headers.'www-authenticate'
                // ??? What if multiple www-authenticate headers are returned? We need to pick a supported algorithm...
                def ah
                if ( authstring.startsWith( 'Digest ' ) ) {
                    def authmap = authstring.replaceAll("Digest ", "").replaceAll(", ", ",").findAll(/([^,=]+)=([^,]+)/) { full, name, value -> [name, value.replaceAll("\"", "")] }.collectEntries( { it })
                    ah = digestAuth( new java.net.URI( rp.uri ).getPath(), authmap )
                    state.authmethod = 'digest'
                } else {
                    ah = basicAuth()
                    state.authmethod = 'basic'
                }
                if ( rp.headers == null ) {
                    rp.headers = [ 'Authorization': ah ]
                } else {
                    rp.headers.Authorization = ah
                }
                D("_request(): resubmitting with ${rp.headers}")
                madeWithAuth = true
            } else if ( e.getResponse().getStatus() == 200 ) {
                D("_request: unparsable response")
                callback.call( e.getResponse(), e.getCause() )
                break
            } else {
                callback.call( e.getResponse(), e )
                break
            }
        } catch( e ) {
            E( "_request(): ${e.class} ${e}" )
            callback.call( null, e )
            break
        }
    }
}

private def defaultCommandCallback( resp, err ) {
    // def result = resp.data
    /* Unconditionally schedule refresh */
    unschedule()
    runInMillis( 2000, "do_refresh" )
    if ( err ) {
        E("command failed: ${e}")
    } else if ( resp.getStatus() != 200 ) {
        E("thermostat response ${resp.getStatus()} ${resp.getStatusLine()}")
    } else {
        D("thermostat response ${resp.getStatus()} data=${resp.getData()}")
    }
}

private def sendCommand( command, reqparams, callback=null ) {
    D("sendCommand(${command}, ${reqparams})")
    def uri = "${requestProto}://${thermostatIp}/${command}"
    def body = ""
    if ( reqparams instanceof java.util.Map ) {
        D("sendCommand handling ${command} reqparams as Map")
        reqparams.each( { key, val -> body += "${key}=${java.net.URLEncoder.encode(val.toString(), 'UTF-8')}&" } );
    } else if ( reqparams instanceof java.util.List ) {
        D("sendCommand handling ${command} reqparams as List")
        def li = reqparams.listIterator()
        while ( li.hasNext() ) {
            def el = li.next()
            body += "${el.key}=${java.net.URLEncoder.encode(el.value.toString(), 'UTF-8')}&"
        }
    } else if ( reqparams != null ) {
        E("bug, can't handle reqparams=${reqparams.class}")
    }
    body = body.replaceAll( /&$/, "" )  /* T3900 can't parse trailing & */
    D("sending request to ${uri.toString()}")
    def params = [
        uri: uri,
        body: (body.equals("") ? null : body),
        contentType: "application/json",
        requestContentType: "application/x-www-form-urlencoded",
        timeout: 15
    ]
    _request( params, callback ?: { r, e -> defaultCommandCallback(r, e) } )
}

private def control() {
    /* Mode and setpoints have to be sent together, in specific order (so use array/list) */
    sendCommand( "control", [
        [ key: 'mode', value: state.mode ],
        [ key: 'heattemp', value: state.heattemp ],
        [ key: 'cooltemp', value: state.cooltemp ]
    ] )
}

private def set_humidity_setpoints() {
    /* Humidity setpoints must be sent together, in order, like control() */

    def params = [ hum_setpoint: state.hum_setpoint, dehum_setpoint: state.dehum_setpoint ]
    sendCommand( "settings", [
        [ key: 'hum_setpoint', value: state.hum_setpoint ],
        [ key: 'dehum_setpoint', value: state.dehum_setpoint ]
    ] )
}

def checkProgram() {
    if ( state.schedule == 1 ) {
        if ( autoProgramStop ) {
            programStop()
            return true
        } else {
            return false
        }
    }
    return true
}

def auto() {
    if ( checkProgram() ) {
        state.mode = 3
        control()
    } else {
        E( "mode changes are ignored when program is running" )
    }
}

def cool() {
    if ( checkProgram() ) {
        state.mode = 2
        control()
    } else {
        E( "mode changes are ignored when program is running" )
    }
}

def emergencyHeat() {
    if ( checkProgram() ) {
        W("mode 'emergency heat' is not supported; setting mode to HEAT")
        state.mode = 1
        control()
    } else {
        E( "mode changes are ignored when program is running" )
    }
}

def fanAuto() {
    sendCommand( 'control', [ fan: 0 ] )
}

def fanCirculate() {
    W("fan 'circulate' mode is not supported; setting fan mode to AUTO")
    fanAuto()
}

def fanOn() {
    sendCommand( 'control', [ fan: 1 ] )
}

def heat() {
    if ( checkProgram() ) {
        state.mode = 1
        control()
    } else {
        E( "mode changes are ignored when program is running" )
    }
}

def off() {
    if ( checkProgram() ) {
        state.mode = 0
        control()
    } else {
        E( "mode changes are ignored when program is running" )
    }
}

def setCoolingSetpoint( temperature ) {
    def ttemp = temperature
    def scale = configUnits == "hub" ? getTemperatureScale() : configUnits
    D("setCoolingSetpoint(${temperature}): scale=${scale}, tempunits=${state.tempunits}")
    if ( scale == "C" && state.tempunits == 0 ) {
        ttemp = round( celsiusToFahrenheit( ttemp ) )
    } else if ( scale == "F" && state.tempunits == 1 ) {
        ttemp = round( fahrenheitToCelsius( ttemp ) )
    }
    if ( ttemp < state.cooltempmin ) {
        W( "requested cooling setpoint ${ttemp} is below minumum (${state.cooltempmin}); limiting to minimum" )
        ttemp = state.cooltempmin
    } else if ( ttemp > state.cooltempmax ) {
        W( "requested cooling setpoint ${ttemp} exceeds maximum (${state.cooltempmax}); limiting to maximum" )
        ttemp = state.cooltempmax
    }
    if ( state.heattemp > ( ttemp - state.setpointdelta ) ) {
        /* Move heat setp down to honor differential; if we bump heat min, everything has to move up */
        state.heattemp = ttemp - state.setpointdelta
        if ( state.heattemp < state.heattempmin ) {
            state.heattemp = state.heattempmin
            state.cooltemp = state.heattempmin + state.setpointdelta
            W( "adjusting heating and cooling setpoints to honor limits and differential; cooling now ${state.cooltemp}, heating ${state.heattemp}" )
        } else {
            W( "adjusting heating setpoint to ${state.heattemp} to preserve differential (${state.setpointdelta})" )
        }
    }
    state.cooltemp = ttemp
    control()
}

def setHeatingSetpoint( temperature ) {
    def ttemp = temperature
    def scale = configUnits == "hub" ? getTemperatureScale() : configUnits
    D("setHeatingSetpoint(${temperature}): scale=${scale}, tempunits=${state.tempunits}")
    if ( scale == "C" && state.tempunits == 0 ) {
        ttemp = round( celsiusToFahrenheit( ttemp ) )
    } else if ( scale == "F" && state.tempunits == 1 ) {
        ttemp = round( fahrenheitToCelsius( ttemp ) )
    }
    if ( ttemp < state.heattempmin ) {
        W( "requested heating setpoint ${ttemp} is below minumum (${state.heattempmin}); limiting to minimum" )
        ttemp = state.heattempmin
    } else if ( ttemp > state.heattempmax ) {
        W( "requested heating setpoint ${ttemp} exceeds maximum (${state.heattempmax}); limiting to maximum" )
        ttemp = state.heattempmax
    }
    if ( state.cooltemp < ( ttemp + state.setpointdelta ) ) {
        /* Move cool setp up to honor differential; if we bump cool max, everything has to move down */
        state.cooltemp = ttemp + state.setpointdelta
        if ( state.cooltemp > state.cooltempmax ) {
            state.cooltemp = state.cooltempmax
            state.heattemp = state.cooltempmax - state.setpointdelta
            W( "adjusting heating and cooling setpoints to honor limits and differential; heating now ${state.heattemp}, cooling ${state.cooltemp}" )
        } else {
            W( "adjusting cooling setpoint to ${state.cooltemp} to preserve differential (${state.setpointdelta})" )
        }
    }
    state.heattemp = ttemp
    control()
}

def setSchedule( sched ) {
    /* not implemented -- deprecated method */
}

def setThermostatFanMode( fanmode ) {
    D("setThermostatFanMode(${fanmode})")
    fanmode = fanmode.trim()
    if ( fanmode == 'on' ) {
        state.fan = 1 /* on */
    } else if ( fanmode == 'auto' ) {
        state.fan = 0 /* auto */
    }
    sendCommand( 'control', [ fan: state.fan ] )
}

def setThermostatMode( mode ) {
    mode = mode.trim()
    if ( mode == 'auto' ) {
        state.mode = 3
    } else if ( mode == 'cool' ) {
        state.mode = 2
    } else if ( mode == 'heat' ) {
        state.mode = 1
    } else if ( mode == 'off' ) {
        state.mode = 0
    }
    control()
}

def setPollingInterval( num ) {
    if ( num < 0 ) {
        num = pollInterval
    }
    if ( num >= 0 && num <= 86400 ) {
        unschedule()
        state.pollInterval = num
        if ( num > 0 ) {
            def nextpoll = state.lastpoll + ( num * 1000 ) - now()
            if ( nextpoll < 10 ) {
                nextpoll = 10
            }
            D("setPollingInterval(${num}) lastpoll ${state.lastpoll} nextpoll ${nextpoll}ms")
            runInMillis( nextpoll as Integer, "do_refresh" )
        }
    }
}

def home() {
    sendCommand( 'settings', [ away: 0 ] )
}

def away() {
    sendCommand( 'settings', [ away: 1 ] )
}

def setHumidificationSetpoint( rel ) {
    if ( state.hum_setpoint != null ) {
        /* ??? enforce deadband? */
        if ( rel >= 0 && rel < 100 ) {
            state.hum_setpoint = rel
            set_humidity_setpoints()
        }
    } else {
        D( "humidification setpoint is not supported by the thermostat" )
    }
}

def setDehumidificationSetpoint( rel ) {
    if ( state.dehum_setpoint != null ) {
        /* ??? enforce deadband? */
        if ( rel >= 0 && rel < 100 ) {
            state.dehum_setpoint = rel
            set_humidity_setpoints()
        }
    } else {
        D( "dehumidification setpoint is not supported by the thermostat" )
    }
}

def setProgram( action ) {
    sendCommand( 'settings', [ schedule: action.trim() == "run" ? 1 : 0 ] )
}

def programRun() {
    sendCommand( 'settings', [ schedule: 1 ] )
}

def programStop() {
    sendCommand( 'settings', [ schedule: 0 ] )
}
