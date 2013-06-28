package name.abuchen.portfolio.model;

import java.text.MessageFormat;
import java.util.Date;
import java.util.List;

import name.abuchen.portfolio.Messages;
import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.snapshot.ClientSnapshot;
import name.abuchen.portfolio.snapshot.ReportingPeriod;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Interval;

public class PerformanceStrategyNetAssetValue
{
    private Client client;
    private ReportingPeriod reportInterval;
    
    private Date[] dates;
    private long[] totals;
    private double[] delta;
    private double[] accumulated;
    private boolean taxesArePerformanceRelevant;
    
    public PerformanceStrategyNetAssetValue(Client client, ReportingPeriod reportInterval, boolean taxesArePerformanceRelevant)
    {
        this.client = client;
        this.reportInterval = reportInterval;
        this.taxesArePerformanceRelevant = taxesArePerformanceRelevant;
    }
    
    public Date[] getDates() {
        return this.dates;
    }
    
    public long[] getTotals() {
        return this.totals;
    }
    
    public double[] getDelta() {
        return this.delta;
    }
    
    public double[] getAccumulated() {
        return this.accumulated;
    }
    
    public void calculate(List<Exception> warnings)
    {
        Interval interval = this.reportInterval.toInterval();
        int size = Days.daysBetween(interval.getStart(), interval.getEnd()).getDays() + 1;

        dates = new Date[size];
        totals = new long[size];
        delta = new double[size];
        accumulated = new double[size];
        // the marketprice of the "virtual" stock
        double[] marketPriceVirtualShares = new double[size];
        // the number of virtual shares - so that we get the correct absolute value of our portfolio
        double[] numVirtualShares = new double[size]; 

        // first collect all relevant transactions for number of virtual shares
        long[] numSharesTransactions = collectNumSharesTransactions(size, interval);
        // Transaction[][] marketPriceTransactions = collectMarketPriceTransactions(size, interval);
        // transferals = collectTransferals(size, interval);

        // first value = reference value
        dates[0] = interval.getStart().toDate();
        delta[0] = 0.0;
        accumulated[0] = 0.0;
        marketPriceVirtualShares[0] = 1.0; // start with a virtual share price of 1 - to ease percent-calculations
        ClientSnapshot snapshot = ClientSnapshot.create(this.client, dates[0]);
        long valuation = totals[0] = snapshot.getAssets();
        numVirtualShares[0] = ((double)valuation) / marketPriceVirtualShares[0] ; 
        
        // calculate series
        int index = 1;
        DateTime date = interval.getStart().plusDays(1);
        while (date.compareTo(interval.getEnd()) <= 0)
        {
            dates[index] = date.toDate();

            snapshot = ClientSnapshot.create(this.client, dates[index]);
            // get virtual market Share value of previous day
            double prevDayMarketPriceVirtualShare = marketPriceVirtualShares[index-1];
            // start with the same number of virtual shares as yesterday
            double currentNumVirtualShares = numVirtualShares[index-1];
            
            // remove/add appropriate number of virtual shares to account for monetary transactions (deposit/removal/...)
            numVirtualShares[index] = currentNumVirtualShares
                            = currentNumVirtualShares + (numSharesTransactions[index] / prevDayMarketPriceVirtualShare);
            
            // calculate new market price for virtual shares by dividing the total net value of the assets by the number of virtual shares
            valuation = totals[index] = snapshot.getAssets();
            double currentMarketPriceVirtualShare = prevDayMarketPriceVirtualShare;
            if (currentNumVirtualShares != 0.0)
            {
                currentMarketPriceVirtualShare = valuation / currentNumVirtualShares;
            }
            else
            {
                // this should really never happen - but if we no longer have any assets, we just keep the
                // previous day marketprice - if we have nothing - nothing changes :-(
                // TODO: change the message - how?!
                warnings.add(new RuntimeException(MessageFormat.format(Messages.MsgDeltaWithoutAssets,
                                valuation, date.toDate())));
            }
            marketPriceVirtualShares[index] = currentMarketPriceVirtualShare;
            
            // ok, we have the new number and price - now calculate new delta and accumulated
            // we are "1-based", so we should be able to pull this off without any kind of "translation"
            delta[index] = currentMarketPriceVirtualShare - prevDayMarketPriceVirtualShare;
            // accumulated is 0-based
            accumulated[index] = currentMarketPriceVirtualShare - 1;
            
            // start next iteration
            date = date.plusDays(1);
            index++;
        }
    }

//    private Transaction[][] collectMarketPriceTransactions(int size, Interval interval)
//    {
//        long[] accumulatedInOuts = new long[size];
//
//        for (Account a : this.client.getAccounts())
//        {
//            for (AccountTransaction t : a.getTransactions())
//            {
//                if (t.getDate().getTime() >= interval.getStartMillis()
//                                && t.getDate().getTime() <= interval.getEndMillis())
//                {
//                    long currentTransaction = 0;
//                    switch (t.getType())
//                    {
//                        case DEPOSIT:
//                        case TRANSFER_IN:
//                            currentTransaction = t.getAmount();
//                            break;
//                        case REMOVAL:
//                        case TRANSFER_OUT:
//                            currentTransaction = -t.getAmount();
//                            break;
//                        case TAXES:
//                            // if taxes are not performanceRelevant, they need to be taken into account
//                            // like REMOVALS
//                            if (!this.taxesArePerformanceRelevant)
//                            {
//                                currentTransaction = -t.getAmount();
//                            }
//                            break;
//                        default:
//                            // do nothing
//                    }
//
//                    if (currentTransaction != 0)
//                    {
//                        int ii = Days.daysBetween(interval.getStart(), new DateTime(t.getDate().getTime())).getDays();
//                        accumulatedInOuts[ii] += currentTransaction;
//                    }
//                }
//            }
//        }
//
//        for (Portfolio p : this.client.getPortfolios())
//        {
//            for (PortfolioTransaction t : p.getTransactions())
//            {
//                if (t.getDate().getTime() >= interval.getStartMillis()
//                                && t.getDate().getTime() <= interval.getEndMillis())
//                {
//                    long currentTransaction = 0;
//
//                    switch (t.getType())
//                    {
//                        case DELIVERY_INBOUND:
//                        case TRANSFER_IN:
//                            currentTransaction = t.getAmount();
//                            break;
//                        case DELIVERY_OUTBOUND:
//                        case TRANSFER_OUT:
//                            currentTransaction = -t.getAmount();
//                            break;
//                        default:
//                            // do nothing
//                    }
//
//                    if (currentTransaction != 0)
//                    {
//                        int ii = Days.daysBetween(interval.getStart(), new DateTime(t.getDate().getTime())).getDays();
//                        accumulatedInOuts[ii] += currentTransaction;
//                    }
//
//                }
//            }
//        }
//
//        return accumulatedInOuts;
//
//    }

    private long[] collectNumSharesTransactions(int size, Interval interval)
    {
        long[] accumulatedInOuts = new long[size];

        for (Account a : this.client.getAccounts())
        {
            for (AccountTransaction t : a.getTransactions())
            {
                if (t.getDate().getTime() >= interval.getStartMillis()
                                && t.getDate().getTime() <= interval.getEndMillis())
                {
                    long currentTransaction = 0;
                    switch (t.getType())
                    {
                        case DEPOSIT:
                        case TRANSFER_IN:
                            currentTransaction = t.getAmount();
                            break;
                        case REMOVAL:
                        case TRANSFER_OUT:
                            currentTransaction = -t.getAmount();
                            break;
                        case TAXES:
                            // if taxes are not performanceRelevant, they need to be taken into account
                            // like REMOVALS
                            if (!this.taxesArePerformanceRelevant)
                            {
                                currentTransaction = -t.getAmount();
                            }
                            break;
                        default:
                            // do nothing
                    }

                    if (currentTransaction != 0)
                    {
                        int ii = Days.daysBetween(interval.getStart(), new DateTime(t.getDate().getTime())).getDays();
                        accumulatedInOuts[ii] += currentTransaction;
                    }
                }
            }
        }

        for (Portfolio p : this.client.getPortfolios())
        {
            for (PortfolioTransaction t : p.getTransactions())
            {
                if (t.getDate().getTime() >= interval.getStartMillis()
                                && t.getDate().getTime() <= interval.getEndMillis())
                {
                    long currentTransaction = 0;

                    switch (t.getType())
                    {
                        case DELIVERY_INBOUND:
                        case TRANSFER_IN:
                            currentTransaction = t.getAmount();
                            break;
                        case DELIVERY_OUTBOUND:
                        case TRANSFER_OUT:
                            currentTransaction = -t.getAmount();
                            break;
                        default:
                            // do nothing
                    }

                    if (currentTransaction != 0)
                    {
                        int ii = Days.daysBetween(interval.getStart(), new DateTime(t.getDate().getTime())).getDays();
                        accumulatedInOuts[ii] += currentTransaction;
                    }

                }
            }
        }

        return accumulatedInOuts;
    }

/*    private long[] collectTransferals(int size, Interval interval)
    {
        long[] transferals = new long[size];

        for (Account a : getClient().getAccounts())
        {
            for (AccountTransaction t : a.getTransactions())
            {
                if (t.getDate().getTime() >= interval.getStartMillis()
                                && t.getDate().getTime() <= interval.getEndMillis())
                {
                    long transferal = 0;
                    switch (t.getType())
                    {
                        case DEPOSIT:
                            transferal = t.getAmount();
                            break;
                        case REMOVAL:
                            transferal = -t.getAmount();
                            break;
                        default:
                            // do nothing
                    }

                    if (transferal != 0)
                    {
                        int ii = Days.daysBetween(interval.getStart(), new DateTime(t.getDate().getTime())).getDays();
                        transferals[ii] += transferal;
                    }
                }
            }
        }

        for (Portfolio p : getClient().getPortfolios())
        {
            for (PortfolioTransaction t : p.getTransactions())
            {
                if (t.getDate().getTime() >= interval.getStartMillis()
                                && t.getDate().getTime() <= interval.getEndMillis())
                {
                    long transferal = 0;

                    switch (t.getType())
                    {
                        case DELIVERY_INBOUND:
                            transferal = t.getAmount();
                            break;
                        case DELIVERY_OUTBOUND:
                            transferal = -t.getAmount();
                            break;
                        default:
                            // do nothing
                    }

                    if (transferal != 0)
                    {
                        int ii = Days.daysBetween(interval.getStart(), new DateTime(t.getDate().getTime())).getDays();
                        transferals[ii] += transferal;
                    }

                }
            }
        }

        return transferals;
    }
*/
}
