import React, { useState } from 'react';
import { Plus, Edit2, Trash2, ArrowUpRight, DollarSign, ShieldAlert, Sparkles } from 'lucide-react';
import { Account, AccountType, Person } from '../types';
import { DonutChart, DonutSlice } from './Charts';

interface DashboardTabProps {
  accounts: Account[];
  people: Person[];
  onAddPot: () => void;
  onEditPot: (account: Account) => void;
  onQuickBalance: (account: Account) => void;
  onDeletePot: (id: string) => void;
}

export const DashboardTab: React.FC<DashboardTabProps> = ({
  accounts,
  people,
  onAddPot,
  onEditPot,
  onQuickBalance,
  onDeletePot
}) => {
  const [selectedPerson, setSelectedPerson] = useState<string>('all');
  const [selectedType, setSelectedType] = useState<string>('all');
  const [searchTerm, setSearchTerm] = useState<string>('');

  const personMap = new Map(people.map(p => [p.id, p.name]));

  // Categorized Totals
  const totalNetWorth = accounts
    .filter(a => a.isIncluded && a.type !== 'FINAL_SALARY')
    .reduce((sum, a) => sum + a.balance, 0);

  const totalDc = accounts
    .filter(a => a.isIncluded && a.type === 'PENSION')
    .reduce((sum, a) => sum + a.balance, 0);

  const totalIsa = accounts
    .filter(a => a.isIncluded && a.type === 'ISA')
    .reduce((sum, a) => sum + a.balance, 0);

  const totalGia = accounts
    .filter(a => a.isIncluded && a.type === 'GENERAL_INVESTMENT')
    .reduce((sum, a) => sum + a.balance, 0);

  const totalCash = accounts
    .filter(a => a.isIncluded && a.type === 'CURRENT')
    .reduce((sum, a) => sum + a.balance, 0);

  const totalDbAnnual = accounts
    .filter(a => a.isIncluded && a.type === 'FINAL_SALARY')
    .reduce((sum, a) => sum + a.balance, 0);

  // Donut slices
  const donutSlices: DonutSlice[] = [
    { label: 'DC Pensions', value: totalDc, color: '#f59e0b' },
    { label: 'ISAs (Tax-Free)', value: totalIsa, color: '#06b6d4' },
    { label: 'General Inv. (GIA)', value: totalGia, color: '#8b5cf6' },
    { label: 'Cash / Current', value: totalCash, color: '#10b981' }
  ].filter(s => s.value > 0);

  // Filtered accounts
  const filteredAccounts = accounts.filter(a => {
    if (selectedPerson !== 'all' && a.personId !== selectedPerson) return false;
    if (selectedType !== 'all' && a.type !== selectedType) return false;
    if (searchTerm.trim() && !a.name.toLowerCase().includes(searchTerm.toLowerCase())) return false;
    return true;
  });

  const getTypeBadge = (type: AccountType) => {
    switch (type) {
      case 'PENSION':
        return <span className="type-badge badge-pension">Pension</span>;
      case 'ISA':
        return <span className="type-badge badge-isa">ISA</span>;
      case 'GENERAL_INVESTMENT':
        return <span className="type-badge badge-gia">GIA</span>;
      case 'CURRENT':
        return <span className="type-badge badge-current">Current</span>;
      case 'FINAL_SALARY':
        return <span className="type-badge badge-db">DB Final Salary</span>;
    }
  };

  return (
    <div className="tab-page-container">
      {/* KPI Cards Row */}
      <div className="kpi-grid">
        <div className="kpi-card highlight-glow">
          <div className="kpi-header">
            <span className="kpi-label">Total Liquid Net Worth</span>
            <Sparkles size={18} className="text-indigo" />
          </div>
          <div className="kpi-value">£{Math.round(totalNetWorth).toLocaleString('en-GB')}</div>
          <div className="kpi-footer">Across all pensions, ISAs, GIAs and cash accounts</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-label">Pensions Pot (DC)</span>
            <span className="dot dot-amber" />
          </div>
          <div className="kpi-value">£{Math.round(totalDc).toLocaleString('en-GB')}</div>
          <div className="kpi-footer">25% tax-free lifetime allowance up to £268,275</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-label">ISAs (100% Tax-Free)</span>
            <span className="dot dot-cyan" />
          </div>
          <div className="kpi-value">£{Math.round(totalIsa).toLocaleString('en-GB')}</div>
          <div className="kpi-footer">Sheltered from UK income and capital gains tax</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-label">GIAs & Cash</span>
            <span className="dot dot-purple" />
          </div>
          <div className="kpi-value">£{Math.round(totalGia + totalCash).toLocaleString('en-GB')}</div>
          <div className="kpi-footer">Bed & ISA transfers shelter £20k/person/year</div>
        </div>

        {totalDbAnnual > 0 && (
          <div className="kpi-card">
            <div className="kpi-header">
              <span className="kpi-label">Guaranteed DB Payout</span>
              <span className="dot dot-pink" />
            </div>
            <div className="kpi-value">£{Math.round(totalDbAnnual).toLocaleString('en-GB')}<small>/yr</small></div>
            <div className="kpi-footer">Index-linked final salary pensions (e.g. NHS)</div>
          </div>
        )}
      </div>

      {/* Main Content Layout: Donut Breakdown + Filter Bar & Pot Cards */}
      <div className="dashboard-content-layout">
        {/* Left Column: Donut Breakdown Card */}
        <div className="dashboard-sidebar">
          <div className="sidebar-card">
            <h3 className="section-title">Portfolio Asset Allocation</h3>
            <p className="section-desc">Distribution of total accumulated wealth by vehicle</p>
            <DonutChart slices={donutSlices} centerLabel="Net Worth" centerValue={`£${Math.round(totalNetWorth).toLocaleString('en-GB')}`} />
          </div>
        </div>

        {/* Right Column: Pot List & Management */}
        <div className="dashboard-main">
          {/* Controls Bar */}
          <div className="controls-bar">
            <div className="search-filter-group">
              <input
                type="text"
                className="search-input"
                placeholder="Search pots..."
                value={searchTerm}
                onChange={e => setSearchTerm(e.target.value)}
              />

              <select
                className="filter-select"
                value={selectedPerson}
                onChange={e => setSelectedPerson(e.target.value)}
              >
                <option value="all">All People</option>
                {people.map(p => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>

              <select
                className="filter-select"
                value={selectedType}
                onChange={e => setSelectedType(e.target.value)}
              >
                <option value="all">All Types</option>
                <option value="PENSION">Pensions (DC)</option>
                <option value="FINAL_SALARY">Final Salary (DB)</option>
                <option value="ISA">ISAs</option>
                <option value="GENERAL_INVESTMENT">GIAs</option>
                <option value="CURRENT">Current Account</option>
              </select>
            </div>

            <button type="button" className="btn btn-primary" onClick={onAddPot}>
              <Plus size={18} />
              <span>Add New Pot</span>
            </button>
          </div>

          {/* Pots Grid */}
          {filteredAccounts.length === 0 ? (
            <div className="empty-state-card">
              <ShieldAlert size={48} className="empty-state-icon" />
              <h4>No Investment Pots Found</h4>
              <p>No pots match your search or filter. Try clearing filters or add a new pot.</p>
              <button type="button" className="btn btn-secondary" onClick={() => { setSearchTerm(''); setSelectedPerson('all'); setSelectedType('all'); }}>
                Clear Filters
              </button>
            </div>
          ) : (
            <div className="pots-grid">
              {filteredAccounts.map(pot => {
                const ownerName = personMap.get(pot.personId) || 'User';
                const isDb = pot.type === 'FINAL_SALARY';
                return (
                  <div key={pot.id} className="pot-card">
                    <div className="pot-card-header">
                      <div className="pot-badges">
                        {getTypeBadge(pot.type)}
                        <span className="owner-badge">{ownerName}</span>
                        {pot.institution && <span className="institution-badge">{pot.institution}</span>}
                      </div>

                      <div className="pot-card-actions">
                        <button
                          type="button"
                          className="action-icon-btn"
                          title="Quick Balance Update"
                          onClick={() => onQuickBalance(pot)}
                        >
                          <DollarSign size={16} />
                        </button>
                        <button
                          type="button"
                          className="action-icon-btn"
                          title="Edit Pot Details"
                          onClick={() => onEditPot(pot)}
                        >
                          <Edit2 size={16} />
                        </button>
                        <button
                          type="button"
                          className="action-icon-btn danger"
                          title="Delete Pot"
                          onClick={() => {
                            if (window.confirm(`Are you sure you want to delete ${pot.name}?`)) {
                              onDeletePot(pot.id);
                            }
                          }}
                        >
                          <Trash2 size={16} />
                        </button>
                      </div>
                    </div>

                    <h4 className="pot-name">{pot.name}</h4>

                    <div className="pot-balance-row">
                      <span className="pot-balance-value">
                        £{Math.round(pot.balance).toLocaleString('en-GB')}
                        {isDb && <small className="pot-subtext"> / year</small>}
                      </span>
                    </div>

                    <div className="pot-meta-grid">
                      {isDb ? (
                        <>
                          <div className="pot-meta-item">
                            <span className="meta-label">Payout Age:</span>
                            <span className="meta-val">Age {pot.payoutAge}</span>
                          </div>
                          <div className="pot-meta-item">
                            <span className="meta-label">Index Linked:</span>
                            <span className="meta-val">{pot.isInflationLinked ? 'Yes (CPI)' : 'No'}</span>
                          </div>
                        </>
                      ) : (
                        <>
                          <div className="pot-meta-item">
                            <span className="meta-label">Growth / Interest:</span>
                            <span className="meta-val text-emerald">+{pot.interestRate}%</span>
                          </div>
                          {pot.annualManagementCharge > 0 && (
                            <div className="pot-meta-item">
                              <span className="meta-label">AMC Fee:</span>
                              <span className="meta-val">{pot.annualManagementCharge}%</span>
                            </div>
                          )}
                          {pot.monthlyContribution > 0 && (
                            <div className="pot-meta-item">
                              <span className="meta-label">Personal Monthly:</span>
                              <span className="meta-val">+£{pot.monthlyContribution.toLocaleString('en-GB')}/mo</span>
                            </div>
                          )}
                          {pot.employerContribution > 0 && (
                            <div className="pot-meta-item">
                              <span className="meta-label">Employer Match:</span>
                              <span className="meta-val">+£{pot.employerContribution.toLocaleString('en-GB')}/mo</span>
                            </div>
                          )}
                        </>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
