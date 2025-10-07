package org.synyx.urlaubsverwaltung.mail;

import com.opencsv.CSVWriter;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import net.fortuna.ical4j.validate.ValidationException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.synyx.urlaubsverwaltung.csv.CSVFile;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.search.PageableSearchQuery;
import org.synyx.urlaubsverwaltung.sicknote.sickdays.SickDaysDetailedStatistics;
import org.synyx.urlaubsverwaltung.sicknote.sickdays.SickDaysStatisticsService;
import org.synyx.urlaubsverwaltung.web.FilterPeriod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

import static com.opencsv.ICSVWriter.*;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;
import static java.lang.invoke.MethodHandles.lookup;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.text.NumberFormat.getInstance;
import static java.time.format.DateTimeFormatter.ofLocalizedDate;
import static java.time.format.FormatStyle.MEDIUM;
import static java.time.format.FormatStyle.SHORT;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class MonthlyEmailScheduler {

    private static final Logger LOG = getLogger(lookup().lookupClass());


    private final MailProperties mailProperties;
    private final MessageSource messageSource;
    private final PersonService personService;
    private final SickDaysStatisticsService sickDaysStatisticsService;

    private final JavaMailSender mailSender;

    @Autowired
    public MonthlyEmailScheduler(MailProperties mailProperties, MessageSource messageSource, PersonService personService, SickDaysStatisticsService sickDaysStatisticsService, JavaMailSender mailSender) {
        this.mailProperties = mailProperties;
        this.messageSource = messageSource;
        this.personService = personService;
        this.sickDaysStatisticsService = sickDaysStatisticsService;
        this.mailSender = mailSender;
    }


    // @Scheduled(cron = "0 0 8 1 * *")
    // Einmal monatlich am 10ten Tag um 8 Uhr

    /**
     *
     * 0 Sekunde
     * 0 Minute
     * 8 Stunde
     * 10 Tag des Monats
     * * Monat (alle)
     * * Wochentag (egal)
     */
    @Scheduled(cron = "0 0 8 10 * *")
    public void sendMonthlyEmail() {

        String from = generateMailAddressAndDisplayName(mailProperties.getFrom(), mailProperties.getFromDisplayName());
        String replyTo = generateMailAddressAndDisplayName(mailProperties.getFrom(), mailProperties.getFromDisplayName());
        String email = "buchhaltung@i-telligence.de";
        String subject = "Monatliche E-Mail";
        String body = "Dies ist eine automatisch generierte monatliche E-Mail.";

        // List<MailAttachment> mailAttachments = Arrays.asList();

        CSVFile csv = createAttachment();
        ByteArrayResource resource = csv.resource();

        final MimeMessage mimeMessage = mailSender.createMimeMessage();

        try {
            final MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            helper.setFrom(from);
            helper.setReplyTo(replyTo);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(body);

            helper.addAttachment("sickdays_statistics.csv", new ByteArrayDataSource(resource.getByteArray(), "text/csv; charset=UTF-8"));

        } catch (MessagingException e) {
            LOG.error("Sending email to {} failed", email, e);
        }

        mailSender.send(mimeMessage);
    }

    private String generateMailAddressAndDisplayName(String address, String displayName) {
        return String.format("%s <%s>", displayName, address);
    }

    private CSVFile createAttachment() {

        return generateCSV();
    }

    private CSVFile generateCSV() {


        LocalDate startDate = LocalDate.now()
            .minusMonths(1)
            .with(TemporalAdjusters.firstDayOfMonth());

        LocalDate endDate = LocalDate.now()
            .minusMonths(1)
            .with(TemporalAdjusters.lastDayOfMonth());

        Person person = personService.getPersonByMailAddress("maxime.ridzewski@i-telligence.de").orElseThrow();

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
                getTranslation(locale, "person.data.firstName"),
                getTranslation(locale, "person.data.lastName"),
                getTranslation(locale, "sicknotes.statistics.from"),
                getTranslation(locale, "sicknotes.statistics.to"),
                getTranslation(locale, "sicknotes.statistics.length"),
                getTranslation(locale, "sicknotes.statistics.days"),
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

                    sickNoteCsvRow[0] = detailedSickNote.getPerson().getFirstName();
                    sickNoteCsvRow[1] = detailedSickNote.getPerson().getLastName();
                    sickNoteCsvRow[2] = sickNote.getStartDate().format(dateTimeFormatter);
                    sickNoteCsvRow[3] = sickNote.getEndDate().format(dateTimeFormatter);
                    sickNoteCsvRow[4] = getTranslation(locale, sickNote.getDayLength().name());
                    sickNoteCsvRow[5] = decimalFormat.format(sickNote.getWorkDays());
                    if (sickNote.isAubPresent()) {
                        sickNoteCsvRow[6] = sickNote.getAubStartDate().format(dateTimeFormatter);
                        sickNoteCsvRow[7] = sickNote.getAubEndDate().format(dateTimeFormatter);
                        sickNoteCsvRow[8] = decimalFormat.format(sickNote.getWorkDaysWithAub());
                    }
                    csvWriter.writeNext(sickNoteCsvRow);
                })
            );
        }

        private String getTranslation(Locale locale, String key, Object... args) {
            return messageSource.getMessage(key, args, locale);
        }
    }

    public interface CsvExportService<T> {

        /**
         * Writes the data and other information from the filter period into the csv writer
         *
         * @param period    to add period to csv
         * @param locale    for i18n (messages and number formats)
         * @param data      are the main information for the csv
         * @param csvWriter to write data that will be used to create the ByteArrayResource
         */
        void write(FilterPeriod period, Locale locale, List<T> data, CSVWriter csvWriter);

        /**
         * Contains the algorithm to create a unique filename
         *
         * @param period can be used for a unique filename
         * @param locale for i18n (messages and number formats)
         * @return the filename to be used for this kind of files
         */
        String fileName(FilterPeriod period, Locale locale);

        /**
         * Main method of this interface to retrieve the {@link CSVFile} containing the filename and resource.
         *
         * @param period will be used to create the content of the csv file
         * @param data   will be used to create the content of the csv file
         * @return a {@link CSVFile} containing the filename and resource
         */
        default CSVFile generateCSV(FilterPeriod period, Locale locale, List<T> data) {
            return new CSVFile(fileName(period, locale), resource(period, locale, data));
        }

        /**
         * Method to override the utf8 bom that is used at the start of the csv.
         *
         * @return a byte array with the bom
         */
        default byte[] bom() {
            return new byte[]{(byte) 239, (byte) 187, (byte) 191};
        }

        /**
         * Method to override the separator that is used to separate the column in a row
         *
         * @return a separator to separate columns of rows
         */
        default char separator() {
            return ';';
        }


        /**
         * Helper method to create a ByteArrayResource from the filter period and the provided data.
         *
         * @param period to create content
         * @param data   to create content
         * @return {@link ByteArrayResource} based on the filter period and data
         */
        default ByteArrayResource resource(FilterPeriod period, Locale locale, List<T> data) {
            final ByteArrayResource byteArrayResource;

            try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                byteArrayOutputStream.write(bom());

                try (final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream, UTF_8)) {
                    outputStreamWriter.write('\ufeff'); // write bom for excel
                    try (final CSVWriter csvWriter = new CSVWriter(outputStreamWriter, separator(), NO_QUOTE_CHARACTER, DEFAULT_QUOTE_CHARACTER, DEFAULT_LINE_END)) {
                        write(period, locale, data, csvWriter);
                    }
                }
                byteArrayResource = new ByteArrayResource(byteArrayOutputStream.toByteArray());
                return byteArrayResource;
            } catch (ValidationException | IOException e) {
                throw new UncheckedIOException(new IOException("Unable to write csv data", e));
            }
        }
    }


}
