import {
  Account,
  Person,
  InvestmentAssumptions,
  DrawdownPreferences,
  AppDataExport,
  ProjectionResult
} from '../types';

export const DEFAULT_PEOPLE: Person[] = [
  {
    id: 'person-1',
    name: 'Person 1',
    birthYear: 1980,
    retirementAge: 65,
    qualifyingYears: 35
  },
  {
    id: 'person-2',
    name: 'Person 2',
    birthYear: 1982,
    retirementAge: 65,
    qualifyingYears: 35
  }
];

export const DEFAULT_ACCOUNTS: Account[] = [];

export const DEFAULT_ASSUMPTIONS: InvestmentAssumptions = {
  equityReturn: 0.07,
  bondReturn: 0.04,
  cashReturn: 0.02,
  inflationRate: 0.025,
  equityAllocation: 0.60,
  bondAllocation: 0.30,
  cashAllocation: 0.10
};

export const DEFAULT_DRAWDOWN: DrawdownPreferences = {
  targetAnnualIncome: 0,
  inflationAdjusted: true,
  startAge: 65,
  endAge: 95,
  strategy: 'TAX_MINIMIZED',
  lumpSumOption: 'AS_YOU_GO',
  stepDownAge: 80,
  stepDownPercentage: 25,
  isCouple: true
};

const STORAGE_KEY = 'finance_tracker_web_state_v1';

export interface AppStoredState {
  people: Person[];
  accounts: Account[];
  assumptions: InvestmentAssumptions;
  drawdown: DrawdownPreferences;
}

export function loadStoredState(): AppStoredState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed.people && parsed.accounts) {
        return {
          people: parsed.people,
          accounts: parsed.accounts,
          assumptions: parsed.assumptions || DEFAULT_ASSUMPTIONS,
          drawdown: parsed.drawdown || DEFAULT_DRAWDOWN
        };
      }
    }
  } catch (err) {
    console.error('Failed to load local storage state:', err);
  }

  return {
    people: DEFAULT_PEOPLE,
    accounts: DEFAULT_ACCOUNTS,
    assumptions: DEFAULT_ASSUMPTIONS,
    drawdown: DEFAULT_DRAWDOWN
  };
}

export function saveStoredState(state: AppStoredState): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch (err) {
    console.error('Failed to save state to local storage:', err);
  }
}

export function downloadJsonFile(data: AppDataExport, filename = 'pension-finance-data.json'): void {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

/**
 * Robust JSON Import Parser:
 * Supports:
 * 1. Native web export format
 * 2. Android app JSON format
 * 3. Legacy CRA pension tracker JSON format
 */
export function parseImportedJson(jsonText: string): AppStoredState {
  const raw = JSON.parse(jsonText);

  // Check 1: Native web format
  if (raw.people && Array.isArray(raw.accounts)) {
    return {
      people: raw.people,
      accounts: raw.accounts,
      assumptions: raw.assumptions || DEFAULT_ASSUMPTIONS,
      drawdown: raw.drawdownPreferences || raw.drawdown || DEFAULT_DRAWDOWN
    };
  }

  // Check 2: Android app format ({ accounts, person / person_1, person_2, assumptions, drawdown })
  if (Array.isArray(raw.accounts)) {
    const p1Raw = raw.person_1 || raw.person;
    const p1 = p1Raw ? {
      id: p1Raw.id || 'person-1',
      name: p1Raw.name || 'Person 1',
      birthYear: Number(p1Raw.birthYear) || 1980,
      retirementAge: Number(p1Raw.retirementAge) || 65,
      qualifyingYears: 35
    } : DEFAULT_PEOPLE[0];

    const p2Raw = raw.person_2;
    const p2 = p2Raw ? {
      id: p2Raw.id || 'person-2',
      name: p2Raw.name || 'Person 2',
      birthYear: Number(p2Raw.birthYear) || 1982,
      retirementAge: Number(p2Raw.retirementAge) || 65,
      qualifyingYears: 35
    } : DEFAULT_PEOPLE[1];

    return {
      people: [p1, p2],
      accounts: raw.accounts,
      assumptions: raw.assumptions || DEFAULT_ASSUMPTIONS,
      drawdown: raw.drawdown || DEFAULT_DRAWDOWN
    };
  }

  // Check 3: Legacy pension tracker format ({ dcPensions, dbPensions, savings, people, investmentAssumptions, drawdownPreferences })
  if (raw.dcPensions || raw.dbPensions || raw.savings) {
    const people: Person[] = (raw.people || []).map((p: any) => {
      let bYear = 1974;
      if (p.birthDate) {
        bYear = parseInt(p.birthDate.split('-')[0], 10) || 1974;
      } else if (p.birthYear) {
        bYear = Number(p.birthYear);
      }
      return {
        id: p.id || `person-${Math.random()}`,
        name: p.name || 'User',
        birthYear: bYear,
        retirementAge: Number(p.retirementAge) || 60,
        qualifyingYears: 35
      };
    });

    const accounts: Account[] = [];

    // Map DC pensions
    (raw.dcPensions || []).forEach((dc: any) => {
      const dcRate = (dc.interestRate !== undefined && dc.interestRate !== null && !isNaN(Number(dc.interestRate)))
        ? Number(dc.interestRate)
        : (dc.growthRate !== undefined && !isNaN(Number(dc.growthRate)) ? Number(dc.growthRate) : 7.0);
      const amc = (dc.annualManagementCharge !== undefined && dc.annualManagementCharge !== null && !isNaN(Number(dc.annualManagementCharge)))
        ? Number(dc.annualManagementCharge)
        : 0.5;

      accounts.push({
        id: dc.id || `dc-${Math.random()}`,
        name: dc.name ? `${dc.name} Pension` : 'DC Pension',
        type: 'PENSION',
        institution: 'HOSTED',
        balance: Number(dc.currentValue) || 0,
        monthlyContribution: Number(dc.monthlyContribution) || 0,
        employerContribution: Number(dc.employerContribution) || 0,
        interestRate: dcRate,
        annualManagementCharge: amc,
        personId: dc.personId?.includes('1760') ? 'person-2' : (dc.personId || 'person-1'),
        isIncluded: true,
        payoutAge: 65,
        isInflationLinked: true
      });
    });

    // Map DB pensions
    (raw.dbPensions || []).forEach((db: any) => {
      accounts.push({
        id: db.id || `db-${Math.random()}`,
        name: db.name ? `${db.name.toUpperCase()} DB Pension` : 'Final Salary DB',
        type: 'FINAL_SALARY',
        institution: db.name?.toLowerCase().includes('nhs') ? 'NHS' : 'HOSTED',
        balance: Number(db.annualPayment) || 0,
        monthlyContribution: 0,
        employerContribution: 0,
        interestRate: 0,
        annualManagementCharge: 0,
        personId: db.personId?.includes('1760') ? 'person-2' : (db.personId || 'person-1'),
        isIncluded: true,
        payoutAge: Number(db.pensionAge || db.startAge) || 67,
        isInflationLinked: db.inflationProtection !== false
      });
    });

    // Map Savings (ISAs vs Non-ISAs)
    (raw.savings || []).forEach((s: any) => {
      const isIsa = s.taxFree !== false && (s.name?.toLowerCase().includes('isa') || s.taxFree === true);
      const sRate = (s.interestRate !== undefined && s.interestRate !== null && !isNaN(Number(s.interestRate)))
        ? Number(s.interestRate)
        : (s.growthRate !== undefined && !isNaN(Number(s.growthRate)) ? Number(s.growthRate) : (isIsa ? 6.0 : 5.0));
      const amc = (s.annualManagementCharge !== undefined && s.annualManagementCharge !== null && !isNaN(Number(s.annualManagementCharge)))
        ? Number(s.annualManagementCharge)
        : 0.0;

      accounts.push({
        id: s.id || `sav-${Math.random()}`,
        name: s.name || (isIsa ? 'ISA' : 'General Investment Account'),
        type: isIsa ? 'ISA' : 'GENERAL_INVESTMENT',
        institution: 'HOSTED',
        balance: Number(s.currentAmount) || 0,
        monthlyContribution: Number(s.monthlyContribution) || 0,
        employerContribution: 0,
        interestRate: sRate,
        annualManagementCharge: amc,
        personId: s.personId?.includes('1760') ? 'person-2' : (s.personId || 'person-1'),
        isIncluded: true,
        payoutAge: 65,
        isInflationLinked: true
      });
    });

    return {
      people: people.length > 0 ? people : DEFAULT_PEOPLE,
      accounts: accounts.length > 0 ? accounts : DEFAULT_ACCOUNTS,
      assumptions: raw.investmentAssumptions || DEFAULT_ASSUMPTIONS,
      drawdown: {
        ...DEFAULT_DRAWDOWN,
        targetAnnualIncome: raw.drawdownPreferences?.targetAnnualIncome || DEFAULT_DRAWDOWN.targetAnnualIncome,
        inflationAdjusted: raw.drawdownPreferences?.inflationAdjusted !== false,
        startAge: raw.drawdownPreferences?.startAge || DEFAULT_DRAWDOWN.startAge,
        endAge: raw.drawdownPreferences?.endAge || DEFAULT_DRAWDOWN.endAge
      }
    };
  }

  throw new Error('Unrecognized JSON structure. Please ensure this is an export from this app or the Android app.');
}

/**
 * Detailed CSV Export with Summary AND Every Pot's Yearly Lifecycle
 */
export function downloadDetailedCsv(results: ProjectionResult[]): void {
  const headers = [
    'Year',
    'Age',
    'Total Pension (£)',
    'Total Savings (£)',
    'Net Worth (£)',
    'Target Income (£)',
    'Gross Drawn (£)',
    'Net Received (£)',
    'Tax Paid (£)',
    'Total Growth (£)',
    'Target Met?',
    'Pot Name',
    'Pot Type',
    'Owner',
    'Pot Start Balance (£)',
    'Contributions (£)',
    'Bed & ISA Transfer (£)',
    'Growth Rate (%)',
    'Growth Amount (£)',
    'Gross Withdrawal (£)',
    'Tax Paid On Pot (£)',
    'Pot End Balance (£)'
  ];

  const rows: string[] = [];
  rows.push(headers.join(','));

  results.forEach(res => {
    if (res.potBreakdowns.length === 0) {
      rows.push([
        res.year,
        res.age,
        res.totalPensionValue.toFixed(2),
        res.totalSavings.toFixed(2),
        res.totalNetWorth.toFixed(2),
        res.annualIncome.toFixed(2),
        res.grossIncome.toFixed(2),
        res.netIncome.toFixed(2),
        res.tax.toFixed(2),
        res.totalGrowthThisYear.toFixed(2),
        res.canRetire ? 'YES' : 'NO',
        '--',
        '--',
        '--',
        '0.00',
        '0.00',
        '0.00',
        '0.00',
        '0.00',
        '0.00',
        '0.00',
        '0.00'
      ].join(','));
    } else {
      res.potBreakdowns.forEach(pot => {
        rows.push([
          res.year,
          res.age,
          res.totalPensionValue.toFixed(2),
          res.totalSavings.toFixed(2),
          res.totalNetWorth.toFixed(2),
          res.annualIncome.toFixed(2),
          res.grossIncome.toFixed(2),
          res.netIncome.toFixed(2),
          res.tax.toFixed(2),
          res.totalGrowthThisYear.toFixed(2),
          res.canRetire ? 'YES' : 'NO',
          `"${pot.potName.replace(/"/g, '""')}"`,
          pot.potType,
          pot.ownerName,
          pot.startBalance.toFixed(2),
          pot.contributionsAdded.toFixed(2),
          pot.bedAndIsaTransfer.toFixed(2),
          pot.growthRate.toFixed(2),
          pot.growthAmount.toFixed(2),
          pot.grossWithdrawal.toFixed(2),
          pot.taxPaid.toFixed(2),
          pot.endBalance.toFixed(2)
        ].join(','));
      });
    }
  });

  const csvContent = rows.join('\n');
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `pension-projections-detailed-${new Date().toISOString().split('T')[0]}.csv`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
