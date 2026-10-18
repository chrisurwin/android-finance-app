import React, { useState, useEffect } from 'react';
import { X, Check } from 'lucide-react';
import { Account, AccountType, Person } from '../types';

interface PotModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (account: Account) => void;
  initialAccount?: Account | null;
  people: Person[];
  isBalanceOnly?: boolean;
}

export const PotModal: React.FC<PotModalProps> = ({
  isOpen,
  onClose,
  onSave,
  initialAccount,
  people,
  isBalanceOnly = false
}) => {
  const [name, setName] = useState('');
  const [type, setType] = useState<AccountType>('PENSION');
  const [institution, setInstitution] = useState('AVIVA');
  const [balance, setBalance] = useState<number>(0);
  const [monthlyContribution, setMonthlyContribution] = useState<number>(0);
  const [employerContribution, setEmployerContribution] = useState<number>(0);
  const [interestRate, setInterestRate] = useState<number>(7.0);
  const [annualManagementCharge, setAnnualManagementCharge] = useState<number>(0.5);
  const [personId, setPersonId] = useState('person-1');
  const [payoutAge, setPayoutAge] = useState<number>(65);
  const [isInflationLinked, setIsInflationLinked] = useState<boolean>(true);

  useEffect(() => {
    if (initialAccount) {
      setName(initialAccount.name);
      setType(initialAccount.type);
      setInstitution(initialAccount.institution);
      setBalance(initialAccount.balance);
      setMonthlyContribution(initialAccount.monthlyContribution);
      setEmployerContribution(initialAccount.employerContribution);
      setInterestRate(initialAccount.interestRate ?? 0);
      setAnnualManagementCharge(initialAccount.annualManagementCharge ?? 0);
      setPersonId(initialAccount.personId);
      setPayoutAge(initialAccount.payoutAge ?? 65);
      setIsInflationLinked(initialAccount.isInflationLinked ?? true);
    } else {
      setName('');
      setType('PENSION');
      setInstitution('AVIVA');
      setBalance(50000);
      setMonthlyContribution(0);
      setEmployerContribution(0);
      setInterestRate(7.0);
      setAnnualManagementCharge(0.5);
      setPersonId(people[0]?.id || 'person-1');
      setPayoutAge(65);
      setIsInflationLinked(true);
    }
  }, [initialAccount, isOpen, people]);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    const accountToSave: Account = {
      id: initialAccount ? initialAccount.id : `pot-${Date.now()}`,
      name: name.trim(),
      type,
      institution,
      balance: Math.max(0, balance),
      monthlyContribution: Math.max(0, monthlyContribution),
      employerContribution: type === 'PENSION' ? Math.max(0, employerContribution) : 0,
      interestRate: Math.max(0, interestRate),
      annualManagementCharge: (type === 'PENSION' || type === 'ISA' || type === 'GENERAL_INVESTMENT')
        ? Math.max(0, annualManagementCharge)
        : 0,
      personId,
      isIncluded: initialAccount ? initialAccount.isIncluded : true,
      payoutAge: type === 'FINAL_SALARY' ? payoutAge : 65,
      isInflationLinked: type === 'FINAL_SALARY' ? isInflationLinked : true
    };

    onSave(accountToSave);
    onClose();
  };

  return (
    <div className="modal-backdrop">
      <div className="modal-card">
        <div className="modal-header">
          <h2 className="modal-title">
            {isBalanceOnly
              ? `Update Balance: ${initialAccount?.name}`
              : (initialAccount ? 'Edit Investment Pot' : 'Add New Investment Pot')}
          </h2>
          <button type="button" className="modal-close-btn" onClick={onClose}>
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="modal-form">
          {isBalanceOnly ? (
            <div className="form-group">
              <label htmlFor="pot-balance-quick" className="form-label">Current Balance (£)</label>
              <input
                id="pot-balance-quick"
                type="number"
                step="any"
                min="0"
                className="form-input text-lg"
                value={balance}
                onChange={e => setBalance(parseFloat(e.target.value) || 0)}
                autoFocus
              />
            </div>
          ) : (
            <>
              <div className="form-row">
                <div className="form-group flex-2">
                  <label htmlFor="pot-name" className="form-label">Pot / Account Name</label>
                  <input
                    id="pot-name"
                    type="text"
                    required
                    placeholder="e.g. Aviva Workplace Pension"
                    className="form-input"
                    value={name}
                    onChange={e => setName(e.target.value)}
                  />
                </div>

                <div className="form-group flex-1">
                  <label htmlFor="pot-type" className="form-label">Account Type</label>
                  <select
                    id="pot-type"
                    className="form-select"
                    value={type}
                    onChange={e => {
                      const newType = e.target.value as AccountType;
                      setType(newType);
                      if (newType === 'CURRENT') {
                        setInterestRate(1.5);
                        setAnnualManagementCharge(0);
                      } else if (newType === 'FINAL_SALARY') {
                        setInterestRate(0);
                        setAnnualManagementCharge(0);
                      } else if (newType === 'PENSION') {
                        setInterestRate(7.0);
                        setAnnualManagementCharge(0.5);
                      }
                    }}
                  >
                    <option value="PENSION">Pension (Defined Contribution)</option>
                    <option value="FINAL_SALARY">Final Salary / DB Pension</option>
                    <option value="ISA">Stocks & Shares / Cash ISA</option>
                    <option value="GENERAL_INVESTMENT">General Investment Account (GIA)</option>
                    <option value="CURRENT">Current Account (Cash)</option>
                  </select>
                </div>
              </div>

              <div className="form-row">
                <div className="form-group flex-1">
                  <label htmlFor="pot-owner" className="form-label">Owner</label>
                  <select
                    id="pot-owner"
                    className="form-select"
                    value={personId}
                    onChange={e => setPersonId(e.target.value)}
                  >
                    {people.map(p => (
                      <option key={p.id} value={p.id}>{p.name}</option>
                    ))}
                  </select>
                </div>

                <div className="form-group flex-1">
                  <label htmlFor="pot-institution" className="form-label">Institution / Provider</label>
                  <input
                    id="pot-institution"
                    type="text"
                    className="form-input"
                    placeholder="e.g. Aviva, Nutmeg, HSBC, NHS"
                    value={institution}
                    onChange={e => setInstitution(e.target.value)}
                  />
                </div>
              </div>

              <div className="form-row">
                <div className="form-group flex-1">
                  <label htmlFor="pot-balance" className="form-label">
                    {type === 'FINAL_SALARY' ? 'Annual Payout (£/year)' : 'Current Balance (£)'}
                  </label>
                  <input
                    id="pot-balance"
                    type="number"
                    step="any"
                    min="0"
                    required
                    className="form-input"
                    value={balance}
                    onChange={e => setBalance(parseFloat(e.target.value) || 0)}
                  />
                </div>

                {type !== 'FINAL_SALARY' && (
                  <div className="form-group flex-1">
                    <label htmlFor="pot-monthly" className="form-label">Monthly Contribution (£)</label>
                    <input
                      id="pot-monthly"
                      type="number"
                      step="any"
                      min="0"
                      className="form-input"
                      value={monthlyContribution}
                      onChange={e => setMonthlyContribution(parseFloat(e.target.value) || 0)}
                    />
                  </div>
                )}
              </div>

              {type === 'PENSION' && (
                <div className="form-row">
                  <div className="form-group flex-1">
                    <label htmlFor="pot-employer" className="form-label">Employer Monthly Match (£)</label>
                    <input
                      id="pot-employer"
                      type="number"
                      step="any"
                      min="0"
                      className="form-input"
                      value={employerContribution}
                      onChange={e => setEmployerContribution(parseFloat(e.target.value) || 0)}
                    />
                  </div>
                  <div className="form-group flex-1">
                    <label htmlFor="pot-amc" className="form-label">Annual Charge (AMC %)</label>
                    <input
                      id="pot-amc"
                      type="number"
                      step="0.01"
                      min="0"
                      max="10"
                      className="form-input"
                      value={annualManagementCharge}
                      onChange={e => setAnnualManagementCharge(parseFloat(e.target.value) || 0)}
                    />
                  </div>
                </div>
              )}

              {type !== 'FINAL_SALARY' && (
                <div className="form-row">
                  <div className="form-group flex-1">
                    <label htmlFor="pot-rate" className="form-label">Expected Growth / Interest Rate (%)</label>
                    <input
                      id="pot-rate"
                      type="number"
                      step="0.1"
                      min="0"
                      max="50"
                      className="form-input"
                      value={interestRate}
                      onChange={e => setInterestRate(parseFloat(e.target.value) || 0)}
                    />
                  </div>
                  {type !== 'CURRENT' && type !== 'PENSION' && (
                    <div className="form-group flex-1">
                      <label htmlFor="pot-amc-invest" className="form-label">Platform / AMC Fee (%)</label>
                      <input
                        id="pot-amc-invest"
                        type="number"
                        step="0.01"
                        min="0"
                        max="10"
                        className="form-input"
                        value={annualManagementCharge}
                        onChange={e => setAnnualManagementCharge(parseFloat(e.target.value) || 0)}
                      />
                    </div>
                  )}
                </div>
              )}

              {type === 'FINAL_SALARY' && (
                <div className="form-row">
                  <div className="form-group flex-1">
                    <label htmlFor="pot-payout-age" className="form-label">Payout Start Age</label>
                    <input
                      id="pot-payout-age"
                      type="number"
                      min="50"
                      max="75"
                      className="form-input"
                      value={payoutAge}
                      onChange={e => setPayoutAge(parseInt(e.target.value, 10) || 65)}
                    />
                  </div>
                  <div className="form-group flex-1 flex-center-items">
                    <label className="checkbox-label">
                      <input
                        type="checkbox"
                        checked={isInflationLinked}
                        onChange={e => setIsInflationLinked(e.target.checked)}
                      />
                      <span>Inflation Linked (CPI / RPI increases)</span>
                    </label>
                  </div>
                </div>
              )}
            </>
          )}

          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary">
              <Check size={18} />
              <span>{initialAccount ? 'Save Changes' : 'Create Pot'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
