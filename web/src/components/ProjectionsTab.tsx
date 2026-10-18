import React, { useState, useMemo } from 'react';
import {
  ChevronDown,
  ChevronRight,
  Download,
  CheckCircle2,
  AlertTriangle,
  Sliders,
  Maximize2,
  Minimize2,
  TrendingUp,
  Coins,
  ShieldCheck,
  Percent
} from 'lucide-react';
import {
  Account,
  InvestmentAssumptions,
  DrawdownPreferences,
  Person,
  ProjectionType,
  DrawdownStrategy,
  LumpSumOption
} from '../types';
import { calculateProjections } from '../utils/pensionEngine';
import { downloadDetailedCsv } from '../utils/storage';
import { ProjectionChart } from './Charts';

interface ProjectionsTabProps {
  accounts: Account[];
  assumptions: InvestmentAssumptions;
  drawdown: DrawdownPreferences;
  people: Person[];
  onUpdateDrawdown: (updated: DrawdownPreferences) => void;
}

export const ProjectionsTab: React.FC<ProjectionsTabProps> = ({
  accounts,
  assumptions,
  drawdown,
  people,
  onUpdateDrawdown
}) => {
  const person1 = people[0] || { id: 'person-1', name: 'Person 1', birthYear: 1980, retirementAge: 65, qualifyingYears: 35 };
  const person2 = people[1] || { id: 'person-2', name: 'Person 2', birthYear: 1982, retirementAge: 65, qualifyingYears: 35 };

  const [retirementAge1, setRetirementAge1] = useState<number>(person1.retirementAge);
  const [retirementAge2, setRetirementAge2] = useState<number>(person2.retirementAge);
  const [targetIncome, setTargetIncome] = useState<number>(drawdown.targetAnnualIncome);
  const [strategy, setStrategy] = useState<DrawdownStrategy>(drawdown.strategy);
  const [lumpSumOption, setLumpSumOption] = useState<LumpSumOption>(drawdown.lumpSumOption);
  const [stepDownAge, setStepDownAge] = useState<number>(drawdown.stepDownAge);
  const [stepDownPercentage, setStepDownPercentage] = useState<number>(drawdown.stepDownPercentage);
  const [inflationAdjusted, setInflationAdjusted] = useState<boolean>(drawdown.inflationAdjusted);
  const [projectionType, setProjectionType] = useState<ProjectionType>(drawdown.isCouple ? 'COUPLE' : 'INDIVIDUAL_PERSON1');

  // Expanded rows state
  const [expandedYears, setExpandedYears] = useState<Set<number>>(new Set());
  const [showRetirementOnly, setShowRetirementOnly] = useState<boolean>(false);
  const [showControls, setShowControls] = useState<boolean>(true);

  // Sync back to parent preferences when changed
  const handleUpdatePreference = (field: keyof DrawdownPreferences, val: any) => {
    const updated = { ...drawdown, [field]: val };
    onUpdateDrawdown(updated);
  };

  // Run dynamic calculation
  const projection = useMemo(() => {
    const updatedDrawdown: DrawdownPreferences = {
      targetAnnualIncome: targetIncome,
      inflationAdjusted,
      startAge: Math.min(retirementAge1, retirementAge2),
      endAge: drawdown.endAge,
      strategy,
      lumpSumOption,
      stepDownAge,
      stepDownPercentage,
      isCouple: projectionType === 'COUPLE'
    };

    return calculateProjections(
      accounts,
      assumptions,
      updatedDrawdown,
      person1,
      person2,
      retirementAge1,
      retirementAge2,
      projectionType
    );
  }, [
    accounts,
    assumptions,
    targetIncome,
    inflationAdjusted,
    drawdown.endAge,
    strategy,
    lumpSumOption,
    stepDownAge,
    stepDownPercentage,
    projectionType,
    person1,
    person2,
    retirementAge1,
    retirementAge2
  ]);

  // Comparative Standard vs Tax-Minimized projection
  const standardComparison = useMemo(() => {
    if (strategy === 'STANDARD') return null;
    const stdPrefs: DrawdownPreferences = {
      targetAnnualIncome: targetIncome,
      inflationAdjusted,
      startAge: Math.min(retirementAge1, retirementAge2),
      endAge: drawdown.endAge,
      strategy: 'STANDARD',
      lumpSumOption,
      stepDownAge,
      stepDownPercentage,
      isCouple: projectionType === 'COUPLE'
    };
    return calculateProjections(
      accounts,
      assumptions,
      stdPrefs,
      person1,
      person2,
      retirementAge1,
      retirementAge2,
      projectionType
    );
  }, [
    accounts,
    assumptions,
    targetIncome,
    inflationAdjusted,
    drawdown.endAge,
    strategy,
    lumpSumOption,
    stepDownAge,
    stepDownPercentage,
    projectionType,
    person1,
    person2,
    retirementAge1,
    retirementAge2
  ]);

  const taxSavingsFromStrategy = standardComparison
    ? Math.max(0, standardComparison.totalTaxPaid - projection.totalTaxPaid)
    : 0;

  const toggleYearExpand = (year: number) => {
    setExpandedYears(prev => {
      const next = new Set(prev);
      if (next.has(year)) {
        next.delete(year);
      } else {
        next.add(year);
      }
      return next;
    });
  };

  const expandAllYears = () => {
    const allYears = new Set(projection.results.map(r => r.year));
    setExpandedYears(allYears);
  };

  const collapseAllYears = () => {
    setExpandedYears(new Set());
  };

  // Filtered rows
  const displayedResults = useMemo(() => {
    if (!showRetirementOnly) return projection.results;
    const activeRetireAge = projectionType === 'INDIVIDUAL_PERSON2' ? retirementAge2 : retirementAge1;
    return projection.results.filter(r => r.age >= activeRetireAge);
  }, [projection.results, showRetirementOnly, projectionType, retirementAge1, retirementAge2]);

  return (
    <div className="tab-page-container">
      {/* Controls & Sliders Panel */}
      <div className="card-glass-panel">
        <div className="panel-header-collapsible" onClick={() => setShowControls(!showControls)}>
          <div className="flex-row items-center gap-2">
            <Sliders size={20} className="text-indigo" />
            <h3 className="text-lg font-bold">Retirement & Drawdown Parameters</h3>
          </div>
          <button type="button" className="btn-icon">
            {showControls ? <ChevronDown size={18} /> : <ChevronRight size={18} />}
          </button>
        </div>

        {showControls && (
          <div className="controls-grid">
            {/* Target Income */}
            <div className="control-item">
              <div className="control-label-row">
                <label htmlFor="target-income-input" className="control-label">Target Annual Net Income</label>
                <span className="control-badge text-indigo font-bold">
                  £{Math.round(targetIncome).toLocaleString('en-GB')} / yr
                </span>
              </div>
              <input
                type="range"
                min="15000"
                max="150000"
                step="1000"
                className="slider-input"
                value={targetIncome}
                onChange={e => {
                  const val = parseFloat(e.target.value);
                  setTargetIncome(val);
                  handleUpdatePreference('targetAnnualIncome', val);
                }}
              />
              <div className="control-subtext">£{Math.round(targetIncome / 12).toLocaleString('en-GB')} per month</div>
            </div>

            {/* Planned Retirement Ages */}
            <div className="control-item">
              <div className="control-label-row">
                <label htmlFor="retire-age1-input" className="control-label">{person1.name}'s Retirement Age</label>
                <span className="control-badge text-amber font-bold">Age {retirementAge1}</span>
              </div>
              <input
                type="range"
                min="55"
                max="75"
                step="1"
                className="slider-input"
                value={retirementAge1}
                onChange={e => setRetirementAge1(parseInt(e.target.value, 10))}
              />
              <div className="control-subtext">Birth Year {person1.birthYear} (SPA 67)</div>
            </div>

            {drawdown.isCouple && (
              <div className="control-item">
                <div className="control-label-row">
                  <label htmlFor="retire-age2-input" className="control-label">{person2.name}'s Retirement Age</label>
                  <span className="control-badge text-pink font-bold">Age {retirementAge2}</span>
                </div>
                <input
                  type="range"
                  min="55"
                  max="75"
                  step="1"
                  className="slider-input"
                  value={retirementAge2}
                  onChange={e => setRetirementAge2(parseInt(e.target.value, 10))}
                />
                <div className="control-subtext">Birth Year {person2.birthYear} (SPA 67)</div>
              </div>
            )}

            {/* Drawdown Strategy */}
            <div className="control-item">
              <label htmlFor="drawdown-strategy-select" className="control-label">Drawdown Tax Strategy</label>
              <div className="button-toggle-group">
                <button
                  type="button"
                  className={`toggle-option-btn ${strategy === 'STANDARD' ? 'active' : ''}`}
                  onClick={() => {
                    setStrategy('STANDARD');
                    handleUpdatePreference('strategy', 'STANDARD');
                  }}
                >
                  Standard (PA Harvest)
                </button>
                <button
                  type="button"
                  className={`toggle-option-btn ${strategy === 'TAX_MINIMIZED' ? 'active' : ''}`}
                  onClick={() => {
                    setStrategy('TAX_MINIMIZED');
                    handleUpdatePreference('strategy', 'TAX_MINIMIZED');
                  }}
                >
                  Tax-Minimized (Basic Rate)
                </button>
              </div>
              <div className="control-subtext">
                {strategy === 'TAX_MINIMIZED'
                  ? 'Harvests pension up to £50,270 to prevent 40% higher rate tax in later years'
                  : 'Harvests only personal allowance (£12,570), drawing non-ISAs before ISAs'}
              </div>
            </div>

            {/* 25% Lump Sum Option */}
            <div className="control-item">
              <label htmlFor="lump-sum-option-select" className="control-label">25% Tax-Free Pension Option</label>
              <div className="button-toggle-group">
                <button
                  type="button"
                  className={`toggle-option-btn ${lumpSumOption === 'AS_YOU_GO' ? 'active' : ''}`}
                  onClick={() => {
                    setLumpSumOption('AS_YOU_GO');
                    handleUpdatePreference('lumpSumOption', 'AS_YOU_GO');
                  }}
                >
                  As You Go (UFPLS)
                </button>
                <button
                  type="button"
                  className={`toggle-option-btn ${lumpSumOption === 'UP_FRONT' ? 'active' : ''}`}
                  onClick={() => {
                    setLumpSumOption('UP_FRONT');
                    handleUpdatePreference('lumpSumOption', 'UP_FRONT');
                  }}
                >
                  Upfront Lump Sum
                </button>
              </div>
              <div className="control-subtext">
                {lumpSumOption === 'UP_FRONT'
                  ? 'Deposits 25% lump sum in GIA, using Bed & ISA (£20k/yr each) to progressively shelter tax'
                  : 'Each withdrawal takes 25% tax-free up to £268,275 lifetime allowance'}
              </div>
            </div>

            {/* Projection Scope */}
            <div className="control-item">
              <label htmlFor="projection-scope-select" className="control-label">Projection Perspective</label>
              <div className="button-toggle-group">
                {drawdown.isCouple && (
                  <button
                    type="button"
                    className={`toggle-option-btn ${projectionType === 'COUPLE' ? 'active' : ''}`}
                    onClick={() => setProjectionType('COUPLE')}
                  >
                    Both ({person1.name} & {person2.name})
                  </button>
                )}
                <button
                  type="button"
                  className={`toggle-option-btn ${projectionType === 'INDIVIDUAL_PERSON1' ? 'active' : ''}`}
                  onClick={() => setProjectionType('INDIVIDUAL_PERSON1')}
                >
                  {person1.name} Only
                </button>
                {drawdown.isCouple && (
                  <button
                    type="button"
                    className={`toggle-option-btn ${projectionType === 'INDIVIDUAL_PERSON2' ? 'active' : ''}`}
                    onClick={() => setProjectionType('INDIVIDUAL_PERSON2')}
                  >
                    {person2.name} Only
                  </button>
                )}
              </div>
            </div>

            {/* Step-down & Inflation */}
            <div className="control-item flex-row items-center gap-4">
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={inflationAdjusted}
                  onChange={e => {
                    setInflationAdjusted(e.target.checked);
                    handleUpdatePreference('inflationAdjusted', e.target.checked);
                  }}
                />
                <span>Inflation Adjusted ({assumptions.inflationRate * 100}%)</span>
              </label>

              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={stepDownPercentage > 0}
                  onChange={e => {
                    const newPct = e.target.checked ? 25 : 0;
                    setStepDownPercentage(newPct);
                    handleUpdatePreference('stepDownPercentage', newPct);
                  }}
                />
                <span>Step-Down Spending at Age {stepDownAge} (-{stepDownPercentage}%)</span>
              </label>
            </div>
          </div>
        )}
      </div>

      {/* KPI Status Row */}
      <div className="kpi-grid">
        <div className={`kpi-card ${projection.feasible ? 'border-success' : 'border-danger'}`}>
          <div className="kpi-header">
            <span className="kpi-label">Retirement Feasibility</span>
            {projection.feasible ? (
              <CheckCircle2 size={20} className="text-emerald" />
            ) : (
              <AlertTriangle size={20} className="text-danger" />
            )}
          </div>
          <div className={`kpi-value ${projection.feasible ? 'text-emerald' : 'text-danger'}`}>
            {projection.feasible ? '100% Sustainable' : `Depletes Age ${projection.fundsLastUntilAge}`}
          </div>
          <div className="kpi-footer">
            {projection.feasible
              ? `Funds comfortably last to age ${drawdown.endAge}+`
              : `Pots run out before age ${drawdown.endAge}; consider adjusting income`}
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-label">Peak Pension Value</span>
            <TrendingUp size={20} className="text-amber" />
          </div>
          <div className="kpi-value text-amber">
            £{Math.round(projection.peakPensionPot).toLocaleString('en-GB')}
          </div>
          <div className="kpi-footer">Maximum value reached prior to decumulation</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-label">Total Tax Paid in Retirement</span>
            <Coins size={20} className="text-cyan" />
          </div>
          <div className="kpi-value">
            £{Math.round(projection.totalTaxPaid).toLocaleString('en-GB')}
          </div>
          <div className="kpi-footer">
            {taxSavingsFromStrategy > 0 ? (
              <span className="text-emerald font-semibold">
                Saves £{Math.round(taxSavingsFromStrategy).toLocaleString('en-GB')} vs Standard Strategy!
              </span>
            ) : (
              'Cumulative UK Income Tax paid on withdrawals'
            )}
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-label">Funds Last To</span>
            <ShieldCheck size={20} className="text-indigo" />
          </div>
          <div className="kpi-value text-indigo">
            Age {projection.fundsLastUntilAge}{projection.fundsLastUntilAge >= drawdown.endAge ? '+' : ''}
          </div>
          <div className="kpi-footer">
            Target £{Math.round(targetIncome).toLocaleString('en-GB')}/yr achieved every year
          </div>
        </div>
      </div>

      {/* Projection Chart */}
      <ProjectionChart data={projection.results} retirementAge={retirementAge1} />

      {/* DETAILED PER-POT PROJECTION BREAKDOWN TABLE (Core User Request) */}
      <div className="breakdown-table-card">
        <div className="breakdown-header">
          <div>
            <h3 className="breakdown-title">Comprehensive Year-by-Year & Per-Pot Breakdown</h3>
            <p className="breakdown-subtitle">
              Expand any year row below to see each pot's exact opening balance, growth gained, withdrawals, and tax paid!
            </p>
          </div>

          <div className="breakdown-actions">
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={() => setShowRetirementOnly(!showRetirementOnly)}
            >
              {showRetirementOnly ? 'Show All Years' : 'Retirement Years Only'}
            </button>

            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={expandedYears.size === displayedResults.length ? collapseAllYears : expandAllYears}
            >
              {expandedYears.size === displayedResults.length ? (
                <>
                  <Minimize2 size={15} />
                  <span>Collapse All</span>
                </>
              ) : (
                <>
                  <Maximize2 size={15} />
                  <span>Expand All Pots</span>
                </>
              )}
            </button>

            <button
              type="button"
              className="btn btn-primary btn-sm"
              onClick={() => downloadDetailedCsv(projection.results)}
            >
              <Download size={15} />
              <span>Export Full CSV</span>
            </button>
          </div>
        </div>

        <div className="table-responsive">
          <table className="projection-table">
            <thead>
              <tr>
                <th style={{ width: '40px' }}></th>
                <th>Age</th>
                <th>Year</th>
                <th>Pensions Total</th>
                <th>Savings / ISAs</th>
                <th>Total Net Worth</th>
                <th>Gross Drawn</th>
                <th>Net Received</th>
                <th>Tax Paid</th>
                <th>Total Growth</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {displayedResults.map(row => {
                const isExpanded = expandedYears.has(row.year);
                const isRetired = row.age >= retirementAge1;

                return (
                  <React.Fragment key={row.year}>
                    {/* Main Year Row */}
                    <tr
                      className={`year-summary-row ${isExpanded ? 'expanded' : ''} ${!row.canRetire ? 'row-deficit' : ''}`}
                      onClick={() => toggleYearExpand(row.year)}
                    >
                      <td className="text-center">
                        <button type="button" className="expand-chevron-btn" aria-label="Expand pot details">
                          {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                        </button>
                      </td>
                      <td>
                        <strong className="text-white">Age {row.age}</strong>
                        {row.age2 && <small className="text-muted block">({row.age2})</small>}
                      </td>
                      <td>
                        <span className="year-pill">{row.year}</span>
                      </td>
                      <td className="text-amber font-semibold">
                        £{Math.round(row.totalPensionValue).toLocaleString('en-GB')}
                      </td>
                      <td className="text-cyan font-semibold">
                        £{Math.round(row.totalSavings).toLocaleString('en-GB')}
                      </td>
                      <td className="text-indigo font-bold">
                        £{Math.round(row.totalNetWorth).toLocaleString('en-GB')}
                      </td>
                      <td>
                        {row.grossIncome > 0 ? `£${Math.round(row.grossIncome).toLocaleString('en-GB')}` : '—'}
                      </td>
                      <td className={row.netIncome > 0 ? 'text-emerald font-semibold' : ''}>
                        {row.netIncome > 0 ? `£${Math.round(row.netIncome).toLocaleString('en-GB')}` : '—'}
                      </td>
                      <td className={row.tax > 0 ? 'text-danger font-semibold' : ''}>
                        {row.tax > 0 ? `£${Math.round(row.tax).toLocaleString('en-GB')}` : '£0'}
                      </td>
                      <td className="text-emerald">
                        {row.totalGrowthThisYear > 0 ? `+£${Math.round(row.totalGrowthThisYear).toLocaleString('en-GB')}` : '—'}
                      </td>
                      <td>
                        {isRetired ? (
                          row.canRetire ? (
                            <span className="status-badge success">Target Met</span>
                          ) : (
                            <span className="status-badge danger">Deficit</span>
                          )
                        ) : (
                          <span className="status-badge info">Working</span>
                        )}
                      </td>
                    </tr>

                    {/* EXPANDED INNER POT BREAKDOWN TABLE */}
                    {isExpanded && (
                      <tr className="expanded-detail-container-row">
                        <td colSpan={11}>
                          <div className="pot-breakdown-panel">
                            <div className="pot-breakdown-header">
                              <h4 className="font-bold text-white flex-row items-center gap-2">
                                <Percent size={16} className="text-indigo" />
                                <span>Individual Pot Lifecycle Breakdown for {row.year} (Age {row.age})</span>
                              </h4>
                              <span className="text-muted text-sm">
                                Values at start of year, growth achieved, withdrawals taken, tax paid, and ending balance
                              </span>
                            </div>

                            {row.potBreakdowns.length === 0 ? (
                              <div className="p-4 text-muted text-center">No active pot breakdown data recorded for this year.</div>
                            ) : (
                              <div className="pot-table-wrapper">
                                <table className="inner-pot-table">
                                  <thead>
                                    <tr>
                                      <th>Pot Name</th>
                                      <th>Type</th>
                                      <th>Owner</th>
                                      <th>Opening Balance</th>
                                      <th>Contributions</th>
                                      <th>Bed & ISA</th>
                                      <th>Growth (£)</th>
                                      <th>Growth (%)</th>
                                      <th>Gross Drawn</th>
                                      <th>Tax-Free</th>
                                      <th>Tax Paid</th>
                                      <th>Closing Balance</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {row.potBreakdowns.map((pot, idx) => (
                                      <tr key={idx} className="inner-pot-row">
                                        <td className="font-semibold text-white">{pot.potName}</td>
                                        <td>
                                          <span className={`type-badge-mini ${pot.potType.toLowerCase()}`}>
                                            {pot.potType}
                                          </span>
                                        </td>
                                        <td className="text-muted">{pot.ownerName}</td>
                                        <td>£{Math.round(pot.startBalance).toLocaleString('en-GB')}</td>
                                        <td className={pot.contributionsAdded > 0 ? 'text-emerald' : 'text-muted'}>
                                          {pot.contributionsAdded > 0 ? `+£${Math.round(pot.contributionsAdded).toLocaleString('en-GB')}` : '—'}
                                        </td>
                                        <td className={pot.bedAndIsaTransfer !== 0 ? (pot.bedAndIsaTransfer > 0 ? 'text-cyan' : 'text-purple') : 'text-muted'}>
                                          {pot.bedAndIsaTransfer !== 0
                                            ? `${pot.bedAndIsaTransfer > 0 ? '+' : ''}£${Math.round(pot.bedAndIsaTransfer).toLocaleString('en-GB')}`
                                            : '—'}
                                        </td>
                                        <td className={pot.growthAmount > 0 ? 'text-emerald font-semibold' : 'text-muted'}>
                                          {pot.growthAmount > 0 ? `+£${Math.round(pot.growthAmount).toLocaleString('en-GB')}` : '—'}
                                        </td>
                                        <td className="text-muted">
                                          {pot.growthRate > 0 ? `${pot.growthRate.toFixed(2)}%` : '0%'}
                                        </td>
                                        <td className={pot.grossWithdrawal > 0 ? 'text-amber' : 'text-muted'}>
                                          {pot.grossWithdrawal > 0 ? `£${Math.round(pot.grossWithdrawal).toLocaleString('en-GB')}` : '—'}
                                        </td>
                                        <td className={pot.taxFreeWithdrawal > 0 ? 'text-cyan' : 'text-muted'}>
                                          {pot.taxFreeWithdrawal > 0 ? `£${Math.round(pot.taxFreeWithdrawal).toLocaleString('en-GB')}` : '—'}
                                        </td>
                                        <td className={pot.taxPaid > 0 ? 'text-danger font-semibold' : 'text-muted'}>
                                          {pot.taxPaid > 0 ? `£${Math.round(pot.taxPaid).toLocaleString('en-GB')}` : '£0'}
                                        </td>
                                        <td className="font-bold text-indigo">
                                          £{Math.round(pot.endBalance).toLocaleString('en-GB')}
                                        </td>
                                      </tr>
                                    ))}

                                    {/* Subtotal summary footer */}
                                    <tr className="inner-pot-subtotal">
                                      <td colSpan={3} className="font-bold text-white">YEAR {row.year} TOTALS</td>
                                      <td className="font-bold">
                                        £{Math.round(row.potBreakdowns.reduce((sum, p) => sum + p.startBalance, 0)).toLocaleString('en-GB')}
                                      </td>
                                      <td className="text-emerald font-bold">
                                        +£{Math.round(row.potBreakdowns.reduce((sum, p) => sum + p.contributionsAdded, 0)).toLocaleString('en-GB')}
                                      </td>
                                      <td className="text-muted">—</td>
                                      <td className="text-emerald font-bold">
                                        +£{Math.round(row.totalGrowthThisYear).toLocaleString('en-GB')}
                                      </td>
                                      <td className="text-muted">—</td>
                                      <td className="text-amber font-bold">
                                        £{Math.round(row.grossIncome).toLocaleString('en-GB')}
                                      </td>
                                      <td className="text-cyan font-bold">
                                        £{Math.round(row.potBreakdowns.reduce((sum, p) => sum + p.taxFreeWithdrawal, 0)).toLocaleString('en-GB')}
                                      </td>
                                      <td className="text-danger font-bold">
                                        £{Math.round(row.tax).toLocaleString('en-GB')}
                                      </td>
                                      <td className="text-indigo font-bold">
                                        £{Math.round(row.totalNetWorth).toLocaleString('en-GB')}
                                      </td>
                                    </tr>
                                  </tbody>
                                </table>
                              </div>
                            )}

                            {/* Withdrawals details list (e.g. state pensions, etc) */}
                            {row.withdrawals.length > 0 && (
                              <div className="withdrawals-callout">
                                <span className="font-bold text-sm text-white">Income Streams Delivered:</span>
                                <div className="withdrawals-badges-list">
                                  {row.withdrawals.map((w, wIdx) => (
                                    <span key={wIdx} className="withdrawal-pill">
                                      <strong>{w.potName}</strong>: £{Math.round(w.amountDrawn).toLocaleString('en-GB')}
                                      {w.taxPaid > 0 && <small className="text-danger"> (Tax: £{Math.round(w.taxPaid)})</small>}
                                    </span>
                                  ))}
                                </div>
                              </div>
                            )}
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
