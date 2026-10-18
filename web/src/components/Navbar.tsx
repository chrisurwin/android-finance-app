import React from 'react';
import { Wallet, TrendingUp, Users, FileDown, ShieldCheck } from 'lucide-react';

export type TabType = 'dashboard' | 'projections' | 'assumptions' | 'data';

interface NavbarProps {
  currentTab: TabType;
  onSelectTab: (tab: TabType) => void;
  totalNetWorth: number;
  isCouple: boolean;
  activePersonNames: string;
}

export const Navbar: React.FC<NavbarProps> = ({
  currentTab,
  onSelectTab,
  totalNetWorth,
  isCouple,
  activePersonNames
}) => {
  return (
    <header className="navbar-container">
      <div className="navbar-inner">
        <div className="brand-section">
          <div className="brand-logo-glow">
            <ShieldCheck className="brand-icon" size={24} />
          </div>
          <div className="brand-titles">
            <h1 className="brand-title">RetireTrack Pro</h1>
            <span className="brand-subtitle">UK Pension & Wealth Intelligence</span>
          </div>
        </div>

        {/* Navigation Tabs */}
        <nav className="nav-tabs" aria-label="Main Navigation">
          <button
            type="button"
            className={`nav-tab-btn ${currentTab === 'dashboard' ? 'active' : ''}`}
            onClick={() => onSelectTab('dashboard')}
          >
            <Wallet size={18} />
            <span>Dashboard & Pots</span>
          </button>

          <button
            type="button"
            className={`nav-tab-btn ${currentTab === 'projections' ? 'active' : ''}`}
            onClick={() => onSelectTab('projections')}
          >
            <TrendingUp size={18} />
            <span>Projections & Breakdown</span>
          </button>

          <button
            type="button"
            className={`nav-tab-btn ${currentTab === 'assumptions' ? 'active' : ''}`}
            onClick={() => onSelectTab('assumptions')}
          >
            <Users size={18} />
            <span>People & Assumptions</span>
          </button>

          <button
            type="button"
            className={`nav-tab-btn ${currentTab === 'data' ? 'active' : ''}`}
            onClick={() => onSelectTab('data')}
          >
            <FileDown size={18} />
            <span>Data Export / Import</span>
          </button>
        </nav>

        {/* Net Worth & Scenario Pill */}
        <div className="navbar-badge-container">
          <div className="scenario-pill">
            <span className="scenario-dot" />
            <span className="scenario-text">{isCouple ? `Couple (${activePersonNames})` : activePersonNames}</span>
          </div>
          <div className="net-worth-pill">
            <span className="net-worth-label">Total Net Worth</span>
            <span className="net-worth-value">£{Math.round(totalNetWorth).toLocaleString('en-GB')}</span>
          </div>
        </div>
      </div>
    </header>
  );
};
