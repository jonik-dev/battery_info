import 'package:battery_info/enums/charging_status.dart';

class IosBatteryInfo {
  int? batteryLevelPercentage;
  ChargingStatus? chargingStatus;
  IosBatteryInfo({this.batteryLevelPercentage, this.chargingStatus});

  /// Serialise data back to json from the model
  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = new Map<String, dynamic>();
    data["batteryLevelPercentage"] = this.batteryLevelPercentage;
    data["chargingStatus"] = this.chargingStatus;
    return data;
  }

  ChargingStatus getChargingStatus(String? status) {
    switch (status) {
      case "charging":
        return ChargingStatus.Charging;
      case "unplugged":
        return ChargingStatus.Discharging;
      case "full":
        return ChargingStatus.Full;
      default:
        return ChargingStatus.Unknown;
    }
  }

  @override
  IosBatteryInfo.fromJson(Map<String, dynamic> json) {
    this.batteryLevelPercentage = json["batteryLevelPercentage"];
    this.chargingStatus = getChargingStatus(json["batteryStatus"]);
  }
}
