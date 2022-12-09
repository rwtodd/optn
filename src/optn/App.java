package optn;

import java.util.List;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.rwtodd.args.*;
import static optn.Utils.*;

final class Utils {
    static double WDAYS_PER_YEAR = 260.0;

    static LocalDate nextFriday(LocalDate today) {
        return today.plusDays((5 - today.getDayOfWeek().getValue() + 7) % 7);
    }

    // count weekdays between the two dates, inclusive, with a formula rather than looping
    static long weekdaysBetween(LocalDate earlier, LocalDate later) {
        long numDays = earlier.until(later, ChronoUnit.DAYS) + 1;
        long startNum = earlier.getDayOfWeek().getValue();
        // Sunday is 0, so we want divisible by zero days...
        double numSundays = Math.ceil((double) (startNum + numDays) / 7.0) - Math.ceil((double) startNum / 7.0);
        double numSaturdays = Math.ceil((double) (startNum + numDays + 1) / 7.0) - Math.ceil((double) (startNum + 1) / 7.0);
        return numDays - (long) numSaturdays - (long) numSundays;
    }
}

class ShortPut {
    static void run(String[] args) {
        // process args...
        final var today = LocalDate.now();
        final var openDate = new DateParam(List.of("open","o"), today, "<Date> The Date the position was opened (defaults to today)");
        final var expiryDate = new DateParam(List.of("expiry","e"), nextFriday(today), "<Date> The date the position expires (defaults to Friday)");
        final var strikePrice = new DoubleParam(List.of("strike","s"), "<Price> The strike price of the option");
        final var salePrice = new DoubleParam(List.of("premium","p"), "<Price> The premium from the sale");
        final var help = new FlagParam(List.of("help"), "Displays this help text.");
        final var p = new Parser(openDate, expiryDate, strikePrice, salePrice, help);
        try {
            final var extras = p.parse(args, 1);
            if (help.getValue() || !extras.isEmpty()) throw new Exception("User asked for help");
            if (strikePrice.getValue().isNaN() || salePrice.getValue().isNaN()) throw new Exception("Must give both strike price and sale price!");
            if (expiryDate.getValue().isBefore(openDate.getValue())) throw new Exception("Expiry can't be before the open date!");

            // ok, calculate the answers...
            long weekdays = weekdaysBetween(openDate.getValue(), expiryDate.getValue());
            double multiplier = (salePrice.getValue() - 0.005) / strikePrice.getValue() + 1.0;
            NumberFormat pctFmt = NumberFormat.getPercentInstance();
            pctFmt.setMinimumFractionDigits(2);
            pctFmt.setMaximumFractionDigits(2);
            NumberFormat dblFmt = NumberFormat.getNumberInstance();
            dblFmt.setMinimumFractionDigits(2);
            dblFmt.setMaximumFractionDigits(2);

            System.out.printf("Days in Market: %13s\n", dblFmt.format(weekdays));
            System.out.printf("Capital:       $%13s\n", dblFmt.format(strikePrice.getValue() * 100.0));
            System.out.printf("Max Value:     $%13s\n", dblFmt.format(salePrice.getValue() * 100.0 - 0.5));
            System.out.printf("Pct Gain:       %14s\n", pctFmt.format(multiplier - 1.0));
            System.out.printf("Pct Annualized: %14s\n", pctFmt.format(Math.pow(multiplier, WDAYS_PER_YEAR / weekdays) - 1.0));
            System.out.printf("Break Even:    $%13s\n", dblFmt.format(strikePrice.getValue() - salePrice.getValue()));
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.err.println("\nUsage: sp [parameters]\n");
            p.printHelpText(System.err);
            System.exit(1);
        }
    }
}

 class CoveredCall {
     static void run(String[] args) {
         // process args...
         final var today = LocalDate.now();
         final var openDate = new DateParam(List.of("open","o"), today, "<Date> The Date the position was opened (defaults to today)");
         final var expiryDate = new DateParam(List.of("expiry","e"), nextFriday(today), "<Date> The date the position expires (defaults to Friday)");
         final var strikePrice = new DoubleParam(List.of("strike","s"), "<Price> The strike price of the option");
         final var salePrice = new DoubleParam(List.of("premium","p"), "<Price> The premium from the sale");
         var basis = new DoubleParam(List.of("basis","b"), "<Price> The cost basis (defaults to the strike price");
         final var help = new FlagParam(List.of("help"), "Displays this help text.");
         final var p = new Parser(openDate, expiryDate, strikePrice, salePrice, basis, help);
         try {
             final var extras = p.parse(args, 1);
             if (help.getValue() || !extras.isEmpty()) throw new Exception("User asked for help");
             if (strikePrice.getValue().isNaN() || salePrice.getValue().isNaN()) throw new Exception("Must give both strike price and sale price!");
             if (expiryDate.getValue().isBefore(openDate.getValue())) throw new Exception("Expiry can't be before the open date!");
             if (basis.getValue().isNaN()) basis = strikePrice;  // basis defaults to whatever the strike price was

             // ok, calculate the answers...
             long weekdays = weekdaysBetween(openDate.getValue(), expiryDate.getValue());
             double maxGain = (strikePrice.getValue() - basis.getValue()) + (salePrice.getValue() - 0.005);
             double multiplier = 1.0 + (maxGain / basis.getValue());
             double lowGain = salePrice.getValue() - 0.005;
             double lowMult = 1.0 + (lowGain / basis.getValue());
             NumberFormat pctFmt = NumberFormat.getPercentInstance();
             pctFmt.setMinimumFractionDigits(2);
             pctFmt.setMaximumFractionDigits(2);
             NumberFormat dblFmt = NumberFormat.getNumberInstance();
             dblFmt.setMinimumFractionDigits(2);
             dblFmt.setMaximumFractionDigits(2);

             System.out.printf("Days in Market: %13s\n", dblFmt.format(weekdays));
             System.out.printf("Capital:       $%13s\n", dblFmt.format(basis.getValue() * 100.0));
             System.out.printf("Max Value:     $%13s\n", dblFmt.format(maxGain * 100.0));
             System.out.printf("Pct Max Gain:   %14s\n", pctFmt.format(multiplier - 1.0));
             System.out.printf("    Annualized: %14s\n", pctFmt.format(Math.pow(multiplier, WDAYS_PER_YEAR / weekdays) - 1.0));
             System.out.printf("Low Value:     $%13s\n", dblFmt.format(lowGain * 100.0));
             System.out.printf("Pct Low Gain:   %14s\n", pctFmt.format(lowMult - 1.0));
             System.out.printf("    Annualized: %14s\n", pctFmt.format(Math.pow(lowMult, WDAYS_PER_YEAR / weekdays) - 1.0));
         } catch (Exception e) {
             System.err.println(e.getMessage());
             System.err.println("\nUsage: cc [parameters]\n");
             p.printHelpText(System.err);
             System.exit(1);
         }
     }
 }

class App {
    static void usage() {
        System.err.println("""
Usage: optn <command> [options]
       
Commands:
  cc    calculate the returns on a covered call
  sp    calculate the returns on a short put
  help  print this help message

Use `optn <command> --help` for help on an individual command
""");
    System.exit(1);
    }

    public static void main(String[] args) {
        // with no args, just print usage and error exit
        if (args.length == 0) {
            usage();
        }

        switch(args[0]) {
          case "cc" -> CoveredCall.run(args);
          case "sp" -> ShortPut.run(args);
          default -> usage();
        }
    }
}
