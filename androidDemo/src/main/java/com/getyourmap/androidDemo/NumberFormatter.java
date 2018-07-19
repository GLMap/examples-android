package com.getyourmap.androidDemo;

import java.util.Locale;

public class NumberFormatter {
  static Locale locale = Locale.getDefault();

  public static String NiceDoubleToString(double val) {
    if (Double.isNaN(val)) {
      return "---";
    }

    if (val < 10) {
      return String.format(locale, "%.2f", val);
    } else if (val < 100) {
      return String.format(locale, "%.1f", val);
    }
    return String.format(locale, "%.0f", val);
  }

  public static String FormatSize(long val) {
    double sizeInMB = (double) val / (1000 * 1000);
    return String.format("%s %s", NiceDoubleToString(sizeInMB), "MB");
  }
}
