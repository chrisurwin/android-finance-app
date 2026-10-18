export type AccountType = 
  | 'CURRENT' 
  | 'ISA' 
  | 'GENERAL_INVESTMENT' 
  | 'PENSION' 
  | 'FINAL_SALARY';

export type Institution = 
  | 'HSBC' 
  | 'FIRST_DIRECT' 
  | 'CHASE' 
  | 'JP_ORGAN' 
  | 'AVIVA' 
  | 'TOWERS_WATSON' 
  | 'STARLING' 
  | 'NUTMEG'
  | 'NHS'
  | 'HOSTED'
  | string;

export interface Account {
  id: string;
  name: string;
  type: AccountType;
  institution: Institution;
  balance: number;
  monthlyContribution: number;
  employerContribution: number;
  interestRate?: number; // annual % (or expected growth rate)
  annualManagementCharge: number; // AMC % (for DC pensions & investments)
  personId: string; // 'person-1' | 'person-2'
  accountNumber?: string;
  isIncluded: boolean;
  payoutAge: number; // for Final Salary (DB) pensions
  isInflationLinked: boolean;
}

export type ProjectionType = 'INDIVIDUAL_PERSON1' | 'INDIVIDUAL_PERSON2' | 'COUPLE';

export type DrawdownStrategy = 'STANDARD' | 'TAX_MINIMIZED';

export type LumpSumOption = 'UP_FRONT' | 'AS_YOU_GO';

export interface Person {
  id: string;
  name: string;
  birthYear: number;
  retirementAge: number;
  qualifyingYears: number; // UK State Pension qualifying years (max 35)
}

export interface InvestmentAssumptions {
  equityReturn: number; // e.g. 0.07 (7%)
  bondReturn: number; // e.g. 0.04 (4%)
  cashReturn: number; // e.g. 0.02 (2%)
  inflationRate: number; // e.g. 0.025 (2.5%)
  equityAllocation: number; // e.g. 0.60
  bondAllocation: number; // e.g. 0.30
  cashAllocation: number; // e.g. 0.10
}

export interface DrawdownPreferences {
  targetAnnualIncome: number;
  inflationAdjusted: boolean;
  startAge: number;
  endAge: number;
  strategy: DrawdownStrategy;
  lumpSumOption: LumpSumOption;
  stepDownAge: number;
  stepDownPercentage: number;
  isCouple: boolean;
}

export interface WithdrawalDetail {
  potId?: string;
  potName: string;
  ownerName: string;
  amountDrawn: number;
  taxFreeAmount?: number;
  taxableAmount?: number;
  taxPaid: number;
}

export interface PotYearBreakdown {
  potId: string;
  potName: string;
  potType: AccountType;
  ownerName: string;
  startBalance: number;
  contributionsAdded: number; // employee + employer annual contribution
  bedAndIsaTransfer: number; // positive if transferred into ISA, negative if moved from GIA
  growthRate: number; // effective growth % applied
  growthAmount: number; // £ gained
  grossWithdrawal: number; // total withdrawn from this pot
  taxFreeWithdrawal: number; // tax-free portion
  taxableWithdrawal: number; // taxable portion
  taxPaid: number; // income tax or savings interest tax
  endBalance: number;
}

export interface ProjectionResult {
  age: number; // active person age (Person 1 if couple)
  age1: number; // Person 1 age
  age2: number; // Person 2 age
  year: number;
  totalPensionValue: number;
  totalSavings: number;
  totalNetWorth: number;
  annualIncome: number; // target / required income
  grossIncome: number; // actual gross drawn
  netIncome: number; // actual net cash received
  tax: number; // total tax paid in year
  tax1: number; // Person 1's tax component
  tax2: number; // Person 2's tax component
  canRetire: boolean; // whether target income was fully met
  withdrawals: WithdrawalDetail[];
  potBreakdowns: PotYearBreakdown[]; // Detailed per-pot breakdown for this year!
  totalGrowthThisYear: number;
  totalTaxThisYear: number;
}

export interface RetirementProjection {
  results: ProjectionResult[];
  retirementAge: number;
  feasible: boolean;
  totalTaxPaid: number;
  fundsLastUntilAge: number;
  peakPensionPot: number;
  totalContributionsAccumulated: number;
}

export interface AppDataExport {
  version: string;
  exportDate: string;
  isCouple: boolean;
  people: Person[];
  accounts: Account[];
  assumptions: InvestmentAssumptions;
  drawdownPreferences: DrawdownPreferences;
}
