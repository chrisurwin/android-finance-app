import React, { useState, useEffect } from 'react';
import { Account, Person, InvestmentAssumptions, DrawdownPreferences } from './types';
import { loadStoredState, saveStoredState } from './utils/storage';
import { Navbar, TabType } from './components/Navbar';
import { DashboardTab } from './components/DashboardTab';
import { ProjectionsTab } from './components/ProjectionsTab';
import { PeopleAssumptionsTab } from './components/PeopleAssumptionsTab';
import { DataTab } from './components/DataTab';
import { PotModal } from './components/PotModal';

export const App: React.FC = () => {
  const [initialLoaded, setInitialLoaded] = useState(false);
  const [currentTab, setCurrentTab] = useState<TabType>('dashboard');

  const [people, setPeople] = useState<Person[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [assumptions, setAssumptions] = useState<InvestmentAssumptions>({} as InvestmentAssumptions);
  const [drawdown, setDrawdown] = useState<DrawdownPreferences>({} as DrawdownPreferences);

  // Modal State
  const [isPotModalOpen, setIsPotModalOpen] = useState(false);
  const [editingPot, setEditingPot] = useState<Account | null>(null);
  const [isBalanceOnly, setIsBalanceOnly] = useState(false);

  // Initialize from LocalStorage
  useEffect(() => {
    const stored = loadStoredState();
    setPeople(stored.people);
    setAccounts(stored.accounts);
    setAssumptions(stored.assumptions);
    setDrawdown(stored.drawdown);
    setInitialLoaded(true);
  }, []);

  // Save to LocalStorage whenever state changes
  useEffect(() => {
    if (initialLoaded) {
      saveStoredState({
        people,
        accounts,
        assumptions,
        drawdown
      });
    }
  }, [people, accounts, assumptions, drawdown, initialLoaded]);

  if (!initialLoaded) {
    return <div className="loading-screen">Loading RetireTrack...</div>;
  }

  // Net worth calculation
  const totalNetWorth = accounts
    .filter(a => a.isIncluded && a.type !== 'FINAL_SALARY')
    .reduce((sum, a) => sum + a.balance, 0);

  const activePersonNames = drawdown.isCouple
    ? `${people[0]?.name || 'Person 1'} & ${people[1]?.name || 'Person 2'}`
    : (people[0]?.name || 'Person 1');

  // Account Handlers
  const handleAddPot = () => {
    setEditingPot(null);
    setIsBalanceOnly(false);
    setIsPotModalOpen(true);
  };

  const handleEditPot = (account: Account) => {
    setEditingPot(account);
    setIsBalanceOnly(false);
    setIsPotModalOpen(true);
  };

  const handleQuickBalance = (account: Account) => {
    setEditingPot(account);
    setIsBalanceOnly(true);
    setIsPotModalOpen(true);
  };

  const handleSavePot = (savedAccount: Account) => {
    setAccounts(prev => {
      const idx = prev.findIndex(a => a.id === savedAccount.id);
      if (idx >= 0) {
        const next = [...prev];
        next[idx] = savedAccount;
        return next;
      }
      return [...prev, savedAccount];
    });
  };

  const handleDeletePot = (id: string) => {
    setAccounts(prev => prev.filter(a => a.id !== id));
  };

  const handleImportState = (
    newPeople: Person[],
    newAccounts: Account[],
    newAssumptions: InvestmentAssumptions,
    newDrawdown: DrawdownPreferences
  ) => {
    setPeople(newPeople);
    setAccounts(newAccounts);
    setAssumptions(newAssumptions);
    setDrawdown(newDrawdown);
  };

  return (
    <div className="app-shell">
      <Navbar
        currentTab={currentTab}
        onSelectTab={setCurrentTab}
        totalNetWorth={totalNetWorth}
        isCouple={drawdown.isCouple}
        activePersonNames={activePersonNames}
      />

      <main className="main-content-area">
        {currentTab === 'dashboard' && (
          <DashboardTab
            accounts={accounts}
            people={people}
            onAddPot={handleAddPot}
            onEditPot={handleEditPot}
            onQuickBalance={handleQuickBalance}
            onDeletePot={handleDeletePot}
          />
        )}

        {currentTab === 'projections' && (
          <ProjectionsTab
            accounts={accounts}
            assumptions={assumptions}
            drawdown={drawdown}
            people={people}
            onUpdateDrawdown={setDrawdown}
          />
        )}

        {currentTab === 'assumptions' && (
          <PeopleAssumptionsTab
            people={people}
            assumptions={assumptions}
            drawdown={drawdown}
            onUpdatePeople={setPeople}
            onUpdateAssumptions={setAssumptions}
            onUpdateDrawdown={setDrawdown}
          />
        )}

        {currentTab === 'data' && (
          <DataTab
            people={people}
            accounts={accounts}
            assumptions={assumptions}
            drawdown={drawdown}
            onImportState={handleImportState}
          />
        )}
      </main>

      <PotModal
        isOpen={isPotModalOpen}
        onClose={() => setIsPotModalOpen(false)}
        onSave={handleSavePot}
        initialAccount={editingPot}
        people={people}
        isBalanceOnly={isBalanceOnly}
      />
    </div>
  );
};

export default App;
