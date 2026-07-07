package com.example.slagalica.ranking;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public final class CycleUtils {

    private CycleUtils() {
    }

    public static String currentDayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime());
    }

    public static String currentWeekKey() {
        return weekKeyFor(isoCalendar());
    }

    public static String previousWeekKey() {
        Calendar calendar = isoCalendar();
        calendar.add(Calendar.WEEK_OF_YEAR, -1);
        return weekKeyFor(calendar);
    }

    public static String currentMonthKey() {
        Calendar calendar = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d",
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1);
    }

    public static String previousMonthKey() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -1);
        return String.format(Locale.US, "%04d-%02d",
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1);
    }

    public static String weekRangeLabel() {
        Calendar calendar = isoCalendar();
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
        String start = format.format(calendar.getTime());
        calendar.add(Calendar.DAY_OF_MONTH, 6);
        String end = format.format(calendar.getTime());
        return start + " - " + end;
    }

    public static String monthRangeLabel() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
        String start = format.format(calendar.getTime());
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        String end = format.format(calendar.getTime());
        return start + " - " + end;
    }

    private static Calendar isoCalendar() {
        Calendar calendar = Calendar.getInstance();
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.setMinimalDaysInFirstWeek(4);
        return calendar;
    }

    private static String weekKeyFor(Calendar calendar) {
        Calendar thursday = (Calendar) calendar.clone();
        thursday.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        thursday.add(Calendar.DAY_OF_MONTH, 3);
        int week = calendar.get(Calendar.WEEK_OF_YEAR);
        return String.format(Locale.US, "%04d-W%02d", thursday.get(Calendar.YEAR), week);
    }
}
