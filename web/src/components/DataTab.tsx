import React, { useState, useRef } from 'react';
import {
  Download,
  Upload,
  RotateCcw,
  FileSpreadsheet,
  FileJson,
  CheckCircle,
  AlertCircle,
  HelpCircle
} from 'lucide-react';
import { Account, Person, InvestmentAssumptions, DrawdownPreferences, AppDataExport } from '../types';
import {
  downloadJsonFile,
  downloadDetailedCsv,
  parseImportedJson,
  DEFAULT_PEOPLE,
  DEFAULT_ASSUMPTIONS,
  DEFAULT_DRAWDOWN
} from '../utils/storage';
import { calculateProjections } from '../utils/pensionEngine';

interface DataTabProps {
  people: Person[];
  accounts: Account[];
  assumptions: InvestmentAssumptions;
  drawdown: DrawdownPreferences;
  onImportState: (
    people: Person[],
    accounts: Account[],
    assumptions: InvestmentAssumptions,
    drawdown: DrawdownPreferences
  ) => void;
}

export const DataTab: React.FC<DataTabProps> = ({
  people,
  accounts,
  assumptions,
  drawdown,
  onImportState
}) => {
  const [importStatus, setImportStatus] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleExportJson = () => {
    const exportData: AppDataExport = {
      version: '1.0.0',
      exportDate: new Date().toISOString(),
      isCouple: drawdown.isCouple,
      people,
      accounts,
      assumptions,
      drawdownPreferences: drawdown
    };
    downloadJsonFile(exportData, `retiretrack-portfolio-${new Date().toISOString().split('T')[0]}.json`);
  };

  const handleExportCsv = () => {
    const p1 = people[0] || DEFAULT_PEOPLE[0];
    const p2 = people[1] || DEFAULT_PEOPLE[1];
    const projection = calculateProjections(
      accounts,
      assumptions,
      drawdown,
      p1,
      p2,
      p1.retirementAge,
      p2.retirementAge,
      drawdown.isCouple ? 'COUPLE' : 'INDIVIDUAL_PERSON1'
    );
    downloadDetailedCsv(projection.results);
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = event => {
      try {
        const text = event.target?.result as string;
        const imported = parseImportedJson(text);
        onImportState(imported.people, imported.accounts, imported.assumptions, imported.drawdown);
        setImportStatus({
          type: 'success',
          message: `Successfully imported ${imported.accounts.length} pots and ${imported.people.length} people profiles!`
        });
      } catch (err: any) {
        setImportStatus({
          type: 'error',
          message: err.message || 'Failed to parse JSON file. Please check file format.'
        });
      }
    };
    reader.readAsText(file);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleReset = () => {
    if (window.confirm('Reset all accounts and data to clean defaults?')) {
      onImportState(DEFAULT_PEOPLE, [], DEFAULT_ASSUMPTIONS, DEFAULT_DRAWDOWN);
      setImportStatus({
        type: 'success',
        message: 'Reset data to empty portfolio.'
      });
    }
  };

  return (
    <div className="tab-page-container">
      {/* Status banner */}
      {importStatus && (
        <div className={`notification-banner ${importStatus.type === 'success' ? 'success' : 'error'}`}>
          {importStatus.type === 'success' ? <CheckCircle size={20} /> : <AlertCircle size={20} />}
          <span>{importStatus.message}</span>
        </div>
      )}

      {/* Export & Import Cards Grid */}
      <div className="grid-two-cols">
        {/* Export Card */}
        <div className="card-glass">
          <div className="flex-row items-center gap-2 mb-3">
            <Download size={22} className="text-indigo" />
            <h3 className="font-bold text-lg text-white">Export Portfolio & Projections</h3>
          </div>
          <p className="text-muted text-sm mb-6">
            Save your complete setup, personal details, and accounts locally as a JSON file, or export the full per-pot yearly breakdown to a spreadsheet.
          </p>

          <div className="flex-col gap-3">
            <button type="button" className="btn btn-primary" onClick={handleExportJson}>
              <FileJson size={18} />
              <span>Export Complete Portfolio (JSON)</span>
            </button>

            <button type="button" className="btn btn-secondary" onClick={handleExportCsv}>
              <FileSpreadsheet size={18} />
              <span>Export Detailed Breakdown (CSV / Excel)</span>
            </button>
          </div>
        </div>

        {/* Import Card */}
        <div className="card-glass">
          <div className="flex-row items-center gap-2 mb-3">
            <Upload size={22} className="text-cyan" />
            <h3 className="font-bold text-lg text-white">Import Portfolio Data</h3>
          </div>
          <p className="text-muted text-sm mb-6">
            Import an existing portfolio JSON file. Compatible with this web app, the Android Finance App, and legacy pension tracker files.
          </p>

          <input
            ref={fileInputRef}
            type="file"
            accept=".json,application/json"
            style={{ display: 'none' }}
            onChange={handleFileUpload}
          />

          <div className="flex-col gap-3">
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => fileInputRef.current?.click()}
            >
              <Upload size={18} />
              <span>Select JSON File to Import</span>
            </button>

            <button
              type="button"
              className="btn btn-secondary text-danger"
              onClick={handleReset}
            >
              <RotateCcw size={16} />
              <span>Reset to Empty Portfolio</span>
            </button>
          </div>
        </div>
      </div>

      {/* UK Pension Tax & Drawdown Rules Card */}
      <div className="card-glass">
        <div className="flex-row items-center gap-2 mb-4">
          <HelpCircle size={22} className="text-indigo" />
          <h3 className="font-bold text-lg text-white">UK Pension & Tax Rules Guide (2024–2026)</h3>
        </div>

        <div className="grid-three-cols text-sm">
          <div className="p-4 rounded-lg bg-surface-dark border border-white/5">
            <h4 className="font-bold text-amber mb-2">25% Tax-Free Lifetime Allowance (LSA)</h4>
            <p className="text-muted leading-relaxed">
              Effective 6 April 2024, the UK Lump Sum Allowance (LSA) is strictly capped at <strong>£268,275</strong> per individual. Whether you take 25% upfront or draw it progressively via UFPLS (Uncrystallised Funds Pension Lump Sum), total tax-free cash cannot exceed £268,275.
            </p>
          </div>

          <div className="p-4 rounded-lg bg-surface-dark border border-white/5">
            <h4 className="font-bold text-cyan mb-2">Bed & ISA Shelter Transfers (£20k/yr)</h4>
            <p className="text-muted leading-relaxed">
              Funds withdrawn upfront into a General Investment Account (GIA) incur tax on savings interest. This app automatically simulates "Bed & ISA" transfers of up to <strong>£20,000 per person per year (£40,000 for couples)</strong> into ISAs to shelter money from tax.
            </p>
          </div>

          <div className="p-4 rounded-lg bg-surface-dark border border-white/5">
            <h4 className="font-bold text-emerald mb-2">Drawdown Order: Non-ISAs First</h4>
            <p className="text-muted leading-relaxed">
              To maximize tax-free growth, the drawdown engine exhausts Current Accounts and taxable GIAs before touching ISAs. This preserves the 100% tax-free ISA umbrella for as long as possible into old age.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
