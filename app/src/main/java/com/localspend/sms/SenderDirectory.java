package com.localspend.sms;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises SMS sender IDs used by banks and card issuers.
 *
 * Banks send transaction alerts from registered alphanumeric sender IDs ("CommBank", "NAB",
 * "VM-HDFCBK"), not from ordinary phone numbers. The list covers Indian public sector, private,
 * small finance, payments, foreign and major co-operative banks, and Australian banks, mutual
 * banks and credit unions. Senders not on the list are still recognised when they look like a
 * bank: an Indian DLT header ending in BNK/BANK/BK/CRD/SFB, or any sender name containing "BANK". Messages from these IDs are trusted; anything
 * else is held for the user to review. The user can trust or block any other sender, which
 * always takes priority over this list.
 */
public final class SenderDirectory {

    /**
     * Indian banks: DLT sender headers (the six characters after the operator prefix, as in
     * "AX-ICICIT-S"). Grouped by type of bank; several banks use more than one header.
     */
    private static final String[] INDIA = {
            // Public sector banks
            "SBIINB", "SBIPSG", "SBIUPI", "SBMSMS", "CBSSBI", "ATMSBI", "SBICRD", "SBYONO", "SBIBNK",
            "SBIBK", "PNBSMS", "PNBBNK", "PUNBNK", "BOBTXN", "BOBSMS", "BOBCRD", "BARODA", "BOBFIN",
            "CANBNK", "CNRBNK", "CANBK", "UNIONB", "UBINBK", "UBIBNK", "BOIIND", "BOISMS", "BOIBNK",
            "INDBNK", "INDIBK", "INBANK", "CENTBK", "CBOIBK", "CENTRL", "IOBCHN", "IOBBNK", "IOBSMS",
            "UCOBNK", "UCOBK", "MAHABK", "BOMBNK", "PSBANK", "PSBSMS", "IDBIBK", "IDBIBN",
            // Private sector banks
            "HDFCBK", "HDFCBN", "HDFCCC", "HDFCBA", "ICICIB", "ICICIT", "ICICIO", "ICICIC", "ICICIS",
            "AXISBK", "AXISCR", "AXISMR", "AXISBN", "KOTAKB", "KOTAKM", "KOTAKC", "KMBANK",
            "YESBNK", "YESBK", "INDUSB", "INDUSL", "IDFCFB", "IDFCBK", "RBLBNK", "RBLCRD", "RBLBK",
            "FEDBNK", "FEDFIB", "FEDBK", "SIBSMS", "SIBANK", "KVBANK", "KVBSMS", "KTKBNK", "KBLBNK",
            "CUBANK", "CUBLTD", "TMBANK", "TMBLTD", "DCBBNK", "DCBANK", "CSBBNK", "CSBANK", "JKBANK",
            "JKBSMS", "DHANBK", "NAINIB", "BANDHN", "BDNSMS", "BANDBK",
            // Small finance banks
            "AUBANK", "AUBNKL", "AUSFBL", "EQUTAS", "ESFBNK", "UJJIVN", "UJJSFB", "SURYDY", "SSFBNK",
            "JANABK", "JSFBNK", "ESAFBK", "UTKSFB", "FINCAR", "SHIVLK", "UNITYB", "NESFBL", "CAPSFB",
            // Payments banks
            "PAYTMB", "AIRBNK", "ARTLPB", "JIOPBK", "FINOBK", "FINOPB", "IPPBNK", "NSDLPB",
            // Foreign banks in India
            "SCBANK", "STANCB", "DBSBNK", "DIGIBK", "CITIBK", "CITIBN", "HSBCIN", "HSBCBK", "AMEXIN",
            "DEUTBK", "BARCLY",
            // Major co-operative banks
            "SARSWT", "COSBNK", "SVCBNK", "TJSBBK", "ABHYDY", "APNABK", "NKGSBK", "KALUPR", "BHARTB",
            // Credit cards
            "ONECRD", "AMEXCC"};

    /** Australian banks, mutual banks and credit unions, as they appear as SMS sender names. */
    private static final String[] AUSTRALIA = {
            // Major banks and their brands
            "COMMBANK", "CBA", "NETBANK", "NAB", "WESTPAC", "ANZ", "ANZBANK", "STGEORGE",
            "STGEORGEBANK", "BANKSA", "BANKOFMELB", "BANKMELB", "BANKWEST", "UBANK", "AMEX",
            "AMERICANEXP",
            // Other banks
            "ING", "INGBANK", "INGAUSTRALIA", "MACQUARIE", "MACQUARIEBANK", "SUNCORP", "SUNCORPBANK",
            "BOQ", "BOQBANK", "BENDIGO", "BENDIGOBANK", "ADELAIDEBANK", "AMPBANK", "MEBANK", "MEGO",
            "HSBC", "CITI", "CITIBANK", "RABOBANK", "VIRGINMONEY", "JUDOBANK", "UPBANK", "BANKOFSYDNEY",
            "BANKOFCHINA", "ICBCSYDNEY", "MYSTATE", "AUSWIDE", "RACQBANK", "BANKFIRST", "BANKVIC",
            // Mutual banks and credit unions
            "GREATSOUTHERN", "GSB", "HERITAGEBANK", "PEOPLESCHOICE", "NEWCASTLEPM",
            "NEWCASTLEPERM", "BEYONDBANK", "BANKAUST", "BANKAUSTRALIA", "TEACHERSMB", "TEACHERSMUTUAL",
            "TMBANK", "UNIBANK", "HUMEBANK", "IMB", "IMBBANK", "GREATERBANK", "PNBANK", "POLICEBANK",
            "DEFENCEBANK", "BCU", "AUSMILBANK", "REGIONALAUS", "CUA", "QBANK", "QUDOS", "QUDOSBANK",
            "UNITYBANK", "GATEWAYBANK", "COMMUNITYFIRST", "SUMMERLAND", "CREDITUNIONSA",
            "GCMUTUAL", "HORIZONBANK", "NORTHERNINLAND", "FIRSTOPTION", "BDCU", "WAWCU",
            "SOUTHWESTCU", "LCU", "CCU", "MOVEBANK", "QPCU", "BANKOFUS", "ECUAUSTRALIA",
            // Card issuers and payment services
            "LATITUDE", "COLESCARD", "COLESFIN", "PAYPAL", "CITICARDS"};

    /** Normalised sender IDs of known banks (see {@link #normalise}). */
    private static final Set<String> KNOWN = new HashSet<>();

    static {
        KNOWN.addAll(Arrays.asList(INDIA));
        KNOWN.addAll(Arrays.asList(AUSTRALIA));
    }

    /** Indian DLT header: two-letter operator prefix, six-character header, optional suffix. */
    private static final Pattern INDIAN_HEADER =
            Pattern.compile("^[A-Z]{2}-([A-Z0-9]{6})(?:-[A-Z])?$");
    /** Header endings banks use for their own IDs (HDFCBK, CANBNK, AUSFBL, SBICRD...). */
    private static final Pattern BANK_LIKE_HEADER =
            Pattern.compile(".*(?:BANK|BNK|BK|SFB|SFBL|CRD|CARD)$|.*BANK.*");

    private SenderDirectory() {
    }

    /**
     * Sender in a comparable form: upper case, letters and digits only, and Indian operator
     * prefixes ("VM-", "AD-", "JD-") and category suffixes ("-S", "-T") removed.
     * "VM-HDFCBK-S" and "hdfcbk" both become "HDFCBK"; "+61 402 377 715" becomes "61402377715".
     */
    public static String normalise(String sender) {
        if (sender == null) {
            return "";
        }
        String s = sender.trim().toUpperCase(Locale.ROOT);
        s = s.replaceFirst("^[A-Z]{2}-(?=[A-Z0-9]{3,})", "");    // operator prefix
        s = s.replaceFirst("-[A-Z]$", "");                       // category suffix
        return s.replaceAll("[^A-Z0-9]", "");
    }

    /** True if the sender is a phone number rather than a registered sender ID. */
    public static boolean isPhoneNumber(String sender) {
        String n = normalise(sender);
        return !n.isEmpty() && n.matches("\\d{8,}");
    }

    /** True if the sender ID belongs to a bank or card issuer in the built-in list. */
    public static boolean isKnownBank(String sender) {
        String n = normalise(sender);
        if (n.isEmpty() || isPhoneNumber(sender)) {
            return false;
        }
        if (KNOWN.contains(n)) {
            return true;
        }
        // Indian banks not in the list still use recognisable headers ("XY-ABCBNK-S").
        Matcher indian = INDIAN_HEADER.matcher(sender.trim().toUpperCase(Locale.ROOT));
        if (indian.matches() && BANK_LIKE_HEADER.matcher(indian.group(1)).matches()) {
            return true;
        }
        // Any named sender with "bank" in it ("HumeBank", "BankVic").
        if (n.contains("BANK")) {
            return true;
        }
        // Allow small variations such as "COMMBANKAU" or "NABALERTS".
        for (String known : KNOWN) {
            if (known.length() >= 4 && n.startsWith(known)) {
                return true;
            }
        }
        return false;
    }

    /** The built-in list, for display. */
    public static List<String> knownSenders() {
        String[] all = KNOWN.toArray(new String[0]);
        Arrays.sort(all);
        return Collections.unmodifiableList(Arrays.asList(all));
    }
}
