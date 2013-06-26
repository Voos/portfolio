package name.abuchen.portfolio.snapshot;

import java.text.MessageFormat;
import java.util.Date;
import java.util.List;

import name.abuchen.portfolio.Messages;
import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Interval;

/* package */class ClientIndex extends PerformanceIndex
{
    /* package */ClientIndex(Client client, ReportingPeriod reportInterval)
    {
        super(client, reportInterval);
    }

    /* package */void calculate(List<Exception> warnings)
    {
        Interval interval = getReportInterval().toInterval();
        int size = Days.daysBetween(interval.getStart(), interval.getEnd()).getDays() + 1;

        dates = new Date[size];
        totals = new long[size];
        delta = new double[size];
        accumulated = new double[size];

        transferals = collectTransferals(size, interval);
        long[] buys = collectBuys(size, interval);
        long[] sales = collectSales(size, interval);

        // first value = reference value
        dates[0] = interval.getStart().toDate();
        delta[0] = 0;
        accumulated[0] = 0;
        ClientSnapshot snapshot = ClientSnapshot.create(getClient(), dates[0]);
        long valuation = totals[0] = snapshot.getAssets();

        // calculate series
        int index = 1;
        DateTime date = interval.getStart().plusDays(1);
        while (date.compareTo(interval.getEnd()) <= 0)
        {
            dates[index] = date.toDate();

            snapshot = ClientSnapshot.create(getClient(), dates[index]);
            long thisValuation = totals[index] = snapshot.getAssets();
            long thisDelta = thisValuation - transferals[index] - valuation;

            if (valuation == 0)
            {
                delta[index] = 0;

                if (thisDelta != 0d)
                {
                    if (transferals[index] != 0)
                        delta[index] = (double) thisDelta / (double) transferals[index];
                    else
                        warnings.add(new RuntimeException(MessageFormat.format(Messages.MsgDeltaWithoutAssets,
                                        thisDelta, date.toDate())));
                }
            }
            else
            {
                delta[index] = (double) thisDelta / (double) valuation;
            }

            // accumulated[index] = ((accumulated[index - 1] + 1) * (delta[index] + 1)) - 1;
            long divisor = totals[0] + buys[index];
            if (0 != divisor)
            {
                accumulated[index] = ((double)(totals[index] + sales[index] ) / (double)( totals[0] + buys[index] )) - 1;
            }
            else
            {
                accumulated[index] = 0;
            }
            
            date = date.plusDays(1);
            valuation = thisValuation;
            index++;
        }
    }

    private long[] collectTransferals(int size, Interval interval)
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
    
    private long[] collectBuys(int size, Interval interval)
    {
        long[] buys = new long[size];

        // ignore accounts for buy
/*        for (Account a : getClient().getAccounts())
        {
            for (AccountTransaction t : a.getTransactions())
            {
                if (t.getDate().getTime() >= interval.getStartMillis()
                                && t.getDate().getTime() <= interval.getEndMillis())
                {
                    long buy = 0;
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
*/
        for (Portfolio p : getClient().getPortfolios())
        {
            for (PortfolioTransaction t : p.getTransactions())
            {
                if (t.getDate().getTime() >= interval.getStartMillis()
                                && t.getDate().getTime() <= interval.getEndMillis())
                {
                    long buy = 0;

                    switch (t.getType())
                    {
                        case BUY:
                        case DELIVERY_INBOUND:
                            buy = t.getAmount();
                            break;
/*                        case DELIVERY_OUTBOUND:
                            transferal = -t.getAmount();
                            break;
*/                        default:
                            // do nothing
                    }

                    if (buy != 0)
                    {
                        int ii = Days.daysBetween(interval.getStart(), new DateTime(t.getDate().getTime())).getDays();
                        buys[ii] += buy;
                    }

                }
            }
        }
        
        // get cumulative sum of buys and store in index
        for (int i = 1; i < size; i++)
        {
            buys[i] = buys[i] + buys[i-1];
        }

        return buys;
    }

    private long[] collectSales(int size, Interval interval)
    {
        long[] sales = new long[size];

        // ignore accounts for buy
/*        for (Account a : getClient().getAccounts())
        {
            for (AccountTransaction t : a.getTransactions())
            {
                if (t.getDate().getTime() >= interval.getStartMillis()
                                && t.getDate().getTime() <= interval.getEndMillis())
                {
                    long buy = 0;
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
*/
        for (Portfolio p : getClient().getPortfolios())
        {
            for (PortfolioTransaction t : p.getTransactions())
            {
                if (t.getDate().getTime() >= interval.getStartMillis()
                                && t.getDate().getTime() <= interval.getEndMillis())
                {
                    long sale = 0;

                    switch (t.getType())
                    {
                        case SELL:
                        case DELIVERY_OUTBOUND:
                            sale = t.getAmount();
                            break;
/*                        case DELIVERY_OUTBOUND:
                            transferal = -t.getAmount();
                            break;
*/                        default:
                            // do nothing
                    }

                    if (sale != 0)
                    {
                        int ii = Days.daysBetween(interval.getStart(), new DateTime(t.getDate().getTime())).getDays();
                        sales[ii] += sale;
                    }

                }
            }
        }
        
        // get cumulative sum of buys and store in index
        for (int i = 1; i < size; i++)
        {
            sales[i] = sales[i] + sales[i-1];
        }

        return sales;
    }

}
