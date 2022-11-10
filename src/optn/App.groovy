package optn

import groovy.transform.PackageScope
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



@PackageScope class ShortPut {
  def run(args) {
    println("Ima short put")
  }
}

@PackageScope class CoveredCall {
  def run(args) {
    println("Ima covered call")
  }
}


class App {
  static void usage() {
    System.err.println('''Usage: optn <command> [options]
       
Commands:
  cc    calculate the returns on a covered call
  sp    calculate the returns on a short put
  help  print this help message

Use `optn <command> --help` for help on an individual command
''')
    System.exit(1)
  }

  static void main(String[] args) {
    if(args.length < 1) { usage() }
    switch(args[0]) {
    case "cc" -> new CoveredCall().run(args.drop 1)
    case "sp" -> new ShortPut().run(args.drop 1)
    default -> usage()
    }
  }
}
