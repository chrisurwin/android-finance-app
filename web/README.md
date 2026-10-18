# RetireTrack Pro - UK Pension & Wealth Intelligence Web Application

A modern, high-performance web application for comprehensive UK retirement planning, pension drawdown modeling, and multi-asset wealth forecasting.

Built with **React 19 + TypeScript + Vite**, running in a lightweight **Docker container (Nginx)**, featuring custom dark-mode glassmorphism styling and zero external bloated UI dependencies.

---

## 🌟 Key Features

1. **Detailed Per-Pot Projections & Yearly Lifecycle**:
   - For every projection year, view a **deep-dive table for each individual pot**:
     - **Opening Balance**
     - **Contributions Added** (Employee + Employer match)
     - **Bed & ISA Transfers** (Sheltering £20k/person/year from GIA into ISAs)
     - **Growth Achieved** (£ gain and effective % return after AMC charges)
     - **Gross Withdrawals Taken**
     - **Tax-Free Portion** (UFPLS or ISA)
     - **Taxable Portion**
     - **Tax Paid attributable to this specific pot**
     - **Closing Balance**
2. **Complete UK Tax & Pension Engine (2024–2026)**:
   - **Lump Sum Allowance (LSA)**: Strictly enforces the £268,275 lifetime 25% tax-free cap across all pensions.
   - **Upfront vs As-You-Go (UFPLS)**:
     - Taking 25% upfront deposits it into a General Investment Account (GIA).
     - Models annual **"Bed & ISA" transfers** (£20,000/person/year, £40,000/year for couple) into ISAs to shelter money from tax.
   - **Tax on Savings Interest**: GIA returns incur UK savings interest tax based on Personal Savings Allowance (£1,000 basic / £500 higher / £0 additional).
   - **Drawdown Priority**: Non-ISAs (Current accounts & GIAs) are drawn down **before** touching ISAs to preserve the 100% tax-free ISA umbrella as long as possible.
   - **Personal Allowance Taper**: £1 lost per £2 earned above £100,000 (reaching 0 at £125,140).
   - **State Pension Forecasting**: Accurate legislated State Pension Ages (66 / 67 / 68) with qualifying National Insurance years.
   - **Defined Benefit / Final Salary (DB)** pensions (e.g. NHS) with customizable payout ages and inflation-linking.
3. **Personal Data Export & Import**:
   - **Export JSON**: Download your entire portfolio, people, ages, assumptions, and preferences in one click.
   - **Export CSV**: Download the full year-by-year and per-pot breakdown for spreadsheet analysis.
   - **Smart Import**: Intelligently loads and migrates data from:
     - RetireTrack Pro web format
     - Android Finance App format
     - Legacy pension tracker backup files
4. **Manual Account Management (No OpenBanking Hassle)**:
   - Easily add, edit, or delete any pot.
   - 1-click Quick Balance Update modal for regular tracking.
5. **Modern, Dynamic UI**:
   - Responsive dark-mode palette with glassmorphism panels.
   - Interactive SVG Donut charts and multi-line projection trajectory charts with hover crosshairs and tooltips.

---

## 🐳 Running with Docker

You can run RetireTrack Pro in a Docker container in one step:

```bash
# Build and run container in detached mode
docker compose up --build -d
```

The application is now accessible at:
👉 **http://localhost:8080**

To stop the container:
```bash
docker compose down
```

---

## 💻 Running Locally for Development

Ensure you have **Node.js 20+** installed:

```bash
# Install dependencies
npm install

# Start Vite development server
npm run dev
```

Visit **http://localhost:5173** (or the port shown in terminal).

To build the production bundle locally:
```bash
npm run build
```

---

## 📂 Project Structure

```
├── Dockerfile              # Multi-stage build (Node 22 -> Nginx Alpine)
├── docker-compose.yml      # Port 8080 mapping
├── nginx.conf              # SPA routing, gzip compression, security headers
├── package.json            # Scripts and dependencies
├── src/
│   ├── types.ts            # Core TypeScript interfaces
│   ├── App.tsx             # Root application shell
│   ├── index.css           # Vanilla CSS design system & tokens
│   ├── components/
│   │   ├── Navbar.tsx      # Top glassmorphism navigation & net worth pill
│   │   ├── DashboardTab.tsx# Asset overview, donut charts, pot cards
│   │   ├── ProjectionsTab.tsx # Interactive forecast & per-pot deep dive
│   │   ├── PeopleAssumptionsTab.tsx # Couple & macroeconomic inputs
│   │   ├── DataTab.tsx     # JSON & CSV Import/Export handlers
│   │   ├── PotModal.tsx    # Add/Edit/Quick Balance modal
│   │   └── Charts.tsx      # Custom SVG Donut & Projection line charts
│   └── utils/
│       ├── pensionEngine.ts# UK tax, LSA, Bed & ISA, and projection math
│       └── storage.ts      # LocalStorage & multi-schema import/export
```
