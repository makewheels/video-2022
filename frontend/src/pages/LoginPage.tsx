import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import api from '../utils/api';
import { useToast } from '../utils/toast';

function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { toast } = useToast();

  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [countdown, setCountdown] = useState(0);
  const [codeRequested, setCodeRequested] = useState(false);

  useEffect(() => {
    if (countdown <= 0) return;
    const timer = setTimeout(() => setCountdown((c) => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [countdown]);

  const handleRequestCode = useCallback(async () => {
    if (!/^1\d{10}$/.test(phone)) {
      toast('请输入正确的手机号', 'error');
      return;
    }
    try {
      await api.get('/user/requestVerificationCode', { params: { phone } });
      setCountdown(60);
      setCodeRequested(true);
      toast('验证码已发送', 'success');
    } catch (err) {
      toast(err instanceof Error ? err.message : '发送验证码失败', 'error');
    }
  }, [phone, toast]);

  const handleLogin = useCallback(async () => {
    if (!code.trim()) {
      toast('请输入验证码', 'error');
      return;
    }
    try {
      const res = await api.get('/user/submitVerificationCode', {
        params: { phone, code },
      });
      const token = res.data.data.token as string;
      const target = searchParams.get('target') || '/';
      navigate(`/auth/callback?token=${encodeURIComponent(token)}&target=${encodeURIComponent(target)}`);
    } catch (err) {
      toast(err instanceof Error ? err.message : '登录失败', 'error');
    }
  }, [phone, code, searchParams, navigate, toast]);

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '80vh' }}>
      <div className="card" style={{ width: 400 }}>
        <div className="card-header">登录</div>
        <div style={{ padding: 24 }}>
          <div className="form-group">
            <input
              className="form-input"
              type="tel"
              placeholder="手机号"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              maxLength={11}
            />
          </div>
          <div className="form-group" style={{ display: 'flex', gap: 8 }}>
            <input
              className="form-input"
              type="text"
              placeholder="验证码"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              style={{ flex: 1 }}
            />
            <button
              className="btn btn-secondary"
              disabled={countdown > 0}
              onClick={handleRequestCode}
            >
              {countdown > 0 ? `${countdown}秒后重试` : '获取验证码'}
            </button>
          </div>
          <div className="form-group">
            <button
              className="btn btn-primary"
              style={{ width: '100%' }}
              disabled={!codeRequested}
              onClick={handleLogin}
            >
              登录
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default LoginPage;
