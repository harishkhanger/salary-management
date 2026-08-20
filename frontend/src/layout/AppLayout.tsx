import { Layout, Menu, Button, Typography, Spin } from 'antd'
import {
  TeamOutlined,
  RiseOutlined,
  AuditOutlined,
  DollarOutlined,
  SettingOutlined,
  BarChartOutlined,
  FileSearchOutlined,
  LogoutOutlined,
} from '@ant-design/icons'
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const items = [
  { key: '/', icon: <BarChartOutlined />, label: 'Dashboard' },
  { key: '/employees', icon: <TeamOutlined />, label: 'Employees' },
  { key: '/bulk-raises', icon: <RiseOutlined />, label: 'Bulk raises' },
  { key: '/review-queue', icon: <FileSearchOutlined />, label: 'Review queue' },
  { key: '/payroll', icon: <DollarOutlined />, label: 'Payroll' },
  { key: '/audit', icon: <AuditOutlined />, label: 'Audit feed' },
  { key: '/settings', icon: <SettingOutlined />, label: 'Settings' },
]

export default function AppLayout() {
  const { user, loading, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  if (loading) {
    return <Spin size="large" style={{ display: 'block', marginTop: '20vh' }} />
  }
  if (!user) {
    return <Navigate to="/login" replace />
  }

  const selected = '/' + (location.pathname.split('/')[1] ?? '')

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Layout.Sider theme="dark" width={220}>
        <Typography.Title level={5} style={{ color: 'white', textAlign: 'center', padding: '16px 0 0' }}>
          Salary Mgmt
        </Typography.Title>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selected]}
          items={items}
          onClick={(e) => navigate(e.key)}
        />
      </Layout.Sider>
      <Layout>
        <Layout.Header
          style={{ background: 'white', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 12 }}
        >
          <span>{user.name}</span>
          <Button icon={<LogoutOutlined />} onClick={() => logout().then(() => navigate('/login'))}>
            Logout
          </Button>
        </Layout.Header>
        <Layout.Content style={{ margin: 24 }}>
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  )
}
