import React, { useState } from 'react';
import { ProjectionResult } from '../types';

export interface DonutSlice {
  label: string;
  value: number;
  color: string;
}

interface DonutChartProps {
  slices: DonutSlice[];
  centerLabel?: string;
  centerValue?: string;
}

export const DonutChart: React.FC<DonutChartProps> = ({ slices, centerLabel = 'Total Value', centerValue }) => {
  const total = slices.reduce((sum, s) => sum + s.value, 0);
  const size = 260;
  const strokeWidth = 28;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;

  let accumulatedPercent = 0;

  return (
    <div className="donut-chart-wrapper">
      <div className="donut-svg-container" style={{ width: size, height: size, position: 'relative' }}>
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
          {/* Background track */}
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="transparent"
            stroke="rgba(255, 255, 255, 0.05)"
            strokeWidth={strokeWidth}
          />

          {total === 0 ? (
            <circle
              cx={size / 2}
              cy={size / 2}
              r={radius}
              fill="transparent"
              stroke="rgba(255, 255, 255, 0.15)"
              strokeWidth={strokeWidth}
            />
          ) : (
            slices.map((slice, i) => {
              const percent = slice.value / total;
              if (percent <= 0) return null;
              const dashLength = percent * circumference;
              const strokeDasharray = `${dashLength} ${circumference - dashLength}`;
              const strokeDashoffset = -accumulatedPercent * circumference;
              accumulatedPercent += percent;

              return (
                <circle
                  key={i}
                  cx={size / 2}
                  cy={size / 2}
                  r={radius}
                  fill="transparent"
                  stroke={slice.color}
                  strokeWidth={strokeWidth}
                  strokeDasharray={strokeDasharray}
                  strokeDashoffset={strokeDashoffset}
                  strokeLinecap="round"
                  transform={`rotate(-90 ${size / 2} ${size / 2})`}
                  style={{
                    transition: 'stroke-dasharray 0.6s ease, stroke-dashoffset 0.6s ease',
                    filter: `drop-shadow(0 0 6px ${slice.color}44)`
                  }}
                />
              );
            })
          )}
        </svg>

        <div className="donut-center-content">
          <div className="donut-center-label">{centerLabel}</div>
          <div className="donut-center-value">
            {centerValue ?? `£${Math.round(total).toLocaleString('en-GB')}`}
          </div>
        </div>
      </div>

      <div className="donut-legend">
        {slices.map((slice, i) => {
          const pct = total > 0 ? ((slice.value / total) * 100).toFixed(1) : '0';
          return (
            <div key={i} className="donut-legend-item">
              <span className="donut-legend-dot" style={{ backgroundColor: slice.color, boxShadow: `0 0 8px ${slice.color}` }} />
              <div className="donut-legend-info">
                <span className="donut-legend-label">{slice.label}</span>
                <span className="donut-legend-value">
                  £{Math.round(slice.value).toLocaleString('en-GB')} <small>({pct}%)</small>
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

interface ProjectionChartProps {
  data: ProjectionResult[];
  retirementAge: number;
}

export const ProjectionChart: React.FC<ProjectionChartProps> = ({ data, retirementAge }) => {
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);

  if (!data || data.length === 0) return null;

  const width = 800;
  const height = 340;
  const padding = { top: 30, right: 30, bottom: 40, left: 75 };
  const graphWidth = width - padding.left - padding.right;
  const graphHeight = height - padding.top - padding.bottom;

  const maxVal = Math.max(
    ...data.map(d => Math.max(d.totalNetWorth, d.totalPensionValue, d.totalSavings, d.grossIncome * 4)),
    100000
  );

  const minAge = data[0].age;
  const maxAge = data[data.length - 1].age;
  const ageSpan = Math.max(1, maxAge - minAge);

  const getX = (age: number) => padding.left + ((age - minAge) / ageSpan) * graphWidth;
  const getY = (val: number) => padding.top + graphHeight - (Math.max(0, val) / maxVal) * graphHeight;

  // Path generators
  const pensionPoints = data.map(d => `${getX(d.age)},${getY(d.totalPensionValue)}`).join(' ');
  const savingsPoints = data.map(d => `${getX(d.age)},${getY(d.totalSavings)}`).join(' ');
  const netWorthPoints = data.map(d => `${getX(d.age)},${getY(d.totalNetWorth)}`).join(' ');

  // Area under Net Worth
  const netWorthArea = `${getX(minAge)},${padding.top + graphHeight} ${netWorthPoints} ${getX(maxAge)},${padding.top + graphHeight}`;

  // Y-axis tick marks
  const yTicks = [0, 0.25, 0.5, 0.75, 1.0].map(fraction => {
    const val = maxVal * fraction;
    return {
      value: val,
      y: getY(val),
      label: val >= 1000000 ? `£${(val / 1000000).toFixed(1)}M` : `£${Math.round(val / 1000)}k`
    };
  });

  // X-axis tick marks
  const xTicks: number[] = [];
  const step = Math.ceil(ageSpan / 8);
  for (let a = minAge; a <= maxAge; a += step) {
    xTicks.push(a);
  }
  if (!xTicks.includes(maxAge)) xTicks.push(maxAge);

  const hoverItem = hoverIndex !== null ? data[hoverIndex] : null;

  return (
    <div className="projection-chart-card">
      <div className="chart-header">
        <div>
          <h3 className="chart-title">Net Worth & Pot Trajectory</h3>
          <p className="chart-subtitle">Pensions, Savings, and Total Wealth through retirement</p>
        </div>
        <div className="chart-series-legend">
          <div className="legend-badge">
            <span className="dot" style={{ backgroundColor: '#6366f1' }} /> Total Net Worth
          </div>
          <div className="legend-badge">
            <span className="dot" style={{ backgroundColor: '#f59e0b' }} /> Pension Pots
          </div>
          <div className="legend-badge">
            <span className="dot" style={{ backgroundColor: '#06b6d4' }} /> Savings & ISAs
          </div>
          <div className="legend-badge">
            <span className="line-dashed" /> Retirement Age ({retirementAge})
          </div>
        </div>
      </div>

      <div className="chart-svg-container" style={{ position: 'relative' }}>
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="projection-svg"
          onMouseLeave={() => setHoverIndex(null)}
        >
          <defs>
            <linearGradient id="netWorthGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#6366f1" stopOpacity="0.35" />
              <stop offset="100%" stopColor="#6366f1" stopOpacity="0.0" />
            </linearGradient>
            <linearGradient id="pensionGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#f59e0b" stopOpacity="0.25" />
              <stop offset="100%" stopColor="#f59e0b" stopOpacity="0.0" />
            </linearGradient>
          </defs>

          {/* Grid lines */}
          {yTicks.map((tick, i) => (
            <g key={i}>
              <line
                x1={padding.left}
                y1={tick.y}
                x2={width - padding.right}
                y2={tick.y}
                stroke="rgba(255, 255, 255, 0.08)"
                strokeDasharray="4 4"
              />
              <text
                x={padding.left - 10}
                y={tick.y + 4}
                textAnchor="end"
                className="axis-label"
              >
                {tick.label}
              </text>
            </g>
          ))}

          {/* X Axis labels */}
          {xTicks.map((age, i) => (
            <text
              key={i}
              x={getX(age)}
              y={height - 12}
              textAnchor="middle"
              className="axis-label"
            >
              Age {age}
            </text>
          ))}

          {/* Retirement vertical line marker */}
          {retirementAge >= minAge && retirementAge <= maxAge && (
            <g>
              <line
                x1={getX(retirementAge)}
                y1={padding.top}
                x2={getX(retirementAge)}
                y2={height - padding.bottom}
                stroke="#ec4899"
                strokeWidth={2}
                strokeDasharray="5 5"
              />
              <rect
                x={getX(retirementAge) - 35}
                y={padding.top - 20}
                width={70}
                height={18}
                rx={4}
                fill="#ec4899"
              />
              <text
                x={getX(retirementAge)}
                y={padding.top - 7}
                textAnchor="middle"
                fill="#ffffff"
                fontSize={10}
                fontWeight="bold"
              >
                Retire {retirementAge}
              </text>
            </g>
          )}

          {/* Net Worth Area */}
          <polygon points={netWorthArea} fill="url(#netWorthGrad)" />

          {/* Lines */}
          <polyline
            points={pensionPoints}
            fill="none"
            stroke="#f59e0b"
            strokeWidth={2.5}
            strokeLinecap="round"
            strokeLinejoin="round"
          />

          <polyline
            points={savingsPoints}
            fill="none"
            stroke="#06b6d4"
            strokeWidth={2.5}
            strokeLinecap="round"
            strokeLinejoin="round"
          />

          <polyline
            points={netWorthPoints}
            fill="none"
            stroke="#6366f1"
            strokeWidth={3}
            strokeLinecap="round"
            strokeLinejoin="round"
            style={{ filter: 'drop-shadow(0 0 6px rgba(99, 102, 241, 0.5))' }}
          />

          {/* Hover crosshair and data points */}
          {hoverItem && (
            <g>
              <line
                x1={getX(hoverItem.age)}
                y1={padding.top}
                x2={getX(hoverItem.age)}
                y2={height - padding.bottom}
                stroke="rgba(255, 255, 255, 0.4)"
                strokeWidth={1.5}
                strokeDasharray="3 3"
              />
              <circle
                cx={getX(hoverItem.age)}
                cy={getY(hoverItem.totalNetWorth)}
                r={6}
                fill="#6366f1"
                stroke="#ffffff"
                strokeWidth={2}
              />
              <circle
                cx={getX(hoverItem.age)}
                cy={getY(hoverItem.totalPensionValue)}
                r={5}
                fill="#f59e0b"
                stroke="#ffffff"
                strokeWidth={2}
              />
              <circle
                cx={getX(hoverItem.age)}
                cy={getY(hoverItem.totalSavings)}
                r={5}
                fill="#06b6d4"
                stroke="#ffffff"
                strokeWidth={2}
              />
            </g>
          )}

          {/* Transparent hit boxes for mouse hover */}
          {data.map((d, index) => {
            const x = getX(d.age);
            const boxWidth = graphWidth / data.length;
            return (
              <rect
                key={index}
                x={x - boxWidth / 2}
                y={padding.top}
                width={boxWidth}
                height={graphHeight}
                fill="transparent"
                onMouseEnter={() => setHoverIndex(index)}
                style={{ cursor: 'pointer' }}
              />
            );
          })}
        </svg>

        {/* Hover Tooltip Overlay */}
        {hoverItem && (
          <div
            className="chart-tooltip"
            style={{
              left: `${Math.min(Math.max(10, (getX(hoverItem.age) / width) * 100), 75)}%`,
              top: '15px'
            }}
          >
            <div className="tooltip-title">
              Age {hoverItem.age} ({hoverItem.year})
              {hoverItem.age >= retirementAge ? (
                <span className="tooltip-tag retired">In Retirement</span>
              ) : (
                <span className="tooltip-tag working">Accumulating</span>
              )}
            </div>
            <div className="tooltip-grid">
              <div className="tooltip-row">
                <span className="dot" style={{ backgroundColor: '#6366f1' }} />
                <span>Net Worth:</span>
                <strong>£{Math.round(hoverItem.totalNetWorth).toLocaleString('en-GB')}</strong>
              </div>
              <div className="tooltip-row">
                <span className="dot" style={{ backgroundColor: '#f59e0b' }} />
                <span>Pensions:</span>
                <strong>£{Math.round(hoverItem.totalPensionValue).toLocaleString('en-GB')}</strong>
              </div>
              <div className="tooltip-row">
                <span className="dot" style={{ backgroundColor: '#06b6d4' }} />
                <span>Savings / ISAs:</span>
                <strong>£{Math.round(hoverItem.totalSavings).toLocaleString('en-GB')}</strong>
              </div>
              {hoverItem.netIncome > 0 && (
                <>
                  <div className="tooltip-row">
                    <span className="dot" style={{ backgroundColor: '#10b981' }} />
                    <span>Net Income:</span>
                    <strong>£{Math.round(hoverItem.netIncome).toLocaleString('en-GB')}</strong>
                  </div>
                  <div className="tooltip-row">
                    <span className="dot" style={{ backgroundColor: '#ef4444' }} />
                    <span>Tax Paid:</span>
                    <strong>£{Math.round(hoverItem.tax).toLocaleString('en-GB')}</strong>
                  </div>
                </>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
