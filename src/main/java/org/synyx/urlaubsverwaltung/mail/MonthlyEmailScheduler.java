package org.synyx.urlaubsverwaltung.mail;

import com.opencsv.CSVWriter;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.synyx.urlaubsverwaltung.csv.CSVFile;
import org.synyx.urlaubsverwaltung.csv.CsvExportService;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.search.PageableSearchQuery;
import org.synyx.urlaubsverwaltung.sicknote.sickdays.SickDaysDetailedStatistics;
import org.synyx.urlaubsverwaltung.sicknote.sickdays.SickDaysStatisticsService;
import org.synyx.urlaubsverwaltung.sicknote.sicknote.SickNoteService;
import org.synyx.urlaubsverwaltung.web.FilterPeriod;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;
import static java.text.NumberFormat.getInstance;
import static java.time.format.DateTimeFormatter.ofLocalizedDate;
import static java.time.format.FormatStyle.MEDIUM;
import static java.time.format.FormatStyle.SHORT;

@Component
public class MonthlyEmailScheduler {
    private final MailSenderService mailSenderService;
    private final MailProperties mailProperties;
    private final SickNoteService sickNoteService;
    private final MessageSource messageSource;
    private final PersonService personService;
    private final SickDaysStatisticsService sickDaysStatisticsService;

    public MonthlyEmailScheduler(MailSenderService mailSenderService, MailProperties mailProperties, SickNoteService sickNoteService, MessageSource messageSource, PersonService personService, SickDaysStatisticsService sickDaysStatisticsService) {
        this.mailSenderService = mailSenderService;
        this.mailProperties = mailProperties;
        this.sickNoteService = sickNoteService;
        this.messageSource = messageSource;
        this.personService = personService;
        this.sickDaysStatisticsService = sickDaysStatisticsService;
    }

    // Einmal monatlich am 1. Tag um 8 Uhr
    // @Scheduled(cron = "0 0 8 1 * *")
    public void sendMonthlyEmail() {

        String from = generateMailAddressAndDisplayName(mailProperties.getFrom(), mailProperties.getFromDisplayName());
        String replyTo = "maximilian.radmacher@gmail.com";
        String email = "maximilian.radmacher@gmail.com";
        String subject = "Monatliche E-Mail";
        String body = "Dies ist eine automatisch generierte monatliche E-Mail.";

        // List<MailAttachment> mailAttachments = Arrays.asList();

        CSVFile csv = createAttachment();

        MailAttachment mailAttachment = new MailAttachment("sickdays_statistics.csv", csv.resource());

        ArrayList<MailAttachment> mailAttachments = new ArrayList<MailAttachment>();

        mailAttachments.add(mailAttachment);

        mailSenderService.sendEmail(from, replyTo, email, subject, body, mailAttachments);
    }

    private String generateMailAddressAndDisplayName(String address, String displayName) {
        return String.format("%s <%s>", displayName, address);
    }

    private CSVFile createAttachment() {

        return generateCSV();
    }

    private CSVFile generateCSV() {

        LocalDate startDate = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        LocalDate endDate  = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());

        Person person = personService.getPersonByMailAddress("maximilian.radmacher@i-telligence.de").orElseThrow();

        Sort sort =  Sort.by(Sort.Order.asc("person.firstName"));

        PageableSearchQuery pageableSearchQuery = new PageableSearchQuery(PageRequest.of(0, MAX_VALUE, sort), "");

        Page<SickDaysDetailedStatistics> list = sickDaysStatisticsService.getAll(
            person,
            startDate,
            endDate,
            pageableSearchQuery
        );

        List<SickDaysDetailedStatistics> content = list.getContent();

        SickDaysDetailedStatisticsCsvExportService exportService = new SickDaysDetailedStatisticsCsvExportService(messageSource);

        final FilterPeriod period = new FilterPeriod(startDate, endDate);

        final Locale locale = Locale.GERMAN;

        return exportService.generateCSV(period, locale, content );
    }



    class SickDaysDetailedStatisticsCsvExportService implements CsvExportService<SickDaysDetailedStatistics> {

        private final MessageSource messageSource;

        SickDaysDetailedStatisticsCsvExportService(MessageSource messageSource) {
            this.messageSource = messageSource;
        }

        @Override
        public String fileName(FilterPeriod period, Locale locale) {
            final DateTimeFormatter dateTimeFormatter = ofLocalizedDate(SHORT).withLocale(locale);
            return format("%s_%s_%s_%s.csv",
                getTranslation(locale, "action.sicknotes.download.filename").replace(" ", "-"),
                period.startDate().format(dateTimeFormatter).replace("/", "-"),
                period.endDate().format(dateTimeFormatter).replace("/", "-"),
                locale.getLanguage());
        }

        @Override
        public void write(FilterPeriod period, Locale locale, List<SickDaysDetailedStatistics> allDetailedSickNotes, CSVWriter csvWriter) {

            final String[] csvHeader = {
                getTranslation(locale, "person.account.basedata.personnelNumber"),
                getTranslation(locale, "person.data.firstName"),
                getTranslation(locale, "person.data.lastName"),
                getTranslation(locale, "sicknotes.statistics.departments"),
                getTranslation(locale, "sicknotes.statistics.from"),
                getTranslation(locale, "sicknotes.statistics.to"),
                getTranslation(locale, "sicknotes.statistics.length"),
                getTranslation(locale, "sicknotes.statistics.days"),
                getTranslation(locale, "sicknotes.statistics.type"),
                getTranslation(locale, "sicknotes.statistics.certificate.from"),
                getTranslation(locale, "sicknotes.statistics.certificate.to"),
                getTranslation(locale, "sicknotes.statistics.certificate.days")
            };

            final DateTimeFormatter dateTimeFormatter = ofLocalizedDate(MEDIUM).withLocale(locale);
            final DecimalFormat decimalFormat = (DecimalFormat) getInstance(locale);

            csvWriter.writeNext(csvHeader);

            allDetailedSickNotes.forEach(detailedSickNote ->
                detailedSickNote.getSickNotes().forEach(sickNote -> {
                    final String[] sickNoteCsvRow = new String[csvHeader.length];
                    sickNoteCsvRow[0] = detailedSickNote.getPersonalNumber();
                    sickNoteCsvRow[1] = detailedSickNote.getPerson().getFirstName();
                    sickNoteCsvRow[2] = detailedSickNote.getPerson().getLastName();
                    sickNoteCsvRow[3] = String.join(", ", detailedSickNote.getDepartments());
                    sickNoteCsvRow[4] = sickNote.getStartDate().format(dateTimeFormatter);
                    sickNoteCsvRow[5] = sickNote.getEndDate().format(dateTimeFormatter);
                    sickNoteCsvRow[6] = getTranslation(locale, sickNote.getDayLength().name());
                    sickNoteCsvRow[7] = decimalFormat.format(sickNote.getWorkDays());
                    sickNoteCsvRow[8] = getTranslation(locale, sickNote.getSickNoteType().getMessageKey());
                    if (sickNote.isAubPresent()) {
                        sickNoteCsvRow[9] = sickNote.getAubStartDate().format(dateTimeFormatter);
                        sickNoteCsvRow[10] = sickNote.getAubEndDate().format(dateTimeFormatter);
                        sickNoteCsvRow[11] = decimalFormat.format(sickNote.getWorkDaysWithAub());
                    }
                    csvWriter.writeNext(sickNoteCsvRow);
                })
            );
        }

        private String getTranslation(Locale locale, String key, Object... args) {
            return messageSource.getMessage(key, args, locale);
        }
    }

}
