package optn

import groovy.transform.PackageScope
import groovy.transform.CompileStatic as CS

import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.rwtodd.args.*

@CS
@PackageScope
final class Utils {
    static double WDAYS_PER_YEAR = 260.0d

    static LocalDate nextFriday(LocalDate today) {
        today.plusDays((5 - today.dayOfWeek.value + 7) % 7)
    }

    // count weekdays between the two dates, inclusive, with a formula rather than looping
    static long weekdaysBetween(LocalDate earlier, LocalDate later) {
        long numDays = earlier.until(later, ChronoUnit.DAYS) + 1
        long startNum = earlier.dayOfWeek.value
        // Sunday is 0, so we want divisible by zero days...
        double numSundays = Math.ceil((double) (startNum + numDays) / 7.0d) - Math.ceil((double) startNum / 7.0d)
        double numSaturdays = Math.ceil((double) (startNum + numDays + 1) / 7.0d) - Math.ceil((double) (startNum + 1) / 7.0d)
        return numDays - (long) numSaturdays - (long) numSundays
    }

    // help to make the output uniform for the numbers...
    static String fmtField(String f) {
        // decimal field is 12 digits wide, or 13 if we end in a '%', integer field is 9 wide
        int total = 12 + (f.endsWith('%') ? 1 : 0) + ((f.indexOf('.') >= 0) ? 0 : -3)
        f.padLeft(total)
    }
}

import static optn.Utils.*  // just use the util funcs freely

@CS
@PackageScope
class ShortPut {
    static def run(String[] args) {
        // process args...
        def today = LocalDate.now()
        def openDate = new DateParam(['open', 'o'], today, '<Date> The Date the position was opened (defaults to today)')
        def expiryDate = new DateParam(['expiry', 'e'], nextFriday(today), '<Date> The date the position expires (defaults to Friday)')
        def strikePrice = new DoubleParam(['strike', 's'], '<Price> The strike price of the option')
        def salePrice = new DoubleParam(['premium', 'p'], '<Price> The premium from the sale')
        def help = new FlagParam(['help'], 'Displays this help text.')
        Parser p = [openDate, expiryDate, strikePrice, salePrice, help]
        try {
            def extras = p.parse(args)
            if (help.value || !extras.empty) throw new Exception("User asked for help")
            if (strikePrice.value.isNaN() || salePrice.value.isNaN()) throw new Exception("Must give both strike price and sale price!")
            if (expiryDate.value < openDate.value) throw new Exception("Expiry can't be before the open date!")

            // ok, calculate the answers...
            long weekdays = weekdaysBetween(openDate.value, expiryDate.value)
            double multiplier = (salePrice.value - 0.005d) / strikePrice.value + 1.0d
            NumberFormat pctFmt = NumberFormat.percentInstance
            pctFmt.minimumFractionDigits = 2
            pctFmt.maximumFractionDigits = 2
            NumberFormat dblFmt = NumberFormat.numberInstance
            dblFmt.maximumFractionDigits = 2
            dblFmt.minimumFractionDigits = 2

            print("""\
Days in Market:  ${fmtField weekdays.toString()}
Capital:        \$${fmtField dblFmt.format(strikePrice.value * 100d)}
Max Value:      \$${fmtField dblFmt.format(salePrice.value * 100d - 0.5d)}
Pct Gain:        ${fmtField pctFmt.format(multiplier - 1)}
Pct Annualized:  ${fmtField pctFmt.format(multiplier**(WDAYS_PER_YEAR / weekdays) - 1.0d)}
Break Even:     \$${fmtField dblFmt.format(strikePrice.value - salePrice.value)}
""")
        } catch (Exception e) {
            System.err.println(e.message)
            System.err.println("\nUsage: sp [parameters]\n")
            p.printHelpText(System.err)
            System.exit(1)
        }
    }
}

@CS
@PackageScope
class CoveredCall {
    static def run(String[] args) {
        // process args...
        def today = LocalDate.now()
        def openDate = new DateParam(['open', 'o'], today, '<Date> The date the position was opened (defaults to today)')
        def expiryDate = new DateParam(['expiry', 'e'], nextFriday(today), '<Date> The date the position expires (defaults to Friday)')
        def strikePrice = new DoubleParam(['strike', 's'], '<Price> The strike price of the option')
        def salePrice = new DoubleParam(['premium', 'p'], '<Price> The premium from the sale')
        def basis = new DoubleParam(['basis', 'b'], '<Price> The cost basis (defaults to strike price)')
        def help = new FlagParam(['help'], 'Displays this help text.')
        Parser p = [openDate, expiryDate, strikePrice, salePrice, basis, help]
        try {
            def extras = p.parse(args)
            if (help.value || !extras.empty) throw new Exception("User asked for help")
            if (strikePrice.value.isNaN() || salePrice.value.isNaN()) throw new Exception("Must give both strike price and sale price!")
            if (expiryDate.value < openDate.value) throw new Exception("Expiry can't be before the open date!")
            if (basis.value.isNaN()) basis = strikePrice  // basis defaults to whatever the strike price was

            // ok, calculate the answers...
            long weekdays = weekdaysBetween(openDate.value, expiryDate.value)
            double maxGain = (strikePrice.value - basis.value) + (salePrice.value - 0.005d)
            double multiplier = 1.0d + (maxGain / basis.value)
            double lowGain = salePrice.value - 0.005d
            double lowMult = 1.0d + (lowGain / basis.value)
            NumberFormat pctFmt = NumberFormat.percentInstance
            pctFmt.minimumFractionDigits = 2
            pctFmt.maximumFractionDigits = 2
            NumberFormat dblFmt = NumberFormat.numberInstance
            dblFmt.maximumFractionDigits = 2
            dblFmt.minimumFractionDigits = 2

            print("""\
Days in Market:  ${fmtField weekdays.toString()}
Capital:        \$${fmtField dblFmt.format(basis.value * 100d)}
Max Value:      \$${fmtField dblFmt.format(maxGain * 100d)}
Pct Max Gain:    ${fmtField pctFmt.format(multiplier - 1)}
    Annualized:  ${fmtField pctFmt.format(multiplier**(WDAYS_PER_YEAR / weekdays) - 1.0d)}
Low Value:      \$${fmtField dblFmt.format(lowGain * 100d)}
Pct Low Gain:    ${fmtField pctFmt.format(lowMult - 1)}
    Annualized:  ${fmtField pctFmt.format(lowMult**(WDAYS_PER_YEAR / weekdays) - 1.0d)}
""")
        } catch (Exception e) {
            System.err.println(e.message)
            System.err.println("\nUsage: cc [parameters]\n")
            p.printHelpText(System.err)
            System.exit(1)
        }
    }
}

@CS
class App {
    static void usage() {
        System.err.println('''\
Usage: optn <command> [options]
       
Commands:
  cc    calculate the returns on a covered call
  sp    calculate the returns on a short put
  repl  take arguments from stdin in a loop, to avoid the cost of restarting the JVM
  help  print this help message

Use `optn <command> --help` for help on an individual command
''')
        System.exit(1)
    }

    // read all the lines from stdin, parsing each as if it was the command line.
    // This extremely cheap "repl" is better with rlwrap.
    static void runRepl() {
        System.in.withReader { rdr ->
            rdr.eachLine {
                main(it.trim().split(' +'))
                println('\n')
            }
        }
    }

    static void main(String[] args) {
        if (args.length < 1) {
            usage()
        }
        switch (args[0]) {
            case 'cc' -> CoveredCall.run(args.drop 1)
            case 'sp' -> ShortPut.run(args.drop 1)
            case 'repl' -> runRepl()
            default -> usage()
        }
    }
}
