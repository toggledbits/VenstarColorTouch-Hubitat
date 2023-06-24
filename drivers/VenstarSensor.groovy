metadata {
  definition(name: "Venstar Thermostat Sensor", namespace: "toggledbits.com", author: "Sasha Kotlyar") {
    capability "TemperatureMeasurement"
    capability "RelativeHumidityMeasurement"
    capability "AirQuality"
    capability "CarbonDioxideMeasurement"
    capability "IlluminanceMeasurement"
  }
}

def processSensorData(dataMap, providedTempUnits)
{
  if (dataMap["temp"] != null) {
    temp = dataMap["temp"]
    if (location.temperatureScale == "F" && providedTempUnits == 1) temp = celsiusToFahrenheit(temp)
    else if (location.temperatureScale == "C" && providedTempUnits == 0) temp = fahrenheitToCelsius(temp)
    temp = (temp as double).round(2)
    sendEvent(name: "temperature", value: temp, unit: "°${location.temperatureScale}", descriptionText: "Temperature is ${temp}°${location.temperatureScale}")
  }

  if (dataMap["hum"] != null) {
    humidity = dataMap["hum"]
    sendEvent(name: "humidity", value: humidity, descriptionText: "Relative humidity is ${humidity}%")
  }

  if (dataMap["iaq"] != null) {
    sendEvent(name: "airQualityIndex", value: dataMap["iaq"], descriptionText: "Indoor Air Quality (IAQ) is ${dataMap["iaq"]}")
  }

  if (dataMap["co2"] != null) {
    sendEvent(name: "carbonDioxide", value: dataMap["co2"], descriptionText: "Carbon dioxide level is ${dataMap["co2"]} ppm")
  }

  if (dataMap["intensity"] != null) {
    sendEvent(name: "illuminance", value: dataMap["intensity"], descriptionText: "Light intensity is ${dataMap["intensity"]}")
  } else {
    /* "intensity" is not included in the JSON payload when its value is 0, so remove the state data for equivalent functionality. */
    device.deleteCurrentState("illuminance")
  }
}
