import { Outlet } from 'react-router-dom';
import NavBar from './NavBar';
import { ToastProvider } from '../utils/toast';

export default function Layout() {
  return (
    <ToastProvider>
      <NavBar />
      <Outlet />
      <footer className="page-footer">
        <span>Video Platform</span>
      </footer>
    </ToastProvider>
  );
}
