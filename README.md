# Venstar ColorTouch Thermostat Driver for Hubitat Elevation

<small>By [toggledbits](https://www.toggledbits.com/)</small>

This driver for Hubitat Elevation works with [Venstar ColorTouch Thermostats](https://venstar.com/thermostats/colortouch/) that have Wi-Fi support and local API. It uses the local API only, and does not use the Skyport cloud to access the thermostat. The following thermostats are known to work:

* T7850
* T7900

I suspect that the commercial versions of these models (the T8850 and T8900) will also work.

This is a new, completely rewritten driver offered under the very liberal MIT license. The previous maintainer "withdrew" his version of the driver &mdash; a questionable concept legally, but regardless, I'm happy to start over and license a new work under terms more favorable to the Hubitat community. For clarity, as this driver is now released to the public as Open Source under the MIT License, under U.S. law (the governing law for this author), the "performance" has been started and thus the license and your right to use this driver can never be withdrawn. Enjoy!

Other features of this new version include:

* Support for standard capabilities: Thermostat, TemperatureMeasurement, RelativeHumidityMeasurement, PresenceSensor;
* Extended (driver-specific) support for humidification/dehumidification (T7900/8900);
* Reports heating/cooling stages running on multi-stage units;
* Control of effective state of thermostat's program schedule;
* Control of the selection of *Home* or *Away* (e.g. vacation) settings selection;
* HTTPS (SSL/TLS) support with optional HTTP user authentication for improved security on your LAN (HTTP Basic and Digest authentication methods supported).

Future:

* Child devices for additional sensors;
* Retrieval/storage/availability of thermostat's collected runtime stats.

Refer to the `CHANGELOG` file for release notes.

## About the Author

Before diving in, I'll say that I'm relatively new to Hubitat and this is my first driver for the platform. I am and have been a prolific developer of plugins for the [Vera](https://community.ezlo.com/) hub (including the Vera Venstar ColorTouch driver; I'm known as rigpapa in that community and have 14 plugins widely used), and I am the author of [Multi-hub Reactor](https://reactor.toggledbits.com/docs/), a cross-hub rules engine that serves as an alternative to Node-Red and the rules engines of Vera, Home Assistant, Hubitat, and eZLO hubs. So while I have some experience in HA, my work here in the Hubitat community is relatively new, and I'd appreciate any guidance as I learn the platform and groovy.

## Installation

### Configuring Your Venstar ColorTouch Thermostat

**IMPORTANT!** Be sure to perform these configuration steps before installing the driver. It will make your life easier.

The following must be configured on your thermostat:

1. Go to the "Wi-Fi Status" page (under the "Wi-Fi" menu), and confirm that you are connected to the network and have good signal strength--if not, configure or troubleshoot that before continuing (see your thermostat's documentation);
1. Under the "Wi-Fi" menu, turn on the "Local API";
1. Under the "Wi-Fi" menu, select 'HTTP' as the "API Protocol" for now; you can change it later, but let's keep the variables to a minimum for initial setup.
1. Assign your thermostat a DHCP-reserved IP address at your DHCP server/router, or a static IP address in the thermostat interface.
1. Power-cycle the thermostat.

Once you have completed these steps, confirm that your thermostat's API is reachable by opening the thermostat in a browser. For example, if your thermostat's assigned IP (from step 2) is 192.168.9.100, then you would open `http://192.168.9.100/`. You should get a short JSON response containing the string "api_ver" (it does not matter what version of the API your thermostat uses).

### Driver Installation (on hub)

1. Log in to your hub.
2. Go to the *Drivers Code* page.
3. If *Venstar ColorTouch Thermostat* is listed there, click on that entry to go into its code. Otherwise, click the *New Driver* button.
4. Click the *Import* button.
5. Copy-paste this URL:

        https://raw.githubusercontent.com/toggledbits/VenstarColorTouch-Hubitat/main/drivers/VenstarColorTouch.groovy

6. Click the *Import* button. Your driver will be installed/updated.

You can then create a device for the thermostat using the driver.

## Device Configuration

Configuration should be pretty straight-forward. You need to supply the IP address of the thermostat and select the API protocol (HTTP or HTTPS), at a minimum, and of course, these need to match the configuration of the thermostat itself.

If you are using HTTPS, the thermostat will allow you to configure an authentication username and password. If you choose this option, you will need to supply that username and password to the driver in the fields indicated. Otherwise, leave these fields blank. See *Improving Security* below.

The *Polling Interval* determines how often the driver queries the thermostat for new data. The Venstar local API does not "notify" the driver/hub of changes, so the driver must poll. This creates a delay in response to manual changes at the thermostat up to the length of the polling interval. The protocol is lightweight, so a polling interval of 60 seconds (the default) should present no significant load to either the hub or thermostat. Intervals of less than 15 seconds are not recommended, however.

Some commands, like mode changes, are not honored by the thermostat when its programmed schedule is in effect (running). When *Auto Program Stop* is on, the thermostat's running program will be stopped if a command is attempted that would otherwise be ignored. If you turn this setting off, you will need to stop the running program yourself (see the `setProgram` and `programStop` commands) before issuing mode changes, to ensure that they will be honored by the thermostat.

## Operation

The driver provides the following standard Hubitat capabilities:

* Thermostat
* TemperatureMeasurement
* RelativeHumidityMeasurement (not used with T7850/8850)
* PresenceSensor
* Refresh
* Sensor
* Actuator

The following driver-specific attributes are defined:

* `name` &mdash; [string] the name provided by the thermostat; this is informational only and is not otherwise used by the driver. It may assist you during setup in differentiating one of many thermostats in your home.
* `online` &mdash; [boolean] the current communications state of the thermostat; if the driver cannot communicate with the thermostat, this attribute will be *false*.
* `activeStages` &mdash; [number] when heating or cooling with a multi-stage unit, this reflects the number of heating or cooling stages running as reported by the thermostat.
* `thermostatFanOperatingState` &mdash; [enum `off`, `on`] operating state of the fan/blower.
* `humdificationSetpoint` and `dehumidificationSetpoint` &mdash; [number] for the T7900/8900 models that support humidity, these are the setpoints for humidification and dehumdification, respectively. The availability of these values may be further conditioned by the configuration of the thermostat.
* `program` &mdash; [enum `running`, `stopped`, `unknown`] state of the thermostat's programmed schedule.
* `schedulePeriod` &mdash; [enum `morning`, `day`, `evening`, `night`, `n/a`, `unknown`] the current effective period of the thermostat's programmed schedule; will be `n/a` if the program is stopped.
* `override` &mdash; [enum `off`, `on`, `unknown`] used on commercial models only to indicate a forced temporary change in occupancy status.
* `lastupdate` &mdash; [date] date/time of the last successful update from the thermostat.

The following driver-specific commands are defined:

* `home` &mdash; changes the thermostat's Home/Away program mode to "Home".
* `away` &mdash; changes the thermostat's Home/Away program mode to "Away".
* `setHumidicationSetpoint` and `setDehumidificationSetpoint` &mdash; for models T7900/8900 that are configured for (de)humidification control, these commands take a single (integer 0-99) parameter to modify the respective setpoint.
* `setProgram` &mdash; sets the thermostat's program state; the single parameter may be `run` to make the thermostat's programmed schedule effective, or `stop` to stop the programmed schedule (indefinitely). Note that when the program is running, the thermostat may ignore mode change commands, and setpoint changes are temporary.
* `programRun` and `programStop` &mdash; these are parameter-less aliases for `setProgram "run"` and `setProgram "stop"`, respectively.
* `setPollingInterval` &mdash; take a single (integer >= 0) parameter to *temporarily* override the polling interval of the thermostat; units are seconds. Setting 0 disables polling. The polling interval returns to the configured state when the hub is rebooted.

## Other Details

Here are some other things/behaviors you need to know about:

* The `thermostatSetpoint` reflects the setpoint last in effect. If the thermostat mode is `heat` or `cool`, then it's pretty clear what this value should be. When the mode is `auto`, it's less clear, so the determination is made based on the operating state: if the thermostat is currently (or was most recently) heating the space, then the heating setpoint is certainly in effect and its value is shown; likewise for cooling. I don't think this value is very useful, honestly, but it's part of the standard capability so I've implemented it, as described. Use the mode-specific setpoints in preference to this generic one.
* When setting the heating and cooling setpoints, there is a "deadband" enforced between the two. This is configurable at the thermostat (referred to as the "Setpoint Delta"), and the driver uses this value as well to enforce the separation between the setpoints. For example, if you try to set the cooling setpoint too close to, or below, the heating setpoint, the heating setpoint is reduced to maintain the deadband. The extrema (min/max allowed values) of the setpoints are also considered and enforced.
* If the fan is running when the thermostat is idle (not heating or cooling), the `thermostatOperatingState` will be reported as `fan only` (in addition to the `thermostatFanOperatingState` showing `on`).
* While the thermostat does have a "circulate" mode in which the fan is run for a configured number of minutes per hour, this is not specifically a separate mode for the fan, it just happens when the fan is in "auto" mode (and you can only disable the feature by reconfiguring the thermostat). Therefore, the `fanCirculate` command is a synonym for `fanAuto`.
* The thermostat has no deadband for the humidification and dehumidication setpoints currently, so no attempt is made to enforce their separation. Caveat emptor.
* I have not, as yet, seen any identifiable way on the thermostat or in the API to know when humidification or dehumidification is running, so there is no state for these. Perhaps Venstar will add these to a future firmware and API.
* If your thermostat does not support heating or cooling, that may be reflected in `supportedThermostatModes`, but it is not enforced by the driver.

## Improving Security -- HTTPS and User Authentication ##

With respect to security, there are basically three generations of Venstar ColorTouch thermostat firmware: those that support no security whatsoever (very outdated firmware), those that support HTTP Basic Authentication and HTTPS (not the latest firmware, but improved), and those that support HTTP Digest Authentication and HTTPS (not perfect, but much better than Basic). Users concerned with vulnerability of their IoT devices should use HTTPS with username/password authentication. Ideally, those users should upgrade their thermostats to the latest firmware that does HTTP Digest authentication (mine currently are at 6.91 and support Digest authentication).

At the thermostat:

1. In the thermostat's Wi-Fi menu, set the API Protocol to HTTPS; some versions of firmware will require you to (temporarily) disable the local API to change these settings.
1. Set the HTTP Authentication username and password.
1. Re-enable the local API if you had to turn it off to modify the above settings.
1. Power-cycle the thermostat or restart it using the Installer menu.

In the Hubitat UI:

1. Open the thermostat device (click its name in the *Devices* page).
1. In the device's *Preferences* section, set the *Local API Protocol* to `https` and provide the username and password you configured at the thermostat in the device configuration fields *Auth Username* and *Auth Password*
1. Click the *Save Preferences* button. The driver will restart and requery the thermostat, so you should observe the `lastUpdate` field (in *Current States* for the device) change (or if you miss the actual change, the time shown should be seconds from the current time). You can also check the logs for any errors from the driver. Always try rebooting the thermostat if you have problems after modifying its authentication configuration. And of course, make sure to double-check your username and password configuration at both the thermostat and driver; they must agree in every respect.

> NOTE: Although the thermostat refers to these in its UI as "Basic Auth", they could use either Basic or Digest authentication as determined by the thermostat's firmware. The driver will use whichever the thermostat supports/offers.

## Reporting Bugs/Enhancement Requests ##

Support questions, bug reports and enhancement requests are welcome! There are two ways to share them:

1. I have a strong preference for the use of the ["Issues" section of the Github repository](https://github.com/toggledbits/VenstarColorTouch-Hubitat/issues) for reporting of (actual or suspected) bugs.
1. I'm happy to answer questions directed to me (@toggledbits) in posts or PMs on the [Hubitat Community Forums](https://community.hubitat.com/).

<hr>

Copyright 2021 Patrick H. Rigney (toggledbits), All Rights Reserved. Please see the LICENSE file for further information.
