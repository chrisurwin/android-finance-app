import React from 'react';
import { User, Users, TrendingUp, ShieldCheck, Info } from 'lucide-react';
import { Person, InvestmentAssumptions, DrawdownPreferences } from '../types';
import { getStatePensionAge, calculateStatePension } from '../utils/pensionEngine';

interface PeopleAssumptionsTabProps {
  people: Person[];
  assumptions: InvestmentAssumptions;
  drawdown: DrawdownPreferences;
  onUpdatePeople: (people: Person[]) => void;
  onUpdateAssumptions: (assumptions: InvestmentAssumptions) => void;
  onUpdateDrawdown: (drawdown: DrawdownPreferences) => void;
}

export const PeopleAssumptionsTab: React.FC<PeopleAssumptionsTabProps> = ({
  people,
  assumptions,
  drawdown,
  onUpdatePeople,
  onUpdateAssumptions,
  onUpdateDrawdown
}) => {
  const p1 = people[0] || { id: 'person-1', name: 'Person 1', birthYear: 1980, retirementAge: 65, qualifyingYears: 35 };
  const p2 = people[1] || { id: 'person-2', name: 'Person 2', birthYear: 1982, retirementAge: 65, qualifyingYears: 35 };

  const handleUpdatePerson = (index: number, field: keyof Person, value: any) => {
    const updated = [...people];
    if (!updated[index]) {
      updated[index] = index === 1 ? { ...p2 } : { ...p1 };
    }
    updated[index] = { ...updated[index], [field]: value };
    onUpdatePeople(updated);
  };

  const handleUpdateAssumption = (field: keyof InvestmentAssumptions, value: number) => {
    onUpdateAssumptions({ ...assumptions, [field]: value });
  };

  const blendedRate = (
    assumptions.equityReturn * assumptions.equityAllocation +
    assumptions.bondReturn * assumptions.bondAllocation +
    assumptions.cashReturn * assumptions.cashAllocation
  ) * 100;

  const realReturn = blendedRate - (assumptions.inflationRate * 100);

  const spa1 = getStatePensionAge(p1.birthYear);
  const spa2 = getStatePensionAge(p2.birthYear);
  const spAmount1 = calculateStatePension(p1.qualifyingYears ?? 35);
  const spAmount2 = calculateStatePension(p2.qualifyingYears ?? 35);

  return (
    <div className="tab-page-container">
      {/* Couple Switch Banner */}
      <div className="card-glass-panel flex-row items-center justify-between">
        <div className="flex-row items-center gap-3">
          <div className="p-3 rounded-full bg-indigo-500/20 text-indigo">
            {drawdown.isCouple ? <Users size={24} /> : <User size={24} />}
          </div>
          <div>
            <h3 className="font-bold text-lg text-white">Planning Mode: {drawdown.isCouple ? 'Couple Portfolio' : 'Individual Portfolio'}</h3>
            <p className="text-muted text-sm">
              {drawdown.isCouple
                ? 'Accounts, tax thresholds (£12,570 each), state pensions, and Bed & ISA transfers (£40,000/yr combined) are split across two people'
                : 'Projections use a single person allowance and accounts'}
            </p>
          </div>
        </div>

        <button
          type="button"
          className={`btn ${drawdown.isCouple ? 'btn-primary' : 'btn-secondary'}`}
          onClick={() => onUpdateDrawdown({ ...drawdown, isCouple: !drawdown.isCouple })}
        >
          {drawdown.isCouple ? 'Switch to Individual' : 'Switch to Couple'}
        </button>
      </div>

      {/* People Configuration Cards */}
      <div className="grid-two-cols">
        {/* Person 1 */}
        <div className="card-glass">
          <div className="flex-row items-center justify-between mb-4">
            <div className="flex-row items-center gap-2">
              <User size={20} className="text-amber" />
              <h3 className="font-bold text-lg text-white">Primary Person ({p1.name})</h3>
            </div>
            <span className="badge badge-amber">Person 1</span>
          </div>

          <div className="form-group mb-3">
            <label htmlFor="p1-name-input" className="form-label">Full Name</label>
            <input
              id="p1-name-input"
              type="text"
              className="form-input"
              value={p1.name}
              onChange={e => handleUpdatePerson(0, 'name', e.target.value)}
            />
          </div>

          <div className="form-row mb-3">
            <div className="form-group flex-1">
              <label htmlFor="p1-birthyear-input" className="form-label">Birth Year</label>
              <input
                id="p1-birthyear-input"
                type="number"
                min="1940"
                max="2010"
                className="form-input"
                value={p1.birthYear}
                onChange={e => handleUpdatePerson(0, 'birthYear', parseInt(e.target.value, 10) || 1974)}
              />
            </div>
            <div className="form-group flex-1">
              <label htmlFor="p1-retireage-input" className="form-label">Target Retirement Age</label>
              <input
                id="p1-retireage-input"
                type="number"
                min="50"
                max="75"
                className="form-input"
                value={p1.retirementAge}
                onChange={e => handleUpdatePerson(0, 'retirementAge', parseInt(e.target.value, 10) || 60)}
              />
            </div>
          </div>

          <div className="form-group mb-4">
            <label htmlFor="p1-qualifying-years-input" className="form-label">State Pension Qualifying NI Years (max 35)</label>
            <input
              id="p1-qualifying-years-input"
              type="number"
              min="0"
              max="35"
              className="form-input"
              value={p1.qualifyingYears ?? 35}
              onChange={e => handleUpdatePerson(0, 'qualifyingYears', parseInt(e.target.value, 10) || 35)}
            />
          </div>

          <div className="p-3 rounded-lg bg-surface-dark border border-white/5">
            <div className="flex-row justify-between items-center text-sm mb-1">
              <span className="text-muted">Legislated State Pension Age:</span>
              <strong className="text-white">Age {spa1}</strong>
            </div>
            <div className="flex-row justify-between items-center text-sm">
              <span className="text-muted">Projected Full State Pension:</span>
              <strong className="text-emerald">£{Math.round(spAmount1).toLocaleString('en-GB')}/yr</strong>
            </div>
          </div>
        </div>

        {/* Person 2 (if couple) */}
        {drawdown.isCouple ? (
          <div className="card-glass">
            <div className="flex-row items-center justify-between mb-4">
              <div className="flex-row items-center gap-2">
                <User size={20} className="text-pink" />
                <h3 className="font-bold text-lg text-white">Partner / Spouse ({p2.name})</h3>
              </div>
              <span className="badge badge-pink">Person 2</span>
            </div>

            <div className="form-group mb-3">
              <label htmlFor="p2-name-input" className="form-label">Full Name</label>
              <input
                id="p2-name-input"
                type="text"
                className="form-input"
                value={p2.name}
                onChange={e => handleUpdatePerson(1, 'name', e.target.value)}
              />
            </div>

            <div className="form-row mb-3">
              <div className="form-group flex-1">
                <label htmlFor="p2-birthyear-input" className="form-label">Birth Year</label>
                <input
                  id="p2-birthyear-input"
                  type="number"
                  min="1940"
                  max="2010"
                  className="form-input"
                  value={p2.birthYear}
                  onChange={e => handleUpdatePerson(1, 'birthYear', parseInt(e.target.value, 10) || 1974)}
                />
              </div>
              <div className="form-group flex-1">
                <label htmlFor="p2-retireage-input" className="form-label">Target Retirement Age</label>
                <input
                  id="p2-retireage-input"
                  type="number"
                  min="50"
                  max="75"
                  className="form-input"
                  value={p2.retirementAge}
                  onChange={e => handleUpdatePerson(1, 'retirementAge', parseInt(e.target.value, 10) || 60)}
                />
              </div>
            </div>

            <div className="form-group mb-4">
              <label htmlFor="p2-qualifying-years-input" className="form-label">State Pension Qualifying NI Years (max 35)</label>
              <input
                id="p2-qualifying-years-input"
                type="number"
                min="0"
                max="35"
                className="form-input"
                value={p2.qualifyingYears ?? 35}
                onChange={e => handleUpdatePerson(1, 'qualifyingYears', parseInt(e.target.value, 10) || 35)}
              />
            </div>

            <div className="p-3 rounded-lg bg-surface-dark border border-white/5">
              <div className="flex-row justify-between items-center text-sm mb-1">
                <span className="text-muted">Legislated State Pension Age:</span>
                <strong className="text-white">Age {spa2}</strong>
              </div>
              <div className="flex-row justify-between items-center text-sm">
                <span className="text-muted">Projected Full State Pension:</span>
                <strong className="text-emerald">£{Math.round(spAmount2).toLocaleString('en-GB')}/yr</strong>
              </div>
            </div>
          </div>
        ) : (
          <div className="card-glass flex-col justify-center items-center text-center p-8">
            <Users size={48} className="text-muted mb-3" />
            <h4 className="font-bold text-white mb-2">Individual Planning Enabled</h4>
            <p className="text-muted text-sm mb-4">
              Switch to Couple Mode above to model combined finances, two state pensions, and couple Bed & ISA allowances (£40k/yr).
            </p>
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={() => onUpdateDrawdown({ ...drawdown, isCouple: true })}
            >
              Enable Couple Mode
            </button>
          </div>
        )}
      </div>

      {/* Macro Assumptions Panel */}
      <div className="card-glass">
        <div className="flex-row items-center justify-between mb-4">
          <div className="flex-row items-center gap-2">
            <TrendingUp size={20} className="text-indigo" />
            <h3 className="font-bold text-lg text-white">Investment Returns & Economic Assumptions</h3>
          </div>
          <div className="flex-row items-center gap-3">
            <span className="text-sm text-muted">Nominal Portfolio Growth: <strong className="text-indigo font-bold">{blendedRate.toFixed(2)}%</strong></span>
            <span className="text-sm text-muted">Real (Post-Inflation): <strong className="text-emerald font-bold">+{realReturn.toFixed(2)}%</strong></span>
          </div>
        </div>

        <div className="grid-four-cols mb-6">
          <div className="form-group">
            <label htmlFor="macro-equity-return" className="form-label">Equity Return (%/yr)</label>
            <input
              id="macro-equity-return"
              type="number"
              step="0.1"
              min="0"
              max="20"
              className="form-input"
              value={assumptions.equityReturn * 100}
              onChange={e => handleUpdateAssumption('equityReturn', (parseFloat(e.target.value) || 7) / 100)}
            />
          </div>

          <div className="form-group">
            <label htmlFor="macro-bond-return" className="form-label">Bond Return (%/yr)</label>
            <input
              id="macro-bond-return"
              type="number"
              step="0.1"
              min="0"
              max="15"
              className="form-input"
              value={assumptions.bondReturn * 100}
              onChange={e => handleUpdateAssumption('bondReturn', (parseFloat(e.target.value) || 4) / 100)}
            />
          </div>

          <div className="form-group">
            <label htmlFor="macro-cash-return" className="form-label">Cash Interest (%/yr)</label>
            <input
              id="macro-cash-return"
              type="number"
              step="0.1"
              min="0"
              max="15"
              className="form-input"
              value={assumptions.cashReturn * 100}
              onChange={e => handleUpdateAssumption('cashReturn', (parseFloat(e.target.value) || 2) / 100)}
            />
          </div>

          <div className="form-group">
            <label htmlFor="macro-inflation-rate" className="form-label">Inflation Rate (%/yr)</label>
            <input
              id="macro-inflation-rate"
              type="number"
              step="0.1"
              min="0"
              max="15"
              className="form-input"
              value={assumptions.inflationRate * 100}
              onChange={e => handleUpdateAssumption('inflationRate', (parseFloat(e.target.value) || 2.5) / 100)}
            />
          </div>
        </div>

        <div className="asset-allocation-panel">
          <h4 className="font-semibold text-white mb-2">Asset Allocation Mix</h4>
          <div className="grid-three-cols">
            <div>
              <div className="flex-row justify-between text-sm mb-1">
                <span>Equities:</span>
                <strong className="text-indigo">{Math.round(assumptions.equityAllocation * 100)}%</strong>
              </div>
              <input
                type="range"
                min="0"
                max="100"
                step="5"
                className="slider-input"
                value={assumptions.equityAllocation * 100}
                onChange={e => {
                  const val = parseFloat(e.target.value) / 100;
                  const rem = 1.0 - val;
                  const bond = rem * (assumptions.bondAllocation / (assumptions.bondAllocation + assumptions.cashAllocation || 1));
                  const cash = rem - bond;
                  onUpdateAssumptions({
                    ...assumptions,
                    equityAllocation: val,
                    bondAllocation: bond,
                    cashAllocation: cash
                  });
                }}
              />
            </div>

            <div>
              <div className="flex-row justify-between text-sm mb-1">
                <span>Bonds:</span>
                <strong className="text-amber">{Math.round(assumptions.bondAllocation * 100)}%</strong>
              </div>
              <input
                type="range"
                min="0"
                max="100"
                step="5"
                className="slider-input"
                value={assumptions.bondAllocation * 100}
                onChange={e => {
                  const val = parseFloat(e.target.value) / 100;
                  const rem = 1.0 - assumptions.equityAllocation;
                  const safeVal = Math.min(val, rem);
                  onUpdateAssumptions({
                    ...assumptions,
                    bondAllocation: safeVal,
                    cashAllocation: Math.max(0, rem - safeVal)
                  });
                }}
              />
            </div>

            <div>
              <div className="flex-row justify-between text-sm mb-1">
                <span>Cash:</span>
                <strong className="text-cyan">{Math.round(assumptions.cashAllocation * 100)}%</strong>
              </div>
              <input
                type="range"
                min="0"
                max="100"
                step="5"
                className="slider-input"
                value={assumptions.cashAllocation * 100}
                onChange={e => {
                  const val = parseFloat(e.target.value) / 100;
                  const rem = 1.0 - assumptions.equityAllocation;
                  const safeVal = Math.min(val, rem);
                  onUpdateAssumptions({
                    ...assumptions,
                    cashAllocation: safeVal,
                    bondAllocation: Math.max(0, rem - safeVal)
                  });
                }}
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
