package optn

import groovy.transform.PackageScope

import java.text.NumberFormat
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.rwtodd.args.*

/* define a date argument that's easy to use from the command-line */
@PackageScope final class DateParam extends BasicOneArgParam<LocalDate> {
  private LocalDate today = LocalDate.now()

  DateParam(names, dflt, help) { super(names, dflt, help); }
  protected LocalDate convertArg(String param, String arg) {
    final var parts = arg.split('-').collect { Integer.valueOf(it) }
    return switch(parts.size()) {
    case 3 -> LocalDate.of(*parts)
    case 2 -> LocalDate.of(today.year, *parts)
    case 1 -> LocalDate.of(today.year, today.monthValue, parts[0])
    default -> throw new IllegalArgumentException("not a yyyy-mm-dd date!") 
    }
  }
}


@PackageScope final class Utils {
  static int WDAYS_PER_YEAR = 260

  static def nextFriday(LocalDate today) {
    today.plusDays(5 - today.dayOfWeek.value)
  }

  static long weekdaysBetween(LocalDate earlier, LocalDate later) {
    long numDays = earlier.until(later, ChronoUnit.DAYS) + 1
    long startNum = earlier.dayOfWeek.value
    // Sunday is 0, so we want divisible by zero days...
    double numSundays = Math.ceil((double)(startNum + numDays) / 7.0d) - Math.ceil((double)startNum / 7.0d)
    double numSaturdays = Math.ceil((double)(startNum + numDays + 1) / 7.0d) - Math.ceil((double)(startNum + 1) / 7.0d)
    return numDays - (long)numSaturdays - (long)numSundays
  }
}

import static optn.Utils.*; // just use the util funcs freely

@PackageScope class ShortPut {
  def run(String[] args) {
    // process args...
    def today = LocalDate.now()
    def openDate = new DateParam(['open','o'],today,'The Date the position was opened (defaults to today)')
    def expiryDate = new DateParam(['expiry','e'], nextFriday(today), 'The date the position expires (defaults to Friday)')
    def strikePrice = new DoubleParam(['strike','s'],'the strike price of the option')
    def salePrice = new DoubleParam(['premium','p'],'the premium from the sale')
    def help = new FlagParam(['help'],'Displays this help text.')
    Parser p = [openDate,expiryDate, strikePrice, salePrice, help]
    try {
      def extras = p.parse(args)
      if(help.value || !extras.empty) throw new Exception("User asked for help")
      if(strikePrice.value.isNaN() || salePrice.value.isNaN()) throw new Exception("Must give both strike price and sale price!")

      // ok, calculate the answers...
      long weekdays = weekdaysBetween(openDate.value, expiryDate.value)
      double multiplier = (salePrice.value - 0.01d) / strikePrice.value + 1.0d
      NumberFormat pctFmt = NumberFormat.percentInstance
      pctFmt.maximumFractionDigits = 1
      NumberFormat dblFmt = NumberFormat.numberInstance
      dblFmt.maximumFractionDigits = 2
      dblFmt.minimumFractionDigits = 2

      print("""
Days in Market:  ${weekdays}
Capital:        \$${dblFmt.format(strikePrice.value * 100d)}
Max Value:      \$${dblFmt.format(salePrice.value * 100d - 0.5d)}
Pct Gain:        ${pctFmt.format(multiplier - 1)}
Pct Annualized:  ${pctFmt.format(multiplier ** ((double)WDAYS_PER_YEAR / (double)weekdays) - 1)}
Break Even:     \$${dblFmt.format(strikePrice.value - salePrice.value)}
""")
    } catch(Exception e) {
      System.err.println(e.message)
      System.err.println("\nUsage: sp [parameters]\n")
      p.printHelpText(System.err)
      System.exit(1)
    }
  }
}

@PackageScope class CoveredCall {
  def run(String [] args) {
    // process args...
    def today = LocalDate.now()
    def openDate = new DateParam(['open','o'],today,'The Date the position was opened (defaults to today)')
    def expiryDate = new DateParam(['expiry','e'], nextFriday(today), 'The date the position expires (defaults to Friday)')
    def strikePrice = new DoubleParam(['strike','s'],'the strike price of the option')
    def salePrice = new DoubleParam(['premium','p'], 'the premium from the sale')
    def basis = new DoubleParam(['basis','b'], 'the cost basis (defaults to strike price)')
    def help = new FlagParam(['help'],'Displays this help text.')
    Parser p = [openDate,expiryDate, strikePrice, salePrice, basis, help]
    try {
      def extras = p.parse(args)
      if(help.value || !extras.empty) throw new Exception("User asked for help")
      if(strikePrice.value.isNaN() || salePrice.value.isNaN()) throw new Exception("Must give both strike price and sale price!")
      if(basis.value.isNaN()) basis = strikePrice  // basis defaults to whatever the strike price was

      // ok, calculate the answers...
      long weekdays = weekdaysBetween(openDate.value, expiryDate.value)
      double maxGain = (strikePrice.value - basis.value) + (salePrice.value - 0.005d)
      double multiplier = 1.0d + (maxGain / basis.value)
      double lowGain = salePrice.value - 0.005d
      double lowMult = 1.0d + (lowGain / basis.value)
      NumberFormat pctFmt = NumberFormat.percentInstance
      pctFmt.maximumFractionDigits = 1
      NumberFormat dblFmt = NumberFormat.numberInstance
      dblFmt.maximumFractionDigits = 2
      dblFmt.minimumFractionDigits = 2

      print("""
Days in Market:  ${weekdays}
Capital:        \$${dblFmt.format(basis.value * 100d)}
Max Value:      \$${dblFmt.format(maxGain * 100d)}
Pct Max Gain:    ${pctFmt.format(multiplier - 1)}
    Annualized:  ${pctFmt.format(multiplier ** ((double)WDAYS_PER_YEAR / (double)weekdays) - 1)}
Low Value:      \$${dblFmt.format(lowGain * 100d)}
Pct Low Gain:    ${pctFmt.format(lowMult - 1)}
    Annualized:  ${pctFmt.format(lowMult ** ((double) WDAYS_PER_YEAR / (double) weekdays) - 1)}
""")
    } catch(Exception e) {
      System.err.println(e.message)
      System.err.println("\nUsage: cc [parameters]\n")
      p.printHelpText(System.err)
      System.exit(1)
    }
  }
}


class App {
  static void usage() {
    System.err.println('''Usage: optn <command> [options]
       
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
    if(args.length < 1) { usage() }
    switch(args[0]) {
    case "cc" -> new CoveredCall().run(args.drop 1)
    case "sp" -> new ShortPut().run(args.drop 1)
    case "repl" -> runRepl()
    default -> usage()
    }
  }
}
