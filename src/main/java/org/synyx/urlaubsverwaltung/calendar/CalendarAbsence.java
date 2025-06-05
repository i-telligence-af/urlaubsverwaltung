package org.synyx.urlaubsverwaltung.calendar;

import org.synyx.urlaubsverwaltung.application.vacationtype.ProvidedVacationType;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationType;
import org.synyx.urlaubsverwaltung.period.Period;
import org.synyx.urlaubsverwaltung.person.Person;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.synyx.urlaubsverwaltung.calendar.CalendarAbsenceType.DEFAULT;
import static org.synyx.urlaubsverwaltung.calendar.CalendarAbsenceType.HOLIDAY_REPLACEMENT;

public class CalendarAbsence {

    private final ZonedDateTime startDate;
    private final ZonedDateTime endDate;
    private final Person person;
    private final boolean isAllDay;
    private final CalendarAbsenceType calendarAbsenceType;

    private final VacationType vacationType;

    public CalendarAbsence(Person person, Period period, CalendarAbsenceConfiguration absenceTimeConfiguration, VacationType vacationType) {
        this(person, period, absenceTimeConfiguration, DEFAULT, vacationType);
    }

    public CalendarAbsence(Person person, Period period, CalendarAbsenceConfiguration absenceTimeConfiguration){
        this(person, period, absenceTimeConfiguration, DEFAULT, null);
    }

    public CalendarAbsence(Person person, Period period, CalendarAbsenceConfiguration absenceTimeConfiguration, CalendarAbsenceType calendarAbsenceType, VacationType vacationType) {

        this.person = person;
        this.calendarAbsenceType = calendarAbsenceType;
        this.vacationType = vacationType;

        final ZonedDateTime periodStartDate = period.startDate().atStartOfDay(ZoneId.of(absenceTimeConfiguration.timeZoneId()));
        final ZonedDateTime periodEndDate = period.endDate().atStartOfDay(ZoneId.of(absenceTimeConfiguration.timeZoneId()));

        switch (period.dayLength()) {
            case FULL -> {
                this.startDate = periodStartDate;
                this.endDate = periodEndDate.plusDays(1);
                this.isAllDay = true;
            }
            case MORNING -> {
                this.startDate = periodStartDate.with(absenceTimeConfiguration.morningStartTime());
                this.endDate = periodEndDate.with(absenceTimeConfiguration.morningEndTime());
                this.isAllDay = false;
            }
            case NOON -> {
                this.startDate = periodStartDate.with(absenceTimeConfiguration.noonStartTime());
                this.endDate = periodEndDate.with(absenceTimeConfiguration.noonEndTime());
                this.isAllDay = false;
            }
            default -> throw new IllegalArgumentException("Invalid day length!");
        }
    }

    public CalendarAbsence(Person person, Period period, CalendarAbsenceConfiguration absenceTimeConfiguration, CalendarAbsenceType calendarAbsenceType){
        this(person, period, absenceTimeConfiguration, calendarAbsenceType, null);
    }

    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public ZonedDateTime getEndDate() {
        return endDate;
    }

    public Person getPerson() {
        return person;
    }

    public boolean isAllDay() {
        return isAllDay;
    }

    public boolean isHolidayReplacement() {
        return calendarAbsenceType == HOLIDAY_REPLACEMENT;
    }

    public String getCalendarAbsenceTypeMessageKey() {
        if ( vacationType instanceof ProvidedVacationType providedVacationType && providedVacationType.isVisibleToEveryone() ){
            return providedVacationType.getMessageKey()  + ".person";
        }
        return calendarAbsenceType.getMessageKey();
    }

    @Override
    public String toString() {
        return "Absence{" +
            "startDate=" + startDate +
            ", endDate=" + endDate +
            ", person=" + person +
            ", isAllDay=" + isAllDay +
            ", absenceType=" + calendarAbsenceType +
            '}';
    }
}
