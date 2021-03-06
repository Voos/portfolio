package name.abuchen.portfolio.ui.views;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.IndustryClassification;
import name.abuchen.portfolio.model.IndustryClassification.Category;
import name.abuchen.portfolio.model.LatestSecurityPrice;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityPrice;
import name.abuchen.portfolio.model.Values;
import name.abuchen.portfolio.model.Watchlist;
import name.abuchen.portfolio.ui.AbstractFinanceView;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.PortfolioPlugin;
import name.abuchen.portfolio.ui.UpdateQuotesJob;
import name.abuchen.portfolio.ui.dialogs.BuySellSecurityDialog;
import name.abuchen.portfolio.ui.dialogs.DividendsDialog;
import name.abuchen.portfolio.ui.dnd.SecurityDragListener;
import name.abuchen.portfolio.ui.dnd.SecurityTransfer;
import name.abuchen.portfolio.ui.util.ColumnViewerSorter;
import name.abuchen.portfolio.ui.util.ShowHideColumnHelper;
import name.abuchen.portfolio.ui.util.ShowHideColumnHelper.Column;
import name.abuchen.portfolio.ui.util.ShowHideColumnHelper.OptionLabelProvider;
import name.abuchen.portfolio.ui.util.SimpleListContentProvider;
import name.abuchen.portfolio.ui.util.ViewerHelper;
import name.abuchen.portfolio.ui.util.WebLocationMenu;
import name.abuchen.portfolio.ui.wizards.EditSecurityWizard;
import name.abuchen.portfolio.ui.wizards.ImportQuotesWizard;
import name.abuchen.portfolio.util.Dates;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

public final class SecuritiesTable
{
    private AbstractFinanceView view;

    private Watchlist watchlist;

    private Menu contextMenu;
    private TableViewer securities;

    private ShowHideColumnHelper support;

    public SecuritiesTable(Composite parent, AbstractFinanceView view)
    {
        this.view = view;

        Composite container = new Composite(parent, SWT.NONE);
        TableColumnLayout layout = new TableColumnLayout();
        container.setLayout(layout);

        this.securities = new TableViewer(container, SWT.FULL_SELECTION);

        support = new ShowHideColumnHelper(SecuritiesTable.class.getName(), securities, layout);

        Column column = new Column(Messages.ColumnName, SWT.LEFT, 400);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                return ((Security) e).getName();
            }

            @Override
            public Image getImage(Object e)
            {
                return PortfolioPlugin.image(PortfolioPlugin.IMG_SECURITY);
            }
        });
        column.setSorter(ColumnViewerSorter.create(Security.class, "name"), SWT.DOWN); //$NON-NLS-1$
        support.addColumn(column);

        column = new Column(Messages.ColumnISIN, SWT.LEFT, 100);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                return ((Security) e).getIsin();
            }
        });
        column.setSorter(ColumnViewerSorter.create(Security.class, "isin")); //$NON-NLS-1$
        support.addColumn(column);

        column = new Column(Messages.ColumnTicker, SWT.LEFT, 80);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                return ((Security) e).getTickerSymbol();
            }
        });
        column.setSorter(ColumnViewerSorter.create(Security.class, "tickerSymbol")); //$NON-NLS-1$
        support.addColumn(column);

        column = new Column(Messages.ColumnSecurityType, SWT.LEFT, 80);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                return ((Security) e).getType().toString();
            }
        });
        column.setSorter(ColumnViewerSorter.create(Security.class, "type")); //$NON-NLS-1$
        support.addColumn(column);

        column = new Column(Messages.ColumnLatest, SWT.RIGHT, 60);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                SecurityPrice latest = ((Security) e).getSecurityPrice(Dates.today());
                return latest != null ? Values.Quote.format(latest.getValue()) : null;
            }
        });
        column.setSorter(ColumnViewerSorter.create(new Comparator<Object>()
        {
            @Override
            public int compare(Object o1, Object o2)
            {
                SecurityPrice p1 = ((Security) o1).getSecurityPrice(Dates.today());
                SecurityPrice p2 = ((Security) o2).getSecurityPrice(Dates.today());

                if (p1 == null)
                    return p2 == null ? 0 : -1;
                if (p2 == null)
                    return 1;

                long v1 = p1.getValue();
                long v2 = p2.getValue();
                return v1 > v2 ? 1 : v1 == v2 ? 0 : -1;
            }
        }));
        support.addColumn(column);

        column = new Column(Messages.ColumnDelta, SWT.RIGHT, 60);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                SecurityPrice price = ((Security) e).getSecurityPrice(Dates.today());
                if (!(price instanceof LatestSecurityPrice))
                    return null;

                LatestSecurityPrice latest = (LatestSecurityPrice) price;
                return String.format("%,.2f %%", //$NON-NLS-1$
                                ((double) (latest.getValue() - latest.getPreviousClose()) / (double) latest
                                                .getPreviousClose()) * 100);
            }

            @Override
            public Color getForeground(Object element)
            {
                SecurityPrice price = ((Security) element).getSecurityPrice(Dates.today());
                if (!(price instanceof LatestSecurityPrice))
                    return null;

                LatestSecurityPrice latest = (LatestSecurityPrice) price;
                return latest.getValue() >= latest.getPreviousClose() ? Display.getCurrent().getSystemColor(
                                SWT.COLOR_DARK_GREEN) : Display.getCurrent().getSystemColor(SWT.COLOR_DARK_RED);
            }
        });
        column.setSorter(ColumnViewerSorter.create(new Comparator<Object>()
        {
            @Override
            public int compare(Object o1, Object o2)
            {
                SecurityPrice p1 = ((Security) o1).getSecurityPrice(Dates.today());
                SecurityPrice p2 = ((Security) o2).getSecurityPrice(Dates.today());

                if (!(p1 instanceof LatestSecurityPrice))
                    return p2 == null ? 0 : -1;
                if (!(p2 instanceof LatestSecurityPrice))
                    return 1;

                LatestSecurityPrice l1 = (LatestSecurityPrice) p1;
                LatestSecurityPrice l2 = (LatestSecurityPrice) p2;

                double v1 = (((double) (l1.getValue() - l1.getPreviousClose())) / l1.getPreviousClose() * 100);
                double v2 = (((double) (l2.getValue() - l2.getPreviousClose())) / l2.getPreviousClose() * 100);
                return Double.compare(v1, v2);
            }
        }));
        support.addColumn(column);

        addIndustryClassificationColumns(support);

        column = new Column(Messages.ColumnWKN, SWT.LEFT, 60);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                return ((Security) e).getWkn();
            }
        });
        column.setSorter(ColumnViewerSorter.create(Security.class, "wkn")); //$NON-NLS-1$
        column.setVisible(false);
        support.addColumn(column);

        column = new Column(Messages.ColumnRetired, SWT.LEFT, 40);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                return ((Security) e).isRetired() ? "\u2022" : null; //$NON-NLS-1$
            }
        });
        column.setSorter(ColumnViewerSorter.create(Security.class, "retired")); //$NON-NLS-1$
        column.setVisible(false);
        support.addColumn(column);

        addColumnLatestPrice();
        addColumnLatestHistoricalPrice();

        support.createColumns();

        securities.getTable().setHeaderVisible(true);
        securities.getTable().setLinesVisible(true);

        securities.setContentProvider(new SimpleListContentProvider());

        securities.addDragSupport(DND.DROP_MOVE, //
                        new Transfer[] { SecurityTransfer.getTransfer() }, //
                        new SecurityDragListener(securities));

        if (!support.isUserConfigured())
            ViewerHelper.pack(securities);
        securities.refresh();

        securities.getTable().addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetDefaultSelected(SelectionEvent event)
            {
                Security security = (Security) ((IStructuredSelection) securities.getSelection()).getFirstElement();
                if (security == null)
                    return;

                Dialog dialog = new WizardDialog(getShell(), new EditSecurityWizard(getClient(), security));
                if (dialog.open() != Dialog.OK)
                    return;

                markDirty();
                if (!securities.getControl().isDisposed())
                {
                    refresh(security);
                    updateQuotes(security);
                }
            }
        });

        hookContextMenu();
    }

    private void addIndustryClassificationColumns(ShowHideColumnHelper support)
    {
        List<Integer> options = new ArrayList<Integer>();

        StringBuilder commonLabels = new StringBuilder();
        commonLabels.append("{0,choice,"); //$NON-NLS-1$

        int index = 1;
        for (String label : getClient().getIndustryTaxonomy().getLabels())
        {
            options.add(index);
            if (index > 1)
                commonLabels.append('|');
            commonLabels.append(index).append('#').append(label);

            index++;
        }
        options.add(100);

        String menuLabels = commonLabels + //
                        "|100#" + Messages.LabelFullClassification + //$NON-NLS-1$
                        "}"; //$NON-NLS-1$

        String columnLabels = commonLabels + //
                        "|100#" + Messages.ShortLabelIndustry + //$NON-NLS-1$
                        "}"; //$NON-NLS-1$

        Column column = new Column(Messages.ShortLabelIndustry, SWT.LEFT, 120);
        column.setOptions(menuLabels, columnLabels, options.toArray(new Integer[0]));
        column.setLabelProvider(new OptionLabelProvider()
        {
            private IndustryClassification taxonomy = getClient().getIndustryTaxonomy();

            @Override
            public String getText(Object e, Integer option)
            {
                Security security = (Security) e;
                IndustryClassification.Category category = taxonomy.getCategoryById(security
                                .getIndustryClassification());
                if (category == null)
                    return null;

                switch (option)
                {
                    case 100:
                        return category.getPathLabel();
                    default:
                        List<Category> path = category.getPath();
                        if (option < path.size())
                            return path.get(option).getLabel();
                }

                return null;
            }
        });
        column.setVisible(false);
        support.addColumn(column);
    }

    private void addColumnLatestPrice()
    {
        Column column;
        column = new Column(Messages.ColumnLatestDate, SWT.LEFT, 80);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                SecurityPrice latest = ((Security) element).getSecurityPrice(Dates.today());
                return latest != null ? Values.Date.format(latest.getTime()) : null;
            }

            @Override
            public Color getForeground(Object element)
            {
                return getColor(element, SWT.COLOR_INFO_FOREGROUND);
            }

            @Override
            public Color getBackground(Object element)
            {
                return getColor(element, SWT.COLOR_INFO_BACKGROUND);
            }

            private Color getColor(Object element, int colorId)
            {
                SecurityPrice latest = ((Security) element).getSecurityPrice(Dates.today());

                Date sevenDaysAgo = new Date(System.currentTimeMillis() - (7 * Dates.DAY_IN_MS));
                if (latest != null && latest.getTime().before(sevenDaysAgo))
                    return Display.getDefault().getSystemColor(colorId);
                else
                    return null;
            }
        });
        column.setSorter(ColumnViewerSorter.create(new Comparator<Object>()
        {
            @Override
            public int compare(Object o1, Object o2)
            {
                SecurityPrice p1 = ((Security) o1).getSecurityPrice(Dates.today());
                SecurityPrice p2 = ((Security) o2).getSecurityPrice(Dates.today());

                if (p1 == null)
                    return p2 == null ? 0 : -1;
                if (p2 == null)
                    return 1;

                return p1.getTime().compareTo(p2.getTime());
            }
        }));
        support.addColumn(column);
    }

    private void addColumnLatestHistoricalPrice()
    {
        Column column = new Column(Messages.ColumnLatestHistoricalDate, SWT.LEFT, 80);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                List<SecurityPrice> prices = ((Security) element).getPrices();
                if (prices.isEmpty())
                    return null;

                SecurityPrice latest = prices.get(prices.size() - 1);
                return latest != null ? Values.Date.format(latest.getTime()) : null;
            }

            @Override
            public Color getForeground(Object element)
            {
                return getColor(element, SWT.COLOR_INFO_FOREGROUND);
            }

            @Override
            public Color getBackground(Object element)
            {
                return getColor(element, SWT.COLOR_INFO_BACKGROUND);
            }

            private Color getColor(Object element, int colorId)
            {
                List<SecurityPrice> prices = ((Security) element).getPrices();
                if (prices.isEmpty())
                    return null;

                SecurityPrice latest = prices.get(prices.size() - 1);
                if (latest.getTime().before(new Date(System.currentTimeMillis() - (7 * Dates.DAY_IN_MS))))
                    return Display.getDefault().getSystemColor(colorId);
                else
                    return null;
            }
        });
        column.setSorter(ColumnViewerSorter.create(new Comparator<Object>()
        {
            @Override
            public int compare(Object o1, Object o2)
            {
                List<SecurityPrice> prices1 = ((Security) o1).getPrices();
                SecurityPrice p1 = prices1.isEmpty() ? null : prices1.get(prices1.size() - 1);
                List<SecurityPrice> prices2 = ((Security) o2).getPrices();
                SecurityPrice p2 = prices2.isEmpty() ? null : prices2.get(prices2.size() - 1);

                if (p1 == null)
                    return p2 == null ? 0 : -1;
                if (p2 == null)
                    return 1;

                return p1.getTime().compareTo(p2.getTime());
            }
        }));
        support.addColumn(column);
    }

    public void addSelectionChangedListener(ISelectionChangedListener listener)
    {
        this.securities.addSelectionChangedListener(listener);
    }

    public void addFilter(ViewerFilter filter)
    {
        this.securities.addFilter(filter);
    }

    public void setInput(List<Security> securities)
    {
        this.securities.setInput(securities);
        this.watchlist = null;
    }

    public void setInput(Watchlist watchlist)
    {
        this.securities.setInput(watchlist.getSecurities());
        this.watchlist = watchlist;
    }

    public void refresh(Security security)
    {
        this.securities.refresh(security, true);
    }

    public void refresh()
    {
        try
        {
            securities.getControl().setRedraw(false);
            securities.refresh();
        }
        finally
        {
            securities.getControl().setRedraw(true);
        }
    }

    public void updateQuotes(Security security)
    {
        new UpdateQuotesJob(security)
        {
            @Override
            protected void notifyFinished()
            {
                PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                {
                    public void run()
                    {
                        markDirty();
                        if (!securities.getTable().isDisposed())
                        {
                            securities.refresh();
                            securities.setSelection(securities.getSelection());
                        }
                    }
                });
            }

        }.schedule();
    }

    public TableViewer getTableViewer()
    {
        return securities;
    }

    public ShowHideColumnHelper getColumnHelper()
    {
        return support;
    }

    //
    // private
    //

    private Client getClient()
    {
        return view.getClient();
    }

    private Shell getShell()
    {
        return securities.getTable().getShell();
    }

    private void markDirty()
    {
        view.markDirty();
    }

    private void hookContextMenu()
    {
        MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener()
        {
            public void menuAboutToShow(IMenuManager manager)
            {
                fillContextMenu(manager);
            }
        });

        contextMenu = menuMgr.createContextMenu(securities.getTable());
        securities.getTable().setMenu(contextMenu);

        securities.getTable().addDisposeListener(new DisposeListener()
        {
            @Override
            public void widgetDisposed(DisposeEvent e)
            {
                if (contextMenu != null)
                    contextMenu.dispose();
            }
        });
    }

    private void fillContextMenu(IMenuManager manager)
    {
        final Security security = (Security) ((IStructuredSelection) securities.getSelection()).getFirstElement();
        if (security == null)
            return;

        manager.add(new AbstractDialogAction(Messages.SecurityMenuBuy)
        {
            @Override
            Dialog createDialog(Security security)
            {
                return new BuySellSecurityDialog(getShell(), getClient(), security, PortfolioTransaction.Type.BUY);
            }
        });

        manager.add(new AbstractDialogAction(Messages.SecurityMenuSell)
        {
            @Override
            Dialog createDialog(Security security)
            {
                return new BuySellSecurityDialog(getShell(), getClient(), security, PortfolioTransaction.Type.SELL);
            }
        });

        manager.add(new AbstractDialogAction(Messages.SecurityMenuDividends)
        {
            @Override
            Dialog createDialog(Security security)
            {
                return new DividendsDialog(getShell(), getClient(), security);
            }
        });
        manager.add(new Separator());

        manager.add(new AbstractDialogAction(Messages.SecurityMenuEditSecurity)
        {
            @Override
            Dialog createDialog(Security security)
            {
                return new WizardDialog(getShell(), new EditSecurityWizard(getClient(), security));
            }

            @Override
            protected void performFinish(Security security)
            {
                super.performFinish(security);
                updateQuotes(security);
            }
        });
        manager.add(new Separator());

        manager.add(new Action(Messages.SecurityMenuUpdateQuotes)
        {
            @Override
            public void run()
            {
                updateQuotes(security);
            }
        });
        manager.add(new AbstractDialogAction(Messages.SecurityMenuImportQuotes)
        {
            @Override
            Dialog createDialog(Security security)
            {
                return new WizardDialog(getShell(), new ImportQuotesWizard(security));
            }
        });

        manager.add(new Separator());
        manager.add(new WebLocationMenu(security));

        manager.add(new Separator());
        if (watchlist == null)
        {
            manager.add(new Action(Messages.SecurityMenuDeleteSecurity)
            {
                @Override
                public void run()
                {
                    if (!security.getTransactions(getClient()).isEmpty())
                    {
                        MessageDialog.openError(getShell(), Messages.MsgDeletionNotPossible,
                                        MessageFormat.format(Messages.MsgDeletionNotPossibleDetail, security.getName()));
                    }
                    else if (getClient().getRootCategory().getTreeElements().contains(security))
                    {
                        MessageDialog.openError(getShell(), Messages.MsgDeletionNotPossible, MessageFormat.format(
                                        Messages.MsgDeletionNotPossibleAssignedInAllocation, security.getName()));
                    }
                    else
                    {

                        getClient().removeSecurity(security);
                        markDirty();

                        securities.setInput(getClient().getSecurities());
                    }
                }
            });
        }
        else
        {
            manager.add(new Action(MessageFormat.format(Messages.SecurityMenuRemoveFromWatchlist, watchlist.getName()))
            {
                @Override
                public void run()
                {
                    watchlist.getSecurities().remove(security);
                    markDirty();

                    securities.setInput(watchlist.getSecurities());
                }
            });
        }
    }

    private abstract class AbstractDialogAction extends Action
    {

        public AbstractDialogAction(String text)
        {
            super(text);
        }

        @Override
        public final void run()
        {
            Security security = (Security) ((IStructuredSelection) securities.getSelection()).getFirstElement();

            if (security == null)
                return;

            Dialog dialog = createDialog(security);
            if (dialog.open() == Dialog.OK)
                performFinish(security);
        }

        protected void performFinish(Security security)
        {
            markDirty();
            if (!securities.getControl().isDisposed())
            {
                securities.refresh(security, true);
                securities.setSelection(securities.getSelection());
            }
        }

        abstract Dialog createDialog(Security security);
    }
}
