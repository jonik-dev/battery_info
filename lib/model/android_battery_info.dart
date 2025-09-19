import 'package:battery_info/enums/charging_status.dart';

/// Android Battery Info data model
class AndroidBatteryInfo {
  int? currentNowMilliA;
  int? currentAverageMilliA;
  int? chargeTimeRemainingMs;
  String? health = "unknown";
  String? pluggedStatus = "unknown";
  String? technology = "unknown";
  int? batteryLevelPercentage;
  int? remainingbatteryCapacityMilliAh;
  int? remainingEnergyNWh;
  int? maximumBatteryLevel;
  double? temperatureC;
  int? voltageMilliV;
  bool? present;
  ChargingStatus? chargingStatus;

  AndroidBatteryInfo({
    this.remainingbatteryCapacityMilliAh,
    this.batteryLevelPercentage,
    this.chargeTimeRemainingMs,
    this.chargingStatus,
    this.currentAverageMilliA,
    this.currentNowMilliA,
    this.health,
    this.pluggedStatus,
    this.present,
    this.remainingEnergyNWh,
    this.maximumBatteryLevel,
    this.technology,
    this.temperatureC,
    this.voltageMilliV,
  });

  /// Serialise data back to json from the model
  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = new Map<String, dynamic>();
    data["remainingbatteryCapacityMilliAh"] = this.remainingbatteryCapacityMilliAh;
    data["batteryLevelPercentage"] = this.batteryLevelPercentage;
    data["chargingStatus"] = this.chargingStatus;
    data["chargeTimeRemainingMs"] = this.chargeTimeRemainingMs;
    data["currentAverageMilliA"] = this.currentAverageMilliA;
    data["currentNowMilliA"] = this.currentNowMilliA;
    data["health"] = this.health;
    data["pluggedStatus"] = this.pluggedStatus;
    data["present"] = this.present;
    data["maximumBatteryLevel"] = this.maximumBatteryLevel;
    data["remainingEnergyNWh"] = this.remainingEnergyNWh;
    data["temperatureC"] = this.temperatureC;
    data["technology"] = this.technology;
    data["voltageMilliV"] = this.voltageMilliV;
    return data;
  }

  /// Retrieves the chargin status from the native value
  ChargingStatus getChargingStatus(String? status) {
    switch (status) {
      case "charging":
        return ChargingStatus.Charging;
      case "discharging":
        return ChargingStatus.Discharging;
      case "full":
        return ChargingStatus.Full;
      default:
        return ChargingStatus.Unknown;
    }
  }

  /// Deserialize data from json
  AndroidBatteryInfo.fromJson(Map<String, dynamic> json) {
    this.remainingbatteryCapacityMilliAh = json["remainingbatteryCapacityMilliAh"];
    this.batteryLevelPercentage = json["batteryLevelPercentage"];
    this.chargingStatus = getChargingStatus(json["chargingStatus"]);
    this.chargeTimeRemainingMs = json["chargeTimeRemainingMs"];
    this.currentAverageMilliA = json["currentAverageMilliA"];
    this.currentNowMilliA = json["currentNowMilliA"];
    this.health = json["health"];
    this.pluggedStatus = json["pluggedStatus"];
    this.present = json["present"];
    this.maximumBatteryLevel = json["maximumBatteryLevel"];
    this.remainingEnergyNWh = json["remainingEnergyNWh"];
    this.technology = json["technology"];
    this.temperatureC = json["temperatureC"];
    this.voltageMilliV = json["voltageMilliV"];
  }
}
