import ArgParser
import Foundation

/**
 A proxy for Dates that is `LosslessStringConvertible` for use with `ArgParser`
 */
struct DateArg : LosslessStringConvertible {
    let date : Date
    init?(_ description: String) {
        let calendar = Calendar(identifier: .gregorian)
        let parts = description.split(separator: "-").map { Int($0) }
        guard !parts.contains(nil) else { return nil }
        
        var dc = calendar.dateComponents([.year,.month,.day], from: Date())
        switch parts.count {
        case 3:
            dc.year = parts[0]
            dc.month = parts[1]
            dc.day = parts[2]
        case 2:
            dc.month = parts[0]
            dc.day = parts[1]
        case 1:
            dc.day = parts[0]
        default:
            return nil
        }
        guard let date = calendar.date(from: dc) else { return nil }
        self.date = date
    }

    init(_ d: Date) {
        date = d
    }
    
    var description: String { date.description }
}


@main
public struct OptnCommand {
    let cal: Calendar
    let daysInYear: Int // weekdays per year
    let today: Date  // start of day for today
    let friday: Date // start of day for the nearest friday
    
    init() {
        cal = Calendar(identifier: .gregorian)
        daysInYear = 260 // could calculate, but why bother...
        today = cal.startOfDay(for: Date())
        let daysTilFriday = (13 - cal.component(.weekday, from: today)) % 7
        friday = cal.date(byAdding: .day, value: daysTilFriday, to: today)!
    }

    private func weekdaysBetween(_ first: Date, and second: Date, inclusive: Bool = true) -> Int {
        let numDays = cal.dateComponents([.day], from: first, to: second).day! + (inclusive ? 1 : 0)
        let startNum = cal.dateComponents([.weekday], from: first).weekday! - 1
        // Sunday is 0, so we want divisible by zero days...
        let numSundays : Double = ceil(Double(startNum + numDays) / 7.0) - ceil(Double(startNum) / 7.0)
        let numSaturdays : Double  = ceil(Double(startNum + numDays + 1) / 7.0) - ceil(Double(startNum + 1) / 7.0)
        return numDays - Int(numSaturdays) - Int(numSundays)
    }
    
    /**
     Display help text for the shortPut command and exit the process.
     
     - Parameter params: the `ArgParser` which holds the param list
     - returns: Never
     */
    private func spUsage(params: ArgParser) -> Never {
        print("Usage: optn sp [parameters]\n")
        print(params.argumentHelpText())
        exit(EXIT_FAILURE)
    }
    
    /**
     Calculate the ROI and Break-Even of a potential short put options play.
     */
    private func shortPut(args: ArraySlice<String>) {
        let sellDate = BasicParam(names: ["open","o"], initial: DateArg(today), help: "date the position was opened (defaults to today)")
        let expiryDate = BasicParam(names: ["expiry","e"], initial: DateArg(friday), help: "the expiry date (defaults to this Friday)")
        let strikePrice = BasicParam(names: ["strike","s"], initial: Double.infinity, help: "the strike price")
        let salePrice = BasicParam(names: ["premium","p"], initial: Double.infinity, help: "the sale price")
        let help = FlagParam(names: ["help","h"], help: "displays help")
        let ap = ArgParser(sellDate,expiryDate,strikePrice,salePrice,help)
        do {
            let extras = try ap.parseArgs(args)
            if help.value || strikePrice.value.isInfinite || salePrice.value.isInfinite || !extras.isEmpty {
                spUsage(params: ap)
            }
            
            let weekdays = weekdaysBetween(sellDate.value.date, and: expiryDate.value.date)
            let multiplier = (salePrice.value - 0.01) / strikePrice.value + 1.0
            
            let nf = NumberFormatter()
            nf.numberStyle = .decimal
            nf.minimumFractionDigits = 2
            nf.maximumFractionDigits = 2
            nf.formatWidth = 12
            print("""
                  Days in Market:  \(weekdays)
                  Capital:        $\(nf.string(from: NSNumber(value: strikePrice.value * 100))!)
                  Max Value:      $\(nf.string(from: NSNumber(value: salePrice.value * 100 - 1))!)
                  Pct Gain:        \(nf.string(from: NSNumber(value: 100.0 * (multiplier - 1)))!)%
                  Pct Annualized:  \(nf.string(from: NSNumber(value: 100.0 * (pow(multiplier, Double(daysInYear) / Double(weekdays)) - 1)))!)%
                  Break Even:     $\(nf.string(from: NSNumber(value: strikePrice.value - salePrice.value))!)
                  """)
        } catch ArgumentErrors.invalidArgument(let desc) {
            print("** Error: \(desc)\n\n")
            spUsage(params: ap)
        }
        catch {
            spUsage(params: ap)
        }
    }
    
    /**
     Display help text for the covCall command and exit the process.
     
     - Parameter params: the `ArgParser` which holds the param list
     - returns: Never
     */
    private func ccUsage(params: ArgParser) -> Never {
        print("Usage: optn cc [parameters]\n")
        print(params.argumentHelpText())
        exit(EXIT_FAILURE)
    }
    
    /**
     Calculate the ROI of a potential covered-call options play.
     */
    private func covCall(args: ArraySlice<String>) {
        let sellDate = BasicParam(names: ["open","o"], initial: DateArg(today), help: "date the position was opened (defaults to today)")
        let expiryDate = BasicParam(names: ["expiry","e"], initial: DateArg(friday), help: "the expiry date (defaults to this Friday)")
        let strikePrice = BasicParam(names: ["strike","s"], initial: Double.infinity, help: "the strike price")
        let salePrice = BasicParam(names: ["premium","p"], initial: Double.infinity, help: "the sale price")
        var basis = BasicParam(names: ["basis","b"], initial: Double.infinity, help: "the cost basis (defaults to strike price)")
        let help = FlagParam(names: ["help","h"], help: "displays help")
        let ap = ArgParser(sellDate,expiryDate,strikePrice,salePrice,basis,help)
        do {
            let extras = try ap.parseArgs(args)
            if help.value || strikePrice.value.isInfinite || salePrice.value.isInfinite || !extras.isEmpty {
                ccUsage(params: ap)
            }
            if basis.value.isInfinite {
                // if the basis wasn't given, then default to the strike price
                basis = strikePrice
            }
            let weekdays = weekdaysBetween(sellDate.value.date, and: expiryDate.value.date)
            let maxGain = (strikePrice.value - basis.value) + (salePrice.value - 0.005)
            let multiplier = 1.0 + (maxGain / basis.value)
            let lowGain = salePrice.value - 0.005
            let lowMult = 1.0 + (lowGain / basis.value)
            
            let nf = NumberFormatter()
            nf.numberStyle = .decimal
            nf.minimumFractionDigits = 2
            nf.maximumFractionDigits = 2
            nf.formatWidth = 12
            print("""
                  Days in Market:  \(weekdays)
                  Capital:        $\(nf.string(from: NSNumber(value: basis.value * 100.0))!)
                  Max Value:      $\(nf.string(from: NSNumber(value: maxGain * 100.0))!)
                  Pct Max Gain:    \(nf.string(from: NSNumber(value: 100.0 * (multiplier - 1)))!)%
                      Annualized:  \(nf.string(from: NSNumber(value: 100.0 * (pow(multiplier, Double(daysInYear) / Double(weekdays)) - 1)))!)%
                  Low Value:      $\(nf.string(from: NSNumber(value: lowGain * 100.0))!)
                  Pct Low Gain:    \(nf.string(from: NSNumber(value: 100.0 * (lowMult - 1)))!)%
                      Annualized:  \(nf.string(from: NSNumber(value: 100.0 * (pow(lowMult, Double(daysInYear) / Double(weekdays)) - 1)))!)%
                  """)
        } catch ArgumentErrors.invalidArgument(let desc) {
            print("** Error: \(desc)\n\n")
            ccUsage(params: ap)
        }
        catch {
            ccUsage(params: ap)
        }
    }
    
    private static func mainUsage() -> Never {
        print("""
              Usage: optn <command> [options]
              
              Commands:
                cc    calculate the returns on a covered call
                sp    calculate the returns on a short put
                help  print this help message
              
              Use `optn <command> --help` for help on an individual command
              """)
        exit(EXIT_FAILURE)
    }
    
    public static func main() {
        guard CommandLine.argc >= 2 else {
            mainUsage()
        }
        let o = OptnCommand()
        let cmdArgs = CommandLine.arguments.dropFirst(2)

        switch CommandLine.arguments[1] {
        case "cc":
            o.covCall(args: cmdArgs)
        case "sp":
            o.shortPut(args: cmdArgs)
        default:
            mainUsage()
        }
    }
}

