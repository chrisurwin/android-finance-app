import {
  Account,
  AccountType,
  InvestmentAssumptions,
  DrawdownPreferences,
  Person,
  ProjectionType,
  RetirementProjection,
  ProjectionResult,
  WithdrawalDetail,
  PotYearBreakdown,
  StochasticYearSample,
  MonteCarloResult,
  MonteCarloYearPercentile
} from '../types';

export const MAX_TAX_FREE_LUMP_SUM = 268275.0; // UK Lump Sum Allowance (LSA)
export const ANNUAL_ISA_ALLOWANCE = 20000.0; // UK ISA Annual Allowance per person
export const FULL_STATE_PENSION_WEEKLY = 230.25;
export const FULL_STATE_PENSION_ANNUAL = FULL_STATE_PENSION_WEEKLY * 52.0; // ~£11,973.00

// UK Tax Bands 2024/25 - 2025/26
export const PERSONAL_ALLOWANCE = 12570.0;
export const BASIC_RATE_THRESHOLD = 37700.0; // £12,570 to £50,270
export const HIGHER_RATE_THRESHOLD = 125140.0; // £50,270 to £125,140

export const BASIC_RATE = 0.20;
export const HIGHER_RATE = 0.40;
export const ADDITIONAL_RATE = 0.45;

/**
 * Determines UK legislated State Pension age based on birth year.
 */
export function getStatePensionAge(birthYear: number): number {
  if (birthYear < 1961) return 66;
  if (birthYear <= 1977) return 67;
  return 68;
}

/**
 * Calculates annual state pension based on qualifying years (up to 35 years).
 */
export function calculateStatePension(qualifyingYears: number): number {
  const proportion = Math.min(qualifyingYears / 35.0, 1.0);
  return FULL_STATE_PENSION_ANNUAL * proportion;
}

/**
 * Calculates UK Income Tax including Personal Allowance taper for incomes > £100,000.
 */
export function calculateIncomeTax(taxableIncome: number): number {
  if (taxableIncome <= 0.0) return 0.0;

  // Personal Allowance taper: £1 lost for every £2 over £100,000
  const personalAllowance = Math.max(0.0, PERSONAL_ALLOWANCE - Math.max(0.0, taxableIncome - 100000.0) / 2.0);
  let remainingIncome = Math.max(0.0, taxableIncome - personalAllowance);
  let tax = 0.0;

  // Basic rate band: £37,700 at 20%
  if (remainingIncome > 0.0) {
    const taxableAtBasic = Math.min(remainingIncome, BASIC_RATE_THRESHOLD);
    tax += taxableAtBasic * BASIC_RATE;
    remainingIncome -= taxableAtBasic;
  }

  // Higher rate band: up to £125,140 at 40%
  if (remainingIncome > 0.0 && taxableIncome <= HIGHER_RATE_THRESHOLD) {
    tax += remainingIncome * HIGHER_RATE;
    remainingIncome = 0.0;
  } else if (remainingIncome > 0.0) {
    const higherRateBandSize = Math.max(0.0, HIGHER_RATE_THRESHOLD - (personalAllowance + BASIC_RATE_THRESHOLD));
    const taxableAtHigher = Math.min(remainingIncome, higherRateBandSize);
    tax += taxableAtHigher * HIGHER_RATE;
    remainingIncome -= taxableAtHigher;

    // Additional rate band: > £125,140 at 45%
    if (remainingIncome > 0.0) {
      tax += remainingIncome * ADDITIONAL_RATE;
    }
  }

  return tax;
}

export interface PensionWithdrawalResult {
  grossWithdrawal: number;
  taxFreeAmount: number;
  taxableAmount: number;
  taxPaid: number;
  netReceived: number;
}

/**
 * Exact binary search solver to determine the gross pension withdrawal needed to deliver netNeeded cash.
 */
export function calculatePensionWithdrawal(
  potBalance: number,
  netNeeded: number,
  currentTaxableIncome: number,
  remainingLSA: number,
  isUpFront: boolean,
  maxTaxableAmount: number = Number.MAX_VALUE
): PensionWithdrawalResult {
  if (potBalance <= 0.0 || netNeeded <= 0.0 || maxTaxableAmount <= 0.0) {
    return { grossWithdrawal: 0, taxFreeAmount: 0, taxableAmount: 0, taxPaid: 0, netReceived: 0 };
  }

  function evaluate(gross: number): PensionWithdrawalResult {
    const tf = isUpFront ? 0.0 : Math.min(gross * 0.25, remainingLSA);
    const taxable = gross - tf;
    const taxBefore = calculateIncomeTax(currentTaxableIncome);
    const taxAfter = calculateIncomeTax(currentTaxableIncome + taxable);
    const taxPaid = Math.max(0.0, taxAfter - taxBefore);
    const net = gross - taxPaid;
    return { grossWithdrawal: gross, taxFreeAmount: tf, taxableAmount: taxable, taxPaid, netReceived: net };
  }

  const maxGrossAllowed = maxTaxableAmount < Number.MAX_VALUE
    ? (isUpFront
        ? maxTaxableAmount
        : ((maxTaxableAmount / 0.75 * 0.25 <= remainingLSA)
            ? maxTaxableAmount / 0.75
            : maxTaxableAmount + remainingLSA))
    : potBalance;

  const effectiveMaxGross = Math.min(potBalance, maxGrossAllowed);
  if (effectiveMaxGross <= 0.0) {
    return { grossWithdrawal: 0, taxFreeAmount: 0, taxableAmount: 0, taxPaid: 0, netReceived: 0 };
  }

  const maxRes = evaluate(effectiveMaxGross);
  if (maxRes.netReceived <= netNeeded) {
    return maxRes;
  }

  let low = 0.0;
  let high = effectiveMaxGross;
  for (let i = 0; i < 32; i++) {
    const mid = (low + high) / 2.0;
    const res = evaluate(mid);
    if (res.netReceived < netNeeded) {
      low = mid;
    } else {
      high = mid;
    }
  }

  return evaluate(high);
}

/**
 * Exact solver for General Investment Account (GIA) withdrawals.
 */
export const CGT_ANNUAL_EXEMPTION = 3000.0; // £3,000 UK Capital Gains Tax annual exemption per person

/**
 * Capital withdrawals from a GIA are NOT subject to income tax.
 * You are only taxed on the annual growth in the GIA (with £3k annual tax-free allowance).
 */
export function calculateGiaWithdrawal(
  potBalance: number,
  netNeeded: number,
  _currentTaxableIncome: number = 0
): { grossWithdrawal: number; taxPaid: number; netReceived: number } {
  const drawn = Math.max(0.0, Math.min(potBalance, netNeeded));
  return { grossWithdrawal: drawn, taxPaid: 0.0, netReceived: drawn };
}

/**
 * Calculates UK Capital Gains Tax on General Investment Account (GIA) annual growth.
 * Each individual has a £3,000 annual tax-free capital gains exemption.
 * Growth within the £3,000 allowance is 100% tax-free (£0 tax).
 * Gains above £3,000 are taxed at standard UK CGT rates:
 * - 10% for gains within the basic rate band (up to £50,270)
 * - 20% for gains in the higher / additional rate band
 */
export function calculateTaxOnGiaGrowth(annualGrowth: number, otherTaxableIncome: number): number {
  if (annualGrowth <= CGT_ANNUAL_EXEMPTION) return 0.0;
  const taxableGain = annualGrowth - CGT_ANNUAL_EXEMPTION;

  const basicRateLimit = PERSONAL_ALLOWANCE + BASIC_RATE_THRESHOLD; // £50,270
  const unusedBasicBand = Math.max(0.0, basicRateLimit - otherTaxableIncome);

  const gainInBasicBand = Math.min(taxableGain, unusedBasicBand);
  const gainInHigherBand = Math.max(0.0, taxableGain - gainInBasicBand);

  return (gainInBasicBand * 0.10) + (gainInHigherBand * 0.20);
}

/**
 * Calculates UK Tax on Savings Interest from cash / non-exempt savings.
 */
export function calculateTaxOnSavings(savingsInterest: number, otherTaxableIncome: number): number {
  if (savingsInterest <= 0.0) return 0.0;

  const personalAllowance = Math.max(0.0, PERSONAL_ALLOWANCE - Math.max(0.0, (otherTaxableIncome + savingsInterest) - 100000.0) / 2.0);
  const unusedPA = Math.max(0.0, personalAllowance - otherTaxableIncome);

  const totalIncome = otherTaxableIncome + savingsInterest;
  const psa = totalIncome <= (PERSONAL_ALLOWANCE + BASIC_RATE_THRESHOLD)
    ? 1000.0
    : (totalIncome <= HIGHER_RATE_THRESHOLD ? 500.0 : 0.0);

  const taxableSavings = Math.max(0.0, savingsInterest - unusedPA - psa);
  if (taxableSavings <= 0.0) return 0.0;

  const taxBefore = calculateIncomeTax(otherTaxableIncome + unusedPA);
  const taxAfter = calculateIncomeTax(otherTaxableIncome + unusedPA + taxableSavings);
  return Math.max(0.0, taxAfter - taxBefore);
}

/**
 * Performs annual Bed & ISA transfer from GIA to ISAs (£20k/person/year, £40k/couple).
 * Returns mapping of pot ID changes for tracking.
 */
function performBedAndIsaTransfer(
  savings: Account[],
  projectionType: ProjectionType,
  person1: Person,
  person2: Person,
  isRetired1: boolean,
  isRetired2: boolean,
  assumptions: InvestmentAssumptions,
  inflationFactor: number
): { giaTransfersOut: Map<string, number>; isaTransfersIn: Map<string, number> } {
  const giaTransfersOut = new Map<string, number>();
  const isaTransfersIn = new Map<string, number>();

  const totalGia = savings
    .filter(s => s.type === 'GENERAL_INVESTMENT' && s.balance > 0.0)
    .reduce((sum, s) => sum + s.balance, 0);

  if (totalGia <= 0.0) return { giaTransfersOut, isaTransfersIn };

  const usedIsa1 = savings
    .filter(s => s.personId !== 'person-2' && s.type === 'ISA')
    .reduce((sum, s) => sum + (!isRetired1 ? s.monthlyContribution * 12.0 * inflationFactor : 0.0), 0);
  const maxIsa1 = projectionType === 'INDIVIDUAL_PERSON2' ? 0.0 : Math.max(0.0, ANNUAL_ISA_ALLOWANCE - usedIsa1);

  const usedIsa2 = savings
    .filter(s => s.personId === 'person-2' && s.type === 'ISA')
    .reduce((sum, s) => sum + (!isRetired2 ? s.monthlyContribution * 12.0 * inflationFactor : 0.0), 0);
  const maxIsa2 = projectionType === 'INDIVIDUAL_PERSON1' ? 0.0 : Math.max(0.0, ANNUAL_ISA_ALLOWANCE - usedIsa2);

  const maxTransferTotal = maxIsa1 + maxIsa2;
  const actualTransferTotal = Math.min(totalGia, maxTransferTotal);
  if (actualTransferTotal <= 0.0) return { giaTransfersOut, isaTransfersIn };

  let t1 = 0;
  let t2 = 0;
  if (projectionType === 'COUPLE') {
    if (actualTransferTotal >= (maxIsa1 + maxIsa2)) {
      t1 = maxIsa1;
      t2 = maxIsa2;
    } else {
      const half = actualTransferTotal / 2.0;
      t1 = Math.min(maxIsa1, half);
      t2 = Math.min(maxIsa2, actualTransferTotal - t1);
    }
  } else if (projectionType === 'INDIVIDUAL_PERSON1') {
    t1 = actualTransferTotal;
  } else {
    t2 = actualTransferTotal;
  }

  // Deduct from GIAs
  let remDeduct = actualTransferTotal;
  for (const gia of savings.filter(s => s.type === 'GENERAL_INVESTMENT')) {
    if (remDeduct <= 0.0) break;
    const d = Math.min(gia.balance, remDeduct);
    gia.balance -= d;
    giaTransfersOut.set(gia.id, (giaTransfersOut.get(gia.id) || 0) + d);
    remDeduct -= d;
  }

  // Credit to ISAs
  if (t1 > 0.0) {
    const isa1 = savings.find(s => s.personId !== 'person-2' && s.type === 'ISA');
    if (isa1) {
      isa1.balance += t1;
      isaTransfersIn.set(isa1.id, (isaTransfersIn.get(isa1.id) || 0) + t1);
    } else {
      const newIsa: Account = {
        id: `isa-bed-1-${Date.now()}`,
        name: `${person1.name}'s ISA (Bed & ISA)`,
        type: 'ISA',
        institution: 'HOSTED',
        balance: t1,
        monthlyContribution: 0,
        employerContribution: 0,
        interestRate: assumptions.cashReturn * 100,
        annualManagementCharge: 0,
        personId: 'person-1',
        isIncluded: true,
        payoutAge: 65,
        isInflationLinked: true
      };
      savings.push(newIsa);
      isaTransfersIn.set(newIsa.id, t1);
    }
  }

  if (t2 > 0.0) {
    const isa2 = savings.find(s => s.personId === 'person-2' && s.type === 'ISA');
    if (isa2) {
      isa2.balance += t2;
      isaTransfersIn.set(isa2.id, (isaTransfersIn.get(isa2.id) || 0) + t2);
    } else {
      const newIsa: Account = {
        id: `isa-bed-2-${Date.now()}`,
        name: `${person2.name}'s ISA (Bed & ISA)`,
        type: 'ISA',
        institution: 'HOSTED',
        balance: t2,
        monthlyContribution: 0,
        employerContribution: 0,
        interestRate: assumptions.cashReturn * 100,
        annualManagementCharge: 0,
        personId: 'person-2',
        isIncluded: true,
        payoutAge: 65,
        isInflationLinked: true
      };
      savings.push(newIsa);
      isaTransfersIn.set(newIsa.id, t2);
    }
  }

  return { giaTransfersOut, isaTransfersIn };
}

export interface PotGrowthCalculation {
  grossRate: number;
  netRate: number;
  amcRate: number;
}

/**
 * Calculates growth rate adhering strictly to each pot's individual growth / interest rate.
 * If user explicitly set 0% (e.g. Premium Bonds or cash), this 0% is strictly honored.
 * Falls back to macro asset allocation only if pot.interestRate is undefined / null / NaN.
 */
export function getPotGrowthRate(
  pot: Account,
  assumptions: InvestmentAssumptions,
  portfolioGrowthRate: number,
  customEquityReturn?: number,
  customCashReturn?: number
): PotGrowthCalculation {
  const hasExplicitRate = pot.interestRate !== undefined &&
                          pot.interestRate !== null &&
                          !isNaN(pot.interestRate);

  let grossRate: number;
  if (hasExplicitRate) {
    const baseRate = Number(pot.interestRate) / 100.0;
    if (customEquityReturn !== undefined && baseRate > 0.0 && pot.type !== 'CURRENT') {
      const equityShock = customEquityReturn - assumptions.equityReturn;
      grossRate = Math.max(-0.50, baseRate + equityShock);
    } else {
      grossRate = baseRate;
    }
  } else {
    grossRate = pot.type === 'CURRENT'
      ? (customCashReturn !== undefined ? customCashReturn : assumptions.cashReturn)
      : portfolioGrowthRate;
  }

  const amcRate = (pot.annualManagementCharge !== undefined &&
                   pot.annualManagementCharge !== null &&
                   !isNaN(pot.annualManagementCharge))
    ? Number(pot.annualManagementCharge) / 100.0
    : 0.0;

  const appliesAmc = pot.type === 'PENSION' || pot.type === 'ISA' || pot.type === 'GENERAL_INVESTMENT';
  const effectiveAmc = appliesAmc ? amcRate : 0.0;
  const netRate = grossRate - effectiveAmc;

  return { grossRate, netRate, amcRate: effectiveAmc };
}

/**
 * Complete Retirement Projection Engine with comprehensive per-pot tracking!
 */
export function calculateProjections(
  accounts: Account[],
  assumptions: InvestmentAssumptions,
  preferences: DrawdownPreferences,
  person1: Person,
  person2: Person,
  retirementAge1: number,
  retirementAge2: number,
  projectionType: ProjectionType = 'COUPLE',
  stochasticSchedule?: StochasticYearSample[]
): RetirementProjection {
  const currentYear = new Date().getFullYear();
  const currentAge1 = currentYear - person1.birthYear;
  const currentAge2 = currentYear - person2.birthYear;
  const endAge = preferences.endAge;

  const minPensionAge1 = person1.birthYear < 1973 ? 55 : 57;
  const minPensionAge2 = person2.birthYear < 1973 ? 55 : 57;

  const spAge1 = getStatePensionAge(person1.birthYear);
  const spAge2 = getStatePensionAge(person2.birthYear);

  const currentAgeActive = projectionType === 'INDIVIDUAL_PERSON2' ? currentAge2 : currentAge1;

  const totalAssets = accounts.reduce((sum, a) => sum + a.balance, 0);
  if (totalAssets <= 0.0) {
    const emptyResults: ProjectionResult[] = [];
    const count = Math.max(1, endAge - currentAgeActive + 1);
    for (let i = 0; i < count; i++) {
      emptyResults.push({
        age: currentAgeActive + i,
        age1: currentAge1 + i,
        age2: currentAge2 + i,
        year: currentYear + i,
        totalPensionValue: 0,
        totalSavings: 0,
        totalNetWorth: 0,
        annualIncome: 0,
        grossIncome: 0,
        netIncome: 0,
        tax: 0,
        tax1: 0,
        tax2: 0,
        canRetire: false,
        withdrawals: [],
        potBreakdowns: [],
        totalGrowthThisYear: 0,
        totalTaxThisYear: 0
      });
    }
    return {
      results: emptyResults,
      retirementAge: retirementAge1,
      feasible: false,
      totalTaxPaid: 0,
      fundsLastUntilAge: currentAgeActive,
      peakPensionPot: 0,
      totalContributionsAccumulated: 0
    };
  }

  // Clone active accounts based on projection type
  const activeAccounts = accounts
    .filter(a => a.isIncluded)
    .filter(a => {
      if (projectionType === 'INDIVIDUAL_PERSON1') return a.personId !== 'person-2';
      if (projectionType === 'INDIVIDUAL_PERSON2') return a.personId === 'person-2';
      return true;
    })
    .map(a => ({ ...a }));

  const dcPensions = activeAccounts.filter(a => a.type === 'PENSION');
  const dbPensions = activeAccounts.filter(a => a.type === 'FINAL_SALARY');
  const savings = activeAccounts.filter(a => a.type === 'CURRENT' || a.type === 'ISA' || a.type === 'GENERAL_INVESTMENT');

  const portfolioGrowthRate = (
    assumptions.equityReturn * assumptions.equityAllocation +
    assumptions.bondReturn * assumptions.bondAllocation +
    assumptions.cashReturn * assumptions.cashAllocation
  );

  let remainingLSA1 = MAX_TAX_FREE_LUMP_SUM;
  let remainingLSA2 = MAX_TAX_FREE_LUMP_SUM;
  let lumpSumTaken1 = false;
  let lumpSumTaken2 = false;

  let totalTaxPaid = 0.0;
  let totalContributionsAccumulated = 0.0;
  let peakPensionPot = dcPensions.reduce((sum, p) => sum + p.balance, 0);
  let retirementFeasible = true;
  let fundsLastUntilAge = endAge;

  const results: ProjectionResult[] = [];
  const yearsToProject = Math.max(1, endAge - currentAgeActive + 1);
  let cumulativeInflationFactor = 1.0;

  for (let yearOffset = 0; yearOffset < yearsToProject; yearOffset++) {
    const year = currentYear + yearOffset;
    const age1 = currentAge1 + yearOffset;
    const age2 = currentAge2 + yearOffset;
    const ageActive = currentAgeActive + yearOffset;

    const isRetired1 = age1 >= retirementAge1;
    const isRetired2 = age2 >= retirementAge2;

    const inRetirement = projectionType === 'COUPLE'
      ? (isRetired1 || isRetired2)
      : (projectionType === 'INDIVIDUAL_PERSON2' ? isRetired2 : isRetired1);

    const sample = stochasticSchedule ? stochasticSchedule[yearOffset] : null;
    const currentEquityReturn = sample ? sample.equityReturn : assumptions.equityReturn;
    const currentBondReturn = sample ? sample.bondReturn : assumptions.bondReturn;
    const currentCashReturn = sample ? sample.cashReturn : assumptions.cashReturn;
    const currentInflationRate = sample ? sample.inflationRate : assumptions.inflationRate;

    const portfolioGrowthRate = (
      currentEquityReturn * assumptions.equityAllocation +
      currentBondReturn * assumptions.bondAllocation +
      currentCashReturn * assumptions.cashAllocation
    );

    if (sample) {
      if (yearOffset > 0 && preferences.inflationAdjusted) {
        cumulativeInflationFactor *= (1.0 + currentInflationRate);
      }
    } else {
      cumulativeInflationFactor = preferences.inflationAdjusted
        ? Math.pow(1.0 + assumptions.inflationRate, yearOffset)
        : 1.0;
    }
    const inflationFactor = cumulativeInflationFactor;

    // Snapshot start-of-year balances for per-pot breakdown
    const potStartBalances = new Map<string, number>();
    [...dcPensions, ...dbPensions, ...savings].forEach(p => {
      potStartBalances.set(p.id, p.balance);
    });

    const potContributions = new Map<string, number>();
    const potTransfers = new Map<string, number>();
    const potWithdrawals = new Map<string, { gross: number; tf: number; taxable: number; tax: number }>();

    // 1. Accumulation Contributions
    if (!isRetired1) {
      dcPensions.filter(p => p.personId !== 'person-2').forEach(p => {
        const annualEmployee = p.monthlyContribution * 12.0 * inflationFactor;
        const annualEmployer = p.employerContribution * 12.0 * inflationFactor;
        const total = annualEmployee + annualEmployer;
        p.balance += total;
        potContributions.set(p.id, (potContributions.get(p.id) || 0) + total);
        totalContributionsAccumulated += total;
      });

      savings.filter(s => s.personId !== 'person-2').forEach(s => {
        const annual = s.monthlyContribution * 12.0 * inflationFactor;
        s.balance += annual;
        potContributions.set(s.id, (potContributions.get(s.id) || 0) + annual);
        totalContributionsAccumulated += annual;
      });
    }

    if (!isRetired2) {
      dcPensions.filter(p => p.personId === 'person-2').forEach(p => {
        const annualEmployee = p.monthlyContribution * 12.0 * inflationFactor;
        const annualEmployer = p.employerContribution * 12.0 * inflationFactor;
        const total = annualEmployee + annualEmployer;
        p.balance += total;
        potContributions.set(p.id, (potContributions.get(p.id) || 0) + total);
        totalContributionsAccumulated += total;
      });

      savings.filter(s => s.personId === 'person-2').forEach(s => {
        const annual = s.monthlyContribution * 12.0 * inflationFactor;
        s.balance += annual;
        potContributions.set(s.id, (potContributions.get(s.id) || 0) + annual);
        totalContributionsAccumulated += annual;
      });
    }

    const currentTotalPension = dcPensions.reduce((sum, p) => sum + p.balance, 0);
    if (currentTotalPension > peakPensionPot) {
      peakPensionPot = currentTotalPension;
    }

    // 2. Upfront Lump Sum logic (per person, once each person is retired and eligible)
    if (preferences.lumpSumOption === 'UP_FRONT') {
      let lumpSumDeposit1 = 0.0;
      let lumpSumDeposit2 = 0.0;

      // Person 1 Upfront 25% lump sum
      if (projectionType !== 'INDIVIDUAL_PERSON2' && isRetired1 && age1 >= minPensionAge1 && !lumpSumTaken1) {
        lumpSumTaken1 = true;
        const person1Pensions = dcPensions.filter(p => p.personId !== 'person-2');
        for (const pension of person1Pensions) {
          if (remainingLSA1 <= 0.0) break;
          const chunk = Math.min(pension.balance * 0.25, remainingLSA1);
          pension.balance -= chunk;
          remainingLSA1 -= chunk;
          lumpSumDeposit1 += chunk;

          const currentW = potWithdrawals.get(pension.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
          potWithdrawals.set(pension.id, {
            ...currentW,
            gross: currentW.gross + chunk,
            tf: currentW.tf + chunk
          });
        }
      }

      // Person 2 Upfront 25% lump sum
      if (projectionType !== 'INDIVIDUAL_PERSON1' && isRetired2 && age2 >= minPensionAge2 && !lumpSumTaken2) {
        lumpSumTaken2 = true;
        const person2Pensions = dcPensions.filter(p => p.personId === 'person-2');
        for (const pension of person2Pensions) {
          if (remainingLSA2 <= 0.0) break;
          const chunk = Math.min(pension.balance * 0.25, remainingLSA2);
          pension.balance -= chunk;
          remainingLSA2 -= chunk;
          lumpSumDeposit2 += chunk;

          const currentW = potWithdrawals.get(pension.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
          potWithdrawals.set(pension.id, {
            ...currentW,
            gross: currentW.gross + chunk,
            tf: currentW.tf + chunk
          });
        }
      }

      // Deposit lump sum into GIA (taxable savings, per UK tax rules)
      if (lumpSumDeposit1 > 0.0) {
        const person1Gia = savings.find(s => s.personId !== 'person-2' && s.type === 'GENERAL_INVESTMENT');
        if (person1Gia) {
          person1Gia.balance += lumpSumDeposit1;
          potTransfers.set(person1Gia.id, (potTransfers.get(person1Gia.id) || 0) + lumpSumDeposit1);
        } else {
          const newGia: Account = {
            id: `gia-lump-1-${year}`,
            name: `${person1.name}'s Lump Sum (GIA)`,
            type: 'GENERAL_INVESTMENT',
            institution: 'HOSTED',
            balance: lumpSumDeposit1,
            monthlyContribution: 0,
            employerContribution: 0,
            interestRate: assumptions.equityReturn * 100,
            annualManagementCharge: 0,
            personId: 'person-1',
            isIncluded: true,
            payoutAge: 65,
            isInflationLinked: true
          };
          savings.push(newGia);
          potStartBalances.set(newGia.id, 0);
          potTransfers.set(newGia.id, lumpSumDeposit1);
        }
      }

      if (lumpSumDeposit2 > 0.0) {
        const person2Gia = savings.find(s => s.personId === 'person-2' && s.type === 'GENERAL_INVESTMENT');
        if (person2Gia) {
          person2Gia.balance += lumpSumDeposit2;
          potTransfers.set(person2Gia.id, (potTransfers.get(person2Gia.id) || 0) + lumpSumDeposit2);
        } else {
          const newGia: Account = {
            id: `gia-lump-2-${year}`,
            name: `${person2.name}'s Lump Sum (GIA)`,
            type: 'GENERAL_INVESTMENT',
            institution: 'HOSTED',
            balance: lumpSumDeposit2,
            monthlyContribution: 0,
            employerContribution: 0,
            interestRate: assumptions.equityReturn * 100,
            annualManagementCharge: 0,
            personId: 'person-2',
            isIncluded: true,
            payoutAge: 65,
            isInflationLinked: true
          };
          savings.push(newGia);
          potStartBalances.set(newGia.id, 0);
          potTransfers.set(newGia.id, lumpSumDeposit2);
        }
      }
    }

    if (inRetirement) {
      // 3. Retirement Drawdown Phase
      const stepDownFactor = ageActive >= preferences.stepDownAge
        ? (1.0 - preferences.stepDownPercentage / 100.0)
        : 1.0;
      const baseTarget = preferences.targetAnnualIncome * stepDownFactor;
      const targetAnnualIncome = preferences.inflationAdjusted
        ? baseTarget * inflationFactor
        : baseTarget;

      let netIncomeGathered = 0.0;
      let grossIncomeGathered = 0.0;
      let taxableIncome1 = 0.0;
      let taxableIncome2 = 0.0;
      let tax1 = 0.0;
      let tax2 = 0.0;
      const yearWithdrawals: WithdrawalDetail[] = [];

      // Guaranteed Income 1: State Pension
      if (projectionType !== 'INDIVIDUAL_PERSON2' && age1 >= spAge1) {
        const spGross = calculateStatePension(person1.qualifyingYears ?? 35) * inflationFactor;
        taxableIncome1 += spGross;
        const newTax = calculateIncomeTax(taxableIncome1);
        const incrementalTax = Math.max(0.0, newTax - tax1);
        tax1 = newTax;
        const spNet = spGross - incrementalTax;
        netIncomeGathered += spNet;
        grossIncomeGathered += spGross;
        yearWithdrawals.push({
          potName: `State Pension (${person1.name})`,
          ownerName: person1.name,
          amountDrawn: spGross,
          taxFreeAmount: 0,
          taxableAmount: spGross,
          taxPaid: incrementalTax
        });
      }

      if (projectionType !== 'INDIVIDUAL_PERSON1' && age2 >= spAge2) {
        const spGross = calculateStatePension(person2.qualifyingYears ?? 35) * inflationFactor;
        taxableIncome2 += spGross;
        const newTax = calculateIncomeTax(taxableIncome2);
        const incrementalTax = Math.max(0.0, newTax - tax2);
        tax2 = newTax;
        const spNet = spGross - incrementalTax;
        netIncomeGathered += spNet;
        grossIncomeGathered += spGross;
        yearWithdrawals.push({
          potName: `State Pension (${person2.name})`,
          ownerName: person2.name,
          amountDrawn: spGross,
          taxFreeAmount: 0,
          taxableAmount: spGross,
          taxPaid: incrementalTax
        });
      }

      // Guaranteed Income 2: Final Salary (DB) Pensions
      for (const db of dbPensions) {
        const isPerson2 = db.personId === 'person-2';
        const ownerAge = isPerson2 ? age2 : age1;
        const ownerName = isPerson2 ? person2.name : person1.name;

        if (ownerAge >= db.payoutAge) {
          const annualPayment = db.isInflationLinked ? db.balance * inflationFactor : db.balance;
          if (isPerson2) {
            taxableIncome2 += annualPayment;
            const newTax = calculateIncomeTax(taxableIncome2);
            const incTax = Math.max(0.0, newTax - tax2);
            tax2 = newTax;
            netIncomeGathered += (annualPayment - incTax);
            grossIncomeGathered += annualPayment;
            yearWithdrawals.push({
              potId: db.id,
              potName: db.name,
              ownerName,
              amountDrawn: annualPayment,
              taxFreeAmount: 0,
              taxableAmount: annualPayment,
              taxPaid: incTax
            });
            const prevW = potWithdrawals.get(db.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
            potWithdrawals.set(db.id, {
              gross: prevW.gross + annualPayment,
              tf: 0,
              taxable: prevW.taxable + annualPayment,
              tax: prevW.tax + incTax
            });
          } else {
            taxableIncome1 += annualPayment;
            const newTax = calculateIncomeTax(taxableIncome1);
            const incTax = Math.max(0.0, newTax - tax1);
            tax1 = newTax;
            netIncomeGathered += (annualPayment - incTax);
            grossIncomeGathered += annualPayment;
            yearWithdrawals.push({
              potId: db.id,
              potName: db.name,
              ownerName,
              amountDrawn: annualPayment,
              taxFreeAmount: 0,
              taxableAmount: annualPayment,
              taxPaid: incTax
            });
            const prevW = potWithdrawals.get(db.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
            potWithdrawals.set(db.id, {
              gross: prevW.gross + annualPayment,
              tf: 0,
              taxable: prevW.taxable + annualPayment,
              tax: prevW.tax + incTax
            });
          }
        }
      }

      // Drawdown Strategy Execution
      const isUpFront = preferences.lumpSumOption === 'UP_FRONT';

      if (preferences.strategy === 'TAX_MINIMIZED') {
        // TAX MINIMIZED:
        // Step 1: Harvest 0% Tax-Free Personal Allowance for both individuals.
        // Person 2's pension is smoothed to age 67 so she/he gets all years of tax-free Personal Allowance.
        const paLeft1 = Math.max(0.0, PERSONAL_ALLOWANCE - taxableIncome1);
        const paLeft2 = Math.max(0.0, PERSONAL_ALLOWANCE - taxableIncome2);

        // 1a. Harvest Person 1 pensions up to Personal Allowance (£0 tax)
        if (projectionType !== 'INDIVIDUAL_PERSON2' && isRetired1 && paLeft1 > 0.0) {
          let remPa1 = paLeft1;
          for (const pension of dcPensions.filter(p => p.personId !== 'person-2' && p.balance > 0.0)) {
            if (remPa1 <= 0.0 || netIncomeGathered >= targetAnnualIncome) break;
            const neededNet = targetAnnualIncome - netIncomeGathered;
            const res = calculatePensionWithdrawal(pension.balance, neededNet, taxableIncome1, remainingLSA1, isUpFront, remPa1);
            if (res.grossWithdrawal > 0.0) {
              pension.balance -= res.grossWithdrawal;
              remainingLSA1 = Math.max(0.0, remainingLSA1 - res.taxFreeAmount);
              taxableIncome1 += res.taxableAmount;
              tax1 += res.taxPaid;
              netIncomeGathered += res.netReceived;
              grossIncomeGathered += res.grossWithdrawal;
              remPa1 = Math.max(0.0, remPa1 - res.taxableAmount);
              yearWithdrawals.push({
                potId: pension.id,
                potName: pension.name,
                ownerName: person1.name,
                amountDrawn: res.grossWithdrawal,
                taxFreeAmount: res.taxFreeAmount,
                taxableAmount: res.taxableAmount,
                taxPaid: res.taxPaid
              });
              const prevW = potWithdrawals.get(pension.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
              potWithdrawals.set(pension.id, {
                gross: prevW.gross + res.grossWithdrawal,
                tf: prevW.tf + res.taxFreeAmount,
                taxable: prevW.taxable + res.taxableAmount,
                tax: prevW.tax + res.taxPaid
              });
            }
          }
        }

        // 1b. Harvest Person 2 pensions smoothed to age 67 under Personal Allowance (£0 tax)
        if (projectionType !== 'INDIVIDUAL_PERSON1' && isRetired2 && paLeft2 > 0.0) {
          const yearsUntilSpa2 = Math.max(1, spAge2 - age2);
          const person2TotalPension = dcPensions
            .filter(p => p.personId === 'person-2' && p.balance > 0.0)
            .reduce((sum, p) => sum + p.balance, 0);

          // Spread over remaining years to SPA (up to 10 years of basic income allowance)
          const spreadGross = age2 < spAge2 && yearsUntilSpa2 > 1
            ? person2TotalPension / yearsUntilSpa2
            : person2TotalPension;
          const maxTaxFreeGross = isUpFront ? paLeft2 : (paLeft2 / 0.75);
          const targetGross = Math.min(maxTaxFreeGross, spreadGross);
          const maxTaxableForPerson2 = isUpFront ? targetGross : Math.min(paLeft2, targetGross * 0.75);

          let remPa2 = Math.max(0.0, maxTaxableForPerson2);
          for (const pension of dcPensions.filter(p => p.personId === 'person-2' && p.balance > 0.0)) {
            if (remPa2 <= 0.0 || netIncomeGathered >= targetAnnualIncome) break;
            const neededNet = targetAnnualIncome - netIncomeGathered;
            const res = calculatePensionWithdrawal(pension.balance, neededNet, taxableIncome2, remainingLSA2, isUpFront, remPa2);
            if (res.grossWithdrawal > 0.0) {
              pension.balance -= res.grossWithdrawal;
              remainingLSA2 = Math.max(0.0, remainingLSA2 - res.taxFreeAmount);
              taxableIncome2 += res.taxableAmount;
              tax2 += res.taxPaid;
              netIncomeGathered += res.netReceived;
              grossIncomeGathered += res.grossWithdrawal;
              remPa2 = Math.max(0.0, remPa2 - res.taxableAmount);
              yearWithdrawals.push({
                potId: pension.id,
                potName: pension.name,
                ownerName: person2.name,
                amountDrawn: res.grossWithdrawal,
                taxFreeAmount: res.taxFreeAmount,
                taxableAmount: res.taxableAmount,
                taxPaid: res.taxPaid
              });
              const prevW = potWithdrawals.get(pension.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
              potWithdrawals.set(pension.id, {
                gross: prevW.gross + res.grossWithdrawal,
                tf: prevW.tf + res.taxFreeAmount,
                taxable: prevW.taxable + res.taxableAmount,
                tax: prevW.tax + res.taxPaid
              });
            }
          }
        }

        // 2. Draw down OTHER SAVINGS (Current and GIA) BEFORE ISAs!
        if (netIncomeGathered < targetAnnualIncome) {
          const nonIsaSavings = savings.filter(s => (s.type === 'CURRENT' || s.type === 'GENERAL_INVESTMENT') && s.balance > 0.0);
          for (const pot of nonIsaSavings) {
            if (netIncomeGathered >= targetAnnualIncome) break;
            const remainingNeeded = targetAnnualIncome - netIncomeGathered;
            const isPerson2 = pot.personId === 'person-2';
            const ownerName = isPerson2 ? person2.name : person1.name;

            const drawn = Math.min(pot.balance, remainingNeeded);
            if (drawn > 0.0) {
              pot.balance -= drawn;
              netIncomeGathered += drawn;
              grossIncomeGathered += drawn;
              yearWithdrawals.push({
                potId: pot.id,
                potName: pot.name,
                ownerName,
                amountDrawn: drawn,
                taxFreeAmount: drawn,
                taxableAmount: 0,
                taxPaid: 0
              });
              const prevW = potWithdrawals.get(pot.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
              potWithdrawals.set(pot.id, {
                gross: prevW.gross + drawn,
                tf: prevW.tf + drawn,
                taxable: 0,
                tax: 0
              });
            }
          }
        }

        // 3. Draw down DC pensions up to Basic Rate Threshold (£50,270 taxable income) BEFORE ISAs!
        // This utilizes the 20% basic rate band in early retirement (before DB pensions & State Pension kick in),
        // burning down taxable DC pensions at 20% (effective 15% net with 25% tax-free lump sum)
        // rather than leaving them to be taxed at 40% later when DB pensions absorb the basic rate band!
        // This crucially PRESERVES ISAs so they can compound tax-free and shield against 40% tax in later years.
        if (netIncomeGathered < targetAnnualIncome) {
          const basicRateLimit = PERSONAL_ALLOWANCE + BASIC_RATE_THRESHOLD; // £50,270
          const eligiblePensions = dcPensions
            .filter(p => {
              const isPerson2 = p.personId === 'person-2';
              const ownerAge = isPerson2 ? age2 : age1;
              const minAge = isPerson2 ? minPensionAge2 : minPensionAge1;
              return ownerAge >= minAge && p.balance > 0.0;
            })
            .sort((a, b) => {
              // Draw Person 1 first to protect Person 2's Personal Allowance smoothing to age 67
              if (a.personId !== 'person-2' && b.personId === 'person-2') return -1;
              if (a.personId === 'person-2' && b.personId !== 'person-2') return 1;
              return 0;
            });

          for (const pension of eligiblePensions) {
            if (netIncomeGathered >= targetAnnualIncome) break;
            const isPerson2 = pension.personId === 'person-2';

            // In TAX_MINIMIZED, Person 2's pension is protected to be smoothed under PA to age 67 (£0 tax).
            // Only Person 1 draws up to the Basic Rate Threshold in couple projections (unless Person 2 is an individual projection).
            if (isPerson2 && projectionType === 'COUPLE' && age2 < spAge2) {
              continue;
            }

            const currTaxable = isPerson2 ? taxableIncome2 : taxableIncome1;
            const basicCap = Math.max(0.0, basicRateLimit - currTaxable);
            if (basicCap <= 0.0) continue;

            const remainingNeeded = targetAnnualIncome - netIncomeGathered;
            const remLsa = isPerson2 ? remainingLSA2 : remainingLSA1;
            const ownerName = isPerson2 ? person2.name : person1.name;

            const res = calculatePensionWithdrawal(pension.balance, remainingNeeded, currTaxable, remLsa, isUpFront, basicCap);
            if (res.grossWithdrawal > 0.0) {
              pension.balance -= res.grossWithdrawal;
              if (isPerson2) {
                remainingLSA2 = Math.max(0.0, remainingLSA2 - res.taxFreeAmount);
                taxableIncome2 += res.taxableAmount;
                tax2 += res.taxPaid;
              } else {
                remainingLSA1 = Math.max(0.0, remainingLSA1 - res.taxFreeAmount);
                taxableIncome1 += res.taxableAmount;
                tax1 += res.taxPaid;
              }
              netIncomeGathered += res.netReceived;
              grossIncomeGathered += res.grossWithdrawal;
              yearWithdrawals.push({
                potId: pension.id,
                potName: pension.name,
                ownerName,
                amountDrawn: res.grossWithdrawal,
                taxFreeAmount: res.taxFreeAmount,
                taxableAmount: res.taxableAmount,
                taxPaid: res.taxPaid
              });
              const prevW = potWithdrawals.get(pension.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
              potWithdrawals.set(pension.id, {
                gross: prevW.gross + res.grossWithdrawal,
                tf: prevW.tf + res.taxFreeAmount,
                taxable: prevW.taxable + res.taxableAmount,
                tax: prevW.tax + res.taxPaid
              });
            }
          }
        }

        // 4. Draw down ISAs NEXT (100% tax-free shelter)
        // Preserved from steps 1-3! Drawn here to avoid crossing into the 40% Higher Rate band,
        // or when DC pensions are depleted, or after DB pensions absorb the basic rate band.
        if (netIncomeGathered < targetAnnualIncome) {
          const isas = savings.filter(s => s.type === 'ISA' && s.balance > 0.0);
          for (const isa of isas) {
            if (netIncomeGathered >= targetAnnualIncome) break;
            const remainingNeeded = targetAnnualIncome - netIncomeGathered;
            const drawn = Math.min(isa.balance, remainingNeeded);
            isa.balance -= drawn;
            netIncomeGathered += drawn;
            grossIncomeGathered += drawn;
            const ownerName = isa.personId === 'person-2' ? person2.name : person1.name;
            yearWithdrawals.push({
              potId: isa.id,
              potName: isa.name,
              ownerName,
              amountDrawn: drawn,
              taxFreeAmount: drawn,
              taxableAmount: 0,
              taxPaid: 0
            });
            const prevW = potWithdrawals.get(isa.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
            potWithdrawals.set(isa.id, {
              gross: prevW.gross + drawn,
              tf: prevW.tf + drawn,
              taxable: 0,
              tax: 0
            });
          }
        }

        // 5. Draw down remaining DC pensions above Basic Rate Threshold (Higher Rate 40%)
        // Only reached as a last resort if ISAs are also exhausted!
        if (netIncomeGathered < targetAnnualIncome) {
          const eligiblePensions = dcPensions
            .filter(p => {
              const isPerson2 = p.personId === 'person-2';
              const ownerAge = isPerson2 ? age2 : age1;
              const minAge = isPerson2 ? minPensionAge2 : minPensionAge1;
              return ownerAge >= minAge && p.balance > 0.0;
            })
            .sort((a, b) => {
              if (a.personId !== 'person-2' && b.personId === 'person-2') return -1;
              if (a.personId === 'person-2' && b.personId !== 'person-2') return 1;
              return 0;
            });

          for (const pension of eligiblePensions) {
            if (netIncomeGathered >= targetAnnualIncome) break;
            const isPerson2 = pension.personId === 'person-2';

            // In TAX_MINIMIZED couple projections, protect Person 2's pension smoothing under PA to age 67 (£0 tax)
            if (isPerson2 && projectionType === 'COUPLE' && age2 < spAge2) {
              continue;
            }

            const remainingNeeded = targetAnnualIncome - netIncomeGathered;
            const currTaxable = isPerson2 ? taxableIncome2 : taxableIncome1;
            const remLsa = isPerson2 ? remainingLSA2 : remainingLSA1;
            const ownerName = isPerson2 ? person2.name : person1.name;

            const res = calculatePensionWithdrawal(pension.balance, remainingNeeded, currTaxable, remLsa, isUpFront);
            if (res.grossWithdrawal > 0.0) {
              pension.balance -= res.grossWithdrawal;
              if (isPerson2) {
                remainingLSA2 = Math.max(0.0, remainingLSA2 - res.taxFreeAmount);
                taxableIncome2 += res.taxableAmount;
                tax2 += res.taxPaid;
              } else {
                remainingLSA1 = Math.max(0.0, remainingLSA1 - res.taxFreeAmount);
                taxableIncome1 += res.taxableAmount;
                tax1 += res.taxPaid;
              }
              netIncomeGathered += res.netReceived;
              grossIncomeGathered += res.grossWithdrawal;
              yearWithdrawals.push({
                potId: pension.id,
                potName: pension.name,
                ownerName,
                amountDrawn: res.grossWithdrawal,
                taxFreeAmount: res.taxFreeAmount,
                taxableAmount: res.taxableAmount,
                taxPaid: res.taxPaid
              });
              const prevW = potWithdrawals.get(pension.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
              potWithdrawals.set(pension.id, {
                gross: prevW.gross + res.grossWithdrawal,
                tf: prevW.tf + res.taxFreeAmount,
                taxable: prevW.taxable + res.taxableAmount,
                tax: prevW.tax + res.taxPaid
              });
            }
          }
        }
      } else {
        // STANDARD STRATEGY:
        // 1. Harvest Personal Allowance from DC pensions (up to £12,570 free)
        const paLeft1 = Math.max(0.0, PERSONAL_ALLOWANCE - taxableIncome1);
        const paLeft2 = Math.max(0.0, PERSONAL_ALLOWANCE - taxableIncome2);

        if (projectionType !== 'INDIVIDUAL_PERSON2' && isRetired1 && paLeft1 > 0.0) {
          let remPa1 = paLeft1;
          for (const pension of dcPensions.filter(p => p.personId !== 'person-2' && p.balance > 0.0)) {
            if (remPa1 <= 0.0 || netIncomeGathered >= targetAnnualIncome) break;
            const neededNet = targetAnnualIncome - netIncomeGathered;
            const res = calculatePensionWithdrawal(pension.balance, neededNet, taxableIncome1, remainingLSA1, isUpFront, remPa1);
            if (res.grossWithdrawal > 0.0) {
              pension.balance -= res.grossWithdrawal;
              remainingLSA1 = Math.max(0.0, remainingLSA1 - res.taxFreeAmount);
              taxableIncome1 += res.taxableAmount;
              tax1 += res.taxPaid;
              netIncomeGathered += res.netReceived;
              grossIncomeGathered += res.grossWithdrawal;
              remPa1 = Math.max(0.0, remPa1 - res.taxableAmount);
              yearWithdrawals.push({
                potId: pension.id,
                potName: pension.name,
                ownerName: person1.name,
                amountDrawn: res.grossWithdrawal,
                taxFreeAmount: res.taxFreeAmount,
                taxableAmount: res.taxableAmount,
                taxPaid: res.taxPaid
              });
              const prevW = potWithdrawals.get(pension.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
              potWithdrawals.set(pension.id, {
                gross: prevW.gross + res.grossWithdrawal,
                tf: prevW.tf + res.taxFreeAmount,
                taxable: prevW.taxable + res.taxableAmount,
                tax: prevW.tax + res.taxPaid
              });
            }
          }
        }

        if (projectionType !== 'INDIVIDUAL_PERSON1' && isRetired2 && paLeft2 > 0.0) {
          const yearsUntilSpa2 = Math.max(1, spAge2 - age2);
          const person2TotalPension = dcPensions
            .filter(p => p.personId === 'person-2' && p.balance > 0.0)
            .reduce((sum, p) => sum + p.balance, 0);

          // Spread over remaining years to SPA (up to 10 years of basic income allowance)
          const spreadGross = age2 < spAge2 && yearsUntilSpa2 > 1
            ? person2TotalPension / yearsUntilSpa2
            : person2TotalPension;
          const maxTaxFreeGross = isUpFront ? paLeft2 : (paLeft2 / 0.75);
          const targetGross = Math.min(maxTaxFreeGross, spreadGross);
          const maxTaxableForPerson2 = isUpFront ? targetGross : Math.min(paLeft2, targetGross * 0.75);

          let remPa2 = Math.max(0.0, maxTaxableForPerson2);
          for (const pension of dcPensions.filter(p => p.personId === 'person-2' && p.balance > 0.0)) {
            if (remPa2 <= 0.0 || netIncomeGathered >= targetAnnualIncome) break;
            const neededNet = targetAnnualIncome - netIncomeGathered;
            const res = calculatePensionWithdrawal(pension.balance, neededNet, taxableIncome2, remainingLSA2, isUpFront, remPa2);
            if (res.grossWithdrawal > 0.0) {
              pension.balance -= res.grossWithdrawal;
              remainingLSA2 = Math.max(0.0, remainingLSA2 - res.taxFreeAmount);
              taxableIncome2 += res.taxableAmount;
              tax2 += res.taxPaid;
              netIncomeGathered += res.netReceived;
              grossIncomeGathered += res.grossWithdrawal;
              remPa2 = Math.max(0.0, remPa2 - res.taxableAmount);
              yearWithdrawals.push({
                potId: pension.id,
                potName: pension.name,
                ownerName: person2.name,
                amountDrawn: res.grossWithdrawal,
                taxFreeAmount: res.taxFreeAmount,
                taxableAmount: res.taxableAmount,
                taxPaid: res.taxPaid
              });
              const prevW = potWithdrawals.get(pension.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
              potWithdrawals.set(pension.id, {
                gross: prevW.gross + res.grossWithdrawal,
                tf: prevW.tf + res.taxFreeAmount,
                taxable: prevW.taxable + res.taxableAmount,
                tax: prevW.tax + res.taxPaid
              });
            }
          }
        }

        // 2. Draw down OTHER SAVINGS (Current & GIA) BEFORE ISAs!
        if (netIncomeGathered < targetAnnualIncome) {
          const nonIsaSavings = savings.filter(s => (s.type === 'CURRENT' || s.type === 'GENERAL_INVESTMENT') && s.balance > 0.0);
          for (const pot of nonIsaSavings) {
            if (netIncomeGathered >= targetAnnualIncome) break;
            const remainingNeeded = targetAnnualIncome - netIncomeGathered;
            const isPerson2 = pot.personId === 'person-2';
            const ownerName = isPerson2 ? person2.name : person1.name;

            const drawn = Math.min(pot.balance, remainingNeeded);
            if (drawn > 0.0) {
              pot.balance -= drawn;
              netIncomeGathered += drawn;
              grossIncomeGathered += drawn;
              yearWithdrawals.push({
                potId: pot.id,
                potName: pot.name,
                ownerName,
                amountDrawn: drawn,
                taxFreeAmount: drawn,
                taxableAmount: 0,
                taxPaid: 0
              });
              const prevW = potWithdrawals.get(pot.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
              potWithdrawals.set(pot.id, {
                gross: prevW.gross + drawn,
                tf: prevW.tf + drawn,
                taxable: 0,
                tax: 0
              });
            }
          }
        }

        // 3. Draw down DC pensions up to Basic Rate Threshold (£50,270 taxable income) BEFORE ISAs!
        if (netIncomeGathered < targetAnnualIncome) {
          const basicRateLimit = PERSONAL_ALLOWANCE + BASIC_RATE_THRESHOLD; // £50,270
          const eligiblePensions = dcPensions
            .filter(p => {
              const isPerson2 = p.personId === 'person-2';
              const ownerAge = isPerson2 ? age2 : age1;
              const minAge = isPerson2 ? minPensionAge2 : minPensionAge1;
              return ownerAge >= minAge && p.balance > 0.0;
            })
            .sort((a, b) => {
              if (a.personId !== 'person-2' && b.personId === 'person-2') return -1;
              if (a.personId === 'person-2' && b.personId !== 'person-2') return 1;
              return 0;
            });

          for (const pension of eligiblePensions) {
            if (netIncomeGathered >= targetAnnualIncome) break;
            const isPerson2 = pension.personId === 'person-2';
            const currTaxable = isPerson2 ? taxableIncome2 : taxableIncome1;
            const basicCap = Math.max(0.0, basicRateLimit - currTaxable);
            if (basicCap <= 0.0) continue;

            const remainingNeeded = targetAnnualIncome - netIncomeGathered;
            const remLsa = isPerson2 ? remainingLSA2 : remainingLSA1;
            const ownerName = isPerson2 ? person2.name : person1.name;

            const res = calculatePensionWithdrawal(pension.balance, remainingNeeded, currTaxable, remLsa, isUpFront, basicCap);
            if (res.grossWithdrawal > 0.0) {
              pension.balance -= res.grossWithdrawal;
              if (isPerson2) {
                remainingLSA2 = Math.max(0.0, remainingLSA2 - res.taxFreeAmount);
                taxableIncome2 += res.taxableAmount;
                tax2 += res.taxPaid;
              } else {
                remainingLSA1 = Math.max(0.0, remainingLSA1 - res.taxFreeAmount);
                taxableIncome1 += res.taxableAmount;
                tax1 += res.taxPaid;
              }
              netIncomeGathered += res.netReceived;
              grossIncomeGathered += res.grossWithdrawal;
              yearWithdrawals.push({
                potId: pension.id,
                potName: pension.name,
                ownerName,
                amountDrawn: res.grossWithdrawal,
                taxFreeAmount: res.taxFreeAmount,
                taxableAmount: res.taxableAmount,
                taxPaid: res.taxPaid
              });
              const prevW = potWithdrawals.get(pension.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
              potWithdrawals.set(pension.id, {
                gross: prevW.gross + res.grossWithdrawal,
                tf: prevW.tf + res.taxFreeAmount,
                taxable: prevW.taxable + res.taxableAmount,
                tax: prevW.tax + res.taxPaid
              });
            }
          }
        }

        // 4. Draw down ISAs NEXT (100% tax-free shelter)
        if (netIncomeGathered < targetAnnualIncome) {
          const isas = savings.filter(s => s.type === 'ISA' && s.balance > 0.0);
          for (const isa of isas) {
            if (netIncomeGathered >= targetAnnualIncome) break;
            const remainingNeeded = targetAnnualIncome - netIncomeGathered;
            const drawn = Math.min(isa.balance, remainingNeeded);
            isa.balance -= drawn;
            netIncomeGathered += drawn;
            grossIncomeGathered += drawn;
            const ownerName = isa.personId === 'person-2' ? person2.name : person1.name;
            yearWithdrawals.push({
              potId: isa.id,
              potName: isa.name,
              ownerName,
              amountDrawn: drawn,
              taxFreeAmount: drawn,
              taxableAmount: 0,
              taxPaid: 0
            });
            const prevW = potWithdrawals.get(isa.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
            potWithdrawals.set(isa.id, {
              gross: prevW.gross + drawn,
              tf: prevW.tf + drawn,
              taxable: 0,
              tax: 0
            });
          }
        }

        // 5. Draw down remaining DC pensions above Basic Rate Threshold (Higher Rate 40%)
        if (netIncomeGathered < targetAnnualIncome) {
          const eligiblePensions = dcPensions
            .filter(p => {
              const isPerson2 = p.personId === 'person-2';
              const ownerAge = isPerson2 ? age2 : age1;
              const minAge = isPerson2 ? minPensionAge2 : minPensionAge1;
              return ownerAge >= minAge && p.balance > 0.0;
            })
            .sort((a, b) => {
              if (a.personId !== 'person-2' && b.personId === 'person-2') return -1;
              if (a.personId === 'person-2' && b.personId !== 'person-2') return 1;
              return 0;
            });

          for (const pension of eligiblePensions) {
            if (netIncomeGathered >= targetAnnualIncome) break;
            const remainingNeeded = targetAnnualIncome - netIncomeGathered;
            const isPerson2 = pension.personId === 'person-2';
            const currTaxable = isPerson2 ? taxableIncome2 : taxableIncome1;
            const remLsa = isPerson2 ? remainingLSA2 : remainingLSA1;
            const ownerName = isPerson2 ? person2.name : person1.name;

            const res = calculatePensionWithdrawal(pension.balance, remainingNeeded, currTaxable, remLsa, isUpFront);
            if (res.grossWithdrawal > 0.0) {
              pension.balance -= res.grossWithdrawal;
              if (isPerson2) {
                remainingLSA2 = Math.max(0.0, remainingLSA2 - res.taxFreeAmount);
                taxableIncome2 += res.taxableAmount;
                tax2 += res.taxPaid;
              } else {
                remainingLSA1 = Math.max(0.0, remainingLSA1 - res.taxFreeAmount);
                taxableIncome1 += res.taxableAmount;
                tax1 += res.taxPaid;
              }
              netIncomeGathered += res.netReceived;
              grossIncomeGathered += res.grossWithdrawal;
              yearWithdrawals.push({
                potId: pension.id,
                potName: pension.name,
                ownerName,
                amountDrawn: res.grossWithdrawal,
                taxFreeAmount: res.taxFreeAmount,
                taxableAmount: res.taxableAmount,
                taxPaid: res.taxPaid
              });
              const prevW = potWithdrawals.get(pension.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
              potWithdrawals.set(pension.id, {
                gross: prevW.gross + res.grossWithdrawal,
                tf: prevW.tf + res.taxFreeAmount,
                taxable: prevW.taxable + res.taxableAmount,
                tax: prevW.tax + res.taxPaid
              });
            }
          }
        }
      }

      // 4. Annual Bed & ISA Transfer
      const transferRes = performBedAndIsaTransfer(
        savings,
        projectionType,
        person1,
        person2,
        isRetired1,
        isRetired2,
        assumptions,
        inflationFactor
      );

      transferRes.giaTransfersOut.forEach((amount, id) => {
        potTransfers.set(id, (potTransfers.get(id) || 0) - amount);
      });
      transferRes.isaTransfersIn.forEach((amount, id) => {
        potTransfers.set(id, (potTransfers.get(id) || 0) + amount);
      });

      // 5. Apply Growth and Tax on Savings
      const potGrowthAmounts = new Map<string, { rate: number; growth: number }>();
      const potTaxPaidMap = new Map<string, number>();

      dcPensions.forEach(p => {
        const growthInfo = getPotGrowthRate(p, assumptions, portfolioGrowthRate, currentEquityReturn, currentCashReturn);
        const growth = p.balance * growthInfo.netRate;
        p.balance = Math.max(0.0, p.balance + growth);
        potGrowthAmounts.set(p.id, { rate: growthInfo.netRate, growth });
      });

      // GIA annual growth & Capital Gains Tax (with £3,000 annual exemption)
      const giaGrowth1 = savings
        .filter(s => s.personId !== 'person-2' && s.type === 'GENERAL_INVESTMENT')
        .reduce((sum, s) => sum + s.balance * getPotGrowthRate(s, assumptions, portfolioGrowthRate, currentEquityReturn, currentCashReturn).grossRate, 0);
      const giaGrowth2 = savings
        .filter(s => s.personId === 'person-2' && s.type === 'GENERAL_INVESTMENT')
        .reduce((sum, s) => sum + s.balance * getPotGrowthRate(s, assumptions, portfolioGrowthRate, currentEquityReturn, currentCashReturn).grossRate, 0);

      const giaTax1 = calculateTaxOnGiaGrowth(giaGrowth1, taxableIncome1);
      const giaTax2 = calculateTaxOnGiaGrowth(giaGrowth2, taxableIncome2);

      tax1 += giaTax1;
      tax2 += giaTax2;

      savings.forEach(s => {
        const growthInfo = getPotGrowthRate(s, assumptions, portfolioGrowthRate, currentEquityReturn, currentCashReturn);
        const rawGrowth = s.balance * growthInfo.grossRate;

        if (s.type === 'GENERAL_INVESTMENT') {
          const isPerson2 = s.personId === 'person-2';
          const totalGrowth = isPerson2 ? giaGrowth2 : giaGrowth1;
          const totalTax = isPerson2 ? giaTax2 : giaTax1;
          const taxForThis = totalGrowth > 0.0 ? totalTax * (rawGrowth / totalGrowth) : 0.0;
          const amcAmount = s.balance * growthInfo.amcRate;

          s.balance = Math.max(0.0, s.balance + rawGrowth - taxForThis - amcAmount);
          potGrowthAmounts.set(s.id, { rate: growthInfo.netRate, growth: rawGrowth });
          potTaxPaidMap.set(s.id, taxForThis);

          if (taxForThis > 0.0) {
            yearWithdrawals.push({
              potId: s.id,
              potName: `${s.name} (GIA Capital Gains Tax)`,
              ownerName: isPerson2 ? person2.name : person1.name,
              amountDrawn: taxForThis,
              taxFreeAmount: 0,
              taxableAmount: taxForThis,
              taxPaid: taxForThis
            });
          }
        } else {
          const growth = s.balance * growthInfo.netRate;
          s.balance = Math.max(0.0, s.balance + growth);
          potGrowthAmounts.set(s.id, { rate: growthInfo.netRate, growth });
        }
      });

      const currentTax = tax1 + tax2;
      totalTaxPaid += currentTax;

      const metTarget = netIncomeGathered >= (targetAnnualIncome - 1.0);
      if (!metTarget && retirementFeasible) {
        retirementFeasible = false;
        fundsLastUntilAge = ageActive;
      }

      const totalPensionVal = dcPensions.reduce((sum, p) => sum + p.balance, 0);
      const totalSavingsVal = savings.reduce((sum, s) => sum + s.balance, 0);

      // Build detailed per-pot breakdown for this year!
      const potBreakdowns: PotYearBreakdown[] = [];
      let totalGrowthThisYear = 0;

      [...dcPensions, ...savings].forEach(pot => {
        const startBalance = potStartBalances.get(pot.id) || 0.0;
        const contrib = potContributions.get(pot.id) || 0.0;
        const transfer = potTransfers.get(pot.id) || 0.0;
        const growthInfo = potGrowthAmounts.get(pot.id) || { rate: 0, growth: 0 };
        const wInfo = potWithdrawals.get(pot.id) || { gross: 0, tf: 0, taxable: 0, tax: 0 };
        const savingsTax = potTaxPaidMap.get(pot.id) || 0.0;

        totalGrowthThisYear += growthInfo.growth;

        potBreakdowns.push({
          potId: pot.id,
          potName: pot.name,
          potType: pot.type,
          ownerName: pot.personId === 'person-2' ? person2.name : person1.name,
          startBalance,
          contributionsAdded: contrib,
          bedAndIsaTransfer: transfer,
          growthRate: growthInfo.rate * 100.0,
          growthAmount: growthInfo.growth,
          grossWithdrawal: wInfo.gross,
          taxFreeWithdrawal: wInfo.tf,
          taxableWithdrawal: wInfo.taxable,
          taxPaid: wInfo.tax + savingsTax,
          endBalance: pot.balance
        });
      });

      results.push({
        age: ageActive,
        age1,
        age2,
        year,
        totalPensionValue: totalPensionVal,
        totalSavings: totalSavingsVal,
        totalNetWorth: totalPensionVal + totalSavingsVal,
        annualIncome: targetAnnualIncome,
        grossIncome: grossIncomeGathered,
        netIncome: netIncomeGathered,
        tax: currentTax,
        tax1,
        tax2,
        canRetire: metTarget,
        withdrawals: yearWithdrawals,
        potBreakdowns,
        totalGrowthThisYear,
        totalTaxThisYear: currentTax
      });
    } else {
      // Accumulation Phase:
      const transferRes = performBedAndIsaTransfer(
        savings,
        projectionType,
        person1,
        person2,
        false,
        false,
        assumptions,
        inflationFactor
      );

      transferRes.giaTransfersOut.forEach((amount, id) => {
        potTransfers.set(id, (potTransfers.get(id) || 0) - amount);
      });
      transferRes.isaTransfersIn.forEach((amount, id) => {
        potTransfers.set(id, (potTransfers.get(id) || 0) + amount);
      });

      const potGrowthAmounts = new Map<string, { rate: number; growth: number }>();
      const potTaxPaidMap = new Map<string, number>();

      dcPensions.forEach(p => {
        const growthInfo = getPotGrowthRate(p, assumptions, portfolioGrowthRate, currentEquityReturn, currentCashReturn);
        const growth = p.balance * growthInfo.netRate;
        p.balance = Math.max(0.0, p.balance + growth);
        potGrowthAmounts.set(p.id, { rate: growthInfo.netRate, growth });
      });

      const giaGrowth1 = savings
        .filter(s => s.personId !== 'person-2' && s.type === 'GENERAL_INVESTMENT')
        .reduce((sum, s) => sum + s.balance * getPotGrowthRate(s, assumptions, portfolioGrowthRate, currentEquityReturn, currentCashReturn).grossRate, 0);
      const giaGrowth2 = savings
        .filter(s => s.personId === 'person-2' && s.type === 'GENERAL_INVESTMENT')
        .reduce((sum, s) => sum + s.balance * getPotGrowthRate(s, assumptions, portfolioGrowthRate, currentEquityReturn, currentCashReturn).grossRate, 0);

      const giaTax1 = calculateTaxOnGiaGrowth(giaGrowth1, 0.0);
      const giaTax2 = calculateTaxOnGiaGrowth(giaGrowth2, 0.0);
      const currentTax = giaTax1 + giaTax2;
      totalTaxPaid += currentTax;

      savings.forEach(s => {
        const growthInfo = getPotGrowthRate(s, assumptions, portfolioGrowthRate, currentEquityReturn, currentCashReturn);
        const rawGrowth = s.balance * growthInfo.grossRate;

        if (s.type === 'GENERAL_INVESTMENT') {
          const isPerson2 = s.personId === 'person-2';
          const totalGrowth = isPerson2 ? giaGrowth2 : giaGrowth1;
          const totalTax = isPerson2 ? giaTax2 : giaTax1;
          const taxForThis = totalGrowth > 0.0 ? totalTax * (rawGrowth / totalGrowth) : 0.0;
          const amcAmount = s.balance * growthInfo.amcRate;

          s.balance = Math.max(0.0, rawGrowth - taxForThis - amcAmount);
          potGrowthAmounts.set(s.id, { rate: growthInfo.netRate, growth: rawGrowth });
          potTaxPaidMap.set(s.id, taxForThis);
        } else {
          const growth = s.balance * growthInfo.netRate;
          s.balance = Math.max(0.0, s.balance + growth);
          potGrowthAmounts.set(s.id, { rate: growthInfo.netRate, growth });
        }
      });

      const totalPensionVal = dcPensions.reduce((sum, p) => sum + p.balance, 0);
      const totalSavingsVal = savings.reduce((sum, s) => sum + s.balance, 0);

      const potBreakdowns: PotYearBreakdown[] = [];
      let totalGrowthThisYear = 0;

      [...dcPensions, ...savings].forEach(pot => {
        const startBalance = potStartBalances.get(pot.id) || 0.0;
        const contrib = potContributions.get(pot.id) || 0.0;
        const transfer = potTransfers.get(pot.id) || 0.0;
        const growthInfo = potGrowthAmounts.get(pot.id) || { rate: 0, growth: 0 };
        const savingsTax = potTaxPaidMap.get(pot.id) || 0.0;

        totalGrowthThisYear += growthInfo.growth;

        potBreakdowns.push({
          potId: pot.id,
          potName: pot.name,
          potType: pot.type,
          ownerName: pot.personId === 'person-2' ? person2.name : person1.name,
          startBalance,
          contributionsAdded: contrib,
          bedAndIsaTransfer: transfer,
          growthRate: growthInfo.rate * 100.0,
          growthAmount: growthInfo.growth,
          grossWithdrawal: 0,
          taxFreeWithdrawal: 0,
          taxableWithdrawal: 0,
          taxPaid: savingsTax,
          endBalance: pot.balance
        });
      });

      results.push({
        age: ageActive,
        age1,
        age2,
        year,
        totalPensionValue: totalPensionVal,
        totalSavings: totalSavingsVal,
        totalNetWorth: totalPensionVal + totalSavingsVal,
        annualIncome: 0,
        grossIncome: 0,
        netIncome: 0,
        tax: currentTax,
        tax1: giaTax1,
        tax2: giaTax2,
        canRetire: false,
        withdrawals: [],
        potBreakdowns,
        totalGrowthThisYear,
        totalTaxThisYear: currentTax
      });
    }
  }

  return {
    results,
    retirementAge: retirementAge1,
    feasible: retirementFeasible,
    totalTaxPaid,
    fundsLastUntilAge,
    peakPensionPot,
    totalContributionsAccumulated
  };
}

/**
 * Stochastic Monte Carlo Simulation Engine
 * Runs N trials with randomized annual market returns and inflation volatility to stress-test
 * sequence-of-returns risk and determine portfolio longevity probability.
 */
export function runMonteCarloSimulation(
  accounts: Account[],
  assumptions: InvestmentAssumptions,
  preferences: DrawdownPreferences,
  person1: Person,
  person2: Person,
  retirementAge1: number,
  retirementAge2: number,
  projectionType: ProjectionType,
  trialCount: number = 500
): MonteCarloResult {
  const currentYear = new Date().getFullYear();
  const currentAge1 = currentYear - person1.birthYear;
  const currentAge2 = currentYear - person2.birthYear;
  const currentAgeActive = projectionType === 'INDIVIDUAL_PERSON2' ? currentAge2 : currentAge1;
  const endAge = preferences.endAge;
  const yearsToProject = Math.max(1, endAge - currentAgeActive + 1);

  // Standard historical annual volatility (standard deviations)
  const equityVol = 0.16;
  const bondVol = 0.07;
  const cashVol = 0.015;
  const inflVol = 0.015;

  function sampleNormal(mean: number, stdDev: number): number {
    let u1 = Math.random();
    let u2 = Math.random();
    while (u1 <= 1e-7) u1 = Math.random();
    const z0 = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    return mean + z0 * stdDev;
  }

  const endingWealths: number[] = [];
  const depletionAges: number[] = [];
  const yearlyNetWorthMatrix: number[][] = Array.from({ length: yearsToProject }, () => []);
  let successfulTrials = 0;

  for (let trial = 0; trial < trialCount; trial++) {
    const stochasticSchedule: StochasticYearSample[] = [];
    for (let y = 0; y < yearsToProject; y++) {
      stochasticSchedule.push({
        equityReturn: sampleNormal(assumptions.equityReturn, equityVol),
        bondReturn: sampleNormal(assumptions.bondReturn, bondVol),
        cashReturn: Math.max(0.0, sampleNormal(assumptions.cashReturn, cashVol)),
        inflationRate: Math.max(0.0, sampleNormal(assumptions.inflationRate, inflVol))
      });
    }

    const trialRes = calculateProjections(
      accounts,
      assumptions,
      preferences,
      person1,
      person2,
      retirementAge1,
      retirementAge2,
      projectionType,
      stochasticSchedule
    );

    const isSuccess = trialRes.feasible;
    if (isSuccess) {
      successfulTrials++;
    } else {
      depletionAges.push(trialRes.fundsLastUntilAge);
    }

    const finalNetWorth = trialRes.results.length > 0
      ? trialRes.results[trialRes.results.length - 1].totalNetWorth
      : 0;
    endingWealths.push(finalNetWorth);

    trialRes.results.forEach((r, idx) => {
      if (idx < yearsToProject) {
        yearlyNetWorthMatrix[idx].push(r.totalNetWorth);
      }
    });
  }

  endingWealths.sort((a, b) => a - b);
  const getPercentile = (arr: number[], p: number) => {
    if (arr.length === 0) return 0;
    const idx = Math.min(arr.length - 1, Math.max(0, Math.floor(arr.length * p)));
    return arr[idx];
  };

  const successRate = Math.round((successfulTrials / trialCount) * 1000) / 10;
  const p10EndingWealth = Math.round(getPercentile(endingWealths, 0.10));
  const medianEndingWealth = Math.round(getPercentile(endingWealths, 0.50));
  const p90EndingWealth = Math.round(getPercentile(endingWealths, 0.90));

  depletionAges.sort((a, b) => a - b);
  const earliestDepletionAge = depletionAges.length > 0 ? depletionAges[0] : null;
  const medianDepletionAge = depletionAges.length > 0 ? getPercentile(depletionAges, 0.50) : null;

  const yearlyPercentiles: MonteCarloYearPercentile[] = yearlyNetWorthMatrix.map((row, idx) => {
    row.sort((a, b) => a - b);
    const age = currentAgeActive + idx;
    const year = currentYear + idx;
    return {
      age,
      year,
      p10: Math.round(getPercentile(row, 0.10)),
      p25: Math.round(getPercentile(row, 0.25)),
      p50: Math.round(getPercentile(row, 0.50)),
      p75: Math.round(getPercentile(row, 0.75)),
      p90: Math.round(getPercentile(row, 0.90))
    };
  });

  let confidenceRating: 'HIGH' | 'MODERATE' | 'CAUTION' | 'CRITICAL';
  if (successRate >= 90) {
    confidenceRating = 'HIGH';
  } else if (successRate >= 75) {
    confidenceRating = 'MODERATE';
  } else if (successRate >= 60) {
    confidenceRating = 'CAUTION';
  } else {
    confidenceRating = 'CRITICAL';
  }

  return {
    totalTrials: trialCount,
    successRate,
    successfulTrials,
    failedTrials: trialCount - successfulTrials,
    medianEndingWealth,
    p10EndingWealth,
    p90EndingWealth,
    earliestDepletionAge,
    medianDepletionAge,
    yearlyPercentiles,
    confidenceRating,
    simulatedAt: new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  };
}
