# Changelog for Venstar ColorTouch Thermostat Hubitat Driver

## 22101

* Fix repeating message in log when thermostat does not report "activestage".
* Correct handling for polling interval=0
* Refresh command works even if polling is disabled (i.e. you can turn polling off but still update the device "manually").

## 21334

* Trim input strings for setThermostatMode and setThermostatFanMode; the Hubitat dashboard seems to add spaces to some values.

## 21270

* Add config to optionally force device units (default: use hub's configured unit)
* Improve error handling for certain exceptions during request
* Temperature unit default for T2000
* May support T5800/5900, but I can't test since I don't have one.

## 21268

* Clean up HTTP Digest authentication; add state variable indicator for which is used (debug assist);

## 21265

* Initial release/publication under MIT License.
