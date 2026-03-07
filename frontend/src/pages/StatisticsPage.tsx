import { useState, useEffect, useCallback } from 'react';
import ReactECharts from 'echarts-for-react';
import api from '../utils/api';
import { useToast } from '../utils/toast';

function getTimestampRange(days: number): { start: number; end: number } {
  const end = Date.now();
  const start = end - days * 24 * 60 * 60 * 1000;
  return { start, end };
}

function formatDate(ts: number): string {
  return new Date(ts).toISOString().slice(0, 10);
}

function StatisticsPage() {
  const { toast } = useToast();
  const [activeRange, setActiveRange] = useState<'7' | '30' | 'custom'>('7');
  const [startDate, setStartDate] = useState(() => formatDate(Date.now() - 7 * 24 * 60 * 60 * 1000));
  const [endDate, setEndDate] = useState(() => formatDate(Date.now()));
  const [chartOption, setChartOption] = useState<Record<string, unknown>>({});

  const loadData = useCallback(async (startTime: number, endTime: number) => {
    try {
      const res = await api.get('/statistics/aggregateTrafficData', {
        params: { startTime, endTime },
      });
      setChartOption(res.data.data as Record<string, unknown>);
    } catch (err) {
      toast(err instanceof Error ? err.message : '加载统计数据失败', 'error');
    }
  }, [toast]);

  useEffect(() => {
    const { start, end } = getTimestampRange(7);
    loadData(start, end);
  }, [loadData]);

  const handlePreset = useCallback((days: 7 | 30) => {
    const label = days === 7 ? '7' : '30';
    setActiveRange(label);
    const { start, end } = getTimestampRange(days);
    setStartDate(formatDate(start));
    setEndDate(formatDate(end));
    loadData(start, end);
  }, [loadData]);

  const handleCustomSearch = useCallback(() => {
    const start = new Date(startDate).getTime();
    const end = new Date(endDate).getTime();
    if (isNaN(start) || isNaN(end) || start > end) {
      toast('请选择有效的日期范围', 'error');
      return;
    }
    setActiveRange('custom');
    loadData(start, end);
  }, [startDate, endDate, loadData, toast]);

  return (
    <div className="page-container">
      <div className="card">
        <div className="card-header">数据统计</div>
        <div className="dashboard-controls">
          <button
            className={`btn ${activeRange === '7' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => handlePreset(7)}
          >
            最近7天
          </button>
          <button
            className={`btn ${activeRange === '30' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => handlePreset(30)}
          >
            最近30天
          </button>
          <input
            type="date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
          />
          <input
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
          />
          <button className="btn btn-primary" onClick={handleCustomSearch}>
            查询
          </button>
        </div>
        <div className="chart-container">
          <ReactECharts option={chartOption} style={{ height: 400 }} />
        </div>
      </div>
    </div>
  );
}

export default StatisticsPage;
