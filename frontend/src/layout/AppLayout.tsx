import { Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Spinner } from '../components/ui'
import {
  AuditIcon,
  ChartIcon,
  HomeIcon,
  CoinsIcon,
  GearIcon,
  LogoutIcon,
  QueueIcon,
  RiseIcon,
  TeamIcon,
} from '../components/icons'

const nav = [
  { path: '/', label: 'Home', icon: <HomeIcon /> },
  { path: '/employees', label: 'Employees', icon: <TeamIcon /> },
  { path: '/bulk-raises', label: 'Bulk raises', icon: <RiseIcon /> },
  { path: '/review-queue', label: 'Review queue', icon: <QueueIcon /> },
  { path: '/payroll', label: 'Payroll', icon: <CoinsIcon /> },
  { path: '/analytics', label: 'Analytics', icon: <ChartIcon /> },
  { path: '/audit', label: 'Audit feed', icon: <AuditIcon /> },
  { path: '/settings', label: 'Settings', icon: <GearIcon /> },
]

export default function AppLayout() {
  const { user, loading, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  if (loading) return <Spinner />
  if (!user) return <Navigate to="/login" replace />

  const section = '/' + (location.pathname.split('/')[1] ?? '')

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="sidebar-brand-mark">S</div>
          Salary Mgmt
        </div>
        <nav className="sidebar-nav">
          {nav.map((item) => (
            <button
              key={item.path}
              className={`nav-item${section === item.path ? ' active' : ''}`}
              onClick={() => navigate(item.path)}
            >
              {item.icon}
              {item.label}
            </button>
          ))}
        </nav>
      </aside>
      <div className="main">
        <header className="topbar">
          <span className="topbar-user">
            <span className="avatar">{user.name.charAt(0).toUpperCase()}</span>
            {user.name}
          </span>
          <button className="btn btn-sm" onClick={() => logout().then(() => navigate('/login'))}>
            <span style={{ width: 15, height: 15, display: 'inline-flex' }}>
              <LogoutIcon />
            </span>
            Logout
          </button>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
